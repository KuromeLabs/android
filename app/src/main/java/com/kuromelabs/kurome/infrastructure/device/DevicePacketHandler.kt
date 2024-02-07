package com.kuromelabs.kurome.infrastructure.device

import Kurome.Fbs.Component
import Kurome.Fbs.DeviceQuery
import Kurome.Fbs.DeviceQueryResponse
import Kurome.Fbs.Packet
import android.os.Build
import android.os.Environment
import android.os.StatFs
import com.google.flatbuffers.FlatBufferBuilder
import com.kuromelabs.kurome.infrastructure.network.Link
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.nio.ByteBuffer

class DevicePacketHandler(
    private val link: Link,
    private val scope: CoroutineScope,
    private val identityProvider: IdentityProvider
) {
    private var handleJob: Job? = null
    private var fsAccessor = FilesystemPacketHandler(link)
    fun startHandling() {
        handleJob = scope.launch {
            link.receivedPackets.collect {
                processPacket(it)
            }
        }
    }

    fun stopHandling() {
        handleJob?.cancel()
    }

    private fun processPacket(packet: Packet) {
        when (packet.componentType)
        {
            Component.DeviceQuery -> {
                val builder = FlatBufferBuilder(256)
                val deviceQuery = packet.component(DeviceQuery()) as DeviceQuery
                val response = processDeviceQuery(builder)

                val p = Packet.createPacket(
                    builder,
                    Component.DeviceQueryResponse,
                    response,
                    packet.id
                )
                builder.finishSizePrefixed(p)
                val buffer = builder.dataBuffer()
                sendPacket(buffer)
            }
            else -> {
                fsAccessor.processFileAction(packet)
            }

        }
    }

    private fun processDeviceQuery(builder: FlatBufferBuilder): Int {
        val name = Build.MODEL
        val statFs = StatFs(Environment.getDataDirectory().path)
        return DeviceQueryResponse.createDeviceQueryResponse(
            builder,
            statFs.totalBytes,
            statFs.freeBytes,
            builder.createString(name),
            builder.createString(identityProvider.getEnvironmentId()),
            builder.createString("")
        )

    }

    private fun sendPacket(packet: ByteBuffer) {
        link.send(packet)
    }
}