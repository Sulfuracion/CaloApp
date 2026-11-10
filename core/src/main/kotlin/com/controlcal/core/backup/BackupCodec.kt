package com.controlcal.core.backup

import kotlinx.serialization.json.Json
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.BadPaddingException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Pure JVM codec for encrypting and decrypting ControlCal backups using AES-256-GCM
 * and PBKDF2WithHmacSHA256 key derivation.
 *
 * File Structure:
 * - [0..3]   : Magic bytes "CCBK" (ASCII)
 * - [4..7]   : Format version (Int, Big-Endian)
 * - [8..11]  : PBKDF2 iterations (Int, Big-Endian)
 * - [12..27] : Cryptographic salt (16 bytes)
 * - [28..39] : Initialization Vector / Nonce (12 bytes)
 * - [40..N]  : Ciphertext with appended 128-bit GCM authentication tag
 *
 * The 40-byte cleartext header is bound into the GCM cipher via updateAAD.
 */
object BackupCodec {

    val MAGIC_BYTES = byteArrayOf('C'.code.toByte(), 'C'.code.toByte(), 'B'.code.toByte(), 'K'.code.toByte())
    const val CURRENT_FORMAT_VERSION = 1
    const val PBKDF2_ITERATIONS = 600_000
    const val SALT_LENGTH_BYTES = 16
    const val IV_LENGTH_BYTES = 12
    const val GCM_TAG_LENGTH_BITS = 128
    const val GCM_TAG_LENGTH_BYTES = GCM_TAG_LENGTH_BITS / 8
    const val HEADER_SIZE_BYTES = 4 + 4 + 4 + SALT_LENGTH_BYTES + IV_LENGTH_BYTES // 40 bytes
    const val MIN_ENCRYPTED_FILE_SIZE = HEADER_SIZE_BYTES + GCM_TAG_LENGTH_BYTES // 56 bytes
    const val KEY_LENGTH_BITS = 256
    const val MIN_PASSWORD_LENGTH = 8

    private val secureRandom = SecureRandom()

    val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
        prettyPrint = false
    }

    fun isPasswordValid(password: CharSequence): Boolean = password.length >= MIN_PASSWORD_LENGTH

    /**
     * Serializes and encrypts [payload] using [password].
     */
    fun encrypt(
        payload: BackupPayload,
        password: CharArray,
        iterations: Int = PBKDF2_ITERATIONS,
    ): ByteArray {
        require(isPasswordValid(password.concatToString())) {
            "La contraseña debe tener al menos $MIN_PASSWORD_LENGTH caracteres"
        }

        val salt = ByteArray(SALT_LENGTH_BYTES).also { secureRandom.nextBytes(it) }
        val iv = ByteArray(IV_LENGTH_BYTES).also { secureRandom.nextBytes(it) }

        val headerBuffer = ByteBuffer.allocate(HEADER_SIZE_BYTES).order(ByteOrder.BIG_ENDIAN)
        headerBuffer.put(MAGIC_BYTES)
        headerBuffer.putInt(CURRENT_FORMAT_VERSION)
        headerBuffer.putInt(iterations)
        headerBuffer.put(salt)
        headerBuffer.put(iv)
        val headerBytes = headerBuffer.array()

        val derivedKey = deriveKey(password, salt, iterations)
        val jsonString = json.encodeToString(BackupPayload.serializer(), payload)
        val plaintextBytes = jsonString.toByteArray(Charsets.UTF_8)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.ENCRYPT_MODE, derivedKey, gcmSpec)
        cipher.updateAAD(headerBytes)
        val ciphertext = cipher.doFinal(plaintextBytes)

        val output = ByteArray(HEADER_SIZE_BYTES + ciphertext.size)
        System.arraycopy(headerBytes, 0, output, 0, HEADER_SIZE_BYTES)
        System.arraycopy(ciphertext, 0, output, HEADER_SIZE_BYTES, ciphertext.size)
        return output
    }

    /**
     * Decrypts [encryptedBytes] using [password] and parses into [BackupPayload].
     */
    fun decrypt(encryptedBytes: ByteArray, password: CharArray): BackupPayload {
        if (encryptedBytes.size < MIN_ENCRYPTED_FILE_SIZE) {
            throw TruncatedBackupException("El archivo es demasiado pequeño para ser una copia válida (${encryptedBytes.size} bytes)")
        }

        val headerBuffer = ByteBuffer.wrap(encryptedBytes, 0, HEADER_SIZE_BYTES).order(ByteOrder.BIG_ENDIAN)
        val magic = ByteArray(4)
        headerBuffer.get(magic)
        if (!magic.contentEquals(MAGIC_BYTES)) {
            throw InvalidBackupFormatException("Cabecera no válida: no coincide con los bytes mágicos CCBK")
        }

        val formatVersion = headerBuffer.getInt()
        if (formatVersion > CURRENT_FORMAT_VERSION) {
            throw UnsupportedBackupVersionException(formatVersion)
        }
        if (formatVersion < 1) {
            throw InvalidBackupFormatException("Versión de formato no válida: $formatVersion")
        }

        val iterations = headerBuffer.getInt()
        if (iterations < 100_000) {
            throw InvalidBackupFormatException("Número de iteraciones insuficiente o manipulado: $iterations")
        }

        val salt = ByteArray(SALT_LENGTH_BYTES)
        headerBuffer.get(salt)

        val iv = ByteArray(IV_LENGTH_BYTES)
        headerBuffer.get(iv)

        val headerBytes = encryptedBytes.copyOfRange(0, HEADER_SIZE_BYTES)
        val ciphertext = encryptedBytes.copyOfRange(HEADER_SIZE_BYTES, encryptedBytes.size)

        val derivedKey = deriveKey(password, salt, iterations)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)

        val plaintextBytes = try {
            cipher.init(Cipher.DECRYPT_MODE, derivedKey, gcmSpec)
            cipher.updateAAD(headerBytes)
            cipher.doFinal(ciphertext)
        } catch (e: AEADBadTagException) {
            throw InvalidPasswordOrCorruptException(e)
        } catch (e: BadPaddingException) {
            throw InvalidPasswordOrCorruptException(e)
        } catch (e: Exception) {
            throw InvalidPasswordOrCorruptException(e)
        }

        return try {
            val jsonString = plaintextBytes.toString(Charsets.UTF_8)
            json.decodeFromString(BackupPayload.serializer(), jsonString)
        } catch (e: Exception) {
            throw InvalidBackupFormatException("Error al analizar el contenido JSON descifrado: ${e.message}")
        }
    }

    /**
     * Extracts preview summary from a [BackupPayload].
     */
    fun extractSummary(payload: BackupPayload): BackupSummary {
        return BackupSummary(
            createdAt = payload.createdAt,
            schemaVersion = payload.schemaVersion,
            appVersion = payload.appVersion,
            productCount = payload.products.size,
            dayCount = payload.dailyLogs.size,
            mealEntryCount = payload.mealEntries.size,
            weightEntryCount = payload.weightEntries.size,
            supplementCount = payload.supplements.size,
        )
    }

    private fun deriveKey(password: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val keySpec = PBEKeySpec(password, salt, iterations, KEY_LENGTH_BITS)
        return try {
            val keyBytes = factory.generateSecret(keySpec).encoded
            SecretKeySpec(keyBytes, "AES")
        } finally {
            keySpec.clearPassword()
        }
    }
}
