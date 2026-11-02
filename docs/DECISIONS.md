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

---

## D-025 · Card expiry is stored as digits while editing; the slash is only drawn
**Date:** 2026-11-02 · **Status:** Accepted

**Problem:** the expiry field wrote the "/" into the text itself. Compose kept the cursor
where it was, which was before the new slash, so the year digits were inserted in reverse
order: typing `1 2 2 7` produced `12/72`.

**Decision:** the editor keeps only the digits (`"1227"`). A `VisualTransformation`
(`ExpiryTransformation`) draws them as `12/27` with an offset mapping, so the cursor always
follows the digits and backspace skips over the slash. The slash is added back when saving,
so the stored format (`MM/YY`) is unchanged and existing cards open normally. A first digit
of 2 to 9 becomes `0x`, since no month starts with those digits.

**Alternatives:** keeping the slash in the value and moving the cursor to the end with a
`TextFieldValue` (works, but editing in the middle jumps the cursor); two separate MM and YY
boxes (more taps, and autofill-style paste of "12/27" breaks).

---

## D-026 · Branded Home header and lock screens
**Date:** 2026-11-02 · **Status:** Accepted

**Problem:** the app name appeared nowhere on the Home screen (the bar said "Your vault"),
so the app had little recall value.

**Decision:** Home now opens with a fixed header on a blue gradient that starts at the
launcher icon's colour: the launcher's shield-and-keyhole as a logo tile (`BrandMark`), the
name **SafeVault**, the line "Encrypted · Offline", the generator / lock / settings buttons,
and counts of logins, cards and favourites. Items are shown as rounded cards. The setup and
unlock screens show the same logo and name. Status-bar icons switch to light while Home is
shown so they stay readable on the blue.

**Why fixed, not scrolling:** the name and the lock button stay visible at all times.

**Alternatives:** a Material `LargeTopAppBar` titled "SafeVault" (plainer, collapses away);
a separate splash screen (seen for a moment only, and it slows every unlock).

---

## D-027 · Temporary desktop preview before building the APK
**Date:** 2026-11-02 · **Status:** Accepted (temporary)

**Decision:** `desktop-preview/index.html` is a single HTML file that mirrors the Lock, Home,
Add/Edit and Detail screens with the same colours and the `CardUtils` rules ported to
JavaScript, so UI changes can be tried in a desktop browser before an APK is built. It holds
sample data in memory only, has no encryption, and is not part of the app build.

**Why:** the emulator on this PC is slow and FLAG_SECURE blocks screenshots, which makes
quick visual review hard.

**Limits:** it is a copy, not the Kotlin code, so it can drift. Delete the folder once the
APK is signed off.

**Alternatives:** Compose Multiplatform desktop target (needs the UI split into a shared
module, a large change); emulator screenshots with FLAG_SECURE turned off in debug builds
(would weaken a security check, see D-011).

---

## D-028 · Bundled brand logos for items, matched on the phone
**Date:** 2026-11-02 · **Status:** Accepted

**Decision:** item avatars show the real brand logo for common apps, banks and card
networks. The logos are vector paths from **Simple Icons 16.34.0** (CC0 licence), compiled
into the app as `util/BrandCatalog.kt`. `tools/gen_brand_icons.py` downloads the pinned
release, keeps the 75 chosen brands, checks every path is plain SVG path syntax, and writes
the Kotlin file and the preview's copy. Each logo sits in white (black for very light
colours) on a tile of the brand's colour, like an app icon.

**Matching** (`BrandMatcher`): the Website field first (longest matching domain, so
`mail.google.com` is Gmail, not Google), then whole words of the name (longest keyword wins,
so "Google Pay" beats "Google"). Cards use the bank, then the name, then the detected
network. Generic words such as "visa" and "discover" are not name keywords, so a login
called "Visa application" isn't labelled as a card network.

**Brands with no free logo:** LinkedIn, Amazon, Microsoft (Outlook, Hotmail, Xbox,
OneDrive, Teams, Skype), Slack, Yahoo, Flipkart, ChatGPT/OpenAI and Canva asked Simple Icons
to remove theirs. They show their first letter on the brand colour rather than a
hand-drawn imitation. SBI, Kotak, RuPay, Hotstar and other brands that aren't in the set
keep the normal letter avatar.

**Why bundled:** SafeVault has no INTERNET permission (D-003). Fetching favicons would need
it and would tell a server which sites are in the vault. Matching runs locally on fields
that are already decrypted in memory while the vault is open, so nothing new is exposed.
Cost: about 73 KB of source, a little more in the APK.

**Trademarks:** the logos belong to their owners and are used only to identify the user's
own items, which is the usual practice in password managers.

**Alternatives:** favicon download (needs internet, leaks site list); letting the user pick
an icon (more taps, and few would); full-colour official logo files (no single licensed
source, much larger).

---

## D-029 · Product demo video recorded from the desktop preview
**Date:** 2026-11-02 · **Status:** Accepted (temporary, lives with D-027)

**Decision:** the product tour `demos/SafeVault-1.1.0-demo.mp4` is made by
`desktop-preview/make_demo.py`. It drives the HTML preview in headless Chrome, puts a caption
panel (chapter, feature list with the current point highlighted, one-line subtitle) beside
the phone, shows a touch marker before each tap, and stitches the screenshots with short
crossfades. 13 chapters: create vault, unlock and lockout, home, search and filters, add a
login with the generator, copy and reveal, add a card, card details, edit/favourite/delete,
settings, backups, privacy, dark mode. Screens the preview doesn't have (create vault,
fingerprint prompt, generator, settings, dialogs) are drawn as mock-ups with the same CSS.
The music is a quiet lo-fi chord loop synthesised in the script, so there is no licensing
question.

**Why:** FLAG_SECURE (D-011) makes screen recordings of the real app black, and the emulator
is too slow for smooth footage. The preview gives clean, repeatable frames.

**Limits:** it shows the preview, not the APK, so it can drift from the app like the preview
itself. Mock-up screens show the documented behaviour (USER_WORKFLOW.md), not pixel copies of
the Kotlin screens. Re-run the script after UI changes.

**Alternatives:** screen-recording the emulator (black under FLAG_SECURE, and slow);
Playwright's built-in video capture (real-time, lower quality, no clean caption timing).

