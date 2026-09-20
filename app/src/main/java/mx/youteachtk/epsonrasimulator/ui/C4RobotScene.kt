package mx.youteachtk.epsonrasimulator.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.github.sceneview.SceneView
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.math.Size
import io.github.sceneview.node.CubeNode
import io.github.sceneview.node.ModelNode
import io.github.sceneview.node.Node
import io.github.sceneview.rememberCameraManipulator
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberModelInstance
import io.github.sceneview.rememberModelLoader

private const val MODEL_ROOT = "models/robots/c4-a601s"

@Composable
fun C4RobotScene(
    jointValues: List<Float>,
    modifier: Modifier = Modifier,
    workcellBoxes: List<WorkcellSceneBox> = emptyList()
) {
    require(jointValues.size == 6) {
        "C4RobotScene requires exactly six joint values"
    }

    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)

    val base = rememberModelInstance(modelLoader, "$MODEL_ROOT/C4_BASE.glb")
    val j1 = rememberModelInstance(modelLoader, "$MODEL_ROOT/C4_J1.glb")
    val j2 = rememberModelInstance(modelLoader, "$MODEL_ROOT/C4_J2.glb")
    val j3 = rememberModelInstance(modelLoader, "$MODEL_ROOT/C4_J3.glb")
    val j4 = rememberModelInstance(modelLoader, "$MODEL_ROOT/C4_J4.glb")
    val j5 = rememberModelInstance(modelLoader, "$MODEL_ROOT/C4_J5.glb")
    val j6 = rememberModelInstance(modelLoader, "$MODEL_ROOT/C4_J6.glb")

    val loaded = listOf(base, j1, j2, j3, j4, j5, j6).all { it != null }

    Box(modifier = modifier) {
        SceneView(
            modifier = Modifier.fillMaxSize(),
            engine = engine,
            modelLoader = modelLoader,
            cameraManipulator = rememberCameraManipulator()
        ) {
            base?.let { instance ->
                ModelNode(
                    modelInstance = instance,
                    autoAnimate = false
                )
            }

            Node(
                rotation = Rotation(y = jointValues[0])
            ) {
                j1?.let { instance ->
                    ModelNode(
                        modelInstance = instance,
                        autoAnimate = false
                    )
                }

                Node(
                    position = Position(
                        x = 0.0f,
                        y = 0.320f,
                        z = -0.100f
                    ),
                    rotation = Rotation(x = jointValues[1])
                ) {
                    j2?.let { instance ->
                        ModelNode(
                            modelInstance = instance,
                            autoAnimate = false
                        )
                    }

                    Node(
                        position = Position(
                            x = 0.0f,
                            y = 0.250f,
                            z = 0.0f
                        ),
                        rotation = Rotation(x = jointValues[2])
                    ) {
                        j3?.let { instance ->
                            ModelNode(
                                modelInstance = instance,
                                autoAnimate = false
                            )
                        }

                        Node(
                            rotation = Rotation(z = jointValues[3])
                        ) {
                            j4?.let { instance ->
                                ModelNode(
                                    modelInstance = instance,
                                    autoAnimate = false
                                )
                            }

                            Node(
                                position = Position(
                                    x = 0.0f,
                                    y = 0.0f,
                                    z = -0.250f
                                ),
                                rotation = Rotation(x = jointValues[4])
                            ) {
                                j5?.let { instance ->
                                    ModelNode(
                                        modelInstance = instance,
                                        autoAnimate = false
                                    )
                                }

                                Node(
                                    rotation = Rotation(z = jointValues[5])
                                ) {
                                    j6?.let { instance ->
                                        ModelNode(
                                            modelInstance = instance,
                                            autoAnimate = false
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            workcellBoxes.forEach { box ->
                CubeNode(
                    size = Size(
                        x = box.sizeMeters.x,
                        y = box.sizeMeters.y,
                        z = box.sizeMeters.z
                    ),
                    position = Position(
                        x = box.centerMeters.x,
                        y = box.centerMeters.y,
                        z = box.centerMeters.z
                    )
                )
            }
        }

        if (!loaded) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}
