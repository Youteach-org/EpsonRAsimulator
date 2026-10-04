package mx.youteachtk.epsonrasimulator.project.persistence

import java.io.IOException
import java.util.UUID
import mx.youteachtk.epsonrasimulator.project.ProjectRuntime
import mx.youteachtk.epsonrasimulator.project.ProjectRuntimeState
import mx.youteachtk.epsonrasimulator.project.ProjectRuntimeSubscription
import mx.youteachtk.epsonrasimulator.project.persistence.android.DocumentTreeGateway
import mx.youteachtk.epsonrasimulator.project.persistence.android.DocumentTreeProjectDestination
import mx.youteachtk.epsonrasimulator.project.persistence.android.DocumentTreeProjectSource
import mx.youteachtk.epsonrasimulator.project.persistence.session.ProjectSessionPersistencePort
import mx.youteachtk.epsonrasimulator.project.persistence.session.ProjectSessionRestorePlan
import mx.youteachtk.epsonrasimulator.project.persistence.session.ProjectSessionSubscription
import mx.youteachtk.epsonrasimulator.runtime.SharedRuntime

enum class PersistenceStartupStatus {
    LOADING,
    READY,
    ERROR
}

enum class PersistenceSaveStatus {
    NO_PROJECT,
    SAVED,
    RECOVERED,
    DIRTY,
    SAVING,
    ERROR,
    CONFLICT
}

enum class ProjectReplacementDecision {
    SAVE,
    DISCARD,
    CANCEL
}

data class ProjectPersistenceState(
    val startup: PersistenceStartupStatus = PersistenceStartupStatus.LOADING,
    val projectId: String? = null,
    val projectName: String? = null,
    val origin: DocumentTreeOrigin? = null,
    val saveStatus: PersistenceSaveStatus = PersistenceSaveStatus.NO_PROJECT,
    val message: String? = null,
    val replacementDecisionRequired: Boolean = false,
    val canSave: Boolean = false,
    val canExport: Boolean = false,
    val lastExport: FolderExportResult? = null
)

interface ProjectPersistenceController {
    val state: ProjectPersistenceState

    fun attachSessionPersistence(port: ProjectSessionPersistencePort) {
        throw UnsupportedOperationException(
            "This persistence controller does not support semantic session attachment"
        )
    }

    fun start()
    fun requestImport(selection: DocumentTreeSelection)
    fun requestCreateLocal(name: String)
    fun resolveReplacement(decision: ProjectReplacementDecision)
    fun saveNow()
    fun exportTo(selection: DocumentTreeSelection)
    fun dismissMessage()
    fun subscribe(listener: (ProjectPersistenceState) -> Unit): ProjectPersistenceSubscription
    fun close()
}

class ProjectPersistenceSubscription(
    private val onCancel: () -> Unit
) {
    private var cancelled = false
    fun cancel() {
        if (!cancelled) {
            cancelled = true
            onCancel()
        }
    }
}

class ProjectPersistenceCoordinator(
    private val projectRuntime: ProjectRuntime,
    private val runtime: SharedRuntime,
    private val activeRecordStore: ActiveProjectRecordStore,
    private val slotStores: ProjectSlotStoreFactory,
    private val documentGateway: DocumentTreeGateway,
    private val execution: PersistenceExecution,
    private val folderTransfer: ProjectFolderTransfer = ProjectFolderTransfer(),
    private val idGenerator: () -> String = { UUID.randomUUID().toString() },
    private val autosaveDebounceMillis: Long = 750
) : ProjectPersistenceController {
    init {
        require(autosaveDebounceMillis >= 0)
    }

    private val listeners =
        linkedSetOf<(ProjectPersistenceState) -> Unit>()
    private val openLock = Any()

    override var state: ProjectPersistenceState = ProjectPersistenceState()
        private set

    private var started = false
    private var closed = false
    private var projectSubscription: ProjectRuntimeSubscription? = null
    private var sessionPort: ProjectSessionPersistencePort? = null
    private var sessionSubscription: ProjectSessionSubscription? = null
    private var applyingOwnedProject = false
    private var currentToken: SnapshotToken? = null

    @Volatile
    private var currentRevision = 0L

    private var pendingSnapshot: ProjectSnapshot? = null
    private var debounce: PersistenceCancellation? = null
    private var saveInFlight = false
    private var activeRecordPublished = false
    private var pendingOpen: ProjectOpenRequest? = null
    private var openAfterSave = false
    private var queuedOpenAfterPublication: ProjectOpenRequest? = null
    private var openPublicationRequestId: Long? = null
    private var openPublicationInvalidatedByEdit = false
    private var openPublicationSuperseded = false

    @Volatile
    private var openGeneration = 0L

    override fun attachSessionPersistence(
        port: ProjectSessionPersistencePort
    ) {
        check(!started && !closed) {
            "Session persistence must be attached before start"
        }
        check(sessionPort == null) {
            "Session persistence is already attached"
        }
        sessionPort = port
    }

    override fun start() {
        if (started || closed) return
        started = true
        publish(state.copy(startup = PersistenceStartupStatus.LOADING))
        execution.execute {
            val result = loadStartup()
            execution.dispatchUi {
                if (!closed) applyStartup(result)
            }
        }
    }

    override fun requestImport(selection: DocumentTreeSelection) {
        if (closed) return
        if (!selection.read) {
            publish(state.copy(message = "Selected folder is not readable"))
            return
        }
        requestOpen(ProjectOpenRequest.Import(selection))
    }

    override fun requestCreateLocal(name: String) {
        if (closed) return
        val normalized = name.trim()
        try {
            validateMetadata(normalized, PersistenceLimits().maxNameBytes)
        } catch (_: PersistenceException) {
            publish(state.copy(message = "Project name is invalid or too long"))
            return
        }
        requestOpen(ProjectOpenRequest.Local(normalized))
    }

    private fun requestOpen(request: ProjectOpenRequest) {
        if (state.startup != PersistenceStartupStatus.READY) {
            publish(state.copy(message = "Project storage is not ready"))
            return
        }
        val queuedBehindPublication = synchronized(openLock) {
            if (openPublicationRequestId != null) {
                queuedOpenAfterPublication = request
                openPublicationSuperseded = true
                true
            } else {
                // A newer request supersedes unpublished work even while awaiting consent.
                openGeneration += 1
                false
            }
        }
        if (queuedBehindPublication) return
        openAfterSave = false
        if (hasUnsavedWork() && state.projectId != null) {
            pendingOpen = request
            publish(state.copy(replacementDecisionRequired = true, message = null))
            return
        }
        beginOpen(request)
    }

    override fun resolveReplacement(decision: ProjectReplacementDecision) {
        if (closed || !state.replacementDecisionRequired) return
        when (decision) {
            ProjectReplacementDecision.CANCEL -> {
                pendingOpen = null
                openAfterSave = false
                cancelOpenRequests()
                publish(
                    state.copy(
                        replacementDecisionRequired = false,
                        message = null
                    )
                )
            }

            ProjectReplacementDecision.DISCARD -> {
                val selection = pendingOpen ?: return
                publish(state.copy(replacementDecisionRequired = false))
                debounce?.cancel()
                debounce = null
                if (saveInFlight) {
                    openAfterSave = true
                } else {
                    pendingOpen = null
                    beginOpen(selection)
                }
            }

            ProjectReplacementDecision.SAVE -> {
                openAfterSave = true
                publish(state.copy(replacementDecisionRequired = false))
                if (pendingSnapshot == null && !saveInFlight) {
                    val selection = pendingOpen
                    pendingOpen = null
                    openAfterSave = false
                    if (selection != null) beginOpen(selection)
                } else {
                    saveNow()
                }
            }
        }
    }

    override fun saveNow() {
        if (closed) return
        if (state.startup != PersistenceStartupStatus.READY) {
            publish(state.copy(message = "Project storage is not ready"))
            return
        }
        if (state.projectId == null || projectRuntime.state.projectName == null) {
            publish(state.copy(message = "No project to save"))
            return
        }
        debounce?.cancel()
        debounce = null
        if (saveInFlight) return
        val snapshot = pendingSnapshot
        if (snapshot == null) {
            publish(state.copy(message = "Project is already saved"))
            if (openAfterSave) {
                val selection = pendingOpen
                pendingOpen = null
                openAfterSave = false
                if (selection != null) beginOpen(selection)
            }
            return
        }
        beginSave(snapshot)
    }

    override fun exportTo(selection: DocumentTreeSelection) {
        if (closed) return
        val projectId = state.projectId
        val projectName = projectRuntime.state.projectName
        if (
            state.startup != PersistenceStartupStatus.READY ||
            projectId == null ||
            projectName == null
        ) {
            publish(state.copy(message = "No project to export", lastExport = null))
            return
        }
        if (!selection.write) {
            publish(
                state.copy(
                    message = "Selected folder is not writable",
                    lastExport = null
                )
            )
            return
        }

        val snapshot = captureSnapshot(
            projectId = projectId,
            projectName = projectName,
            revision = currentRevision.coerceAtLeast(1)
        )
        execution.execute {
            val result = try {
                val destination =
                    DocumentTreeProjectDestination(selection, documentGateway)
                folderTransfer.exportProject(snapshot, destination)
            } catch (e: PersistenceException) {
                FolderExportResult(
                    rootId = null,
                    completed = emptyList(),
                    failedPath = null,
                    remaining = snapshot.exportResources().keys.sorted(),
                    failure = e.reason
                )
            } catch (_: IOException) {
                FolderExportResult(
                    null,
                    emptyList(),
                    null,
                    snapshot.exportResources().keys.sorted(),
                    PersistenceFailure.IO
                )
            } catch (_: SecurityException) {
                FolderExportResult(
                    null,
                    emptyList(),
                    null,
                    snapshot.exportResources().keys.sorted(),
                    PersistenceFailure.IO
                )
            }
            execution.dispatchUi {
                if (!closed) {
                    publish(
                        state.copy(
                            lastExport = result,
                            message = if (result.complete) {
                                "Export complete"
                            } else {
                                "Export incomplete"
                            }
                        )
                    )
                }
            }
        }
    }

    override fun dismissMessage() {
        if (!closed) publish(state.copy(message = null))
    }

    override fun subscribe(
        listener: (ProjectPersistenceState) -> Unit
    ): ProjectPersistenceSubscription {
        listeners += listener
        listener(state)
        return ProjectPersistenceSubscription {
            listeners -= listener
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        debounce?.cancel()
        debounce = null
        projectSubscription?.cancel()
        projectSubscription = null
        sessionSubscription?.cancel()
        sessionSubscription = null
        synchronized(openLock) {
            openGeneration++
            openPublicationRequestId = null
            openPublicationInvalidatedByEdit = false
            openPublicationSuperseded = false
        }
        queuedOpenAfterPublication = null
        listeners.clear()
        execution.close()
    }

    private fun loadStartup(): StartupResult = try {
        val record = activeRecordStore.read()
            ?: return StartupResult.Empty
        when (val loaded = slotStores.open(record.projectId).load()) {
            StoreLoad.Empty ->
                StartupResult.Failed(
                    PersistenceFailure.CORRUPT,
                    "Active project has no committed snapshot"
                )

            is StoreLoad.Rejected ->
                StartupResult.Failed(
                    loaded.reason,
                    "Cannot restore the active project"
                )

            is StoreLoad.Loaded -> {
                if (loaded.snapshot.projectId != record.projectId) {
                    StartupResult.Failed(
                        PersistenceFailure.CORRUPT,
                        "Active project id does not match its snapshot"
                    )
                } else {
                    val port = sessionPort
                    if (
                        port == null &&
                        (
                            loaded.snapshot.adapterId !=
                                runtime.state.simulatorAdapterId.value ||
                            loaded.snapshot.robotId != runtime.state.activeRobotId
                        )
                    ) {
                        StartupResult.Failed(
                            PersistenceFailure.INVALID_METADATA,
                            "Saved project targets an unavailable simulator or robot"
                        )
                    } else {
                        val restorePlan =
                            port?.prepareRestore(loaded.snapshot)
                        StartupResult.Loaded(
                            record,
                            loaded.snapshot,
                            loaded.token,
                            loaded.recovered,
                            restorePlan
                        )
                    }
                }
            }
        }
    } catch (e: PersistenceException) {
        StartupResult.Failed(e.reason, "Cannot read active-project metadata")
    } catch (_: IOException) {
        StartupResult.Failed(PersistenceFailure.IO, "Cannot read active-project metadata")
    } catch (_: SecurityException) {
        StartupResult.Failed(PersistenceFailure.IO, "Cannot read active-project metadata")
    }

    private fun applyStartup(result: StartupResult) {
        when (result) {
            StartupResult.Empty -> {
                currentToken = null
                currentRevision = 0
                activeRecordPublished = false
                publish(
                    ProjectPersistenceState(
                        startup = PersistenceStartupStatus.READY,
                        saveStatus = PersistenceSaveStatus.NO_PROJECT
                    )
                )
                subscribeProjectChanges()
            }

            is StartupResult.Failed -> {
                publish(
                    ProjectPersistenceState(
                        startup = PersistenceStartupStatus.ERROR,
                        saveStatus = PersistenceSaveStatus.ERROR,
                        message = result.message
                    )
                )
            }

            is StartupResult.Loaded -> {
                try {
                    applyingOwnedProject = true
                    projectRuntime.loadProject(
                        result.snapshot.projectName,
                        result.snapshot.exportResources()
                    )
                    result.restorePlan?.apply()
                } finally {
                    applyingOwnedProject = false
                }
                currentToken = result.token
                currentRevision = result.snapshot.revision
                activeRecordPublished = true
                pendingSnapshot = null
                publish(
                    ProjectPersistenceState(
                        startup = PersistenceStartupStatus.READY,
                        projectId = result.record.projectId,
                        projectName = result.snapshot.projectName,
                        origin = result.record.origin,
                        saveStatus = if (result.recovered) {
                            PersistenceSaveStatus.RECOVERED
                        } else {
                            PersistenceSaveStatus.SAVED
                        },
                        message = if (result.recovered) {
                            "Recovered the previous complete project generation"
                        } else {
                            null
                        }
                    )
                )
                subscribeProjectChanges()
            }
        }
    }

    private fun subscribeProjectChanges() {
        projectSubscription?.cancel()
        sessionSubscription?.cancel()

        var first = true
        projectSubscription = projectRuntime.subscribe { next ->
            if (first) {
                first = false
            } else if (!closed && !applyingOwnedProject) {
                onDurableStateChanged(next)
            }
        }

        sessionSubscription = sessionPort?.subscribe {
            if (!closed && !applyingOwnedProject) {
                onDurableStateChanged(projectRuntime.state)
            }
        }
    }

    private fun onDurableStateChanged(next: ProjectRuntimeState) {
        if (next.projectName == null) {
            currentRevision += 1
            synchronized(openLock) {
                if (openPublicationRequestId != null) openPublicationInvalidatedByEdit = true
            }
            return
        }
        val projectName = next.projectName
        var projectId = state.projectId
        if (projectId == null) {
            projectId = idGenerator()
            validateCanonicalProjectId(projectId)
            currentRevision = 0
            currentToken = null
            activeRecordPublished = false
        }

        currentRevision += 1
        synchronized(openLock) {
            if (openPublicationRequestId != null) {
                openPublicationInvalidatedByEdit = true
            }
        }
        val snapshot = captureSnapshot(projectId, projectName, currentRevision)
        pendingSnapshot = snapshot
        publish(
            state.copy(
                projectId = projectId,
                projectName = projectName,
                origin = state.origin,
                saveStatus = PersistenceSaveStatus.DIRTY,
                message = null
            )
        )
        if (!saveInFlight) scheduleAutosave()
    }

    private fun scheduleAutosave() {
        debounce?.cancel()
        debounce = execution.schedule(autosaveDebounceMillis) {
            execution.dispatchUi {
                debounce = null
                if (!closed && !saveInFlight) {
                    pendingSnapshot?.let(::beginSave)
                }
            }
        }
    }

    private fun beginSave(snapshot: ProjectSnapshot) {
        if (saveInFlight || closed) return
        debounce?.cancel()
        debounce = null
        saveInFlight = true
        val expected = currentToken
        val origin = state.origin
        val recordPublishedAtStart = activeRecordPublished
        publish(
            state.copy(
                saveStatus = PersistenceSaveStatus.SAVING,
                message = null
            )
        )
        execution.execute {
            val result = try {
                when (
                    val saved =
                        slotStores.open(snapshot.projectId).save(snapshot, expected)
                ) {
                    is StoreSave.Saved -> {
                        if (!recordPublishedAtStart) {
                            try {
                                activeRecordStore.write(
                                    ActiveProjectRecord(snapshot.projectId, origin)
                                )
                            } catch (e: IOException) {
                                return@execute execution.dispatchUi {
                                    handleSaveResult(
                                        snapshot,
                                        SaveResult.RecordFailed(saved.token)
                                    )
                                }
                            } catch (e: SecurityException) {
                                return@execute execution.dispatchUi {
                                    handleSaveResult(
                                        snapshot,
                                        SaveResult.RecordFailed(saved.token)
                                    )
                                }
                            }
                        }
                        SaveResult.Saved(saved.token)
                    }

                    StoreSave.Conflict -> SaveResult.Conflict
                    is StoreSave.Rejected -> SaveResult.Rejected(saved.reason)
                }
            } catch (e: PersistenceException) {
                SaveResult.Rejected(e.reason)
            } catch (_: IOException) {
                SaveResult.Rejected(PersistenceFailure.IO)
            } catch (_: SecurityException) {
                SaveResult.Rejected(PersistenceFailure.IO)
            }
            execution.dispatchUi {
                handleSaveResult(snapshot, result)
            }
        }
    }

    private fun handleSaveResult(
        snapshot: ProjectSnapshot,
        result: SaveResult
    ) {
        if (closed) return
        saveInFlight = false
        when (result) {
            is SaveResult.Saved -> {
                currentToken = result.token
                activeRecordPublished = true
                if (
                    pendingSnapshot?.revision == snapshot.revision &&
                    currentRevision == snapshot.revision
                ) {
                    pendingSnapshot = null
                }
                if (pendingSnapshot == null) {
                    publish(
                        state.copy(
                            saveStatus = PersistenceSaveStatus.SAVED,
                            message = null
                        )
                    )
                    continuePendingOpenAfterSave()
                } else {
                    publish(
                        state.copy(
                            saveStatus = PersistenceSaveStatus.DIRTY,
                            message = null
                        )
                    )
                    if (openAfterSave) {
                        saveNow()
                    } else {
                        scheduleAutosave()
                    }
                }
            }

            is SaveResult.RecordFailed -> {
                currentToken = result.token
                activeRecordPublished = false
                publish(
                    state.copy(
                        saveStatus = PersistenceSaveStatus.ERROR,
                        message = "Project bytes were saved but active-project metadata could not be published"
                    )
                )
            }

            SaveResult.Conflict -> {
                openAfterSave = false
                publish(
                    state.copy(
                        saveStatus = PersistenceSaveStatus.CONFLICT,
                        message = "Project changed outside this session; save was not overwritten"
                    )
                )
            }

            is SaveResult.Rejected -> {
                openAfterSave = false
                publish(
                    state.copy(
                        saveStatus = PersistenceSaveStatus.ERROR,
                        message = "Project save failed: ${result.reason}"
                    )
                )
            }
        }
    }

    private fun continuePendingOpenAfterSave() {
        if (!openAfterSave) return
        val selection = pendingOpen
        pendingOpen = null
        openAfterSave = false
        if (selection != null) beginOpen(selection)
    }

    private fun beginOpen(request: ProjectOpenRequest) {
        debounce?.cancel()
        debounce = null
        val requestId = synchronized(openLock) {
            openGeneration += 1
            openGeneration
        }
        val newProjectId = idGenerator()
        validateCanonicalProjectId(newProjectId)
        val adapterId = runtime.state.simulatorAdapterId.value
        val robotId = runtime.state.activeRobotId
        val authorizedRevision = currentRevision
        val previousRecord =
            if (activeRecordPublished && state.projectId != null) {
                ActiveProjectRecord(
                    requireNotNull(state.projectId),
                    state.origin
                )
            } else {
                null
            }

        // Read live session only on the UI thread. First creation adopts anonymous work.
        val localSidecar = try {
            if (request is ProjectOpenRequest.Local && state.projectId == null)
                sessionPort?.capture() ?: byteArrayOf() else byteArrayOf()
        } catch (_: Exception) {
            publish(state.copy(message = "Cannot create project: current session could not be saved. Check point names and session data, then retry."))
            return
        }

        execution.execute {
            if (!isCurrentOpen(requestId)) return@execute
            val result = try {
                val snapshot = when (request) {
                    is ProjectOpenRequest.Import -> {
                        val source = DocumentTreeProjectSource(request.selection, documentGateway)
                        folderTransfer.importProject(
                            source = source, rootId = source.rootId,
                            projectId = newProjectId, projectName = source.rootName,
                            adapterId = adapterId, robotId = robotId, revision = 1,
                            cancelled = { !isCurrentOpen(requestId) }
                        )
                    }
                    is ProjectOpenRequest.Local -> ProjectSnapshot(
                        projectId = newProjectId, projectName = request.name,
                        adapterId = adapterId, robotId = robotId, revision = 1,
                        resources = emptyMap(), sidecar = localSidecar
                    )
                }
                if (!isCurrentOpen(requestId))
                    return@execute

                val restorePlan =
                    sessionPort?.prepareRestore(snapshot)

                val saved = when (
                    val save =
                        slotStores.open(newProjectId).save(snapshot, null)
                ) {
                    is StoreSave.Saved -> save
                    StoreSave.Conflict ->
                        throw PersistenceException(
                            PersistenceFailure.CONFLICT,
                            "Generated project slot already exists"
                        )
                    is StoreSave.Rejected ->
                        throw PersistenceException(
                            save.reason,
                            "Cannot save new project"
                        )
                }

                if (!isCurrentOpen(requestId))
                    return@execute

                val newOrigin = (request as? ProjectOpenRequest.Import)?.let { documentGateway.persist(it.selection) }
                var needsDecision = false
                try {
                    synchronized(openLock) {
                        if (requestId != openGeneration || closed) {
                            try {
                                if (newOrigin != null) documentGateway.release(newOrigin)
                            } catch (_: Exception) {
                            }
                            return@execute
                        }
                        if (currentRevision != authorizedRevision) {
                            needsDecision = true
                        } else {
                            // Failed durable publication must not leave an in-flight
                            // marker that queues every subsequent request forever.
                            activeRecordStore.write(
                                ActiveProjectRecord(newProjectId, newOrigin)
                            )
                            openPublicationRequestId = requestId
                            openPublicationInvalidatedByEdit = false
                            openPublicationSuperseded = false
                        }
                    }
                } catch (e: Exception) {
                    try {
                        if (newOrigin != null) documentGateway.release(newOrigin)
                    } catch (_: Exception) {
                    }
                    throw e
                }

                if (needsDecision) {
                    try {
                        if (newOrigin != null) documentGateway.release(newOrigin)
                    } catch (_: Exception) {
                    }
                    OpenResult.NeedsDecision(
                        requestId = requestId,
                        request = request
                    )
                } else {
                    OpenResult.Success(
                        requestId = requestId,
                        request = request,
                        snapshot = snapshot,
                        token = saved.token,
                        origin = newOrigin,
                        previousRecord = previousRecord,
                        restorePlan = restorePlan
                    )
                }
            } catch (e: PersistenceException) {
                OpenResult.Failed(
                    requestId,
                    e.reason,
                    "Opening project failed: ${e.reason}"
                )
            } catch (_: IOException) {
                OpenResult.Failed(
                    requestId,
                    PersistenceFailure.IO,
                    "Opening project failed: IO"
                )
            } catch (_: SecurityException) {
                OpenResult.Failed(
                    requestId,
                    PersistenceFailure.IO,
                    "Opening project failed: IO"
                )
            }

            execution.dispatchUi {
                handleOpenResult(result)
            }
        }
    }

    private fun handleOpenResult(result: OpenResult) {
        if (closed) return
        when (result) {
            is OpenResult.Success -> {
                if (!isCurrentOpen(result.requestId)) return

                val invalidation = synchronized(openLock) {
                    val ownsPublication =
                        openPublicationRequestId == result.requestId
                    if (!ownsPublication) {
                        OpenInvalidation.SUPERSEDED
                    } else if (openPublicationInvalidatedByEdit) {
                        OpenInvalidation.EDIT
                    } else if (openPublicationSuperseded) {
                        OpenInvalidation.SUPERSEDED
                    } else {
                        OpenInvalidation.NONE
                    }
                }

                if (invalidation != OpenInvalidation.NONE) {
                    rollbackPublishedOpen(result, invalidation)
                    return
                }

                try {
                    applyingOwnedProject = true
                    projectRuntime.loadProject(
                        result.snapshot.projectName,
                        result.snapshot.exportResources()
                    )
                    result.restorePlan?.apply()
                } finally {
                    applyingOwnedProject = false
                }
                synchronized(openLock) {
                    if (openPublicationRequestId == result.requestId) {
                        openPublicationRequestId = null
                        openPublicationInvalidatedByEdit = false
                        openPublicationSuperseded = false
                    }
                }
                currentToken = result.token
                currentRevision = result.snapshot.revision
                pendingSnapshot = null
                saveInFlight = false
                activeRecordPublished = true
                pendingOpen = null
                openAfterSave = false
                publish(
                    state.copy(
                        projectId = result.snapshot.projectId,
                        projectName = result.snapshot.projectName,
                        origin = result.origin,
                        saveStatus = PersistenceSaveStatus.SAVED,
                        message = null,
                        replacementDecisionRequired = false,
                        lastExport = null
                    )
                )

                releasePreviousOriginAsync(
                    previous = result.previousRecord?.origin,
                    current = result.origin
                )
                continueQueuedOpenAfterPublication()
            }

            is OpenResult.NeedsDecision -> {
                if (!isCurrentOpen(result.requestId)) return
                pendingOpen = result.request
                openAfterSave = false
                publish(
                    state.copy(
                        replacementDecisionRequired = true,
                        message = null
                    )
                )
            }

            is OpenResult.Failed -> {
                if (!isCurrentOpen(result.requestId)) return
                pendingOpen = null
                openAfterSave = false
                val hasCurrent = state.projectId != null
                publish(
                    state.copy(
                        saveStatus = if (hasCurrent) {
                            state.saveStatus
                        } else {
                            PersistenceSaveStatus.ERROR
                        },
                        message = result.message,
                        replacementDecisionRequired = false
                    )
                )
                if (
                    pendingSnapshot != null &&
                    !saveInFlight &&
                    state.projectId != null
                ) {
                    scheduleAutosave()
                }
            }
        }
    }

    private fun rollbackPublishedOpen(
        result: OpenResult.Success,
        invalidation: OpenInvalidation
    ) {
        execution.execute {
            val rollbackFailure = try {
                if (result.previousRecord == null) {
                    activeRecordStore.clear()
                } else {
                    activeRecordStore.write(result.previousRecord)
                }
                try {
                    if (result.origin != null) documentGateway.release(result.origin)
                } catch (_: Exception) {
                }
                null
            } catch (_: Exception) {
                PersistenceFailure.IO
            }

            execution.dispatchUi {
                if (closed) return@dispatchUi
                synchronized(openLock) {
                    if (openPublicationRequestId == result.requestId) {
                        openPublicationRequestId = null
                        openPublicationInvalidatedByEdit = false
                        openPublicationSuperseded = false
                    }
                }
                if (rollbackFailure != null) {
                    publish(
                        state.copy(
                            saveStatus = PersistenceSaveStatus.ERROR,
                            message =
                                "Import could not restore the previous active-project metadata"
                        )
                    )
                    return@dispatchUi
                }

                when (invalidation) {
                    OpenInvalidation.EDIT -> {
                        val replacement =
                            queuedOpenAfterPublication ?: result.request
                        queuedOpenAfterPublication = null
                        pendingOpen = replacement
                        openAfterSave = false
                        publish(
                            state.copy(
                                replacementDecisionRequired = true,
                                message = null
                            )
                        )
                    }

                    OpenInvalidation.SUPERSEDED -> {
                        val replacement = queuedOpenAfterPublication
                        queuedOpenAfterPublication = null
                        if (replacement != null) {
                            beginOpen(replacement)
                        }
                    }

                    OpenInvalidation.NONE -> Unit
                }
            }
        }
    }

    private fun continueQueuedOpenAfterPublication() {
        val queued = queuedOpenAfterPublication
        queuedOpenAfterPublication = null
        if (queued != null) {
            requestOpen(queued)
        }
    }

    private fun releasePreviousOriginAsync(
        previous: DocumentTreeOrigin?,
        current: DocumentTreeOrigin?
    ) {
        if (previous == null || previous.uri == current?.uri) return
        execution.execute {
            try {
                documentGateway.release(previous)
            } catch (_: Exception) {
                execution.dispatchUi {
                    if (!closed) {
                        publish(
                            state.copy(
                                message =
                                    "New project opened, but the previous folder permission could not be released"
                            )
                        )
                    }
                }
            }
        }
    }

    private fun captureSnapshot(
        projectId: String,
        projectName: String,
        revision: Long
    ): ProjectSnapshot =
        ProjectSnapshot(
            projectId = projectId,
            projectName = projectName,
            adapterId = runtime.state.simulatorAdapterId.value,
            robotId = runtime.state.activeRobotId,
            revision = revision,
            resources = projectRuntime.export(),
            sidecar = sessionPort?.capture() ?: byteArrayOf()
        )

    private fun hasUnsavedWork(): Boolean =
        state.saveStatus in setOf(
            PersistenceSaveStatus.DIRTY,
            PersistenceSaveStatus.SAVING,
            PersistenceSaveStatus.ERROR,
            PersistenceSaveStatus.CONFLICT
        )

    private fun isCurrentOpen(requestId: Long): Boolean =
        synchronized(openLock) {
            requestId == openGeneration && !closed
        }

    private fun cancelOpenRequests() {
        synchronized(openLock) {
            openGeneration += 1
        }
    }

    private fun publish(next: ProjectPersistenceState) {
        val canSave =
            next.startup == PersistenceStartupStatus.READY &&
                next.projectId != null &&
                next.saveStatus in setOf(
                    PersistenceSaveStatus.DIRTY,
                    PersistenceSaveStatus.ERROR,
                    PersistenceSaveStatus.CONFLICT
                )
        val canExport =
            next.startup == PersistenceStartupStatus.READY &&
                next.projectId != null
        state = next.copy(
            canSave = canSave,
            canExport = canExport
        )
        listeners.toList().forEach { it(state) }
    }

    private sealed interface StartupResult {
        data object Empty : StartupResult
        data class Loaded(
            val record: ActiveProjectRecord,
            val snapshot: ProjectSnapshot,
            val token: SnapshotToken,
            val recovered: Boolean,
            val restorePlan: ProjectSessionRestorePlan?
        ) : StartupResult

        data class Failed(
            val reason: PersistenceFailure,
            val message: String
        ) : StartupResult
    }

    private sealed interface SaveResult {
        data class Saved(val token: SnapshotToken) : SaveResult
        data class RecordFailed(val token: SnapshotToken) : SaveResult
        data object Conflict : SaveResult
        data class Rejected(val reason: PersistenceFailure) : SaveResult
    }

    private enum class OpenInvalidation {
        NONE,
        EDIT,
        SUPERSEDED
    }

    private sealed interface OpenResult {
        val requestId: Long

        data class Success(
            override val requestId: Long,
            val request: ProjectOpenRequest,
            val snapshot: ProjectSnapshot,
            val token: SnapshotToken,
            val origin: DocumentTreeOrigin?,
            val previousRecord: ActiveProjectRecord?,
            val restorePlan: ProjectSessionRestorePlan?
        ) : OpenResult

        data class NeedsDecision(
            override val requestId: Long,
            val request: ProjectOpenRequest
        ) : OpenResult

        data class Failed(
            override val requestId: Long,
            val reason: PersistenceFailure,
            val message: String
        ) : OpenResult
    }
}
