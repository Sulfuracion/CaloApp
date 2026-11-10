package com.controlcal.core.backup

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class BackupCodecTest {

    private val samplePassword = "MiPasswordSeguro123".toCharArray()

    private val samplePayload = BackupPayload(
        backupFormatVersion = 1,
        schemaVersion = 3,
        createdAt = 1770000000000L,
        appVersion = "1.0",
        products = listOf(
            BackupProductDto(
                id = 1L,
                barcode = "8412345678901",
                name = "Avena Integral",
                brand = "Copos",
                kcal100g = 375.0,
                protein100g = 13.0,
                carbs100g = 68.0,
                fat100g = 7.0,
                fiber100g = 10.0,
                isLiquid = false,
                source = "MANUAL",
                archived = false,
                createdAt = 1769000000000L,
                updatedAt = 1769000000000L,
            )
        ),
        dailyLogs = listOf(
            BackupDailyLogDto(
                date = "2026-11-10",
                dayType = "TRAINING",
                waterMl = 2500,
                notes = "Día excelente",
                steps = 10500,
            )
        ),
        mealEntries = listOf(
            BackupMealEntryDto(
                id = 101L,
                date = "2026-11-10",
                meal = "BREAKFAST",
                productId = 1L,
                productName = "Avena Integral",
                grams = 60.0,
                kcal = 225.0,
                protein = 7.8,
                carbs = 40.8,
                fat = 4.2,
                loggedAt = 1770010000000L,
            ),
            // Quick calories entry (productId = null, grams = 0)
            BackupMealEntryDto(
                id = 102L,
                date = "2026-11-10",
                meal = "SNACK",
                productId = null,
                productName = "Calorías rápidas",
                grams = 0.0,
                kcal = 150.0,
                protein = 0.0,
                carbs = 0.0,
                fat = 0.0,
                loggedAt = 1770020000000L,
            ),
            // Tare entry (grams < 0)
            BackupMealEntryDto(
                id = 103L,
                date = "2026-11-10",
                meal = "BREAKFAST",
                productId = 1L,
                productName = "Avena Integral",
                grams = -10.0,
                kcal = -37.5,
                protein = -1.3,
                carbs = -6.8,
                fat = -0.7,
                loggedAt = 1770011000000L,
            )
        ),
        weightEntries = listOf(
            BackupWeightEntryDto(
                date = "2026-11-10",
                weightKg = 78.4,
            )
        ),
        goalProfiles = listOf(
            BackupGoalProfileDto(
                id = 1L,
                dayType = "TRAINING",
                kcal = 2400.0,
                proteinG = 160.0,
                carbsG = 260.0,
                fatG = 75.0,
                validFrom = "2026-10-01",
                origin = "MANUAL",
            )
        ),
        preferences = BackupPreferencesDto(
            weeklyPattern = "TRAINING,REST,TRAINING,REST,TRAINING,REST,REST",
            minKcalLimit = 1300.0,
            lastTdeeProposalReviewedDate = "2026-11-03",
            configuredMealsJson = """[{"id":"BREAKFAST","name":"Desayuno","startTime":"08:00","isActive":true}]""",
        )
    )

    @Test
    fun roundtrip_encryptAndDecrypt_restoresIdenticalPayload() {
        // Fast iterations for testing
        val encrypted = BackupCodec.encrypt(samplePayload, samplePassword, iterations = 100_000)
        assertNotNull(encrypted)
        assertTrue(encrypted.size > BackupCodec.HEADER_SIZE_BYTES)

        val decrypted = BackupCodec.decrypt(encrypted, samplePassword)
        assertEquals(samplePayload.backupFormatVersion, decrypted.backupFormatVersion)
        assertEquals(samplePayload.schemaVersion, decrypted.schemaVersion)
        assertEquals(samplePayload.createdAt, decrypted.createdAt)
        assertEquals(samplePayload.appVersion, decrypted.appVersion)

        assertEquals(samplePayload.products.size, decrypted.products.size)
        assertEquals(samplePayload.products.first(), decrypted.products.first())

        assertEquals(samplePayload.dailyLogs.size, decrypted.dailyLogs.size)
        assertEquals(samplePayload.dailyLogs.first(), decrypted.dailyLogs.first())

        assertEquals(samplePayload.mealEntries.size, decrypted.mealEntries.size)
        assertEquals(samplePayload.mealEntries[0], decrypted.mealEntries[0])
        assertEquals(samplePayload.mealEntries[1], decrypted.mealEntries[1]) // null product_id
        assertEquals(samplePayload.mealEntries[2], decrypted.mealEntries[2]) // negative tare

        assertEquals(samplePayload.weightEntries.size, decrypted.weightEntries.size)
        assertEquals(samplePayload.weightEntries.first(), decrypted.weightEntries.first())

        assertEquals(samplePayload.goalProfiles.size, decrypted.goalProfiles.size)
        assertEquals(samplePayload.goalProfiles.first(), decrypted.goalProfiles.first())

        assertEquals(samplePayload.preferences, decrypted.preferences)
    }

    @Test(expected = InvalidPasswordOrCorruptException::class)
    fun decrypt_wrongPassword_throwsInvalidPasswordOrCorruptException() {
        val encrypted = BackupCodec.encrypt(samplePayload, samplePassword, iterations = 100_000)
        val wrongPassword = "ContraseñaTotalmenteErronea".toCharArray()
        BackupCodec.decrypt(encrypted, wrongPassword)
    }

    @Test(expected = TruncatedBackupException::class)
    fun decrypt_truncatedFile_throwsTruncatedBackupException() {
        val tooShort = ByteArray(30)
        BackupCodec.decrypt(tooShort, samplePassword)
    }

    @Test(expected = InvalidPasswordOrCorruptException::class)
    fun decrypt_alteredCiphertextByte_throwsInvalidPasswordOrCorruptException() {
        val encrypted = BackupCodec.encrypt(samplePayload, samplePassword, iterations = 100_000)
        // Alter a byte in ciphertext (after the 40-byte header)
        val corrupted = encrypted.clone()
        val targetIndex = BackupCodec.HEADER_SIZE_BYTES + 5
        corrupted[targetIndex] = (corrupted[targetIndex].toInt() xor 0xFF).toByte()

        BackupCodec.decrypt(corrupted, samplePassword)
    }

    @Test(expected = InvalidPasswordOrCorruptException::class)
    fun decrypt_alteredSaltInHeader_failsAadAuthentication() {
        val encrypted = BackupCodec.encrypt(samplePayload, samplePassword, iterations = 100_000)
        val corrupted = encrypted.clone()
        // Salt is at indices 12..27
        corrupted[15] = (corrupted[15].toInt() xor 0xFF).toByte()

        BackupCodec.decrypt(corrupted, samplePassword)
    }

    @Test(expected = InvalidPasswordOrCorruptException::class)
    fun decrypt_alteredIvInHeader_failsAadAuthentication() {
        val encrypted = BackupCodec.encrypt(samplePayload, samplePassword, iterations = 100_000)
        val corrupted = encrypted.clone()
        // IV is at indices 28..39
        corrupted[30] = (corrupted[30].toInt() xor 0xFF).toByte()

        BackupCodec.decrypt(corrupted, samplePassword)
    }

    @Test(expected = InvalidBackupFormatException::class)
    fun decrypt_alteredMagicBytes_throwsInvalidBackupFormatException() {
        val encrypted = BackupCodec.encrypt(samplePayload, samplePassword, iterations = 100_000)
        val corrupted = encrypted.clone()
        corrupted[0] = 'X'.code.toByte()

        BackupCodec.decrypt(corrupted, samplePassword)
    }

    @Test(expected = UnsupportedBackupVersionException::class)
    fun decrypt_unsupportedFormatVersion_throwsUnsupportedBackupVersionException() {
        val encrypted = BackupCodec.encrypt(samplePayload, samplePassword, iterations = 100_000)
        val buffer = ByteBuffer.wrap(encrypted).order(ByteOrder.BIG_ENDIAN)
        buffer.position(4)
        buffer.putInt(99) // Format version 99

        BackupCodec.decrypt(encrypted, samplePassword)
    }

    @Test
    fun extractSummary_returnsCorrectCounts() {
        val summary = BackupCodec.extractSummary(samplePayload)
        assertEquals(samplePayload.createdAt, summary.createdAt)
        assertEquals(samplePayload.schemaVersion, summary.schemaVersion)
        assertEquals(samplePayload.appVersion, summary.appVersion)
        assertEquals(1, summary.productCount)
        assertEquals(1, summary.dayCount)
        assertEquals(3, summary.mealEntryCount)
        assertEquals(1, summary.weightEntryCount)
    }

    @Test
    fun isPasswordValid_checksMinimumLength() {
        assertFalse(BackupCodec.isPasswordValid("short"))
        assertFalse(BackupCodec.isPasswordValid("1234567"))
        assertTrue(BackupCodec.isPasswordValid("12345678"))
        assertTrue(BackupCodec.isPasswordValid("passwordSegura123"))
    }
}
