#!/usr/bin/env bash
#
# Provision Azure infrastructure (Linux Python App Service + Redis) with Bicep,
# then zip-deploy the FastAPI app and print the resulting public URL.
#
# Example:
#   ./deploy.sh --env dev --location eastus --location-abbr eus \
#       --ios-app-id "SGGM6D27TK.com.microsoft.azurevisionliveness" \
#       --android-package-name "com.microsoft.azurevisionliveness" \
#       --android-sha256 "AA:BB:...:FF"
set -euo pipefail

ENV="dev"; LOCATION="eastus"; LOCATION_ABBR="eus"; SUBSCRIPTION=""
IOS_APP_ID=""; IOS_APP_CLIP_ID=""; ANDROID_PACKAGE_NAME=""
ANDROID_SHA256=""; IOS_STORE_URL=""; ANDROID_STORE_URL=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --env) ENV="$2"; shift 2;;
    --location) LOCATION="$2"; shift 2;;
    --location-abbr) LOCATION_ABBR="$2"; shift 2;;
    --subscription) SUBSCRIPTION="$2"; shift 2;;
    --ios-app-id) IOS_APP_ID="$2"; shift 2;;
    --ios-app-clip-id) IOS_APP_CLIP_ID="$2"; shift 2;;
    --android-package-name) ANDROID_PACKAGE_NAME="$2"; shift 2;;
    --android-sha256) ANDROID_SHA256="$2"; shift 2;;
    --ios-store-url) IOS_STORE_URL="$2"; shift 2;;
    --android-store-url) ANDROID_STORE_URL="$2"; shift 2;;
    *) echo "Unknown arg: $1" >&2; exit 1;;
  esac
done

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(dirname "$SCRIPT_DIR")"
BICEP="$SCRIPT_DIR/bicep/main.bicep"

[[ -n "$SUBSCRIPTION" ]] && az account set --subscription "$SUBSCRIPTION"

echo "==> Deploying infrastructure (env=$ENV, location=$LOCATION)..."
DEPLOY_NAME="liveness-py-$ENV-$(date +%Y%m%d%H%M%S)"
OUTPUTS=$(az deployment sub create \
  --name "$DEPLOY_NAME" \
  --location "$LOCATION" \
  --template-file "$BICEP" \
  --parameters \
    env="$ENV" location="$LOCATION" locationabbr="$LOCATION_ABBR" \
    iosAppId="$IOS_APP_ID" iosAppClipId="$IOS_APP_CLIP_ID" \
    androidPackageName="$ANDROID_PACKAGE_NAME" \
    androidSha256Fingerprints="$ANDROID_SHA256" \
    iosAppStoreUrl="$IOS_STORE_URL" androidPlayStoreUrl="$ANDROID_STORE_URL" \
  --query properties.outputs -o json)

WEBAPP_NAME=$(echo "$OUTPUTS" | python3 -c "import sys,json;print(json.load(sys.stdin)['webAppName']['value'])")
WEBAPP_URL=$(echo "$OUTPUTS" | python3 -c "import sys,json;print(json.load(sys.stdin)['webAppUrl']['value'])")
RG=$(echo "$OUTPUTS" | python3 -c "import sys,json;print(json.load(sys.stdin)['resourceGroupName']['value'])")
echo "    App Service: $WEBAPP_NAME  (rg: $RG)"

echo "==> Packaging app..."
ZIP="$(mktemp -d)/liveness-py-app.zip"
STAGE="$(mktemp -d)"
cp -r "$REPO_ROOT/app" "$STAGE/app"
# Vendor the in-repo attestation library and point requirements at it (the
# editable path in requirements.txt only resolves in the local checkout).
LIB_SRC="$REPO_ROOT/../../library/python/AzureAIVisionFaceDeviceAttestation"
VENDOR="$STAGE/vendor/azure-ai-vision-face-deviceattestation"
mkdir -p "$VENDOR"
cp -r "$LIB_SRC/azure_ai_vision_face_deviceattestation" "$VENDOR/"
cp "$LIB_SRC/pyproject.toml" "$LIB_SRC/README.md" "$VENDOR/"
awk '{ sub(/\r$/, ""); if ($0 == "-e ../../library/python/AzureAIVisionFaceDeviceAttestation") { print "./vendor/azure-ai-vision-face-deviceattestation"; next } print }' \
  "$REPO_ROOT/requirements.txt" > "$STAGE/requirements.txt"
find "$STAGE" -type d -name __pycache__ -prune -exec rm -rf {} +
( cd "$STAGE" && zip -r -q "$ZIP" . )

echo "==> Zip-deploying to $WEBAPP_NAME..."
az webapp deploy --resource-group "$RG" --name "$WEBAPP_NAME" --src-path "$ZIP" --type zip >/dev/null

echo ""
echo "Deployed: $WEBAPP_URL"
echo "  AASA:       $WEBAPP_URL/.well-known/apple-app-site-association"
echo "  assetlinks: $WEBAPP_URL/.well-known/assetlinks.json"
echo "  health:     $WEBAPP_URL/healthz"
