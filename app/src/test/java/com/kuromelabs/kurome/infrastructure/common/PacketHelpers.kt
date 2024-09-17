package com.kuromelabs.kurome.infrastructure.common

import Kurome.Fbs.Component
import Kurome.Fbs.DeviceIdentityResponse
import Kurome.Fbs.Packet
import Kurome.Fbs.Pair
import Kurome.Fbs.Platform
import com.google.flatbuffers.FlatBufferBuilder
import java.nio.ByteBuffer

class PacketHelpers {
    companion object {
        fun getWindowsDeviceIdentityResponse(id: String): DeviceIdentityResponse {
            val builder = FlatBufferBuilder(256)
            val packet = Packet.createPacket(
                builder,
                Component.DeviceIdentityResponse,
                DeviceIdentityResponse.createDeviceIdentityResponse(
                    builder, 0, 0,
                    builder.createString(id),
                    builder.createString("test"),
                    builder.createString("127.0.0.1"),
                    Platform.Windows,
                    33587u
                ),
                1
            )
            builder.finish(packet)
            return Packet.getRootAsPacket(builder.dataBuffer()).component(DeviceIdentityResponse()) as DeviceIdentityResponse
        }


        fun getPairPacketByteBuffer(value: Boolean): ByteBuffer {
            val builder = FlatBufferBuilder(256)
            val packet = Packet.createPacket(
                builder,
                Component.Pair,
                Pair.createPair(builder, value),
                1
            )
            builder.finishSizePrefixed(packet)
            return builder.dataBuffer()
        }
    }
}