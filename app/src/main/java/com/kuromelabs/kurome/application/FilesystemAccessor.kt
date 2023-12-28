package com.kuromelabs.kurome.application

import android.os.Build
import android.os.Environment
import com.google.flatbuffers.FlatBufferBuilder
import com.kuromelabs.kurome.domain.Device
import kurome.fbs.Attributes
import kurome.fbs.Component
import kurome.fbs.Create
import kurome.fbs.Delete
import kurome.fbs.FileCommand
import kurome.fbs.FileCommandType
import kurome.fbs.FileQuery
import kurome.fbs.FileQueryType
import kurome.fbs.FileResponeType
import kurome.fbs.FileStatus
import kurome.fbs.FileType
import kurome.fbs.Packet
import kurome.fbs.Rename
import kurome.fbs.SetAttributes
import kurome.fbs.Write
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

            Component.FileQuery -> {
                val builder = FlatBufferBuilder(256)
                val fileQuery = packet.component(FileQuery()) as FileQuery
                val response = processFileQuery(builder, fileQuery)
                sendPacket(builder, response, Component.FileResponse, packet.id)
            }

            Component.FileCommand -> {
                val fileCommand = packet.component(FileCommand()) as FileCommand
                processFileCommand(fileCommand)
            }
        }
    }

    private fun sendPacket(
        builder: FlatBufferBuilder,
        response: Int,
        type: UByte,
        responseId: Long
    ) {
        val packet = flatBufferHelper.createPacket(builder, response, type, responseId)
        val buffer = flatBufferHelper.finishBuilding(builder, packet)
        device.sendPacket(buffer)
    }

    private fun processFileQuery(builder: FlatBufferBuilder, q: FileQuery): Int {
        var response = 0
        var type = FileResponeType.Node
        when (q.type) {
            FileQueryType.GetDirectory -> response = directoryToFbs(builder, root + q.path!!)
            FileQueryType.ReadFile -> {
                type = FileResponeType.Raw
                response = fileBufferToFbs(builder, root + q.path!!, q.offset, q.length)
            }
        }
        return flatBufferHelper.createFileResponse(builder, response, type)
    }

    private fun fileToFbs(builder: FlatBufferBuilder, path: String): Int {
        val file = File(path)
        val status =
            if (file.exists())
                FileStatus.Exists
            else if (!File(path.substring(0, path.lastIndexOf("/"))).exists())
                FileStatus.PathNotFound
            else FileStatus.FileNotFound

        val (crTime, laTime, lwTime, extraAttributes) = getFileAttributes(file)

        return flatBufferHelper.createNode(
            builder,
            file.name,
            status,
            file.length(),
            crTime as Long,
            lwTime as Long,
            laTime as Long,
            extraAttributes as UInt
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

        return flatBufferHelper.createNode(
            builder,
            directory.name,
            FileStatus.Exists,
            directory.length(),
            crTime as Long,
            lwTime as Long,
            laTime as Long,
            extraAttributes as UInt,
            children
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

    private fun fileBufferToFbs(builder: FlatBufferBuilder, path: String, offset: Long, length: Int): Int {
        val fileChannel = RandomAccessFile(path, "r").channel
        Timber.d("Attempting to read $length bytes from $path at offset $offset")
        val byteBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, offset, length.toLong())
        val buffer = ByteBuffer.allocate(length)
        buffer.put(byteBuffer)
        fileChannel.close()
        return flatBufferHelper.createRaw(builder, buffer.array(), offset, length)
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

            FileCommandType.SetAttributes -> {
                val setAttributes = (command.command(SetAttributes()) as SetAttributes)
                setAttributes(root + setAttributes.path, setAttributes.attributes!!)
            }
        }
    }

    private fun setAttributes(path: String, attributes: Attributes) {
        if (attributes.length != 0L) {
            val raf = RandomAccessFile(path, "rw")
            raf.setLength(attributes.length)
            raf.close()
        }
        if (attributes.lastWriteTime != 0L) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val attrs = Files.getFileAttributeView(
                    Paths.get(path),
                    BasicFileAttributeView::class.java
                )
                attrs.setTimes(
                    FileTime.fromMillis(attributes.creationTime),
                    FileTime.fromMillis(attributes.lastAccessTime),
                    FileTime.fromMillis(attributes.lastWriteTime)
                )
            } else File(path).setLastModified(attributes.lastWriteTime)
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