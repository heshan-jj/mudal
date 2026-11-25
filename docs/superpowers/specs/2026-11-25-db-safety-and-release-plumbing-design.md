<!-- Mudal fork: new file in Mudal, a fork of PennyWise AI (AGPL-3.0-only), 2026-11-25 - see NOTICE. -->
# Design: Core DB Safety & Release Plumbing (Workstream 1)

**Date:** 2026-11-25  
**Scope:** Workstream 1 of 3 leading to the next stable Mudal release (`v2.25.0`).  
**Size:** Small-to-medium hardening pass (no Room schema version bump, no new user-facing features).

---

## 1. Intent & Why

Code inspection after `v2.24.3` surfaced two classes of release-blocking hazards:

1. **Silent data-loss / broken recovery in the encrypted database layer:**
   - `DatabaseModule.providePennyWiseDatabase` catches exceptions and falls back to `Room.inMemoryDatabaseBuilder`, so a SQLCipher or Keystore failure silently drops new transactions into RAM, and **Restore from Backup** on `DatabaseErrorScreen` writes into that in-memory DB and loses everything when the app closes.
   - `System.loadLibrary("sqlcipher")` catches `Throwable` and logs without rethrowing, masking missing-library failures as DB corruption.
   - `DatabaseKeyManager.getOrCreatePassphrase()` treats *any* existing `pennywise_database` without a stored wrapped key as key loss, locking out any pre-encryption plaintext DB instead of upgrading it to SQLCipher.
   - `MainActivity` calls `resetDatabaseAndKey()` while Room's `@Singleton` instance is still open and only calls `recreate()`, leaving Hilt bound to the stale/in-memory DB.
   - Unused `PennyWiseDatabase.getInstance()` and `DatabaseKeyManager.getInstance()` duplicate DB creation outside Hilt.

2. **Broken release & CI plumbing:**
   - `.github/workflows/test.yml` was removed in `deef2bb6`, and `.github/workflows/release.yml` builds and publishes APKs without running unit tests.
   - `scripts/release.sh` still checks for `app-standard-release.aab` (deleted flavor), has no unit-test gate before tagging, and runs `gh release create` on tag push, racing with `release.yml`.
   - `init.sh` fails on Windows unless `JAVA_HOME` is manually set to JDK 21.
   - Planning docs (`../Docs/fork_plan.md` / `.superpowers/sdd/2026-10-21-gemini-api-integration/progress.md`) have drifted from the shipped codebase (`gemini-3.5-flash-lite`, Phase 1/2/release state).

---

## 2. Goals, Non-Goals, and Anti-Goals

### Goals
1. **No silent in-memory DB fallback:** Keystore/SQLCipher errors must never construct a live in-memory `PennyWiseDatabase` that accepts writes.
2. **Fail loudly on missing `libsqlcipher.so`:** Rethrow `UnsatisfiedLinkError` / `Throwable` from `System.loadLibrary("sqlcipher")`.
3. **Plaintext → encrypted DB upgrade:** Detect an unencrypted SQLite file (`"SQLite format 3\u0000"` header), re-encrypt it atomically using SQLCipher (`sqlcipher_export` preserving `PRAGMA user_version`), verify it opens with the wrapped key, and swap cleanly before Room opens.
4. **Clean reset & restore recovery:** On `DatabaseErrorScreen` Reset or Restore, close any open DB handle, clean up `-wal`/`-shm` sidecars, reset the key, and restart the app process cleanly so Hilt builds a fresh on-disk encrypted `PennyWiseDatabase` (staging any chosen backup file in `cacheDir` across the restart).
5. **Single DB creation path:** Remove unused `PennyWiseDatabase.getInstance()` and `DatabaseKeyManager.getInstance()`.
6. **Reliable CI & release gates:** Restore unit-test CI workflow, gate `release.yml` on `:parser-core:jvmTest` + `:app:testDebugUnitTest`, fix `scripts/release.sh` and `init.sh` (Windows JDK 21 support), and sync stale docs.

### Non-Goals
- Scheduled folder backup encryption (Workstream 2).
- Phase 4 SMS recurring/transfer pairing or Room schema changes (Workstream 3).
- Refactoring `PennyWiseApplication` / ViewModels to `dagger.Lazy<PennyWiseDatabase>`.

### Anti-Goals
- Never add `fallbackToDestructiveMigration()`.
- Never log raw passphrases, keys, or SMS bodies.
- Never silently swallow DB open/key errors into an empty working UI.

---

## 3. Approaches Considered

1. **Lazy Hilt DB Provider Refactor:** Refactor `PennyWiseApplication`, `MainActivity`, and `BackupImporter` to inject `Provider<PennyWiseDatabase>` / a mutable `DatabaseHolder` so DB construction is deferred until `keyStatus == Ready` and swappable in-process.
   - *Rejected:* Broad DI churn across app startup and backup classes for minimal extra benefit over a process restart during rare error recovery.
2. **Failing `SupportSQLiteOpenHelper.Factory` + Clean Process Restart *(Selected)*:**
   - Keep Hilt's `@Singleton PennyWiseDatabase` signature unchanged.
   - When `DatabaseKeyManager` detects key loss/error, provide a `FailingOpenHelperFactory` to `Room.databaseBuilder(...)` so Hilt constructs the singleton without crashing `MainActivity`, `PennyWiseApplication.onCreate()` skips background DB jobs (`hasKeyError()`), `MainActivity` renders `DatabaseErrorScreen`, and any stray DB query throws `DatabaseKeyException` instead of writing to RAM.
   - On Reset or Restore, close DB, reset key, and restart the app process so Hilt binds cleanly to the real on-disk encrypted DB.

---

## 4. Technical Design

### 4.1 Database Initialization, Error Handling & Plaintext Upgrade

- **Native library loading (`DatabaseModule.kt`):**
  - Call `System.loadLibrary("sqlcipher")` without swallowing `Throwable`; log error and rethrow so missing native libs fail immediately with a clear cause.
- **Plaintext detection & key resolution (`DatabaseKeyManager.kt` + `PlaintextDatabaseMigrator.kt`):**
  - Helper `isPlaintextSqlite(dbFile: File): Boolean` reads the first 16 bytes; returns `true` iff `dbFile.exists()` and length >= 16 and starts with `"SQLite format 3\u0000"`.
  - In `DatabaseKeyManager.getOrCreatePassphrase()`:
    - If `encryptedKeyBase64 == null || ivBase64 == null`:
      - If `dbFile.exists()` and **not** `isPlaintextSqlite(dbFile)` → key loss: set `DatabaseKeyStatus.Error`, throw `DatabaseKeyException`.
      - Otherwise (fresh install *or* legacy plaintext DB) → `generateAndStoreNewPassphrase()`.
- **Pre-Room plaintext → encrypted upgrade (`PlaintextDatabaseMigrator.kt`):**
  - Called in `DatabaseModule.providePennyWiseDatabase` right after obtaining `passphrase`, only if `isPlaintextSqlite(dbFile)` is `true`:
    1. Open `dbFile` unencrypted (`SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READWRITE)`), run `PRAGMA wal_checkpoint(TRUNCATE)`, read `PRAGMA user_version`, and close.
    2. Delete any leftover `dbFile.tmp` / `dbFile.bak`.
    3. Open `tmpFile` encrypted with `SQLiteDatabase.openOrCreateDatabase(tmpFile, passphrase, null, null)`.
    4. Attach `dbFile` (`ATTACH DATABASE ? AS plain KEY ''` using bound arg), execute `SELECT sqlcipher_export('main', 'plain')`, set `PRAGMA user_version = $userVersion`, `DETACH DATABASE plain`, and close `tmpFile`.
    5. Verify `tmpFile` exists, is **not** `isPlaintextSqlite(tmpFile)`, and opens cleanly via `SQLiteDatabase.openDatabase(tmpFile.path, passphrase, null, SQLiteDatabase.OPEN_READONLY)` with matching `user_version`.
    6. Swap: move `dbFile` → `bakFile`, move `tmpFile` → `dbFile`, delete `-wal`/`-shm` sidecars for `dbFile`, then delete `bakFile`. On any failure before swap completes, restore `bakFile` if needed, delete `tmpFile`, mark `DatabaseKeyManager` error, and return `FailingOpenHelperFactory`.
- **No in-memory fallback (`DatabaseModule.kt`):**
  - Remove `Room.inMemoryDatabaseBuilder`.
  - If `databaseKeyManager.getOrCreatePassphrase()` throws or plaintext upgrade fails, mark `DatabaseKeyManager.markError(...)` and configure `Room.databaseBuilder(..., DATABASE_NAME).openHelperFactory(FailingOpenHelperFactory(error))`.
  - `FailingOpenHelperFactory` creates a `SupportSQLiteOpenHelper` whose `writableDatabase` / `readableDatabase` throw `DatabaseKeyException` when accessed, while `close()` is a safe no-op.
  - *(Builder/Reviewer call)* Also wrap the normal `SupportOpenHelperFactory(passphrase)` in a lightweight `KeyAwareOpenHelperFactory` that catches open-time SQLCipher failures (corrupt DB or key mismatch where a wrapped key still existed in prefs), calls `databaseKeyManager.markError(...)`, and rethrows `DatabaseKeyException`. That routes open-time corruption to `DatabaseErrorScreen` without paying an extra PBKDF2 derivation cost on startup. (Non-fatal background observers should treat `DatabaseKeyException` as an expected key-error state.)
- **Remove duplicate singleton (`PennyWiseDatabase.kt` & `DatabaseKeyManager.kt`):**
  - Remove `PennyWiseDatabase.getInstance()`, `PennyWiseDatabase.setInstance()`, `PennyWiseDatabase.INSTANCE`, and `DatabaseKeyManager.getInstance()`. Keep `ALL_MIGRATIONS` and `DATABASE_NAME`.
- **Clean Reset & Restore (`MainActivity.kt` & `DatabaseKeyManager.kt`):**
  - Inject `PennyWiseDatabase` into `MainActivity` (safe since `FailingOpenHelperFactory` does not open SQLite on construction).
  - Add `resetDatabaseAndKey()` cleanup of `-wal`, `-shm`, `-journal`, and `.bak`/`.tmp` files alongside `context.deleteDatabase(...)`.
  - For **Reset**: close `database` (catching exceptions if unopened), call `databaseKeyManager.resetDatabaseAndKey()`, and restart the process via `Intent.makeRestartActivityTask(componentName)` + `Runtime.getRuntime().exit(0)`.
  - For **Restore from Backup**: before resetting, copy the picked SAF `Uri` stream to `File(cacheDir, "pending_restore.json")` (inside a try/catch showing an error toast/message on read failure without wiping the existing state); then close `database`, call `databaseKeyManager.resetDatabaseAndKey()`, and restart the process. On `MainActivity.onCreate`, when `keyStatus is Ready` and `pending_restore.json` exists, run `backupImporter.importBackup(Uri.fromFile(stagedFile), ImportStrategy.REPLACE_ALL)` in `lifecycleScope` (with a brief loading state), then delete `pending_restore.json`.

### 4.2 Release, CI & Tooling Plumbing

- **CI test workflow (`.github/workflows/test.yml`):**
  - Run on `push` to `main` and `pull_request` to `main`: JDK 21 (`temurin`), `./gradlew :parser-core:jvmTest :app:compileDebugKotlin :app:testDebugUnitTest`.
- **Release workflow (`.github/workflows/release.yml`):**
  - **GitHub Actions owns GitHub Release publishing** on `v*` tag push (and `workflow_dispatch`).
  - Add a `test` job (`:parser-core:jvmTest :app:compileDebugKotlin :app:testDebugUnitTest`) that `release` `needs:` before building/signing/publishing.
  - Keep APK naming consistent as `Mudal-v${VER}-universal.apk` (+ `.sha256`), published with `generate_release_notes: true`.
- **`scripts/release.sh`:**
  - Run `./gradlew :parser-core:jvmTest :app:testDebugUnitTest` as a pre-flight check before bumping versions or tagging (unless `--dry-run`).
  - Keep version bump (`versionName`/`versionCode`), fastlane changelog generation (`fastlane/metadata/android/en-US/changelogs/${NEXT_CODE}.txt` + `default.txt`), commit, tag `v$NEXT_VERSION`, and push (`origin main` + `origin "v$NEXT_VERSION"`).
  - Remove the redundant local `clean` + `assembleRelease` + `gh release create` steps from the default flow (since pushing `v$NEXT_VERSION` triggers `release.yml` to build, sign, hash, and publish), and remove dead `pennywise-web` deploy references.
  - Fix line 468 `.aab` check under `--play` (`app-standard-release.aab` → `app-release.aab`).
- **`init.sh`:**
  - Extend `find_jdk21()` to also check Windows Android Studio JBR (`C:/Program Files/Android/Android Studio/jbr`) and Gradle-cached JDK 21 (`$HOME/.gradle/jdks/eclipse_adoptium-21*`) when running under Git Bash / MSYS on Windows.
- **Doc sync:**
  - Update `.superpowers/sdd/2026-10-21-gemini-api-integration/progress.md` to mark Tasks 5 & 6 complete (`gemini-3.5-flash-lite`), and note Phase 1/2/release state accurately in `../Docs/fork_plan.md` (or `docs/FORK_PLAN.md` inside the repo—builder should check both).

---

## 5. Verification Plan (What the Builder Should Test)

1. **JVM Unit Tests:**
   - `DatabaseKeyManagerTest`: key generation, unwrapping, encrypted-DB key-loss error, plaintext-header exemption (generates fresh key instead of throwing), and full sidecar file cleanup on `resetDatabaseAndKey()`.
   - `FailingOpenHelperFactoryTest`: verifying `writableDatabase` / `readableDatabase` throw `DatabaseKeyException` and `close()` is safe.
2. **Instrumented Tests (`SqlCipherEncryptionTest`):**
   - Plaintext→encrypted upgrade test: create a real plaintext SQLite DB with `android.database.sqlite.SQLiteDatabase` (including `transactions` rows and WAL mode), run `PlaintextDatabaseMigrator`, assert the file no longer starts with `"SQLite format 3"`, opens with `SupportOpenHelperFactory(passphrase)`, preserves `PRAGMA user_version` and row contents.
3. **Full Gate:**
   - `./gradlew :parser-core:jvmTest :app:compileDebugKotlin :app:testDebugUnitTest` green.
   - `./gradlew :app:assembleRelease` (or `assembleDebug`) green.
   - Manual/emulator smoke check for `DatabaseErrorScreen` Reset & Restore flow if an emulator is available.

---

## 6. Left to the Builder

- Exact UI loading indicator wording during `pending_restore.json` import on `MainActivity` startup.
- Whether `PlaintextDatabaseMigrator` lives in `data/database/` as a standalone object injected/called by `DatabaseModule`.
- Exact GitHub Actions cache configuration in `.github/workflows/test.yml`.
