package com.kuromelabs.kurome.infrastructure.network

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.kuromelabs.kurome.application.interfaces.SecurityService
import com.kuromelabs.kurome.infrastructure.device.IdentityProvider
import timber.log.Timber
import java.math.BigInteger
import java.net.Socket
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.Principal
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import javax.inject.Inject
import javax.net.ssl.KeyManager
import javax.net.ssl.X509ExtendedKeyManager
import javax.security.auth.x500.X500Principal

class SslService @Inject constructor(
    private val identityProvider: IdentityProvider
) : SecurityService<X509Certificate, KeyManager> {

    private companion object {
        const val PROVIDER = "AndroidKeyStore"

        const val ALIAS = "kurome-identity-v2"
        val SUPERSEDED_ALIASES = listOf("kurome-identity")
    }

    private val keyStore: KeyStore = KeyStore.getInstance(PROVIDER).apply { load(null) }
    private val certificate: X509Certificate = existingCertificate() ?: createIdentity()

    init {
        SUPERSEDED_ALIASES.forEach { alias ->
            runCatching { if (keyStore.containsAlias(alias)) keyStore.deleteEntry(alias) }
                .onSuccess { Timber.d("Removed superseded keystore entry $alias") }
        }
    }

    override fun getSecurityContext(): X509Certificate = certificate

    override fun getKeys(): KeyManager =
        SingleIdentityKeyManager(ALIAS, arrayOf(certificate), privateKey)

    private val privateKey: PrivateKey
        get() = keyStore.getKey(ALIAS, null) as PrivateKey

    private class SingleIdentityKeyManager(
        private val alias: String,
        private val chain: Array<X509Certificate>,
        private val key: PrivateKey
    ) : X509ExtendedKeyManager() {
        override fun chooseClientAlias(
            keyType: Array<out String>?, issuers: Array<out Principal>?, socket: Socket?
        ): String = alias

        override fun chooseServerAlias(
            keyType: String?, issuers: Array<out Principal>?, socket: Socket?
        ): String = alias

        override fun getClientAliases(
            keyType: String?, issuers: Array<out Principal>?
        ): Array<String> = arrayOf(alias)

        override fun getServerAliases(
            keyType: String?, issuers: Array<out Principal>?
        ): Array<String> = arrayOf(alias)

        override fun getCertificateChain(alias: String?): Array<X509Certificate> = chain

        override fun getPrivateKey(alias: String?): PrivateKey = key
    }

    private fun existingCertificate(): X509Certificate? {
        return try {
            if (!keyStore.isKeyEntry(ALIAS)) return null
            val certificate = keyStore.getCertificate(ALIAS) as? X509Certificate ?: return null

            val id = identityProvider.getEnvironmentId()
            if (!certificate.subjectX500Principal.name.contains(id)) {
                Timber.w("Stored certificate does not belong to $id; generating a new identity")
                keyStore.deleteEntry(ALIAS)
                return null
            }

            Timber.d("Loaded the TLS identity from the Android keystore")
            certificate
        } catch (e: Exception) {
            Timber.w(e, "Could not read the stored TLS identity; generating a new one")
            null
        }
    }

    private fun createIdentity(): X509Certificate {
        val id = identityProvider.getEnvironmentId()
        val notBefore = Date.from(LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant())
        val notAfter = Date.from(
            LocalDate.now().plusYears(20).atStartOfDay(ZoneId.systemDefault()).toInstant()
        )

        val spec = KeyGenParameterSpec.Builder(
            ALIAS,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY or
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setKeySize(2048)
            .setDigests(
                KeyProperties.DIGEST_NONE,
                KeyProperties.DIGEST_SHA256,
                KeyProperties.DIGEST_SHA384,
                KeyProperties.DIGEST_SHA512
            )
            .setSignaturePaddings(
                KeyProperties.SIGNATURE_PADDING_RSA_PKCS1,
                KeyProperties.SIGNATURE_PADDING_RSA_PSS
            )
            .setEncryptionPaddings(
                KeyProperties.ENCRYPTION_PADDING_NONE,
                KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1
            )
            .setRandomizedEncryptionRequired(false)
            .setCertificateSubject(X500Principal("CN=$id, OU=Kurome, O=Kurome Labs"))
            .setCertificateSerialNumber(BigInteger(64, SecureRandom()))
            .setCertificateNotBefore(notBefore)
            .setCertificateNotAfter(notAfter)
            .build()

        KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, PROVIDER).apply {
            initialize(spec)
            generateKeyPair()
        }

        keyStore.load(null)
        Timber.i("Generated a new TLS identity in the Android keystore")
        return keyStore.getCertificate(ALIAS) as X509Certificate
    }
}
