package com.controlcal.app.ui.debug

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class OcrLabelStorageHelperTest {

    private lateinit var context: Context
    private lateinit var storageHelper: OcrLabelStorageHelper
    private lateinit var targetDir: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        storageHelper = OcrLabelStorageHelper(context)
        targetDir = storageHelper.getLabelsDirectory()
        targetDir.deleteRecursively()
        targetDir.mkdirs()
    }

    @After
    fun tearDown() {
        targetDir.deleteRecursively()
    }

    @Test
    fun countSavedLabels_initiallyZero() {
        assertEquals(0, storageHelper.countSavedLabels())
    }

    @Test
    fun getNextBaseName_incrementsSequentially() {
        assertEquals("label_01", storageHelper.getNextBaseName())

        File(targetDir, "label_01.jpg").writeText("dummy")
        assertEquals("label_02", storageHelper.getNextBaseName())

        File(targetDir, "label_02.json").writeText("{}")
        assertEquals("label_03", storageHelper.getNextBaseName())
    }

    @Test
    fun saveJsonMetadata_savesAndSerializesCorrectly() {
        val metadata = OcrLabelMetadata(
            imageFileName = "label_01.jpg",
            timestamp = "2026-10-13T21:45:00Z",
            imageWidth = 1080,
            imageHeight = 1920,
            lines = listOf(
                OcrLine(
                    text = "Valor energético",
                    box = OcrBox(left = 10, top = 20, right = 150, bottom = 45)
                ),
                OcrLine(
                    text = "1046 kJ / 250 kcal",
                    box = OcrBox(left = 160, top = 20, right = 320, bottom = 45)
                )
            )
        )

        val jsonFile = storageHelper.saveJsonMetadata("label_01", metadata)
        assertTrue(jsonFile.exists())
        assertEquals(1, storageHelper.countSavedLabels())

        val content = jsonFile.readText()
        val parsed = Json.decodeFromString<OcrLabelMetadata>(content)

        assertEquals("label_01.jpg", parsed.imageFileName)
        assertEquals(1080, parsed.imageWidth)
        assertEquals(1920, parsed.imageHeight)
        assertEquals(2, parsed.lines.size)
        assertEquals("Valor energético", parsed.lines[0].text)
        assertEquals(10, parsed.lines[0].box.left)
        assertEquals(20, parsed.lines[0].box.top)
        assertEquals(150, parsed.lines[0].box.right)
        assertEquals(45, parsed.lines[0].box.bottom)
        assertEquals("1046 kJ / 250 kcal", parsed.lines[1].text)
    }

    @Test
    fun deleteLabel_removesBothJpgAndJson() {
        val photoFile = storageHelper.getPhotoTargetFile("label_01")
        photoFile.writeText("image bytes")

        val metadata = OcrLabelMetadata(
            imageFileName = "label_01.jpg",
            timestamp = "2026-10-13T21:45:00Z",
            imageWidth = 100,
            imageHeight = 100,
            lines = emptyList()
        )
        val jsonFile = storageHelper.saveJsonMetadata("label_01", metadata)

        assertTrue(photoFile.exists())
        assertTrue(jsonFile.exists())
        assertEquals(1, storageHelper.countSavedLabels())

        val result = storageHelper.deleteLabel("label_01")
        assertTrue(result)
        assertFalse(photoFile.exists())
        assertFalse(jsonFile.exists())
        assertEquals(0, storageHelper.countSavedLabels())
    }

    @Test
    fun getAdbPullCommand_containsPackageAndDirectory() {
        val cmd = storageHelper.getAdbPullCommand()
        assertTrue(cmd.startsWith("adb pull"))
        assertTrue(cmd.contains(context.packageName))
        assertTrue(cmd.contains("ocr_labels"))
    }
}
