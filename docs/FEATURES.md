# Feature guide

Every feature below states what it does, what input it takes and how that input
is validated, and what must exist first (prerequisites), with the in-app place
that creates or grants the missing piece. Permission states and grants live in
the **Permission Center** (drawer → Permission Center); features degrade
honestly when a prerequisite is missing instead of failing silently.

Privilege backends are picked automatically, strongest first:
**Device Owner > Root > Shizuku > Non-root** (see the privilege matrix in the
README).

## App detail (F1)

- **What:** per-app screen with version name/code, install/update dates, APK
  size, target/min SDK, real enabled/stopped state, storage and usage data,
  permission list, and every action below.
- **Input:** a package name from the app list (long-press → App details).
- **Validation:** the package must still be installed; a stale entry closes
  the screen with a toast instead of crashing.
- **Prerequisites:** none for the basic data. Storage/usage sections appear
  only with Usage Access (Permission Center → Usage access).

## APK export (F2) and pre-uninstall backup (F18)

- **What:** copies the installed app's original APK to a user-picked location;
  the uninstall flow offers "Export APK first" before removing anything.
- **Input:** destination document picked in the system file picker.
- **Validation:** the source APK path must exist at export time; copy failures
  are reported, never shown as success.
- **Prerequisites:** none (Storage Access Framework; no storage permission).

## Storage & usage figures (F3, F4)

- **What:** app/data/cache sizes (StorageStatsManager), last-used time,
  7-day foreground time and launch counts (UsageStatsManager); also powers
  sorting by size / last used and the "unused apps" logic.
- **Input:** none.
- **Validation:** figures are shown only when the platform returns them;
  missing access leaves the values "unknown", never guessed.
- **Prerequisites:** Usage Access (Permission Center → Usage access). If it is
  missing, create it there: the button opens the system's Usage Access screen.

## Search & sorting (F5, F6)

- **What:** diacritics-insensitive search ("dong bang" finds "Đóng băng"),
  sorting by name, package, install date, update date, size, last used.
- **Input:** free text; a sort key + direction from the toolbar menu.
- **Validation:** empty query restores the full list; stale async results are
  dropped when a newer query exists.
- **Prerequisites:** size/last-used sorting needs Usage Access (above).

## Rule export / import (F7)

- **What:** exports the freeze list, notification block list and settings to a
  versioned JSON file; import applies the same format on another device.
- **Input:** a JSON file picked in the system file picker.
- **Validation:** the file must carry `schema: 1`; every rule line's package
  name must match the Android package format. Invalid lines are rejected
  individually and reported (applied / rejected / not-installed counts) — one
  bad line never fails the whole import. Numeric settings are clamped
  (screen-off delay 0–600, unused threshold 1–3650).
- **Prerequisites:** none.

## Activity history (F8)

- **What:** in-app log of freeze/unfreeze/shell/uninstall actions with time,
  backend and success flag. Drawer → Activity history; can be cleared there.
- **Input:** none. **Validation:** entries are written fire-and-forget; a
  logging failure never blocks the recorded action.
- **Prerequisites:** none.

## Widget (F9) and Quick Settings tiles (F27)

- **What:** home-screen widget with live frozen/blocked counts and one-tap
  freeze-all; QS tiles for freeze-all and for toggling notification blocking.
- **Input:** a tap. **Validation:** freeze-all goes through the same durable
  queue as the shortcut; nothing runs inline.
- **Prerequisites:** add the widget / tiles from the system widget picker /
  QS edit screen. If they are missing, create them there — the app cannot add
  them for you.

## Blocked-notification archive (F10)

- **What:** notifications the listener cancels are kept (title, app, time) in
  Drawer → Blocked notifications, capped at 200 entries.
- **Input:** none. **Validation:** only notifications actually cancelled are
  archived.
- **Prerequisites:** Notification access (Permission Center → Notification
  access) and the archive switch in settings (on by default). If the switch
  is off, create the archive again by turning it back on in the exported
  settings or on the next install; blocked notifications then resume being
  recorded.

## Freeze profiles (F11) and focus sessions (F14)

- **What:** a profile is a named package set created from the current freeze
  list; applying it enqueues the set for freezing. A focus session freezes the
  freeze list for 15–120 minutes and restores it automatically afterwards.
- **Input:** profile name (non-empty after trim); focus duration from the
  fixed list.
- **Validation:** an empty freeze list blocks profile creation/focus start
  with an explanation; the restore work is unique ("focus-session"), so
  starting a new session replaces the previous pending restore.
- **Prerequisites:** at least one privileged backend for the freeze itself
  (root/Shizuku/Device Owner) or the accessibility freeze flow on non-root.
  Actual freezing quality follows the backend matrix in the README.

## Auto freeze on screen off (F12) and on low battery (F28)

- **What:** turning the screen off (or the battery going low) enqueues the
  freeze list; WorkManager drains it through the durable queue.
- **Input:** optional screen-off delay in settings, 0–600 seconds.
- **Validation:** the delay is clamped to 0–600 at import and at scheduling.
- **Prerequisites:** a non-empty freeze list; battery-optimisation exemption
  (Permission Center → Battery exemption) improves reliability on strict OEMs.

## Per-channel notification control (F13)

- **What:** from the detail screen, jump to the system's per-app notification
  screen where individual channels can be allowed/blocked.
- **Input:** none. **Validation:** —
- **Prerequisites:** none; channel switches themselves are owned by Android.

## Unused-app suggestions (F15)

- **What:** a weekly WorkManager check posts ONE suggestion notification
  listing apps unused longer than the threshold (default 60 days).
- **Input:** threshold in days (settings/import), clamped 1–3650.
- **Validation:** suggestions never act on their own; system apps and this
  app are excluded.
- **Prerequisites:** Usage Access and the POST_NOTIFICATIONS runtime
  permission; without either, the worker stays silent.

## Privileged detail actions (F19–F26)

- **Debloat / uninstall for user 0 + restore (F21):** `pm uninstall --user 0`
  and `cmd package install-existing`. Input: explicit confirmation dialog.
  Validation: a hard-coded critical set (system UI, phone, settings, package
  installer, Play Store, Android core, this app) is always refused.
  Prerequisites: Root or Shizuku.
- **Clear cache (F22):** root removes the app's cache dirs; Shizuku falls
  back to a global `pm trim-caches` (its sandbox cannot enter other apps'
  data). Prerequisites: Root (per-app) or Shizuku (global).
- **AppOps background restriction (F23):** sets `RUN_ANY_IN_BACKGROUND` to
  ignore/allow. Prerequisites: Root or Shizuku.
- **Standby bucket (F24):** `am set-standby-bucket` with the five platform
  buckets. Prerequisites: Root or Shizuku.
- **Component manager (F25):** lists activities/services/receivers/providers
  and toggles them with `pm enable/disable <component>`. Validation: this
  app's own components are never togglable. Prerequisites: Root for
  toggling; listing is read-only without it.
- **Technical info (F26):** `dumpsys meminfo` total PSS and wakelock
  mentions. Read-only. Prerequisites: Root or Shizuku.

## Rescue (F29)

- **What:** Drawer → Rescue unfreezes every app in the freeze list and clears
  the pending freeze queue. Rules are kept.
- **Input:** confirmation dialog. **Validation:** —
- **Prerequisites:** a privileged backend strengthens it; without one it
  still clears the queue and reports, and the Permission Center explains how
  to regain a backend.

## Background health report (F30)

- **What:** shareable weekly summary: rule counts, top foreground apps,
  stale apps, sampled cache total. Every figure names its source
  (UsageStats/StorageStats) and battery impact is never claimed, because
  Android does not expose per-app battery use to normal apps.
- **Input:** none. **Prerequisites:** Usage Access for the usage sections.

## Device Owner provisioning (for the Device Owner backend)

The app never asks for device-admin activation in the UI. To provision:

```
adb shell dpm set-device-owner com.god.ApplicationManager/.admin.AppAdminReceiver
```

- **Prerequisites:** no accounts on the device (Android requirement for
  `set-device-owner`); remove accounts first if the command refuses. If the
  component is missing, reinstall the app — the receiver is declared in the
  manifest.
