# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Repository Overview

This is the **Azure AI Vision SDK Samples** repository — a collection of sample applications demonstrating Azure AI Face Liveness Detection across multiple platforms. The SDK itself is distributed as pre-built packages (Maven, npm, CocoaPods/SPM); this repo contains only sample/integration code.

Two core features are demonstrated:
- **Liveness** — detects if a face is real vs. spoofed (photo, mask, video)
- **LivenessWithVerify** — liveness detection combined with 1:1 face verification

## Architecture

All samples follow the same integration pattern:

1. **Backend** generates a session token by calling the Azure Face API
2. **Client** initializes the pre-built Face Liveness Detector UI component with the token
3. **SDK component** handles camera, liveness checks, and communicates with Azure
4. **Client** retrieves results via backend (session result + optional verification)

The SDK UI component is a black box — samples integrate it, they don't implement liveness detection logic.

## Platform Samples

| Path | Platform | Build System |
|------|----------|-------------|
| `samples/kotlin/face/FaceLivenessDetectorSample/` | Android | Gradle (Kotlin DSL) |
| `samples/kotlin/face/FaceLivenessDetectorFeatureSample/` | Android (dynamic feature) | Gradle (Kotlin DSL) |
| `samples/swift/face/FaceAnalyzerSample/` | iOS | Xcode + SPM |
| `samples/web/nextjs/` | Web | npm + Next.js 14 |
| `samples/web/angularjs/` | Web | npm + Angular |
| `samples/web/vuejs/` | Web | npm + Vue.js |
| `samples/web/javascript/` | Web | Vanilla JS |
| `samples/flutter/face/FlutterLivenessSample/` | Android + iOS | Flutter + Gradle/CocoaPods |
| `samples/react_native/face/RNLivenessSample/` | Android + iOS | React Native + Gradle/CocoaPods |
| `samples/maui/` | Android + iOS | .NET MAUI (.NET 9+) |

Legacy `samples/{python,java,cpp,csharp}/image-analysis/` directories have been moved to other Azure SDK repos.

## Build Commands

### Android (Kotlin)
```bash
# Open in Android Studio, then:
# Build: Ctrl+F9 or Build > Make Project
# Run:   Shift+F10 or Run > Run 'app'
# Requires: Android Studio, Gradle 8.3+, device API 24+
```
Maven credentials must be set in `gradle.properties`:
```properties
mavenUser=any_username_string
mavenPassword=<access_token>
```

### iOS (Swift)
```bash
xcodebuild -scmProvider system -resolvePackageDependencies
# Then open .xcodeproj in Xcode, select device, build and run
# Requires: Xcode 13+, iOS 14+ device, Git LFS
```
Git PAT must be configured for `https://msface.visualstudio.com`.

### Web (Next.js)
```bash
cd samples/web/nextjs
npm install
npm run dev
```
Requires `.npmrc` with Azure DevOps registry token and `.env.local` with `FACE_API_ENDPOINT` and `FACE_API_KEY`.

### Flutter
```bash
cd samples/flutter/face/FlutterLivenessSample
flutter pub get
# Android: open android/ in Android Studio, run
# iOS: cd ios && pod install, open in Xcode, run
```

### React Native
```bash
cd samples/react_native/face/RNLivenessSample
# Android: open android/ in Android Studio, run
# iOS: cd ios && pod install, open in Xcode, run
```

### .NET MAUI
```bash
# Open FaceAnalyzerSample.sln in VS Code
# Configure FaceAPI.Config.cs with endpoint/key
# Select device target, build and run
# Requires: .NET 9+
```

## SDK Package Sources

All SDK artifacts require **Limited Access Features (LAF)** approval. Apply at [Face Recognition Limited Access](https://customervoice.microsoft.com/Pages/ResponsePage.aspx?id=v4j5cvGGr0GRqy180BHbR7en2Ais5pxKtso_Pz4b1_xUQjA5SkYzNDM4TkcwQzNEOE1NVEdKUUlRRCQlQCN0PWcu).

- **Android Maven:** `https://pkgs.dev.azure.com/msface/SDK/_packaging/AzureAIVision/maven/v1`
- **Web npm:** `https://pkgs.dev.azure.com/msface/SDK/_packaging/AzureAIVision/npm/registry/`
- **iOS SPM/CocoaPods:** `https://msface.visualstudio.com/SDK/_git/AzureAIVisionFaceUI.podspec`

## Key Dependencies

- **Android:** `com.azure:azure-ai-vision-face-ui`, Jetpack Compose, OkHttp
- **iOS:** `AzureAIVisionFaceUI` (via SPM or CocoaPods), Git LFS for binaries
- **Web:** `@azure/ai-vision-face-ui` (npm), Azure Face REST API v1.3-preview.1

## Contributing

- Fork the repo, make changes in a branch, submit a PR
- CLA signing required (automated via bot)
- Microsoft Open Source Code of Conduct applies
- File issues at: https://github.com/Azure-Samples/azure-ai-vision-sdk/issues
