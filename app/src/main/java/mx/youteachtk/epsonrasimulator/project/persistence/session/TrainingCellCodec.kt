package mx.youteachtk.epsonrasimulator.project.persistence.session

import java.io.DataInputStream
import java.io.DataOutputStream
import mx.youteachtk.epsonrasimulator.domain.*
import mx.youteachtk.epsonrasimulator.kinematics.Vector3
import mx.youteachtk.epsonrasimulator.runtime.io.DigitalIoAddress
import mx.youteachtk.epsonrasimulator.runtime.training.*
import mx.youteachtk.epsonrasimulator.project.persistence.*

/** Fixed-size v3 extension; no allocation depends on an untrusted count. */
internal object TrainingCellCodec {
    fun write(data: DataOutputStream, config: TrainingCellConfig?) {
        data.writeByte(if (config == null) 0 else 1)
        if (config == null) return
        data.writeByte(1) // c4-pick-place-v1
        listOf(config.approachPick, config.pick, config.lift, config.approachPlace, config.place)
            .forEach { it.values.forEach(data::writeDouble) }
        data.writeLong(config.segmentDurationMillis)
        with(config.toolTcp) { listOf(x, y, z, rx, ry, rz).forEach(data::writeDouble) }
        listOf(config.openWidthMm, config.closedWidthMm, config.speedMmPerSecond,
            config.partSizeMm.x, config.partSizeMm.y, config.partSizeMm.z).forEach(data::writeDouble)
        data.writeInt(config.closeOutput.value)
    }

    fun read(data: DataInputStream): TrainingCellConfig? {
        when (data.readUnsignedByte()) {
            0 -> return null
            1 -> Unit
            else -> fail(PersistenceFailure.CORRUPT, "Invalid training configuration marker")
        }
        if (data.readUnsignedByte() != 1) fail(PersistenceFailure.CORRUPT, "Unsupported training preset")
        fun joints() = JointState(List(6) { data.readDouble() })
        try {
            val config = TrainingCellConfig(
                approachPick = joints(), pick = joints(), lift = joints(), approachPlace = joints(), place = joints(),
                segmentDurationMillis = data.readLong(),
                toolTcp = CartesianPose(data.readDouble(), data.readDouble(), data.readDouble(), data.readDouble(), data.readDouble(), data.readDouble()),
                openWidthMm = data.readDouble(), closedWidthMm = data.readDouble(), speedMmPerSecond = data.readDouble(),
                partSizeMm = Vector3(data.readDouble(), data.readDouble(), data.readDouble()),
                closeOutput = DigitalIoAddress(data.readInt())
            )
            return TrainingCellFactory.build(config).config
        } catch (error: IllegalArgumentException) {
            fail(PersistenceFailure.CORRUPT, "Invalid training configuration: ${error.message}")
        }
    }
}
