# Releasing Tonewire

Releases are published automatically by `.github/workflows/release.yml` when a version tag is
pushed. Each release carries two signed APKs, a checksum file, and install notes.

## Cutting a release

```bash
# 1. Bump the version in both modules so the APKs report it.
#    android/tv/build.gradle.kts      versionCode / versionName
#    android/phone/build.gradle.kts   versionCode / versionName

git add android/tv/build.gradle.kts android/phone/build.gradle.kts
git commit -m "Bump version to 0.2.0"
git push origin master

# 2. Tag and push the tag. This is what triggers the release.
git tag -a v0.2.0 -m "Tonewire 0.2.0"
git push origin v0.2.0
```

The workflow runs `verify build`, signs both release APKs, writes `SHA256SUMS.txt`, and creates the
GitHub Release. Watch it under the repository's Actions tab.

To re-run a failed release for an existing tag, use **Actions → Release → Run workflow** and supply
the tag.

## Why the version numbers matter

`versionCode` must increase for every release or Android will refuse to upgrade an installed app.
`versionName` is what people see. Keep them in step:

| `versionName` | `versionCode` |
| --- | --- |
| 0.2.0 | 2 |
| 0.2.1 | 3 |
| 0.3.0 | 4 |

## Signing

Release APKs must be signed with **the same key the installed app uses**, otherwise Android rejects
the upgrade and the only way forward is uninstalling — which erases the TV's paired-phone
credentials and every saved preset.

Tonewire's installed builds use the debug keystore. To keep upgrades working, the release workflow
signs with that same keystore, supplied from repository secrets.

### Configuring the secrets

Once, in the repository settings (**Settings → Secrets and variables → Actions → New repository
secret**):

```bash
# Encode the keystore. On the machine that holds it:
base64 -w0 ~/.android/debug.keystore > keystore.b64
```

Then add four secrets:

| Secret | Value |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | contents of `keystore.b64` |
| `ANDROID_KEYSTORE_PASSWORD` | `android` (the debug keystore default) |
| `ANDROID_KEY_ALIAS` | `androiddebugkey` |
| `ANDROID_KEY_PASSWORD` | `android` |

Delete `keystore.b64` afterwards.

**Without these secrets the workflow still publishes**, but the APKs are unsigned and marked
`-UNSIGNED` in the filename, and the release notes say so plainly. An unsigned APK will not install.

### Note on using a debug keystore

A debug keystore is not a secret in the usual sense — it is a well-known default on every Android
developer's machine — so publishing it via secrets is not a meaningful loss. But it also provides no
real authenticity guarantee: anyone can sign an APK that claims to be Tonewire.

That is acceptable for a hobby project installing over ADB on a home TV. If Tonewire ever ships to
strangers, generate a dedicated release keystore, store it somewhere durable, and accept that the
first such release requires a one-time uninstall on existing devices.

Losing the keystore permanently means existing installs can never be upgraded in place.

## What a release contains

```
tonewire-tv-<version>.apk        the TV service app
tonewire-phone-<version>.apk     the phone remote
SHA256SUMS.txt                   checksums for both
```

Installing:

```bash
adb install -r tonewire-tv-<version>.apk
adb install -r tonewire-phone-<version>.apk
```

`-r` upgrades in place and preserves app data. Never uninstall the TV app to "fix" an install
problem, because that destroys the pairing store.

## Manual release (no CI)

If Actions is unavailable:

```bash
cd android
export ANDROID_HOME=$HOME/Android/Sdk
./gradlew verify build

BUILD_TOOLS=$(ls -d "$ANDROID_HOME"/build-tools/* | sort -V | tail -1)
cp tv/build/outputs/apk/release/tv-release-unsigned.apk ../tonewire-tv-0.2.0.apk
"$BUILD_TOOLS/apksigner" sign --ks ~/.android/debug.keystore \
  --ks-pass pass:android --ks-key-alias androiddebugkey --key-pass pass:android \
  ../tonewire-tv-0.2.0.apk

cd .. && sha256sum tonewire-*.apk > SHA256SUMS.txt
gh release create v0.2.0 tonewire-*.apk SHA256SUMS.txt \
  --title "Tonewire 0.2.0" --notes "See CHANGELOG or commit history."
```
