package com.kuromelabs.kurome.infrastructure.network

import android.annotation.SuppressLint
import com.kuromelabs.kurome.application.interfaces.SecurityService
import java.net.Socket
import java.security.KeyPair
import java.security.KeyStore
import java.security.cert.X509Certificate
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

class NetworkHelper(private val securityService: SecurityService<X509Certificate, KeyPair>) {
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
        val keyStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
            load(null, null)
            setKeyEntry("key", securityService.getKeys()!!.private, "".toCharArray(), arrayOf(securityService.getSecurityContext()))
            certificate?.let { setCertificateEntry("cert", it) }
        }

        val keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply {
            init(keyStore, "".toCharArray())
        }

        val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply {
            init(keyStore)
        }

        return SSLContext.getInstance("TLSv1.2").apply {
            init(
                keyManagerFactory.keyManagers,
                if (certificate == null) arrayOf<TrustManager>(TrustAllManager()) else trustManagerFactory.trustManagers,
                java.security.SecureRandom()
            )
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