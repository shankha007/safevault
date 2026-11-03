# SafeVault user workflow

How you'll use SafeVault day to day, screen by screen. This file is updated whenever a
feature is added or changed.

_Last updated: 2026-10-28 (Phase 1)_

---

## 1. Installing the app

1. Copy the newest APK from `release/` (now `SafeVault-1.2.0.apk`) to your phone (USB cable, Google Drive, or a pen drive).
2. Open it from the phone's **Files** app.
3. Android asks to allow installs from that app. Choose **Settings → Allow from this source**,
   then go back and tap **Install**.
4. Play Protect may say the app is unrecognised. That's because it isn't from the Play Store.
   Tap **More details → Install anyway**.
5. After installing, you can turn the "Allow from this source" permission off again.

**Want a quick tour first?** `demos/SafeVault-1.1.0-demo.mp4` is a 3-minute captioned video
of every feature below. Shorter clips, one per feature group, are in `demos/shorts/`.

**Updating from an older version:** install the new APK the same way, over the old one.
Android shows **Update**; your vault, settings and fingerprint unlock stay as they are. Don't
uninstall first: uninstalling deletes the vault on the phone (restore from a backup if you
did). Taking a fresh backup before updating is a good habit.

---

## 2. First launch: create your vault

```
Open SafeVault
   │
   ├── New user ──► Create your vault
   │                  • Type a master password (strength bar shows how strong it is)
   │                  • Type it again to confirm
   │                  • Tick "I understand nobody can recover my vault"
   │                  • Tap Create vault (takes about a second)
   │                        │
   │                        ▼
   │                  Your vault (empty)
   │                  • A card offers "Unlock with your fingerprint" → Turn on / Not now
   │
   └── Moving from another phone ──► Restore from a backup file
                      • Pick your .svault backup
                      • Enter the master password used when the backup was made
                      • The vault opens with all your items
```

**Choosing a master password:** use a short sentence you'll remember, for example four or
five unrelated words. The app needs at least 12 characters rated "Strong". A single word
with numbers or symbols added ("Mumbai@123") isn't accepted, because that's the first thing
password-cracking tools try. **If you forget it, nobody can recover your data.**

---

## 3. Unlocking

```
Open SafeVault ──► Vault locked
                     │
                     ├── Fingerprint on ──► fingerprint dialog opens automatically ──► Your vault
                     │                      (or tap "Use master password")
                     │
                     └── Type master password ──► Unlock ──► Your vault
```

- **Wrong password:** the first 4 mistakes have no penalty. After that you wait 30 s, then 1 min,
  2 min, and so on, up to 1 hour. Changing the phone's date doesn't shorten the wait, and
  restarting the phone during a wait starts it again from the beginning.
- Wrong master passwords typed anywhere in the app count: on the unlock screen, in "confirm
  it's you", in Settings and when changing the password.
- **Erase after 10 wrong passwords** (if you turned it on in Settings): the unlock screen
  shows how many attempts are left, and after the 10th the vault is erased.
- **Fingerprints changed on the phone:** fingerprint unlock turns itself off for safety. Unlock
  with your master password, then turn fingerprint unlock back on in Settings.

---

## 4. The vault home screen

- **Search box:** finds items by name, username, website, bank, or last 4 card digits.
- **Filters:** All · Favourites · Logins · Cards
- **List:** favourites appear first, then everything alphabetically. Cards show the bank and
  `•••• 1234`.
- **Logos:** well-known apps and sites show their own logo (Gmail, Netflix, Instagram,
  WhatsApp, Swiggy, Paytm, PhonePe, HDFC, ICICI, Axis and about 70 more). The app recognises
  them from the **Website** field first, then from the item's **Name**, so "My Gmail" or a
  website of `mail.google.com` both work. Cards show the bank's logo if known, otherwise
  Visa / Mastercard / Amex and so on. A few brands with no free logo (LinkedIn, Amazon,
  Microsoft/Outlook, Flipkart, Slack, Yahoo, ChatGPT, Canva) show their first letter in the
  brand colour. Anything else keeps a coloured letter. Logos are built into the app;
  nothing is downloaded.
- **Header:** the SafeVault logo and name on a blue band, with how many logins, cards and
  favourites you have. It stays at the top while the list scrolls.
- **Header buttons:** 🔑 password generator · 🔒 lock now · ⚙ settings
- **Add button** (bottom right): choose **Login** or **Card**.

---

## 5. Saving a website login

1. Tap **Add → Login**.
2. Fill in:
   - **Name** (for example, Gmail)
   - **Username or email**
   - **Password**: type it, or tap ✨ to open the generator and tap **Use this password**
   - **Website** (optional)
   - **Notes** (optional, encrypted)
   - **Favourite** switch
3. Tap **Save**. The new item opens.

## 6. Using a saved login

1. Tap the item.
2. **Copy username** (📋), then paste it into the website or app. Leaving SafeVault to paste
   locks the vault by default. That's expected: the copied value stays on the clipboard until
   it's cleared. If you'd rather not re-unlock each time, set **Auto-lock** to 30 seconds.
3. **Copy password** (📋). A message confirms it, and the clipboard is cleared automatically
   after 30 seconds (you can change this in Settings).
4. Tap 👁 to see the password on screen. It hides itself again after 30 seconds.

---

## 7. Saving a bank card

1. Tap **Add → Card**.
2. Choose **Debit** or **Credit**.
3. Fill in:
   - **Name** (for example, HDFC debit card)
   - **Bank**
   - **Card number**: the network (Visa, Mastercard, RuPay, …) is shown as you type, and
     a warning appears if the number looks mistyped
   - **Name on card**
   - **Expiry**: type the 4 digits (for example `1227` for December 2027); the slash is
     shown for you. A month from 2 to 9 can be typed as one digit: `529` becomes `05/29`.
     Backspace works across the slash.
   - **CVV** and **PIN**: both optional
   - **Notes** (optional)
4. Tap **Save**.

## 8. Viewing card details

- The card face shows the bank, the network and `•••• •••• •••• 1234`.
- **Card number:** tap 👁 to show it, or 📋 to copy it.
- **CVV and PIN:** tap 👁. You'll be asked for your **fingerprint** (or master password).
  One check covers 60 seconds. You can turn this extra check off in Settings, but keeping
  it on is recommended.

---

## 9. Editing, favouriting, deleting

From an item's page:
- ⭐ adds or removes it from favourites
- ✏ edits it. If you leave with unsaved changes, the app asks before discarding them.
  For a card, the CVV and PIN stay hidden until you tap **Show PIN and CVV to change them**
  and confirm it's you, just like on the card's page.
- 🗑 deletes it, after you confirm

---

## 10. Password generator

Open it from the 🔑 icon on the home screen, or from ✨ while editing a password.
- Length slider: 8 to 64 characters (default 20)
- Switches: capital letters, small letters, numbers, symbols, avoid look-alike characters
- ↻ makes another one, 📋 copies it
- From the editor, **Use this password** puts it straight into the password field

---

## 11. Settings

| Setting | What it does | Default |
|---|---|---|
| Unlock with fingerprint | Use your fingerprint instead of typing the master password | Off until you turn it on |
| Auto-lock | How soon the vault locks after you leave the app | Immediately |
| Clear copied passwords | When the clipboard is wiped after copying | 30 seconds |
| Confirm before showing PIN and CVV | Asks for your fingerprint again for card secrets | On |
| Erase after 10 wrong passwords | Wipes the vault if someone keeps guessing | Off |
| Change master password | Needs your current password. Turns fingerprint unlock off (turn it on again) | — |
| Export encrypted backup | Saves a `.svault` file wherever you choose | — |
| Import from backup | Merges items from a `.svault` file | — |
| Delete vault | Erases everything on this phone (asks for your master password) | — |

The vault also locks by itself after **5 minutes without a touch**, even with the app open.

**Some changes ask for your master password**, even though the vault is open: exporting a
backup, turning on fingerprint unlock, turning off the PIN/CVV check or auto-erase, and making
auto-lock slower. That way someone who picks up your unlocked phone can't weaken your vault
or copy it. Changes that make the vault safer don't ask.

---

## 12. Backups (please do this)

Your vault exists **only on this phone**. If the phone is lost, broken or reset, the vault
is gone unless you have a backup.

**Making a backup**
1. **Settings → Export encrypted backup**
2. Choose a location (Downloads, Google Drive, a USB drive…) and save the file.
3. Copy that file somewhere off the phone, such as your PC or a pen drive.

The backup is encrypted. It opens only with the master password you had **when you made it**.
If you change your master password later, make a new backup. Old backups still open with
the old password, but they only contain what was in the vault when they were made.

**Restoring on a new phone:** install SafeVault, then on the first screen tap **Restore from a
backup file**.

**Merging into an existing vault:** **Settings → Import from backup**. New items are added.
If the same item exists in both, the more recently changed copy is kept.

Suggested habit: export a new backup once a month, and after adding anything important.

---

## 13. What SafeVault protects against, and what it doesn't

**Protected**
- Someone picking up your unlocked phone: the vault locks as soon as you leave the app.
- Someone stealing your phone: everything is encrypted, and guessing is slowed down.
- Other apps: they can't read SafeVault's private files, and screenshots of SafeVault are
  blocked.
- Cloud leaks: nothing goes to Google backup, and the app has no internet access.
- Copy-paste leftovers: the clipboard is cleared automatically, even if Android closes the
  app before the timer runs.
- Your keyboard learning your data: SafeVault asks the keyboard not to remember anything you
  type in it (like Gboard's incognito mode).
- Apps drawing over SafeVault to trick your taps: taps through another app's overlay are
  ignored. If a screen-dimming app is on, SafeVault may ignore taps until you turn it off.

**Not fully protected**
- A rooted phone, or one with malware that has root access. The app warns you if the phone
  looks rooted.
- A weak or reused master password, if someone also copies the vault files off the phone.
- Someone watching over your shoulder while values are shown.
