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
    private var applyingOwnedProject = false
    private var currentToken: SnapshotToken? = null
    private var currentRevision = 0L
    private var pendingSnapshot: ProjectSnapshot? = null
    private var debounce: PersistenceCancellation? = null
    private var saveInFlight = false
    private var activeRecordPublished = false
    private var pendingImport: DocumentTreeSelection? = null
    private var importAfterSave = false

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
                pendingSnapshot = null
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
        synchronized(importLock) {
            importGeneration++
        }
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

        currentRevision += 1
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
        val previousOrigin = state.origin

        execution.execute {
            if (!isCurrentImport(requestId)) return@execute
            val result = try {
                val source = DocumentTreeProjectSource(selection, documentGateway)
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
                    val save = slotStores.open(newProjectId).save(snapshot, null)
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
                try {
                    synchronized(importLock) {
                        if (requestId != importGeneration) {
                            try {
                                documentGateway.release(newOrigin)
                            } catch (_: Exception) {
                            }
                            return@execute
                        }
                        activeRecordStore.write(
                            ActiveProjectRecord(newProjectId, newOrigin)
                        )
                    }
                } catch (e: Exception) {
                    try {
                        documentGateway.release(newOrigin)
                    } catch (_: Exception) {
                    }
                    throw e
                }

                var warning: String? = null
                if (
                    previousOrigin != null &&
                    previousOrigin.uri != newOrigin.uri
                ) {
                    try {
                        documentGateway.release(previousOrigin)
                    } catch (_: Exception) {
                        warning =
                            "New project opened, but the previous folder permission could not be released"
                    }
                }

                ImportResult.Success(
                    requestId,
                    snapshot,
                    saved.token,
                    newOrigin,
                    warning
                )
            } catch (e: PersistenceException) {
                ImportResult.Failed(requestId, e.reason, "Import failed: ${e.reason}")
            } catch (_: IOException) {
                ImportResult.Failed(requestId, PersistenceFailure.IO, "Import failed: IO")
            } catch (_: SecurityException) {
                ImportResult.Failed(requestId, PersistenceFailure.IO, "Import failed: IO")
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
                try {
                    applyingOwnedProject = true
                    projectRuntime.loadProject(
                        result.snapshot.projectName,
                        result.snapshot.exportResources()
                    )
                } finally {
                    applyingOwnedProject = false
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
                        message = result.warning,
                        replacementDecisionRequired = false,
                        lastExport = null
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
            resources = projectRuntime.export()
        )

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

    private sealed interface ImportResult {
        val requestId: Long

        data class Success(
            override val requestId: Long,
            val snapshot: ProjectSnapshot,
            val token: SnapshotToken,
            val origin: DocumentTreeOrigin,
            val warning: String?
        ) : ImportResult

        data class Failed(
            override val requestId: Long,
            val reason: PersistenceFailure,
            val message: String
        ) : ImportResult
    }
}
