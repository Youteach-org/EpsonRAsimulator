package mx.youteachtk.epsonrasimulator

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import java.io.File
import mx.youteachtk.epsonrasimulator.project.persistence.ExecutorPersistenceExecution
import mx.youteachtk.epsonrasimulator.project.persistence.ProjectPersistenceCoordinator
import mx.youteachtk.epsonrasimulator.project.persistence.SemanticSessionBridge
import mx.youteachtk.epsonrasimulator.project.persistence.android.AndroidActiveProjectRecordStore
import mx.youteachtk.epsonrasimulator.project.persistence.android.AndroidDocumentTreeGateway
import mx.youteachtk.epsonrasimulator.project.persistence.android.AndroidProjectSlotStoreFactory
import mx.youteachtk.epsonrasimulator.runtime.AppRuntimeFactory
import mx.youteachtk.epsonrasimulator.ui.AppExperienceRoot

class MainActivity : ComponentActivity() {
    private val session: AppSessionViewModel by viewModels {
        val app = applicationContext
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass.isAssignableFrom(AppSessionViewModel::class.java))
                val bundle = AppRuntimeFactory.createDefault()
                val handler = Handler(Looper.getMainLooper())
                val execution = ExecutorPersistenceExecution { block -> handler.post { block() } }
                val semanticSession = SemanticSessionBridge()
                val coordinator = ProjectPersistenceCoordinator(
                    projectRuntime = bundle.projectRuntime,
                    runtime = bundle.runtime,
                    activeRecordStore = AndroidActiveProjectRecordStore(File(app.filesDir, "persistence")),
                    slotStores = AndroidProjectSlotStoreFactory(File(app.filesDir, "projects")),
                    documentGateway = AndroidDocumentTreeGateway(app.contentResolver),
                    execution = execution,
                    semanticSession = semanticSession
                )
                @Suppress("UNCHECKED_CAST")
                return AppSessionViewModel(
                    bundle,
                    coordinator,
                    semanticSession
                ) as T
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppExperienceRoot(session = session)
                }
            }
        }
    }
}
