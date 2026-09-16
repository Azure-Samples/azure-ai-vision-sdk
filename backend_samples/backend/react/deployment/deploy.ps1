<#
.SYNOPSIS
  Provision Azure infrastructure (Linux Node App Service + Managed Redis) with
  Bicep, then build + zip-deploy the Next.js standalone app and print the public URL.

.EXAMPLE
  ./deploy.ps1 -Env dev -Location eastus -LocationAbbr eus `
      -IosAppId "SGGM6D27TK.com.microsoft.azurevisionliveness" `
      -AndroidPackageName "com.microsoft.azurevisionliveness" `
      -AndroidSha256Fingerprints "AA:BB:...:FF"
#>
[CmdletBinding()]
param(
  [string]$Env = "dev",
  [string]$Location = "eastus",
  [string]$LocationAbbr = "eus",
  [string]$SubscriptionId = "",
  [string]$IosAppId = "",
  [string]$IosAppClipId = "",
  [string]$AndroidPackageName = "",
  [string]$AndroidSha256Fingerprints = "",
  [string]$IosAppStoreUrl = "",
  [string]$AndroidPlayStoreUrl = "",
  [string]$Owner = ""
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot
$bicep = Join-Path $PSScriptRoot "bicep\main.bicep"

if ($SubscriptionId) { az account set --subscription $SubscriptionId | Out-Null }

if (-not $Owner) { $Owner = az account show --query user.name -o tsv }

Write-Host "==> Deploying infrastructure (env=$Env, location=$Location)..." -ForegroundColor Cyan
$deployName = "liveness-react-$Env-$(Get-Date -Format 'yyyyMMddHHmmss')"
$outputs = az deployment sub create `
  --name $deployName `
  --location $Location `
  --template-file $bicep `
  --parameters `
    env=$Env location=$Location locationabbr=$LocationAbbr `
    iosAppId=$IosAppId iosAppClipId=$IosAppClipId `
    androidPackageName=$AndroidPackageName `
    androidSha256Fingerprints=$AndroidSha256Fingerprints `
    iosAppStoreUrl=$IosAppStoreUrl androidPlayStoreUrl=$AndroidPlayStoreUrl `
    owner=$Owner `
  --query properties.outputs -o json | ConvertFrom-Json

$webAppName = $outputs.webAppName.value
$webAppUrl  = $outputs.webAppUrl.value
$webAppHost = $outputs.webAppHost.value
$rg         = $outputs.resourceGroupName.value
Write-Host "    App Service: $webAppName  (rg: $rg)" -ForegroundColor Green

Write-Host "==> Building app..." -ForegroundColor Cyan
Push-Location $repoRoot
try {
  npm ci
  npm run build
} finally {
  Pop-Location
}

Write-Host "==> Packaging Next.js standalone bundle..." -ForegroundColor Cyan
# The standalone server needs the static assets (and public/ if present) copied in.
$standalone = Join-Path $repoRoot ".next\standalone"
Copy-Item (Join-Path $repoRoot ".next\static") (Join-Path $standalone ".next\static") -Recurse -Force
$publicDir = Join-Path $repoRoot "public"
if (Test-Path $publicDir) { Copy-Item $publicDir (Join-Path $standalone "public") -Recurse -Force }

$zipPath = Join-Path $env:TEMP "liveness-react-app.zip"
if (Test-Path $zipPath) { Remove-Item $zipPath -Force }
Compress-Archive -Path (Join-Path $standalone "*") -DestinationPath $zipPath -Force

Write-Host "==> Zip-deploying to $webAppName..." -ForegroundColor Cyan
az webapp deploy --resource-group $rg --name $webAppName --src-path $zipPath --type zip | Out-Null

Write-Host ""
Write-Host "Deployed: $webAppUrl" -ForegroundColor Green
Write-Host "  AASA:       $webAppUrl/.well-known/apple-app-site-association"
Write-Host "  assetlinks: $webAppUrl/.well-known/assetlinks.json"
Write-Host "  health:     $webAppUrl/healthz"
Write-Host ""
Write-Host "Next: bind the mobile samples to this host:" -ForegroundColor Yellow
Write-Host "  ../scripts/set-domain.ps1 -AppHost $webAppHost"
