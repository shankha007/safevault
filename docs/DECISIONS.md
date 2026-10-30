# Decision log

Every technical decision made while building SafeVault: what was chosen, what else was
considered, and why. Newest entries go at the bottom. If a decision is reversed later, the
old entry stays and a new one explains the change.

Status values: **Accepted** (in use), **Superseded** (replaced by a later entry), **Open**
(needs your input).

---

## D-001 · Native Android app in Kotlin with Jetpack Compose
**Date:** 2026-10-28 · **Status:** Accepted

**Decision:** Write the app in Kotlin, build the UI with Jetpack Compose and Material 3.

**Alternatives:** Flutter, React Native, Java with XML layouts.

**Why:**
- The security features we rely on most (Android Keystore, BiometricPrompt, FLAG_SECURE,
  backup rules) are native Android APIs. Using them directly avoids bridge layers that
  add code and possible bugs.
- Compose is Google's current recommended UI toolkit, and it supports dark mode and
  accessibility out of the box.
- Smaller APK and fewer third-party dependencies than cross-platform frameworks.

---

## D-002 · Minimum Android version 8.0 (API 26), target Android 15 (API 35)
**Date:** 2026-10-28 · **Status:** Accepted

**Why:** API 26 covers almost every phone still in use, and gives us `java.util.Base64`,
hardware-backed Keystore and adaptive icons without compatibility shims. Targeting API 35
gets the latest platform security behaviour.

---

## D-003 · No INTERNET permission at all
**Date:** 2026-10-28 · **Status:** Accepted

**Decision:** The manifest has no INTERNET permission. A `tools:node="remove"` rule also
strips it if any library tries to add it during the manifest merge.

**Why:** This is the strongest possible guarantee against leaks. Android itself blocks
every network socket the app might open, so even a bug or a compromised library can't
send your data anywhere.

**Trade-off:** Features that need the internet (for example, checking passwords against
known data breaches) can't be added without giving up this guarantee.

---

## D-004 · Single encrypted vault file instead of SQLCipher database
**Date:** 2026-10-28 · **Status:** Accepted (changes what the original plan said)

**Decision:** Store the whole vault as one AES-256-GCM encrypted file (`vault.dat`) plus a
small plain-JSON header (`vault.meta`) that holds no secrets. This is the same approach
KeePass and Bitwarden's offline exports use.

**Original plan:** Room + SQLCipher (an encrypted SQLite database).

**Why the change:**
- **Smaller attack surface.** SQLCipher is a large native (C) library. Our approach uses
  only the AES-GCM built into Android plus a small Kotlin file.
- **Everything is encrypted**, including item names, counts and timestamps.
- **Tamper-proof.** AES-GCM detects any change to the file; a modified file is refused.
- **Backups are trivial.** A backup is the same encrypted file plus header, so there's no
  separate export format to get wrong.
- **Testable on a PC.** All format logic is plain Kotlin, covered by JVM unit tests.
- A personal vault has tens or hundreds of items. Re-encrypting the whole file on each save
  takes milliseconds, so a database brings no real benefit.

**Writes are atomic:** `android.util.AtomicFile` writes to a temp file and renames it, so a
crash or power loss during a save can't corrupt the vault.

---

## D-005 · Two layers of encryption
**Date:** 2026-10-28 · **Status:** Accepted

**Decision:**
- **Outer layer:** the whole vault file is encrypted with `fileKey`.
- **Inner layer:** each secret (password, card number, CVV, PIN, notes) is encrypted on its
  own with `fieldKey`, using the item id and field name as associated data.

**Why:** After you unlock, the vault list is in memory, but passwords, PINs and CVVs stay
encrypted until you tap "show" or "copy". Binding each ciphertext to its item and field
means an encrypted PIN can't be swapped into another field or item.

`fileKey` and `fieldKey` are derived from the master key with HMAC-SHA256 and different
labels, so no key is used for two purposes.

---

## D-006 · Argon2id for the master password (Bouncy Castle, pure Java)
**Date:** 2026-10-28 · **Status:** Accepted

**Decision:** Argon2id with 46 MiB memory, 2 iterations, parallelism 1, a 16-byte random salt,
and a 256-bit output. Bouncy Castle (`bcprov-jdk18on`) provides the implementation.

**Alternatives:** PBKDF2 (built in, but weak against GPU cracking); scrypt; the `argon2kt`
library (native C, faster).

**Why:**
- Argon2id won the Password Hashing Competition and is OWASP's first recommendation. It's
  memory-hard, which makes GPU and ASIC cracking very expensive.
- These settings are above OWASP's minimum (19 MiB, t=2).
- Bouncy Castle is pure Java, so it runs in PC unit tests and adds no native code.
  `argon2kt` would be faster but adds a JNI library.
- The settings are saved in `vault.meta`, so a future version can raise them without
  breaking existing vaults.

**Check:** Unlock time should be about 1 second on a mid-range phone. If it's much slower on
your phone, we can tune it. Logged as a follow-up.

---

## D-007 · Random master key, wrapped by the password
**Date:** 2026-10-28 · **Status:** Accepted

**Decision:** The vault is encrypted with a random 256-bit master key. The master password
(through Argon2id) only wraps (encrypts) that key.

**Why:**
- Changing the master password re-wraps 32 bytes; the vault data never needs re-encrypting.
- Fingerprint unlock can wrap the same master key a second way (D-008).
- The master key comes from `SecureRandom`, so it's full-strength even when the password is
  weaker.

---

## D-008 · Fingerprint unlock through an Android Keystore key
**Date:** 2026-10-28 · **Status:** Accepted

**Decision:** Generate an AES-256-GCM key in the Android Keystore with:
- `setUserAuthenticationRequired(true)`: the key works only right after a fingerprint check
- `AUTH_BIOMETRIC_STRONG`: only Class 3 (strong) biometrics, not weak face unlock
- `setInvalidatedByBiometricEnrollment(true)`: adding a new fingerprint kills the key
- StrongBox (dedicated security chip) when the phone has one, otherwise the TEE

That key wraps the master key. Unlocking uses `BiometricPrompt` with a `CryptoObject`, so the
fingerprint check is enforced by the secure hardware, not just by app code.

**Why:** This is the pattern Google recommends. A fingerprint "yes/no" without a
CryptoObject could be bypassed on a compromised phone; a CryptoObject can't.

**Fallback:** The master password always works. If fingerprints change, fingerprint unlock
turns itself off and the user is told why.

---

## D-009 · Defaults chosen for questions not yet answered
**Date:** 2026-10-28 · **Status:** Open (change any of these whenever you like)

The plan asked five questions. Phase 1 started before they were answered, so these defaults
were used. Each one can be changed in the app's settings or with a small code change.

| Question | Default used | Why |
|---|---|---|
| Phone model / Android version | Supports Android 8.0 and newer | Covers nearly all phones |
| Fingerprint or face? | Fingerprint/strong biometric if present; master password always works | Works on every phone |
| Wipe after 10 wrong attempts? | **Off** by default, available in Settings | Accidental wipe is a bigger everyday risk than a thief guessing a strong password. Growing delays are always on. |
| Store CVV? | **Yes**, but optional per card, and shown only after fingerprint or master password | You asked for it; extra gate limits exposure |
| Extras in Phase 1? | None beyond the plan | Keep Phase 1 small and solid |

---

## D-010 · Brute-force protection: growing delays, optional wipe
**Date:** 2026-10-28 · **Status:** Accepted

**Decision:** The first 4 wrong passwords have no delay (typos happen). From the 5th: 30 s,
then 1 min, 2 min, 4 min, and so on, capped at 1 hour. The counter resets after a successful
unlock. An optional setting erases the vault after 10 wrong attempts in a row.

**Limitation:** The counter is stored on the phone. Someone who has already extracted the
vault files (which needs root) can guess offline without delays. In that case Argon2id's cost
and the strength of your master password are what protect you, which is why the setup screen
refuses weak master passwords.

---

## D-011 · Screen and system-level protections
**Date:** 2026-10-28 · **Status:** Accepted

- **FLAG_SECURE** on the window: no screenshots, no screen recording, no casting, and a blank
  preview in the recent-apps list.
- **`allowBackup="false"`**, **`fullBackupContent="false"`**, and **`dataExtractionRules`**
  excluding everything: no Google cloud backup and no device-to-device copy.
- Vault files live in **`noBackupFilesDir`**, which Android never backs up, as a second guard.
- **Release build:** R8 shrinking and obfuscation, `debuggable=false`, and every
  `android.util.Log` call removed, so nothing reaches logcat.
- **No analytics, ads, or crash-reporting SDKs.**

---

## D-012 · Auto-lock rules
**Date:** 2026-10-28 · **Status:** Accepted

**Decision:**
- Locks when the app leaves the screen, immediately by default. You can choose 30 s, 1 min
  or 5 min instead.
- Locks after 5 minutes without a touch, even if the app is open.
- Locking wipes the in-memory keys (`ByteArray.fill(0)`) and drops the decrypted list.
- While the system file picker is open (for backup or restore), locking is held off for up
  to 3 minutes. Otherwise opening the picker would lock the vault right away.

**Uses** `ProcessLifecycleOwner`, so it reacts to the whole app going to the background, not
to individual screens.

---

## D-013 · Clipboard handling
**Date:** 2026-10-28 · **Status:** Accepted

**Decision:** Copied values are tagged `EXTRA_IS_SENSITIVE`, which hides the preview on
Android 13+ and keeps them out of clipboard history on keyboards that honour the flag. They
are cleared after 30 s by default (configurable to 15 s, 1 min or 2 min).

**Limitation:** The clear timer runs inside the app. If Android kills the app before the timer
fires, the clipboard isn't cleared. A WorkManager job could fix this, but it adds a dependency
for a rare case. Logged as a possible Phase 2 improvement.

---

## D-014 · Secrets never go into saved instance state
**Date:** 2026-10-28 · **Status:** Accepted

**Decision:** Screens that hold decrypted values use `remember`, never `rememberSaveable`.
The activity declares `configChanges` for rotation, dark-mode switches and similar, so it
isn't recreated and nothing is lost.

**Why:** Android can write saved instance state to disk when it kills an app in the background.
Secrets must never take that path.

---

## D-015 · Simple in-memory navigation instead of Navigation-Compose
**Date:** 2026-10-28 · **Status:** Accepted

**Decision:** A small back stack of `Screen` objects in `AppRoot.kt`.

**Why:** Six screens don't need a navigation library. Navigation-Compose saves the back stack
(including arguments) into saved state; ours holds only item ids and is rebuilt from scratch
on every unlock, so after a lock you always land on the home screen.

---

## D-016 · Manual dependency injection
**Date:** 2026-10-28 · **Status:** Accepted

**Decision:** `SafeVaultApp` creates `VaultRepository`, `AutoLock` and `SecureClipboard` once
and screens fetch them from there.

**Why:** Hilt or Koin add code generation and a dependency for what amounts to three objects.

---

## D-017 · Encrypted backup format and restore behaviour
**Date:** 2026-10-28 · **Status:** Accepted

**Decision:** A backup (`.svault` file) is JSON that contains the KDF settings, salt, the
password-wrapped master key and the encrypted vault file. It never contains the fingerprint-
wrapped key, because that key is tied to this phone's hardware.
- **Restore on a new phone** (setup screen): the backup becomes the vault, and its password
  becomes the master password.
- **Import into an existing vault** (settings): items are merged by id, the newer copy wins,
  and secrets are re-encrypted under this vault's key.
- Files are saved and opened with Android's system file picker (Storage Access Framework), so
  the app needs **no storage permission**.

**Note:** A backup opens with the master password that was in use when it was made. Changing
the master password later doesn't change existing backup files. The app says so after a
password change.

---

## D-018 · Card data handling
**Date:** 2026-10-28 · **Status:** Accepted

- Card number, CVV and PIN are encrypted fields (D-005). The last 4 digits are kept as a
  normal field (still inside the encrypted file) so the list can show "•••• 4821" without
  decrypting anything.
- The card network (Visa, Mastercard, RuPay, Amex, and others) is detected from the number.
  A Luhn checksum warns about typos but doesn't block saving.
- By default, PIN and CVV ask for a fingerprint or the master password each time they're
  shown. One successful check covers 60 seconds. The user can turn this off in Settings.
- Revealed values hide themselves again after 30 seconds.

---

## D-019 · Master password rules
**Date:** 2026-10-28 · **Status:** Accepted

**Decision:** At least 8 characters and rated "Fair" or better by the built-in strength meter,
which rejects common passwords and simple sequences. The user must tick "I understand it
can't be recovered".

**Why:** The master password is the last line of defence if the vault files are ever copied
off the phone (D-010). Recovery is impossible by design, because there's no server.

---

## D-020 · Library versions
**Date:** 2026-10-28 · **Status:** Accepted

| Library | Version | Purpose |
|---|---|---|
| Android Gradle Plugin | 8.7.3 | Build |
| Gradle | 8.11.1 | Build |
| Kotlin | 2.0.21 | Language |
| Compose BOM | 2024.12.01 | UI |
| androidx.biometric | 1.1.0 | Fingerprint prompt |
| kotlinx-serialization-json | 1.7.3 | Vault JSON |
| Bouncy Castle bcprov-jdk18on | 1.79 | Argon2id |

**Why these:** These are well-established stable releases. Newer versions exist; we'll upgrade
only when there's a security fix or a feature we need, and log each upgrade here.

---

## D-021 · Root detection is a warning only
**Date:** 2026-10-28 · **Status:** Accepted

**Decision:** On the unlock screen, the app warns if it finds common root signs (su binaries,
Magisk, test-keys). It doesn't refuse to run.

**Why:** Root detection is easy to fool, so relying on it would be false security. The warning
helps an honest user understand the risk.

---

## D-022 · Release signing key stays outside the source tree's history
**Date:** 2026-10-28 · **Status:** Accepted

**Decision:** The release keystore and its passwords live in `signing/`, which is in
`.gitignore`. `app/build.gradle.kts` reads them from `signing/keystore.properties` if it exists.

**Why:** Anyone with the signing key could build an "update" that Android would install over
your app. **You must back up the `signing/` folder somewhere safe.** Without it, a future
version can't be installed as an update. You'd have to export a backup, uninstall, install
the new version, and restore.

The key is RSA 4096, valid for about 27 years, and the APK uses signature scheme v2. v1 (JAR)
signing isn't needed because the minimum is Android 8.0.

---

## D-023 · Password generator opens on top of the editor, not as its own screen
**Date:** 2026-10-28 · **Status:** Accepted (bug fix found in emulator testing)

**Problem:** The generator was originally a separate screen on the back stack. Opening it
removed the editor from the screen, and because editor values are kept in `remember` on
purpose (D-014), everything typed so far was lost. Testing caught this: the username was
blank after using the generator.

**Decision:** `EditScreen` draws `GeneratorScreen` itself while `showGenerator` is true, so the
editor's state stays alive. The standalone generator (from the home screen) is still a normal
screen.

**Why not `rememberSaveable`:** That would put plaintext passwords into saved state (D-014).

---

## D-024 · How the app is tested
**Date:** 2026-10-28 · **Status:** Accepted

**Decision:**
1. **JVM unit tests** for everything security-critical that doesn't need Android: key
   derivation, encryption, the file format, backups, merge rules, the generator and the
   strength meter. They run on the PC in seconds.
2. **Manual end-to-end runs on an Android 15 emulator** (Pixel 6 image), driven with
   `adb input` and read with `uiautomator dump`. Screenshots can't be used because
   FLAG_SECURE blacks them out, which also proves FLAG_SECURE works.
3. **Inspecting the files on disk** with root on the emulator, to confirm nothing readable is
   stored.

**Not covered yet:** fingerprint flows (the emulator has no enrolled fingerprint), a physical
phone, and automated UI tests. These will be checked on your phone.

**Alternatives:** Espresso or Compose UI tests. These are worth adding once the UI settles,
because they need a running device and slow the build.
