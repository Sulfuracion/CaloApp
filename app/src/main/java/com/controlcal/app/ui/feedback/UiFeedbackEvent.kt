package com.controlcal.app.ui.feedback

sealed interface UiFeedbackEvent {
    data class Success(
        val message: UiText,
        val vibrate: Boolean = true,
        val actionLabel: UiText? = null,
        val onAction: (() -> Unit)? = null,
    ) : UiFeedbackEvent

    data class Error(
        val message: UiText,
    ) : UiFeedbackEvent
}
