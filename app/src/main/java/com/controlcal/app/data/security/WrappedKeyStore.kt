package com.controlcal.app.data.security

import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/** Blocking view of the wrapped-key storage, so the DB can open synchronously at startup. */
interface WrappedKeyStoreIO {
    /** The wrapped database key, or null on first run / after a data clear. */
    fun read(): ByteArray?

    fun write(wrapped: ByteArray)

    fun delete()
}

/**
 * Persists the wrapped database key in Preferences DataStore (section 10 of
 * docs/PLAN.md: nothing outside the encrypted DB and DataStore). The stored
 * value is AES-GCM ciphertext produced by the Keystore wrapper, so a plaintext
 * DataStore file leaks nothing usable without the Keystore key.
 */
class DataStoreWrappedKeyStore(
    private val dataStore: DataStore<Preferences>,
) : WrappedKeyStoreIO {

    private val wrappedKey = stringPreferencesKey("wrapped_db_key")

    override fun read(): ByteArray? = blocking {
        dataStore.data.first()[wrappedKey]?.let { Base64.decode(it, Base64.NO_WRAP) }
    }

    override fun write(wrapped: ByteArray) {
        blocking {
            val encoded = Base64.encodeToString(wrapped, Base64.NO_WRAP)
            dataStore.edit { preferences -> preferences[wrappedKey] = encoded }
        }
    }

    override fun delete() {
        blocking { dataStore.edit { preferences -> preferences.remove(wrappedKey) } }
    }

    private fun <T> blocking(block: suspend () -> T): T = runBlocking(Dispatchers.IO) { block() }
}
