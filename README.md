# Apps Manager

An Android app for taming background apps: freeze apps you rarely use, stop them
from running, and silence their notifications — in bulk, or automatically when the
screen turns off. Works without root (via Accessibility / shortcut tricks); with
root it can do the same jobs directly and more reliably.

- Package: `com.god.ApplicationManager`
- Min Android: 7.0 (API 24) · Target: Android 16 (API 36)
- Language: Java (migrating to Kotlin in slices) · Storage: Room (SQLite)

## Features

### Freeze / unfreeze apps

**Overview.** Marks apps in a freeze list so they can be frozen (disabled) or
stopped in one action — from the app, from a home-screen shortcut, or automatically
when the screen turns off.

**Prerequisites** — one of these mechanisms must be available, otherwise the
freeze action only opens the system app-info screen:

| Mechanism | How to enable | Where |
| --- | --- | --- |
| Accessibility flow (no root) | Turn on the app's accessibility service | [Granting permissions](#granting-permissions) |
| Root | A rooted device with a superuser manager (Magisk, etc.) | Granted by your root manager when the app asks |

**Input validation.** The app-list confirmation dialog warns before a *system* app
is added to the freeze list (the warning can be muted in the list menu). Package
names are validated against the Android package-name format before they are ever
used in a root shell command.

### Auto-freeze when the screen turns off

**Overview.** A scheduled job freezes the whole freeze list shortly after the
display goes off, driven by the `Freeze when screen off` entry in the navigation
drawer.

**Prerequisites.** The freeze list must be non-empty (create it via
[Freeze / unfreeze apps](#freeze--unfreeze-apps)), the app must be excluded from
battery optimisations (Android Settings → Apps → Apps Manager → Battery →
Unrestricted), and one freeze mechanism above must be available. If the screen is
still locked when the job runs, the freeze is aborted instead of fighting the
keyguard.

### Block notifications per app

**Overview.** Marks apps whose notifications should be cancelled as they arrive,
without changing the apps' own notification settings.

**Prerequisites.** Notification access must be granted to *Notification listener*
(see [Granting permissions](#granting-permissions)). If access is missing, the
app takes you to the system screen where you can create/grant it — the feature
simply stays inactive until then. The master switch *Disable auto turn off
notification* in the drawer pauses the whole feature without changing the list.

### Security center — strange / junk / malicious apps

**Overview.** A dedicated drawer menu that scans every installed app and
groups the results into Dangerous, Suspicious and Junk with a 0–100 risk
score. Each row offers quick actions: app details, uninstall, freeze, flag as
junk/dangerous, or whitelist. Separate tabs list apps holding sensitive
permissions, manage the threat-intel sources, and show your hand-set flags.

**How it detects.** All offline, no root needed: known-bad package names and
signing-cert SHA-1s (a renamed malware APK is still caught), installer
provenance (sideload vs known stores), signature spoofing, hidden apps (no
launcher icon), dangerous/sensitive permissions, *granted* sensitive access
(overlay, install-packages, usage stats, accessibility, notification listener,
device admin), debuggable/test-only builds, ancient target SDK, never launched.

**Intel sources (manual > online > bundled).** A bundled offline copy
(`tools/build_threat_intel.py` regenerates it from the public
AssoEchap/stalkerware-indicators repo); online feeds over HTTPS refreshed
daily (custom JSON or ioc.yaml feeds can be added); a hand-entered list with
JSON import/export.

**Prerequisites** — INTERNET for feed refresh; Usage Access sharpens the
"never launched" signal; uninstall/freeze use the normal privilege backends
(root/Shizuku perform the real action, otherwise the system uninstall screen
opens). The app never flags itself.

**Input validation.** Package names are validated before any shell use; feed
URLs must be https and are size-capped; a feed cache is only replaced after a
clean parse; import lines are validated individually and reported as
applied/rejected/not-installed.

### Force stop & app info

**Overview.** One-tap force stop (root) or jump to the system app-info screen
where you can force stop / disable manually (no root).

**Prerequisites.** None without root. Force-stop-in-place requires root.

### Root extras

**Overview.** On rooted devices the app can uninstall for the current user,
disable (freeze) and force-stop apps directly through a superuser shell.

**Prerequisites.** Root, as above. If root is not available these entries fall
back to the system screens instead of failing silently.

## Granting permissions

Open these from inside the app (drawer / dialogs guide you), or directly:

- **Accessibility service:** Android Settings → Accessibility → Installed apps →
  *Freeze service* → On. The app only uses the service to press *Force stop /
  Disable* on the system app-info screen on your behalf.
- **Notification access:** Android Settings → Notifications → Device & app
  notifications → Notification access → *Apps Manager* → Allow. (Older versions:
  Settings → Apps → Special app access → Notification access.)
- **Usage access** (for running-state labels): Settings → Apps → Special app
  access → Usage access → *Apps Manager* → Allow.
- **Root:** no system screen — your superuser manager prompts the first time the
  app runs a privileged command.

If a permission is missing, the related feature is skipped with a dialog that
points back to the right screen — nothing fails silently.

## Build from source

Requirements: JDK 17, Android SDK with platform 36 and build-tools 36.

```bash
./gradlew assembleDebug          # debug APK in app/build/outputs/apk/debug/
./gradlew testDebugUnitTest      # unit tests
./gradlew lintDebug              # lint (0 errors expected)
```

CI (GitHub Actions, `.github/workflows/android.yml`) runs the same three tasks on
every push / pull request to `main` and uploads the debug APK as an artifact.

### Release builds & signing

Release builds are minified with R8 (`app/proguard-rules.pro`). Signing is read
from the environment first (CI), then from `local.properties` (never committed):

| Env var | local.properties key |
| --- | --- |
| `KEYSTORE_PATH` | `keystore.path` |
| `KEYSTORE_PASSWORD` | `keystore.password` |
| `KEY_ALIAS` | `key.alias` |
| `KEY_PASSWORD` | `key.password` |

```bash
./gradlew assembleRelease
```

## Data & upgrades

- Current storage is a Room database (`apps-manager.db`) with two tables:
  `app_info` (per-app freeze / notification rules, keyed by package name) and
  `settings` (single-row app settings).
- Upgrading from a pre-1.1.0 version: the first launch imports the old Sugar ORM
  database (`god.db.ApplicationManager`) into Room — freeze list, notification
  list and settings are preserved — and the old file is renamed to `.bak`.
  If the import fails for any reason the app starts with empty lists rather than
  crashing; the `.bak` file is kept so data can be recovered manually.

## Project layout

| Area | Package / file |
| --- | --- |
| App entry | `com.god.ApplicationManager.App` |
| Persistence | `...DB` (Room database, DAOs, legacy import) |
| Privileged actions | `...Facade.AppManagerFacade` (root shell now; Shizuku backend planned) |
| Services | `...Service` (accessibility freeze, notification listener, scheduler) |
| UI | `...UI`, `...Adapter`, layouts in `app/src/main/res/layout` |

Versions are managed centrally in `gradle/libs.versions.toml`. The app version
follows semver (`versionName`), with `versionCode` increasing monotonically.

## Changelog

See [CHANGELOG.md](CHANGELOG.md).

## License

MIT — see [LICENSE](LICENSE).
