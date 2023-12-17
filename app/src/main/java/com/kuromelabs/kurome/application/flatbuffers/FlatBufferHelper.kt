package flatBufferHelper

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



fun deserializePacket(buffer: ByteArray): Packet {
    return Packet.getRootAsPacket(ByteBuffer.wrap(buffer))
}

fun getDeviceQuery(packet: Packet): DeviceQuery {
    return packet.component(DeviceQuery()) as DeviceQuery
}

fun createDeviceInfo(
    builder: FlatBufferBuilder, deviceId: String?, name: String?, totalSpace: Long?, freeSpace: Long?
): Int {
    var space: Int? = null
    var details: Int? = null
    if (totalSpace != null && freeSpace != null) {
        space = Space.createSpace(builder, totalSpace, freeSpace)
    }
    if (name != null && deviceId != null) {
        val nameOffset = builder.createString(name)
        val deviceIdOffset = builder.createString(deviceId)
        details = Details.createDetails(
            builder, nameOffset = nameOffset, idOffset = deviceIdOffset, 0
        )
    }
    DeviceInfo.startDeviceInfo(builder)
    if (space != null) DeviceInfo.addSpace(builder, space)
    if (details != null) DeviceInfo.addDetails(builder, details)
    return DeviceInfo.endDeviceInfo(builder)
}

fun createDeviceResponse(builder: FlatBufferBuilder, offset: Int, type: UByte): Int {
    DeviceResponse.startDeviceResponse(builder)
    DeviceResponse.addResponseType(builder, type)
    DeviceResponse.addResponse(builder, offset)
    return DeviceResponse.endDeviceResponse(builder)
}

fun createDeviceInfoResponse(
    builder: FlatBufferBuilder,
    deviceId: String? = null,
    name: String? = null,
    totalSpace: Long? = null,
    freeSpace: Long? = null
): Int {
    val infoOffset = createDeviceInfo(builder, deviceId, name, totalSpace, freeSpace)
    return createDeviceResponse(builder, infoOffset, DeviceResponseType.DeviceInfo)
}

fun createFileAttributes(
    builder: FlatBufferBuilder,
    name: String,
    status: Byte,
    length: Long,
    cTime: Long,
    lwTime: Long,
    laTime: Long,
    extraAttributes: UInt
): Int {
    val of = builder.createString(name)
    return Attributes.createAttributes(builder, of, status, length, cTime, laTime, lwTime, extraAttributes)
}

fun createNode(
    builder: FlatBufferBuilder,
    name: String,
    status: Byte,
    length: Long,
    cTime: Long,
    lwTime: Long,
    laTime: Long,
    extraAttributes: UInt,
    children: IntArray = IntArray(0)
): Int {
    val attrs =
        createFileAttributes(builder, name, status, length, cTime, laTime, lwTime, extraAttributes)
    return Node.createNode(builder, attrs, Node.createChildrenVector(builder, children))
}

fun createRaw(builder: FlatBufferBuilder, buffer: ByteArray, offset: Long, length: Int): Int {
    val vector = Raw.createDataVector(builder, buffer)
    return Raw.createRaw(builder, vector, offset, length)
}

fun createFileResponse(builder: FlatBufferBuilder, response: Int, type: UByte): Int {
    return FileResponse.createFileResponse(builder, type, response)
}

fun createPacket(builder: FlatBufferBuilder, offset: Int, type: UByte, packetId: Long): Int {
    return Packet.createPacket(builder, type, offset, packetId)
}

fun finishBuilding(builder: FlatBufferBuilder, root: Int): ByteBuffer {
    builder.finishSizePrefixed(root)
    return builder.dataBuffer()
}