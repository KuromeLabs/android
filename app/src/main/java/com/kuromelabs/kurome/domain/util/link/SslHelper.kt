package com.kuromelabs.kurome.domain.util.link

import android.content.Context
import com.kuromelabs.kurome.domain.util.IdentityProvider
import org.bouncycastle.asn1.x500.X500NameBuilder
import org.bouncycastle.asn1.x500.style.BCStyle
import org.bouncycastle.cert.X509v3CertificateBuilder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey
import java.security.cert.X509Certificate
import java.time.LocalDate
import java.time.ZoneId
import java.util.*


object SslHelper {
    var certificate: X509Certificate? = null
    lateinit var privateKey: PrivateKey
    lateinit var publicKey: PublicKey
    private val BC = BouncyCastleProvider()
    fun initializeSsl(context: Context) {
        initializeKeys()
        val nameBuilder = X500NameBuilder(BCStyle.INSTANCE)
        nameBuilder.addRDN(BCStyle.CN, IdentityProvider.getGuid(context))
        nameBuilder.addRDN(BCStyle.OU, "Kurome")
        nameBuilder.addRDN(BCStyle.O, "Kurome Labs")
        val certificateBuilder: X509v3CertificateBuilder = JcaX509v3CertificateBuilder(
            nameBuilder.build(),
            BigInteger.ONE,
            Date.from(LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant()),
            Date.from(
                LocalDate.now().plusYears(20).atStartOfDay(ZoneId.systemDefault())
                    .toInstant()
            ),
            nameBuilder.build(),
            publicKey
        )
        val contentSigner =
            JcaContentSignerBuilder("SHA256WithRSAEncryption").setProvider(BC).build(privateKey)
        certificate = JcaX509CertificateConverter().setProvider(BC)
            .getCertificate(certificateBuilder.build(contentSigner))

    }

    private fun initializeKeys() {
        val keyGen = KeyPairGenerator.getInstance("RSA")
        keyGen.initialize(2048)
        val keyPair = keyGen.genKeyPair()

        publicKey = keyPair.public
        privateKey = keyPair.private

    }
}