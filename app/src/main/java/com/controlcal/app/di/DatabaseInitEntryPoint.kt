package com.controlcal.app.di

import com.controlcal.app.data.db.ControlCalDatabaseProvider
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Lets the Application trigger the database opening before any screen uses it. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface DatabaseInitEntryPoint {
    fun databaseProvider(): ControlCalDatabaseProvider
}
