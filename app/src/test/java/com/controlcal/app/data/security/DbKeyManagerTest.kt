package com.controlcal.app.data.security

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DbKeyManagerTest {

    private val cipher = FakeKeyCipher()
    private val keyManager = DbKeyManager(cipher, FakeSecureRandom(0x2A))

    @Test
    fun generatedKeyHasTheRightLength() {
        val key = keyManager.generateKey()
        assertEquals(DbKeyManager.KEY_LENGTH_BYTES, key.size)
    }

    @Test
    fun wrapThenUnwrapRoundTrips() {
        val key = keyManager.generateKey()
        val wrapped = keyManager.wrap(key)
        assertFalse(key.contentEquals(wrapped))
        assertArrayEquals(key, keyManager.unwrap(wrapped))
    }

    @Test
    fun twoWrapsOfTheSameKeyDifferBecauseOfTheRandomIv() {
        val key = keyManager.generateKey()
        val first = keyManager.wrap(key)
        val second = keyManager.wrap(key)
        assertFalse(first.contentEquals(second))
    }

    @Test
    fun wipeZeroesTheKey() {
        val key = keyManager.generateKey()
        keyManager.wipe(key)
        assertTrue(key.all { it == 0.toByte() })
    }

    @Test
    fun unwrapOfForeignBlobFails() {
        // The real Keystore wrapper turns any decrypt failure (bad tag, lost
        // key) into KeyWrapException; the fake models that with failDecrypt.
        cipher.failDecrypt = true
        val garbage = ByteArray(40) { 0x11 }
        try {
            keyManager.unwrap(garbage)
            throw AssertionError("Expected KeyWrapException")
        } catch (expected: KeyWrapException) {
            // expected
        }
    }
}
