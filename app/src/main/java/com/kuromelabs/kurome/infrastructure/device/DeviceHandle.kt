package com.kuromelabs.kurome.infrastructure.device

import com.kuromelabs.core.models_fbs.Packet
import com.kuromelabs.kurome.application.devices.Plugin
import com.kuromelabs.kurome.infrastructure.network.Link
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.nio.ByteBuffer
import java.security.cert.X509Certificate
import java.util.concurrent.CopyOnWriteArrayList

class DeviceHandle(
    private val isTrusted: Boolean,
    var name: String,
    val id: String,
    var certificate: X509Certificate?,
) {
    private val plugins = CopyOnWriteArrayList<Plugin>()
    var pairHandler: PairHandler = PairHandler(this, if (isTrusted) PairStatus.PAIRED else PairStatus.UNPAIRED)
    var link: Link? = null
    var localScope: CoroutineScope = CoroutineScope(Dispatchers.IO)

    fun stop() {
        link?.close()
        try {
            localScope.cancel()
        } catch (_: Exception) {}

    }

    fun sendPacket(packet: ByteBuffer) {
        link?.send(packet)
    }

    fun reloadPlugins(identityProvider: IdentityProvider) {
        if (!pairHandler.isStarted) pairHandler.start()
        plugins.forEach { it.stop() }
        plugins.clear()
        plugins.add(IdentityPacketHandlerPlugin(identityProvider, this))
        if (pairHandler.pairStatus.value == PairStatus.PAIRED){
            plugins.add(FilesystemPacketHandlerPlugin(this))
        }
        plugins.forEach { it.start() }
    }

    suspend fun getIncomingPacketWithId(id: Long, timeout: Long): Packet? = withTimeoutOrNull(timeout) {
        return@withTimeoutOrNull link!!.receivedPackets.first {
            it.isSuccess && it.value!!.id == id
        }.value
    }
}

enum class PairStatus {
    PAIRED, UNPAIRED, PAIR_REQUESTED, PAIR_REQUESTED_BY_PEER
}