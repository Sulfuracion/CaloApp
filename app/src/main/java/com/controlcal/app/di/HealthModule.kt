package com.controlcal.app.di

import com.controlcal.app.data.health.HealthConnectManager
import com.controlcal.app.data.health.HealthConnectManagerImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class HealthModule {

    @Binds
    @Singleton
    abstract fun bindHealthConnectManager(
        impl: HealthConnectManagerImpl
    ): HealthConnectManager
}
