package com.kuromelabs.kurome.models

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.os.StatFs
import androidx.core.content.ContextCompat
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import com.kuromelabs.kurome.getGuid
import com.kuromelabs.kurome.network.Link
import com.kuromelabs.kurome.network.LinkProvider
import com.kuromelabs.kurome.network.Packets
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.File
import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.attribute.BasicFileAttributeView
import java.nio.file.attribute.FileTime
import java.util.*


@Suppress("DEPRECATION")
@Entity(tableName = "device_table")
data class Device(
    @PrimaryKey @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "id") val id: String,
) {
    constructor(name: String, id: String, listener: DeviceStatusListener, context: Context) : this(
        name,
        id
    ) {
        this.context = context
        this.listener = listener
    }

    @Ignore
    var listener: DeviceStatusListener? = null

    interface DeviceStatusListener {
        suspend fun onConnected(device: Device)
        suspend fun onDisconnected(device: Device)
    }

    @Ignore
    var context: Context? = null

    @Ignore
    var ip = String()

    @Ignore
    var linkProvider = LinkProvider

    @Ignore
    private val job = SupervisorJob()

    @Ignore
    private val scope = CoroutineScope(Dispatchers.IO + job)

    var isPaired = false

    @Ignore
    var isConnected = false

    @Ignore
    private val activeLinks = Collections.synchronizedList(ArrayList<Link>())

    @Ignore
    private val rootPath = Environment.getExternalStorageDirectory().path


    fun activate() {
        scope.launch {
            val controlLink: Link
            try {
                controlLink = linkProvider.createControlLinkFromUdp(
                    "235.132.20.12",
                    33586,
                    id
                )
            } catch (e: Exception) {
                Timber.d("creating control link from UDP failed $this")
                return@launch
            }
            controlLink.sendMessage(
                byteArrayOf(Packets.ACTION_CONNECT) +
                        (Build.MODEL + ':' + getGuid(context!!)).toByteArray(),
                false
            )
            if (controlLink.receiveMessage()[0] == Packets.RESULT_ACTION_SUCCESS) {
                monitorControlLink(controlLink)
                listener!!.onConnected(this@Device)
            } else {
                listener!!.onDisconnected(this@Device)
                Timber.d("Device connection failed: $this")
            }
        }
    }

    private fun monitorControlLink(controlLink: Link) {
        scope.launch {
            try {
                while (job.isActive) {
                    val link = linkProvider.createLink(controlLink)
                    scope.launch {
                        monitorLink(link)
                    }
                }
            } catch (e: Exception) {
                Timber.d("control link died $this")
                controlLink.stopConnection()
                listener!!.onDisconnected(this@Device)

            }
        }
    }

    private suspend fun monitorLink(link: Link) {
        activeLinks.add(link)
        scope.launch {
            var message = link.receiveMessage()
            while (job.isActive) {
                if (message[0] == Packets.RESULT_ACTION_FAIL) {
                    break
                }
                val pm: PowerManager =
                    ContextCompat.getSystemService(context!!, PowerManager::class.java)!!
                val wl =
                    pm.newWakeLock(
                        PowerManager.PARTIAL_WAKE_LOCK,
                        "com.kuromelabs.kurome: tcp wakelock"
                    )
                wl.acquire(10 * 60 * 1000L /*10 minutes*/)
                val result = parseMessage(message)
                link.sendMessage(result, false)
                wl.release()
                message = link.receiveMessage()
            }
            link.stopConnection()
            activeLinks.remove(link)
        }
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
        val byteVector = Raw.createRawVector(builder, buffer)
        Raw.startRaw(builder)
        Raw.addRaw(builder, byteVector)
        return Raw.endRaw(builder)
    }

    private fun getFileNode(builder: FlatBufferBuilder, path: String): Int {
        val file = File(path)
        var crTime = 0L
        val lwTime = file.lastModified()
        var laTime = 0L
        val filename = builder.createString(file.name)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val pathObj = Paths.get(path)
            val attributes = Files.getFileAttributeView(pathObj, BasicFileAttributeView::class.java)
            crTime = attributes.readAttributes().creationTime().toMillis()
            laTime = attributes.readAttributes().lastAccessTime().toMillis()
        }
        return kurome.FileNode.createFileNode(
            builder,
            filename,
            file.isDirectory,
            file.length(),
            crTime,
            laTime,
            lwTime
        )
    }

    private fun writeFileBuffer(packet: Packet): Byte {
        val raf = RandomAccessFile(root + packet.path, "rw")
        val offset = packet.fileBuffer!!.offset
        try {
            val actualOffset = if (offset == (-1).toLong()) raf.length() else offset
            raf.seek(actualOffset) //offset = -1 means append
            Timber.d("writing file buffer at $offset")
            raf.write(packet.fileBuffer!!.rawAsByteBuffer.array())
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

    private fun parseMessage(bytes: ByteArray): ByteArray {
        val message: String? = if (bytes.size > 1) String(bytes, 1, bytes.size - 1) else null
        when (bytes[0]) {
            Packets.ACTION_GET_SPACE_INFO -> return getSpaceInfo()
            Packets.ACTION_GET_ENUMERATE_DIRECTORY -> return getFileNodes(rootPath + message!!)
            Packets.ACTION_WRITE_DIRECTORY -> return createDirectory(rootPath + message!!)
            Packets.ACTION_GET_FILE_TYPE -> return getFileType(rootPath + message!!)
            Packets.ACTION_GET_DEVICE_NAME -> return Build.MODEL.toByteArray()
            Packets.ACTION_GET_DEVICE_ID -> return getGuid(context!!).toByteArray()
            Packets.ACTION_DELETE -> {
                val file = File(rootPath + message!!)
                return if (file.deleteRecursively()) byteArrayOf(Packets.RESULT_ACTION_SUCCESS)
                else byteArrayOf(Packets.RESULT_ACTION_FAIL)
            }
            Packets.ACTION_SEND_TO_SERVER -> {
                val path = rootPath + message!!.split(':')[0]
                val offset = message.split(':')[1].toLong()
                val size = message.split(':')[2].toInt()
                return readFileBuffer(path, offset, size)
            }
            Packets.ACTION_GET_FILE_INFO ->
                return Json.encodeToString(getFileNode(rootPath + message)).toByteArray()

            Packets.ACTION_WRITE_FILE_BUFFER -> {
                val path = rootPath + message!!.split(':')[0]
                val offset = message.split(':')[1].toLong()
                val firstSymbol: Int = message.indexOf(':')
                val index: Int = message.indexOf(':', firstSymbol + 1) + 2 //offset action byte
                val buffer: ByteArray = bytes.copyOfRange(index, bytes.size)
                return writeFileBuffer(path, offset, buffer)
            }
            Packets.ACTION_RENAME -> {
                val oldPath = rootPath + message!!.split(':')[0]
                val newPath = rootPath + message.split(':')[1]
                File(oldPath).renameTo(File(newPath))
                return byteArrayOf(Packets.RESULT_ACTION_SUCCESS)
            }
            Packets.ACTION_SET_LENGTH -> {
                val path = rootPath + message!!.split(':')[0]
                val length = message.split(':')[1]
                val raf = RandomAccessFile(path, "rw")
                raf.setLength(length.toLong())
                raf.close()
                return byteArrayOf(Packets.RESULT_ACTION_SUCCESS)
            }
            Packets.ACTION_SET_FILE_TIME -> {
                val input = message!!.split(':')
                val path = rootPath + input[0]
                val crTime = if (input[1] != "") input[1].toLong() else 0
                val laTime = if (input[1] != "") input[2].toLong() else 0
                val lwTime = if (input[1] != "") input[3].toLong() else 0
                return setFileTime(path, crTime, laTime, lwTime)
            }
            Packets.ACTION_CREATE_EMPTY_FILE -> {
                File(rootPath + message!!).createNewFile()
                return byteArrayOf(Packets.RESULT_ACTION_SUCCESS)
            }
        }
        return byteArrayOf(Packets.RESULT_ACTION_FAIL)
    }

    private fun getSpaceInfo(): ByteArray {
        val totalSpace = StatFs(Environment.getDataDirectory().path).totalBytes
        val availableSpace = StatFs(Environment.getDataDirectory().path).availableBytes
        val result = "$totalSpace:$availableSpace"
        return result.toByteArray()
    }

    private fun getFileNodes(path: String): ByteArray {
        val fileNodeList: ArrayList<FileNode> = ArrayList()
        val files = File(path).listFiles()
        if (files != null)
            for (file in files) {
                fileNodeList.add(getFileNode(file.path))
            }
        return Json.encodeToString(fileNodeList).toByteArray()
    }

    private fun createDirectory(path: String): ByteArray {
        val file = File(path)
        return if (file.mkdirs())
            byteArrayOf(Packets.RESULT_ACTION_SUCCESS)
        else
            byteArrayOf(Packets.RESULT_ACTION_FAIL)
    }

    private fun getFileType(path: String): ByteArray {
        val file = File(path)
        return if (file.exists())
            if (file.isDirectory)
                byteArrayOf(Packets.RESULT_FILE_IS_DIRECTORY)
            else
                byteArrayOf(Packets.RESULT_FILE_IS_FILE)
        else {
            val directory = File(path.dropLastWhile { it != '/' }.dropLast(1))
            if (!directory.exists()) {
                Timber.d("returning RESULT_PATH_NOT_FOUND for " + file.path)
                byteArrayOf(Packets.RESULT_PATH_NOT_FOUND)
            } else
                byteArrayOf(Packets.RESULT_FILE_NOT_FOUND)
        }
    }

    private fun readFileBuffer(path: String, offset: Long, length: Int): ByteArray {
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
        return buffer
    }

    private fun getFileNode(path: String): FileNode {
        val file = File(path)
        var creationTime = 0L
        val lastModifiedTime = file.lastModified()
        var lastAccessTime = 0L
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val attributes = Files.getFileAttributeView(
                Paths.get(path),
                BasicFileAttributeView::class.java
            )
            creationTime = attributes.readAttributes().creationTime().toMillis()
            lastAccessTime = attributes.readAttributes().lastAccessTime().toMillis()
        }
        Timber.d("returning fileNode for $path")
        return FileNode(
            file.name,
            file.isDirectory,
            file.length(),
            creationTime,
            lastAccessTime,
            lastModifiedTime
        )

    }

    private fun writeFileBuffer(path: String, offset: Long, buffer: ByteArray): ByteArray {
        val raf = RandomAccessFile(path, "rw")
        val actualOffset = if (offset == (-1).toLong()) raf.length() else offset
        raf.seek(actualOffset) //offset = -1 means append
        Timber.d("writing file buffer at $offset")
        raf.write(buffer)
        raf.close()
        return byteArrayOf(Packets.RESULT_ACTION_SUCCESS)
    }

    private fun setFileTime(path: String, crTime: Long, laTime: Long, lwTime: Long): ByteArray {
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
        return byteArrayOf(Packets.RESULT_ACTION_SUCCESS)
    }


    fun deactivate() {
        activeLinks.forEach { it.stopConnection() }
        activeLinks.clear()
        Timber.d("active links after deactivate: $activeLinks")
        job.cancelChildren()
    }
}
