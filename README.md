# Cabin Comfort for MG

An open-source Android app that warms up or cools down your MG with one tap, using comfort profiles. It's an alternative to the official MG iSMART app for the things you do every day.

> **Unofficial.** This app is not affiliated with, endorsed by or sponsored by MG Motor, SAIC Motor or iSMART. MG and iSMART are trademarks of their respective owners.

## Why open source?

To use the app, you log in with your iSMART credentials. With the source published, anyone can check what the app does with them:

- Your credentials are stored **encrypted on the device** (Android Keystore), see `data/auth/KeystoreCipher.kt`
- They're **only sent to MG's own iSMART servers**, see `saic/SaicApi.kt`
- No ads, no analytics, no servers of our own

## Features

- **Comfort profiles.** "Warm up" (cabin heat, defrost, seat heating, steering wheel, rear window) and "Chill" (cooling), all started with one tap
- **Editable profiles.** Set temperature, fan speed, defrost, seat levels, steering wheel and rear window
- **Smart suggestion.** Suggests a profile based on the outside temperature, but never starts one without your confirmation
- **Extended runs.** Keeps the climate going for up to 4 cycles (about 40 min) and restarts each cycle automatically
- **Reliable background queue.** Commands are queued and retried quietly, because the iSMART API often fails
- **Car status.** Shows outside and cabin temperature and battery level

Supported iSMART regions: Europe, Australia/NZ, Türkiye, Brazil.

## Tech stack

Kotlin · Jetpack Compose · Material 3 · MVVM (ViewModel + StateFlow) · Coroutines · Room · DataStore · WorkManager. Min SDK 30.

```
app/src/main/java/dk/Android/Cpmonster/mgmanager/
├── saic/       SAIC iSMART API client (crypto, models, requests)
├── queue/      Background command queue + retry, profile controller
├── climate/    Climate session handling (multi-cycle runs)
├── data/       Auth, profiles, vehicle and settings repositories, Room DB
├── work/       WorkManager workers (background polling)
└── ui/         Compose screens: login, home, profile editor, settings, about
```

## Building

1. Open the project in Android Studio.
2. Build and run the `app` configuration on a device or emulator.

Release builds are signed with a keystore that isn't in this repo. Debug builds work without it.

### Mock server

`tools/mock-server/mock_saic_server.py` is a small local stand-in for the iSMART API, so you can develop without a real car:

```
python tools/mock-server/mock_saic_server.py
```

## Credits

The API implementation is based on the reverse-engineering work in [SAIC-iSmart-API/saic-python-client-ng](https://github.com/SAIC-iSmart-API/saic-python-client-ng) (MIT).

## License

[MIT](LICENSE)
