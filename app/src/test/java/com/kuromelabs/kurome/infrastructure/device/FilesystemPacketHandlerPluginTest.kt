package com.kuromelabs.kurome.infrastructure.device

import android.os.Environment
import com.kuromelabs.kurome.KuromeApplication
import com.kuromelabs.kurome.infrastructure.common.PacketHelpers
import com.kuromelabs.kurome.infrastructure.network.LinkPacket
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import junit.framework.Assert.assertEquals
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.attribute.BasicFileAttributeView

class FilesystemPacketHandlerPluginTest {
    private lateinit var testFsRoot: String
    private val packetFlow = MutableSharedFlow<LinkPacket>(replay=1)
    private lateinit var handle: DeviceHandle
    @BeforeEach
    fun setUp() {
        testFsRoot = "./kuromefstest/"
        File(testFsRoot).deleteRecursively()
        File(testFsRoot).mkdir()
        mockkStatic(Environment::getExternalStorageDirectory)
        every { Environment.getExternalStorageDirectory() } returns mockk {
            every { path } returns testFsRoot
        }
        handle = mockk<DeviceHandle>(relaxed = true) {
            every { link } returns mockk(relaxed = true) {
                every { send(any()) } returns Unit
                coEvery { receivedPackets } coAnswers { packetFlow.asSharedFlow() }
                every { localScope } returns CoroutineScope(Dispatchers.Unconfined)  // Run instantly on current thread
            }
        }
    }

    @AfterEach
    fun tearDown() {
        File(testFsRoot).deleteRecursively()
    }

    @Test
    fun `test create file packet creates a file`() = runTest {
        val filename = "test.txt"
        val packet = PacketHelpers.getCreateFileCommandPacket(filename, 128u)
        val plugin = FilesystemPacketHandlerPlugin(handle)
        plugin.start()
        packetFlow.emit(LinkPacket.success(packet))
        val createdFile = File(testFsRoot + filename)
        assert(createdFile.exists())
        assert(createdFile.isFile)
    }

    @Test
    fun `test create file packet with non existent parent dir fails silently`() = runTest {
        val filename = "/one/two/three/test.txt"
        val packet = PacketHelpers.getCreateFileCommandPacket(filename, 128u)
        val plugin = FilesystemPacketHandlerPlugin(handle)
        assertDoesNotThrow { 
            plugin.start()
            packetFlow.emit(LinkPacket.success(packet))
        }
    }

    @Test
    fun `test create directory packet creates a directory`() = runTest {
        val dirName = "testDir"
        val packet = PacketHelpers.getCreateDirectoryCommandPacket(dirName)
        val plugin = FilesystemPacketHandlerPlugin(handle)
        plugin.start()
        packetFlow.emit(LinkPacket.success(packet))
        val createdDir = File(testFsRoot + dirName)
        assert(createdDir.exists())
        assert(createdDir.isDirectory)
    }

    @Test
    fun `test delete file packet deletes a file`() = runTest {
        val filename = "test.txt"
        val createdFile = File(testFsRoot + filename)
        createdFile.createNewFile()
        assert(createdFile.exists())
        val packet = PacketHelpers.getDeleteFileCommandPacket(filename)
        val plugin = FilesystemPacketHandlerPlugin(handle)
        plugin.start()
        packetFlow.emit(LinkPacket.success(packet))
        assert(!createdFile.exists())
    }

    @Test
    fun `test delete file packet deletes a directory`() = runTest {
        val filename = "test"
        val createdDir = File(testFsRoot + filename)
        createdDir.mkdir()
        assert(createdDir.exists())
        val packet = PacketHelpers.getDeleteFileCommandPacket(filename)
        val plugin = FilesystemPacketHandlerPlugin(handle)
        plugin.start()
        packetFlow.emit(LinkPacket.success(packet))
        assert(!createdDir.exists())
    }


    @Test
    fun `test rename file packet renames a file`() = runTest {
        val oldFileName = "test1.txt"
        val newFileName = "test2.txt"
        val createdFile = File(testFsRoot + oldFileName)
        createdFile.createNewFile()
        assert(createdFile.exists())
        val packet = PacketHelpers.getRenameFileCommandPacket(oldFileName, newFileName)
        val plugin = FilesystemPacketHandlerPlugin(handle)
        plugin.start()
        packetFlow.emit(LinkPacket.success(packet))
        assert(!createdFile.exists())
        assert(File(testFsRoot + newFileName).exists())
    }

    @Test
    fun `test rename file packet renames a directory`() = runTest {
        val oldDirName = "test1"
        val newDirName = "test2"
        val createdDir = File(testFsRoot + oldDirName)
        createdDir.mkdir()
        assert(createdDir.exists())
        val packet = PacketHelpers.getRenameFileCommandPacket(oldDirName, newDirName)
        val plugin = FilesystemPacketHandlerPlugin(handle)
        plugin.start()
        packetFlow.emit(LinkPacket.success(packet))
        assert(!createdDir.exists())
        assert(File(testFsRoot + newDirName).exists())
    }

    @Test
    fun `test rename file packet with null new path fails silently`() = runTest {
        val oldDirName = "test1"
        val newDirName = null
        val createdDir = File(testFsRoot + oldDirName)
        createdDir.mkdir()
        assert(createdDir.exists())
        val packet = PacketHelpers.getRenameFileCommandPacket(oldDirName, newDirName)
        val plugin = FilesystemPacketHandlerPlugin(handle)
        assertDoesNotThrow { plugin.start()
            packetFlow.emit(LinkPacket.success(packet)) }
    }

    @Test
    fun `test write file packet writes contents to file`() = runTest {
        val fileName = "test.txt"
        val sampleDataBytes = "test1!".toByteArray()
        val createdFile = File(testFsRoot + fileName)
        createdFile.createNewFile()
        assert(createdFile.exists())
        
        val packet = PacketHelpers.getWriteFileCommandPacket(fileName, sampleDataBytes, 0)
        val plugin = FilesystemPacketHandlerPlugin(handle)
        plugin.start()
        packetFlow.emit(LinkPacket.success(packet))
        val fileContents = createdFile.readBytes()
        assert(fileContents.contentEquals(sampleDataBytes))

        // write at different offset
        val sampleDataBytes2 = "test2!".toByteArray()
        val packet2 = PacketHelpers.getWriteFileCommandPacket(fileName, sampleDataBytes2, 6)
        packetFlow.emit(LinkPacket.success(packet2))
        val fileContents2 = createdFile.readBytes()
        assert(fileContents2.contentEquals("test1!test2!".toByteArray()))

        // write at append (-1) offset)
        val sampleDataBytes3 = "test3!".toByteArray()
        val packet3 = PacketHelpers.getWriteFileCommandPacket(fileName, sampleDataBytes3, -1)
        packetFlow.emit(LinkPacket.success(packet3))
        val fileContents3 = createdFile.readBytes()
        assert(fileContents3.contentEquals("test1!test2!test3!".toByteArray()))
        assertEquals(fileContents3.size, "test1!test2!test3!".length)

        // overwrite in the middle
        val sampleDataBytes4 = "test425!".toByteArray()
        val packet4 = PacketHelpers.getWriteFileCommandPacket(fileName, sampleDataBytes4, 6)
        packetFlow.emit(LinkPacket.success(packet4))
        val fileContents4 = createdFile.readBytes()
        assert(fileContents4.contentEquals("test1!test425!st3!".toByteArray()))
    }

    @Test
    fun `test write file packet with IO error fails silently`() = runTest {
        val fileName = "test.txt"
        val sampleDataBytes = "test1!".toByteArray()
        val createdFile = File(testFsRoot + fileName)
        createdFile.createNewFile()
        assert(createdFile.exists())
        
        // less than -1 offset will throw IOException
        val packet = PacketHelpers.getWriteFileCommandPacket(fileName, sampleDataBytes, -2)
        val plugin = FilesystemPacketHandlerPlugin(handle)
        assertDoesNotThrow {         
            plugin.start()
            packetFlow.emit(LinkPacket.success(packet)) }
    }

    @Test
    fun `test set file attributes android oreo+`() = runTest {
        mockkObject(KuromeApplication.Companion)
        every { KuromeApplication.getBuildVersion() } returns 26
        val fileName = "test.txt"
        val createdFile = File(testFsRoot + fileName)
        createdFile.createNewFile()
        assert(createdFile.exists())
        
        val packet = PacketHelpers.getSetFileAttributesCommandPacket(fileName, 1, 2, 3, 4, 32u)
        val plugin = FilesystemPacketHandlerPlugin(handle)
        plugin.start()
        packetFlow.emit(LinkPacket.success(packet))
        // assert file has cTime, lwTime and laTime since they are supported in Android oreo+
        val updatedFile = File(testFsRoot + fileName)
        assertEquals(updatedFile.length(), 1)
        val attributes =
            Files.getFileAttributeView(Paths.get(updatedFile.path), BasicFileAttributeView::class.java)
                .readAttributes()
        // this is brittle because setting cTime is not supported in Linux
//        assertEquals(attributes.creationTime().toMillis(), 2)
        assertEquals(attributes.lastAccessTime().toMillis(), 3)
        assertEquals(updatedFile.lastModified(), 4)
    }

    @Test
    fun `test set file attributes android nougat-`() = runTest {
        mockkObject(KuromeApplication.Companion)
        every { KuromeApplication.getBuildVersion() } returns 25
        val fileName = "test.txt"
        val createdFile = File(testFsRoot + fileName)
        createdFile.createNewFile()
        assert(createdFile.exists())
        
        val packet = PacketHelpers.getSetFileAttributesCommandPacket(fileName, 1, 2, 3, 4, 32u)
        val plugin = FilesystemPacketHandlerPlugin(handle)
        plugin.start()
        packetFlow.emit(LinkPacket.success(packet))
        // assert that we don't set laTime and cTime since they're not supported in Android nougat-
        val updatedFile = File(testFsRoot + fileName)
        assertEquals(updatedFile.length(), 1)
        val attributes =
            Files.getFileAttributeView(Paths.get(updatedFile.path), BasicFileAttributeView::class.java)
                .readAttributes()
        // this is brittle because setting cTime is not supported in Linux
//        assertNotEquals(attributes.creationTime().toMillis(), 2)
        assertNotEquals(attributes.lastAccessTime().toMillis(), 3)
        assertEquals(updatedFile.lastModified(), 4)
    }

    @Test
    fun `test set file attributes does not set length when length = 0`() = runTest {
        mockkObject(KuromeApplication.Companion)
        every { KuromeApplication.getBuildVersion() } returns 26
        val fileName = "test.txt"
        val createdFile = File(testFsRoot + fileName)
        createdFile.writeText("test")
        createdFile.createNewFile()
        assert(createdFile.exists())
        
        val packet = PacketHelpers.getSetFileAttributesCommandPacket(fileName, 0, 2, 3, 4, 32u)
        val plugin = FilesystemPacketHandlerPlugin(handle)
        plugin.start()
        packetFlow.emit(LinkPacket.success(packet))
        // assert that we don't set laTime and cTime since they're not supported in Android nougat-
        val updatedFile = File(testFsRoot + fileName)
        assertNotEquals(updatedFile.length(), 0)
    }


    @Test
    fun `test set file attributes file times are not set when lastWriteTime = 0`() = runTest {
        mockkObject(KuromeApplication.Companion)
        every { KuromeApplication.getBuildVersion() } returns 26
        val fileName = "test.txt"
        val createdFile = File(testFsRoot + fileName)
        createdFile.createNewFile()
        assert(createdFile.exists())
        
        val packet = PacketHelpers.getSetFileAttributesCommandPacket(fileName, 1, 2, 3, 0, 32u)
        val plugin = FilesystemPacketHandlerPlugin(handle)
        assertDoesNotThrow {         plugin.start()
            packetFlow.emit(LinkPacket.success(packet)) }
        val updatedFile = File(testFsRoot + fileName)
        assertEquals(updatedFile.length(), 1)
        val attributes =
            Files.getFileAttributeView(Paths.get(updatedFile.path), BasicFileAttributeView::class.java)
                .readAttributes()
        // this is brittle because setting cTime is not supported in Linux
//        assertNotEquals(attributes.creationTime().toMillis(), 2)
        assertNotEquals(attributes.lastAccessTime().toMillis(), 3)
        assertNotEquals(updatedFile.lastModified(), 4)
    }

    @Test
    fun `test set file attributes with IO error fails silently`() = runTest {
        mockkObject(KuromeApplication.Companion)
        every { KuromeApplication.getBuildVersion() } throws IOException()
        val fileName = "test.txt"
        val createdFile = File(testFsRoot + fileName)
        createdFile.writeText("test")
        createdFile.createNewFile()
        assert(createdFile.exists())
        
        val packet = PacketHelpers.getSetFileAttributesCommandPacket(fileName, 1, 2, 3, 4, 32u)
        val plugin = FilesystemPacketHandlerPlugin(handle)
        assertDoesNotThrow {         plugin.start()
            packetFlow.emit(LinkPacket.success(packet)) }
    }
}