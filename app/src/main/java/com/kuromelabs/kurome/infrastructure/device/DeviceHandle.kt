package com.kuromelabs.kurome.infrastructure.device

import com.kuromelabs.kurome.application.devices.Plugin
import com.kuromelabs.kurome.infrastructure.network.Link
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.security.cert.X509Certificate
import java.util.concurrent.CopyOnWriteArrayList

class DeviceHandle(
    val pairStatus: PairStatus,
    val name: String,
    val id: String,
    val certificate: X509Certificate?,
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

    fun copy(
        pairStatus: PairStatus = this.pairStatus,
        name: String = this.name,
        id: String = this.id,
        certificate: X509Certificate? = this.certificate
    ): DeviceHandle {
        val copy = DeviceHandle(pairStatus, name, id, certificate)
        copy.outgoingPairRequestTimerJob = outgoingPairRequestTimerJob
        copy.plugins.addAll(plugins)
        copy.link = link
        copy.localScope = localScope
        return copy
    }

    override fun equals(other: Any?): Boolean {
        if (other is DeviceHandle) {
            return other.id == id && other.pairStatus == pairStatus && other.name == name && other.certificate == certificate
        }
        return false
    }

    override fun hashCode(): Int {
        var result = pairStatus.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + id.hashCode()
        result = 31 * result + (certificate?.hashCode() ?: 0)
        result = 31 * result + (outgoingPairRequestTimerJob?.hashCode() ?: 0)
        result = 31 * result + plugins.hashCode()
        result = 31 * result + (link?.hashCode() ?: 0)
        result = 31 * result + localScope.hashCode()
        return result
    }
}

enum class PairStatus {
    PAIRED, UNPAIRED, PAIR_REQUESTED, PAIR_REQUESTED_BY_PEER
}