# Cabin Comfort for MG — notes for Claude

Native Android app: an easy alternative to MG's iSMART app for heating/cooling the car.
Owner: Christian (Denmark). Use Danish date/time format (24h, day-month-year). Keep answers short.

## Names and IDs
- App name: "Cabin Comfort for MG". Launcher label: "MG Cabin". Never "MG Manager" in user-facing text.
- applicationId `dk.rhea.cabincomfort`; code namespace stays `dk.Android.Cpmonster.mgmanager`.
- Source is public on GitHub (AcePalsgaard/cabin-comfort-mg) and in SVN. No secrets in code or this file.

## Requirements
- API: port of https://github.com/SAIC-iSmart-API/saic-python-client-ng (`saic/`).
- All API calls go through the background queue (`queue/CommandQueue.kt`). The API fails a lot: retry quietly, don't bother the user.
- On start, in the background only: poll car type, temperatures and battery.
- Profiles "Warm up" (kind COLD = heat the car) and "Chill" (kind WARM = cool it). Editable. Easy to toggle from the front page.
- The app suggests a profile from the outside temperature but never starts one without the user's tap.
- No car/model picker: detect model and features from the API and adapt.
- Guest/demo mode (`demo/DemoCar.kt`) must never send anything.
- Fixed brand colours (heat = orange #E8590C, cool = blue #2F6FEB), no dynamic wallpaper colour.

## Features built
- Home screen widgets (`widget/Widgets.kt`): Auto button (starts the profile that fits the outside temp, tap again to stop) and a two-profile widget. Both show the active profile.
- Faded car picture in the home hero card: our own drawn silhouette (`ui/car/CarArt.kt`, body style from model name), coloured from the API `colorName`. Colour override and "use my own photo" in Settings (`ui/car/CarPhoto.kt`).

## Legal / Play rules (decided — don't change without asking)
- No MG logo anywhere and no MG/SAIC official images (trademark/copyright, Play impersonation policy). Use "for MG" wording.
- Keep the "independent, not affiliated with MG Motor / SAIC Motor" disclaimer (About screen, Play listing).
- Privacy policy: https://api.rhea.dk/privacy-policy_mgmanager

## Build, test, release
- Android Studio on Windows; emulator "Med API 36". Check UI changes on the emulator (adb screencap) before calling them done.
- Unit tests: `.\gradlew testDebugUnitTest`
- Mock SAIC server for testing: `tools\mock-server\start_mock_server.bat` (debug builds show a "Test server" option; password `test`).
- `releaseTest` build type = release signed with the debug key, for local installs only. It is excluded from Play publishing (`playConfigs` in `app/build.gradle.kts`).
- Upload key: `keystore/` (not in SVN). Play service account key: `%USERPROFILE%\.keys\...json`.
- Release (Gradle Play Publisher):
  1. Bump `versionCode` and `versionName` in `app/build.gradle.kts`.
  2. Update release notes in `app/src/main/play/release-notes/{en-GB,da-DK}/{internal,alpha}.txt` (max 500 chars).
  3. `.\gradlew publishReleaseBundle` → internal testing.
  4. `.\gradlew promoteReleaseArtifact` → copies internal to closed testing (alpha). Open testing is `--promote-track beta`.
- Don't use `publishBundle` (it would include other variants).
