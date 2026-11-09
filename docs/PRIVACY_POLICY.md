# SafeVault privacy policy

_Last updated: 9 November 2026_

SafeVault is an offline password manager for Android. This policy explains what happens to
the information you put into it. The short version: everything stays on your phone, and the
developer never receives any of it.

## What SafeVault stores

SafeVault stores the items you choose to save: website logins (name, username, password,
website, notes) and bank cards (card details, and optionally CVV and PIN). It also keeps
its own settings, such as the auto-lock time.

All of this is kept in an encrypted vault in the app's private storage on your phone. The
vault is encrypted with AES-256-GCM using a key derived from your master password with
Argon2id. Your master password itself is never stored.

## What SafeVault collects or shares

Nothing. SafeVault:

- has no internet permission, so it cannot send data anywhere;
- contains no analytics, advertising, crash-reporting or tracking code;
- has no accounts, no sign-in and no cloud sync;
- does not access your contacts, location, photos, camera, microphone or other apps' data.

The developer has no way to see, recover or delete your vault.

## Fingerprint unlock

If you turn on fingerprint unlock, Android checks your fingerprint. SafeVault never sees
or stores fingerprint data. It only receives a yes/no answer from the Android system and
uses a key held in the Android Keystore.

## Backups

SafeVault turns off Android's automatic backup, so your vault is not copied to Google
Drive or any other cloud service.

You can export an encrypted `.svault` backup file yourself. The file goes wherever you
choose to save it (for example your phone's storage or a cloud folder you pick). It stays
encrypted with your master password. What happens to that file after you save it is up to
you and the place you saved it.

## Clipboard

When you copy a password or card number, it is placed on the Android clipboard and cleared
automatically after 30 seconds. While it is on the clipboard, other apps that read the
clipboard may be able to see it, as with any copied text.

## Deleting your data

Uninstalling SafeVault, or clearing its storage in Android settings, permanently deletes
the vault from the phone. If you enabled the option to erase the vault after 10 wrong
master-password attempts, the vault is also deleted when that happens. Backup files you
exported are not touched; delete them yourself if you no longer want them.

## Children

SafeVault is not directed at children and collects no data from anyone.

## Changes to this policy

If this policy changes, the new version will be published at this same address with a new
"last updated" date.

## Contact

Questions about this policy: shankhasdas07@gmail.com
