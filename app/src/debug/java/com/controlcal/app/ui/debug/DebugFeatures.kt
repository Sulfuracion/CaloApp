package com.controlcal.app.ui.debug

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

object DebugFeatures {
    val isDebugOcrAvailable: Boolean = true

    @Composable
    fun DebugOcrCollectorRoute(
        onBack: () -> Unit,
        modifier: Modifier = Modifier
    ) {
        OcrCollectorRoute(
            onBack = onBack,
            modifier = modifier
        )
    }
}
