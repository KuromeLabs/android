package com.kuromelabs.kurome.infrastructure.device

import android.os.Build
import android.os.Environment
import android.os.StatFs
import com.kuromelabs.kurome.application.flatbuffers.FlatBufferHelper
import com.kuromelabs.kurome.application.interfaces.DeviceAccessor
import com.kuromelabs.kurome.application.interfaces.DeviceAccessorFactory
import com.kuromelabs.kurome.application.interfaces.IdentityProvider
import com.kuromelabs.kurome.application.interfaces.Link
import com.kuromelabs.kurome.domain.Device
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kurome.fbs.Component
import kurome.fbs.Create
import kurome.fbs.Delete
import kurome.fbs.DeviceQuery
import kurome.fbs.DeviceQueryType
import kurome.fbs.FileCommand
import kurome.fbs.FileCommandType
import kurome.fbs.FileQuery
import kurome.fbs.FileQueryType
import kurome.fbs.FileResponeType
import kurome.fbs.FileStatus
import kurome.fbs.FileType
import kurome.fbs.Packet
import kurome.fbs.Rename
import kurome.fbs.Write
import timber.log.Timber
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.attribute.BasicFileAttributeView


class DeviceAccessorImpl @AssistedInject constructor(
    @Assisted var link: Link,
    @Assisted var device: Device,
    var identityProvider: IdentityProvider,
    var deviceRepository: DeviceRepository,
    var scope: CoroutineScope,
    var flatBufferHelper: FlatBufferHelper
) : DeviceAccessor {

    private val root: String = Environment.getExternalStorageDirectory().path
    override fun start() {
        scope.launch {
            while (scope.coroutineContext.isActive) {
                val sizeBuffer = ByteArray(4)
                if (link.receive(sizeBuffer, 4) <= 0) break
                val size = ByteBuffer.wrap(sizeBuffer).order(ByteOrder.LITTLE_ENDIAN).int
                val data = ByteArray(size)
                if (link.receive(data, size) <= 0) break
                val packet = flatBufferHelper.deserializePacket(data)
                Timber.d("Received packet with ID: ${packet.id}")
                launch { processPacket(packet) }
            }
            link.close()
            deviceRepository.removeDeviceAccessor(get().id)
        }
    }

    override fun get(): Device {
        return device
    }

    private suspend fun processPacket(packet: Packet) {
        when (packet.componentType) {
            Component.DeviceQuery -> {
                val builderId = flatBufferHelper.startBuilding()
                val deviceQuery = flatBufferHelper.getDeviceQuery(packet)
                val response = processDeviceQuery(builderId, deviceQuery)
                sendPacket(builderId, response, Component.DeviceResponse, packet.id)
            }

            Component.FileQuery -> {
                val builderId = flatBufferHelper.startBuilding()
                val fileQuery = packet.component(FileQuery()) as FileQuery
                val response = processFileQuery(builderId, fileQuery)
                sendPacket(builderId, response, Component.FileResponse, packet.id)
            }

            Component.FileCommand -> {
                val fileCommand = packet.component(FileCommand()) as FileCommand
                processFileCommand(fileCommand)
            }
        }
    }

    private suspend fun sendPacket(id: Long, response: Int, type: UByte, responseId: Long) {
        Timber.d("$responseId")
        val packet = flatBufferHelper.createPacket(id, response, type, responseId)
        val buffer = flatBufferHelper.finishBuilding(id, packet)
        link.send(buffer)
    }

    private fun processDeviceQuery(builderId: Long, q: DeviceQuery): Int {
        var response = 0
        when (q.type) {
            DeviceQueryType.GetInfo -> response = deviceIdentityToFbs(builderId)
            DeviceQueryType.GetSpace -> response = deviceSpaceToFbs(builderId)
            DeviceQueryType.GetAll -> response = deviceInfoToFbs(builderId)
        }
        return response
    }

    private fun deviceIdentityToFbs(builderId: Long): Int {
        val id = identityProvider.getEnvironmentId()
        val name = identityProvider.getEnvironmentName()
        return flatBufferHelper.createDeviceInfoResponse(builderId, id, name)
    }

    private fun deviceSpaceToFbs(builderId: Long): Int {
        val statFs = StatFs(Environment.getDataDirectory().path)
        return flatBufferHelper.createDeviceInfoResponse(
            builderId,
            totalSpace = statFs.totalBytes,
            freeSpace = statFs.freeBytes
        )
    }

    private fun deviceInfoToFbs(builderId: Long): Int {
        val id = identityProvider.getEnvironmentId()
        val name = identityProvider.getEnvironmentName()
        val statFs = StatFs(Environment.getDataDirectory().path)
        return flatBufferHelper.createDeviceInfoResponse(
            builderId,
            id,
            name,
            statFs.totalBytes,
            statFs.freeBytes
        )
    }

    private fun processFileQuery(builderId: Long, q: FileQuery): Int {
        var response = 0
        var type = FileResponeType.Node
        when (q.type) {
            FileQueryType.GetDirectory -> response = directoryToFbs(builderId, root + q.path!!)
            FileQueryType.ReadFile -> {
                type = FileResponeType.Raw
                response = fileBufferToFbs(builderId, root + q.path!!, q.offset, q.length)
            }
        }
        return flatBufferHelper.createFileResponse(builderId, response, type)
    }

    private fun fileToFbs(builderId: Long, path: String): Int {
        val file = File(path)
        val status =
            if (file.exists())
                FileStatus.Exists
            else if (!File(path.substring(0, path.lastIndexOf("/"))).exists())
                FileStatus.PathNotFound
            else FileStatus.FileNotFound

        val type =
            if (status == FileStatus.Exists && file.isFile) FileType.File else FileType.Directory
        val (crTime, laTime, lwTime) = getFileTimes(file)

        return flatBufferHelper.createNode(
            builderId,
            file.name,
            type,
            status,
            file.length(),
            crTime,
            lwTime,
            laTime
        )
    }

    private fun directoryToFbs(builderId: Long, path: String): Int {
        Timber.d("Getting directory at $path")
        val directory = File(path)
        val files = directory.listFiles()
        var children = IntArray(0)
        try {
            children = Array(files!!.size) { i -> fileToFbs(builderId, files[i].path) }
                .toIntArray()
        } catch (e: Exception) {
            Timber.e("Exception at $path: $e")
        }
        val (crTime, laTime, lwTime) = getFileTimes(directory)

        return flatBufferHelper.createNode(
            builderId,
            directory.name,
            FileType.Directory,
            FileStatus.Exists,
            directory.length(),
            crTime,
            lwTime,
            laTime,
            children
        )
    }

    private fun getFileTimes(file: File): Triple<Long, Long, Long> {
        val lwTime = file.lastModified()
        var crTime: Long = 0
        var laTime: Long = 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val pathObj = Paths.get(file.path)
            val attributes =
                Files.getFileAttributeView(pathObj, BasicFileAttributeView::class.java)
            crTime = attributes.readAttributes().creationTime().toMillis()
            laTime = attributes.readAttributes().lastAccessTime().toMillis()
        }
        return Triple(crTime, laTime, lwTime)
    }

    private fun fileBufferToFbs(builderId: Long, path: String, offset: Long, length: Int): Int {
        val fileChannel = RandomAccessFile(path, "r").channel
        val byteBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, offset, length.toLong())
        val buffer = ByteBuffer.allocate(length)
        buffer.put(byteBuffer)
        fileChannel.close()
        return flatBufferHelper.createRaw(builderId, buffer.array(), offset, length)
    }

    private fun processFileCommand(command: FileCommand) {
        when (command.commandType) {
            FileCommandType.Delete -> {
                val delete = command.command(Delete()) as Delete
                File(root + delete.path!!).deleteRecursively()
            }

            FileCommandType.Rename -> {
                val rename = command.command(Rename()) as Rename
                File(root + rename.oldPath!!).renameTo(File(root + rename.newPath!!))
            }

            FileCommandType.Write -> {
                val write = command.command(Write()) as Write
                val raw = write.buffer!!
                writeFile(root + write.path!!, raw.dataAsByteBuffer, raw.offset, raw.dataLength)
            }

            FileCommandType.Create -> {
                val create = command.command(Create()) as Create
                Timber.d("Create called on ${root + create.path!!}")
                if (create.type == FileType.File) File(root + create.path!!).createNewFile()
                else File(root + create.path!!).mkdir()
            }
        }
    }

    private fun writeFile(path: String, raw: ByteBuffer, offset: Long, length: Int) {
        val raf = RandomAccessFile(path, "rw")
        val actualOffset = if (offset == (-1).toLong()) raf.length() else offset
        raf.seek(actualOffset) //offset = -1 means append
        raf.write(raw.array(), raw.position(), length)
    }
}