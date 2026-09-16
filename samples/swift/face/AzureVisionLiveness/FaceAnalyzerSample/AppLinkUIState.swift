//
// Copyright (c) Microsoft. All rights reserved.
//

import Foundation
import SwiftUI

/// Drives the "page revealed" animation at app launch — toggled true once the
/// initial setup work finishes.
class PageReveal: ObservableObject {
    @Published var revealed: Bool = false
}

/// Tracks whether the current session was started by a Universal Link URL
/// (the QuickLink / cert-based flow). Set to true inside `processUniversalLink` and
/// cleared by the manual-entry buttons in `LaunchView`. Lives outside
/// `SessionData` because `SessionData` is defined in public_samples and we
/// don't want to fork that file. ObservableObject + @ObservedObject is the
/// SwiftUI-correct way to read this from a view; non-view code can read /
/// write `.isFromAppLink` directly on the shared instance.
@MainActor
class AppLinkState: ObservableObject {
    static let shared = AppLinkState()
    @Published var isFromAppLink: Bool = false
    private init() {}
}

/// Drives the full-screen progress overlay shown while `processUniversalLink` runs
/// the attestation + token chain (5-10s, sometimes longer). The overlay
/// itself is a ViewModifier (see `LinkAuthProgressOverlay`) applied at the
/// app root; this object holds the active flag and current stage label.
@MainActor
class LinkAuthProgress: ObservableObject {
    static let shared = LinkAuthProgress()
    @Published var isActive: Bool = false
    @Published var stage: String = "Preparing your session…"
    private init() {}
}
