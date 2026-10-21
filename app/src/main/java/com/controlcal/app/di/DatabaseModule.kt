package com.controlcal.app.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.controlcal.app.data.db.ControlCalDatabase
import com.controlcal.app.data.db.ControlCalDatabaseProvider
import com.controlcal.app.data.db.DatabaseFiles
import com.controlcal.app.data.security.DataStoreWrappedKeyStore
import com.controlcal.app.data.security.DatabaseKeyResolver
import com.controlcal.app.data.security.DbKeyManager
import com.controlcal.app.data.security.KeyCipher
import com.controlcal.app.data.security.KeystoreKeyCipher
import com.controlcal.app.data.security.WrappedKeyStoreIO
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SecurityBindingsModule {

    @Binds
    abstract fun bindKeyCipher(impl: KeystoreKeyCipher): KeyCipher
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    private const val DATASTORE_NAME = "controlcal_preferences"
    private const val KEY_DATASTORE_NAME = "controlcal_key_store"

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
        ) { context.preferencesDataStoreFile(DATASTORE_NAME) }

    @Provides
    @Singleton
    fun provideWrappedKeyStore(
        @ApplicationContext context: Context,
        legacyDataStore: DataStore<Preferences>,
    ): WrappedKeyStoreIO {
        val keyDataStore = PreferenceDataStoreFactory.create(
            corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
        ) { context.preferencesDataStoreFile(KEY_DATASTORE_NAME) }
        return DataStoreWrappedKeyStore(keyDataStore, legacyDataStore)
    }

    @Provides
    @Singleton
    fun provideDbKeyManager(cipher: KeyCipher): DbKeyManager = DbKeyManager(cipher)

    @Provides
    @Singleton
    fun provideKeyResolver(
        keyStore: WrappedKeyStoreIO,
        keyManager: DbKeyManager,
        databaseFiles: DatabaseFiles,
    ): DatabaseKeyResolver =
        DatabaseKeyResolver(
            keyStore = keyStore,
            keyManager = keyManager,
            hasExistingDatabase = { databaseFiles.exists() },
            onKeyLost = { databaseFiles.deleteAll() },
        )

    @Provides
    @Singleton
    fun provideDatabase(provider: ControlCalDatabaseProvider): ControlCalDatabase = provider.open()

    @Provides
    fun provideProductDao(db: ControlCalDatabase) = db.productDao()

    @Provides
    fun provideDailyLogDao(db: ControlCalDatabase) = db.dailyLogDao()

    @Provides
    fun provideMealEntryDao(db: ControlCalDatabase) = db.mealEntryDao()

    @Provides
    fun provideWeightEntryDao(db: ControlCalDatabase) = db.weightEntryDao()

    @Provides
    fun provideGoalProfileDao(db: ControlCalDatabase) = db.goalProfileDao()
}
