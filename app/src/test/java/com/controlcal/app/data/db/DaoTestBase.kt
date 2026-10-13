package com.controlcal.app.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith

/** In-memory Room database shared by the DAO tests (Robolectric, JVM). */
@RunWith(AndroidJUnit4::class)
abstract class DaoTestBase {

    protected lateinit var db: ControlCalDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            ControlCalDatabase::class.java,
        ).build()
    }

    @After
    fun tearDown() {
        db.close()
    }
}
