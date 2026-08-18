# Building the Android workspace

The workspace targets the CloudWalker TV described in the capability report without adding
Compose, large UI libraries, or networking frameworks.

## Toolchain

- JDK 17
- Android SDK Platform 34 and Build-Tools 34.0.0 or newer
- Android Gradle Plugin 8.7.3 and Gradle 8.9 (both pinned in the wrapper files)

Set `ANDROID_HOME` (or `ANDROID_SDK_ROOT`) to the Android SDK directory. On a clean Windows
machine this is commonly `%LOCALAPPDATA%\\Android\\Sdk`.

## Commands

From this `android/` directory:

```powershell
.\gradlew.bat build
.\gradlew.bat verify
```

`build` produces both debug APKs. `verify` runs the protocol JVM tests, TV/phone JVM test tasks,
and Android lint. Instrumented test source sets are configured, but require a connected device or
emulator to execute:

```powershell
.\gradlew.bat connectedDebugAndroidTest
```

Artifacts are written to:

- `tv/build/outputs/apk/debug/tv-debug.apk`
- `phone/build/outputs/apk/debug/phone-debug.apk`

Record sizes after a build with:

```powershell
Get-Item tv\build\outputs\apk\debug\tv-debug.apk, phone\build\outputs\apk\debug\phone-debug.apk |
    Select-Object Name, Length
```

## Baseline build result

Verified locally on 25 July 2026 with JDK 17 and Android SDK Platform 34:

| Artifact | Debug APK size | Minified release APK size |
| --- | ---: | ---: |
| `tv-debug.apk` | 88,903 bytes | 40,248 bytes |
| `phone-debug.apk` | 43,560 bytes | 20,672 bytes |

The final command was `.\gradlew.bat verify build --console=plain --no-build-cache`. It completed
all 182 tasks successfully, including protocol, TV, and phone unit tests, Android lint, and both
debug and minified release builds.

## Implemented scope

The TV app owns one session-0 hardware Equalizer from a foreground service, listens on stable local
TCP port `49321`, advertises DNS-SD discovery, supports short-code pairing and persistent
authenticated credentials, restores state after process restart, recalls per-route presets, and
starts after boot when autostart is enabled.

The phone app discovers or manually addresses the TV, retains its pairing credential in Android
Keystore, displays the hardware-advertised bands, streams serialized changes while sliders move,
and exposes enable, reset, preset, route, and diagnostics screens. The shared `protocol` module
implements bounded ASCII framing, HMAC authentication, revisions, state snapshots, and mutation
acknowledgements.

The TV module uses `minSdk 28` for the Android 9 device and `targetSdk 34` for current platform
compatibility. The phone module uses `minSdk 26` and `targetSdk 34` for a practical modern remote
baseline.
