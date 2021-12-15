package com.kuromelabs.kurome.database

import androidx.annotation.WorkerThread
import com.kuromelabs.kurome.models.Device
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import timber.log.Timber
import java.net.*


class DeviceRepository(private val deviceDao: DeviceDao) {
    val savedDevices: Flow<List<Device>> = deviceDao.getAllDevices()
    private val networkDevices: Flow<List<Device>> = availableDevices()
    private val connectedDevices: MutableSharedFlow<List<Device>> = MutableSharedFlow(1)
    private val _connectedDevices: SharedFlow<List<Device>> = connectedDevices.onSubscription {
        this.emit(emptyList())
    }

    @WorkerThread
    suspend fun insert(device: Device) {
        deviceDao.insert(device)
    }

    fun get(id: String): Device? {
        return deviceDao.getDevice(id)
    }

    private fun availableDevices():
            Flow<List<Device>> = flow {
        val buffer = ByteArray(1024)
        val packet = DatagramPacket(buffer, buffer.size)
        emit(emptyList()) //to get instantaneous first combine
        val set = HashSet<Device>()
        val socket = MulticastSocket(33586)
        val group = InetAddress.getByName("235.132.20.12")
        socket.joinGroup(group)
        while (currentCoroutineContext().job.isActive) {
            socket.soTimeout = 3000
            try {
                withTimeout(2000) {
                    while (isActive) {
                        socket.receive(packet)
                        val msg = String(packet.data, packet.offset, packet.length)
                        val device = Device(msg.split(':')[2], msg.split(':')[3])
                        device.ip = msg.split(':')[1]
                        set.add(device)
                    }
                    yield()
                }
            } catch (e: TimeoutCancellationException) {
                Timber.d("emitting network devices $set")
                emit(ArrayList(set))
                set.clear()
            } catch (e: SocketTimeoutException) {
                Timber.d("UDP socket timeout: $set")
                set.clear()
                emit(ArrayList(set))
            } catch (e: SocketException) {
                e.printStackTrace()
                Timber.i("UDP socket failed: $set")
                set.clear()
                emit(ArrayList(set))
                delay(2000)
            }
        }
    }.flowOn(Dispatchers.IO)


    fun combineDevices(): Flow<List<Device>> =
        combine(savedDevices, networkDevices, _connectedDevices) { saved, network, connected ->
            val set = HashSet<Device>()
            connected.forEach {
                Timber.d("flowing connected device: $it")
                it.isConnected = true
                set.add(it)
            }
            saved.forEach { set.add(it) }
            network.forEach { if (it !in saved) set.add(it) }
            Timber.d("emitting combined devices $set")
            ArrayList(set)
        }

    suspend fun setConnectedDevices(list: List<Device>) {
        Timber.d("emitting connected devices $list")
        connectedDevices.emit(list)
    }
}