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
import java.io.File
import java.io.RandomAccessFile
import java.net.SocketException


@Entity(tableName = "device_table")
data class Device(
    @PrimaryKey @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "id") val id: String,

    ) {
    constructor(name: String, id: String, context: Context) : this(name, id) {
        this.context = context
    }

    @Ignore
    var context: Context? = null

    @Ignore
    var ip = String()

    @Ignore
    var controlLink = Link()

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
    private val activeLinks = ArrayList<Link>()

    fun activate(controlLink: Link) {
        this.controlLink = controlLink
        linkProvider = LinkProvider
        scope.launch {
            while (job.isActive) {
                val link = linkProvider.createLink(this@Device.controlLink)
                monitorLink(link)
            }
        }
    }

    private suspend fun monitorLink(link: Link) {
        activeLinks.add(link)
        scope.launch {
            var message = link.receiveMessage()
            while (job.isActive) {
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

    @Suppress("DEPRECATION")
    private fun parseMessage(bytes: ByteArray): ByteArray {
        val result: String
        val message: String? = if (bytes.size > 1) String(bytes, 1, bytes.size - 1) else null
        when (bytes[0]) {
            Packets.ACTION_GET_SPACE_INFO -> {
                val totalSpace = StatFs(Environment.getDataDirectory().path).totalBytes
                val availableSpace = StatFs(Environment.getDataDirectory().path).availableBytes
                result = "$totalSpace:$availableSpace"
                return result.toByteArray()
            }

            Packets.ACTION_GET_ENUMERATE_DIRECTORY -> {
                result = Json.encodeToString(getFileNodes(message!!))
                return result.toByteArray()
            }
            Packets.ACTION_WRITE_DIRECTORY -> {
                val dirPath = Environment.getExternalStorageDirectory().path + message!!
                val file = File(dirPath)
                return if (file.mkdir())
                    byteArrayOf(Packets.RESULT_ACTION_SUCCESS)
                else
                    byteArrayOf(Packets.RESULT_ACTION_FAIL)

            }
            Packets.ACTION_GET_FILE_TYPE -> {
                val file = File(Environment.getExternalStorageDirectory().path + message!!)
                return if (file.exists())
                    if (file.isDirectory)
                        byteArrayOf(Packets.RESULT_FILE_IS_DIRECTORY)
                    else
                        byteArrayOf(Packets.RESULT_FILE_IS_FILE)
                else
                    byteArrayOf(Packets.RESULT_FILE_NOT_FOUND)
            }
            Packets.ACTION_DELETE -> {
                val file = File(Environment.getExternalStorageDirectory().path + message!!)
                return if (file.deleteRecursively()) byteArrayOf(Packets.RESULT_ACTION_SUCCESS)
                else byteArrayOf(Packets.RESULT_ACTION_FAIL)

            }
            Packets.ACTION_SEND_TO_SERVER -> {
                val path = Environment.getExternalStorageDirectory().path + message!!.split(':')[0]
                val offset = message.split(':')[1].toLong()
                val size = message.split(':')[2].toInt()
                val fis = File(path).inputStream()
                var count = 0
                var pos = offset
                val buffer = ByteArray(size)
                while (count != size) {
                    //Log.e("kurome/device", "reading file buffer")
                    fis.channel.position(pos)
                    count += fis.read(buffer, count, size - count)
                    pos += count
                }
                fis.close()
                return buffer
            }
            Packets.ACTION_GET_FILE_INFO -> {
                val file = File(Environment.getExternalStorageDirectory().path + message)
                result = Json.encodeToString(FileNode(file.name, file.isDirectory, file.length()))
                return result.toByteArray()
            }
            Packets.ACTION_GET_DEVICE_NAME -> {
                return Build.MODEL.toByteArray()
            }
            Packets.ACTION_GET_DEVICE_ID -> {
                return getGuid(context!!).toByteArray()
            }
            Packets.ACTION_WRITE_FILE_BUFFER -> {
                val path = Environment.getExternalStorageDirectory().path + message!!.split(':')[0]
                val offset = message.split(':')[1].toLong()
                val first: Int = message.indexOf(':')
                val index: Int = message.indexOf(':', first + 1) + 2 //offset action byte
                val buffer: ByteArray = bytes.copyOfRange(index, bytes.size)
                val raf = RandomAccessFile(path, "rw")
                val actualOffset = if (offset == (-1).toLong()) raf.length() else offset
                raf.seek(actualOffset) //offset = -1 means append
//                Log.d("kurome/device","setting file length to " + (raf.length() + buffer.size))
//                raf.setLength(raf.length() + buffer.size)
//                Log.d("kurome/device","writing file buffer at $offset")
                raf.write(buffer)
                raf.close()
                return byteArrayOf(Packets.RESULT_ACTION_SUCCESS)
            }
            Packets.ACTION_RENAME -> {
                val oldPath =
                    Environment.getExternalStorageDirectory().path + message!!.split(':')[0]
                val newPath = Environment.getExternalStorageDirectory().path + message.split(':')[1]
                File(oldPath).renameTo(File(newPath))
                return byteArrayOf(Packets.RESULT_ACTION_SUCCESS)
            }
            Packets.ACTION_SET_LENGTH -> {
                val path = Environment.getExternalStorageDirectory().path + message!!.split(':')[0]
                val length = message.split(':')[1]
                val raf = RandomAccessFile(path, "rw")
                raf.setLength(length.toLong())
                raf.close()
                return byteArrayOf(Packets.RESULT_ACTION_SUCCESS)
            }
        }
        return byteArrayOf(Packets.RESULT_ACTION_FAIL)
    }

    @Suppress("DEPRECATION")
    private fun getFileNodes(path: String): ArrayList<FileNode> {
        val fileNodeList: ArrayList<FileNode> = ArrayList()
        val envPath = Environment.getExternalStorageDirectory().path
        val files = File(envPath + path).listFiles()
        if (files != null)
            for (file in files) {
                fileNodeList.add(FileNode(file.name, file.isDirectory, file.length()))
            }
        return fileNodeList
    }

    fun deactivate() {
        controlLink.stopConnection()
        for (link in activeLinks)
            link.stopConnection()
        scope.cancel()
    }
}
