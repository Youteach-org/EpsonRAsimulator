package mx.youteachtk.epsonrasimulator.ui

import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import mx.youteachtk.epsonrasimulator.ui.visual.tcp.CartesianAxisGuide
import mx.youteachtk.epsonrasimulator.runtime.workcell.SchoolEnclosure
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.github.sceneview.SceneView
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.math.Size
import io.github.sceneview.math.Scale
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
    axisGuide: mx.youteachtk.epsonrasimulator.ui.visual.tcp.CartesianAxisGuide? = null,
    axisGuides: List<CartesianAxisGuide> = emptyList(),
    showJointLabels: Boolean = false,
    activeJoint: Int = -1,
    collision: mx.youteachtk.epsonrasimulator.runtime.motion.CollisionStop? = null,
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
    val frames=C4Kinematics.forward(jointValues.map(Float::toDouble)).baseToJointFrames
    val collisionIds=listOfNotNull(collision?.firstId,collision?.secondId)
    val collisionMarks=collisionIds.mapNotNull {id->
        val joint=id.removePrefix("robot:J").toIntOrNull()
        val box=workcellBoxes.firstOrNull {it.id==id}
        val p=when {
            joint!=null -> frames.getOrNull(joint-1)?.translation
            id=="robot:base" -> Vector3.ZERO
            box!=null -> Vector3(box.centerMeters.x.toDouble()*1000,box.centerMeters.y.toDouble()*1000,box.centerMeters.z.toDouble()*1000)
            else -> null
        }
        p?.let {id to it}
    }
    val latestCollisions by rememberUpdatedState(collisionMarks)
    var projectedCollisions by remember {mutableStateOf<List<Pair<String,Offset>>>(emptyList())}
    val latestGuides by rememberUpdatedState(axisGuides.ifEmpty {listOfNotNull(axisGuide)})
    var projectedGuides by remember {mutableStateOf<List<Pair<CartesianAxisGuide,List<Offset?>>>>(emptyList())}
    var projectedLabels by remember {mutableStateOf<List<Pair<String,Offset>>>(emptyList())}
    val labelPaint=remember {android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {typeface=android.graphics.Typeface.DEFAULT_BOLD}}
    val glassLabels=workcellBoxes.firstOrNull {it.id==SchoolEnclosure.id.value}?.let {box->
        val low=SchoolEnclosure.minimum; val high=SchoolEnclosure.maximum; val middle=SchoolEnclosure.center
        val transform=Matrix4.translation(Vector3(box.centerMeters.x*1000.0,box.centerMeters.y*1000.0,box.centerMeters.z*1000.0))*
            Matrix4.rotation(Vector3(0.0,0.0,1.0),box.rotationDegrees.z.toDouble())*
            Matrix4.rotation(Vector3(0.0,1.0,0.0),box.rotationDegrees.y.toDouble())*
            Matrix4.rotation(Vector3(1.0,0.0,0.0),box.rotationDegrees.x.toDouble())*
            Matrix4.translation(Vector3(box.modelOffsetMeters.x*1000.0,box.modelOffsetMeters.y*1000.0,box.modelOffsetMeters.z*1000.0))
        listOf("+X" to Vector3(high.x-20,middle.y,middle.z),"-X" to Vector3(low.x+20,middle.y,middle.z),
            "+Y" to Vector3(0.0,middle.y,low.z+20),"-Y" to Vector3(0.0,middle.y,high.z-20),
            "+Z" to Vector3(0.0,high.y-45,high.z-20),"-Z" to Vector3(0.0,low.y+45,high.z-20))
            .map {(label,point)->label to transform.transformPoint(point*box.modelScale.toDouble())}
    }.orEmpty()
    val latestLabels by rememberUpdatedState(glassLabels)
    val targetMaterial = remember(materialLoader) { materialLoader.createUnlitColorInstance(Color(0xFFFFC107)) }
    val partMaterial = remember(materialLoader) { materialLoader.createUnlitColorInstance(Color(0xFFFF9F1C)) }
    val toolMaterial = remember(materialLoader) { materialLoader.createUnlitColorInstance(Color(0xFF00BCD4)) }
    val fixtureMaterial = remember(materialLoader) { materialLoader.createUnlitColorInstance(Color(0xFF607D8B)) }
    val eye = Position(0f, 0.65f, -1.65f)
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

    val stickers=(1..6).map {rememberModelInstance(modelLoader,"$MODEL_ROOT/J${it}_sticker.glb")}
    val selectedStickers=(1..6).map {rememberModelInstance(modelLoader,"$MODEL_ROOT/J${it}_sticker_active.glb")}
    val otherStickers=(1..6).map {rememberModelInstance(modelLoader,"$MODEL_ROOT/J${it}_sticker_other.glb")}
    val otherSelectedStickers=(1..6).map {rememberModelInstance(modelLoader,"$MODEL_ROOT/J${it}_sticker_other_active.glb")}
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
            onFrame = {
                if (loaded && presentedFrames < 3) presentedFrames++
                fun project(point: Vector3?): Offset? {
                    if(point==null || camera.viewport==null) return null
                    val p=camera.worldToScreenPoint(io.github.sceneview.collision.Vector3((point.x/1000).toFloat(),(point.y/1000).toFloat(),(point.z/1000).toFloat()))
                    return if(p.x.isFinite() && p.y.isFinite() && p.z>=0f) Offset(p.x,p.y) else null
                }
                projectedGuides=latestGuides.map {g->
                    val marks=listOf(g.start,g.end,g.target).map(::project)
                    g to (marks+if(marks[0]==null || marks[1]==null) g.dots().map(::project) else emptyList())
                }
                projectedCollisions=latestCollisions.mapNotNull {(text,point)->project(point)?.let {text to it}}
                projectedLabels=latestLabels.mapNotNull {(text,point)->project(point)?.let {text to it}}

            }
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
                    if(showJointLabels) (if(activeJoint==0) selectedStickers[0] else stickers[0])?.let {sticker->
                        ModelNode(modelInstance=sticker,autoAnimate=false)
                    }
                    if(showJointLabels) (if(activeJoint==0) otherSelectedStickers[0] else otherStickers[0])?.let {sticker->
                        ModelNode(modelInstance=sticker,autoAnimate=false)
                    }
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
                    if(showJointLabels) (if(activeJoint==1) selectedStickers[1] else stickers[1])?.let {sticker->
                        ModelNode(modelInstance=sticker,autoAnimate=false)
                    }
                    if(showJointLabels) (if(activeJoint==1) otherSelectedStickers[1] else otherStickers[1])?.let {sticker->
                        ModelNode(modelInstance=sticker,autoAnimate=false)
                    }
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
                    if(showJointLabels) (if(activeJoint==2) selectedStickers[2] else stickers[2])?.let {sticker->
                        ModelNode(modelInstance=sticker,autoAnimate=false)
                    }
                    if(showJointLabels) (if(activeJoint==2) otherSelectedStickers[2] else otherStickers[2])?.let {sticker->
                        ModelNode(modelInstance=sticker,autoAnimate=false)
                    }
                        }

                        Node(
                            rotation = Rotation(z = jointValues[3])
                        ) {
                            j4?.let { instance ->
                                ModelNode(
                                    modelInstance = instance,
                                    autoAnimate = false
                                )
                    if(showJointLabels) (if(activeJoint==3) selectedStickers[3] else stickers[3])?.let {sticker->
                        ModelNode(modelInstance=sticker,autoAnimate=false)
                    }
                    if(showJointLabels) (if(activeJoint==3) otherSelectedStickers[3] else otherStickers[3])?.let {sticker->
                        ModelNode(modelInstance=sticker,autoAnimate=false)
                    }
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
                    if(showJointLabels) (if(activeJoint==4) selectedStickers[4] else stickers[4])?.let {sticker->
                        ModelNode(modelInstance=sticker,autoAnimate=false)
                    }
                    if(showJointLabels) (if(activeJoint==4) otherSelectedStickers[4] else otherStickers[4])?.let {sticker->
                        ModelNode(modelInstance=sticker,autoAnimate=false)
                    }
                                }

                                Node(
                                    rotation = Rotation(z = jointValues[5])
                                ) {
                                    j6?.let { instance ->
                                        ModelNode(
                                            modelInstance = instance,
                                            autoAnimate = false
                                        )
                    if(showJointLabels) (if(activeJoint==5) selectedStickers[5] else stickers[5])?.let {sticker->
                        ModelNode(modelInstance=sticker,autoAnimate=false)
                    }
                    if(showJointLabels) (if(activeJoint==5) otherSelectedStickers[5] else otherStickers[5])?.let {sticker->
                        ModelNode(modelInstance=sticker,autoAnimate=false)
                    }
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
                key(box.id) {
                val mesh = box.modelAsset?.let { rememberModelInstance(modelLoader, it) }
                // Explicit nesting preserves the simulation convention Rz * Ry * Rx.
                Node(
                    position = Position(box.centerMeters.x, box.centerMeters.y, box.centerMeters.z),
                    rotation = Rotation(z = box.rotationDegrees.z)
                ) {
                    Node(rotation = Rotation(y = box.rotationDegrees.y)) {
                        if(box.modelAsset != null) {
                            Node(rotation=Rotation(x=box.rotationDegrees.x)) {
                                mesh?.let { instance ->
                                    ModelNode(modelInstance=instance, autoAnimate=false,
                                        position=Position(box.modelOffsetMeters.x,box.modelOffsetMeters.y,box.modelOffsetMeters.z),
                                        scale=Scale(box.modelScale))
                                }
                            }
                        } else {
                        CubeNode(
                            size = Size(box.sizeMeters.x, box.sizeMeters.y, box.sizeMeters.z),
                            rotation = Rotation(x = box.rotationDegrees.x),
                            materialInstance = when {
                                box.id.startsWith("tool:") -> toolMaterial
                                box.kind == mx.youteachtk.epsonrasimulator.runtime.workcell.WorkcellRenderKind.PART -> partMaterial
                                else -> fixtureMaterial
                            }
                        )
                        }
                    }
                }
                }
            }
        }

        // Passive overlay: camera gestures pass through to SceneView, including during slider drags.
        Canvas(Modifier.fillMaxSize()) {
            fun color(axis: Int)=when(axis) {0->Color(0xFFFF7777);1->Color(0xFF70D69A);else->Color(0xFF65B8FF)}
            fun label(text: String,p: Offset,tint: Color) {
                labelPaint.textSize=12.dp.toPx()
                labelPaint.style=android.graphics.Paint.Style.FILL
                val x=p.x+8.dp.toPx();val y=p.y-8.dp.toPx()
                val width=labelPaint.measureText(text)
                labelPaint.color=android.graphics.Color.argb(215,10,22,32)
                drawContext.canvas.nativeCanvas.drawRoundRect(x-5.dp.toPx(),y-14.dp.toPx(),x+width+5.dp.toPx(),y+4.dp.toPx(),5.dp.toPx(),5.dp.toPx(),labelPaint)
                labelPaint.color=tint.toArgb()
                drawContext.canvas.nativeCanvas.drawText(text,x,y,labelPaint)
            }
            clipRect {
                projectedGuides.forEach {(g,marks)->
                    val start=marks[0];val end=marks[1];val target=marks[2]
                    val tint=if(g.safeStart==null) Color(0xFF9CA7AF) else color(g.axis)
                    if(start!=null && end!=null) {
                        drawLine(Color.Black,start,end,strokeWidth=7.dp.toPx())
                        drawLine(tint,start,end,strokeWidth=3.dp.toPx(),pathEffect=PathEffect.dashPathEffect(floatArrayOf(12.dp.toPx(),7.dp.toPx())))
                    } else {
                        val visible=Path();var connected=false
                        marks.drop(3).forEach {p->if(p==null) connected=false else {
                            if(connected) visible.lineTo(p.x,p.y) else visible.moveTo(p.x,p.y)
                            connected=true
                        }}
                        drawPath(visible,Color.Black,style=Stroke(width=7.dp.toPx()))
                        drawPath(visible,tint,style=Stroke(width=3.dp.toPx(),pathEffect=PathEffect.dashPathEffect(floatArrayOf(12.dp.toPx(),7.dp.toPx()))))
                    }
                    listOf(start,end).forEachIndexed {i,p->p?.let {
                        drawCircle(Color.Black,8.dp.toPx(),it);drawCircle(tint,5.dp.toPx(),it)
                        label((if(i==0) "-" else "+")+listOf("X","Y","Z")[g.axis],it,tint)
                    }}
                    target?.let {drawCircle(Color.Black,12.dp.toPx(),it);drawCircle(tint,10.dp.toPx(),it);drawCircle(Color.White,7.dp.toPx(),it)}
                }
                projectedCollisions.forEach {(text,p)->
                    drawCircle(Color.Red.copy(alpha=0.25f),28.dp.toPx(),p)
                    drawCircle(Color.Red,28.dp.toPx(),p,style=Stroke(3.dp.toPx()))
                    label("Choque",p,Color(0xFFFF6666))
                }
                projectedLabels.forEach {(text,p)->label(text,p,color(when(text.last()) {'X'->0;'Y'->1;else->2}))}
            }
        }

        if (!loaded) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}
