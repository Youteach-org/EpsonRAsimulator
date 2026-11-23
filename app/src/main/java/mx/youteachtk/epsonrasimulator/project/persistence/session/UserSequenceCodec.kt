package mx.youteachtk.epsonrasimulator.project.persistence.session

import java.io.DataInputStream
import java.io.DataOutputStream
import mx.youteachtk.epsonrasimulator.project.persistence.*
import mx.youteachtk.epsonrasimulator.runtime.training.*

/** v4 bounded draft extension. No execution state or untrusted allocations. */
internal object UserSequenceCodec {
    fun write(data: DataOutputStream, sequence: UserTrainingSequence) {
        val draft=sequence.snapshot()
        fun text(value: String) {
            val bytes=utf8(value,PersistenceFailure.INVALID_METADATA)
            require(bytes.size in 1..256)
            data.writeInt(bytes.size);data.write(bytes)
        }
        data.writeInt(draft.steps.size)
        data.writeInt(draft.speedPercent)
        draft.steps.forEach { step ->
            text(step.id)
            when(val action=step.action) {
                is UserSequenceAction.Move -> { data.writeByte(if(action.fixedJ6Degrees!=null) 5 else if(action.fixedOrientation) 4 else 0);text(action.pointName);data.writeLong(action.durationMillis); action.fixedJ6Degrees?.let(data::writeDouble) }
                is UserSequenceAction.Gripper -> {
                    data.writeByte(if(action.openingMm==null) 1 else 3);data.writeByte(if(action.closed) 1 else 0)
                    action.openingMm?.let(data::writeDouble)
                }
                is UserSequenceAction.Wait -> { data.writeByte(2);data.writeLong(action.durationMillis) }
            }
        }
    }
    fun read(data: DataInputStream, schema: Int = 8): UserTrainingSequence {
        fun text(): String {
            val count=data.readInt()
            if(count !in 1..256 || count>data.available()) fail(PersistenceFailure.CORRUPT,"Invalid sequence text length")
            return strictText(ByteArray(count).also { data.readFully(it) })
        }
        val count=data.readInt()
        if(count !in 0..100) fail(PersistenceFailure.CORRUPT,"Invalid sequence step count")
        val speed=if(schema>=5) data.readInt() else 100
        try {
            return UserTrainingSequence(List(count) {
                val id=text()
                val action=when(data.readUnsignedByte()) {
                    0 -> UserSequenceAction.Move(text(),data.readLong())
                    5 -> {
                        if(schema<8) fail(PersistenceFailure.CORRUPT,"Unsupported fixed J6 move")
                        UserSequenceAction.Move(text(),data.readLong(),true,data.readDouble())
                    }
                    4 -> {
                        if(schema<7) fail(PersistenceFailure.CORRUPT,"Unsupported fixed orientation move")
                        UserSequenceAction.Move(text(),data.readLong(),fixedOrientation=true)
                    }
                    1 -> UserSequenceAction.Gripper(when(data.readUnsignedByte()) {
                        0 -> false;1 -> true;else -> fail(PersistenceFailure.CORRUPT,"Invalid gripper marker")
                    })
                    3 -> {
                        if(schema<5) fail(PersistenceFailure.CORRUPT,"Unsupported aperture action")
                        val closed=when(data.readUnsignedByte()) {0->false;1->true;else->fail(PersistenceFailure.CORRUPT,"Invalid gripper marker")}
                        UserSequenceAction.Gripper(closed,data.readDouble())
                    }
                    2 -> UserSequenceAction.Wait(data.readLong())
                    else -> fail(PersistenceFailure.CORRUPT,"Unknown sequence action")
                }
                UserSequenceStep(id,action)
            },speed)
        } catch(failure: IllegalArgumentException) {
            fail(PersistenceFailure.CORRUPT,"Invalid sequence: ${failure.message}")
        }
    }
}
