//
// Copyright (c) Microsoft. All rights reserved.
//

import Foundation
import UIKit
import os
import AzureAIVisionFaceDeviceAttestation

private let authLog = Logger(subsystem: "com.microsoft.azurevisionliveness", category: "auth")

/// Reads the liveness backend host from the `LivenessHost` Info.plist key,
/// which is populated from the `LIVENESS_HOST` build setting in
/// AzureVisionLiveness.xcconfig — the same setting referenced by the
/// entitlements files (associated-domains).
///
/// Returns the bare host (e.g. `example.com`); this app-side helper seeds
/// `DeviceAttestation.initialize(livenessHost:)`, which keeps the attestation
/// library itself free of Info.plist / build-time coupling.
func livenessHostFromInfoPlist() -> String {
    guard let host = Bundle.main.object(forInfoDictionaryKey: "LivenessHost") as? String,
          !host.isEmpty,
          host != "$(LIVENESS_HOST)" else {
        fatalError("LivenessHost is not set. Define LIVENESS_HOST in AzureVisionLiveness.xcconfig and ensure the target's Info.plist contains a LivenessHost key set to $(LIVENESS_HOST).")
    }
    return host
}

/// The set of liveness backend hosts this build is allowed to talk to, read from
/// the `LivenessHosts` array in Info.plist (populated at build time from the
/// `LIVENESS_HOST(S)` list — see AzureVisionLiveness.xcconfig and
/// scripts/archive-FaceAnalyzerApps-iOS.sh). Falls back to the single
/// `LivenessHost` key for single-domain builds where the array is absent.
///
/// This is the whitelist used to validate the host taken from an inbound link
/// (full app) or the `domain` query parameter (App Clip) before the app trusts
/// it as an attestation backend.
func allowedLivenessHosts() -> [String] {
    if let hosts = Bundle.main.object(forInfoDictionaryKey: "LivenessHosts") as? [String] {
        let cleaned = hosts
            .map { $0.trimmingCharacters(in: .whitespaces) }
            .filter { !$0.isEmpty && $0 != "$(LIVENESS_HOST)" }
        if !cleaned.isEmpty {
            return cleaned
        }
    }
    return [livenessHostFromInfoPlist()]
}

/// Returns the canonical whitelist entry matching `host` (case-insensitive), or
/// nil when `host` is empty or not one of `allowedLivenessHosts()`. Used to
/// validate the per-session host (inbound link host / App Clip `domain`) before
/// it is trusted as the attestation backend.
func resolvedAllowedHost(_ host: String?) -> String? {
    guard let host, !host.isEmpty else { return nil }
    return allowedLivenessHosts().first { $0.caseInsensitiveCompare(host) == .orderedSame }
}

/// Validates a `callbackUrl` before it is handed to `openURL` at the end of the
/// flow. The value comes from the inbound Universal Link / App Clip URL and is
/// therefore attacker-controllable, so it is only safe to follow when it targets
/// this session's liveness host over https — the backend only ever points
/// `callbackUrl` at its own `/result` page on that host. Anything else is
/// treated as an open-redirect attempt and dropped.
///
/// `allowedHost` is the host resolved for this session (from the inbound link
/// host or the App Clip `domain` parameter), already validated against
/// `allowedLivenessHosts()`. Passing it in keeps the callback strictly
/// same-origin with the backend that issued the session.
///
/// Returns the parsed `URL` when it passes the same-origin check, or nil to
/// skip the redirect.
func sanitizedCallbackURL(_ urlString: String?, allowedHost: String) -> URL? {
    guard !allowedHost.isEmpty,
          let urlString,
          let url = URL(string: urlString),
          url.scheme?.lowercased() == "https",
          let host = url.host,
          host.caseInsensitiveCompare(allowedHost) == .orderedSame else {
        return nil
    }
    return url
}

/// Holds the small bag of values parsed out of a Universal Link / App Clip URL.
private struct UniversalLinkParams {
    let sParam: String
    let callbackUrl: String?
    /// Optional backend host carried as the `domain` query parameter. The App
    /// Clip is launched via the App Store / App Clip URL (whose host is
    /// apps.apple.com, not the backend), so it cannot derive the backend host
    /// from `url.host` — the backend passes it explicitly here. nil for the full
    /// app, which uses the inbound link's own host instead.
    let domain: String?
}

/// Entry point for the cert-based QuickLink auth flow, invoked when the app
/// opens via a Universal Link URL.
///
/// This is a thin UI wrapper: it owns URL parsing, the progress overlay, and
/// navigation, and delegates the crypto / network flow to `DeviceAttestation`.
///
/// Phases:
///   1. Parse session params from the URL + resolve/validate the backend host
///   2. Configure `DeviceAttestation` with the resolved (whitelisted) host
///   3. Start a session — runs challenge → verify → register as needed and
///      returns an `AttestationSession`
///   4. Fetch a session token from the session
///   5. Push the user into the liveness page
///
/// The progress overlay is driven via `LinkAuthProgress.shared.stage`; failures
/// are surfaced through `errorState.show`.
func processUniversalLink(url: URL, sessionData: SessionData, pageSelection: PageSelection, errorState: ErrorState) {
    Task {
        // 1. Parse URL params (sParam, callbackUrl, domain) and resolve the
        // backend host for this session, validating it against the built-in
        // whitelist. The full app uses the inbound link's own host (the OS only
        // delivers links from associated domains); the App Clip is launched via
        // the App Store / App Clip URL — whose host is NOT the backend — so the
        // backend passes the target host as the `domain` query parameter. The
        // value is attacker-controllable either way, so it is only trusted when
        // it matches allowedLivenessHosts().
        guard let params = parseUniversalLinkParams(from: url) else { return }
        guard let livenessHost = resolvedAllowedHost(params.domain ?? url.host) else {
            await MainActor.run {
                LinkAuthProgress.shared.isActive = false
                errorState.show("This link points at an unrecognized liveness host.")
            }
            return
        }

        // Show progress overlay immediately so the user has feedback during
        // the ~5-10s attestation + token acquisition chain.
        await MainActor.run {
            LinkAuthProgress.shared.stage = "Establishing secure session…"
            LinkAuthProgress.shared.isActive = true

            sessionData.callbackUrl = params.callbackUrl
            sessionData.livenessHost = livenessHost
        }

        // 2. Resolve clientId + deviceUUID.
        let clientId = await MainActor.run {
            sessionData.deviceCorrelationIdInClient ??
            UIDevice.current.identifierForVendor?.uuidString ??
            UUID().uuidString
        }
        let deviceUUID = UIDevice.current.identifierForVendor?.uuidString ?? UUID().uuidString

        // 3. Configure the attestation library with the resolved (whitelisted) host.
        await DeviceAttestation.shared.initialize(livenessHost: livenessHost)

        // 4. Run the attestation flow (challenge → verify → register as needed).
        await MainActor.run {
            LinkAuthProgress.shared.stage = "Verifying device…"
        }

        let session: AttestationSession
        switch await DeviceAttestation.shared.startSession(
            sessionId: params.sParam,
            clientId: clientId,
            deviceUUID: deviceUUID
        ) {
        case .success(let started):
            session = started
        case .error(let code, let message):
            authLog.error("Attestation error \(code, privacy: .public): \(message, privacy: .public)")
            await MainActor.run {
                LinkAuthProgress.shared.isActive = false
                errorState.show("Couldn't start your session (\(code)). \(message)")
            }
            return
        case .exception(let error):
            authLog.error("Attestation failed: \(String(describing: error), privacy: .public)")
            await MainActor.run {
                LinkAuthProgress.shared.isActive = false
                errorState.show("Couldn't start your session. \(String(describing: error))")
            }
            return
        }

        // 5. Session token.
        await MainActor.run {
            LinkAuthProgress.shared.stage = "Fetching session token…"
        }

        let token: String
        switch await session.fetchSessionToken() {
        case .success(let fetched):
            token = fetched
        case .error(let code, let message):
            authLog.error("Session token error \(code, privacy: .public): \(message, privacy: .public)")
            await MainActor.run {
                LinkAuthProgress.shared.isActive = false
                errorState.show("Couldn't start your session (\(code)). \(message)")
            }
            return
        case .exception(let error):
            authLog.error("Session token failed: \(String(describing: error), privacy: .public)")
            await MainActor.run {
                LinkAuthProgress.shared.isActive = false
                errorState.show("Couldn't start your session. \(String(describing: error))")
            }
            return
        }

        // 6. Hand the token to SessionData and navigate.
        await MainActor.run {
            sessionData.token = token
            sessionData.sessionId = params.sParam
            if sessionData.deviceCorrelationIdInClient?.isEmpty ?? true {
                sessionData.deviceCorrelationIdInClient = clientId
            }
            AppLinkState.shared.isFromAppLink = true
            LinkAuthProgress.shared.isActive = false
            pageSelection.current = .liveness
        }
    }
}

// MARK: - Step helpers

/// Returns the URL params we care about — `sParam` is required; `callbackUrl`
/// and `domain` are optional and URL-decoded.
private func parseUniversalLinkParams(from url: URL) -> UniversalLinkParams? {
    guard let components = URLComponents(url: url, resolvingAgainstBaseURL: true),
          let queryItems = components.queryItems,
          let sParam = queryItems.first(where: { $0.name == "s" })?.value else {
        return nil
    }
    let callbackUrl = queryItems.first(where: { $0.name == "callbackUrl" })?.value?.removingPercentEncoding
    let domain = queryItems.first(where: { $0.name == "domain" })?.value?.removingPercentEncoding
    return UniversalLinkParams(sParam: sParam, callbackUrl: callbackUrl, domain: domain)
}
