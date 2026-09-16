<#
.SYNOPSIS
  Provision Azure infrastructure (Linux Python App Service + Redis) with Bicep,
  then zip-deploy the FastAPI app and print the resulting public URL.

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
$deployName = "liveness-py-$Env-$(Get-Date -Format 'yyyyMMddHHmmss')"
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
$rg         = $outputs.resourceGroupName.value
Write-Host "    App Service: $webAppName  (rg: $rg)" -ForegroundColor Green

Write-Host "==> Packaging app..." -ForegroundColor Cyan
$zipPath = Join-Path $env:TEMP "liveness-py-app.zip"
if (Test-Path $zipPath) { Remove-Item $zipPath -Force }
$staging = Join-Path $env:TEMP "liveness-py-stage"
if (Test-Path $staging) { Remove-Item $staging -Recurse -Force }
New-Item -ItemType Directory -Path $staging | Out-Null
Copy-Item (Join-Path $repoRoot "app") (Join-Path $staging "app") -Recurse
# Vendor the in-repo attestation library and point requirements at it (the
# editable path in requirements.txt only resolves in the local checkout).
$libSrc = Join-Path $repoRoot "..\..\library\python\AzureAIVisionFaceDeviceAttestation"
$vendor = Join-Path $staging "vendor\azure-ai-vision-face-deviceattestation"
New-Item -ItemType Directory -Path $vendor -Force | Out-Null
Copy-Item (Join-Path $libSrc "azure_ai_vision_face_deviceattestation") $vendor -Recurse
Copy-Item (Join-Path $libSrc "pyproject.toml") $vendor
Copy-Item (Join-Path $libSrc "README.md") $vendor
Get-Content (Join-Path $repoRoot "requirements.txt") | ForEach-Object {
  if ($_ -eq "-e ../../library/python/AzureAIVisionFaceDeviceAttestation") {
    "./vendor/azure-ai-vision-face-deviceattestation"
  } else {
    $_
  }
} | Set-Content (Join-Path $staging "requirements.txt")
Get-ChildItem $staging -Recurse -Include "__pycache__" -Directory | Remove-Item -Recurse -Force
Compress-Archive -Path (Join-Path $staging "*") -DestinationPath $zipPath -Force

Write-Host "==> Zip-deploying to $webAppName..." -ForegroundColor Cyan
az webapp deploy --resource-group $rg --name $webAppName --src-path $zipPath --type zip | Out-Null

Write-Host ""
Write-Host "Deployed: $webAppUrl" -ForegroundColor Green
Write-Host "  AASA:       $webAppUrl/.well-known/apple-app-site-association"
Write-Host "  assetlinks: $webAppUrl/.well-known/assetlinks.json"
Write-Host "  health:     $webAppUrl/healthz"
