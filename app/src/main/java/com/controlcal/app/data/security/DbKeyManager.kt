package com.controlcal.app.data.security

import java.security.SecureRandom
import java.util.Arrays

/**
 * Generates, wraps and wipes the random passphrase used by SQLCipher.
 *
 * The passphrase is 32 random bytes (256 bits of entropy) generated on the
 * first launch. It is only ever persisted wrapped by [KeyCipher] (Android
 * Keystore AES-GCM); the plain bytes live only in memory while the database
 * opens, and [wipe] zeroes them afterwards. Nothing here is ever logged.
 */
class DbKeyManager(
    private val cipher: KeyCipher,
    private val random: SecureRandom = SecureRandom(),
) {

    /** A fresh random SQLCipher passphrase. */
    fun generateKey(): ByteArray = ByteArray(KEY_LENGTH_BYTES).also(random::nextBytes)

    /** Encrypts the passphrase for storage in DataStore. */
    fun wrap(key: ByteArray): ByteArray = cipher.encrypt(key)

    /** Recovers the passphrase from its wrapped form. */
    fun unwrap(wrapped: ByteArray): ByteArray = cipher.decrypt(wrapped)

    /** Overwrites the passphrase bytes in memory. Call as soon as it is no longer needed. */
    fun wipe(key: ByteArray) {
        Arrays.fill(key, 0)
    }

    companion object {
        const val KEY_LENGTH_BYTES = 32
    }
}
