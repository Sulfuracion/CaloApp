package com.controlcal.app.ui.debug

import kotlinx.serialization.Serializable

@Serializable
data class OcrBox(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
)

@Serializable
data class OcrLine(
    val text: String,
    val box: OcrBox
)

@Serializable
data class OcrLabelMetadata(
    val imageFileName: String,
    val timestamp: String,
    val imageWidth: Int,
    val imageHeight: Int,
    val lines: List<OcrLine>
)
