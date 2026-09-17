# Azure Face Liveness: Integration Quick Start

Use this guide to start a Face liveness check from your website, complete it
in your mobile app, and verify the result on your backend. Device attestation
checks the app and device before the backend releases a Face session token.

## Integration Parts

You connect **three parts of your application**:

| Part | Responsibility | What You Add |
| --- | --- | --- |
| Website | Requests a session from your backend and presents a launch link or QR code. | Calls to your backend; no attestation library runs in the browser. |
| Backend | Creates and stores sessions, verifies attestation, releases tokens, and validates results. | One backend attestation library, your storage implementation, and calls to Azure Face. |
| Mobile app | Opens the session link, attests, runs liveness, and submits its digest. | The client attestation library **and** the separate Face liveness UI SDK. |

Choose one backend language and integrate Android, iOS, or both. An optional
iOS [App Clip](#52-use-an-app-clip) provides the mobile flow without installing
the full app; [Play installation recovery](#51-resume-after-google-play-installation)
can continue an Android session after installation.

## Session Pipeline

At runtime, each session follows this sequence:

1. **[Create the session](#13-create-sessions-and-launch-links):** The website asks your backend to start liveness.
   The backend creates a session with Azure Face, stores its ID and token,
   and returns a launch link containing the session ID, never the token.
2. **Open the app:** The user taps the link or scans its QR code.
   [Android App Links](#23-configure-and-check-android-app-links-on-the-backend)
   or [iOS Universal Links](#33-configure-and-check-ios-universal-links-on-the-backend)
   route it to your app. Configure both the backend and app; link handling
   does not attest the app.
3. **Attest and obtain the token:** The [Android](#21-configure-android-identity-and-play-integrity)
   or [iOS](#31-configure-ios-identity-and-app-attest) attestation library sends
   app and device proof to your [backend APIs](#12-expose-the-attestation-endpoints).
   The backend verifies it before releasing the encrypted Face session token.
4. **Run liveness:** Your app passes the token to the Face liveness UI SDK.
   That SDK captures and submits the liveness data to Azure Face and returns
   a digest, a fingerprint of the submitted payload.
5. **Submit the client digest:** Your app sends that digest to your backend
   through the attestation library, using the same authenticated session.
   See the [Android](#25-run-attestation-and-liveness) or
   [iOS runtime flow](#35-run-attestation-and-liveness) for steps 3-5.
6. **[Validate and use the result](#14-validate-the-liveness-result):** Your backend retrieves the Face result
   for the same session and **requires its digest to match the client digest**.
   Reject missing or mismatched digests. Matching binds the Face result to
   the attested client's submission; then use the result's liveness decision
   in your business workflow. A match alone does not mean liveness passed.

## Integration Roadmap

Follow this implementation order. Complete the shared backend work, then the
mobile platforms you support.

1. **Prepare:** Complete the [prerequisites](#prerequisites) and collect the
   configuration values listed in [backend setup](#11-configure-the-library-and-storage).
2. **Integrate the backend and website:** Configure the [library and storage](#11-configure-the-library-and-storage),
   expose the [attestation endpoints](#12-expose-the-attestation-endpoints),
   then implement [session creation and launch links](#13-create-sessions-and-launch-links)
   and [result validation](#14-validate-the-liveness-result).
3. **Integrate your mobile app:** Follow [Android](#2-android-integration),
   [iOS](#3-ios-integration), or both, in section order: app identity,
   libraries and permissions, link configuration on the backend and app,
   then the attestation and liveness flow.
4. **Verify the core flow:** [Run one complete session](#4-verify-one-complete-session)
   on a supported physical device and check the documented failure cases.
5. **Add optional installation flows:** Configure [Play installation recovery](#51-resume-after-google-play-installation)
   or an [App Clip](#52-use-an-app-clip) if needed, then repeat the
   complete-session checks for each flow.
6. **Prepare for production:** Complete the [Before Shipping](#before-shipping)
   checklist.

## Prerequisites

- Have an Azure Face resource enabled for liveness and access to the Face
   liveness UI SDK. The attestation library does not replace that SDK.
- Use a publicly reachable HTTPS backend. Replace `liveness.example.com` and
   all example app identifiers with your values.
- Test with signed apps on physical devices: Android 12+ or iOS 15+ with App
   Attest support. iOS development requires macOS and Xcode.
- Keep Face and Google service-account credentials on the backend. Never put
   them or the Face session token in a browser bundle, log, QR code, or link.

**Local development:** Set the backend configuration's `debugMode` to `true`
to support locally installed development builds (`DebugMode` in .NET,
`debug_mode` in Python). This is not a mobile initialization parameter and
does not disable attestation. Keep it `false` in production; see the
[development-policy reference](OVERVIEW.md#local-development-policy) for details.

## 1. Backend and Website Integration

### 1.1. Configure the Library and Storage

Choose one language and add its local source library using the sample's
dependency mechanism. Do not assume these packages are on a public feed.

| Backend | Library to Add | Integration Example |
| --- | --- | --- |
| .NET | [library/dotnet/AzureAIVisionFaceDeviceAttestation](library/dotnet/AzureAIVisionFaceDeviceAttestation), as a project reference | [backend/dotnet/Program.cs](backend/dotnet/Program.cs) |
| Java | [library/java/AzureAIVisionFaceDeviceAttestation](library/java/AzureAIVisionFaceDeviceAttestation), as a built Maven dependency | [backend/pom.xml](backend/pom.xml) and [backend/java/pom.xml](backend/java/pom.xml) |
| Python | [library/python/AzureAIVisionFaceDeviceAttestation](library/python/AzureAIVisionFaceDeviceAttestation), as a local package or built wheel | [backend/python/app/attestation_service.py](backend/python/app/attestation_service.py) |
| Node.js / TypeScript | [library/javascript/AzureAIVisionFaceDeviceAttestation](library/javascript/AzureAIVisionFaceDeviceAttestation), as a built local npm package | [backend/react/app/_lib/attestation_service.ts](backend/react/app/_lib/attestation_service.ts) |

Prepare an `AttestationConfig` before creating the service. **The library does
not read environment variables:** your application supplies the configuration.
This guide uses Java/TypeScript field names; use your language's definition
for all fields and defaults:
[.NET](library/dotnet/AzureAIVisionFaceDeviceAttestation/src/Configuration/AttestationConfig.cs),
[Java](library/java/AzureAIVisionFaceDeviceAttestation/src/main/java/com/azure/ai/vision/face/deviceattestation/config/AttestationConfig.java),
[Python](library/python/AzureAIVisionFaceDeviceAttestation/azure_ai_vision_face_deviceattestation/config.py),
[TypeScript](library/javascript/AzureAIVisionFaceDeviceAttestation/src/config.ts).

| Configuration to Collect | Where to Get the Values |
| --- | --- |
| `androidPackageName`, `androidSha256CertFingerprints`, `googleServiceAccountJson` | [Android identity and Play Integrity](#21-configure-android-identity-and-play-integrity) |
| `iosAppId`, `iosApplinkAppId`, `applinkPath` | [iOS identity](#31-configure-ios-identity-and-app-attest) and [Universal Links](#33-configure-and-check-ios-universal-links-on-the-backend) |
| `debugMode` | `false` for production; see the local-development note above |

Supply a logger and your implementation of `ClusterStore` (`IClusterStore`
on .NET). This is a storage interface, not a database: Redis, SQL, or Azure
Table Storage can satisfy its [contract](library/javascript/AzureAIVisionFaceDeviceAttestation/src/store/cluster_store.ts),
including expiration and atomic, version-checked updates. All server instances
must share the store; in-memory storage is only for single-instance evaluation.

Create one service at startup. For example, with a complete `config` and your
application's `store` and `logger` in Node.js/TypeScript:

```typescript
import { createAttestationService } from '@azure/ai-vision-face-deviceattestation';

const attestation = createAttestationService(config, store, logger);
```

The [sample-host configuration reference](OVERVIEW.md#configuration) covers
environment-variable mappings; they are not required by the library.

### 1.2. Expose the Attestation Endpoints

Delegate these routes to the library, preserving the sample adapters'
query parameters, JSON bodies, HTTP status codes, and responses. Keep
them outside login-page redirects. The POST paths are defaults.

| Method | Path | Implement With |
| --- | --- | --- |
| POST | `/api/attestation/challenge` | Attestation service |
| POST | `/api/attestation/register` | Attestation service |
| POST | `/api/attestation/verify` | Attestation service |
| POST | `/api/session/token` | Attestation service |
| POST | `/api/liveness/digest` | Attestation service |
| GET | `/.well-known/assetlinks.json` | Service's Android association document |
| GET | `/.well-known/apple-app-site-association` | Service's Apple association document |

**Custom API paths:** Change the backend routes and pass matching
`DeviceAttestationEndpoints` to `initialize` on Android and iOS. Paths are
host-relative, without a leading `/`; unspecified fields keep their defaults.
See [endpoint configuration](../client_libraries/OVERVIEW.md#endpoints).
The two `/.well-known/...` association URLs must stay unchanged and publicly
accessible without authentication or redirects.

### 1.3. Create Sessions and Launch Links

When your website requests a liveness check, create a Face session on the
server, then save its ID and token through the attestation service's
session-storage API before publishing the link. Keep credentials and
application metadata in your own server-side store. The library does not
create Face sessions; see the [Node.js creation example](backend/react/app/actions.ts).

Return a launch link or QR code to your website and serve a browser fallback
at the same path. `/native` is the sample launch route, not a library requirement:

```text
https://liveness.example.com/native?s=<session-id>
```

You can change `/native`; keep backend routing, generated links, and mobile
link matching consistent. `applinkPath` changes AASA matching, not backend
routes. Launch paths are separate from `DeviceAttestationEndpoints` API paths;
see the [routing reference](OVERVIEW.md#custom-launch-paths).

Optionally append `callbackUrl=<percent-encoded-return-url>` for a browser
return. Use an HTTPS page on your backend origin, outside the app-opening
path pattern.

**Verify:** Deploy behind HTTPS, create a session, and check that its launch
link contains no token or credentials.

### 1.4. Validate the Liveness Result

After digest submission, check completion through the attestation service and
retrieve the Face result for the same session on your server. Require a
non-empty client digest and an exact match with the Face result's digest.
Reject missing or mismatched digests before using the liveness decision.
Your backend application is responsible for this comparison.

Use the verified outcome internally in your business workflow. The sample's
`GET /api/session/result` and `/result` page only display results; neither
browser polling nor result display is required in production. See the
[Node.js verification example](backend/react/app/api/session/result/route.ts).

**Verify:** Test matching, missing, and mismatched digests. Only the matching
case may reach the business workflow that uses the liveness decision.

## 2. Android Integration

Use the [Android sample app](../samples/kotlin/face/AzureVisionLiveness/OVERVIEW.md)
as an implementation example, not an app dependency. Its overview covers the
sample's host and Play project settings. Use your own app identity, signing,
backend configuration, and UI.
Open the project in Android Studio from the repository checkout; it uses
shared sample sources.

### 2.1. Configure Android Identity and Play Integrity

Set `androidPackageName` and `androidSha256CertFingerprints` in the backend's
`AttestationConfig` to match the installed app's `applicationId` (for example,
`com.example.liveness`) and signing certificate. Supply fingerprints as a
collection of colon-separated SHA-256 strings. For Play builds, use the
[Play App Signing certificate](https://developer.android.com/studio/publish/app-signing#certificates),
not the upload certificate.

Google's [Digital Asset Links guide](https://developer.android.com/training/app-links/configure-assetlinks)
explains the `sha256_cert_fingerprints` field and where to find its value.

Enable Play Integrity and link the Cloud project to your app. Follow Google's
[project-number lookup instructions](https://docs.cloud.google.com/resource-manager/docs/view-update-projects#identifying_projects)
and [Play Integrity setup](https://developer.android.com/google/play/integrity/setup).
Use the numeric **project number**, not the project ID. Opt in to
`MEETS_STRONG_INTEGRITY`, which the backend's default policy requires.

Load the service-account JSON contents securely into `googleServiceAccountJson`
on the backend, with access to decode your app's Play Integrity tokens. Follow
Google's [service-account key instructions](https://docs.cloud.google.com/iam/docs/keys-create-delete#creating)
to download the JSON key. It contains a private key; never commit it or include
it in your app.

### 2.2. Add the Android Library and Permissions

Add the attestation module alongside your
[Face liveness UI SDK](../samples/kotlin/face/FaceLivenessDetectorSample/README.md).

1. Copy the [Android library module](../client_libraries/android/AzureAIVisionFaceDeviceAttestation)
   into `azure-ai-vision-face-deviceattestation` under your project root. Use
   `minSdk` 31+, `compileSdk` 36, and `google()` / `mavenCentral()` repositories.
   Match the sample's [AGP 9.1 build setup](../samples/kotlin/face/AzureVisionLiveness/build.gradle.kts),
   which uses built-in Kotlin. Include the module in your Gradle settings:

```kotlin
include(":azure-ai-vision-face-deviceattestation")
```

Add it to your app's `dependencies` block:

```kotlin
implementation(project(":azure-ai-vision-face-deviceattestation"))
```

2. Add Internet and camera permissions to your manifest and handle the runtime
   camera permission request before showing the Face liveness UI.

### 2.3. Configure and Check Android App Links on the Backend

Publish the library's Android association document using the package name
and signing fingerprint from [Android identity setup](#21-configure-android-identity-and-play-integrity).

**Verify:** After deploying or restarting the backend, expect HTTP 200 JSON
with your package name and signing fingerprint, without login or redirects:

```shell
curl -i https://liveness.example.com/.well-known/assetlinks.json
```

### 2.4. Configure and Check Android App Links in the App

Add this filter to the exported activity that handles liveness links:

```xml
<intent-filter android:autoVerify="true">
    <action android:name="android.intent.action.VIEW" />
    <category android:name="android.intent.category.DEFAULT" />
    <category android:name="android.intent.category.BROWSABLE" />
    <data android:scheme="https"
          android:host="liveness.example.com"
          android:pathPrefix="/native" />
</intent-filter>
```

Handle both initial and new intents. Accept only HTTPS links for your
allowlisted host and expected path, and read `s` as the session ID. Validate
any callback against your backend's HTTPS origin before opening it.
See the sample's [manifest](../samples/kotlin/face/AzureVisionLiveness/app/src/main/AndroidManifest.xml)
and [AppCenterActivity.kt](../samples/kotlin/face/AzureVisionLiveness/app/src/main/java/com/example/faceanalyzersamplecomposeinternal/AppCenterActivity.kt)
for app configuration and intent handling.

**Verify**

Install a build signed by the configured certificate and allow Android's
automatic link verification to finish. Confirm that the host is `verified`:

```shell
adb shell pm get-app-links com.example.liveness
```

Tap a fresh session link from another app and confirm your app receives its
session ID. This checks routing, not attestation. For GUI settings, retries,
or failures, see [Android link diagnostics](../samples/kotlin/face/AzureVisionLiveness/OVERVIEW.md#link-diagnostics).

### 2.5. Run Attestation and Liveness

See [AppLinkAuthFlow.kt](../samples/kotlin/face/AzureVisionLiveness/app/src/main/java/com/example/faceanalyzersamplecomposeinternal/AppLinkAuthFlow.kt)
for the sample's attestation and token flow.

From the validated link handler, run this in a coroutine. Supply `context`,
`sessionId`, and a persistent app-owned `deviceId`; replace the example host
and project number with your configuration.

```kotlin
import com.azure.android.ai.vision.face.deviceattestation.AttestationSession
import com.azure.android.ai.vision.face.deviceattestation.DeviceAttestation

DeviceAttestation.initialize(
   context = context,
   livenessHost = "liveness.example.com",
   cloudProjectNumber = 123456789012L
)

val session = when (val result = DeviceAttestation.startSession(context, sessionId, deviceId)) {
   is DeviceAttestation.StartSessionResult.Success -> result.session
   is DeviceAttestation.StartSessionResult.Error -> error("Attestation failed: ${result.code}")
   is DeviceAttestation.StartSessionResult.Exception -> throw result.exception
}

val token = when (val result = session.fetchSessionToken()) {
   is AttestationSession.SessionTokenResult.Success -> result.token
   is AttestationSession.SessionTokenResult.Error -> error("Token request failed: ${result.code}")
   is AttestationSession.SessionTokenResult.Exception -> throw result.exception
}
```

Pass `token` to the Face liveness UI SDK, as shown in the shared
[LivenessScreen.kt](../samples/kotlin/face/sample/java/com/example/facelivenessdetectorsample/screens/LivenessScreen.kt).
Submit its non-empty digest using the same session; see
[ResultScreen.kt](../samples/kotlin/face/AzureVisionLiveness/app/src/main/java/com/example/facelivenessdetectorsample/screens/ResultScreen.kt)
for the sample's submission handler. Handle success, error, and exception
results in your UI:

```kotlin
val digestResult = session.submitLivenessDigest(digest)
```

After wiring this flow, run [end-to-end verification](#4-verify-one-complete-session).

## 3. iOS Integration

Use the [iOS sample app](../samples/swift/face/AzureVisionLiveness/OVERVIEW.md)
as an implementation example. Its overview covers host configuration for the
full app and optional App Clip. Use your own signing, bundle identifiers,
backend configuration, and UI.
Open its [Xcode project](../samples/swift/face/AzureVisionLiveness/AzureVisionLiveness.xcodeproj)
from the repository checkout; it uses shared sample sources.

### 3.1. Configure iOS Identity and App Attest

Set your signing team and bundle identifier, and enable App Attest with
an environment accepted by your backend; distributed builds use production.
See the [sample entitlements](../samples/swift/face/AzureVisionLiveness/FaceAnalyzerSample/FaceAnalyzerSample.entitlements).

Set `iosAppId` in the backend's `AttestationConfig` to your full app's signed
application identifier, such as `ABCDE12345.com.example.liveness`:
`<AppIDPrefix>.<BundleID>`. The prefix is often the Team ID, but use the actual
signing value.

`iosAppId` controls App Attest. Check `DCAppAttestService.shared.isSupported`
in your app and handle unsupported devices.

### 3.2. Add the iOS Library and Permissions

Add the attestation package alongside your
[Face liveness UI SDK](../samples/swift/face/FaceAnalyzerSample/README.md).

1. In Xcode, use **File > Add Package Dependencies > Add Local** to add the
   [Swift package](../client_libraries/ios/AzureAIVisionFaceDeviceAttestation).
   Link its `AzureAIVisionFaceDeviceAttestation` product to your app target.
   The package requires iOS 15+ and Swift tools 5.9+.
2. Add a camera usage description and request permission before liveness.

### 3.3. Configure and Check iOS Universal Links on the Backend

Set `iosApplinkAppId` and `applinkPath` in the backend's `AttestationConfig`
to the full app's signed identifier (such as `ABCDE12345.com.example.liveness`)
and launch-path pattern (`/native*`). `iosApplinkAppId` controls Universal Links
and defaults to `iosAppId` when unset.

For the AASA format and hosting requirements, see Apple's
[associated domains guide](https://developer.apple.com/documentation/xcode/supporting-associated-domains).

**Verify:** Deploy or restart, then check the Apple App Site Association (AASA)
endpoint. Expect HTTP 200 `application/json`, no login or redirects, and
`applinks` details containing your full app identifier and `/native*`:

```shell
curl -i https://liveness.example.com/.well-known/apple-app-site-association
```

### 3.4. Configure and Check iOS Universal Links in the App

Follow Apple's [Universal Links setup guide](https://developer.apple.com/documentation/xcode/allowing-apps-and-websites-to-link-to-your-content)
for app configuration and incoming link handling.
In **Signing & Capabilities > Associated Domains**, add your host without
a scheme, path, or trailing slash:

```text
applinks:liveness.example.com
```

Handle `NSUserActivity.webpageURL` (SwiftUI:
`.onContinueUserActivity(NSUserActivityTypeBrowsingWeb)`). Validate HTTPS, the
allowlisted host, and the expected path before reading `s` with `URLComponents`.
Validate any callback against your backend's HTTPS origin before opening it.
See [UniversalLinkProcessor.swift](../samples/swift/face/AzureVisionLiveness/FaceAnalyzerSample/UniversalLinkProcessor.swift)
for the sample's link validation and attestation flow.

**Verify:** Rebuild and install a signed app after changing entitlements.
Create a fresh session and tap its launch link from Notes or Mail, not
Safari's address bar. Expect your app to receive the session ID. This checks
link handling, not attestation or the liveness result.

### 3.5. Run Attestation and Liveness

From an async throwing function, supply the validated `sessionId`, a stable
UUID-format `clientId`, and a stable `deviceUUID` for the local key. Initialize
with your validated backend host:

```swift
import Foundation
import AzureAIVisionFaceDeviceAttestation

await DeviceAttestation.shared.initialize(livenessHost: "liveness.example.com")

let session: AttestationSession
switch await DeviceAttestation.shared.startSession(
   sessionId: sessionId,
   clientId: clientId,
   deviceUUID: deviceUUID
) {
case .success(let started):
   session = started
case .error(let code, let message):
   throw NSError(domain: "LivenessAttestation", code: code,
              userInfo: [NSLocalizedDescriptionKey: message])
case .exception(let error):
   throw error
}

let token: String
switch await session.fetchSessionToken() {
case .success(let fetched):
   token = fetched
case .error(let code, let message):
   throw NSError(domain: "LivenessAttestation", code: code,
              userInfo: [NSLocalizedDescriptionKey: message])
case .exception(let error):
   throw error
}
```

Pass `token` to the Face liveness UI SDK, as shown in the shared
[MainView.swift](../samples/swift/face/FaceAnalyzerSample/FaceAnalyzerSample/MainView.swift).
Submit its non-empty digest using the same session; see
[ResultViewFullApp.swift](../samples/swift/face/AzureVisionLiveness/FaceAnalyzerSample/ResultViewFullApp.swift)
for the sample's submission handler. Handle success, error, and exception
results in your UI:

```swift
let digestResult = await session.submitLivenessDigest(digest)
```

After wiring this flow, run [end-to-end verification](#4-verify-one-complete-session).

## 4. Verify One Complete Session

Use a supported physical device. For final production-policy verification,
set `debugMode` to `false` in the backend configuration and install through
a Play test track (Android) or TestFlight/App Store (iOS).

Run one session at a time and keep its host and Play project configuration
unchanged throughout the flow.

1. Create a fresh session on your website and open its launch link or QR code.
   Confirm that the intended app receives the correct session ID.
2. Confirm that the app completes attestation and obtains the Face session
   token. Failed attestation must prevent token delivery.
3. Complete the Face liveness UI flow and await successful submission of its
   non-empty digest through the originating attestation session.
4. Confirm that your backend applies [result validation](#14-validate-the-liveness-result)
   before using the liveness decision. Missing or mismatched digests must
   prevent the result from reaching your business workflow.
5. If you use a browser return, open only a validated callback after successful
   digest submission. The sample result screens can still return after
   submission failures; gate this explicitly in your app.

Also test first and returning-device sessions, expiration, unsupported devices,
denied camera access, and untrusted hosts or callbacks. Handle failures in your
UI and fail closed when attestation or digest verification fails.

## 5. Optional Installation Flows

### 5.1. Resume After Google Play Installation

Use Play Install Referrer when the user must install your Android app before
continuing the session. Encode the session launch link in the Play Store URL's
`referrer`; retrieve and validate it when the installed app first opens.
The store URL belongs to your website/backend, not `AttestationConfig`.

Follow [Resuming after Play installation](../samples/kotlin/face/AzureVisionLiveness/OVERVIEW.md#resume-after-google-play-installation)
for link construction, one-time consumption, and expiration checks.
Test installation and session recovery through Google Play, not `adb`.

### 5.2. Use an App Clip

An App Clip runs the iOS liveness flow without installing the full app. Build
and publish your own Clip using the same libraries and runtime flow as above.

1. Follow Apple's [creation guide](https://developer.apple.com/documentation/appclip/creating-an-app-clip-with-xcode).
   Use the full app's signing team and parent-app association. Share the
   attestation package, Face liveness UI SDK, and invocation handler; enable
   App Attest and camera access in the Clip too.
2. Add `appclips:liveness.example.com` to **Associated Domains** on both
   targets. Keep `applinks:liveness.example.com` on the full app and add it to
   the Clip for this sample's flow. Use the same runtime host allowlist.
3. Set `iosAppClipId` in the backend configuration to the signed Clip ID,
   such as `ABCDE12345.com.example.liveness.Clip`. Keep `iosAppId` set to the
   **full app's** identity for [App Attest verification](https://developer.apple.com/documentation/devicecheck/validating-apps-that-connect-to-your-server);
   the Clip ID is only for link association.
4. Rebuild and sign, then upload the full app with its embedded Clip and
   configure a [default experience](https://developer.apple.com/documentation/appclip/configuring-the-launch-experience-of-your-app-clip).
   Store its generated default link in your host's link-generation settings,
   not `AttestationConfig`. A demo link cannot carry session parameters.
   Public links require App Store approval and release; TestFlight is for beta testing.
5. For custom-domain invocation, configure an experience matching your launch
   URL and follow Apple's [website association guide](https://developer.apple.com/documentation/appclip/associating-your-app-clip-with-your-website).

Build invocation URLs with a URL/query encoder. Include the session ID and
backend host; add an encoded `callbackUrl` only for an optional browser return:

```text
https://appclip.apple.com/id?p=com.example.liveness.Clip&s=<session-id>&domain=liveness.example.com
```

For Apple-hosted links, select the backend from the allowlisted `domain`, not
`appclip.apple.com`. Direct backend links use their validated host. Reject
missing sessions and untrusted hosts or callbacks; see the
[sample invocation handler](../samples/swift/face/AzureVisionLiveness/FaceAnalyzerSampleAppClip/FaceAnalyzerSampleAppClipApp.swift).

**Verify:** Check AASA `appclips.apps` and `applinks` for the Clip/app identities
and launch-path pattern; confirm domain validation in App Store Connect.
Follow Apple's [testing guide](https://developer.apple.com/documentation/appclip/testing-the-launch-experience-of-your-app-clip)
for Xcode (`_XCAppClipURL`) and TestFlight, then test a released link with the
full app absent and installed. Remove local experience overrides first and
run the [complete-session checks](#4-verify-one-complete-session) in both cases;
development testing alone does not verify public launch.

## Before Shipping

- Disable development mode in the library configuration. Keep weaker integrity
   and Google-outage fallback policies disabled unless deliberately reviewed.
- Add your own authorization, session expiration, shared storage, secret
   management, and safe logging. Attestation is not user authentication.

For the trust model and further considerations, see the
[backend security notes](OVERVIEW.md#security-notes) and
[client security notes](../client_libraries/OVERVIEW.md#security-notes).
