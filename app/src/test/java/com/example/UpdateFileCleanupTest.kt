package com.example

import com.example.update.UpdateFileCleanup
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateFileCleanupTest {
    @Test
    fun removesEveryDownloadedInstaller() {
        val dir = java.nio.file.Files.createTempDirectory("updates").toFile()
        File(dir, "a.apk").writeText("a")
        File(dir, "b.apk").writeText("b")
        assertEquals(2, UpdateFileCleanup.cleanDir(dir))
        assertTrue(dir.listFiles()!!.isEmpty())
        dir.delete()
    }

    @Test
    fun missingFolderIsFine() {
        assertEquals(0, UpdateFileCleanup.cleanDir(File("/nonexistent-folder-for-test")))
        assertEquals(0, UpdateFileCleanup.cleanDir(null))
    }
}
