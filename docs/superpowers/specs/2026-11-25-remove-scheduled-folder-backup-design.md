# Workstream 2 Design: Remove Scheduled Folder Backup

**Date:** 2026-11-25  
**Status:** Proposed (awaiting user approval)  
**Scope:** Workstream 2 of the pre-`v2.25.0` stabilization plan (`docs/FORK_PLAN.md` Phase 1/2 backup threat model).

## Decision

Remove the unattended **Scheduled Folder Backup** feature completely (Approach 3) rather than encrypting daily SAF folder writes. Backups will only ever be written outside app-private storage when the user explicitly triggers **Export Data** (plaintext JSON), which satisfies the Mudal backup threat model:

> No plaintext transaction file is written to shared storage without an explicit user action; manual export stays plaintext JSON (documented in `docs/PRIVACY.md`); scheduled folder backup is disabled/removed.

## Existing-Install Upgrade Safety

Users upgrading from `<= v2.24.3` who previously enabled Automatic Folder Backup may still have:
1. A queued `WorkManager` periodic request named `"scheduled_folder_backup"`.
2. Four DataStore keys (`scheduled_folder_backup_enabled`, `scheduled_folder_backup_tree_uri`, `scheduled_folder_backup_last_timestamp`, `folder_backup_include_receipts`).
3. A persisted SAF tree URI permission held by `ContentResolver`.

Deleting `ScheduledFolderBackupWorker` without cleaning these up would cause `HiltWorkerFactory` / `WorkManager` to fail trying to instantiate the removed worker class.

To handle upgrades cleanly:
- Replace the startup re-arm block in `PennyWiseApplication.onCreate()` with a lightweight idempotent cleanup in `applicationScope.launch`:
  - Call `WorkManager.getInstance(this).cancelUniqueWork("scheduled_folder_backup")`.
  - Call `userPreferencesRepository.clearLegacyScheduledFolderBackup()`, which returns any stored legacy `tree_uri` and deletes only those four legacy DataStore keys (a no-op write if none are present).
  - If a legacy `tree_uri` was returned, best-effort release its persisted SAF read/write permission via `contentResolver.releasePersistableUriPermission(...)` inside `runCatching`.

## Code & Resource Changes

### Delete
- `app/src/main/java/com/pennywiseai/tracker/backup/folder/FolderBackupWriter.kt`
- `app/src/main/java/com/pennywiseai/tracker/backup/folder/ScheduledFolderBackupConstants.kt`
- `app/src/main/java/com/pennywiseai/tracker/backup/folder/ScheduledFolderBackupScheduler.kt`
- `app/src/main/java/com/pennywiseai/tracker/worker/ScheduledFolderBackupWorker.kt`
- `app/src/test/java/com/pennywiseai/tracker/backup/folder/ScheduledFolderBackupSchedulerTest.kt`

### Update
- **`PennyWiseApplication.kt`:** Remove `scheduledFolderBackupScheduler` injection; run legacy folder-backup WorkManager/DataStore/SAF cleanup on startup.
- **`UserPreferencesRepository.kt`:** Remove the four scheduled/folder backup flows, getters, and setters; keep only a minimal `clearLegacyScheduledFolderBackup()` helper referencing the four legacy key names for upgrade cleanup.
- **`BackupExporter.kt` & `BackupModels.kt`:** Remove `exportBackupBytes(...)` and `ExportBytesResult` (unused without folder backup). Keep `PennyWiseBackup`, `DatabaseSnapshot`, `PreferencesSnapshot`, and all defaulted wire-format fields untouched so `v1.x` backup compatibility and `BackupSchemaGuardTest` remain intact.
- **`SettingsViewModel.kt`:** Remove `FolderBackupWriter`, `ScheduledFolderBackupScheduler`, `FolderPickerAction`, and all folder-backup/SAF-picker state and methods. Keep manual `exportBackup()`, `setExportIncludeReceipts()`, `importBackup()`, and `importCsv()`.
- **`SettingsScreen.kt`:** Remove `backupFolderLauncher`, `LaunchedEffect(requestFolderPicker)`, "Automatic Folder Backup" switch, and its conditional sub-rows ("Back Up Now", "Include receipt photos in automatic backups", "Change Backup Folder"). Keep "Export Data" and "Include receipt photos".
- **`app/src/main/res/values*/strings_settings.xml`:** Remove the unused folder-backup string keys across the default and localized `strings_settings.xml` files.
- **Documentation (`docs/FORK_PLAN.md`, `docs/backup-format.md`, `docs/PRIVACY.md`):** Update Phase 1/2 backup threat-model checkboxes and document that backups are created only on explicit manual export (plaintext JSON).

## Verification Plan
- Unit test verifying legacy folder-backup cleanup removes only the four legacy DataStore keys, cancels WorkManager unique work `"scheduled_folder_backup"`, and releases any persisted SAF URI permission.
- Full verification via `./init.sh` (`:parser-core:jvmTest`, `:app:compileDebugKotlin`, `:app:testDebugUnitTest`, including `BackupSchemaGuardTest` and `StringPlaceholderParityTest`), plus fresh `pro` code review before committing on `main`.
