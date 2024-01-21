package com.kuromelabs.kurome.infrastructure.network

import com.kuromelabs.kurome.application.interfaces.SecurityService
import com.kuromelabs.kurome.infrastructure.device.IdentityProvider
import org.bouncycastle.asn1.x500.X500NameBuilder
import org.bouncycastle.asn1.x500.style.BCStyle
import org.bouncycastle.cert.X509v3CertificateBuilder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.cert.X509Certificate
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import javax.inject.Inject

class SslService @Inject constructor(
    identityProvider: IdentityProvider
) : SecurityService<X509Certificate, KeyPair> {

    private var certificate: X509Certificate? = null
    private var keyPair: KeyPair? = null
    private val bc = BouncyCastleProvider()

    init {
        initializeKeys()
        val nameBuilder = X500NameBuilder(BCStyle.INSTANCE)
        nameBuilder.addRDN(BCStyle.CN, identityProvider.getEnvironmentId())
        nameBuilder.addRDN(BCStyle.OU, "Kurome")
        nameBuilder.addRDN(BCStyle.O, "Kurome Labs")
        val certificateBuilder: X509v3CertificateBuilder = JcaX509v3CertificateBuilder(
            nameBuilder.build(),
            BigInteger.ONE,
            Date.from(LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant()),
            Date.from(
                LocalDate
                    .now()
                    .plusYears(20)
                    .atStartOfDay(ZoneId.systemDefault())
                    .toInstant()
            ),
            nameBuilder.build(),
            keyPair!!.public
        )
        val contentSigner = JcaContentSignerBuilder("SHA256WithRSAEncryption")
            .setProvider(bc)
            .build(keyPair!!.private)
        certificate = JcaX509CertificateConverter().setProvider(bc)
            .getCertificate(certificateBuilder.build(contentSigner))
    }

    override fun getSecurityContext(): X509Certificate {
        return certificate!!
    }

    private fun initializeKeys() {
        val keyGen = KeyPairGenerator.getInstance("RSA")
        keyGen.initialize(2048)
        keyPair = keyGen.genKeyPair()
    }

    override fun getKeys(): KeyPair {
        if (keyPair == null) initializeKeys()
        return keyPair!!
    }
}