# Pdb patch — working log

Persistent notes for whoever (human or agent) picks this up next. Keep entries short,
factual, and append-only. `SESSION-LOG.md` is a symlink to this file.

**Latest: 0.3.6 (2026-10-20)** — signed-in evaluation complete; see
`docs/EVAL-0.3.6.md`. Settings row and extras header show v0.3.6; the extras
activity is no longer exported. Login survived the upgrade.

## Environment (verified 2026-10-20)

- Repo: `/home/madusha/dev/sandbox/pdb_patch`
- Emulator: `emulator-5554`, x86_64, Android 14, **ARM translation available**
- Pdb base: `pdb_original.apk` (2.134.6, versionCode 2694, ARM64-only assets)
- Patch build output: `pdb_patched.apk`
- Emulator install APK: `work/device/install.apk` (adds `lib/arm64-v8a/libmmkv.so`
  so `primaryCpuAbi=arm64-v8a`, then signs with `~/.morphe-data/pdb-test.keystore`)
- Build + install:
  - `./tools/pdbbuild` — Gradle build + Morphe patch (does **not** add the ARM stub)
  - `./tools/pdbdevice` — adds ARM stub, signs, uninstalls, installs, launches
  - `./tools/pdbbuild --install` is **wrong for this emulator**: it installs the raw
    ARM-asset-only APK and crashes with `UnsatisfiedLinkError` for `libmmkv_64.so`.
- Version shown in the app: **Settings → Pdb Extras** header and status line
  (`Patch v0.3.1`).

## Data facts (important; saves hours)

- App database: `/data/data/pdb.app/databases/pdb_app_go.db`
  - Tables seen: `profile_view_record` (and Room metadata).
  - **No `users` table**, **no `messages` table**.
- There is a second database, Firebase's `google_app_measurement_local.db`, which
  *does* have a `messages` table. It is analytics, not Pdb chat data. The 0.3.0
  tools accidentally queried it; 0.3.1 excludes any DB whose name starts with
  `google_app_measurement`.
- MMKV: `files/mmkv/pdb`, ID `pdb.app`'s default. Preferences live in
  `shared_prefs/pdb.app_preferences.xml`.
- Client-side premium flag works: the app's own Tweaks screen shows
  **Mock you as pro user = ON** after startup, and the premium-gated
  "Who?" visitor reveal opens the visitor profile.
- Ads are off: startup log shows `adOff=true`; ad SDK providers are disabled.

## 0.3.0 on-device evaluation (before 0.3.1)

Working:
- Settings → Pdb Extras opens; version header shows `v0.3.0`.
- Profile visitors hidden screen opens; "Who?" reveals the visitor (premium gate).
- User center hidden screen opens.
- Settings shortcut/app icon paths: app opens correctly after install.
- Extras long-press popups open and run actions.
- Hidden screen launcher items that need only an Activity open and work
  (Coins engage, Balance, Community info, Verify, Story editor, Profile settings,
  PDB page manager, Instant chat history, Community explore, Boards explore).

Broken / noisy (fixed or hidden in 0.3.1):
- Long-press power menu had a transparent/see-through background; rows floated on
  the app. Fixed by using `PdbUi.card()` (opaque `content_bg_r8`) for the popup
  shell.
- Scrolling the Extras screen put rows under the status bar/clock. Fixed by
  padding the **root FrameLayout** with system-bar insets, not the scrolling
  content.
- "User inspector", "Follows", "Personality", "Subconscious", and "User database"
  either immediately finish (need a user/route context) or are dead on this build.
  They were removed from the hidden-screen list; the list now has 12 items.
- SQLite tools were querying the Firebase measurement DB's `messages` table and
  then joining a non-existent `users` table. 0.3.1 returns a clear
  "no local SQLite message table" message instead, and visitor queries no longer
  join `users`.
- Visitor log/digest now resolve usernames defensively (null-safe), so they do not
  fail when there is no `users` table.
- Added a "Dump database schema (CREATE TABLE SQL)" row and a multi-store
  "Dump MMKV store" (lists every file under `files/mmkv` and its keys).
- Summary/status line shortened so it fits one line on a 1080-wide screen.

Still open:
- `Open profile of entered id` with the **own** numeric id opens
  `ProfileDetailActivity` but shows "Oops! Something went wrong". Opening another
  user's profile from the visitor page works, so this may be an app limitation for
  self-profile (own profile uses `UserCenterActivity`), not a patch bug.
- Direct chat / instant chat was not exercised end-to-end (would create a real
  conversation with a stranger).
- Remaining toggles (force English, text overrides, fake logout, device-id changes)
  were not exhaustively re-tested after 0.3.1.
- Profile/direct-chat numeric fallback is implemented but not tested with a second
  real numeric id.

## 0.3.1 changes (source)

- `PdbExtrasActivity`: root `FrameLayout` + insets padding; shorter status; 12
  hidden screens; schema row; multi-store MMKV dump; clearer labels.
- `PdbMenu`: opaque card background, still scrollable and capped at 65% height.
- `PdbDatabase`: exclude Firebase DB; `schema()`; null-safe message/visitor
  queries; clear messages when `users`/`messages` are absent.
- `PdbMmkv`: `dumpAll(Context)` + reusable `open(id)`; `resolve()` uses `open()`.
- `PdbTools`: `mmkvDump(Context)`.
- `gradle.properties` and `patches-bundle.json`: 0.3.1.

## Install/update signature note (learned the hard way)

Stock Pdb and patched builds have different signing keys. `adb install -r` cannot
cross that boundary (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`). Updating from stock to
patched requires uninstalling, which **wipes app data/login**. `pdbdevice` also
uninstalls by design. If you need to preserve a logged-in session:

1. Install the patched build first, log in, and keep using patched builds signed by
   `~/.morphe-data/pdb-test.keystore`.
2. Do not temporarily restore the stock APK on the same emulator unless you are
   prepared to log in again.
3. If you must compare with stock, use a second emulator/AVD or a cloned user.

## Current blocker (2026-10-20)

After rebuilding 0.3.1, the emulator showed the login/onboarding screen because the
stock↔patched uninstall wiped the session. The 0.3.1 APK is installed and starts
(`primaryCpuAbi=arm64-v8a`, no crash). Waiting for the account owner to sign in
before continuing on-device checks of 0.3.1.

Commands after login:

```bash
export PATH="$HOME/tools/android-sdk/platform-tools:$PATH"
adb shell dumpsys activity activities | grep -m1 topResumedActivity
adb exec-out screencap -p > work/eval/after-login.png
```

Then open **Settings → Pdb Extras** and verify:
- header/status shows `Patch v0.3.1`
- popup background is opaque
- rows no longer slide under the status bar
- Database schema dump shows `pdb_app_go.db` / `profile_view_record`
- MMKV dump lists `files/mmkv/pdb` and keys (not "0 keys" only)
- Recent chats / Chat stats return the clear "not in SQLite" message, not a
  Firebase error
- Visitor digest / Profile view log return rows or a clean empty message

## Update 2026-10-20 — 0.3.2 … 0.3.5

### What changed

- **0.3.2** — defaults on the switches now mirror `PdbStartup` (read receipts,
  "see more", stay-signed-in default ON). MMKV dump switched from `getAll()`
  (unsupported) to key enumeration; per-account `pdb_<uid>` stores are scanned.
  DB tools now use the real `pdb_app_go.db` schema:
  `channel_info` (cid, streak_id, back_prompt_time, request_reply_tracked,
  request_reply_any_tracked, draft_text), `profile_feed`, `metric`,
  `profile_view_record`. Added a generic **Browse table** inspector and
  **Profile feed (local cache)**; `Recent chats` / `Search chats` fall back to
  `channel_info`; `Chat statistics` reports channel/draft/streak counts;
  `Streak radar` and `Unsent drafts` read `channel_info`.
- **0.3.3** — `PdbExtrasActivity` is temporarily `android:exported="true"` so
  `adb shell am start -n pdb.app/app.template.extension.pdb.PdbExtrasActivity`
  opens the screen while signed out. **Set back to false before a release.**
- **0.3.4/0.3.5** — MMKV's public `allKeys()` is stripped by R8 in this build.
  Enumerate keys by invoking the surviving private JNI entry point
  `allKeys(long, boolean)` with the `nativeHandle` field (0.3.5). Verified:
  `Dump MMKV store` lists 6 keys in `pdb`, including `XDeviceId` after
  `Randomize device id`.

### Tooling fix

- `tools/pdbdevice` used to **uninstall the package on every install**, wiping the
  signed-in account each build. It now installs with `-r` (upgrade in place) and
  only falls back to uninstall on a real signature mismatch.

### Verified on the emulator (0.3.5, signed out)

- Header/status: `Pdb Extras v0.3.5`, `Patch v0.3.5 · Pro + ads unlocked · Paused OFF`.
- Insets: after scrolling, rows stop below the status bar (first row y=170 on a
  144px status bar).
- Hidden-screens popup: opaque background, 12 entries, all live.
- Toggles: `Force English` ON persisted (`english=true`) across a force-stop,
  OFF persisted (`english=false`); defaults-true switches now show checked.
- Text override: `US` in feed country persisted as `country='US'`; cleared back
  to `country=-`.
- `Dump MMKV store`: directory + 6 keys, including `XDeviceId`, `auth_session_v1`,
  `enableNativeAd`, `global_time_offset`, `lastCMSTime`, `FirebaseFCMToken`.
- `Browse table` / `Recent chats` / `Chat statistics` / `Streak radar` /
  `Unsent drafts`: clean empty-state messages when the app DB does not exist yet.
- DB schema dump (0.3.1, signed in) showed the real tables above.

### Login blocker (important)

The rebuild after the last login had to uninstall (old `pdbdevice`), so the app is
signed out. **Google sign-in now fails** with:

```
This android application is not registered to use OAuth2.0 ... package name and
SHA-1 certificate fingerprint match ...
```

Reason: the patched APK is re-signed with `~/.morphe-data/pdb-test.keystore`, whose
SHA-1 is not registered in the Pdb Google Cloud OAuth client. Earlier login used
the **email verification** path (`More` → `Continue with email verification`), not
Google. To restore a session either:

1. Sign in with **Continue with email verification** (needs the account inbox), or
2. Add the pdb-test keystore SHA-1 to the OAuth client in Google Cloud.

### Next

- Log in via email verification.
- Re-test `channel_info` tools with real rows (Browse table → `channel_info`).
- Re-check premium/ads and in-app hooks after login.
- Before release: set the extras activity back to `exported="false"`, bump to
  0.3.6, rebuild, and keep `pdbdevice` upgrade-in-place.

## 0.3.6 — release build (2026-10-20)

- `PdbMmkv.value()`: treat MMKV's `""` answer for non-string keys as "not a
  string" and keep probing, so booleans/ints report their real value.
- `FeatureMenuPatch`: extras activity is `android:exported="false"` again. It is
  opened only from the app's Settings row; `adb shell am start` is denied.
- `gradle.properties` / `patches-bundle.json`: **0.3.6**.
- Built, installed with the fixed upgrade-in-place `pdbdevice`; **login
  survived** (`HomeActivity`, id `6398658`).
- Verified: Settings row and extras header both show v0.3.6; DB stats show
  `pdb_app_go.db` with `profile_feed: 46`, `profile_view_record: 2`,
  `channel_info: 0`; Browse table + Profile feed render real rows; MMKV dump
  shows 41 keys including `XDeviceId` / `access_token`; Tweaks still has
  `Mock you as pro user` ON and `debug AD` OFF; home ad container is empty.
- Full matrix: `docs/EVAL-0.3.6.md`.
