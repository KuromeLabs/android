package com.kuromelabs.kurome.models

import android.os.Environment
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import com.kuromelabs.kurome.FileSystemHandler
import com.kuromelabs.kurome.network.Link
import kotlinx.coroutines.*
import timber.log.Timber
import java.nio.ByteBuffer


@OptIn(ExperimentalUnsignedTypes::class)
@Suppress("DEPRECATION")
@Entity(tableName = "device_table")
data class Device(
    @PrimaryKey @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "id") val id: String,
) {
    @Ignore
    private val job = SupervisorJob()

    @Ignore
    private val scope = CoroutineScope(Dispatchers.IO + job)

    var isPaired = false

    @Ignore
    private var link: Link? = null

    @Ignore
    private val root = Environment.getExternalStorageDirectory().path

    @Ignore
    private var linkJob: Job? = null

    @Ignore
    private val fileSystemHandler = FileSystemHandler(root, this)

    fun isConnected(): Boolean = link != null && link!!.isConnected()

    fun setLink(link: Link) {
        linkJob?.cancel()
        Timber.d("Setting link")
        this.link = link
        linkJob = scope.launch {
            link.packetFlow.collect {
                fileSystemHandler.packetReceived(it)
            }
        }
    }

    fun disconnect() {
        link = null
        job.cancelChildren()
    }

    fun sendBuffer(buffer: ByteBuffer) {
        link!!.sendByteBuffer(buffer)
    }
}
