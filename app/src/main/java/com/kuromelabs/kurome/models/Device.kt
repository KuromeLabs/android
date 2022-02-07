package com.kuromelabs.kurome.models

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import com.google.flatbuffers.FlatBufferBuilder
import com.kuromelabs.kurome.network.Link
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kurome.*
import timber.log.Timber
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.attribute.BasicFileAttributeView
import java.nio.file.attribute.FileTime


@OptIn(ExperimentalUnsignedTypes::class)
@Suppress("DEPRECATION")
@Entity(tableName = "device_table")
data class Device(
    @PrimaryKey @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "id") val id: String,
) : Link.PacketReceivedCallback {
    constructor(name: String, id: String, context: Context) : this(name, id) {
        this.context = context
    }

    @Ignore
    var context: Context? = null

    @Ignore
    private val job = SupervisorJob()

    @Ignore
    private val scope = CoroutineScope(Dispatchers.IO + job)

    var isPaired = false

    @Ignore
    var isConnected = false

    @Ignore
    private lateinit var link: Link

    @Ignore
    private val root = Environment.getExternalStorageDirectory().path

    fun setLink(link: Link) {
        Timber.d("Setting link")
        this.link = link
        link.addPacketCallback(this)
    }

    private fun parsePacket(packet: Packet, builder: FlatBufferBuilder): ByteBuffer {
        var deviceInfo = 0
        var fileNodes = IntArray(0)
        var raw = 0
        var result = Result.noResult
        val path = root + packet.path
        when (packet.action) {
            Action.actionGetSpaceInfo -> deviceInfo = getSpaceInfo(builder)
            Action.actionGetDirectory -> fileNodes = getDirectory(builder, path)
            Action.actionCreateDirectory -> result = createDirectory(path)
            Action.actionGetFileType -> result = getFileType(path)
            Action.actionDelete -> result = delete(path)
            Action.actionReadFileBuffer -> raw = readFileBuffer(builder, packet)
            Action.actionGetFileInfo -> fileNodes = intArrayOf(getFileNode(builder, path))
            Action.actionWriteFileBuffer -> result = writeFileBuffer(packet)
            Action.actionRename -> result = rename(packet)
            Action.actionSetLength -> result = setLength(packet)
            Action.actionSetFileTime -> result = setFileTime(packet)
            Action.actionCreateFile -> result = createFile(path)
        }
        val nodesVector = Packet.createNodesVector(builder, fileNodes)
        Packet.startPacket(builder)
        Packet.addNodes(builder, nodesVector)
        Packet.addResult(builder, result)
        Packet.addDeviceInfo(builder, deviceInfo)
        Packet.addFileBuffer(builder, raw)
        val res = Packet.endPacket(builder)
        builder.finishSizePrefixed(res)
        return builder.dataBuffer()
    }

    private fun getSpaceInfo(builder: FlatBufferBuilder): Int {
        DeviceInfo.startDeviceInfo(builder)
        DeviceInfo.addFreeBytes(builder, StatFs(Environment.getDataDirectory().path).availableBytes)
        DeviceInfo.addTotalBytes(builder, StatFs(Environment.getDataDirectory().path).totalBytes)
        return DeviceInfo.endDeviceInfo(builder)
    }

    private fun getDirectory(builder: FlatBufferBuilder, path: String): IntArray {
        val files = File(path).listFiles()
        val nodes = IntArray(files!!.size)
        files.forEachIndexed { i, file -> nodes[i] = getFileNode(builder, file.path) }
        return nodes
    }

    private fun createDirectory(path: String): Byte {
        return if (File(path).mkdirs()) Result.resultActionSuccess
        else Result.resultActionFail
    }

    private fun getFileType(path: String): Byte {
        val file = File(path)
        return if (file.exists())
            if (file.isDirectory) Result.resultFileIsDirectory
            else Result.resultFileIsFile
        else {
            val directory = File(path.substring(0, path.lastIndexOf("/")))
            if (!directory.exists()) Result.resultPathNotFound
            else Result.resultFileNotFound
        }
    }

    private fun delete(path: String): Byte {
        return if (File(path).deleteRecursively()) Result.resultActionSuccess
        else Result.resultActionFail
    }

    private fun readFileBuffer(builder: FlatBufferBuilder, packet: Packet): Int {
        val length = packet.fileBuffer!!.length
        val offset = packet.fileBuffer!!.offset
        val path = root + packet.path
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
        val byteVector = Raw.createDataVector(builder, buffer)
        Raw.startRaw(builder)
        Raw.addData(builder, byteVector)
        return Raw.endRaw(builder)
    }

    private fun getFileNode(builder: FlatBufferBuilder, path: String): Int {
        try {
            val file = File(path)
            var crTime = 0L
            val lwTime = file.lastModified()
            var laTime = 0L
            val filename = builder.createString(file.name)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val pathObj = Paths.get(path)
                val attributes =
                    Files.getFileAttributeView(pathObj, BasicFileAttributeView::class.java)
                crTime = attributes.readAttributes().creationTime().toMillis()
                laTime = attributes.readAttributes().lastAccessTime().toMillis()
            }
            return FileNode.createFileNode(
                builder,
                filename,
                file.isDirectory,
                file.length(),
                crTime,
                laTime,
                lwTime
            )
        } catch (e: Exception) {
            Timber.e(e)
            return 0
        }
    }

    private fun writeFileBuffer(packet: Packet): Byte {
        val raf = RandomAccessFile(root + packet.path, "rw")
        val raw = packet.fileBuffer!!
        val offset = raw.offset
        try {
            val actualOffset = if (offset == (-1).toLong()) raf.length() else offset
            raf.seek(actualOffset) //offset = -1 means append
            Timber.d("writing file buffer at $offset size: ${packet.fileBuffer!!.dataLength} path: ${root + packet.path}")
            raf.write(raw.dataAsByteBuffer.array(), raw.dataAsByteBuffer.position(), raw.dataLength)
            raf.close()
        } catch (e: Exception) {
            e.printStackTrace()
            return Result.resultActionFail
        }
        return Result.resultActionSuccess
    }

    private fun rename(packet: Packet): Byte {
        return try {
            val oldPath = root + packet.path!!
            val newPath = root + packet.nodes(0)!!.filename!!
            File(oldPath).renameTo(File(newPath))
            Result.resultActionSuccess
        } catch (e: Exception) {
            e.printStackTrace()
            Result.resultActionFail
        }
    }

    private fun setLength(packet: Packet): Byte {
        return try {
            val path = root + packet.path!!
            val raf = RandomAccessFile(path, "rw")
            raf.setLength(packet.nodes(0)!!.length)
            raf.close()
            Result.resultActionSuccess
        } catch (e: Exception) {
            e.printStackTrace()
            Result.resultActionFail
        }
    }

    private fun setFileTime(packet: Packet): Byte {
        val path = root + packet.nodes(0)!!.filename!!
        val crTime = packet.nodes(0)!!.creationTime
        val laTime = packet.nodes(0)!!.lastAccessTime
        val lwTime = packet.nodes(0)!!.lastWriteTime
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
        } else { //
            val file = File(path)
            file.setLastModified(lwTime)
        }
        return Result.resultActionSuccess
    }

    private fun createFile(path: String): Byte {
        return if (File(path).createNewFile()) Result.resultActionSuccess
        else Result.resultActionFail
    }


    override fun onPacketReceived(packet: Packet) {
        scope.launch {
            val result = parsePacket(packet, link.builder)
            link.sendByteBuffer(result)
            link.builder.clear()
        }
    }
}
