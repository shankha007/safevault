# SafeVault codebase guide

A map of the code: what each part does, how data flows, and how to build, test and change
it. This file is updated as the code changes. Design reasoning lives in
[DECISIONS.md](DECISIONS.md); the user's view of the app is in [USER_WORKFLOW.md](USER_WORKFLOW.md).

_Last updated: 2026-10-28 (Phase 1)_

---

## 1. Project layout

```
password-manager/
├── build.gradle.kts            plugin versions (AGP, Kotlin, Compose, serialization)
├── settings.gradle.kts         repositories, module list
├── gradle.properties
├── gradlew / gradlew.bat       Gradle wrapper (Gradle 8.11.1)
├── local.properties            path to the Android SDK (machine-specific, not shared)
├── signing/                    release keystore + passwords (NOT in git, back this up)
├── docs/
│   ├── CODEBASE_GUIDE.md       this file
│   ├── USER_WORKFLOW.md        how the app is used
│   └── DECISIONS.md            technical decision log
├── release/                    finished APKs to install on the phone
└── app/
    ├── build.gradle.kts        SDK levels, signing, R8, dependencies
    ├── proguard-rules.pro      R8 rules (strips all Log calls)
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml
        │   ├── res/            icon, theme, backup-exclusion rules
        │   └── java/com/safevault/app/
        │       ├── SafeVaultApp.kt
        │       ├── MainActivity.kt
        │       ├── crypto/
        │       ├── data/
        │       ├── security/
        │       ├── util/
        │       └── ui/
        └── test/               JVM unit tests (run on the PC)
```

---

## 2. Packages and files

### `crypto/`: cryptography
| File | What it does |
|---|---|
| `Crypto.kt` | `Kdf` (Argon2id via Bouncy Castle), `Aead` (AES-256-GCM seal/open), `VaultKeys` (derives `fileKey` and `fieldKey` from the master key; seals the file and individual fields), `KdfParams`, base64 helpers. **Pure Kotlin, no Android imports**, so it's unit-testable. |
| `BiometricKeys.kt` | Android Keystore key bound to strong biometrics. Creates the key (StrongBox if available), gives ciphers for BiometricPrompt, and wraps/unwraps the master key. |

### `data/`: vault model and storage
| File | What it does |
|---|---|
| `Models.kt` | Serializable data: `VaultItem`, `LoginData`, `CardData`, `VaultData`, `VaultMeta` (plain header), `VaultSettings`, `BackupFile`. `Sealed` = a field-encrypted string. `Field` = field names used as encryption context. |
| `VaultCodec.kt` | **Pure Kotlin** format logic: create a vault, wrap/unwrap the master key with a password, encrypt/decrypt the data file, make/parse/open backups, re-seal items under another key, merge items. Fully unit-tested. |
| `VaultStorage.kt` | Reads/writes `vault.meta` and `vault.dat` atomically (`AtomicFile`) in `noBackupFilesDir`. Overwrites the files before deleting them on wipe. |
| `VaultRepository.kt` | The **single owner of vault state**. Exposes `state: StateFlow<VaultState>` (NoVault / Locked / Unlocked(items)) and `meta`. Handles create, unlock (password or biometric), lock, add/edit/delete, reveal/seal fields, settings, password change, backup export/import/restore, wipe, and the failed-attempt counter. Holds the keys only while unlocked. |

### `security/`: runtime protections
| File | What it does |
|---|---|
| `AutoLock.kt` | `ProcessLifecycleOwner` observer. Locks when the app goes to the background (after the chosen delay) and after 5 minutes without a touch. Holds off while the system file picker is open. |
| `SecureClipboard.kt` | Copies with the sensitive-content flag and clears after N seconds. |
| `DeviceIntegrity.kt` | Best-effort root check, used for a warning only. |

### `util/`: helpers (pure Kotlin)
| File | What it does |
|---|---|
| `PasswordTools.kt` | `PasswordGenerator` (SecureRandom, at least one character from each chosen group, shuffled) and `PasswordStrength` (entropy estimate with penalties for common passwords and sequences). |
| `CardUtils.kt` | Card network detection, Luhn check, number formatting, expiry validation. |

### `ui/`: Jetpack Compose screens
| File | What it does |
|---|---|
| `AppRoot.kt` | Top-level switch on `VaultState` (Setup / Unlock / unlocked app) and the in-memory back stack (`Screen`: Home, Detail, Edit, Generator, Settings). |
| `Biometric.kt` | `biometricPrompt()` suspend wrapper around `BiometricPrompt`, plus `LocalActivity`. |
| `Reauth.kt` | `ReauthController` + `ReauthHost`: "confirm it's you" for PIN/CVV (fingerprint, or master password dialog), with a 60-second grace period. |
| `theme/Theme.kt` | Light and dark colour schemes, strength-meter colours. |
| `components/Components.kt` | `PasswordField`, `StrengthMeter`, `ItemAvatar`, `FieldRow`, `ConfirmDialog`, `PasswordPromptDialog`. |
| `screens/LockScreens.kt` | `SetupScreen` (create or restore a vault) and `UnlockScreen` (password, fingerprint, lockout countdown, root warning). |
| `screens/HomeScreen.kt` | Search, filter chips, item list, the add sheet, fingerprint suggestion card. |
| `screens/DetailScreen.kt` | Shows a login or card. Reveal/copy with auto-hide after 30 s; PIN/CVV go through `ReauthController`. |
| `screens/EditScreen.kt` | Add/edit form for logins and cards. `Draft` holds plain values only while open, sealed again on save. Shows the generator on top of itself so the draft survives (D-023). |
| `screens/GeneratorScreen.kt` | Password generator; can hand a password back to the editor. |
| `screens/SettingsScreen.kt` | All settings, backup export/import (Storage Access Framework), change password, delete vault. |

### Root files
| File | What it does |
|---|---|
| `SafeVaultApp.kt` | `Application`: creates `VaultRepository`, `AutoLock`, `SecureClipboard` (manual DI). |
| `MainActivity.kt` | Sets `FLAG_SECURE`, edge-to-edge, provides `LocalActivity`, reports touches to `AutoLock`. |

---

## 3. Key hierarchy and file format

```
master password ──Argon2id(salt, 46 MiB, t=2)──► KEK (256-bit)
                                                   │ AES-GCM wrap
                                                   ▼
            random master key (256-bit) ◄──AES-GCM wrap── Keystore biometric key (optional)
                  │
      ┌───────────┴────────────┐
 HMAC("…/file")          HMAC("…/field")
      ▼                        ▼
   fileKey                  fieldKey
 encrypts vault.dat     encrypts each secret field
                         (AAD = "itemId/fieldName")
```

**`vault.meta`** (plain JSON, no secrets):
`formatVersion, kdf{memoryKiB, iterations, parallelism}, salt, wrappedKey,
biometricWrappedKey?, failedAttempts, lockoutUntil, settings{…}`

**`vault.dat`**: `IV(12) ‖ AES-256-GCM(fileKey, JSON(VaultData)) ‖ tag(16)`, AAD
`safevault/file/v1`.

**Sealed field**: base64(`IV ‖ ciphertext ‖ tag`) under `fieldKey`, AAD
`safevault/field/v1/<itemId>/<field>`.

**Backup `.svault`**: JSON `{format:"safevault-backup", formatVersion, createdAt, kdf, salt,
wrappedKey, data: base64(vault.dat)}`.

---

## 4. Main flows in code

**Create vault:** `SetupScreen` → `VaultRepository.createVault` → `VaultCodec.createVault`
(random master key, Argon2id wrap) → write empty encrypted `vault.dat` and `vault.meta` →
state = `Unlocked`.

**Unlock with password:** `UnlockScreen` → `repo.unlockWithPassword` → checks lockout →
`VaultCodec.unwrapMaster` → on failure, update the counter and lockout. On success:
`VaultKeys.fromMaster`, decrypt `vault.dat`, state = `Unlocked`.

**Unlock with fingerprint:** `repo.biometricUnlockCipher()` (null if the key was invalidated)
→ `biometricPrompt(cipher)` → `repo.unlockWithBiometric(cipher)` unwraps the master key with
the hardware-authorised cipher.

**Reveal a secret:** `DetailScreen.toggle(field)` → PIN/CVV go through `ReauthController.confirm`
first → `repo.reveal(sealed, itemId, field)` → value is kept in a local map, cleared after 30 s.

**Save an item:** `EditScreen.save` → `repo.seal(...)` each secret → `repo.upsert(item)` →
re-encrypt the whole `VaultData` → atomic write.

**Lock:** `AutoLock` or the 🔒 button → `repo.lock()` → `VaultKeys.wipe()`, master key zeroed,
state = `Locked` → `AppRoot` shows `UnlockScreen`. The unlocked back stack is discarded.

---

## 5. Building

Requirements (already installed on this PC):
- JDK 17: `C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot`
- Android SDK: `%LOCALAPPDATA%\Android\Sdk` (platform 35, build-tools 35)

From Git Bash in the project folder:

```bash
export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot"
./gradlew testDebugUnitTest      # run unit tests
./gradlew assembleDebug          # debug APK (app id com.safevault.app.debug)
./gradlew assembleRelease        # signed, shrunk release APK
```

Outputs:
- `app/build/outputs/apk/debug/app-debug.apk`
- `app/build/outputs/apk/release/app-release.apk` (copied to `release/SafeVault-<version>.apk`)

The release build is signed only if `signing/keystore.properties` exists.

**Releasing a new version:** bump `versionCode` (+1) and `versionName` in
`app/build.gradle.kts`, build the release, and install it over the old app. It must be signed
with the same key, or Android refuses the update.

---

## 6. Testing

**Unit tests** (`app/src/test`, run on the PC in seconds):
- `VaultCodecTest`: password wrap/unwrap, wrong password, encrypt/decrypt round trip, secrets
  stay sealed after the outer decrypt, tamper detection, field context binding, random IVs,
  password change, backup round trip, invalid backup files, re-sealing, merge rules, Argon2
  determinism.
- `PasswordToolsTest`: generator length, character groups and uniqueness; strength ratings.

**On a device or emulator:** an Android 15 emulator (`safevault_test`) is set up. FLAG_SECURE
makes screenshots come out black, even in debug builds, so UI checks use
`adb shell uiautomator dump`, which reads the screen's text and layout.

```bash
SDK="$LOCALAPPDATA/Android/Sdk"
"$SDK/emulator/emulator.exe" -avd safevault_test &
"$SDK/platform-tools/adb.exe" install -r app/build/outputs/apk/debug/app-debug.apk
```

---

### Test log

**2026-10-28: v1.0.0 release APK on the Android 15 emulator.** All passed unless noted.

| Check | Result |
|---|---|
| Unit tests | 16/16 pass |
| Release APK permissions | Only `USE_BIOMETRIC` / `USE_FINGERPRINT`, **no INTERNET** |
| `allowBackup` / `fullBackupContent` | both `false` |
| APK signature | v2, `CN=SafeVault` |
| Weak master password rejected | ✔ "Very weak", Create button stays off |
| Create vault | ✔ |
| Wrong password then right password | ✔ "Wrong password.", then unlocks |
| Add login with generated password | ✔ (after the D-023 fix, which was found here) |
| Reveal and copy password | ✔ snackbar "Clipboard clears in 30 s" (auto-clear not observable through adb on Android 15) |
| Add card: Luhn typo warning, Visa detection, MM/YY auto-format | ✔ |
| Card face shows only last 4 digits | ✔ |
| PIN reveal asks to confirm; wrong password refused; right password shows PIN | ✔ |
| Screenshot of the app | ✔ fully black (FLAG_SECURE) |
| Leave the app and return | ✔ vault locked |
| 5 wrong passwords | ✔ "Try again in 25 s" countdown |
| Files on disk (root) | ✔ `vault.dat` has no names, usernames, card digits or PIN; `vault.meta` has no secrets; files are mode 600 |
| Export backup through the file picker | ✔ vault stayed unlocked during the picker; backup has no plaintext |
| Delete vault | ✔ files removed, setup screen shown |
| Restore from backup on the setup screen | ✔ both items back |
| Crashes in logcat | none |

**Not yet tested:** fingerprint enrolment and unlock, import (merge) into an existing vault,
change master password, the erase-after-10 setting, and a real phone.

---

## 7. Rules for changing the code

1. **Never log secrets.** Release builds strip `Log.*`, but don't rely on that.
2. **Never put decrypted values in `rememberSaveable`, navigation arguments, Intents or files.**
3. **New secret fields** must be `Sealed?` in `Models.kt`, get a name in `Field`, be sealed in
   `EditScreen.save`, revealed through `repo.reveal`, and be added to `VaultCodec.resealItem`.
4. **Changing the file format:** bump `formatVersion`, keep reading the old version, and add a
   unit test that opens an old-format vault.
5. **No new permissions** without a decision-log entry. INTERNET stays forbidden.
6. **New libraries** need a decision-log entry (what, why, and the alternatives).
7. Run `./gradlew testDebugUnitTest` before every release build.
