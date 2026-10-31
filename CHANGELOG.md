# Changelog

## 0.5.6 - Strava joins the bundle

**New app: Strava (com.strava) 480.15 / 485.12 / 486.9 / 486.11**

- Unlock Premium: forces the SubscriptionDetail constructor (premium, expiry
  2099, PAID, full product-access bitfield) so every client premium gate opens.
- Remove upsells: renderer gates for the Data Peek carousel, Maps "Explore
  anywhere" card, "Time to flex" replay card, "Your predictions are ready",
  "See how far you've come" and subscriber preview banners - each with a
  runtime toggle in Extra Settings (module dispatcher + 4 converters +
  performance-predictions composable, per-type defaults baked in smali).
- Remove telemetry: Sentry, Embrace, Facebook AppEvents, GA4
  (logEventInternal), first-party Room analytics DAO, Mapbox telemetry;
  GAID hard-emptied at AdvertisingIdClient; push (FCM) untouched.
- Extra Settings screen: native-looking screen in Settings with upsell
  hide-switches (live, no restart), accent display, recording touch-lock and
  patch info.
- AMOLED dark theme: dark surfaces remapped to a true-black ladder
  (48 entries).
- Custom app name: optional launcher-label rewrite (stravaAppName option).
- Recording touch lock: full-screen shield over the recording screen with a
  "tap to unlock" pill - stray taps no longer flip map/stat views or open
  sheets (on by default, toggle in Extra Settings).
- Signature spoof + original certificate re-enabled for Strava.

All notable changes to the Pdb & MyDialog Morphe patch bundle. The Manager reads
this file to show per-app update hints and the in-app changelog.

## [0.5.6](https://github.com/madushas/morphe-patches-custom/compare/v0.5.5...v0.5.6) (2026-10-28)

### 🐛 Bug Fixes

* **Pdb - Soul history:** injected screens now follow the app's dark mode (the in-app appearance setting is read from MMKV; Extras + Soul history use a mode-aware palette)
* **Pdb - Soul history:** thought-bubble and other non-Soul users no longer pollute the local history (only the learned Soul card class is recorded; likes always)
* **Pdb - Soul history:** timestamps are real again (30-minute upsert dedupe instead of per-render records)
* **Pdb - Chat:** deep links carry the required host — `pdb://pdb.app/chat/{type}:{id}` and `/u/{id}` (host-less forms hit the router's blank fallback)

### ✨ New Features

* **Pdb:** native Soul history screen — every Soul profile you saw or liked, newest first, with per-entry Chat (opens your existing conversation; channels are server-created, so no channel = profile fallback)
* **Pdb:** "Chat" entry in the profile screen's ⋯ menu (the stock app has no chat CTA on profiles)
* **Pdb:** Extras power row opens the native history screen

## [0.5.5](https://github.com/madushas/morphe-patches-custom/compare/v0.5.4...v0.5.5) (2026-10-27)

### ✨ New Features

* **Pdb:** Soul history — a local record of every Soul profile you saw or liked (server keeps only a short window; likes beyond it are still recorded)
* **Pdb:** "Chat directly" power action for the last viewed profile

### 🐛 Bug Fixes

* **Pdb:** chat deep link colon format (router splits `type:id`), 30-minute upsert dedupe for seen entries, native-styled history cards
* **Pdb:** deep links need a host — `pdb://pdb.app/chat/{type}:{id}` and `/u/{id}` (verified live on device)

## [0.5.4](https://github.com/madushas/morphe-patches-custom/compare/v0.5.3...v0.5.4) (2026-10-26)

### ✨ New Features

* **MyDialog:** remove the AI assistant FAB entirely (server-driven `fabItems` resolves to nothing)
* **MyDialog:** the menu footer now shows the patch release id next to the device id

## [0.5.3](https://github.com/madushas/morphe-patches-custom/compare/v0.5.2...v0.5.3) (2026-10-25)

### ✨ New Features

* **MyDialog:** unlock hidden feature flags (`showO2A`, `showO2AShop`, `showO2ABuy`, `showUber`, `showDeleteAccount`) via a native response rewrite

## [0.5.2](https://github.com/madushas/morphe-patches-custom/compare/v0.5.1...v0.5.2) (2026-10-25)

### ✨ New Features

* **MyDialog:** kill residual beacon traffic (Facebook event queue + config fetch, CleverTap domain hijack)

## [0.5.1](https://github.com/madushas/morphe-patches-custom/compare/v0.5.0...v0.5.1) (2026-10-24)

### 🐛 Bug Fixes

* **MyDialog:** the server integrity gate passed — the spoofed signing-cert hash now returns `success:true` ("NO IMSI" fixed on re-signed builds)

### ✨ New Features

* **MyDialog:** "Spoof stock signing cert hash" patch (setAppToken resolves the stock constant)
