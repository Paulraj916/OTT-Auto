# DriveLaunch

## Identity — read this first

- **App name: DriveLaunch.** Package: `com.shubhampatel.drivelaunch`. Created by Shubham Patel.
- This project began as a fork of the open-source "AABrowser" project (originally by a developer using the handle "kododake"). It has since been fully rebranded: package renamed, all UI/branding replaced, README rewritten, and every "aabrowser"/"kododake" reference deliberately removed from the codebase.
- **Never reintroduce "AABrowser," "AA Browser," or "kododake"** in code, comments, commit messages, generated docs, strings, or conversation — in this repo those are the *old* identity, not the current one. Do not "restore" them if you find them in old git history; that removal was intentional.
- Footer credit string used in-app: `"DriveLaunch — Created By Shubham Patel"`.
- Repo: https://github.com/shubhampatel3355/DriveLaunch
- Sponsor/contact link used in-app: https://github.com/shubhampatel3355

## What it is

A WebView-based Android Auto browser — a native automotive UI (not a phone browser stretched to fit) with a launcher-style start page of quick-access tiles (Maps, Spotify, YouTube, streaming apps, etc.), full tab management, bookmarks, and video playback support that stock Android Auto restricts. Designed for passenger/parked use — the app's own UI and README are explicit that it's not meant to be used while driving.

## Architecture

Kotlin, ViewBinding, Material3. No Compose (deliberately removed from the dependency graph — don't reintroduce it without a real reason). `minSdk 24` (Android 7.0+), `compileSdk`/`targetSdk 37`.

Feature areas are organized as manager classes wired together from `MainActivity.kt`, each taking a `binding: ActivityMainBinding` and a callbacks interface rather than reaching into Activity internals directly:
- `bookmarks/BookmarkManager.kt` — bookmark list, pin-to-start-page, home page
- `startpage/StartPageManager.kt` — start-page grid, background presets/custom image
- `startpage/SponsorsManager.kt` — sponsor card on the start page (donor-list rendering was removed as dead code; only the static card remains)
- `tabs/TabManager.kt` — tab lifecycle, active-tab tracking
- `settings/SettingsViews.kt` — entire Settings screen, built programmatically (not XML) via one large `createSettingsContent()` function with local factory helpers (`createStyledCard()`, `createSectionTitle()`, etc.). Known maintainability debt: this function is ~900 lines and hasn't been split up because several buttons get listeners wired later in the function referencing earlier locals — a real refactor would need to thread those through properly, not just extract sections blindly.
- `ui/BrowserUIManager.kt`, `ui/OverlayManager.kt`, `ui/MainActivitySetup.kt` — menu overlay, navigation chrome, click wiring
- `data/BrowserPreferences.kt` — SharedPreferences wrapper, single source of truth for persisted settings
- `data/SiteIconCache.kt` — favicon fetch/cache; disk and network I/O run on a background executor, never the main thread (this was a real bug fixed once already — don't reintroduce synchronous disk reads in adapter `bind()` calls)

## Design system

- `ui/GlassStyle.kt` — the *only* place glass/frosted-card styling constants live (`Tier.PRIMARY`/`NESTED`/`ACCENT` alpha values). Every card in the app should route through `GlassStyle.applyGlass(...)` rather than hand-rolling `ColorUtils.setAlphaComponent` calls again.
- `ui/DialogUtils.kt` — every `AlertDialog` in the app should be built via `DialogUtils.styledAlertDialog(context)`, not a raw `MaterialAlertDialogBuilder`, so dialogs stay visually consistent.
- Static "glass" color resources (`glass_dialog_background`, `glass_nested_background`, etc.) live in `values/colors.xml` and `values-night/colors.xml` — used for XML-only cards (RecyclerView item layouts) where a runtime Kotlin call isn't worth the cost.

## Build & run

```
JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-17.0.19.10-hotspot" ANDROID_HOME="$HOME/AppData/Local/Android/Sdk" ANDROID_SDK_ROOT="$HOME/AppData/Local/Android/Sdk" ./gradlew.bat assembleDebug
```
(The system `JAVA_HOME` env var is stale/broken on this machine — always override inline as above.)

- Debug/release APKs are renamed and copied to `app/build/renamedApks/{debug,release}/` by a custom Gradle task in `app/build.gradle.kts`.
- Release signing config reads `RELEASE_STORE_FILE`/`RELEASE_STORE_PASSWORD`/`RELEASE_KEY_ALIAS`/`RELEASE_KEY_PASSWORD` from `local.properties` (gitignored). The actual keystore lives at `../release.keystore` relative to the project root (i.e. outside this folder) — **not backed up anywhere else**; losing it breaks the ability to sign future updates under the same identity.
- Test device: emulator AVD `Medium_Phone_API_36.1`. A real device used for testing is an OPPO CPH2341 on Android 13 — this is why `minSdk` matters; it was previously set to 35 with no actual technical justification and broke installs on that phone.

## Repo conventions

- `.gitignore` excludes `.claude/` (local tooling config), `local.properties`, `*.keystore`/`*.jks`, and `*.apk` — release APKs are distributed as GitHub Release assets, not committed to the source tree.
