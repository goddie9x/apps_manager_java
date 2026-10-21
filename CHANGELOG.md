# Changelog

All notable changes to Apps Manager. Format follows Keep a Changelog; the app
version follows semantic versioning.

## [1.1.0] - 2026-10-21

### Fixed
- Unit test project now compiles and passes (broken package line).
- Icons/labels no longer go stale when scrolling the app list (recycled rows now
  reset every label), and tapping during list updates can no longer crash.
- Accessibility freeze flow: fixed a guaranteed crash when processing the
  app-info screen and a word-count bug that misread its buttons.
- Notification blocking now honours the master disable switch consistently,
  including for already-posted notifications.
- Root actions report their real result instead of always claiming success;
  root detection no longer blocks the UI at startup or leaks a `su` process.
- Back-stack handling when switching groups no longer drops unrelated entries.
- The periodic freeze job is actually scheduled again (it was blocked by an
  inverted flag) and now honours the JobService contract.
- Long-press launcher shortcut creation works again through the legacy
  shortcut flow.

### Changed
- Storage moved from the abandoned Sugar ORM to Room. Existing freeze /
  notification lists and settings are imported automatically on first launch
  after upgrading (the old database is kept as a `.bak` file).
- Dependencies updated (AppCompat 1.7.1, Material 1.12.0, RecyclerView 1.4.0,
  ConstraintLayout 2.2.1); versions are centralised in `gradle/libs.versions.toml`.
- Release signing can be provided through environment variables for CI.

### Removed
- Nine unused or nonexistent permissions (including legacy storage permissions);
  Sugar's SQL query logging is gone with the ORM.
- Dead template code and resources (navigation graphs, preference template,
  unused dependencies); `.idea`/`.gradle` are no longer tracked in git.
- The two launcher-shortcut activities are no longer exported to other apps.

## [1.0.2.0] - earlier

- Target Android 15 (API 35), then Android 16 (API 36); permission flow refresh.
