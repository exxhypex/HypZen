package com.hypex.hypzen.data

import android.content.Context
import android.os.Build
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.X509v3CertificateBuilder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.File
import java.math.BigInteger
import java.security.*
import java.security.cert.Certificate
import java.security.spec.PKCS8EncodedKeySpec
import java.util.*

class HypZenAdbConnectionManager(private val context: Context) : AbsAdbConnectionManager() {
    private lateinit var keyPair: KeyPair
    private lateinit var certificate: Certificate

    init {
        Security.removeProvider("BC")
        Security.addProvider(BouncyCastleProvider())
        setApi(Build.VERSION.SDK_INT)
        val (kp, cert) = getOrGenerateKeys()
        this.keyPair = kp
        this.certificate = cert
    }

    override fun getPrivateKey(): PrivateKey = keyPair.private
    override fun getCertificate(): Certificate = certificate
    override fun getDeviceName(): String = "HypZenOTG"

    private fun getOrGenerateKeys(): Pair<KeyPair, Certificate> {
        val privFile = File(context.filesDir, "adb_priv.key")
        val certFile = File(context.filesDir, "adb_cert.crt")

        if (privFile.exists() && certFile.exists()) {
            val keyBytes = privFile.readBytes()
            val kf = KeyFactory.getInstance("RSA")
            val priv = kf.generatePrivate(PKCS8EncodedKeySpec(keyBytes))
            
            val cf = java.security.cert.CertificateFactory.getInstance("X.509")
            val cert = cf.generateCertificate(certFile.inputStream())
            
            val kp = KeyPair(cert.publicKey, priv)
            return Pair(kp, cert)
        }
        
        val kpg = KeyPairGenerator.getInstance("RSA")
        kpg.initialize(2048)
        val kp = kpg.generateKeyPair()
        val cert = generateSelfSignedCertificate(kp)
        
        privFile.writeBytes(kp.private.encoded)
        certFile.writeBytes(cert.encoded)
        
        return Pair(kp, cert)
    }

    private fun generateSelfSignedCertificate(keyPair: KeyPair): Certificate {
        val issuer = X500Name("CN=HypZen")
        val serial = BigInteger.valueOf(System.currentTimeMillis())
        val notBefore = Date()
        val notAfter = Date(notBefore.time + 1000L * 60 * 60 * 24 * 365 * 10) // 10 years

        val builder = JcaX509v3CertificateBuilder(
            issuer, serial, notBefore, notAfter, issuer, keyPair.public
        )

        val signer = JcaContentSignerBuilder("SHA256withRSA").build(keyPair.private)
        return JcaX509CertificateConverter().getCertificate(builder.build(signer))
    }
}
