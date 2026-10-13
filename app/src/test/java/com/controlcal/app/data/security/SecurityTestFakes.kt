package com.controlcal.app.data.security

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec

/**
 * In-memory AES-GCM wrapper standing in for the Android Keystore, so the key
 * lifecycle logic runs in plain JVM tests. Can simulate key loss with
 * [failDecrypt].
 */
class FakeKeyCipher : KeyCipher {

    var failDecrypt: Boolean = false

    private val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

    override fun encrypt(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        return cipher.iv + cipher.doFinal(plain)
    }

    override fun decrypt(data: ByteArray): ByteArray {
        if (failDecrypt) {
            throw KeyWrapException("Keystore key is gone")
        }
        val iv = data.copyOfRange(0, IV_LENGTH)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        return cipher.doFinal(data, IV_LENGTH, data.size - IV_LENGTH)
    }

    companion object {
        private const val IV_LENGTH = 12
    }
}

/** Deterministic fake store that keeps the wrapped key in memory. */
class FakeWrappedKeyStore : WrappedKeyStoreIO {

    var stored: ByteArray? = null
    var writes: Int = 0

    override fun read(): ByteArray? = stored?.copyOf()

    override fun write(wrapped: ByteArray) {
        stored = wrapped.copyOf()
        writes++
    }

    override fun delete() {
        stored = null
    }
}

/** Deterministic SecureRandom for tests that need a fixed sequence. */
class FakeSecureRandom(private val value: Byte) : SecureRandom() {
    override fun nextBytes(bytes: ByteArray) {
        bytes.fill(value)
    }
}
