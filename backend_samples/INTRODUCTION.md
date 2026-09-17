# How Web-to-Mobile Liveness Works

A user starts a liveness check on your website, then scans a QR code or taps
a link to continue in your Android app, iOS app, or iOS App Clip. Your
**backend** (the server behind your website) connects these steps using a
**session ID**, a reference number for that check. The liveness check assesses
whether a live person is in front of the camera.

This introduction explains the idea with examples. For implementation,
use the [integration quick start](README.md).

![Scan a QR code or tap a session link, then complete device attestation and start liveness.](../docs/face/qr-to-liveness.svg)

## 1. Links and QR Codes Carry Text

A session link is a web address, such as
`https://liveness.example.com/native?s=...`. A browser can use it to load a web
page, while an app set up to handle the link can read the session ID and start
the check. The value after `s=` identifies the session.

For this flow, a QR code stores the same HTTPS session link as a scannable
pattern. When a phone's camera app scans it, the code is decoded into that
link text, equivalent to passing the link directly to the camera app.

The camera app can recognize it as a link and offer to open it. When the user
taps, the phone's app-link and browser rules determine what opens next.

<details>
<summary>Optional: how the same text behaves in different programs</summary>

Consider the string `C:\Pictures\sample.jpg`. What happens depends on which
program receives it:

| Where You Pass the String | What Happens |
| --- | --- |
| `echo` or PowerShell's `Write-Output` | Prints the path. It does not open the file. |
| An image viewer | Opens that path, reads the file, and displays the image. |
| Notepad's file-open command | Reads the same file as text. A JPEG's binary contents usually look unreadable. |
| A command line inside Remote Desktop | Uses the remote computer's path. The file could be different or missing. |

The string does not become an image or unreadable text. The receiving program
chooses whether to print it, open a file, or do something else.

</details>

## 2. How the Phone Chooses App or Browser

The website and app must both agree on which links the app handles. The
website names the allowed app, and the app names the website it handles.
**Android App Links** and **iOS Universal Links** are the platform features
that establish this two-sided association.

![The website names an allowed app, the app declares the website's host, and the phone's OS checks both declarations.](../docs/face/app-link-association.svg)

The phone checks this association and can open a matching installed app
directly. When the link stays in the browser instead, the website provides
another way to continue.

<details>
<summary>Technical details: how the app and website are associated</summary>

| Platform | The App Declares | The Website Declares |
| --- | --- | --- |
| Android App Links | The HTTPS host and URL patterns it handles, in its manifest. | The allowed package name and signing-certificate fingerprint in `/.well-known/assetlinks.json`. |
| iOS Universal Links | The host in its signed Associated Domains entitlement. | The allowed app identifier and URL paths in `/.well-known/apple-app-site-association` (AASA). |

Android verifies the website's association against the installed app.
iOS verifies its association too, normally obtaining the AASA file through
Apple's content delivery network. **Universal Links do not require registering
each session URL with Apple.** App Clip experiences have additional App Store
Connect configuration, described below.

Both association files are hosted publicly over HTTPS without login or
redirects. Your app also validates the incoming host, path, and parameters
before using them.

This association answers **"Which app may open this link?"** It does not
replace your application's user authorization.

Installed apps can receive verified links directly, so the page may never be
loaded on the phone. However, camera apps, browser policies, user preferences,
and link context affect handoff. Typing a URL into a browser's address bar or
following a same-site link may stay in the browser even when the app is installed.
Test a real tap or scan and keep the fallback page usable.

See Google's [App Links explanation](https://developer.android.com/training/app-links/about)
and Apple's [associated domains explanation](https://developer.apple.com/documentation/xcode/supporting-associated-domains).

</details>

## 3. What the Website Displays

The website's session page is also its **fallback page**: it helps the user
continue when the link opens in the browser instead of the app. On a computer,
the page offers a QR code to scan. On a phone, it offers a button to open the
Android app or an App Clip, a small part of an iOS app that can run without
installing the full app.

![The website selects a browser presentation: a desktop QR containing the HTTPS session URL, an Android Open in app button, or an iPhone Open App Clip button.](../docs/face/website-platform-actions.svg)

The link carries the session ID, not the **Face session token**, a temporary
credential that gives the app permission to run the check.

A webpage can use **JavaScript** (code that runs in the browser) to choose
which view to show based on the browser's operating system (OS), such as
Android or iOS.

The diagrams use `SESSION_URL` for `https://HOST/native?s=SID`. `HOST` is your
backend hostname, such as `liveness.example.com`, and `SID` is the same session
ID throughout. `PACKAGE` is the Android package name; `CLIP` is the App Clip
bundle ID. These names identify the apps to open. Optional browser-return
parameters are omitted.

```mermaid
flowchart TD
    Page["SESSION_URL opens in a browser"] --> Detect{"Page JavaScript:<br/>infer the browser's OS"}
    Detect -->|Desktop or unknown| Desktop["Show a QR code<br/>QR contains SESSION_URL:<br/>https://HOST/native?s=SID"]
    Detect -->|Android| Android["Show an Open in app link<br/>Android intent URL shown below"]
    Detect -->|iOS| Ios["Show an Open App Clip link<br/>Apple invocation URL shown below"]
```

The chart shows a browser-side approach. The
[sample](backend/react/app/_lib/session_landing.ts) instead selects the view
on the server using information sent by the browser (`User-Agent`), and also
displays the QR on mobile. This choice affects what the page shows; the phone
still controls whether a link opens an app.

## 4. Android: From Link to Liveness

Scan the QR code or tap the link on your Android phone. It can open the app
directly, or show a webpage where you tap **Open in app**. If the app needs
to be installed first, the page can take you to Google Play. Install it,
then tap **Open**.

The app is where you complete the liveness check.

<details>
<summary>Technical details: Android routing and link contents</summary>

```mermaid
flowchart TD
    Entry["Android: scan QR or tap SESSION_URL"] -->|Verified App Link; handoff allowed| App["Android app receives SESSION_URL"]
    Entry -->|Browser handles the link| Web["Android browser: load session page<br/>Page JS selects the Android action"]
    Web --> Button["Show Open in app link<br/>intent://HOST/native?s=SID<br/>scheme=https; package=PACKAGE<br/>S.browser_fallback_url=encoded Play URL"]
    Button -->|User taps; app can launch| App
    Button -->|Cannot launch; fallback allowed| Play["Google Play link contains:<br/>id=PACKAGE<br/>referrer=encoded SESSION_URL"]
    Play -->|User installs, then opens app| Referrer["App reads and validates Install Referrer<br/>Recovers SESSION_URL if still valid"]
    Referrer --> App
    App --> Check["Attestation, then liveness"]
```

In supporting browsers such as Chrome, a user tap on the `intent://` link
requests the named app. If the intent cannot be handled, the browser can
use the encoded fallback URL to go to Google Play.

The Play URL also carries a **referrer**: the original session link, encoded
as a query parameter. After installation, the app reads that value through
the Play Install Referrer API and validates it before use. Installation
alone does not automatically start the liveness check.

Example **Open in app** link, using placeholder values:

```text
intent://liveness.example.com/native?s=11111111-2222-4333-8444-555555555555#Intent;scheme=https;package=com.example.liveness;S.browser_fallback_url=https%3A%2F%2Fplay.google.com%2Fstore%2Fapps%2Fdetails%3Fid%3Dcom.example.liveness%26referrer%3Dhttps%253A%252F%252Fliveness.example.com%252Fnative%253Fs%253D11111111-2222-4333-8444-555555555555;end
```

| Highlighted Part | Meaning |
| --- | --- |
| **`intent://`** | Identifies an Android intent link. |
| **`liveness.example.com/native`** | Backend hostname and app-opening path. |
| **`?s=...`** | The same session ID used by the website. |
| **`#Intent;`** and **`;end`** | Mark the start and end of the intent settings. |
| **`scheme=https`** | Makes the app's session URL use HTTPS. |
| **`package=com.example.liveness`** | Names the Android app to open. |
| **`S.browser_fallback_url=...`** | The encoded Google Play fallback URL; `S.` marks a string extra. |

Inside the decoded fallback, **`id=com.example.liveness`** selects the Play
listing and **`referrer=...`** carries the encoded original HTTPS session link.
Encode the session link inside the Play URL, then encode that complete Play
URL inside the intent. Both encoding layers are required.

The app must implement referrer recovery; App Links alone do not provide it.
Consume a recovered link once, reject expired sessions, and never put tokens
or credentials in the referrer. Test installation through Google Play.
Browser launch restrictions still apply, so use a user-tapped button, not a
timer or an assumed automatic redirect. See [Chrome's intent guidance](https://developer.chrome.com/docs/android/intents)
and the [sample's installation recovery](../samples/kotlin/face/AzureVisionLiveness/OVERVIEW.md#resume-after-google-play-installation).

</details>

## 5. iOS: From Link to Liveness

An **App Clip** is a small part of the app that lets you do the check without
installing the full app.

Scan the QR code or tap the link on your iPhone. If the app is installed,
it can open directly. Otherwise, the phone can offer an App Clip card;
tap **Open** to continue.

If you are already on the website in Safari, tap **Open App Clip** to
start the check from that page.

<details>
<summary>Technical details: iOS routing and link contents</summary>

```mermaid
flowchart TD
    Entry["iPhone: scan QR or tap SESSION_URL"] -->|Universal Link; handoff allowed| App["Full iOS app receives the link"]
    Entry -->|Full app absent; matching Clip experience| Card["iOS presents App Clip card"]
    Web["User is already on the website in Safari"] --> Button["Show Open App Clip link<br/>https://appclip.apple.com/id?p=CLIP<br/>&amp;s=SID&amp;domain=HOST"]
    Button -->|User taps; full app installed| App
    Button -->|User taps; full app absent| Card
    Card -->|User taps Open| Clip["App Clip receives invocation URL<br/>Reads SID and validates HOST"]
    App --> Check["Attestation, then liveness"]
    Clip --> Check
```

The chart shows configured launch routes. Links without a recognized app or
App Clip association may still open a webpage.

The QR contains `SESSION_URL`, not an Apple URL. The **Open App Clip** button
instead uses Apple's invocation (launch) URL, which contains the Clip's
bundle ID, the session ID, and the backend host.

iOS passes the invocation URL to the Clip, which reads `s` and uses the allowlisted
`domain` as its backend. `appclip.apple.com` is the launch host, not your backend.
If the full app is installed, it handles the invocation instead and must
support the same flow.

For the Safari button, use the generated **default App Clip link**, which can
carry query parameters. A default link for this example looks like:

```text
https://appclip.apple.com/id?p=com.example.liveness.Clip
```

With a placeholder session ID and backend host, it looks like:

```text
https://appclip.apple.com/id?p=com.example.liveness.Clip&s=11111111-2222-4333-8444-555555555555&domain=liveness.example.com
```

| Highlighted Part | Meaning |
| --- | --- |
| **`https://appclip.apple.com/id`** | Apple's HTTPS App Clip launch endpoint, not your backend. |
| **`?p=com.example.liveness.Clip`** | Identifies the App Clip by its bundle ID. |
| **`&s=...`** | The same session ID used by the website. |
| **`&domain=liveness.example.com`** | The backend hostname the Clip validates before use. |

You build and publish the Clip with its parent app and configure its launch
experience in App Store Connect. An ordinary Universal Link does not
automatically download an App Clip.

Public links require a released, approved App Clip experience. Demo links
cannot carry these session parameters. A custom-domain QR invocation also
requires the matching website association and App Clip experience; creating
the URL string alone does not configure any of this. See Apple's
[App Clip experiences guide](https://developer.apple.com/documentation/appclip/configuring-the-launch-experience-of-your-app-clip)
and the [App Clip integration steps](README.md#52-use-an-app-clip).

</details>

## 6. After Handoff: Attestation and Liveness

**Device attestation** helps check that the phone's software has not been
tampered with and that the app is genuine and unmodified. These checks use
security evidence from Android or iOS and depend on the platform and your
backend's security policy.

After those checks pass, the **attestation libraries** (reusable code in your
app and backend) use the verified app identity to establish an **encrypted
session between the app and backend**.

The encrypted messages carry the Face session token and a **digest**, a
fingerprint of the data submitted for the liveness check. Other apps or
anyone intercepting these messages cannot read them without the required
encryption keys. This encryption protects the token and digest messages
exchanged by the attestation libraries; it does not change how the app's
other traffic is handled.

![The backend first checks the app's platform evidence. It then sends an encrypted session token to the app, which returns an encrypted digest after liveness through the same attested session.](../docs/face/attestation-encrypted-session.svg)

The flow has three stages:

| Stage | What Happens |
| --- | --- |
| **1. Attest** | The backend checks the app and phone, then releases the encrypted Face token. |
| **2. Run liveness** | The Face UI runs the check. The app sends its digest (a fingerprint of the submitted data) through the same attestation session. |
| **3. Check the result** | The backend retrieves the Face result for that session and requires a non-empty, matching digest before using the liveness decision. |

Together, these checks link the Face result to the verified app's submission.
Your backend uses the result's liveness decision to determine whether the
check passed. Your flow can then continue in the app or return the user to
the browser.

See the [full session pipeline](README.md#session-pipeline) for the detailed
sequence.

## Next: Integrate It

Start with the [integration roadmap](README.md#integration-roadmap) for library
setup, platform configuration, and verification. The sample's
[landing-page implementation](backend/react/app/_lib/session_landing.ts) shows
how it builds the QR URL and platform buttons; the [backend overview](OVERVIEW.md)
covers architecture and configuration in more depth.