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
        draft.steps.forEach { step ->
            text(step.id)
            when(val action=step.action) {
                is UserSequenceAction.Move -> { data.writeByte(0);text(action.pointName);data.writeLong(action.durationMillis) }
                is UserSequenceAction.Gripper -> { data.writeByte(1);data.writeByte(if(action.closed) 1 else 0) }
                is UserSequenceAction.Wait -> { data.writeByte(2);data.writeLong(action.durationMillis) }
            }
        }
    }
    fun read(data: DataInputStream): UserTrainingSequence {
        fun text(): String {
            val count=data.readInt()
            if(count !in 1..256 || count>data.available()) fail(PersistenceFailure.CORRUPT,"Invalid sequence text length")
            return strictText(ByteArray(count).also { data.readFully(it) })
        }
        val count=data.readInt()
        if(count !in 0..100) fail(PersistenceFailure.CORRUPT,"Invalid sequence step count")
        try {
            return UserTrainingSequence(List(count) {
                val id=text()
                val action=when(data.readUnsignedByte()) {
                    0 -> UserSequenceAction.Move(text(),data.readLong())
                    1 -> UserSequenceAction.Gripper(when(data.readUnsignedByte()) {
                        0 -> false;1 -> true;else -> fail(PersistenceFailure.CORRUPT,"Invalid gripper marker")
                    })
                    2 -> UserSequenceAction.Wait(data.readLong())
                    else -> fail(PersistenceFailure.CORRUPT,"Unknown sequence action")
                }
                UserSequenceStep(id,action)
            })
        } catch(failure: IllegalArgumentException) {
            fail(PersistenceFailure.CORRUPT,"Invalid sequence: ${failure.message}")
        }
    }
}
