package mx.youteachtk.epsonrasimulator.bridge

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.security.MessageDigest
import mx.youteachtk.epsonrasimulator.project.persistence.PersistenceException
import mx.youteachtk.epsonrasimulator.project.persistence.PersistenceLimits
import mx.youteachtk.epsonrasimulator.project.persistence.ProjectSnapshot

enum class BridgeProjectResult {
    APPLIED,
    CONFLICT,
    INVALID
}

/**
 * Independent in-memory project resource endpoint.
 *
 * It owns exact detached native resource bytes only. Live bridge state, local
 * project revision and persistence sidecar authority are intentionally absent.
 */
class BridgeProjectEndpoint(
    initial: ProjectSnapshot,
    private val limits: PersistenceLimits = PersistenceLimits()
) {
    private val projectId: String = initial.projectId
    private val adapterId: String = initial.adapterId
    private val robotId: String = initial.robotId
    private var currentResources: Map<String, ByteArray>

    init {
        require(initial.sidecarBytes().isEmpty()) {
            "Bridge project endpoint does not accept application sidecar data"
        }
        currentResources = revalidate(initial).exportResources()
    }

    @Synchronized
    fun fingerprint(): String =
        digest(currentResources)

    @Synchronized
    fun resources(): Map<String, ByteArray> =
        currentResources.mapValues { (_, bytes) -> bytes.copyOf() }

    @Synchronized
    fun replace(
        expectedFingerprint: String,
        candidate: ProjectSnapshot
    ): BridgeProjectResult {
        if (
            candidate.sidecarBytes().isNotEmpty() ||
            candidate.projectId != projectId ||
            candidate.adapterId != adapterId ||
            candidate.robotId != robotId
        ) {
            return BridgeProjectResult.INVALID
        }

        val validated = try {
            revalidate(candidate)
        } catch (_: PersistenceException) {
            return BridgeProjectResult.INVALID
        } catch (_: IllegalArgumentException) {
            return BridgeProjectResult.INVALID
        }

        if (expectedFingerprint != digest(currentResources)) {
            return BridgeProjectResult.CONFLICT
        }

        currentResources = validated.exportResources()
        return BridgeProjectResult.APPLIED
    }

    private fun revalidate(snapshot: ProjectSnapshot): ProjectSnapshot =
        ProjectSnapshot(
            projectId = snapshot.projectId,
            projectName = snapshot.projectName,
            adapterId = snapshot.adapterId,
            robotId = snapshot.robotId,
            revision = snapshot.revision,
            resources = snapshot.exportResources(),
            sidecar = snapshot.sidecarBytes(),
            sidecarVersion = snapshot.sidecarVersion,
            limits = limits
        )

    private fun digest(resources: Map<String, ByteArray>): String {
        val output = ByteArrayOutputStream()
        DataOutputStream(output).use { data ->
            resources.keys
                .sortedWith(UNSIGNED_UTF8_PATH_ORDER)
                .forEach { path ->
                    val pathBytes = path.toByteArray(Charsets.UTF_8)
                    val content = resources.getValue(path)
                    data.writeInt(pathBytes.size)
                    data.write(pathBytes)
                    data.writeLong(content.size.toLong())
                    data.write(content)
                }
        }

        return MessageDigest.getInstance("SHA-256")
            .digest(output.toByteArray())
            .joinToString("") {
                "%02x".format(it.toInt() and 0xff)
            }
    }

    companion object {
        private val UNSIGNED_UTF8_PATH_ORDER =
            Comparator<String> { left, right ->
                compareUnsigned(
                    left.toByteArray(Charsets.UTF_8),
                    right.toByteArray(Charsets.UTF_8)
                )
            }

        private fun compareUnsigned(
            left: ByteArray,
            right: ByteArray
        ): Int {
            val common = minOf(left.size, right.size)
            for (index in 0 until common) {
                val l = left[index].toInt() and 0xff
                val r = right[index].toInt() and 0xff
                if (l != r) return l.compareTo(r)
            }
            return left.size.compareTo(right.size)
        }
    }
}
