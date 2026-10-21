package com.controlcal.app.data.security

/**
 * Decides which database key to use when the app opens the database.
 *
 * - First launch (or after the user clears app data): no wrapped key stored,
 *   so a fresh 32-byte key is generated, wrapped and stored.
 * - Normal launch: the wrapped key is unwrapped and reused, so the existing
 *   encrypted file keeps opening with the same passphrase.
 * - Key loss (keystore key gone because app data was restored by force onto a
 *   different device, or the wrapped blob is corrupt): the old database file
 *   is unrecoverable by design, so [onKeyLost] is invoked to delete it and a
 *   fresh key starts a new database.
 */
class DatabaseKeyResolver(
    private val keyStore: WrappedKeyStoreIO,
    private val keyManager: DbKeyManager,
    private val hasExistingDatabase: () -> Boolean = { false },
    private val onKeyLost: () -> Unit = {},
) {

    constructor(
        keyStore: WrappedKeyStoreIO,
        keyManager: DbKeyManager,
        onKeyLost: () -> Unit,
    ) : this(
        keyStore = keyStore,
        keyManager = keyManager,
        hasExistingDatabase = { false },
        onKeyLost = onKeyLost,
    )

    /** The plain SQLCipher passphrase; the caller must wipe it after opening. */
    fun resolve(): ByteArray {
        val stored = keyStore.read()
        if (stored != null) {
            return try {
                keyManager.unwrap(stored)
            } catch (e: KeyWrapException) {
                // Old data is unreadable without the keystore key: start fresh.
                onKeyLost()
                keyStore.delete()
                createAndStore()
            }
        }
        if (hasExistingDatabase()) {
            // The wrapped key entry was lost (e.g. DataStore file corruption reset)
            // while an old encrypted database still exists on disk: delete it first.
            onKeyLost()
        }
        return createAndStore()
    }

    private fun createAndStore(): ByteArray {
        val key = keyManager.generateKey()
        try {
            keyStore.write(keyManager.wrap(key))
            return key
        } catch (t: Throwable) {
            keyManager.wipe(key)
            throw t
        }
    }
}

