package com.controlcal.app.ui.debug

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

object DebugFeatures {
    val isDebugOcrAvailable: Boolean = false

    @Composable
    fun DebugOcrCollectorRoute(
        onBack: () -> Unit,
        modifier: Modifier = Modifier
    ) {
        // No-op in release builds
    }
}
