# Core DB Safety & Release Plumbing Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Eliminate silent in-memory database fallback and broken recovery paths in Mudal's SQLCipher setup, add atomic plaintext→encrypted DB migration, and restore CI/release gates before Workstreams 2 and 3.

**Architecture:** Keep Hilt's `@Singleton PennyWiseDatabase` graph unchanged while replacing `inMemoryDatabaseBuilder` with two `SupportSQLiteOpenHelper.Factory` implementations (`FailingOpenHelperFactory` for startup key/migration errors and `KeyAwareOpenHelperFactory` for open-time SQLCipher failures) so all errors surface `DatabaseErrorScreen` instead of dropping writes in RAM. Add a pre-Room `PlaintextDatabaseMigrator` using SQLCipher's `sqlcipher_export`, perform reset/restore via close → delete sidecars → process restart (with staged `cacheDir/pending_restore.json`), and align `.github/workflows/{test,release}.yml`, `scripts/release.sh`, `init.sh`, and `docs/FORK_PLAN.md` so GitHub Actions owns release publishing behind passing tests.

**Tech Stack:** Kotlin `2.3.21`, Android Gradle Plugin `9.2.1`, Room `2.8.4`, SQLCipher `net.zetetic:sqlcipher-android:4.19.1`, Hilt `2.59.2`, JUnit 4 + MockK (JVM), AndroidX Test (`androidTest`), GitHub Actions, Bash.

**Spec:** [docs/superpowers/specs/2026-11-25-db-safety-and-release-plumbing-design.md](../specs/2026-11-25-db-safety-and-release-plumbing-design.md)

## Global Constraints

- Never add `fallbackToDestructiveMigration()` or silently swallow DB/SQLCipher errors into an in-memory database.
- `System.loadLibrary("sqlcipher")` must log and rethrow `Throwable` if the native library fails to load.
- Never log raw passphrases, keys, or SMS bodies; never put PII in code, comments, or tests.
- Keep `minSdk = 26`, `targetSdk = 36`, `compileSdk = 37`, and Room `SCHEMA_VERSION = 67` unchanged in this workstream.
- Verification gate is `./gradlew :parser-core:jvmTest :app:compileDebugKotlin :app:testDebugUnitTest` (or `./init.sh` once updated).
- Every modified existing file keeps its `Mudal fork:` notice updated/preserved per AGPL-3.0.

## Review Focus

1. **Legacy plaintext DB with WAL/SHM sidecars and no wrapped key:** `DatabaseKeyManager.getOrCreatePassphrase()` sees `dbFile.exists()` and `prefs` empty — must detect `"SQLite format 3\u0000"`, generate a new wrapped key, checkpoint WAL, export via `sqlcipher_export`, preserve `PRAGMA user_version`, and swap atomically without losing rows.
2. **Corrupt or wrong-key encrypted DB when Keystore key exists:** `getOrCreatePassphrase()` succeeds, Room builds lazily, then first query fails in SQLCipher — `KeyAwareOpenHelperFactory` must call `databaseKeyManager.markError(...)` and throw `DatabaseKeyException`, flipping `MainActivity` to `DatabaseErrorScreen` without crashing `PennyWiseApplication` background coroutines.
3. **Restore backup file unreadable or invalid JSON before reset:** Selecting an unreadable/corrupt SAF `Uri` on `DatabaseErrorScreen` must fail during `cacheDir/pending_restore.json` staging *before* closing/wiping the DB or key; and if `pending_restore.json` fails during `BackupImporter.importBackup`, it must be deleted in `finally` and surfaced cleanly instead of infinite-looping.
4. **Reset/restore leaves stale `-wal`/`-shm` sidecars behind:** Deleting only the main DB file while `-wal`/`-shm` remain causes SQLite corruption on next open — `resetDatabaseAndKey()` must remove `-wal`, `-shm`, `-journal`, `.tmp`, and `.bak` sidecars too.
5. **Windows `init.sh` and `scripts/release.sh` dry-run/CI behavior:** `init.sh` must resolve JDK 21 on Windows (Android Studio JBR / Gradle JDK cache) without requiring manual `JAVA_HOME`, and `scripts/release.sh` must run tests before bumping version, check `app-release.aab` (not `app-standard-release.aab`) under `--play`, and never run `gh release create` alongside `release.yml`.

---

## File Structure

- **Modify:** `app/src/main/java/com/pennywiseai/tracker/data/database/DatabaseKeyManager.kt` — add `isPlaintextSqlite(File)` and `deleteDatabaseFiles(File)` sidecar cleanup; allow plaintext DBs with missing prefs to generate a key instead of throwing key loss; remove unused `getInstance()`.
- **Create:** `app/src/main/java/com/pennywiseai/tracker/data/database/DatabaseOpenHelpers.kt` — `FailingOpenHelperFactory` and `KeyAwareOpenHelperFactory`.
- **Create:** `app/src/main/java/com/pennywiseai/tracker/data/database/PlaintextDatabaseMigrator.kt` — atomic pre-Room plaintext SQLite → SQLCipher conversion preserving `PRAGMA user_version`.
- **Modify:** `app/src/main/java/com/pennywiseai/tracker/di/DatabaseModule.kt` — rethrow `System.loadLibrary("sqlcipher")`, invoke `PlaintextDatabaseMigrator.migrateIfNeeded`, replace `inMemoryDatabaseBuilder` with `FailingOpenHelperFactory`, wrap `SupportOpenHelperFactory` in `KeyAwareOpenHelperFactory`, remove `PennyWiseDatabase.setInstance`.
- **Modify:** `app/src/main/java/com/pennywiseai/tracker/data/database/PennyWiseDatabase.kt` — remove unused `getInstance()`, `setInstance()`, and `INSTANCE`.
- **Modify:** `app/src/main/java/com/pennywiseai/tracker/MainActivity.kt` — inject `PennyWiseDatabase`, stage backup `Uri` to `cacheDir/pending_restore.json`, close DB + reset key + restart process on Reset/Restore, consume `pending_restore.json` after fresh Hilt startup.
- **Modify:** `app/src/main/java/com/pennywiseai/tracker/ui/screens/database/DatabaseErrorScreen.kt` — surface optional staging/restore error message if staging/import fails.
- **Modify:** `app/src/main/java/com/pennywiseai/tracker/PennyWiseApplication.kt` — guard startup DB-collecting coroutines against `DatabaseKeyException` / `hasKeyError()`.
- **Test (JVM):** `app/src/test/java/com/pennywiseai/tracker/data/database/DatabaseKeyManagerTest.kt`, `app/src/test/java/com/pennywiseai/tracker/data/database/DatabaseOpenHelpersTest.kt`
- **Test (Instrumented):** `app/src/androidTest/java/com/pennywiseai/tracker/data/database/SqlCipherEncryptionTest.kt`
- **Create:** `.github/workflows/test.yml`
- **Modify:** `.github/workflows/release.yml`, `scripts/release.sh`, `init.sh`, `docs/FORK_PLAN.md`, `.superpowers/sdd/2026-10-21-gemini-api-integration/progress.md`

---

### Task 1: Plaintext Detection & Sidecar Cleanup in `DatabaseKeyManager`

**Files:**
- Modify: `app/src/main/java/com/pennywiseai/tracker/data/database/DatabaseKeyManager.kt`
- Test: `app/src/test/java/com/pennywiseai/tracker/data/database/DatabaseKeyManagerTest.kt`

**Interfaces:**
- Produces in `DatabaseKeyManager.Companion`:
  - `fun isPlaintextSqlite(dbFile: File): Boolean`
  - `fun deleteDatabaseFiles(dbFile: File): Boolean`
- Updates in `DatabaseKeyManager`:
  - `fun getOrCreatePassphrase(): ByteArray` (treats missing prefs + `isPlaintextSqlite(dbFile)` as upgradeable, not key loss)
  - `fun resetDatabaseAndKey(): ByteArray` (deletes DB and `-wal`/`-shm`/`-journal`/`.tmp`/`.bak` sidecars)
  - Removes `DatabaseKeyManager.getInstance(context)`

- [ ] **Step 1: Write failing tests in `DatabaseKeyManagerTest.kt`**

Add three tests covering plaintext exemption, non-plaintext key loss, and sidecar file deletion using `java.nio.file.Files.createTempDirectory`:
- `plaintext db file with missing key generates passphrase instead of key loss error`: create a temp file starting with `"SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)`, point `context.getDatabasePath(any())` at it, call `manager.getOrCreatePassphrase()`, assert 32-byte key returned and `manager.hasKeyError() == false`.
- `encrypted db file with missing key still triggers key loss error`: create a temp file with 32 random/non-SQLite bytes, point `context.getDatabasePath(any())` at it, assert `assertThrows(DatabaseKeyException::class.java) { manager.getOrCreatePassphrase() }` and `manager.hasKeyError() == true`.
- `resetDatabaseAndKey deletes wal shm journal tmp and bak sidecar files`: create temp `pennywise_database` plus `-wal`, `-shm`, `-journal`, `.tmp`, `.bak` files, call `manager.resetDatabaseAndKey()`, assert all sidecar files no longer exist.

- [ ] **Step 2: Run `DatabaseKeyManagerTest` to verify it fails**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.pennywiseai.tracker.data.database.DatabaseKeyManagerTest"`
Expected: FAIL (plaintext file triggers `DatabaseKeyException`; sidecars remain on disk).

- [ ] **Step 3: Implement `isPlaintextSqlite`, `deleteDatabaseFiles`, updated `getOrCreatePassphrase`, and remove `getInstance` in `DatabaseKeyManager.kt`**

In `DatabaseKeyManager.kt`:
- Add `SQLITE_HEADER = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)` and `SIDECAR_SUFFIXES = listOf("-wal", "-shm", "-journal", ".tmp", ".bak")` in `companion object`.
- Implement `fun isPlaintextSqlite(dbFile: File): Boolean`: returns `false` if `!dbFile.exists() || !dbFile.isFile || dbFile.length() < SQLITE_HEADER.size`; otherwise reads first 16 bytes via `FileInputStream` and compares `bytesRead == SQLITE_HEADER.size && buffer.contentEquals(SQLITE_HEADER)`.
- Implement `fun deleteDatabaseFiles(dbFile: File): Boolean`: calls `dbFile.delete()` and deletes `File("${dbFile.path}$suffix")` for each suffix in `SIDECAR_SUFFIXES` if present.
- In `getOrCreatePassphrase()`: change `if (dbFile.exists())` to `if (dbFile.exists() && !isPlaintextSqlite(dbFile))`.
- In `resetDatabaseAndKey()`: call `context.deleteDatabase(PennyWiseDatabase.DATABASE_NAME)` and `deleteDatabaseFiles(context.getDatabasePath(PennyWiseDatabase.DATABASE_NAME))`.
- Delete `instance` and `fun getInstance(context: Context)` from `DatabaseKeyManager.Companion`.

- [ ] **Step 4: Run `DatabaseKeyManagerTest` to verify it passes**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.pennywiseai.tracker.data.database.DatabaseKeyManagerTest"`
Expected: PASS (all 8 tests green).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/pennywiseai/tracker/data/database/DatabaseKeyManager.kt app/src/test/java/com/pennywiseai/tracker/data/database/DatabaseKeyManagerTest.kt
git commit -m "fix(db): allow plaintext DB upgrade and clean sidecars on key reset"
```

---

### Task 2: `FailingOpenHelperFactory` & `KeyAwareOpenHelperFactory`

**Files:**
- Create: `app/src/main/java/com/pennywiseai/tracker/data/database/DatabaseOpenHelpers.kt`
- Create: `app/src/test/java/com/pennywiseai/tracker/data/database/DatabaseOpenHelpersTest.kt`

**Interfaces:**
- Consumes: `DatabaseKeyManager.markError(String, Throwable?)`, `DatabaseKeyException`
- Produces:
  - `class FailingOpenHelperFactory(private val errorMessage: String, private val cause: Throwable? = null) : SupportSQLiteOpenHelper.Factory`
  - `class KeyAwareOpenHelperFactory(private val delegate: SupportSQLiteOpenHelper.Factory, private val databaseKeyManager: DatabaseKeyManager) : SupportSQLiteOpenHelper.Factory`

- [ ] **Step 1: Write failing tests in `DatabaseOpenHelpersTest.kt`**

Write JVM unit tests using MockK:
- `failing factory throws DatabaseKeyException on writableDatabase and readableDatabase and close is safe`: build `SupportSQLiteOpenHelper.Configuration.builder(context).name("test.db").callback(callback).build()`, call `create(config)`, assert `assertThrows(DatabaseKeyException::class.java) { helper.writableDatabase }`, same for `readableDatabase`, and `helper.close()` does not throw.
- `key aware factory marks DatabaseKeyManager error and rethrows DatabaseKeyException when delegate fails`: mock `SupportSQLiteOpenHelper.Factory` returning a mock helper whose `writableDatabase` throws `RuntimeException("file is not a database")`, assert `KeyAwareOpenHelperFactory(delegate, manager).create(config).writableDatabase` throws `DatabaseKeyException` and `manager.hasKeyError() == true`.
- `key aware factory delegates cleanly when delegate succeeds`: mock delegate returning a mock `SupportSQLiteDatabase`, assert returned DB equals mock and `manager.hasKeyError() == false`.

- [ ] **Step 2: Run `DatabaseOpenHelpersTest` to verify it fails**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.pennywiseai.tracker.data.database.DatabaseOpenHelpersTest"`
Expected: FAIL (unresolved reference `FailingOpenHelperFactory`, `KeyAwareOpenHelperFactory`).

- [ ] **Step 3: Implement `DatabaseOpenHelpers.kt`**

Implement both factories in `app/src/main/java/com/pennywiseai/tracker/data/database/DatabaseOpenHelpers.kt`:
- `FailingOpenHelperFactory` returns a `SupportSQLiteOpenHelper` storing `databaseName = configuration.name`; `writableDatabase` and `readableDatabase` throw `DatabaseKeyException(errorMessage, cause)`; `setWriteAheadLoggingEnabled` and `close` are no-ops.
- `KeyAwareOpenHelperFactory` delegates `create(configuration)` and wraps `delegateHelper.writableDatabase` / `delegateHelper.readableDatabase` in `try/catch (e: Exception)` (rethrow `CancellationException` and `DatabaseKeyException` as-is; for other `Exception`, call `databaseKeyManager.markError("Failed to open encrypted database: ${e.message}", e)` and throw `DatabaseKeyException(..., e)`).

- [ ] **Step 4: Run `DatabaseOpenHelpersTest` to verify it passes**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.pennywiseai.tracker.data.database.DatabaseOpenHelpersTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/pennywiseai/tracker/data/database/DatabaseOpenHelpers.kt app/src/test/java/com/pennywiseai/tracker/data/database/DatabaseOpenHelpersTest.kt
git commit -m "feat(db): add FailingOpenHelperFactory and KeyAwareOpenHelperFactory"
```

---

### Task 3: `PlaintextDatabaseMigrator` (Pre-Room Plaintext → SQLCipher Upgrade)

**Files:**
- Create: `app/src/main/java/com/pennywiseai/tracker/data/database/PlaintextDatabaseMigrator.kt`
- Modify: `app/src/androidTest/java/com/pennywiseai/tracker/data/database/SqlCipherEncryptionTest.kt`

**Interfaces:**
- Consumes: `DatabaseKeyManager.isPlaintextSqlite(File)`, `net.zetetic.database.sqlcipher.SQLiteDatabase`
- Produces:
  - `object PlaintextDatabaseMigrator { fun migrateIfNeeded(dbFile: File, passphrase: ByteArray): Result<Boolean> }`

- [ ] **Step 1: Add failing instrumentation test in `SqlCipherEncryptionTest.kt`**

Add `testPlaintextDatabaseUpgradesToEncrypted`:
1. Create a plaintext Room/SQLite DB at `testDbName` using standard `android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(dbFile, null)` with WAL enabled (`PRAGMA journal_mode=WAL`), create `transactions` table or insert rows via unencrypted `Room.databaseBuilder` (without `SupportOpenHelperFactory`), write a transaction (`merchantName = "Plaintext Bakery"`, `amount = 750.00`), leave it closed or WAL-active.
2. Assert `DatabaseKeyManager.isPlaintextSqlite(dbFile) == true`.
3. Call `val result = PlaintextDatabaseMigrator.migrateIfNeeded(dbFile, correctPassphrase)`.
4. Assert `result.isSuccess && result.getOrThrow() == true`.
5. Assert `DatabaseKeyManager.isPlaintextSqlite(dbFile) == false`.
6. Open with `Room.databaseBuilder(..., testDbName).openHelperFactory(SupportOpenHelperFactory(correctPassphrase)).addMigrations(*PennyWiseDatabase.ALL_MIGRATIONS).build()` and assert the `"Plaintext Bakery"` transaction and `PRAGMA user_version == SCHEMA_VERSION` are preserved intact.
7. Assert calling `migrateIfNeeded(dbFile, correctPassphrase)` a second time returns `Result.success(false)` (idempotent no-op).

- [ ] **Step 2: Compile `androidTest` to confirm test fails before implementation**

Run: `.\gradlew.bat :app:compileDebugAndroidTestKotlin`
Expected: FAIL (`Unresolved reference 'PlaintextDatabaseMigrator'`).

- [ ] **Step 3: Implement `PlaintextDatabaseMigrator.kt`**

Implement `migrateIfNeeded(dbFile: File, passphrase: ByteArray): Result<Boolean>` in `PlaintextDatabaseMigrator.kt`:
- Return `Result.success(false)` if `!DatabaseKeyManager.isPlaintextSqlite(dbFile)`.
- Checkpoint WAL and read `PRAGMA user_version` by opening `dbFile` unencrypted with `SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READWRITE)` (`rawExecSQL("PRAGMA wal_checkpoint(TRUNCATE)")`, query `PRAGMA user_version`, close).
- Delete any stale `tmpFile = File("${dbFile.path}.tmp")` and `bakFile = File("${dbFile.path}.bak")`.
- Open `tmpFile` encrypted with `SQLiteDatabase.openOrCreateDatabase(tmpFile, passphrase, null, null)`:
  - Execute `rawExecSQL("ATTACH DATABASE ? AS plain KEY ''", arrayOf<Any>(dbFile.absolutePath))` (or `execSQL`).
  - Query `SELECT sqlcipher_export('main', 'plain')` via `rawQuery(...).use { it.moveToFirst() }`.
  - Execute `execSQL("PRAGMA user_version = $userVersion")`.
  - Execute `execSQL("DETACH DATABASE plain")`.
  - Close `tmpFile`.
- Verify `!DatabaseKeyManager.isPlaintextSqlite(tmpFile)` and open `tmpFile` with `SQLiteDatabase.openDatabase(tmpFile.path, passphrase, null, SQLiteDatabase.OPEN_READONLY)` verifying `PRAGMA user_version == userVersion`, then close.
- Swap atomically: rename `dbFile` → `bakFile`, rename `tmpFile` → `dbFile`; delete `-wal`, `-shm`, `-journal` sidecars for `dbFile`, then delete `bakFile`. On exception after `bakFile` exists and `!dbFile.exists()`, restore `bakFile.renameTo(dbFile)` and delete `tmpFile`; return `Result.failure(e)`.

- [ ] **Step 4: Compile `androidTest` and run `testDebugUnitTest`**

Run: `.\gradlew.bat :app:compileDebugAndroidTestKotlin :app:testDebugUnitTest`
Expected: PASS (compiles cleanly; unit tests pass; run `connectedDebugAndroidTest` on emulator if attached).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/pennywiseai/tracker/data/database/PlaintextDatabaseMigrator.kt app/src/androidTest/java/com/pennywiseai/tracker/data/database/SqlCipherEncryptionTest.kt
git commit -m "feat(db): add atomic plaintext-to-SQLCipher upgrade migrator"
```

---

### Task 4: Wire `DatabaseModule`, Remove `PennyWiseDatabase.getInstance()`, Harden `MainActivity` & `PennyWiseApplication`

**Files:**
- Modify: `app/src/main/java/com/pennywiseai/tracker/di/DatabaseModule.kt`
- Modify: `app/src/main/java/com/pennywiseai/tracker/data/database/PennyWiseDatabase.kt`
- Modify: `app/src/main/java/com/pennywiseai/tracker/MainActivity.kt`
- Modify: `app/src/main/java/com/pennywiseai/tracker/PennyWiseApplication.kt`
- Modify: `app/src/main/java/com/pennywiseai/tracker/ui/screens/database/DatabaseErrorScreen.kt`
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: `DatabaseKeyManager`, `FailingOpenHelperFactory`, `KeyAwareOpenHelperFactory`, `PlaintextDatabaseMigrator.migrateIfNeeded`
- Produces:
  - `MainActivity` stages backup `Uri` to `File(cacheDir, "pending_restore.json")`, closes `database`, resets key, restarts process, and imports `pending_restore.json` on clean startup before deleting it.

- [ ] **Step 1: Update `PennyWiseDatabase.kt` and `DatabaseModule.kt`**

In `PennyWiseDatabase.kt`:
- Remove `INSTANCE`, `getInstance(context)`, and `setInstance(database)` while keeping `DATABASE_NAME` and `ALL_MIGRATIONS`.

In `DatabaseModule.kt`:
- `System.loadLibrary("sqlcipher")`: wrap in `try { ... } catch (t: Throwable) { Log.e(...); throw t }` (never swallow).
- Try `val passphrase = databaseKeyManager.getOrCreatePassphrase()` and `PlaintextDatabaseMigrator.migrateIfNeeded(context.getDatabasePath(PennyWiseDatabase.DATABASE_NAME), passphrase).getOrThrow()`.
- If successful, set `openHelperFactory = KeyAwareOpenHelperFactory(SupportOpenHelperFactory(passphrase), databaseKeyManager)`.
- On exception, call `databaseKeyManager.markError(e.message ?: "Failed to initialize encrypted database", e)` and set `openHelperFactory = FailingOpenHelperFactory(e.message ?: "Encrypted database unavailable", e)`.
- Build `Room.databaseBuilder(context, PennyWiseDatabase::class.java, PennyWiseDatabase.DATABASE_NAME).openHelperFactory(openHelperFactory).addMigrations(*PennyWiseDatabase.ALL_MIGRATIONS).addCallback(DatabaseCallback()).build()`. (Delete `Room.inMemoryDatabaseBuilder` and `PennyWiseDatabase.setInstance(database)`.)

- [ ] **Step 2: Harden `PennyWiseApplication.kt` against `DatabaseKeyException` during startup**

In `PennyWiseApplication.kt`:
- Wrap the `goalRepository.observeGoals()` and `categoryRepository.getAllCategories()` coroutine collectors in `try/catch (e: DatabaseKeyException)` (plus checking `databaseKeyManager.hasKeyError()`) so an open-time SQLCipher error detected by `KeyAwareOpenHelperFactory` logs cleanly and lets `MainActivity` display `DatabaseErrorScreen` without crashing the background coroutine.

- [ ] **Step 3: Update `DatabaseErrorScreen.kt` and `MainActivity.kt` for staged Restore + Process Restart**

In `DatabaseErrorScreen.kt` & `strings.xml`:
- Add optional `actionError: String? = null` parameter (default `null`) and `isWorking: Boolean = false` parameter so staging/copy failures display clearly inside the error banner and buttons disable while staging.

In `MainActivity.kt`:
- Inject `lateinit var database: PennyWiseDatabase`.
- Add `private fun stagedRestoreFile(): File = File(cacheDir, "pending_restore.json")`.
- Add `private fun restartAppProcess()` using `packageManager.getLaunchIntentForPackage(packageName)` / `Intent.makeRestartActivityTask(componentName)`, `startActivity(...)`, `finishAffinity()`, `Runtime.getRuntime().exit(0)`.
- On `onRestoreBackup(uri)`: in `lifecycleScope.launch(Dispatchers.IO)`, copy `contentResolver.openInputStream(uri)` into `stagedRestoreFile()` (or temp → rename); if `openInputStream` fails or throws, delete partial file and show error on `DatabaseErrorScreen` without calling `resetDatabaseAndKey()`. Once staged, switch to cleanup: run `runCatching { database.close() }`, `databaseKeyManager.resetDatabaseAndKey()`, and `restartAppProcess()`.
- On `onResetDatabase`: run `runCatching { database.close() }`, `databaseKeyManager.resetDatabaseAndKey()`, and `restartAppProcess()`.
- In `onCreate`, when `keyStatus !is DatabaseKeyStatus.Error` and `stagedRestoreFile().exists()`: show a blocking Material 3 loading state while running `backupImporter.importBackup(Uri.fromFile(stagedFile), ImportStrategy.REPLACE_ALL)` in `LaunchedEffect`, deleting `stagedFile` in `finally`, and surfacing any `ImportResult.Error` if restore failed.

- [ ] **Step 4: Compile and run unit tests**

Run: `.\gradlew.bat :app:compileDebugKotlin :app:testDebugUnitTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/pennywiseai/tracker/di/DatabaseModule.kt app/src/main/java/com/pennywiseai/tracker/data/database/PennyWiseDatabase.kt app/src/main/java/com/pennywiseai/tracker/MainActivity.kt app/src/main/java/com/pennywiseai/tracker/PennyWiseApplication.kt app/src/main/java/com/pennywiseai/tracker/ui/screens/database/DatabaseErrorScreen.kt app/src/main/res/values/strings.xml
git commit -m "fix(db): remove in-memory fallback and restart cleanly on reset/restore"
```

---

### Task 5: Restore CI Test Workflow, Fix `release.yml`, `scripts/release.sh`, `init.sh`, and Sync Docs

**Files:**
- Create: `.github/workflows/test.yml`
- Modify: `.github/workflows/release.yml`
- Modify: `scripts/release.sh`
- Modify: `init.sh`
- Modify: `docs/FORK_PLAN.md`
- Modify (local gitignored): `.superpowers/sdd/2026-10-21-gemini-api-integration/progress.md`

- [ ] **Step 1: Create `.github/workflows/test.yml` and add `test` gate to `.github/workflows/release.yml`**

In `.github/workflows/test.yml`:
- Trigger on `push` (`main`) and `pull_request` (`main`), `workflow_dispatch`.
- Run `parser-tests` (`./gradlew :parser-core:jvmTest --continue`) and `app-tests` (`./gradlew :app:compileDebugKotlin :app:testDebugUnitTest --build-cache`) using `actions/checkout@v4`, `actions/setup-java@v4` (`temurin` `21`), `android-actions/setup-android@v3` (`packages: platform-tools`), `gradle/actions/setup-gradle@v4`.

In `.github/workflows/release.yml`:
- Add a `test` job running `./gradlew :parser-core:jvmTest :app:compileDebugKotlin :app:testDebugUnitTest --build-cache`.
- Make `release` `needs: test` and rename the produced APK to `Mudal-v${VER}-universal.apk` (+ `.sha256`) so release artifact names stay consistent with `v2.24.0`–`v2.24.3`.

- [ ] **Step 2: Fix `scripts/release.sh` and `init.sh`**

In `scripts/release.sh`:
- Before bumping version (when `DRY_RUN != "true"`), run `./gradlew :parser-core:jvmTest :app:testDebugUnitTest` and abort on failure.
- Remove local `clean` + `:parser-core:build` + `assembleRelease` + `gh release create` (and remove dead `--web` / `pennywise-web` deploy block), since pushing `v$NEXT_VERSION` triggers `.github/workflows/release.yml` to build, sign, hash, and publish.
- Keep version bump, fastlane changelog generation (`fastlane/metadata/android/en-US/changelogs/${NEXT_CODE}.txt` + `default.txt`), commit, tag `v$NEXT_VERSION`, push `main` + `v$NEXT_VERSION`, and `--play` (fixing `app-standard-release.aab` → `app-release.aab`).

In `init.sh`:
- Extend `find_jdk21()` to also check `/c/Program Files/Android/Android Studio/jbr` and matching `$HOME/.gradle/jdks/eclipse_adoptium-21*` directories on Windows/MSYS.

- [ ] **Step 3: Sync `docs/FORK_PLAN.md` and local Gemini SDD ledger**

- In `docs/FORK_PLAN.md`: update the Phase table & status summary to reflect Phase 2 hardening completed (plaintext upgrade, no in-memory fallback, close/restart on reset/restore) and `v2.24.3` released; keep Phase 4 + Release (`v2.25.0`) marked as next.
- In `.superpowers/sdd/2026-10-21-gemini-api-integration/progress.md`: mark Tasks 5 & 6 complete (`gemini-3.5-flash-lite` shipped; LiteRT-LM & `ModelDownloader` removed).

- [ ] **Step 4: Verify scripts, workflows, and full test gate**

Run:
- `bash -n scripts/release.sh && bash -n init.sh`
- `.\gradlew.bat :parser-core:jvmTest :app:compileDebugKotlin :app:testDebugUnitTest`
Expected: syntax checks exit 0 and all Gradle tasks pass (`BUILD SUCCESSFUL`).

- [ ] **Step 5: Commit**

```bash
git add .github/workflows/test.yml .github/workflows/release.yml scripts/release.sh init.sh docs/FORK_PLAN.md
git commit -m "chore(ci): restore test workflow, gate releases, and align release script"
```
