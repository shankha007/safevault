# SafeVault

An offline password manager for Android. It keeps website logins and bank cards in an
encrypted vault on your phone. The app has no internet permission, so nothing it stores can
leave the device.

**Version 1.2.1** · Android 8.0+ (API 26), targets Android 16 (API 36) · Kotlin + Jetpack Compose

---

## Features

- **One master password.** It's stretched with Argon2id, and the vault is encrypted with
  AES-256-GCM. Nobody can recover a forgotten master password.
- **Fingerprint unlock** through a key in the Android Keystore.
- **Logins:** name, username, password, website, encrypted notes, favourites.
- **Bank cards:** debit or credit. The network (Visa, Mastercard, RuPay, …) is detected as
  you type, a Luhn check catches typos in the number, and the expiry formats itself as MM/YY.
  CVV and PIN are optional and need a second fingerprint check before they're shown.
- **Password generator:** 8 to 64 characters, with a choice of character sets and a
  strength meter.
- **Home screen:** a branded header with item counts, search (including a card's last 4
  digits), filters, and real logos for 80+ common apps, banks and card networks. The logos
  are bundled in the app, never downloaded.
- **Encrypted backups:** export a `.svault` file, then restore it on a new phone or merge it
  into an existing vault.
- **Protection:**
  - Auto-lock when you leave the app, and after 5 minutes without a touch.
  - Copied passwords are cleared from the clipboard after 30 seconds.
  - Screenshots and screen recording are blocked.
  - Each wrong-password streak means a longer wait, and the vault can optionally be erased
    after 10 wrong tries.
  - A warning appears on rooted phones.
- **Dark mode** follows the phone's theme.

## Install

1. Copy `SafeVault-<version>.apk` to the phone and open it from the **Files** app.
2. Allow installs from that app when Android asks, then tap **Install**.
3. To update, install the new APK over the old one. Your vault is kept. Don't uninstall
   first, because uninstalling deletes the vault.

Full step-by-step use is in [docs/USER_WORKFLOW.md](docs/USER_WORKFLOW.md).

## Build

You need JDK 17 and the Android SDK (platform 36, build-tools 36).

```bash
./gradlew testDebugUnitTest      # unit tests
./gradlew assembleDebug          # debug APK (app id com.safevault.app.debug)
./gradlew assembleRelease        # signed, shrunk release APK
./gradlew bundleRelease          # signed release AAB for Google Play
```

The release build is signed only when `signing/keystore.properties` exists. That folder is
not in git. Updates must be signed with the same key, or Android refuses to install them.

## Project layout

```
app/        Android app (crypto/, data/, security/, util/, ui/)
docs/       CODEBASE_GUIDE.md, USER_WORKFLOW.md, DECISIONS.md
tools/      gen_brand_icons.py: regenerates the bundled brand logos
```

These folders stay local and are git-ignored: `release/` (APKs), `demos/` (product demo
videos), `desktop-preview/` (a temporary HTML copy of the UI for testing on a PC) and
`signing/` (release keystore).

## Documentation

- [Codebase guide](docs/CODEBASE_GUIDE.md): packages, key hierarchy, file format, building
  and testing, test log.
- [User workflow](docs/USER_WORKFLOW.md): how the app is used, screen by screen.
- [Decision log](docs/DECISIONS.md): every technical decision and the reasons for it.
- [Play Store guide](docs/PLAY_STORE.md): listing text and Play Console answers.
- [Privacy policy](docs/PRIVACY_POLICY.md).

## Licences

Brand logos come from [Simple Icons](https://simpleicons.org) 16.34.0 (CC0). Brand names
and logos belong to their owners and are used only to identify the user's own saved items.
