package com.kuromelabs.kurome.application

import Kurome.Fbs.Component
import Kurome.Fbs.CreateDirectoryCommand
import Kurome.Fbs.CreateFileCommand
import Kurome.Fbs.DeleteFileCommand
import Kurome.Fbs.GetDirectoryResponse
import Kurome.Fbs.GetFileInfoResponse
import Kurome.Fbs.Node
import Kurome.Fbs.Packet
import Kurome.Fbs.ReadFileQuery
import Kurome.Fbs.ReadFileResponse
import Kurome.Fbs.RenameFileCommand
import Kurome.Fbs.SetFileInfoCommand
import Kurome.Fbs.WriteFileCommand
import android.os.Build
import android.os.Environment
import com.google.flatbuffers.FlatBufferBuilder
import com.kuromelabs.kurome.domain.Device
import timber.log.Timber
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.attribute.BasicFileAttributeView
import java.nio.file.attribute.FileTime

class FilesystemAccessor constructor(
    val device: Device
) {

    class ExtraAttribute {
        companion object {
            const val None = 0u
            const val Archive = 32u
            const val Compressed = 2048u
            const val Device = 64u
            const val Directory = 16u
            const val Encrypted = 16384u
            const val Hidden = 2u
            const val IntegrityStream = 32768u
            const val Normal = 128u
            const val NoScrubData = 131072u
            const val NotContentIndexed = 8192u
            const val Offline = 4096u
            const val ReadOnly = 1u
            const val ReparsePoint = 1024u
            const val SparseFile = 512u
            const val System = 4u
            const val Temporary = 256u
        }
    }

    private val root: String = Environment.getExternalStorageDirectory().path

    fun processFileAction(packet: Packet) {
        when (packet.componentType) {

            Component.CreateFileCommand -> {
                val command = packet.component(CreateFileCommand()) as CreateFileCommand
                Timber.d("Creating file at ${root + command.path!!}")
                File(root + command.path!!).createNewFile()
            }

            Component.CreateDirectoryCommand -> {
                val command = packet.component(CreateDirectoryCommand()) as CreateDirectoryCommand
                Timber.d("Creating directory at ${root + command.path!!}")
                File(root + command.path!!).mkdir()
            }

            Component.DeleteFileCommand -> {
                val command = packet.component(DeleteFileCommand()) as DeleteFileCommand
                Timber.d("Deleting file at ${root + command.path!!}")
                File(root + command.path!!).delete()
            }

            Component.RenameFileCommand -> {
                val command = packet.component(RenameFileCommand()) as RenameFileCommand
                Timber.d("Renaming file at ${root + command.oldPath!!} to ${root + command.newPath!!}")
                File(root + command.oldPath!!).renameTo(File(root + command.newPath!!))
            }

            Component.WriteFileCommand -> {
                val command = packet.component(WriteFileCommand()) as WriteFileCommand
                Timber.d("Writing ${command.dataLength} bytes to ${root + command.path!!} at offset ${command.offset}")
                writeFile(
                    root + command.path!!,
                    command.dataAsByteBuffer,
                    command.offset,
                    command.dataLength
                )
            }

            Component.SetFileInfoCommand -> {
                val command = packet.component(SetFileInfoCommand()) as SetFileInfoCommand
                setAttributes(
                    root + command.path!!,
                    command.creationTime,
                    command.lastAccessTime,
                    command.lastWriteTime,
                    command.length
                )
            }

            else -> {
                val builder = FlatBufferBuilder(256)
                when (packet.componentType) {
                    Component.ReadFileQuery -> {
                        val q = packet.component(ReadFileQuery()) as ReadFileQuery
                        val response = fileBufferToFbs(builder, root + q.path!!, q.offset, q.length)
                        sendPacket(builder, response, Component.ReadFileResponse, packet.id)
                    }

                    Component.GetFileInfoQuery -> {
                        val q = packet.component(ReadFileQuery()) as ReadFileQuery
                        val node = fileToFbs(builder, root + q.path!!)
                        val response = GetFileInfoResponse.createGetFileInfoResponse(builder, builder.createString(root + q.path!!), node)
                        sendPacket(builder, response, Component.GetFileInfoResponse, packet.id)
                    }

                    Component.GetDirectoryQuery -> {
                        val q = packet.component(ReadFileQuery()) as ReadFileQuery
                        val node = directoryToFbs(builder, root + q.path!!)
                        val response = GetDirectoryResponse.createGetDirectoryResponse(builder, builder.createString(root + q.path!!), node)
                        sendPacket(builder, response, Component.GetDirectoryResponse, packet.id)
                    }
                }
            }
        }
    }

    private fun sendPacket(
        builder: FlatBufferBuilder,
        response: Int,
        type: UByte,
        responseId: Long
    ) {
        val packet = Packet.createPacket(builder, type, response, responseId)
        builder.finishSizePrefixed(packet)
        val buffer = builder.dataBuffer()
        device.sendPacket(buffer)
    }

    private fun fileToFbs(builder: FlatBufferBuilder, path: String): Int {
        val file = File(path)

        val (crTime, laTime, lwTime, extraAttributes) = getFileAttributes(file)

        val name = builder.createString(file.name)
        return Node.createNode(
            builder,
            name,
            file.length(),
            crTime as Long,
            lwTime as Long,
            laTime as Long,
            extraAttributes as UInt,
            file.exists(),
            Node.createChildrenVector(builder, IntArray(0))
        )
    }

    private fun directoryToFbs(builder: FlatBufferBuilder, path: String): Int {
        val directory = File(path)
        val files = directory.listFiles()
        var children = IntArray(0)
        try {
            children = Array(files!!.size) { i -> fileToFbs(builder, files[i].path) }
                .toIntArray()
        } catch (e: Exception) {
            Timber.e("Exception at $path: $e")
        }
        val (crTime, laTime, lwTime, extraAttributes) = getFileAttributes(directory)

        val name = builder.createString(directory.name)
        return Node.createNode(
            builder,
            name,
            directory.length(),
            crTime as Long,
            lwTime as Long,
            laTime as Long,
            extraAttributes as UInt,
            directory.exists(),
            Node.createChildrenVector(builder, children)
        )
    }

    private fun getFileAttributes(file: File): Array<Any> {
        val lwTime = file.lastModified()
        var crTime: Long = 0
        var laTime: Long = 0

        val readOnly: Boolean = !file.canWrite()
        val isHidden: Boolean = file.isHidden
        var extraAttributes: UInt = ExtraAttribute.Normal
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val pathObj = Paths.get(file.path)
            val attributes =
                Files.getFileAttributeView(pathObj, BasicFileAttributeView::class.java)
            crTime = attributes.readAttributes().creationTime().toMillis()
            laTime = attributes.readAttributes().lastAccessTime().toMillis()
        }
        if (readOnly)
            extraAttributes = extraAttributes or ExtraAttribute.ReadOnly
        if (isHidden)
            extraAttributes = extraAttributes or ExtraAttribute.Hidden
        if (file.isDirectory)
            extraAttributes = extraAttributes or ExtraAttribute.Directory
        if (file.name.startsWith("."))
            extraAttributes = extraAttributes or ExtraAttribute.Hidden

        return arrayOf(crTime, laTime, lwTime, extraAttributes)
    }

    private fun fileBufferToFbs(
        builder: FlatBufferBuilder,
        path: String,
        offset: Long,
        length: Int
    ): Int {
        val fileChannel = RandomAccessFile(path, "r").channel
        Timber.d("Attempting to read $length bytes from $path at offset $offset")
        val byteBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, offset, length.toLong())
        val buffer = ByteBuffer.allocate(length)
        buffer.put(byteBuffer)
        fileChannel.close()
        val vector = ReadFileResponse.createDataVector(builder, buffer.array())
        val pathString = builder.createString(path)
        return ReadFileResponse.createReadFileResponse(builder, pathString, vector, offset, length)
    }

    private fun setAttributes(
        path: String,
        creationTime: Long,
        lastAccessTime: Long,
        lastWriteTime: Long,
        length: Long
    ) {
        if (length != 0L) {
            val raf = RandomAccessFile(path, "rw")
            raf.setLength(length)
            raf.close()
        }
        if (lastWriteTime != 0L) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val attrs = Files.getFileAttributeView(
                    Paths.get(path),
                    BasicFileAttributeView::class.java
                )
                attrs.setTimes(
                    FileTime.fromMillis(creationTime),
                    FileTime.fromMillis(lastAccessTime),
                    FileTime.fromMillis(lastWriteTime)
                )
            } else File(path).setLastModified(lastWriteTime)
        }
    }

    private fun writeFile(path: String, raw: ByteBuffer, offset: Long, length: Int) {
        val raf = RandomAccessFile(path, "rw")
        val actualOffset = if (offset == (-1).toLong()) raf.length() else offset
        raf.seek(actualOffset) //offset = -1 means append
        raf.write(raw.array(), raw.position(), length)
        raf.close()
    }
}