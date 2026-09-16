# AzureLiveness Android app

This project is the Android AzureLiveness app. It uses the local
[device-attestation client library](../../../../client_libraries/android/AzureAIVisionFaceDeviceAttestation)
and opens liveness sessions created by any of the
[backend samples](../../../../backend_samples).

## App Link location

The backend website creates links in this form:

```text
https://<backend-host>/native?s=<session-id>&callbackUrl=<encoded-result-url>
```

Only the session ID and an optional same-origin result callback are carried in
the link. The Face session token remains on the backend.

The Android App Link configuration is split across these files:

| Location | Purpose |
| --- | --- |
| [`app/src/main/AndroidManifest.xml`](app/src/main/AndroidManifest.xml) | Declares an HTTPS `android:autoVerify="true"` intent filter for `${livenessHost}` and `/native` |
| [`app/build.gradle.kts`](app/build.gradle.kts) | Reads the backend host, injects it into the manifest, and creates the runtime `LIVENESS_HOSTS` allowlist |
| [`gradle.properties`](gradle.properties) | Provides the default `livenessHost` Gradle property |
| [`AppCenterActivity.kt`](app/src/main/java/com/example/faceanalyzersamplecomposeinternal/AppCenterActivity.kt) | Validates the incoming host, reads the `s` and `callbackUrl` parameters, and starts attestation |

Set the host as a hostname without `https://` or a path. The build accepts the
`LIVENESS_HOST` environment variable or the `livenessHost` Gradle property:

```properties
livenessHost=my-backend.azurewebsites.net
```

A comma-separated value binds the app to multiple backend hosts. Gradle adds an
intent filter and runtime allowlist entry for each host; every host must publish
its own matching Digital Asset Links document.

## How the website URL is bound

Android verifies both sides of the association:

1. The signed app claims `https://<backend-host>/native` in its `autoVerify`
   manifest intent filter.
2. The backend serves
   `https://<backend-host>/.well-known/assetlinks.json`, generated from:
   - `ANDROID_PACKAGE_NAME=com.microsoft.azurevisionliveness`
   - `ANDROID_SHA256_CERT_FINGERPRINTS=<app-signing certificate SHA-256>`
3. Android fetches that document and checks that its package name and
   certificate fingerprint match the installed, signed app.
4. After a match, Android routes the backend's `/native` HTTPS links to
   AzureLiveness. The app accepts the link only when its host is also present in
   the build-time `LIVENESS_HOSTS` allowlist, then calls the attestation APIs on
   that same backend.

Use the fingerprint of the certificate that signs the installed app. For a
Google Play release, this is normally the **Play App Signing** certificate, not
the upload certificate. Updating the app package, signing certificate, or
backend hostname requires updating the corresponding backend or app setting.

Link association determines which app handles the website URL. Hardware Key
Attestation and Play Integrity run afterward and separately establish app and
device integrity before the backend releases the Face token.

## Resume after Google Play installation

When the app is not installed, the backend can preserve the current liveness
session through Google Play Install Referrer. Configure the backend with the
app's base Play Store listing URL:

```text
ANDROID_PLAY_STORE_URL=https://play.google.com/store/apps/details?id=com.microsoft.azurevisionliveness
```

Do not add a `referrer` value to this setting. The backend creates and encodes
it for each session:

```text
https://play.google.com/store/apps/details?id=com.microsoft.azurevisionliveness&referrer=<encoded-app-link>
```

The complete flow is:

1. The backend builds the normal App Link,
   `https://<backend-host>/native?s=<session-id>&callbackUrl=<result-url>`.
2. The **Open in app** action uses an Android Intent URI. If AzureLiveness is
   installed, the intent opens it immediately. Otherwise,
   `S.browser_fallback_url` sends the user to the configured Google Play URL.
3. The fallback URL carries the complete App Link as its percent-encoded
   `referrer` query parameter. Google Play records this value during install.
4. When the user selects **Open** after installation, AzureLiveness starts
   without an App Link intent and queries the Play Install Referrer API. The
   dependency is declared in [`app/build.gradle.kts`](app/build.gradle.kts).
5. [`AppCenterActivity.kt`](app/src/main/java/com/example/faceanalyzersamplecomposeinternal/AppCenterActivity.kt)
   decodes the recovered URL and resumes the same App Link flow. It requires an
   `s` parameter, verifies the URL host against `LIVENESS_HOSTS`, and validates
   the result callback before following it.

The app consumes the install referrer only once and accepts it only when the
recorded Play click is no more than ten minutes old. A missing, malformed,
stale, or previously consumed referrer opens the normal home screen instead.
A direct App Link launch also marks any install referrer as consumed so an old
session cannot unexpectedly resume later.

The referrer is a navigation hint, not a credential. It contains no Face token,
and possession of a session ID does not bypass App Link host validation,
hardware attestation, Play Integrity, request signing, or payload encryption.

Test deferred session recovery through a Google Play internal-testing or
internal-app-sharing installation. Installing an APK directly with `adb` does
not reproduce the Play Store Install Referrer flow.

## Verify the binding

After deploying the backend, confirm that its document contains the expected
package and signing fingerprint:

```text
https://<backend-host>/.well-known/assetlinks.json
```

On a device with the signed app installed, inspect and exercise the association:

```shell
adb shell pm get-app-links com.microsoft.azurevisionliveness
adb shell am start -a android.intent.action.VIEW -d "https://<backend-host>/native?s=<session-id>"
```

If verification fails or the app is not installed, the URL opens the backend's
browser landing page instead.