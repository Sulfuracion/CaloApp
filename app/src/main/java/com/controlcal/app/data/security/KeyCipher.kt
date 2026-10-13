package com.controlcal.app.data.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject

/**
 * Wraps and unwraps the database key. Kept behind an interface so the pure
 * key-management logic can be unit tested with an in-memory fake.
 */
interface KeyCipher {
    /** Encrypts [plain] and returns `IV || ciphertext` (GCM tag appended). */
    fun encrypt(plain: ByteArray): ByteArray

    /** Decrypts the `IV || ciphertext` blob produced by [encrypt]. */
    fun decrypt(data: ByteArray): ByteArray
}

/** The wrapped key cannot be decrypted (keystore key lost or blob corrupt). */
class KeyWrapException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * AES-256-GCM wrapper backed by a non-exportable key held in the Android
 * Keystore. The key never leaves the Keystore and never requires user
 * authentication, so the database can be opened right at app start.
 */
class KeystoreKeyCipher @Inject constructor() : KeyCipher {

    private val alias: String = KEY_ALIAS

    override fun encrypt(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val iv = cipher.iv
        return iv + cipher.doFinal(plain)
    }

    override fun decrypt(data: ByteArray): ByteArray = try {
        require(data.size > IV_LENGTH_BYTES) { "Wrapped key blob too short" }
        val iv = data.copyOfRange(0, IV_LENGTH_BYTES)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_LENGTH_BITS, iv))
        cipher.doFinal(data, IV_LENGTH_BYTES, data.size - IV_LENGTH_BYTES)
    } catch (e: Exception) {
        throw KeyWrapException("Cannot unwrap database key", e)
    }

    /** Gets the keystore key, generating it on first use. */
    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_SIZE_BITS)
                .build(),
        )
        return generator.generateKey()
    }

    companion object {
        const val KEY_ALIAS = "controlcal_db_wrap_key"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KEY_SIZE_BITS = 256
        private const val TAG_LENGTH_BITS = 128

        /** GCM default IV length: 12 bytes. */
        private const val IV_LENGTH_BYTES = 12
    }
}
