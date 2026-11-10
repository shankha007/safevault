# SafeVault codebase guide

A map of the code: what each part does, how data flows, and how to build, test and change
it. This file is updated as the code changes. Design reasoning lives in
[DECISIONS.md](DECISIONS.md); the user's view of the app is in [USER_WORKFLOW.md](USER_WORKFLOW.md).

_Last updated: 2026-11-10 (1.3.0, new package name for Google Play)_

---

## 1. Project layout

```
password-manager/
├── README.md                   project overview, features, install and build
├── build.gradle.kts            plugin versions (AGP, Kotlin, Compose, serialization)
├── settings.gradle.kts         repositories, module list
├── gradle.properties
├── gradlew / gradlew.bat       Gradle wrapper (Gradle 8.11.1)
├── local.properties            path to the Android SDK (machine-specific, not shared)
├── signing/                    release keystore + passwords (NOT in git, back this up)
├── docs/
│   ├── CODEBASE_GUIDE.md       this file
│   ├── USER_WORKFLOW.md        how the app is used
│   ├── DECISIONS.md            technical decision log
│   ├── PLAY_STORE.md           Play Console listing text and form answers (D-039)
│   └── PRIVACY_POLICY.md       public privacy policy linked from the Play listing
├── release/                    finished APKs (sideload) and AABs (Google Play)
├── demos/                      demo video, shorts/, carousel/, LINKEDIN_POSTS.md (NOT in git)
├── desktop-preview/            TEMPORARY HTML mirror of the UI for testing on a PC (D-027, NOT in git)
│   ├── make_demo.py            records the captioned product demo video from it (D-029)
│   ├── make_shorts.py          records 3 short feature videos, 16:9 and 9:16 (D-030)
│   └── make_carousel.py        builds the LinkedIn carousel PDF and slide PNGs (D-030)
├── tools/gen_brand_icons.py    regenerates the bundled brand logos (D-028); needs internet, the app doesn't
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
| `BiometricKeys.kt` | Android Keystore keys bound to strong biometrics. The unlock key (StrongBox if available) wraps/unwraps the master key; the reauth key proves a real fingerprint check for "confirm it's you" (D-036) and is made only after the master password was proven. |

### `data/`: vault model and storage
| File | What it does |
|---|---|
| `Models.kt` | Serializable data: `VaultItem`, `LoginData`, `CardData`, `VaultData`, `VaultMeta` (plain header), `VaultSettings`, `BackupFile`. `Sealed` = a field-encrypted string. `Field` = field names used as encryption context. |
| `Lockout.kt` | **Pure Kotlin** wrong-password lockout on the since-boot clock; restarts after a reboot (D-034). |
| `VaultCodec.kt` | **Pure Kotlin** format logic: create a vault, wrap/unwrap the master key with a password, encrypt/decrypt the data file, make/parse/open backups (size and Argon2 limits, D-035), re-seal items under another key, merge items. Fully unit-tested. |
| `VaultStorage.kt` | Reads/writes `vault.meta` and `vault.dat` atomically (`AtomicFile`) in `noBackupFilesDir`, plus `vault.dat.next`, which exists only during a master-key change (D-032). Overwrites the files before deleting them on wipe. |
| `VaultRepository.kt` | The **single owner of vault state**. Exposes `state: StateFlow<VaultState>` (NoVault / Locked / Unlocked(items)) and `meta`. Handles create, unlock (password or biometric), lock, add/edit/delete, reveal/seal fields, settings, password change, backup export/import/restore, wipe, and the failed-attempt counter. Holds the keys only while unlocked. |

### `security/`: runtime protections
| File | What it does |
|---|---|
| `AutoLock.kt` | `ProcessLifecycleOwner` observer. Locks when the app goes to the background (after the chosen delay) and after 5 minutes without a touch. Holds off while the system file picker is open. |
| `SecureClipboard.kt` | Copies with the sensitive-content flag and clears after N seconds; an alarm and `ClipboardClearReceiver` clear it if the app was killed first (D-038). |
| `DeviceIntegrity.kt` | Best-effort root check, used for a warning only. |

### `util/`: helpers (pure Kotlin)
| File | What it does |
|---|---|
| `PasswordTools.kt` | `PasswordGenerator` (SecureRandom, at least one character from each chosen group, shuffled) and `PasswordStrength` (entropy estimate with penalties for common passwords and sequences). |
| `BrandMatcher.kt` | `Brand` model and `BrandMatcher`: picks a brand for an item from its website (longest matching domain), then whole-word keywords in the name; cards use bank, then name, then card network. Runs offline. |
| `BrandCatalog.kt` | **Generated** by `tools/gen_brand_icons.py`, do not edit. 83 brands: Simple Icons 16.34.0 logo paths (CC0) and colours, plus 8 colour-only brands with no free logo. |
| `CardUtils.kt` | Card network detection, Luhn check, number formatting, expiry validation, and the expiry digit helpers `sanitizeExpiryDigits` / `expiryFromDigits` / `expiryToDigits` (D-025). |

### `ui/`: Jetpack Compose screens
| File | What it does |
|---|---|
| `AppRoot.kt` | Top-level switch on `VaultState` (Setup / Unlock / unlocked app) and the in-memory back stack (`Screen`: Home, Detail, Edit, Generator, Settings). |
| `Biometric.kt` | `biometricPrompt()` suspend wrapper around `BiometricPrompt`, plus `LocalActivity`. |
| `Reauth.kt` | `ReauthController` + `ReauthHost`: "confirm it's you" for PIN/CVV (Keystore-bound fingerprint, or master password dialog), with a 60-second grace period. |
| `theme/Theme.kt` | Light and dark colour schemes, `BrandColors` (brand gradient), strength-meter colours. |
| `components/Components.kt` | `PasswordField`, `StrengthMeter`, `ItemAvatar`, `FieldRow`, `ConfirmDialog`, `PasswordPromptDialog`, `BrandMark` (logo tile) and `brandBrush()` (D-026). `ItemAvatar` shows a brand logo tile when `brandOf(item)` finds one (D-028), otherwise the coloured letter / card icon. |
| `screens/LockScreens.kt` | `SetupScreen` (create or restore a vault) and `UnlockScreen` (password, fingerprint, lockout countdown, root warning). Both show the logo and the SafeVault name. |
| `screens/HomeScreen.kt` | Branded `VaultHeader` (logo, name, item counts, generator/lock/settings) fixed at the top, then search, filter chips, item list as rounded cards, the add sheet, fingerprint suggestion card. Switches status-bar icons to light while shown. |
| `screens/DetailScreen.kt` | Shows a login or card. Reveal/copy with auto-hide after 30 s; PIN/CVV go through `ReauthController`. |
| `screens/EditScreen.kt` | Add/edit form for logins and cards. `Draft` holds plain values only while open, sealed again on save. Shows the generator on top of itself so the draft survives (D-023). Expiry is kept as digits and drawn as MM/YY by `ExpiryTransformation` (D-025). |
| `screens/GeneratorScreen.kt` | Password generator; can hand a password back to the editor. |
| `screens/SettingsScreen.kt` | All settings, backup export/import (Storage Access Framework), change password, delete vault. Actions that weaken protection or export data go through a master-password gate (`withPassword`, D-031). |

### Root files
| File | What it does |
|---|---|
| `SafeVaultApp.kt` | `Application`: creates `VaultRepository`, `AutoLock`, `SecureClipboard` (manual DI). |
| `MainActivity.kt` | Sets `FLAG_SECURE` and `filterTouchesWhenObscured`, edge-to-edge, incognito keyboard for all fields (`IncognitoKeyboard`), provides `LocalActivity`, reports touches to `AutoLock`. |

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
biometricWrappedKey?, failedAttempts, lockoutMs, lockoutBoot, lockoutEndsAt, settings{…}`

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

**Edit a card:** CVV/PIN stay sealed (`Draft.cardSecretsLocked`) until **Show PIN and CVV** passes
`ReauthController.confirm`; saving while still locked keeps the stored sealed values (D-031).

**Change master password:** `repo.changePassword` → `VaultCodec.rotateMaster` (new master key,
every field re-sealed, new wrap, biometric off) → write `vault.dat.next` → write `vault.meta`
(commit) → rename over `vault.dat` → session switches to the new keys. `openFromDisk` finishes
or discards a change a crash interrupted (D-032).

**Save an item:** `EditScreen.save` → `repo.seal(...)` each secret → `repo.upsert(item)` →
re-encrypt the whole `VaultData` → atomic write.

**Lock:** `AutoLock` or the 🔒 button → `repo.lock()` → `VaultKeys.wipe()`, master key zeroed,
state = `Locked` → `AppRoot` shows `UnlockScreen`. The unlocked back stack is discarded.

---

## 5. Building

Requirements (already installed on this PC):
- JDK 17: `C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot`
- Android SDK: `%LOCALAPPDATA%\Android\Sdk` (platform 36, build-tools 36)

From Git Bash in the project folder:

```bash
export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot"
./gradlew testDebugUnitTest      # run unit tests
./gradlew assembleDebug          # debug APK (app id com.shankhadas.safevault.debug)
./gradlew assembleRelease        # signed, shrunk release APK
./gradlew bundleRelease          # signed release AAB for Google Play
```

Outputs:
- `app/build/outputs/apk/debug/app-debug.apk`
- `app/build/outputs/apk/release/app-release.apk` (copied to `release/SafeVault-<version>.apk`)
- `app/build/outputs/bundle/release/app-release.aab` (copied to `release/SafeVault-<version>.aab`; this is what goes to Play)

The release build is signed only if `signing/keystore.properties` exists.

**Releasing a new version:** bump `versionCode` (+1) and `versionName` in
`app/build.gradle.kts`, build the release, and install it over the old app. It must be signed
with the same key, or Android refuses the update. For Google Play, upload the AAB; every
upload needs a `versionCode` higher than any uploaded before. Play's yearly target-SDK rule
means `targetSdk` has to be raised about once a year (D-039).

---

## 6. Testing

**Unit tests** (`app/src/test`, run on the PC in seconds):
- `VaultCodecTest`: password wrap/unwrap, wrong password, encrypt/decrypt round trip, secrets
  stay sealed after the outer decrypt, tamper detection, field context binding, random IVs,
  password change, master-key rotation on password change (old backup + old password can't open
  new data), backup round trip, invalid backup files, hostile or weak Argon2 settings and
  oversized files rejected, re-sealing, merge rules, Argon2 determinism.
- `LockoutTest`: free mistakes, doubling delays and cap, countdown on the since-boot clock,
  reboot restarts an unfinished lockout, finished lockouts are forgotten, missing boot count,
  old `lockoutUntil` files still load.
- `PasswordToolsTest`: generator length, character groups and uniqueness; strength ratings;
  word-plus-digits passwords rated Weak; master password policy (12+ characters, Strong).
- `BrandMatcherTest`: name and website matching, longest match wins, no matches inside other
  words, card bank-then-network order, colour-only brands, catalogue sanity.
- `CardUtilsTest`: expiry typed key by key keeps year digits in order, single-digit month
  padding, extra digits dropped, saved value round trip, month validation.

**Desktop preview:** open `desktop-preview/index.html` in any browser. It mirrors the screens
and ports the `CardUtils` rules to JavaScript; it is not the Kotlin code, so the APK still
needs a check on the phone.

**Demo video:** `python -I desktop-preview/make_demo.py` drives the preview in headless Chrome
(Playwright), screenshots each step with a caption panel, generates soft background music
with numpy and encodes `demos/SafeVault-1.1.0-demo.mp4` (1080p, 30 fps, about 3 min) with
the ffmpeg bundled in `imageio-ffmpeg`. Needs `pip install playwright imageio-ffmpeg pillow
numpy` and Chrome. Generated 2026-11-02: 279 shots, 3:05, music at -22 dB mean.

**Social media kit:** `make_shorts.py` reuses the demo's chapter functions to record three
short videos into `demos/shorts/`, each as `-16x9.mp4` (1920x1080) and `-9x16.mp4`
(1080x1920, layout swapped by `VERTICAL_CSS`); pass a part number to re-record one.
`make_carousel.py` screenshots the preview's phone at 2x and lays out 9 slides at 1080x1350,
saved as `demos/carousel/SafeVault-carousel.pdf` plus `slide-NN.png`. The post texts are in
`demos/LINKEDIN_POSTS.md`.

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

**2026-11-02: v1.1.0 release APK** (`release/SafeVault-1.1.0.apk`, SHA-256
`9a7aa98dfa7d2f23de1069d3806635e121d7fd679b5a488b571d40403af15d83`).

| Check | Result |
|---|---|
| Unit tests (release variant) | 27/27 pass |
| Version | `versionCode 2`, `versionName 1.1.0` |
| Signature | v2, `CN=SafeVault, O=Personal, C=IN`, certificate SHA-256 `9a91a425…58f1095f`, **same as 1.0.0**, so it installs as an update |
| Permissions | `USE_BIOMETRIC`, `USE_FINGERPRINT` only, **no INTERNET** |
| `allowBackup` / `fullBackupContent` | both `false`; not debuggable |
| Data/crypto/backup code vs 1.0.0 | unchanged (`git diff` empty for `data/`, `crypto/`, `security/`, manifest) |
| Update over 1.0.0 on the emulator (`adb install`, no `-r`, no uninstall) | ✔ accepted as an update; `firstInstallTime` kept, `versionCode` 1 → 2 |
| First launch after update | ✔ opens on "Vault locked" with the SafeVault logo and name, so the existing 1.0.0 vault was found; no crash in logcat |
| Unlocking and using the updated vault, expiry typing, brand logos on device | not done: the emulator's UI is too slow to drive (see above); check on the phone |

**2026-11-02: expiry fix and Home redesign (debug build).**

| Check | Result |
|---|---|
| Unit tests | 21/21 pass (5 new in `CardUtilsTest`) |
| `compileDebugKotlin`, `assembleDebug` | ✔ |
| Setup screen shows logo and "SafeVault" (emulator, uiautomator) | ✔ |
| Home header: name, "Encrypted · Offline", Logins/Cards/Favourites counts, three actions (emulator) | ✔ |
| Expiry typed key by key on the emulator | **Not completed**: the emulator ran at ~2 fps on software rendering and stopped taking input. Covered by unit tests and the desktop preview. |
| Unit tests after brand logos | 27/27 pass (6 new in `BrandMatcherTest`); debug build ✔ |
| Preview: brand tiles for Gmail, HDFC, Netflix, Instagram, Swiggy, GitHub, Mastercard (Federal Bank card); LinkedIn letter on brand blue; "Work laptop" keeps its letter; dark mode readable | ✔ (brand logos not yet seen on a device) |
| Logo: the preview's lock-screen logo had a distorted keyhole and an off-centre shield (preview-only bug, fixed). The APK draws the unmodified `ic_launcher_foreground` vector centred by `BrandMark`; confirmed in code, not by screenshot (FLAG_SECURE) | ✔ |
| Desktop preview: `1227` → `12/27`, `529` → `05/29`, `13` flagged, backspace across the slash, `083199` → `08/31`, save shows `12/27` | ✔ |

**2026-11-03/04: security test (VAPT) and fixes for its medium findings.** Code review of the
whole app, the release APK (aapt2/apksigner) and proof-of-concept unit tests.

| Check | Result |
|---|---|
| Release APK: permissions, exported components, backup flags, signing, secrets in git | ✔ no INTERNET; only androidx's DUMP-protected receiver exported; signing files never committed |
| M1 PIN/CVV readable from the Edit screen without re-auth | found → fixed (D-031) |
| M2 export / fingerprint on / checks off without master password | found → fixed (D-031) |
| M3 old backup + old password decrypts data written after a password change | found (PoC recovered the secret) → fixed (D-032), regression test |
| M4 "Mumbai@123", "India@2024" accepted as master passwords | found (PoC) → fixed (D-033), regression tests |
| Unit tests after fixes | 30/30 pass; `assembleRelease` ✔ |
| Fixes on a device (Edit unlock button, Settings password prompts, password change then unlock) | not done: emulator UI too slow; check on the phone |

Low-severity findings, fixed 2026-11-04:

| Check | Result |
|---|---|
| L1 lockout skipped by changing the date; prompts didn't count wrong passwords | fixed (D-034), `LockoutTest` |
| L2 hostile backup settings / huge file; restore kept the file's Argon2 settings | fixed (D-035), regression tests |
| L3 "confirm it's you" fingerprint not tied to a key | fixed (D-036) |
| L4 tapjacking on Android 8-11 | fixed (D-037) |
| L5 task hijacking on Android < 11 | fixed: release APK has `taskAffinity=""` (D-037) |
| L6 keyboard could learn notes, usernames, names | fixed (D-037) |
| L7 clipboard not cleared if the app is killed | fixed (D-038); receiver is `exported=false` in the release APK |
| Info: Keystore errors crashed the unlock screen; search text in saved state | fixed (D-038, D-037) |
| Unit tests after fixes | 40/40 pass; `assembleRelease` ✔; still no INTERNET permission |
| On a device: Keystore-bound prompt, incognito keyboard, overlay filter, clipboard alarm after force-stop, lockout across a date change and a reboot | not done: emulator UI too slow; check on the phone |

Still open (by design or accepted): wiping overwrites files with zeros, which flash storage
doesn't guarantee to erase (file-based encryption covers this); the vault stays unlocked up to
3 minutes while the file picker is open (D-012).

**2026-11-04: v1.2.0 release APK** (`release/SafeVault-1.2.0.apk`, SHA-256
`4f656e3de9232cfed59317c01b1d8e5f3ab731c738b670866a22e5e041653a39`). All VAPT fixes (D-031 to D-038).

| Check | Result |
|---|---|
| Clean build, unit tests (release variant) | 40/40 pass |
| Version | `versionCode 3`, `versionName 1.2.0` |
| Signature | v2, `CN=SafeVault, O=Personal, C=IN`, certificate SHA-256 `9a91a425…58f1095f`, **same as 1.1.0**, so it installs as an update |
| Permissions | `USE_BIOMETRIC`, `USE_FINGERPRINT` only, **no INTERNET**; not debuggable |
| Manifest | `taskAffinity=""` on `MainActivity`; `ClipboardClearReceiver` not exported |
| Update over 1.1.0 and the new behaviour on a device | not done; check on the phone (see the VAPT table above) |

**Not yet tested:** fingerprint enrolment and unlock, import (merge) into an existing vault,
change master password, the erase-after-10 setting, and a real phone.

**2026-11-09: v1.2.1, target SDK 36 for Google Play** (`release/SafeVault-1.2.1.apk`, SHA-256
`b5b4b647c05efd1f5bdf979fef18ccba34dbcdfd107681180c17137fcfcc517c`; `release/SafeVault-1.2.1.aab`,
SHA-256 `4084983cda4bebb2871baf391662fb3749ef471e9258f4728bf9e60e60161c0a`). No code changes, only
SDK levels (D-039).

| Check | Result |
|---|---|
| Unit tests (release variant) | 40/40 pass |
| Version / SDK | `versionCode 4`, `versionName 1.2.1`, compile and target SDK 36 |
| Signature | APK and AAB signed with `CN=SafeVault, O=Personal, C=IN`, certificate SHA-256 `9a91a425…58f1095f` (same as 1.2.0) |
| Permissions | `USE_BIOMETRIC`, `USE_FINGERPRINT` only, **no INTERNET** |
| Native libraries | only `libandroidx.graphics.path.so` (Compose), LOAD segments aligned to 16 KB on all 4 ABIs |
| Update over 1.1.0 on the Android 15 emulator | ✔ installed as an update, launched, no crash |
| Screens on the emulator | not checked: the emulator was too slow (system "not responding" dialogs) |

**Not yet tested:** 1.2.1 on an Android 16 device or emulator, and the predictive-back
behaviour that target SDK 36 turns on (back is handled by Compose `BackHandler`, which supports it).

**2026-11-10: v1.3.0, package name `com.shankhadas.safevault`** (`release/SafeVault-1.3.0.apk`,
SHA-256 `b933ad6a828aa1aab69ecdf2a4413fe304fc93fc0ea030c5edf9a1aaa1e047a8`; `release/SafeVault-1.3.0.aab`,
SHA-256 `04e67ba4e7ee5925e8b27d08e88ac22bcfefac2709a7bb637ab4d718fafaabd2`). Only `applicationId`
changed (D-040); the Kotlin package and `namespace` stay `com.safevault.app`.

| Check | Result |
|---|---|
| Unit tests (release variant) | 40/40 pass |
| Package / version | `com.shankhadas.safevault`, `versionCode 5`, `versionName 1.3.0`, target SDK 36 |
| Permissions | `USE_BIOMETRIC`, `USE_FINGERPRINT` only, **no INTERNET** |
| AAB signature | verifies, same `release.jks` key |

**Not yet tested:** 1.3.0 on a device, including restoring a backup made by the old
`com.safevault.app` install.

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
