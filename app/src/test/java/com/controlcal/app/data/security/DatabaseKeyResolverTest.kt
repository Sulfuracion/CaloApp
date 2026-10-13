package com.controlcal.app.data.security

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DatabaseKeyResolverTest {

    private val cipher = FakeKeyCipher()
    private val store = FakeWrappedKeyStore()
    private val keyManager = DbKeyManager(cipher, FakeSecureRandom(0x2A))

    @Test
    fun firstRunGeneratesAndStoresWrappedKey() {
        var lostHandled = false
        val resolver = DatabaseKeyResolver(store, keyManager) { lostHandled = true }

        val key = resolver.resolve()

        assertEquals(DbKeyManager.KEY_LENGTH_BYTES, key.size)
        assertEquals(1, store.writes)
        assertNotNull(store.stored)
        // The stored blob is the wrapped form, not the plain key.
        assertTrue(!key.contentEquals(store.stored!!))
        // No data loss path on a clean first run.
        assertTrue(!lostHandled)
    }

    @Test
    fun secondRunReusesTheStoredKey() {
        val resolver = DatabaseKeyResolver(store, keyManager)
        val first = resolver.resolve()
        val second = resolver.resolve()

        assertArrayEquals(first, second)
        // Wrapped key written exactly once (first run), reused afterwards.
        assertEquals(1, store.writes)
    }

    @Test
    fun keyLossDeletesFilesAndStartsFresh() {
        // Simulate an unwrappable stored blob: keystore key gone after a
        // forced restore, or corrupt DataStore.
        store.write(ByteArray(64) { 0x33 })
        cipher.failDecrypt = true
        var lostHandled = 0
        val resolver = DatabaseKeyResolver(store, keyManager) { lostHandled++ }

        val key = resolver.resolve()

        assertEquals(1, lostHandled)
        assertEquals(DbKeyManager.KEY_LENGTH_BYTES, key.size)
        // The unwrappable blob was replaced by a fresh wrapped key.
        assertEquals(2, store.writes)
        cipher.failDecrypt = false
        assertArrayEquals(key, keyManager.unwrap(store.read()!!))
    }

    @Test
    fun emptyStoreIsATranquilFirstRun() {
        var lostHandled = false
        val resolver = DatabaseKeyResolver(store, keyManager) { lostHandled = true }

        resolver.resolve()

        assertTrue(!lostHandled)
        assertNotNull(store.stored)
    }
}
