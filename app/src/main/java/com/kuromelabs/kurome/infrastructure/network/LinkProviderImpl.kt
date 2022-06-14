package com.kuromelabs.kurome.infrastructure.network

import com.kuromelabs.kurome.application.interfaces.IdentityProvider
import com.kuromelabs.kurome.application.interfaces.Link
import com.kuromelabs.kurome.application.interfaces.LinkProvider
import com.kuromelabs.kurome.application.interfaces.SecurityService
import timber.log.Timber
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyPair
import java.security.KeyStore
import java.security.cert.X509Certificate
import javax.inject.Inject
import javax.net.ssl.*

class LinkProviderImpl @Inject constructor(
    var identityProvider: IdentityProvider,
    var securityService: SecurityService<X509Certificate, KeyPair>
) : LinkProvider<Socket> {
    override suspend fun createClientLink(connectionInfo: String): Link {
        val split = connectionInfo.split(':')
        val ip = split[0]
        val port = Integer.parseInt(split[1])
        val socket = Socket()
        socket.reuseAddress = true
        sendIdentity(socket, ip, port)
        Timber.d("Sent identity")

        val sslSocket = upgradeToSslSocket(socket, true)



        return LinkImpl(sslSocket)
    }

    override suspend fun createServerLink(client: Socket): Link {
        TODO("Not yet implemented")
    }

    private fun upgradeToSslSocket(socket: Socket, clientMode: Boolean): SSLSocket {
        val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
            override fun getAcceptedIssuers(): Array<X509Certificate?> {
                return arrayOfNulls(0)
            }

            override fun checkClientTrusted(certs: Array<X509Certificate>, authType: String) {}
            override fun checkServerTrusted(certs: Array<X509Certificate>, authType: String) {}
        })


        val keyStore = KeyStore.getInstance(KeyStore.getDefaultType())
        keyStore.load(null, null)
        keyStore.setKeyEntry(
            "key",
            securityService.getKeys()!!.private,
            "".toCharArray(),
            arrayOf(securityService.getSecurityContext())
        )

        val keyManagerFactory =
            KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        keyManagerFactory.init(keyStore, "".toCharArray())


        val sslContext = SSLContext.getInstance("TLSv1.2")
        sslContext.init(keyManagerFactory.keyManagers, trustAllCerts, java.security.SecureRandom())
        val sslSocket: SSLSocket = sslContext.socketFactory.createSocket(
            socket, socket.inetAddress.hostAddress, socket.port, true
        ) as SSLSocket

        if (clientMode) {
            sslSocket.useClientMode = true
        } else {
            sslSocket.useClientMode = false
            sslSocket.needClientAuth = false
            sslSocket.wantClientAuth = false
        }
        sslSocket.startHandshake()
        return sslSocket
    }

    private fun sendIdentity(socket: Socket, ip: String, port: Int) {

        socket.connect(InetSocketAddress(ip, port))
        val channel = socket.getOutputStream()
        val identity =
            "${identityProvider.getEnvironmentId()}:${identityProvider.getEnvironmentName()}"
        val identityBytes = identity.toByteArray()
        val buffer = ByteBuffer.allocate(identityBytes.size + 4)
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(identityBytes.size)
        buffer.put(identityBytes)
        channel.write(buffer.array())
    }
}