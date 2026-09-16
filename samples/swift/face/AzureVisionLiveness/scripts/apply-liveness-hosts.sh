#!/usr/bin/env bash
#
# apply-liveness-hosts.sh
#
# Configures the AzureVisionLiveness sample (full app + App Clip) for one or more
# liveness backend domains. This is the single, self-contained place that wires
# the domains into the project — run it once with your backend host(s) before
# building/archiving in Xcode. It does NOT depend on the CI pipeline (CI simply
# invokes this same script).
#
# It updates, in this project only:
#   - AzureVisionLiveness.xcconfig
#         LIVENESS_HOST = <primary (first) host>
#         (drives the single LivenessHost key + the "Verify LIVENESS_HOST" phase)
#   - FaceAnalyzerSample/FaceAnalyzerSample.entitlements
#         com.apple.developer.associated-domains -> applinks:<host> per host
#   - FaceAnalyzerSampleAppClip/FaceAnalyzerSampleAppClip.entitlements
#         com.apple.developer.associated-domains -> appclips:<host> + applinks:<host> per host
#   - FaceAnalyzerSample/Info.plist and FaceAnalyzerSampleAppClip/Info.plist
#         LivenessHosts array (runtime whitelist read by allowedLivenessHosts())
#
# The command is idempotent — re-run it any time to change the host list.
#
# Usage:
#   ./scripts/apply-liveness-hosts.sh liveness.example.com
#   ./scripts/apply-liveness-hosts.sh a.example.com b.example.com c.example.com
#   ./scripts/apply-liveness-hosts.sh "a.example.com,b.example.com"
#   LIVENESS_HOSTS="a.example.com,b.example.com" ./scripts/apply-liveness-hosts.sh
#
# Requires macOS (uses /usr/libexec/PlistBuddy).
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]:-$0}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

PLIST_BUDDY="/usr/libexec/PlistBuddy"
XCCONFIG="$PROJECT_DIR/AzureVisionLiveness.xcconfig"
APP_ENTITLEMENTS="$PROJECT_DIR/FaceAnalyzerSample/FaceAnalyzerSample.entitlements"
CLIP_ENTITLEMENTS="$PROJECT_DIR/FaceAnalyzerSampleAppClip/FaceAnalyzerSampleAppClip.entitlements"
APP_INFO_PLIST="$PROJECT_DIR/FaceAnalyzerSample/Info.plist"
CLIP_INFO_PLIST="$PROJECT_DIR/FaceAnalyzerSampleAppClip/Info.plist"

# ---- Collect hosts from CLI args (space and/or comma separated) or the
#      LIVENESS_HOSTS environment variable.
raw="${*:-${LIVENESS_HOSTS:-}}"
if [[ -z "${raw//[[:space:],]/}" ]]; then
    echo "error: no liveness host(s) provided." >&2
    echo "usage: $0 <host> [host ...]   (or LIVENESS_HOSTS=\"a.com,b.com\" $0)" >&2
    exit 1
fi

# Split on commas and whitespace into an array (no globbing), trim empties and
# validate each host looks like a bare domain (defends PlistBuddy/sed input).
IFS=', ' read -r -a raw_tokens <<< "$raw"
hosts=()
for h in "${raw_tokens[@]}"; do
    [[ -z "$h" ]] && continue
    if [[ ! "$h" =~ ^[A-Za-z0-9.-]+$ ]]; then
        echo "error: invalid host '$h' (expected a bare domain, e.g. liveness.example.com)." >&2
        exit 1
    fi
    if [[ "$h" == "example.com" ]]; then
        echo "error: 'example.com' is the placeholder host; pass your real liveness backend domain(s)." >&2
        exit 1
    fi
    hosts+=("$h")
done

if [[ ${#hosts[@]} -eq 0 ]]; then
    echo "error: no valid hosts after parsing '$raw'." >&2
    exit 1
fi
primary="${hosts[0]}"

# ---- Sanity-check the environment and project files.
if [[ ! -x "$PLIST_BUDDY" ]]; then
    echo "error: PlistBuddy not found at $PLIST_BUDDY — this script must run on macOS." >&2
    exit 1
fi
for f in "$XCCONFIG" "$APP_ENTITLEMENTS" "$CLIP_ENTITLEMENTS" "$APP_INFO_PLIST" "$CLIP_INFO_PLIST"; do
    if [[ ! -f "$f" ]]; then
        echo "error: expected project file not found: $f" >&2
        exit 1
    fi
done

# ---- 1. xcconfig: set the primary host. This drives the single LivenessHost
#         Info.plist key ($(LIVENESS_HOST)) and satisfies the "Verify
#         LIVENESS_HOST" build phase, so no build-setting override is needed.
tmp="$(mktemp)"
sed "s|^LIVENESS_HOST = .*|LIVENESS_HOST = ${primary}|" "$XCCONFIG" > "$tmp"
mv "$tmp" "$XCCONFIG"

# ---- 2. entitlements: rewrite com.apple.developer.associated-domains with one
#         entry per host. The full app gets applinks:; the App Clip gets both
#         appclips: and applinks:. Other entitlement keys are left untouched.
apply_associated_domains() {
    local file="$1"
    local kind="$2"   # "app" (applinks only) or "clip" (appclips + applinks)
    "$PLIST_BUDDY" -c "Delete :com.apple.developer.associated-domains" "$file" 2>/dev/null || true
    "$PLIST_BUDDY" -c "Add :com.apple.developer.associated-domains array" "$file"
    local i=0
    for h in "${hosts[@]}"; do
        if [[ "$kind" == "clip" ]]; then
            "$PLIST_BUDDY" -c "Add :com.apple.developer.associated-domains:$i string appclips:$h" "$file"
            i=$((i + 1))
        fi
        "$PLIST_BUDDY" -c "Add :com.apple.developer.associated-domains:$i string applinks:$h" "$file"
        i=$((i + 1))
    done
}
apply_associated_domains "$APP_ENTITLEMENTS" "app"
apply_associated_domains "$CLIP_ENTITLEMENTS" "clip"

# ---- 3. Info.plist: rewrite the LivenessHosts whitelist array (both targets).
apply_liveness_hosts_array() {
    local file="$1"
    "$PLIST_BUDDY" -c "Delete :LivenessHosts" "$file" 2>/dev/null || true
    "$PLIST_BUDDY" -c "Add :LivenessHosts array" "$file"
    local i=0
    for h in "${hosts[@]}"; do
        "$PLIST_BUDDY" -c "Add :LivenessHosts:$i string $h" "$file"
        i=$((i + 1))
    done
}
apply_liveness_hosts_array "$APP_INFO_PLIST"
apply_liveness_hosts_array "$CLIP_INFO_PLIST"

echo "Applied ${#hosts[@]} liveness host(s): ${hosts[*]}"
echo "  primary (LIVENESS_HOST): $primary"
echo "Updated: AzureVisionLiveness.xcconfig, app + App Clip entitlements, app + App Clip Info.plist."
