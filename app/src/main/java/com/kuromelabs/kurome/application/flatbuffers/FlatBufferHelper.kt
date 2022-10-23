package com.kuromelabs.kurome.application.flatbuffers

import com.google.flatbuffers.FlatBufferBuilder
import kurome.fbs.Attributes
import kurome.fbs.Details
import kurome.fbs.DeviceInfo
import kurome.fbs.DeviceQuery
import kurome.fbs.DeviceResponse
import kurome.fbs.DeviceResponseType
import kurome.fbs.FileResponse
import kurome.fbs.Node
import kurome.fbs.Packet
import kurome.fbs.Raw
import kurome.fbs.Space
import java.nio.ByteBuffer
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap

class FlatBufferHelper {

    private val map = ConcurrentHashMap<Long, FlatBufferBuilder>()
    private val random = SecureRandom()

    fun startBuilding(): Long {
        return startBuilding(256)
    }

    fun startBuilding(size: Int): Long {
        val builder = FlatBufferBuilder(size)
        val id = random.nextLong()
        map[id] = builder
        return id
    }

    fun deserializePacket(buffer: ByteArray): Packet {
        return Packet.getRootAsPacket(ByteBuffer.wrap(buffer))
    }

    fun getDeviceQuery(packet: Packet): DeviceQuery {
        return packet.component(DeviceQuery()) as DeviceQuery
    }

    fun createDeviceInfo(
        builderId: Long, deviceId: String?, name: String?, totalSpace: Long?, freeSpace: Long?
    ): Int {
        val builder = map[builderId]!!
        var space: Int? = null
        var details: Int? = null
        if (totalSpace != null && freeSpace != null) {
            space = Space.createSpace(builder, totalSpace, freeSpace)
        }
        if (name != null && deviceId != null) {
            val nameOffset = if (name != null) builder.createString(name) else 0
            val deviceIdOffset = if (deviceId != null) builder.createString(deviceId) else 0
            details = Details.createDetails(
                builder, nameOffset = nameOffset, idOffset = deviceIdOffset, 0
            )
        }
        DeviceInfo.startDeviceInfo(builder)
        if (space != null) DeviceInfo.addSpace(builder, space)
        if (details != null) DeviceInfo.addDetails(builder, details)
        return DeviceInfo.endDeviceInfo(builder)
    }

    fun createDeviceResponse(builderId: Long, offset: Int, type: UByte): Int {
        val builder = map[builderId]!!
        DeviceResponse.startDeviceResponse(builder)
        DeviceResponse.addResponseType(builder, type)
        DeviceResponse.addResponse(builder, offset)
        return DeviceResponse.endDeviceResponse(builder)
    }

    fun createDeviceInfoResponse(
        builderId: Long,
        deviceId: String? = null,
        name: String? = null,
        totalSpace: Long? = null,
        freeSpace: Long? = null
    ): Int {
        val infoOffset = createDeviceInfo(builderId, deviceId, name, totalSpace, freeSpace)
        return createDeviceResponse(builderId, infoOffset, DeviceResponseType.DeviceInfo)
    }

    fun createFileAttributes(
        builderId: Long,
        name: String,
        type: Byte,
        status: Byte,
        length: Long,
        cTime: Long,
        lwTime: Long,
        laTime: Long
    ): Int {
        val builder = map[builderId]!!
        val of = builder.createString(name)
        return Attributes.createAttributes(builder, of, type, status, length, cTime, laTime, lwTime)
    }

    fun createNode(
        builderId: Long,
        name: String,
        type: Byte,
        status: Byte,
        length: Long,
        cTime: Long,
        lwTime: Long,
        laTime: Long,
        children: IntArray = IntArray(0)
    ): Int {
        val builder = map[builderId]!!
        val attrs =
            createFileAttributes(builderId, name, type, status, length, cTime, laTime, lwTime)
        val children =
            if (children.isNotEmpty()) Node.createChildrenVector(builder, children) else 0
        return Node.createNode(builder, attrs, children)
    }

    fun createRaw(builderId: Long, buffer: ByteArray, offset: Long, length: Int): Int {
        val builder = map[builderId]!!
        val vector = Raw.createDataVector(builder, buffer)
        return Raw.createRaw(builder, vector, offset, length)
    }

    fun createFileResponse(builderId: Long, response: Int, type: UByte): Int {
        val builder = map[builderId]!!
        return FileResponse.createFileResponse(builder, type, response)
    }

    fun createPacket(builderId: Long, offset: Int, type: UByte, packetId: Long): Int {
        val builder = map[builderId]!!
        return Packet.createPacket(builder, type, offset, packetId)
    }

    fun finishBuilding(builderId: Long, root: Int): ByteBuffer {
        val builder = map[builderId]!!
        map.remove(builderId)
        builder.finish(root)
        return builder.dataBuffer()
    }
}