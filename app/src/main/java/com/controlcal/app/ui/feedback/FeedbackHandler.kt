package com.controlcal.app.ui.feedback

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.SoftwareKeyboardController
import com.controlcal.app.camera.VibrationHelper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest

@Composable
fun HandleFeedbackEvents(
    events: Flow<UiFeedbackEvent>,
    snackbarHostState: SnackbarHostState,
) {
    val context = LocalContext.current

    LaunchedEffect(events) {
        events.collectLatest { event ->
            when (event) {
                is UiFeedbackEvent.Success -> {
                    if (event.vibrate) {
                        VibrationHelper.vibrateShort(context)
                    }
                    val msg = event.message.asString(context)
                    val action = event.actionLabel?.asString(context)
                    if (action != null && event.onAction != null) {
                        val result = snackbarHostState.showSnackbar(
                            message = msg,
                            actionLabel = action,
                            duration = SnackbarDuration.Short,
                        )
                        if (result == SnackbarResult.ActionPerformed) {
                            event.onAction.invoke()
                        }
                    } else {
                        snackbarHostState.showSnackbar(
                            message = msg,
                            duration = SnackbarDuration.Short,
                        )
                    }
                }
                is UiFeedbackEvent.Error -> {
                    val msg = event.message.asString(context)
                    snackbarHostState.showSnackbar(
                        message = msg,
                        duration = SnackbarDuration.Short,
                    )
                }
            }
        }
    }
}

fun clearFocusAndHideKeyboard(
    focusManager: FocusManager,
    keyboardController: SoftwareKeyboardController?,
) {
    focusManager.clearFocus()
    keyboardController?.hide()
}
