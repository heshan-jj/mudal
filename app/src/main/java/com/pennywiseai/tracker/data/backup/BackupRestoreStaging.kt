// Mudal fork: new file in Mudal, a fork of PennyWise AI (AGPL-3.0-only), 2026-11-25 - see NOTICE.
package com.pennywiseai.tracker.data.backup

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.io.File
import java.io.InputStream

/**
 * Stages and validates a user-picked backup file in `cacheDir` before the app
 * closes/resets a damaged encrypted database and restarts to import it.
 */
object BackupRestoreStaging {

    private const val STAGED_FILENAME = "pending_restore.json"

    fun stagedFile(cacheDir: File): File = File(cacheDir, STAGED_FILENAME)

    fun tempFile(cacheDir: File): File = File(cacheDir, "$STAGED_FILENAME.tmp")

    fun clearStagedRestore(cacheDir: File) {
        tempFile(cacheDir).delete()
        stagedFile(cacheDir).delete()
    }

    /**
     * Streams [inputStream] into a temporary file, validates that it is readable
     * JSON carrying a compatible `PennyWise Backup v1*` `_format` marker and
     * decodes cleanly into [PennyWiseBackup], then atomically moves it to [stagedFile].
     */
    fun stageBackup(inputStream: InputStream, cacheDir: File): File {
        val temp = tempFile(cacheDir)
        val staged = stagedFile(cacheDir)
        temp.delete()
        staged.delete()

        return try {
            val copied = inputStream.use { input ->
                temp.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            require(copied > 0L) { "Selected backup file is empty" }

            val raw = temp.readText(Charsets.UTF_8)
            val jsonObj = backupJson.parseToJsonElement(raw).jsonObject
            val formatValue = (jsonObj["_format"] as? JsonPrimitive)?.takeIf { it.isString }?.content
            require(!formatValue.isNullOrBlank() && formatValue.startsWith(PennyWiseBackup.COMPATIBLE_PREFIX)) {
                "Unsupported or missing backup format header"
            }
            backupJson.decodeFromString<PennyWiseBackup>(raw)

            check(temp.renameTo(staged)) { "Could not stage backup file for restore" }
            staged
        } catch (e: Throwable) {
            temp.delete()
            staged.delete()
            throw e
        }
    }
}
