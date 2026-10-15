package com.controlcal.app.data.db

import android.content.Context
import androidx.room.Room
import com.controlcal.app.data.security.DatabaseKeyResolver
import com.controlcal.app.data.security.DbKeyManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import net.zetetic.database.Logger
import net.zetetic.database.NoopTarget
import net.zetetic.database.sqlcipher.SQLiteConnection
import net.zetetic.database.sqlcipher.SQLiteDatabaseHook
import net.zetetic.database.sqlcipher.driver.SQLCipherDriver

/**
 * Opens [ControlCalDatabase] encrypted with SQLCipher (AES-256).
 *
 * The passphrase is the 32-byte random key resolved by [DatabaseKeyResolver]
 * (generated on first launch, stored wrapped by an Android Keystore AES-GCM
 * key). The passphrase array given to the driver is a copy that SQLCipher must
 * keep for the lifetime of the connection pool; the resolver's array is wiped
 * right after the driver is built, and nothing is ever logged.
 */
@Singleton
class ControlCalDatabaseProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val keyResolver: DatabaseKeyResolver,
    private val keyManager: DbKeyManager,
) {

    @Volatile
    private var database: ControlCalDatabase? = null

    fun open(): ControlCalDatabase = database ?: synchronized(this) {
        database ?: create().also { database = it }
    }

    private fun create(): ControlCalDatabase {
        System.loadLibrary("sqlcipher")
        // SQLCipher's java-side logs may contain SQL text: silence them.
        Logger.setTarget(NoopTarget())

        val key = keyResolver.resolve()
        val driverKey = key.copyOf()
        keyManager.wipe(key)

        return Room.databaseBuilder(context, ControlCalDatabase::class.java, ControlCalDatabase.NAME)
            .setDriver(SQLCipherDriver(driverKey, CipherMemorySecurityHook, null))
            .addMigrations(MIGRATION_1_2)
            .build()
    }
}

/**
 * Runs on every SQLCipher connection right after keying: wipes decrypted page
 * buffers from memory as soon as possible (SQLCipher `cipher_memory_security`).
 */
object CipherMemorySecurityHook : SQLiteDatabaseHook {
    override fun preKey(connection: SQLiteConnection) = Unit

    override fun postKey(connection: SQLiteConnection) {
        connection.execute("PRAGMA cipher_memory_security = ON", null, null)
    }
}

/** Deletes the database file and its side files when the key is lost. */
class DatabaseFiles @Inject constructor(@ApplicationContext private val context: Context) {

    fun deleteAll() {
        val base = context.getDatabasePath(ControlCalDatabase.NAME).absolutePath
        listOf(base, "$base-wal", "$base-shm", "$base-journal").forEach { File(it).delete() }
    }
}
