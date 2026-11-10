package com.controlcal.core.backup

/**
 * Base exception for backup and restoration operations.
 */
sealed class BackupException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Thrown when decryption fails due to an invalid password or corrupted/tampered data.
 */
class InvalidPasswordOrCorruptException(cause: Throwable? = null) :
    BackupException("Contraseña incorrecta o archivo de copia dañado", cause)

/**
 * Thrown when the backup format version is newer than supported.
 */
class UnsupportedBackupVersionException(val formatVersion: Int) :
    BackupException("Versión de formato de copia no soportada: $formatVersion")

/**
 * Thrown when the database schema version of the backup is newer than the current app's schema version.
 */
class NewerSchemaVersionException(val backupSchemaVersion: Int, val currentSchemaVersion: Int) :
    BackupException("Actualiza la app para importar esta copia")

/**
 * Thrown when the backup file is truncated or smaller than the required header/tag size.
 */
class TruncatedBackupException(message: String = "El archivo de copia de seguridad está incompleto o truncado") :
    BackupException(message)

/**
 * Thrown when magic bytes or header structure do not match the expected specification.
 */
class InvalidBackupFormatException(message: String = "Formato de archivo de copia no válido") :
    BackupException(message)
