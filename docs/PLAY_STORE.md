# Publishing SafeVault on Google Play

Everything needed to fill in the Play Console for SafeVault: the build to upload, the store
listing text, and the answers to the policy forms. Decision: D-039.

## 1. The build

| | |
|---|---|
| File to upload | `app/build/outputs/bundle/release/app-release.aab` (built by `./gradlew bundleRelease`) |
| Package name | `com.safevault.app` (permanent once uploaded) |
| Version | 1.2.1 (versionCode 4) |
| Target / min SDK | 36 / 26 |
| Signing | Signed with `signing/release.jks`, which acts as the **upload key** under Play App Signing |
| 16 KB page size | OK. The only native library (`libandroidx.graphics.path.so`, from Compose) has 16 KB-aligned segments |

Every later upload needs a higher `versionCode`. Keep a copy of `signing/` (the `.jks` and
`keystore.properties`) somewhere safe outside this folder. If it is lost, uploads stop
until Google support resets the upload key.

## 2. Store listing

**App name** (max 30): `SafeVault: Offline Passwords`

**Short description** (max 80):

> Offline password & card vault. No internet permission. Your data never leaves.

**Full description** (max 4000):

> SafeVault keeps your website logins and bank cards in an encrypted vault on your phone, and only on your phone. The app has no internet permission at all, so nothing you store in it can be sent anywhere.
>
> ONE MASTER PASSWORD
> Your vault is encrypted with AES-256-GCM, using a key derived from your master password with Argon2id. The master password is never stored, and nobody (including the developer) can recover it.
>
> FINGERPRINT UNLOCK
> Unlock with your fingerprint through a key kept in the Android Keystore.
>
> LOGINS AND BANK CARDS
> • Save logins with username, password, website and encrypted notes
> • Save debit and credit cards. The network (Visa, Mastercard, RuPay and more) is detected as you type, and a Luhn check catches typos in the number
> • CVV and PIN are optional and need a second fingerprint check before they are shown
> • Search, filters, favourites, and logos for 80+ common apps and banks, bundled in the app and never downloaded
>
> PASSWORD GENERATOR
> Create strong passwords of 8 to 64 characters with a strength meter.
>
> BUILT TO STAY PRIVATE
> • Auto-locks when you leave the app and after 5 minutes of inactivity
> • Copied passwords are cleared from the clipboard after 30 seconds
> • Screenshots and screen recording are blocked
> • Wrong-password attempts trigger growing waits, with an optional wipe after 10 tries
> • Warning on rooted phones
> • No accounts, no ads, no analytics, no tracking
>
> ENCRYPTED BACKUPS
> Export an encrypted .svault file and restore it on a new phone, or merge it into an existing vault. Android cloud backup is turned off, so your vault is never copied anywhere you didn't choose.
>
> Dark mode follows your phone's theme.

**Category:** Tools. **Tags:** Password manager, Security.

**Contact email:** required, shown publicly on the listing.

**Privacy policy URL:**
`https://github.com/shankha007/safevault/blob/main/docs/PRIVACY_POLICY.md`
(only works if the repository is public; otherwise host `docs/PRIVACY_POLICY.md` anywhere
public). The contact email in it is filled in; use the same address on the listing.

### Graphics

| Asset | Size | Notes |
|---|---|---|
| App icon | 512 × 512 PNG, 32-bit | Export from the launcher icon |
| Feature graphic | 1024 × 500 JPG/PNG | Required |
| Phone screenshots | 2 to 8, 16:9 or 9:16, 320–3840 px per side | Use a demo vault with fake entries only |

SafeVault sets `FLAG_SECURE`, so phone screenshots and `adb screencap` come out black.
Screenshots must be taken another way (see D-039).

## 3. App content forms

| Form | Answer |
|---|---|
| Privacy policy | The URL above |
| Ads | No ads |
| App access | All functionality available without special access (no login; the user creates their own master password) |
| Content rating (IARC) | Category "Utility, productivity, communication or other"; answer No to every content question. Expected result: Everyone / PEGI 3 |
| Target audience | 18 and over (avoids the Families policy requirements) |
| News app | No |
| Data safety | "Does your app collect or share any of the required user data types?" → **No**. The app has no network access, so data stored on the device is not "collected" under Play's definition |
| Government app | No |
| Financial features | None (it stores card details for the user but makes no payments or loans) |
| Health | No |

## 4. Release track order

1. **Internal testing**: upload the AAB, add your own Google account as a tester, install
   from the Play link and check that unlock, add, copy and backup work.
2. **Closed testing**: required for personal developer accounts created after 13 Nov 2023.
   At least 12 testers must stay opted in for 14 days in a row before you can apply for
   production access.
3. **Production**: after Google approves production access. Review usually takes a few days.

Existing users who installed SafeVault from an APK can only move to the Play version if
Play App Signing uses the same key (choose "use my own key" and upload `release.jks` at
first release) or after uninstalling, which deletes the vault. Export a backup first.
