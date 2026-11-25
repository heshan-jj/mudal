// Mudal fork: new file in Mudal, a fork of PennyWise AI (AGPL-3.0-only), 2026-11-25 - see NOTICE.
package com.pennywiseai.tracker.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files

class BackupRestoreStagingTest {

    @Test
    fun `stageBackup rejects empty json object without leaving staged file`() {
        val cacheDir = Files.createTempDirectory("restore_stage_empty_obj").toFile()
        try {
            val input = ByteArrayInputStream("{}".toByteArray())
            assertThrows(IllegalArgumentException::class.java) {
                BackupRestoreStaging.stageBackup(input, cacheDir)
            }
            assertFalse(BackupRestoreStaging.stagedFile(cacheDir).exists())
            assertFalse(BackupRestoreStaging.tempFile(cacheDir).exists())
        } finally {
            cacheDir.deleteRecursively()
        }
    }

    @Test
    fun `stageBackup rejects non-backup json or corrupt bytes without leaving staged file`() {
        val cacheDir = Files.createTempDirectory("restore_stage_bad").toFile()
        try {
            assertThrows(Exception::class.java) {
                BackupRestoreStaging.stageBackup(ByteArrayInputStream("not-json".toByteArray()), cacheDir)
            }
            assertFalse(BackupRestoreStaging.stagedFile(cacheDir).exists())

            val wrongFormat = """{"_format":"OtherApp v1.0","database":{}}"""
            assertThrows(IllegalArgumentException::class.java) {
                BackupRestoreStaging.stageBackup(ByteArrayInputStream(wrongFormat.toByteArray()), cacheDir)
            }
            assertFalse(BackupRestoreStaging.stagedFile(cacheDir).exists())
        } finally {
            cacheDir.deleteRecursively()
        }
    }

    @Test
    fun `stageBackup accepts valid backup json and clearStagedRestore removes files`() {
        val cacheDir = Files.createTempDirectory("restore_stage_ok").toFile()
        try {
            val validJson = """{"_format":"PennyWise Backup v1.3","database":{},"preferences":{}}"""
            val staged = BackupRestoreStaging.stageBackup(ByteArrayInputStream(validJson.toByteArray()), cacheDir)
            assertTrue(staged.exists())
            assertEquals(validJson, staged.readText())

            BackupRestoreStaging.clearStagedRestore(cacheDir)
            assertFalse(staged.exists())
            assertFalse(BackupRestoreStaging.tempFile(cacheDir).exists())
        } finally {
            cacheDir.deleteRecursively()
        }
    }
}
