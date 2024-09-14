package com.kuromelabs.kurome.infrastructure.device

import Kurome.Fbs.Component
import Kurome.Fbs.DeviceIdentityResponse
import Kurome.Fbs.Packet
import Kurome.Fbs.Platform
import android.os.Build
import android.os.Environment
import android.os.StatFs
import com.google.flatbuffers.FlatBufferBuilder
import com.kuromelabs.kurome.application.devices.Plugin

class IdentityPacketHandlerPlugin (private val identityProvider: IdentityProvider, private val handle: DeviceHandle): Plugin {

    val builder = FlatBufferBuilder(256)
    override fun processPacket(packet: Packet) {
        if (packet.componentType != Component.DeviceIdentityQuery) return
        val response = processDeviceQuery()
        val p = Packet.createPacket(
            builder,
            Component.DeviceIdentityResponse,
            response,
            packet.id
        )
        builder.finishSizePrefixed(p)
        val buffer = builder.dataBuffer()
        handle.sendPacket(buffer)
    }

    private fun processDeviceQuery(): Int {
        val name = Build.MODEL
        val statFs = StatFs(Environment.getDataDirectory().path)
        return DeviceIdentityResponse.createDeviceIdentityResponse(
            builder,
            statFs.totalBytes,
            statFs.freeBytes,
            builder.createString(name),
            builder.createString(identityProvider.getEnvironmentId()),
            builder.createString(""),
            Platform.Android
        )

    }
}