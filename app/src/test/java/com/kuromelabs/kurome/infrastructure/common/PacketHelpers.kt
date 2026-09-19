package com.kuromelabs.kurome.infrastructure.common

import com.kuromelabs.core.models_fbs.Component
import com.kuromelabs.core.models_fbs.CreateDirectoryCommand
import com.kuromelabs.core.models_fbs.CreateFileCommand
import com.kuromelabs.core.models_fbs.DeleteFileCommand
import com.kuromelabs.core.models_fbs.DeviceIdentityResponse
import com.kuromelabs.core.models_fbs.Packet
import com.kuromelabs.core.models_fbs.Pair
import com.kuromelabs.core.models_fbs.Platform
import com.kuromelabs.core.models_fbs.RenameFileCommand
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

        fun getDeviceIdentityResponsePacketByteBuffer(id: String): ByteBuffer {
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
                0
            )
            builder.finishSizePrefixed(packet)
            return builder.dataBuffer()
        }

        fun getCreateDirectoryCommandPacket(path: String): Packet {
            val builder = FlatBufferBuilder(256)
            val packet = Packet.createPacket(
                builder,
                Component.CreateDirectoryCommand,
                CreateDirectoryCommand.createCreateDirectoryCommand(
                    builder,
                    builder.createString(path)
                ),
                1
            )
            builder.finish(packet)
            return Packet.getRootAsPacket(builder.dataBuffer())
        }

        fun getDeleteFileCommandPacket(path: String): Packet {
            val builder = FlatBufferBuilder(256)
            val packet = Packet.createPacket(
                builder,
                Component.DeleteFileCommand,
                DeleteFileCommand.createDeleteFileCommand(
                    builder,
                    builder.createString(path)
                ),
                1
            )
            builder.finish(packet)
            return Packet.getRootAsPacket(builder.dataBuffer())
        }

        fun getCreateFileCommandPacket(path: String, attrs: UInt): Packet {
            val builder = FlatBufferBuilder(256)
            val packet = Packet.createPacket(
                builder,
                Component.CreateFileCommand,
                CreateFileCommand.createCreateFileCommand(
                    builder,
                    builder.createString(path),
                    attrs
                ),
                1
            )
            builder.finish(packet)
            return Packet.getRootAsPacket(builder.dataBuffer())
        }

        fun getRenameFileCommandPacket(path: String, newPath: String?): Packet {
            val builder = FlatBufferBuilder(256)
            val packet = Packet.createPacket(
                builder,
                Component.RenameFileCommand,
                RenameFileCommand.createRenameFileCommand(
                    builder,
                    builder.createString(path),
                    if (newPath == null) 0 else builder.createString(newPath)
                ),
                1
            )
            builder.finish(packet)
            return Packet.getRootAsPacket(builder.dataBuffer())
        }

        fun getWriteFileCommandPacket(path: String, data: ByteArray, offset: Long): Packet {
            val builder = FlatBufferBuilder(256)
            val packet = Packet.createPacket(
                builder,
                Component.WriteFileCommand,
                com.kuromelabs.core.models_fbs.WriteFileCommand.createWriteFileCommand(
                    builder,
                    builder.createString(path),
                    builder.createByteVector(data),
                    offset,
                    data.size
                ),
                1
            )
            builder.finish(packet)
            return Packet.getRootAsPacket(builder.dataBuffer())
        }

        fun getSetFileAttributesCommandPacket(path: String, length: Long, cTime: Long, laTime: Long, lwTime: Long, extraAttributes: UInt): Packet {
            val builder = FlatBufferBuilder(256)
            val packet = Packet.createPacket(
                builder,
                Component.SetFileInfoCommand,
                com.kuromelabs.core.models_fbs.SetFileInfoCommand.createSetFileInfoCommand(
                    builder,
                    builder.createString(path),
                    length, cTime, laTime, lwTime, extraAttributes
                ),
                1
            )
            builder.finish(packet)
            return Packet.getRootAsPacket(builder.dataBuffer())
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