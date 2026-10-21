package com.controlcal.app.data.db

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseFilesTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val files = DatabaseFiles(context)

    @Test
    fun deleteAllRemovesDatabaseAndSideFiles() {
        val base = context.getDatabasePath(ControlCalDatabase.NAME)
        val sideFiles = listOf("$base-wal", "$base-shm", "$base-journal")
        (listOf(base.absolutePath) + sideFiles).forEach {
            File(it).apply { parentFile?.mkdirs() }.writeBytes(byteArrayOf(1, 2, 3))
        }
        assertTrue(files.exists())

        files.deleteAll()

        assertFalse(files.exists())
        assertFalse(File(base.absolutePath).exists())
        sideFiles.forEach { assertFalse(File(it).exists()) }
    }

    @Test
    fun deleteAllToleratesMissingFiles() {
        val base = context.getDatabasePath(ControlCalDatabase.NAME)
        File(base.absolutePath).delete()

        files.deleteAll()

        assertFalse(files.exists())
        assertFalse(File(base.absolutePath).exists())
    }
}
