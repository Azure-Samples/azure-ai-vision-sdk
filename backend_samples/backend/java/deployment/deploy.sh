#!/usr/bin/env bash
#
# Full deployment: build the jar, provision infrastructure (App Service + Azure
# Managed Redis) via Bicep, then jar-deploy the app.
#
# Usage:
#   ./deployment/deploy.sh <subscriptionId> [env] [location] [locationabbr]
set -euo pipefail

SUBSCRIPTION_ID="${1:?Usage: deploy.sh <subscriptionId> [env] [location] [locationabbr]}"
ENV="${2:-dev}"
LOCATION="${3:-centralus}"
LOCATION_ABBR="${4:-cus}"
OWNER="${OWNER:-${USER:-owner}}"

SAMPLE_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$SAMPLE_ROOT"

az account set --subscription "$SUBSCRIPTION_ID"

echo "Building the jar..."
mvn -B -DskipTests -f ../pom.xml package

echo "Provisioning infrastructure..."
OUTPUTS=$(az deployment sub create \
  --location "$LOCATION" \
  --template-file deployment/bicep/main.bicep \
  --parameters deployment/main.parameters.json \
    env="$ENV" location="$LOCATION" locationabbr="$LOCATION_ABBR" owner="$OWNER")

APP=$(echo "$OUTPUTS" | python -c "import sys,json;print(json.load(sys.stdin)['properties']['outputs']['webAppName']['value'])")
RG=$(echo "$OUTPUTS" | python -c "import sys,json;print(json.load(sys.stdin)['properties']['outputs']['resourceGroupName']['value'])")
URL=$(echo "$OUTPUTS" | python -c "import sys,json;print(json.load(sys.stdin)['properties']['outputs']['webAppUrl']['value'])")

echo "Deploying jar to $APP..."
az webapp deploy --resource-group "$RG" --name "$APP" --type jar \
  --src-path target/face-liveness-attestation-backend-sample.jar

echo "Deployed: $URL"
