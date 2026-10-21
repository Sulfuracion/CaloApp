package com.controlcal.app

import android.app.Application
import com.controlcal.app.di.DatabaseInitEntryPoint
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.android.EntryPointAccessors

@HiltAndroidApp
class ControlCalApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        val appContext = applicationContext
        // Open (and on first launch, create) the encrypted database right away,
        // off the main thread. Silent on failure: the screens will retry on
        // demand. No key material is logged anywhere in this path.
        Thread(
            {
                runCatching {
                    com.controlcal.app.ui.ocr.OcrCaptureViewModel.cleanupOrphanedTempPhotos(appContext.cacheDir)
                }
                runCatching {
                    EntryPointAccessors.fromApplication(appContext, DatabaseInitEntryPoint::class.java)
                        .databaseProvider()
                        .open()
                }
            },
            "db-open",
        ).start()
    }
}
