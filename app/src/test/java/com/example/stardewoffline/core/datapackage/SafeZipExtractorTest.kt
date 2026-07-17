package com.example.stardewoffline.core.datapackage

import com.example.stardewoffline.core.common.AppResult
import java.io.File
import kotlin.io.path.createTempDirectory
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SafeZipExtractorTest {
    @Test
    fun rejectsZipSlipEntry() {
        runBlocking {
            val root = createTempDirectory("stardew-zip-test").toFile()
            val archive = File(root, "unsafe.svdata")
            writeZip(archive, "../outside.txt")

            val result = SafeZipExtractor(Dispatchers.Unconfined).extract(archive, File(root, "staging"))

            assertTrue(result is AppResult.Failure)
            assertFalse(File(root, "outside.txt").exists())
            root.deleteRecursively()
        }
    }

    private fun writeZip(file: File, entryName: String) {
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry(entryName))
            zip.write("unsafe".toByteArray())
            zip.closeEntry()
        }
    }
}
