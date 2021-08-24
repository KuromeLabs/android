package com.kuromelabs.kurome.database

import android.util.Log
import androidx.annotation.WorkerThread
import com.kuromelabs.kurome.models.Device
import com.kuromelabs.kurome.services.ForegroundConnectionService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.MulticastSocket
import java.net.SocketTimeoutException


class DeviceRepository(private val deviceDao: DeviceDao) {
    val savedDevices: Flow<List<Device>> = deviceDao.getAllDevices()
    val networkDevices: Flow<List<Device>> = availableDevices()
    private val connectedDevices: MutableSharedFlow<List<Device>> = MutableSharedFlow(1)
    val _connectedDevices: SharedFlow<List<Device>> = connectedDevices.onSubscription {
        this.emit(emptyList())
    }

    @WorkerThread
    suspend fun insert(device: Device) {
        deviceDao.insert(device)
    }

    fun get(id: String): Device? {
        return deviceDao.getDevice(id)
    }

    private fun availableDevices(): Flow<List<Device>> = flow {
        emit(emptyList()) //to get instantaneous first combine
        val list = HashSet<Device>()
        while (currentCoroutineContext().job.isActive) {
            val socket = MulticastSocket(33586)
            socket.soTimeout = 6000
            val group = InetAddress.getByName("235.132.20.12")
            socket.joinGroup(group)
            try {
                withTimeout(5000) {
                    while (currentCoroutineContext().job.isActive) {
                        val buffer = ByteArray(1024)
                        val packet = DatagramPacket(buffer, buffer.size)
                        try {
                            socket.receive(packet)
                            val msg = String(packet.data, packet.offset, packet.length)
                            val device = Device(msg.split(':')[2], msg.split(':')[3])
                            list.add(device)
                        } catch (e: SocketTimeoutException){
                            Log.e("kurome/devicerepository", "UDP socket timeout")
                        }
                    }
                }
            } catch (e: TimeoutCancellationException) {
                socket.close()
                list.clear()
                emit(ArrayList(list))
            }
        }
    }.flowOn(Dispatchers.IO)


    fun combineDevices(): Flow<List<Device>> =
       combine(savedDevices, networkDevices, _connectedDevices) { saved, network, connected ->
            val set = HashSet<Device>()
           Log.e("kurome/devicerepository",connected.toString())
            connected.forEach {if (it in saved) set.add(it) }
            saved.forEach {set.add(it)}
            network.forEach { if (it !in saved) set.add(it) }
            ArrayList(set)
        }
    suspend fun setConnectedDevices(list: List<Device>){
        connectedDevices.emit(list)
    }
}