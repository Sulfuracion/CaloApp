package com.controlcal.app.data.security

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DataStoreWrappedKeyStoreTest {

    @Before
    fun cleanState() {
        store.delete()
    }

    @After
    fun cleanStateAfter() {
        store.delete()
    }

    @Test
    fun writeThenReadRoundTrips() {
        val key = ByteArray(32) { it.toByte() }

        store.write(key)

        assertArrayEquals(key, store.read())
    }

    @Test
    fun readOnEmptyStoreReturnsNull() {
        assertNull(store.read())
    }

    @Test
    fun deleteClearsTheKey() {
        store.write(ByteArray(32) { 7 })

        store.delete()

        assertNull(store.read())
    }

    @Test
    fun readOfForeignValueDoesNotCrash() {
        // The value is opaque ciphertext; whatever is there is decoded and
        // returned for the resolver to reject if unwrapping fails.
        store.write(ByteArray(48) { 0x0F })

        val read = store.read()
        assertArrayEquals(ByteArray(48) { 0x0F }, read)
    }

    companion object {
        // One DataStore instance per file, shared by every test in the class.
        private lateinit var dataStore: DataStore<Preferences>
        private lateinit var store: DataStoreWrappedKeyStore

        @BeforeClass
        @JvmStatic
        fun createStore() {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val file = File(context.filesDir, "test_key_store.preferences_pb").apply { delete() }
            dataStore = PreferenceDataStoreFactory.create { file }
            store = DataStoreWrappedKeyStore(dataStore)
        }
    }
}
