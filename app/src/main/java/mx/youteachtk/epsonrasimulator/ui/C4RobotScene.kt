package mx.youteachtk.epsonrasimulator.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
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
import io.github.sceneview.rememberMaterialLoader
import io.github.sceneview.rememberCameraNode
import mx.youteachtk.epsonrasimulator.domain.CartesianPose
import mx.youteachtk.epsonrasimulator.domain.JointState
import mx.youteachtk.epsonrasimulator.kinematics.*
import kotlin.math.ceil

private const val MODEL_ROOT = "models/robots/c4-a601s"

@Composable
fun C4RobotScene(
    jointValues: List<Float>,
    modifier: Modifier = Modifier,
    workcellBoxes: List<WorkcellSceneBox> = emptyList(),
    previewJoints: JointState? = null,
    toolTcp: CartesianPose = CartesianPose(0.0, 0.0, 0.0),
    targetCadMm: Vector3? = null,
    cameraEnabled: Boolean = true
) {
    require(jointValues.size == 6) {
        "C4RobotScene requires exactly six joint values"
    }

    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val materialLoader = rememberMaterialLoader(engine)
    val previewMaterial = remember(materialLoader) {
        materialLoader.createUnlitColorInstance(Color.Cyan).apply {
            // Preview is an overlay: the current robot must not hide its skeleton.
            setDepthCulling(false)
            setDepthWrite(false)
        }
    }
    val targetMaterial = remember(materialLoader) { materialLoader.createUnlitColorInstance(Color(0xFFFFC107)) }
    val eye = Position(1.1f, 0.8f, 1.1f)
    val center = Position(0f, 0.3f, -0.15f)
    val camera = rememberCameraNode(engine) { position = eye; lookAt(center) }
    val manipulator = rememberCameraManipulator(orbitHomePosition = eye, targetPosition = center)

    val base = rememberModelInstance(modelLoader, "$MODEL_ROOT/C4_BASE.glb")
    val j1 = rememberModelInstance(modelLoader, "$MODEL_ROOT/C4_J1.glb")
    val j2 = rememberModelInstance(modelLoader, "$MODEL_ROOT/C4_J2.glb")
    val j3 = rememberModelInstance(modelLoader, "$MODEL_ROOT/C4_J3.glb")
    val j4 = rememberModelInstance(modelLoader, "$MODEL_ROOT/C4_J4.glb")
    val j5 = rememberModelInstance(modelLoader, "$MODEL_ROOT/C4_J5.glb")
    val j6 = rememberModelInstance(modelLoader, "$MODEL_ROOT/C4_J6.glb")

    val loaded = listOf(base, j1, j2, j3, j4, j5, j6).all { it != null }
    var presentedFrames by remember(loaded, jointValues, previewJoints, targetCadMm) { mutableIntStateOf(0) }
    val ghostPoints = remember(previewJoints, toolTcp) {
        previewJoints?.let { q ->
            val fk = C4Kinematics.forward(q.values)
            val joints = fk.baseToJointFrames.map { it.translation } +
                (fk.baseToTcp * SimulationPoseTransforms.fromPose(toolTcp)).translation
            buildList {
                joints.zipWithNext().forEach { (a, b) ->
                    val d = b - a
                    val steps = ceil(d.length / 25.0).toInt().coerceAtLeast(1)
                    for (i in 0..steps) {
                        val t = i.toDouble() / steps
                        add(Vector3(a.x + d.x * t, a.y + d.y * t, a.z + d.z * t))
                    }
                }
            }
        } ?: emptyList()
    }

    Box(modifier = modifier.testTag(if (loaded && presentedFrames >= 3) "c4-scene-ready" else "c4-scene-loading")) {
        SceneView(
            modifier = Modifier.fillMaxSize(),
            engine = engine,
            modelLoader = modelLoader,
            materialLoader = materialLoader,
            autoCenterContent = false,
            cameraNode = camera,
            cameraManipulator = if (cameraEnabled) manipulator else null,
            onFrame = { if (loaded && presentedFrames < 3) presentedFrames++ }
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

            ghostPoints.forEach { point ->
                SphereNode(radius = 0.007f, stacks = 8, slices = 12,
                    position = Position((point.x / 1000).toFloat(), (point.y / 1000).toFloat(), (point.z / 1000).toFloat()),
                    materialInstance = previewMaterial,
                    apply = { setPriority(7); isShadowCaster = false; isShadowReceiver = false })
            }
            targetCadMm?.takeIf { listOf(it.x, it.y, it.z).all { v -> v.isFinite() && kotlin.math.abs(v) <= 5000.0 } }?.let { point ->
                SphereNode(radius = 0.014f, stacks = 12, slices = 16,
                    position = Position((point.x / 1000).toFloat(), (point.y / 1000).toFloat(), (point.z / 1000).toFloat()),
                    materialInstance = targetMaterial)
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
