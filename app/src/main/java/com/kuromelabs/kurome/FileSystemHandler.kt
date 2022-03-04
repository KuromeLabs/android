package com.kuromelabs.kurome

import android.os.Build
import android.os.Environment
import android.os.StatFs
import com.google.flatbuffers.FlatBufferBuilder
import com.kuromelabs.kurome.models.Device
import kurome.*
import timber.log.Timber
import java.io.File
import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.attribute.BasicFileAttributeView
import java.nio.file.attribute.FileTime

@OptIn(ExperimentalUnsignedTypes::class)
class FileSystemHandler(private val root: String, private val device: Device) {
    data class Node(
        val name: String, val type: Byte, val size: Long,
        val crTime: Long, val lwTime: Long, val laTime: Long
    )

    fun packetReceived(packet: Packet) {
        val path = root + packet.path
        val fb = packet.fileBuffer!!
        val node = packet.nodes(0)!!
        when (packet.action) {
            Action.actionGetSpaceInfo -> sendSpaceInfo(packet.id)
            Action.actionGetDirectory -> sendDirectory(path, packet.id)
            Action.actionCreateDirectory -> createDirectory(path)
            Action.actionDelete -> delete(path)
            Action.actionReadFileBuffer -> sendFileBuffer(path, packet.id, fb.length, fb.offset)
            Action.actionGetFileInfo -> sendFileNode(path, packet.id)
            Action.actionWriteFileBuffer -> writeFileBuffer(path, fb)
            Action.actionRename -> rename(path, root + node.filename!!)
            Action.actionSetLength -> setLength(path, node.length)
            Action.actionSetFileTime -> setFileTime(
                path, node.creationTime, node.lastWriteTime, node.lastAccessTime
            )
            Action.actionCreateFile -> createFile(path)
        }
    }

    private fun sendSpaceInfo(id: Int) {
        val statFs = StatFs(Environment.getDataDirectory().path)
        sendPacket(total = statFs.totalBytes, free = statFs.freeBytes, id = id)
    }

    private fun sendDirectory(path: String, id: Int) {
        val files = File(path).listFiles()
        val nodes = Array(files!!.size) { i -> getFileNode(files[i].path) }
        sendPacket(nodes = nodes, id = id)
    }

    private fun createDirectory(path: String) {
        File(path).mkdirs()
    }

    private fun delete(path: String) {
        File(path).deleteRecursively()
    }

    private fun sendFileBuffer(path: String, id: Int, length: Int, offset: Long) {
        val fis = File(path).inputStream()
        var count = 0
        var pos = offset
        val buffer = ByteArray(length)
        while (count != length) {
            Timber.d("reading file buffer ($length) at $pos of file $path")
            fis.channel.position(pos)
            count += fis.read(buffer, count, length - count)
            pos += count
        }
        fis.close()
        sendPacket(buffer = buffer, id = id)
    }

    private fun sendFileNode(path: String, id: Int) {
        val node = Array(1) { getFileNode(path) }
        sendPacket(nodes = node, id = id)
    }

    private fun getFileNode(path: String): Node {
        val file = File(path)
        val type = if (file.exists())
            if (file.isDirectory) FileType.Directory
            else FileType.File
        else {
            val directory = File(path.substring(0, path.lastIndexOf("/")))
            if (!directory.exists()) FileType.PathNotFound
            else FileType.FileNotFound
        }
        var crTime = 0L
        var lwTime = 0L
        var laTime = 0L
        if (file.exists()) {
            lwTime = file.lastModified()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val pathObj = Paths.get(path)
                val attributes =
                    Files.getFileAttributeView(pathObj, BasicFileAttributeView::class.java)
                crTime = attributes.readAttributes().creationTime().toMillis()
                laTime = attributes.readAttributes().lastAccessTime().toMillis()
            }
        }
        return Node(file.name, type, file.length(), crTime, lwTime, laTime)
    }

    private fun writeFileBuffer(path: String, raw: Raw) {
        val raf = RandomAccessFile(path, "rw")
        val offset = raw.offset
        try {
            val actualOffset = if (offset == (-1).toLong()) raf.length() else offset
            raf.seek(actualOffset) //offset = -1 means append
            Timber.d("writing file buffer at $offset size: ${raw.dataLength} path: $path")
            raf.write(raw.dataAsByteBuffer.array(), raw.dataAsByteBuffer.position(), raw.dataLength)
            raf.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun rename(oldPath: String, newPath: String) {
        try {
            File(oldPath).renameTo(File(newPath))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun setLength(path: String, length: Long) {
        try {
            val raf = RandomAccessFile(path, "rw")
            raf.setLength(length)
            raf.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun setFileTime(path: String, crTime: Long, laTime: Long, lwTime: Long) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val attributes = Files.getFileAttributeView(
                Paths.get(path),
                BasicFileAttributeView::class.java
            )
            attributes.setTimes(
                FileTime.fromMillis(crTime),
                FileTime.fromMillis(laTime),
                FileTime.fromMillis(lwTime)
            )
        } else File(path).setLastModified(lwTime)
    }

    private fun createFile(path: String) {
        File(path).createNewFile()
    }

    private fun sendPacket(
        total: Long = 0, free: Long = 0, deviceName: String = Build.MODEL,
        nodes: Array<Node> = arrayOf(), result: Byte = Result.noResult, id: Int = 0,
        buffer: ByteArray = ByteArray(0)
    ) {
        val builder = FlatBufferBuilder(1024)
        val idOff = builder.createString(device.id)
        val nameOff = builder.createString(deviceName)
        val deviceInfo = DeviceInfo.createDeviceInfo(builder, nameOff, idOff, total, free, 0)
        val fileNodes = Array(nodes.size) { i ->
            val nodeNameOff = builder.createString(nodes[i].name)
            FileBuffer.createFileBuffer(
                builder, nodeNameOff, nodes[i].type, nodes[i].size,
                nodes[i].crTime, nodes[i].lwTime, nodes[i].laTime
            )
        }.toIntArray()
        val byteVector = Raw.createDataVector(builder, buffer)
        val raw = Raw.createRaw(builder, byteVector, 0, 0)
        val nodesVector = Packet.createNodesVector(builder, fileNodes)
        val packet = Packet.createPacket(builder, 0, 0, result, deviceInfo, raw, nodesVector, id)
        builder.finishSizePrefixed(packet)
        device.sendBuffer(builder.dataBuffer())
    }
}