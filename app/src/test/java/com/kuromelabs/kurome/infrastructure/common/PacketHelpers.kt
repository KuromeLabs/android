package com.kuromelabs.kurome.infrastructure.common

import Kurome.Fbs.Component
import Kurome.Fbs.CreateDirectoryCommand
import Kurome.Fbs.CreateFileCommand
import Kurome.Fbs.DeleteFileCommand
import Kurome.Fbs.DeviceIdentityResponse
import Kurome.Fbs.Packet
import Kurome.Fbs.Pair
import Kurome.Fbs.Platform
import Kurome.Fbs.RenameFileCommand
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
                Kurome.Fbs.WriteFileCommand.createWriteFileCommand(
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
                Kurome.Fbs.SetFileInfoCommand.createSetFileInfoCommand(
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