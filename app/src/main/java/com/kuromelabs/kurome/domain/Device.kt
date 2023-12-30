package com.kuromelabs.kurome.domain

import Kurome.Fbs.Component
import Kurome.Fbs.DeviceQuery
import Kurome.Fbs.DeviceQueryResponse
import Kurome.Fbs.Packet
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import androidx.preference.PreferenceManager
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
import java.nio.ByteBuffer
import java.util.UUID
import javax.inject.Inject


@Entity(tableName = "device_table")
class Device(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "name") val name: String
) {
    private fun processPacket(packet: Packet) {

        if (packet.componentType == Component.DeviceQuery) {
            val builder = FlatBufferBuilder(256)
            val deviceQuery = packet.component(DeviceQuery()) as DeviceQuery
            val response = processDeviceQuery(builder, deviceQuery)

            val p = Packet.createPacket(
                builder,
                Component.DeviceQueryResponse,
                response,
                packet.id
            )
            builder.finishSizePrefixed(p)
            val buffer = builder.dataBuffer()
            sendPacket(buffer)
        } else {
            fsAccessor.processFileAction(packet)
        }
    }

    private fun processDeviceQuery(builder: FlatBufferBuilder, q: DeviceQuery): Int {
        val name = Build.MODEL
        val statFs = StatFs(Environment.getDataDirectory().path)
        val response = DeviceQueryResponse.createDeviceQueryResponse(
            builder,
            statFs.totalBytes,
            statFs.freeBytes,
            builder.createString(name),
            builder.createString(""),
            builder.createString("")
        )
        return response

    }

    @Ignore
    var link: Link? = null

    @Ignore
    var isOnline: Boolean = false

    @Ignore
    var context: Context? = null

    @Ignore
    var fsAccessor = FilesystemAccessor(this)



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

    fun disconnect() {
        link?.close()
    }

    fun sendPacket(packet: ByteBuffer) {
        link?.send(packet)
    }

}
