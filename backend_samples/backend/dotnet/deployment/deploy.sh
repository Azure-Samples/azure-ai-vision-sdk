#!/usr/bin/env bash
# Provisions infrastructure (Bicep) then builds + zip-deploys the .NET app.
#
#   ./deploy.sh eastus
set -euo pipefail
LOCATION="${1:-eastus}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
PARAMS="$SCRIPT_DIR/main.parameters.json"

echo "Deploying infrastructure..."
OUTPUTS=$(az deployment sub create \
    --location "$LOCATION" \
    --template-file "$SCRIPT_DIR/bicep/main.bicep" \
    --parameters "@$PARAMS" \
    --query properties.outputs -o json)
WEBAPP=$(echo "$OUTPUTS" | jq -r .webAppName.value)
RESOURCE_GROUP=$(echo "$OUTPUTS" | jq -r .resourceGroupName.value)
WEBAPP_URL=$(echo "$OUTPUTS" | jq -r .webAppUrl.value)

echo "Publishing app..."
PUBLISH_DIR="$APP_DIR/publish"
rm -rf "$PUBLISH_DIR"
dotnet publish "$APP_DIR/FaceLivenessAttestationBackendSample.csproj" -c Release -o "$PUBLISH_DIR"

echo "Packaging + deploying to $WEBAPP..."
ZIP="$APP_DIR/publish.zip"
rm -f "$ZIP"
(cd "$PUBLISH_DIR" && zip -qr "$ZIP" .)
az webapp deploy --resource-group "$RESOURCE_GROUP" --name "$WEBAPP" --src-path "$ZIP" --type zip

echo "Deployed: $WEBAPP_URL"
