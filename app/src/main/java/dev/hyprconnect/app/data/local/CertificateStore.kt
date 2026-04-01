package dev.hyprconnect.app.data.local

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.*
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.File
import java.math.BigInteger
import java.security.*
import java.security.cert.X509Certificate
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CertificateStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val TAG = "CertificateStore"
    private val keyStoreFile = File(context.filesDir, "hyprconnect_v2.bks")
    private val keyStorePassword = "hyprconnect_pass".toCharArray()
    private val keyStore: KeyStore
    private val ALIAS_SELF = "hyprconnect_mobile_identity"

    init {
        // Ensure BouncyCastle is registered for BKS support
        if (Security.getProvider("BC") == null) {
            Security.addProvider(BouncyCastleProvider())
        }
        
        // We MUST use BC provider for BKS keystore type
        keyStore = KeyStore.getInstance("BKS", "BC")
        
        try {
            if (keyStoreFile.exists()) {
                keyStoreFile.inputStream().use { keyStore.load(it, keyStorePassword) }
                Log.d(TAG, "Loaded existing keystore")
            } else {
                keyStore.load(null, keyStorePassword)
                Log.d(TAG, "Created new keystore")
            }
            
            if (!keyStore.containsAlias(ALIAS_SELF)) {
                Log.i(TAG, "Generating new self-signed identity certificate")
                generateSelfSignedCertificate()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load/init keystore: ${e.message}", e)
            keyStore.load(null, keyStorePassword)
            generateSelfSignedCertificate()
        }
    }

    private fun generateSelfSignedCertificate() {
        // Use default provider for KeyPairGenerator (RSA is standard)
        val keyPairGenerator = KeyPairGenerator.getInstance("RSA")
        keyPairGenerator.initialize(2048)
        val keyPair = keyPairGenerator.generateKeyPair()

        val issuer = X500Name("CN=HyprConnect-Mobile, O=HyprConnect")
        val serial = BigInteger.valueOf(System.currentTimeMillis())
        val notBefore = Date(System.currentTimeMillis() - 1000L * 60 * 60 * 24) // Yesterday
        val notAfter = Date(notBefore.time + 3650L * 24 * 60 * 60 * 1000) // 10 years

        val certBuilder = JcaX509v3CertificateBuilder(
            issuer, serial, notBefore, notAfter, issuer, keyPair.public
        )

        val extUtils = JcaX509ExtensionUtils()
        
        // Basic Constraints
        certBuilder.addExtension(Extension.basicConstraints, true, BasicConstraints(false))
        
        // Key Usage
        certBuilder.addExtension(
            Extension.keyUsage, true, KeyUsage(
                KeyUsage.digitalSignature or KeyUsage.keyEncipherment or KeyUsage.dataEncipherment
            )
        )

        // Extended Key Usage
        certBuilder.addExtension(
            Extension.extendedKeyUsage, false, ExtendedKeyUsage(
                arrayOf(KeyPurposeId.id_kp_clientAuth, KeyPurposeId.id_kp_serverAuth)
            )
        )
        
        // Identifiers
        certBuilder.addExtension(Extension.subjectKeyIdentifier, false, extUtils.createSubjectKeyIdentifier(keyPair.public))
        certBuilder.addExtension(Extension.authorityKeyIdentifier, false, extUtils.createAuthorityKeyIdentifier(keyPair.public))

        // Subject Alternative Name
        val san = GeneralNames(GeneralName(GeneralName.dNSName, "hyprconnect-mobile"))
        certBuilder.addExtension(Extension.subjectAlternativeName, false, san)

        // Use default provider for signing as well (RSA/SHA256 is standard on Android)
        val signer = JcaContentSignerBuilder("SHA256withRSA").build(keyPair.private)
        
        // Converter uses default provider for CertificateFactory (essential on Android)
        val cert = JcaX509CertificateConverter().getCertificate(certBuilder.build(signer))

        keyStore.setKeyEntry(ALIAS_SELF, keyPair.private, keyStorePassword, arrayOf(cert))
        saveKeyStore()
        Log.i(TAG, "Identity certificate generated and saved")
    }

    private fun saveKeyStore() {
        keyStoreFile.outputStream().use { keyStore.store(it, keyStorePassword) }
    }

    fun getSelfCertificate(): X509Certificate {
        return keyStore.getCertificate(ALIAS_SELF) as X509Certificate
    }

    fun getSelfPrivateKey(): PrivateKey {
        return keyStore.getKey(ALIAS_SELF, keyStorePassword) as PrivateKey
    }

    fun addTrustedCertificate(alias: String, cert: X509Certificate) {
        keyStore.setCertificateEntry(alias, cert)
        saveKeyStore()
    }

    fun getKeyStore(): KeyStore = keyStore
    fun getAlias(): String = ALIAS_SELF
}
