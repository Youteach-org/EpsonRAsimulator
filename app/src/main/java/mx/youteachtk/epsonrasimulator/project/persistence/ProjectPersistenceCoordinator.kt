package mx.youteachtk.epsonrasimulator.project.persistence

import java.io.IOException
import java.util.UUID
import mx.youteachtk.epsonrasimulator.project.ProjectRuntime
import mx.youteachtk.epsonrasimulator.project.ProjectRuntimeState
import mx.youteachtk.epsonrasimulator.project.ProjectRuntimeSubscription
import mx.youteachtk.epsonrasimulator.project.persistence.android.DocumentTreeGateway
import mx.youteachtk.epsonrasimulator.project.persistence.android.DocumentTreeProjectDestination
import mx.youteachtk.epsonrasimulator.project.persistence.android.DocumentTreeProjectSource
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
    fun start()
    fun requestImport(selection: DocumentTreeSelection)
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
    private val sessionSidecar: ProjectSessionSidecarController? = null,
    private val folderTransfer: ProjectFolderTransfer = ProjectFolderTransfer(),
    private val idGenerator: () -> String = { UUID.randomUUID().toString() },
    private val autosaveDebounceMillis: Long = 750
) : ProjectPersistenceController {
    init {
        require(autosaveDebounceMillis >= 0)
    }

    private val listeners =
        linkedSetOf<(ProjectPersistenceState) -> Unit>()
    private val importLock = Any()

    override var state: ProjectPersistenceState = ProjectPersistenceState()
        private set

    private var started = false
    private var closed = false
    private var projectSubscription: ProjectRuntimeSubscription? = null
    private var sessionSubscription: AppSessionSidecarSubscription? = null
    private var applyingOwnedProject = false
    private var currentToken: SnapshotToken? = null

    @Volatile
    private var currentRevision = 0L

    private var pendingSnapshot: ProjectSnapshot? = null
    private var debounce: PersistenceCancellation? = null
    private var saveInFlight = false
    private var activeRecordPublished = false
    private var pendingImport: DocumentTreeSelection? = null
    private var importAfterSave = false
    private var queuedImportAfterPublication: DocumentTreeSelection? = null
    private var importPublicationRequestId: Long? = null
    private var importPublicationInvalidatedByEdit = false
    private var importPublicationSuperseded = false

    @Volatile
    private var importGeneration = 0L

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
        if (state.startup != PersistenceStartupStatus.READY) {
            publish(state.copy(message = "Project storage is not ready"))
            return
        }
        val queuedBehindPublishedImport = synchronized(importLock) {
            if (importPublicationRequestId != null) {
                queuedImportAfterPublication = selection
                importPublicationSuperseded = true
                true
            } else {
                false
            }
        }
        if (queuedBehindPublishedImport) return
        if (hasUnsavedWork()) {
            pendingImport = selection
            publish(
                state.copy(
                    replacementDecisionRequired = true,
                    message = null
                )
            )
            return
        }
        beginImport(selection)
    }

    override fun resolveReplacement(decision: ProjectReplacementDecision) {
        if (closed || !state.replacementDecisionRequired) return
        when (decision) {
            ProjectReplacementDecision.CANCEL -> {
                pendingImport = null
                importAfterSave = false
                cancelImportRequests()
                publish(
                    state.copy(
                        replacementDecisionRequired = false,
                        message = null
                    )
                )
            }

            ProjectReplacementDecision.DISCARD -> {
                val selection = pendingImport ?: return
                publish(state.copy(replacementDecisionRequired = false))
                debounce?.cancel()
                debounce = null
                if (saveInFlight) {
                    importAfterSave = true
                } else {
                    pendingImport = null
                    beginImport(selection)
                }
            }

            ProjectReplacementDecision.SAVE -> {
                importAfterSave = true
                publish(state.copy(replacementDecisionRequired = false))
                if (pendingSnapshot == null && !saveInFlight) {
                    val selection = pendingImport
                    pendingImport = null
                    importAfterSave = false
                    if (selection != null) beginImport(selection)
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
            if (importAfterSave) {
                val selection = pendingImport
                pendingImport = null
                importAfterSave = false
                if (selection != null) beginImport(selection)
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
        synchronized(importLock) {
            importGeneration++
            importPublicationRequestId = null
            importPublicationInvalidatedByEdit = false
            importPublicationSuperseded = false
        }
        queuedImportAfterPublication = null
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
                } else if (
                    loaded.snapshot.adapterId !=
                        runtime.state.simulatorAdapterId.value ||
                    loaded.snapshot.robotId != runtime.state.activeRobotId
                ) {
                    StartupResult.Failed(
                        PersistenceFailure.INVALID_METADATA,
                        "Saved project targets an unavailable simulator or robot"
                    )
                } else {
                    StartupResult.Loaded(
                        record,
                        loaded.snapshot,
                        loaded.token,
                        loaded.recovered
                    )
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
                subscribeChanges()
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
                val sessionWarning: String?
                try {
                    applyingOwnedProject = true
                    projectRuntime.loadProject(
                        result.snapshot.projectName,
                        result.snapshot.exportResources()
                    )
                    sessionWarning = restoreSessionSidecar(
                        result.snapshot.sidecarBytes()
                    )
                } finally {
                    applyingOwnedProject = false
                }
                currentToken = result.token
                currentRevision = result.snapshot.revision
                activeRecordPublished = true
                pendingSnapshot = null
                val recoveredMessage =
                    if (result.recovered) {
                        "Recovered the previous complete project generation"
                    } else {
                        null
                    }
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
                        message = listOfNotNull(
                            recoveredMessage,
                            sessionWarning
                        ).joinToString(" · ").ifBlank { null }
                    )
                )
                subscribeChanges()
            }
        }
    }

    private fun subscribeChanges() {
        subscribeProjectChanges()
        sessionSubscription?.cancel()
        sessionSubscription = sessionSidecar?.subscribe {
            if (!closed && !applyingOwnedProject) {
                onSessionChanged()
            }
        }
    }

    private fun subscribeProjectChanges() {
        projectSubscription?.cancel()
        var first = true
        projectSubscription = projectRuntime.subscribe { next ->
            if (first) {
                first = false
            } else if (!closed && !applyingOwnedProject) {
                onProjectChanged(next)
            }
        }
    }

    private fun onProjectChanged(next: ProjectRuntimeState) {
        val projectName = next.projectName ?: return
        var projectId = state.projectId
        if (projectId == null) {
            projectId = idGenerator()
            validateCanonicalProjectId(projectId)
            currentRevision = 0
            currentToken = null
            activeRecordPublished = false
        }
        markDirty(projectId, projectName)
    }

    private fun onSessionChanged() {
        val projectId = state.projectId ?: return
        val projectName = projectRuntime.state.projectName ?: return
        markDirty(projectId, projectName)
    }

    private fun markDirty(
        projectId: String,
        projectName: String
    ) {
        currentRevision += 1
        synchronized(importLock) {
            if (importPublicationRequestId != null) {
                importPublicationInvalidatedByEdit = true
            }
        }
        val snapshot = captureSnapshot(
            projectId,
            projectName,
            currentRevision
        )
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
                    continuePendingImportAfterSave()
                } else {
                    publish(
                        state.copy(
                            saveStatus = PersistenceSaveStatus.DIRTY,
                            message = null
                        )
                    )
                    if (importAfterSave) {
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
                importAfterSave = false
                publish(
                    state.copy(
                        saveStatus = PersistenceSaveStatus.CONFLICT,
                        message = "Project changed outside this session; save was not overwritten"
                    )
                )
            }

            is SaveResult.Rejected -> {
                importAfterSave = false
                publish(
                    state.copy(
                        saveStatus = PersistenceSaveStatus.ERROR,
                        message = "Project save failed: ${result.reason}"
                    )
                )
            }
        }
    }

    private fun continuePendingImportAfterSave() {
        if (!importAfterSave) return
        val selection = pendingImport
        pendingImport = null
        importAfterSave = false
        if (selection != null) beginImport(selection)
    }

    private fun beginImport(selection: DocumentTreeSelection) {
        debounce?.cancel()
        debounce = null
        val requestId = synchronized(importLock) {
            importGeneration += 1
            importGeneration
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

        execution.execute {
            if (!isCurrentImport(requestId)) return@execute
            val result = try {
                val source =
                    DocumentTreeProjectSource(selection, documentGateway)
                val snapshot = folderTransfer.importProject(
                    source = source,
                    rootId = source.rootId,
                    projectId = newProjectId,
                    projectName = source.rootName,
                    adapterId = adapterId,
                    robotId = robotId,
                    revision = 1,
                    cancelled = { !isCurrentImport(requestId) }
                )
                if (!isCurrentImport(requestId))
                    return@execute

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
                            "Cannot save imported project"
                        )
                }

                if (!isCurrentImport(requestId))
                    return@execute

                val newOrigin = documentGateway.persist(selection)
                var needsDecision = false
                try {
                    synchronized(importLock) {
                        if (requestId != importGeneration || closed) {
                            try {
                                documentGateway.release(newOrigin)
                            } catch (_: Exception) {
                            }
                            return@execute
                        }
                        if (currentRevision != authorizedRevision) {
                            needsDecision = true
                        } else {
                            importPublicationRequestId = requestId
                            importPublicationInvalidatedByEdit = false
                            importPublicationSuperseded = false
                            activeRecordStore.write(
                                ActiveProjectRecord(newProjectId, newOrigin)
                            )
                        }
                    }
                } catch (e: Exception) {
                    try {
                        documentGateway.release(newOrigin)
                    } catch (_: Exception) {
                    }
                    throw e
                }

                if (needsDecision) {
                    try {
                        documentGateway.release(newOrigin)
                    } catch (_: Exception) {
                    }
                    ImportResult.NeedsDecision(
                        requestId = requestId,
                        selection = selection
                    )
                } else {
                    ImportResult.Success(
                        requestId = requestId,
                        selection = selection,
                        snapshot = snapshot,
                        token = saved.token,
                        origin = newOrigin,
                        previousRecord = previousRecord
                    )
                }
            } catch (e: PersistenceException) {
                ImportResult.Failed(
                    requestId,
                    e.reason,
                    "Import failed: ${e.reason}"
                )
            } catch (_: IOException) {
                ImportResult.Failed(
                    requestId,
                    PersistenceFailure.IO,
                    "Import failed: IO"
                )
            } catch (_: SecurityException) {
                ImportResult.Failed(
                    requestId,
                    PersistenceFailure.IO,
                    "Import failed: IO"
                )
            }

            execution.dispatchUi {
                handleImportResult(result)
            }
        }
    }

    private fun handleImportResult(result: ImportResult) {
        if (closed) return
        when (result) {
            is ImportResult.Success -> {
                if (!isCurrentImport(result.requestId)) return

                val invalidation = synchronized(importLock) {
                    val ownsPublication =
                        importPublicationRequestId == result.requestId
                    if (!ownsPublication) {
                        ImportInvalidation.SUPERSEDED
                    } else if (importPublicationInvalidatedByEdit) {
                        ImportInvalidation.EDIT
                    } else if (importPublicationSuperseded) {
                        ImportInvalidation.SUPERSEDED
                    } else {
                        ImportInvalidation.NONE
                    }
                }

                if (invalidation != ImportInvalidation.NONE) {
                    rollbackPublishedImport(result, invalidation)
                    return
                }

                val sessionWarning: String?
                try {
                    applyingOwnedProject = true
                    projectRuntime.loadProject(
                        result.snapshot.projectName,
                        result.snapshot.exportResources()
                    )
                    sessionWarning = resetImportedSession()
                } finally {
                    applyingOwnedProject = false
                }
                synchronized(importLock) {
                    if (importPublicationRequestId == result.requestId) {
                        importPublicationRequestId = null
                        importPublicationInvalidatedByEdit = false
                        importPublicationSuperseded = false
                    }
                }
                currentToken = result.token
                currentRevision = result.snapshot.revision
                pendingSnapshot = null
                saveInFlight = false
                activeRecordPublished = true
                pendingImport = null
                importAfterSave = false
                publish(
                    state.copy(
                        projectId = result.snapshot.projectId,
                        projectName = result.snapshot.projectName,
                        origin = result.origin,
                        saveStatus = PersistenceSaveStatus.SAVED,
                        message = sessionWarning,
                        replacementDecisionRequired = false,
                        lastExport = null
                    )
                )

                releasePreviousOriginAsync(
                    previous = result.previousRecord?.origin,
                    current = result.origin
                )
                continueQueuedImportAfterPublication()
            }

            is ImportResult.NeedsDecision -> {
                if (!isCurrentImport(result.requestId)) return
                pendingImport = result.selection
                importAfterSave = false
                publish(
                    state.copy(
                        replacementDecisionRequired = true,
                        message = null
                    )
                )
            }

            is ImportResult.Failed -> {
                if (!isCurrentImport(result.requestId)) return
                pendingImport = null
                importAfterSave = false
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

    private fun rollbackPublishedImport(
        result: ImportResult.Success,
        invalidation: ImportInvalidation
    ) {
        execution.execute {
            val rollbackFailure = try {
                if (result.previousRecord == null) {
                    activeRecordStore.clear()
                } else {
                    activeRecordStore.write(result.previousRecord)
                }
                try {
                    documentGateway.release(result.origin)
                } catch (_: Exception) {
                }
                null
            } catch (_: Exception) {
                PersistenceFailure.IO
            }

            execution.dispatchUi {
                if (closed) return@dispatchUi
                synchronized(importLock) {
                    if (importPublicationRequestId == result.requestId) {
                        importPublicationRequestId = null
                        importPublicationInvalidatedByEdit = false
                        importPublicationSuperseded = false
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
                    ImportInvalidation.EDIT -> {
                        val replacement =
                            queuedImportAfterPublication ?: result.selection
                        queuedImportAfterPublication = null
                        pendingImport = replacement
                        importAfterSave = false
                        publish(
                            state.copy(
                                replacementDecisionRequired = true,
                                message = null
                            )
                        )
                    }

                    ImportInvalidation.SUPERSEDED -> {
                        val replacement = queuedImportAfterPublication
                        queuedImportAfterPublication = null
                        if (replacement != null) {
                            beginImport(replacement)
                        }
                    }

                    ImportInvalidation.NONE -> Unit
                }
            }
        }
    }

    private fun continueQueuedImportAfterPublication() {
        val queued = queuedImportAfterPublication
        queuedImportAfterPublication = null
        if (queued != null) {
            requestImport(queued)
        }
    }

    private fun releasePreviousOriginAsync(
        previous: DocumentTreeOrigin?,
        current: DocumentTreeOrigin
    ) {
        if (previous == null || previous.uri == current.uri) return
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
            sidecar = sessionSidecar?.capture() ?: byteArrayOf()
        )

    private fun restoreSessionSidecar(
        bytes: ByteArray
    ): String? {
        val controller = sessionSidecar ?: return null
        return try {
            val restored =
                if (bytes.isEmpty()) {
                    controller.resetForImportedProject(
                        projectRuntime.state
                    )
                } else {
                    controller.apply(
                        bytes,
                        projectRuntime.state
                    )
                }
            restored.warnings
                .takeIf { it.isNotEmpty() }
                ?.joinToString("; ")
        } catch (e: PersistenceException) {
            val resetWarning = try {
                controller.resetForImportedProject(
                    projectRuntime.state
                ).warnings
            } catch (_: Exception) {
                emptyList()
            }
            (
                listOf(
                    "Project opened, but saved session state could not be restored: " +
                        e.reason
                ) + resetWarning
                ).joinToString("; ")
        }
    }

    private fun resetImportedSession(): String? {
        val controller = sessionSidecar ?: return null
        return try {
            controller.resetForImportedProject(
                projectRuntime.state
            ).warnings
                .takeIf { it.isNotEmpty() }
                ?.joinToString("; ")
        } catch (e: PersistenceException) {
            "Imported project opened, but session state could not be reset: " +
                e.reason
        }
    }

    private fun hasUnsavedWork(): Boolean =
        state.saveStatus in setOf(
            PersistenceSaveStatus.DIRTY,
            PersistenceSaveStatus.SAVING,
            PersistenceSaveStatus.ERROR,
            PersistenceSaveStatus.CONFLICT
        )

    private fun isCurrentImport(requestId: Long): Boolean =
        synchronized(importLock) {
            requestId == importGeneration && !closed
        }

    private fun cancelImportRequests() {
        synchronized(importLock) {
            importGeneration += 1
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
            val recovered: Boolean
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

    private enum class ImportInvalidation {
        NONE,
        EDIT,
        SUPERSEDED
    }

    private sealed interface ImportResult {
        val requestId: Long

        data class Success(
            override val requestId: Long,
            val selection: DocumentTreeSelection,
            val snapshot: ProjectSnapshot,
            val token: SnapshotToken,
            val origin: DocumentTreeOrigin,
            val previousRecord: ActiveProjectRecord?
        ) : ImportResult

        data class NeedsDecision(
            override val requestId: Long,
            val selection: DocumentTreeSelection
        ) : ImportResult

        data class Failed(
            override val requestId: Long,
            val reason: PersistenceFailure,
            val message: String
        ) : ImportResult
    }
}
