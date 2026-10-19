package com.controlcal.app.di

import com.controlcal.app.data.off.OffClient
import com.controlcal.app.data.off.OffClientImpl
import com.controlcal.app.data.off.OffMapper
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class NetworkBindingModule {

    @Binds
    @Singleton
    abstract fun bindOffClient(impl: OffClientImpl): OffClient
}

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val OFF_TIMEOUT_SECONDS = 5L

    @Provides
    @Singleton
    fun provideJson(): Json = OffMapper.defaultJson

    @Provides
    @Singleton
    fun provideOffMapper(json: Json): OffMapper = OffMapper(json)

    @Provides
    @Singleton
    fun provideOffRateLimiter(clock: java.time.Clock): com.controlcal.app.data.off.OffRateLimiter =
        com.controlcal.app.data.off.OffRateLimiter(clock)

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(OFF_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(OFF_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .callTimeout(OFF_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }
}
