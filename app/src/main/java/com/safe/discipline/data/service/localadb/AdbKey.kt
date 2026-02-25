package com.safe.discipline.data.service.localadb

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.cert.X509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.ByteArrayInputStream
import java.math.BigInteger
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.Key
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.Principal
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.RSAKeyGenParameterSpec
import java.security.spec.RSAPublicKeySpec
import java.util.Date
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.X509ExtendedKeyManager
import javax.net.ssl.X509ExtendedTrustManager

private const val ANDROID_KEYSTORE = "AndroidKeyStore"
private const val ENCRYPTION_KEY_ALIAS = "_local_adb_encryption_key_"
private const val TRANSFORMATION = "AES/GCM/NoPadding"
private const val IV_SIZE_IN_BYTES = 12
private const val TAG_SIZE_IN_BYTES = 16

private val RSA_PADDING = byteArrayOf(
        0x00, 0x01, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 0x00,
        0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2b, 0x0e, 0x03, 0x02, 0x1a, 0x05, 0x00,
        0x04, 0x14
)

interface AdbKeyStore {
    fun put(bytes: ByteArray)
    fun get(): ByteArray?
}

class PreferenceAdbKeyStore(private val preference: SharedPreferences) : AdbKeyStore {
    private val preferenceKey = "adbkey"

    override fun put(bytes: ByteArray) {
        preference.edit { putString(preferenceKey, String(Base64.encode(bytes, Base64.NO_WRAP))) }
    }

    override fun get(): ByteArray? {
        if (!preference.contains(preferenceKey)) return null
        return Base64.decode(preference.getString(preferenceKey, null), Base64.NO_WRAP)
    }
}

class AdbKey(private val adbKeyStore: AdbKeyStore, name: String) {

    private val encryptionKey: Key = getOrCreateEncryptionKey()
    private val privateKey: RSAPrivateKey = getOrCreatePrivateKey()
    private val publicKey: RSAPublicKey = KeyFactory.getInstance("RSA")
            .generatePublic(RSAPublicKeySpec(privateKey.modulus, RSAKeyGenParameterSpec.F4)) as RSAPublicKey
    private val certificate: X509Certificate

    init {
        val signer = JcaContentSignerBuilder("SHA256withRSA").build(privateKey)
        val certHolder = X509v3CertificateBuilder(
                X500Name("CN=00"),
                BigInteger.ONE,
                Date(0),
                Date(2461449600L * 1000),
                Locale.ROOT,
                X500Name("CN=00"),
                SubjectPublicKeyInfo.getInstance(publicKey.encoded)
        ).build(signer)
        certificate = CertificateFactory.getInstance("X.509")
                .generateCertificate(ByteArrayInputStream(certHolder.encoded)) as X509Certificate
    }

    val adbPublicKey: ByteArray by lazy(LazyThreadSafetyMode.NONE) {
        publicKey.adbEncoded(name)
    }

    val sslContext: SSLContext by lazy(LazyThreadSafetyMode.NONE) {
        val sslContext = SSLContext.getInstance("TLSv1.3")
        sslContext.init(arrayOf(keyManager), arrayOf(trustManager), SecureRandom())
        sslContext
    }

    fun sign(data: ByteArray?): ByteArray {
        val cipher = Cipher.getInstance("RSA/ECB/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, privateKey)
        cipher.update(RSA_PADDING)
        return cipher.doFinal(data)
    }

    private fun getOrCreateEncryptionKey(): Key {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)
        keyStore.getKey(ENCRYPTION_KEY_ALIAS, null)?.let { return it }

        val parameterSpec = KeyGenParameterSpec.Builder(
                ENCRYPTION_KEY_ALIAS,
                KeyProperties.PURPOSE_DECRYPT or KeyProperties.PURPOSE_ENCRYPT
        )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        keyGenerator.init(parameterSpec)
        return keyGenerator.generateKey()
    }

    private fun encrypt(plaintext: ByteArray, aad: ByteArray?): ByteArray {
        val ciphertext = ByteArray(IV_SIZE_IN_BYTES + plaintext.size + TAG_SIZE_IN_BYTES)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, encryptionKey)
        if (aad != null) cipher.updateAAD(aad)
        cipher.doFinal(plaintext, 0, plaintext.size, ciphertext, IV_SIZE_IN_BYTES)
        System.arraycopy(cipher.iv, 0, ciphertext, 0, IV_SIZE_IN_BYTES)
        return ciphertext
    }

    private fun decrypt(ciphertext: ByteArray, aad: ByteArray?): ByteArray {
        val params = GCMParameterSpec(8 * TAG_SIZE_IN_BYTES, ciphertext, 0, IV_SIZE_IN_BYTES)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, encryptionKey, params)
        if (aad != null) cipher.updateAAD(aad)
        return cipher.doFinal(ciphertext, IV_SIZE_IN_BYTES, ciphertext.size - IV_SIZE_IN_BYTES)
    }

    private fun getOrCreatePrivateKey(): RSAPrivateKey {
        val aad = ByteArray(16)
        "adbkey".toByteArray().copyInto(aad)

        val saved = adbKeyStore.get()
        if (saved != null) {
            try {
                val plaintext = decrypt(saved, aad)
                val keyFactory = KeyFactory.getInstance("RSA")
                return keyFactory.generatePrivate(PKCS8EncodedKeySpec(plaintext)) as RSAPrivateKey
            } catch (_: Throwable) {
            }
        }

        val keyPairGenerator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA)
        keyPairGenerator.initialize(RSAKeyGenParameterSpec(2048, RSAKeyGenParameterSpec.F4))
        val privateKey = keyPairGenerator.generateKeyPair().private as RSAPrivateKey
        adbKeyStore.put(encrypt(privateKey.encoded, aad))
        return privateKey
    }

    private val keyManager
        get() = object : X509ExtendedKeyManager() {
            private val alias = "key"

            override fun chooseClientAlias(
                    keyTypes: Array<out String>?,
                    issuers: Array<out Principal>?,
                    socket: Socket?
            ): String? {
                if (keyTypes == null) return null
                return if (keyTypes.contains("RSA")) alias else null
            }

            override fun getCertificateChain(alias: String?): Array<X509Certificate>? {
                return if (alias == this.alias) arrayOf(certificate) else null
            }

            override fun getPrivateKey(alias: String?): PrivateKey? {
                return if (alias == this.alias) privateKey else null
            }

            override fun getClientAliases(keyType: String?, issuers: Array<out Principal>?): Array<String>? = null
            override fun getServerAliases(keyType: String?, issuers: Array<out Principal>?): Array<String>? = null
            override fun chooseServerAlias(keyType: String?, issuers: Array<out Principal>?, socket: Socket?): String? = null
        }

    private val trustManager
        get() = object : X509ExtendedTrustManager() {
            @SuppressLint("TrustAllX509TrustManager")
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?, socket: Socket?) {}

            @SuppressLint("TrustAllX509TrustManager")
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?, engine: SSLEngine?) {}

            @SuppressLint("TrustAllX509TrustManager")
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}

            @SuppressLint("TrustAllX509TrustManager")
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?, socket: Socket?) {}

            @SuppressLint("TrustAllX509TrustManager")
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?, engine: SSLEngine?) {}

            @SuppressLint("TrustAllX509TrustManager")
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}

            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }
}

private const val ANDROID_PUBKEY_MODULUS_SIZE = 2048 / 8
private const val ANDROID_PUBKEY_MODULUS_SIZE_WORDS = ANDROID_PUBKEY_MODULUS_SIZE / 4
private const val RSA_PUBLIC_KEY_SIZE = 524

private fun BigInteger.toAdbEncoded(): IntArray {
    val encoded = IntArray(ANDROID_PUBKEY_MODULUS_SIZE_WORDS)
    val r32 = BigInteger.ZERO.setBit(32)
    var tmp = this.add(BigInteger.ZERO)
    for (i in 0 until ANDROID_PUBKEY_MODULUS_SIZE_WORDS) {
        val out = tmp.divideAndRemainder(r32)
        tmp = out[0]
        encoded[i] = out[1].toInt()
    }
    return encoded
}

private fun RSAPublicKey.adbEncoded(name: String): ByteArray {
    val r32 = BigInteger.ZERO.setBit(32)
    val n0inv = modulus.remainder(r32).modInverse(r32).negate()
    val r = BigInteger.ZERO.setBit(ANDROID_PUBKEY_MODULUS_SIZE * 8)
    val rr = r.modPow(BigInteger.valueOf(2), modulus)

    val buffer = ByteBuffer.allocate(RSA_PUBLIC_KEY_SIZE).order(ByteOrder.LITTLE_ENDIAN)
    buffer.putInt(ANDROID_PUBKEY_MODULUS_SIZE_WORDS)
    buffer.putInt(n0inv.toInt())
    modulus.toAdbEncoded().forEach { buffer.putInt(it) }
    rr.toAdbEncoded().forEach { buffer.putInt(it) }
    buffer.putInt(publicExponent.toInt())

    val base64Bytes = Base64.encode(buffer.array(), Base64.NO_WRAP)
    val nameBytes = " $name\u0000".toByteArray()
    return ByteArray(base64Bytes.size + nameBytes.size).also {
        base64Bytes.copyInto(it)
        nameBytes.copyInto(it, base64Bytes.size)
    }
}
