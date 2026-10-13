package com.controlcal.app.ui.debug

import android.content.Context
import kotlinx.serialization.json.Json
import java.io.File
import java.util.Locale

class OcrLabelStorageHelper(private val context: Context) {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    fun getLabelsDirectory(): File {
        val dir = context.getExternalFilesDir("ocr_labels") ?: File(context.filesDir, "ocr_labels")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun countSavedLabels(): Int {
        val dir = getLabelsDirectory()
        val jsonFiles = dir.listFiles { _, name -> name.endsWith(".json", ignoreCase = true) }
        return jsonFiles?.size ?: 0
    }

    fun getNextBaseName(): String {
        val dir = getLabelsDirectory()
        var index = 1
        while (true) {
            val candidate = String.format(Locale.ROOT, "label_%02d", index)
            val jpg = File(dir, "$candidate.jpg")
            val json = File(dir, "$candidate.json")
            if (!jpg.exists() && !json.exists()) {
                return candidate
            }
            index++
        }
    }

    fun getPhotoTargetFile(baseName: String): File {
        return File(getLabelsDirectory(), "$baseName.jpg")
    }

    fun saveJsonMetadata(baseName: String, metadata: OcrLabelMetadata): File {
        val dir = getLabelsDirectory()
        val jsonFile = File(dir, "$baseName.json")
        val jsonString = json.encodeToString(OcrLabelMetadata.serializer(), metadata)
        jsonFile.writeText(jsonString, Charsets.UTF_8)
        return jsonFile
    }

    fun deleteLabel(baseName: String): Boolean {
        val dir = getLabelsDirectory()
        val jpg = File(dir, "$baseName.jpg")
        val json = File(dir, "$baseName.json")
        val jpgDeleted = if (jpg.exists()) jpg.delete() else true
        val jsonDeleted = if (json.exists()) json.delete() else true
        return jpgDeleted && jsonDeleted
    }

    fun getAdbPullCommand(): String {
        return "adb pull /sdcard/Android/data/${context.packageName}/files/ocr_labels/ ./ocr_labels/"
    }
}
