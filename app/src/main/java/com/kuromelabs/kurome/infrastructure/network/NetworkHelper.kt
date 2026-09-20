package com.kuromelabs.kurome.infrastructure.network

import android.annotation.SuppressLint
import com.kuromelabs.kurome.application.interfaces.SecurityService
import java.net.Socket
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.KeyManager
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

class NetworkHelper(private val securityService: SecurityService<X509Certificate, KeyManager>) {
    fun upgradeToSslSocket(socket: Socket, clientMode: Boolean, certificate: X509Certificate?): SSLSocket {
        val sslContext = createSslContext(certificate)
        val sslSocket = sslContext.socketFactory.createSocket(
            socket, socket.inetAddress.hostAddress, socket.port, true
        ) as SSLSocket

        configureSslSocket(sslSocket, clientMode)
        sslSocket.startHandshake()
        return sslSocket
    }

    private fun createSslContext(certificate: X509Certificate?): SSLContext {
        val trustManagers: Array<TrustManager> = if (certificate == null) {
            arrayOf(TrustAllManager())
        } else {
            val trustStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
                load(null, null)
                setCertificateEntry("peer", certificate)
            }
            TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply {
                init(trustStore)
            }.trustManagers
        }

        return SSLContext.getInstance("TLSv1.2").apply {
            init(arrayOf(securityService.getKeys()), trustManagers, SecureRandom())
        }
    }

    private fun configureSslSocket(sslSocket: SSLSocket, clientMode: Boolean) {
        sslSocket.apply {
            useClientMode = clientMode
            soTimeout = 3000
            if (!clientMode) {
                needClientAuth = false
                wantClientAuth = false
            }
            soTimeout = 0
        }
    }

    @SuppressLint("TrustAllX509TrustManager", "CustomX509TrustManager")
    private class TrustAllManager : X509TrustManager {
        override fun getAcceptedIssuers(): Array<X509Certificate?> = arrayOfNulls(0)
        override fun checkClientTrusted(certs: Array<X509Certificate>, authType: String) {}
        override fun checkServerTrusted(certs: Array<X509Certificate>, authType: String) {}
    }
}