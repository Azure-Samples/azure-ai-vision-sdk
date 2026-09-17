# AzureLiveness iOS app

This project contains the iOS AzureLiveness full app and App Clip. Both use the
local
[device-attestation client library](../../../../client_libraries/ios/AzureAIVisionFaceDeviceAttestation)
and connect to any of the [backend samples](../../../../backend_samples/OVERVIEW.md).

The backend environment-variable examples in this guide use the Python, Java,
and Node.js names. For .NET, use the `AppSettings__...` equivalents in the
[backend configuration table](../../../../backend_samples/OVERVIEW.md#configuration).

## Universal Link location

The backend website creates links in this form:

```text
https://<backend-host>/native?s=<session-id>&callbackUrl=<encoded-result-url>
```

Only the session ID and an optional same-origin result callback are carried in
the link. The Face session token remains on the backend.

The iOS Universal Link configuration is split across these files:

| Location | Purpose |
| --- | --- |
| [`AzureVisionLiveness.xcconfig`](AzureVisionLiveness.xcconfig) | Defines the primary `LIVENESS_HOST` hostname |
| [`FaceAnalyzerSample.entitlements`](FaceAnalyzerSample/FaceAnalyzerSample.entitlements) | Signs `applinks:<host>` into the full app |
| [`FaceAnalyzerSampleAppClip.entitlements`](FaceAnalyzerSampleAppClip/FaceAnalyzerSampleAppClip.entitlements) | Signs `appclips:<host>` and `applinks:<host>` into the App Clip |
| [`UniversalLinkProcessor.swift`](FaceAnalyzerSample/UniversalLinkProcessor.swift) | Validates the incoming host, reads the `s`, `callbackUrl`, and optional `domain` parameters, and starts attestation |
| [`scripts/apply-liveness-hosts.sh`](scripts/apply-liveness-hosts.sh) | Applies one or more backend hosts to both targets and their runtime allowlists |

Set the primary host as a hostname without `https://` or a path:

```text
LIVENESS_HOST = my-backend.azurewebsites.net
```

For multiple backend domains, run the configuration script on macOS:

```shell
LIVENESS_HOSTS="one.example.com,two.example.com" ./scripts/apply-liveness-hosts.sh
```

The script updates the signed associated-domain entitlements and the
`LivenessHosts` runtime allowlist for the full app and App Clip.

## How the website URL is bound

iOS verifies both sides of the association:

1. The signed app claims the backend domain with its `applinks:<host>`
   entitlement. The App Clip additionally claims `appclips:<host>`.
2. The backend serves
   `https://<backend-host>/.well-known/apple-app-site-association`, generated
   from:
   - `IOS_APPLINK_APP_ID=<TeamID>.com.microsoft.azurevisionliveness`, falling
     back to `IOS_APP_ID` when unset
   - `IOS_APP_CLIP_ID=<TeamID>.com.microsoft.azurevisionliveness.Clip` when the
     App Clip is enabled
   - `APPLINK_PATH=/native*`
3. iOS fetches the AASA document and checks that the signed app identifier and
   requested path are authorized for that domain.
4. After a match, iOS routes the backend's `/native` HTTPS links to
   AzureLiveness. The app validates the link host against its `LivenessHosts`
   allowlist, reads the session ID, and calls the attestation APIs on that same
   backend.

Link association determines which app handles the website URL. App Attest runs
afterward and separately establishes app and device integrity before the
backend releases the Face token.

## Launch the App Clip when the app is not installed

Configure the backend with the App Clip URL and identifier:

```text
IOS_APP_STORE_URL=https://appclip.apple.com/id?p=com.microsoft.azurevisionliveness.Clip
IOS_APP_CLIP_ID=<TeamID>.com.microsoft.azurevisionliveness.Clip
```

Also set this project's `LIVENESS_HOST` to the backend hostname. Publish the App
Clip and configure its App Store Connect experience for
`https://<backend-host>/native`. The backend's AASA file must be publicly
available and list the same App Clip ID.

For each session, the backend adds the session ID, result page, and backend host
to the configured URL:

```text
https://appclip.apple.com/id?p=<clip-bundle-id>&s=<session-id>&callbackUrl=<result-url>&domain=<backend-host>
```

When the user selects **Open App Clip**:

1. iOS downloads and launches the App Clip when the full app is not installed.
2. The App Clip checks `domain` against its `LivenessHosts` allowlist.
3. It uses `s` to resume attestation and liveness on that backend.
4. When finished, it opens `callbackUrl` only if it is HTTPS on the same host.

The launch URL contains no Face token. The token stays on the backend and is
released only after App Attest succeeds. The URL handling is implemented in
[`FaceAnalyzerSampleAppClipApp.swift`](FaceAnalyzerSampleAppClip/FaceAnalyzerSampleAppClipApp.swift)
and [`UniversalLinkProcessor.swift`](FaceAnalyzerSample/UniversalLinkProcessor.swift).

## Verify the binding

After deploying the backend, confirm that its AASA document contains the full
app ID, optional App Clip ID, and `/native*` component:

```text
https://<backend-host>/.well-known/apple-app-site-association
```

Install a signed build and open this URL from a link in Mail, Notes, or another
app:

```text
https://<backend-host>/native?s=<session-id>
```

If verification fails or the app is not installed, the URL opens the backend's
browser landing page instead. AASA responses are cached by iOS, so association
changes may not be visible immediately on an already-installed device.