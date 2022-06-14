package com.kuromelabs.kurome.background

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import java.net.ServerSocket
import javax.inject.Inject


class TcpListenerService @Inject constructor(var scope: CoroutineScope) {
    private val tcpPort = 33587


    fun start() {
        scope.launch {
            val tcpListener = ServerSocket(tcpPort)
            while (currentCoroutineContext().isActive) {
                val socket = tcpListener.accept()
                Timber.d("Incoming connection from ${socket.inetAddress}")
            }
        }
    }
}