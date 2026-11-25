# Workstream 2 Implementation Plan: Remove Scheduled Folder Backup

> **For agentic workers:** Execute directly on `main` using TDD where logic exists and `./init.sh` + fresh `pro` code review before committing.

**Goal:** Completely remove the unattended Scheduled Folder Backup feature so backups only happen on explicit user manual export, while cleanly cancelling legacy WorkManager work and clearing stale DataStore/SAF state on upgrades from `<= v2.24.3`.

**Spec:** [2026-11-25-remove-scheduled-folder-backup-design.md](../specs/2026-11-25-remove-scheduled-folder-backup-design.md)

---

### Task 1: Legacy upgrade cleanup in `UserPreferencesRepository` & `PennyWiseApplication` (TDD)

**Files:**
- Create: `app/src/test/java/com/pennywiseai/tracker/data/preferences/LegacyFolderBackupCleanupTest.kt`
- Modify: `app/src/main/java/com/pennywiseai/tracker/data/preferences/UserPreferencesRepository.kt`
- Modify: `app/src/main/java/com/pennywiseai/tracker/PennyWiseApplication.kt`

1. Write failing test `LegacyFolderBackupCleanupTest`:
   - Seeds `InMemoryDataStore` with all 4 legacy keys (`scheduled_folder_backup_enabled`, `scheduled_folder_backup_tree_uri`, `scheduled_folder_backup_last_timestamp`, `folder_backup_include_receipts`) plus an unrelated key (`export_include_receipts = false`).
   - Calls `repo.clearLegacyScheduledFolderBackup()`.
   - Asserts it returns the stored tree URI string, deletes all 4 legacy keys, and leaves `export_include_receipts` untouched.
   - Verifies calling `clearLegacyScheduledFolderBackup()` when no legacy keys are present returns `null` without error.
2. Run `:app:testDebugUnitTest --tests "com.pennywiseai.tracker.data.preferences.LegacyFolderBackupCleanupTest"` -> observe RED (`clearLegacyScheduledFolderBackup` unresolved).
3. Implement `clearLegacyScheduledFolderBackup(): String?` in `UserPreferencesRepository.kt` (matching `InMemoryDataStore` / `activeDataStore` usage) and remove the four `scheduledFolderBackup*` / `folderBackupIncludeReceipts` flows, getters, setters, and `PreferencesKeys` entries.
4. Update `PennyWiseApplication.kt`:
   - Remove `@Inject lateinit var scheduledFolderBackupScheduler`.
   - Replace the startup re-arm block with an idempotent cleanup in `applicationScope.launch`:
     - `WorkManager.getInstance(this@PennyWiseApplication).cancelUniqueWork(LEGACY_SCHEDULED_FOLDER_BACKUP_WORK_NAME)` (`"scheduled_folder_backup"`).
     - Call `userPreferencesRepository.clearLegacyScheduledFolderBackup()`, and if non-null, parse `Uri` and best-effort call `contentResolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)` inside `runCatching`.
5. Run `LegacyFolderBackupCleanupTest` -> observe GREEN (note: don't run full compile yet until Task 2 & Task 3 remove callers of deleted `UserPreferencesRepository` methods).

---

### Task 2: Delete scheduled folder backup workers/writers/constants & `exportBackupBytes`

**Files:**
- Delete: `app/src/main/java/com/pennywiseai/tracker/backup/folder/FolderBackupWriter.kt`
- Delete: `app/src/main/java/com/pennywiseai/tracker/backup/folder/ScheduledFolderBackupConstants.kt`
- Delete: `app/src/main/java/com/pennywiseai/tracker/backup/folder/ScheduledFolderBackupScheduler.kt`
- Delete: `app/src/main/java/com/pennywiseai/tracker/worker/ScheduledFolderBackupWorker.kt`
- Delete: `app/src/test/java/com/pennywiseai/tracker/backup/folder/ScheduledFolderBackupSchedulerTest.kt`
- Modify: `app/src/main/java/com/pennywiseai/tracker/data/backup/BackupExporter.kt`
- Modify: `app/src/main/java/com/pennywiseai/tracker/data/backup/BackupModels.kt`

1. Delete the 5 scheduled-folder-backup files listed above.
2. In `BackupExporter.kt`, remove `exportBackupBytes(...)` and unused `encodeToString` import.
3. In `BackupModels.kt`, remove `ExportBytesResult`. Leave `PennyWiseBackup`, `DatabaseSnapshot`, `PreferencesSnapshot`, and all defaulted model fields untouched so `BackupSchemaGuardTest` and `BackupModelsTest` stay intact.

---

### Task 3: Remove folder-backup UI, ViewModel logic, and localized strings

**Files:**
- Modify: `app/src/main/java/com/pennywiseai/tracker/ui/screens/settings/SettingsViewModel.kt`
- Modify: `app/src/main/java/com/pennywiseai/tracker/ui/screens/settings/SettingsScreen.kt`
- Modify: all 14 `app/src/main/res/values*/strings_settings.xml` files

1. In `SettingsViewModel.kt`:
   - Remove `FolderBackupWriter` and `ScheduledFolderBackupScheduler` imports and constructor parameters.
   - Remove `ExportBytesResult` import and folder-backup state flows (`scheduledFolderBackupEnabled`, `scheduledFolderBackupLastTimestamp`, `folderBackupIncludeReceipts`, `requestFolderPicker`, `currentFolderPickerAction`, `FolderPickerAction`).
   - Remove `setFolderBackupIncludeReceipts`, `setScheduledFolderBackupEnabled`, `requestChangeBackupFolder`, `onFolderPickerLaunched`, `onBackupFolderSelected`, `backupToFolderNow`, `performFolderBackup`, `enableScheduledFolderBackup`.
   - Clean up any now-unused imports (e.g., `android.net.Uri` if still used by `importBackup`/`importCsv` keep it; remove unused imports only).
2. In `SettingsScreen.kt`:
   - Remove `scheduledFolderBackupEnabled`, `scheduledFolderBackupLastTimestamp`, `folderBackupIncludeReceipts`, `requestFolderPicker`.
   - Remove `backupFolderLauncher` (`ActivityResultContracts.OpenDocumentTree()`) and `LaunchedEffect(requestFolderPicker)`.
   - Remove the `Automatic Folder Backup` `SettingsSwitchRow` and the entire `if (scheduledFolderBackupEnabled) { ... }` block (`Back Up Now`, `Include receipt photos in automatic backups`, `Change Backup Folder`).
   - Check neighbor `ListItemPosition` (still `Middle` between `settings_export_include_receipts` and `settings_import_data`).
   - Remove any imports in `SettingsScreen.kt` made unused (`ActivityResultContracts.OpenDocumentTree` vs `rememberLauncherForActivityResult`—note import launchers still use them; check `Uri` or `Icons` if unused).
3. Remove the 17 unused folder-backup string resources from all 14 `strings_settings.xml` files:
   - `settings_folder_backup_title`
   - `settings_folder_backup_subtitle_enabled`
   - `settings_folder_backup_subtitle_pro`
   - `settings_folder_backup_subtitle_disabled`
   - `settings_folder_backup_include_receipts_title`
   - `settings_folder_backup_include_receipts_subtitle`
   - `settings_backup_now_title`
   - `settings_backup_now_last_backup`
   - `settings_backup_now_subtitle`
   - `settings_change_backup_folder_title`
   - `settings_change_backup_folder_subtitle`
   - `settings_folder_backup_disabled`
   - `settings_folder_access_failed`
   - `settings_folder_select_first`
   - `settings_folder_not_writable`
   - `settings_folder_backup_saved`
   - `settings_folder_backup_enabled`
4. Verify no orphaned references remain with `git grep`, then run `:app:testDebugUnitTest --tests "com.pennywiseai.tracker.data.preferences.LegacyFolderBackupCleanupTest"` and `./init.sh`.

---

### Task 4: Update docs, run full verification & `pro` code review, and commit

**Files:**
- Modify: `docs/FORK_PLAN.md`
- Modify: `docs/backup-format.md`
- Modify: `docs/PRIVACY.md`

1. Update `docs/FORK_PLAN.md`, `docs/backup-format.md`, and `docs/PRIVACY.md` to reflect the removal of scheduled folder backup and document manual plaintext JSON export.
2. Run `& 'C:\Program Files\Git\bin\bash.exe' ./init.sh` (covers `:parser-core:jvmTest`, `:app:compileDebugKotlin`, `:app:testDebugUnitTest` including `BackupSchemaGuardTest` & `StringPlaceholderParityTest`).
3. Spawn a fresh `pro` code reviewer (`research` subagent) to review the full Workstream 2 diff; fix any valid issues and re-run `./init.sh` if edited.
4. Commit spec + plan + implementation on `main` (do not push until user confirms after all workstreams).
