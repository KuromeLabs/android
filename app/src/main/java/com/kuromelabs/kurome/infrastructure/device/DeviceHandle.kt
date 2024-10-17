package com.kuromelabs.kurome.infrastructure.device

import Kurome.Fbs.Packet
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
    var pairStatus: PairStatus,
    var name: String,
    val id: String,
    var certificate: X509Certificate?,
) {
    var outgoingPairRequestTimerJob: Job? = null
    private val plugins = CopyOnWriteArrayList<Plugin>()
    var link: Link? = null
    var localScope: CoroutineScope = CoroutineScope(Dispatchers.IO)

    fun start() {
        if (link == null) {
            throw NullPointerException("Link is null")
        }
        localScope.launch(Dispatchers.Unconfined) {
            link!!.receivedPackets
                .collect { packetResult ->
                    packetResult.onSuccess { packet ->
                        plugins.onEach { it.processPacket(packet) }
                    }
                }
        }
        link!!.start()
    }

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
        plugins.clear()
        if (pairStatus == PairStatus.PAIRED){
            plugins.add(FilesystemPacketHandlerPlugin(this))
            plugins.add(IdentityPacketHandlerPlugin(identityProvider, this))
        }
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