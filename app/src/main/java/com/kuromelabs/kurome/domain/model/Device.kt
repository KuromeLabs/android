package com.kuromelabs.kurome.domain.model

import android.os.Environment
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import com.kuromelabs.kurome.domain.util.FileSystemHandler
import com.kuromelabs.kurome.domain.util.PairingHandler
import com.kuromelabs.kurome.domain.util.link.Link
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kurome.Action
import timber.log.Timber
import java.nio.ByteBuffer


@OptIn(ExperimentalUnsignedTypes::class)
@Entity(tableName = "device_table")
data class Device(
    @PrimaryKey @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "id") val id: String,
    @Ignore val root: String
) {
    constructor(name: String, id: String) : this(
        name,
        id,
        Environment.getExternalStorageDirectory().path
    )

    @Ignore
    private val job = SupervisorJob()

    @Ignore
    private val scope = CoroutineScope(Dispatchers.IO + job)

    var isPaired = false

    @Ignore
    private var link: Link? = null

    @Ignore
    private var linkJob: Job? = null

    @Ignore
    private val fileSystemHandler = FileSystemHandler(root, this)

    @Ignore
    private val pairingHandler = PairingHandler(this)

    fun isConnected(): Boolean = link != null && link!!.isConnected()

    fun setLink(link: Link) {
        linkJob?.cancel()
        Timber.d("Setting link")
        this.link = link
        linkJob = scope.launch {
            link.packetFlow.collect {
                if (it.action == Action.actionPair)
                    pairingHandler.packetReceived(it)
                else
                    fileSystemHandler.packetReceived(it)
            }
        }
    }

    suspend fun requestPairing(): Flow<PairingHandler.PairingType> {
        pairingHandler.requestPairing()
        return pairingHandler.pairingHandlerFlow
    }

    fun disconnect() {
        link = null
        job.cancelChildren()
    }

    fun sendBuffer(buffer: ByteBuffer) {
        link!!.sendByteBuffer(buffer)
    }
}