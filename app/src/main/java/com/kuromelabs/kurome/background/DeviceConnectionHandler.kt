package com.kuromelabs.kurome.background

import com.kuromelabs.kurome.application.interfaces.LinkProvider
import com.kuromelabs.kurome.application.use_case.device.DeviceUseCases
import timber.log.Timber
import java.net.Socket
import javax.inject.Inject

class DeviceConnectionHandler @Inject constructor(
    var linkProvider: LinkProvider<Socket>,
    var deviceUseCases: DeviceUseCases
) {
    suspend fun handleClientConnection(name: String, id: String, ip: String, port: Int) {
        val result = deviceUseCases.connect(ip, port)

        if (result.isSuccess) {
            Timber.d("Connected to $ip:$port")
            deviceUseCases.monitor(result.getOrNull()!!, id, name)
        } else {
            Timber.e("Failed to connect to $ip:$port")
        }
    }
}