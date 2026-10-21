// Mudal fork: modified from PennyWise AI (AGPL-3.0-only) on 2026-10-21 - see NOTICE.
package com.pennywiseai.tracker.backup.folder

object ScheduledFolderBackupConstants {
    const val BACKUP_FILE_NAME = "Mudal_Backup.pennywisebackup"
    /** Sibling temp file written first, then swapped into [BACKUP_FILE_NAME]. */
    const val TEMP_BACKUP_FILE_NAME = "Mudal_Backup.pennywisebackup.tmp"

    /**
     * File name used before the Mudal rebrand. A scheduled backup written by an
     * earlier build is deleted once, when the next backup is written, so the
     * folder does not keep two copies of the same data.
     */
    const val LEGACY_BACKUP_FILE_NAME = "PennyWise_Backup.pennywisebackup"

    const val BACKUP_HOUR_OF_DAY = 2
}
