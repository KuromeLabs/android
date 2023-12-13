package com.kuromelabs.kurome.domain

import android.content.Context
import android.os.Environment
import android.os.StatFs
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import com.google.flatbuffers.FlatBufferBuilder
import com.kuromelabs.kurome.application.FilesystemAccessor
import com.kuromelabs.kurome.infrastructure.device.IdentityProvider
import com.kuromelabs.kurome.infrastructure.network.Link
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kurome.fbs.Component
import kurome.fbs.DeviceQuery
import kurome.fbs.DeviceQueryType
import kurome.fbs.Packet
import java.nio.ByteBuffer
import javax.inject.Inject


@Entity(tableName = "device_table")
class Device(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "name") val name: String
) {
    private fun processPacket(packet: Packet) {

        if (packet.componentType == Component.DeviceQuery) {
            val builder = FlatBufferBuilder(256)
            val deviceQuery = flatBufferHelper.getDeviceQuery(packet)
            val response = processDeviceQuery(builder, deviceQuery)

            val p = flatBufferHelper.createPacket(builder, response, Component.DeviceResponse, packet.id)
            val buffer = flatBufferHelper.finishBuilding(builder, p)
            sendPacket(buffer)
        } else {
            fsAccessor.processFileAction(packet)
        }
    }

    private fun processDeviceQuery(builder: FlatBufferBuilder, q: DeviceQuery): Int {
        var response = 0
        when (q.type) {
            DeviceQueryType.GetInfo -> response = deviceIdentityToFbs(builder)
            DeviceQueryType.GetSpace -> response = deviceSpaceToFbs(builder)
            DeviceQueryType.GetAll -> response = deviceInfoToFbs(builder)
        }
        return response
    }

    private fun deviceIdentityToFbs(builder: FlatBufferBuilder): Int {
        val id = identityProvider.getEnvironmentId()
        val name = identityProvider.getEnvironmentName()
        return flatBufferHelper.createDeviceInfoResponse(builder, id, name)
    }

    private fun deviceInfoToFbs(builder: FlatBufferBuilder): Int {
        val id = identityProvider.getEnvironmentId()
        val name = identityProvider.getEnvironmentName()
        val statFs = StatFs(Environment.getDataDirectory().path)
        return flatBufferHelper.createDeviceInfoResponse(
            builder,
            id,
            name,
            statFs.totalBytes,
            statFs.freeBytes
        )
    }

    private fun deviceSpaceToFbs(builder: FlatBufferBuilder): Int {
        val statFs = StatFs(Environment.getDataDirectory().path)
        return flatBufferHelper.createDeviceInfoResponse(
            builder,
            totalSpace = statFs.totalBytes,
            freeSpace = statFs.freeBytes
        )
    }

    @Ignore
    var link: Link? = null

    @Ignore
    var isOnline: Boolean = false

    @Ignore
    var context: Context? = null


    @Ignore
    var fsAccessor = FilesystemAccessor(this)

    @Ignore @Inject lateinit var identityProvider: IdentityProvider

    fun connect(link: Link, scope: CoroutineScope) {
        this.link = link
        val packetJob = scope.launch {
            link.receivedPackets.collect {
                processPacket(it)
            }
        }
        scope.launch {
            link.isConnected.collect {
                isOnline = it
                if (!it) {
                    packetJob.cancel()
                    currentCoroutineContext().job.cancel()
                }
            }
        }
    }

    fun sendPacket(packet: ByteBuffer) {
        link?.send(packet)
    }

}
