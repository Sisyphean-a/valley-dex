package com.example.stardewoffline.core.common

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class HashUtilsTest {
    @Test
    fun calculatesSha256() {
        runBlocking {
            val file = File.createTempFile("stardew-hash", ".txt")
            file.writeText("stardew")

            val actual = HashUtils(Dispatchers.Unconfined).sha256(file)

            assertEquals("77bce7f90aa7a6ced1c801d5af64c614eafccce8cca67d54b8f099a615aa9a2f", actual)
            file.delete()
        }
    }
}
