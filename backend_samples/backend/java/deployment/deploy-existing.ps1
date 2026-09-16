<#
  Redeploy the app code to an EXISTING App Service (no infra changes).

  Usage:
    ./deployment/deploy-existing.ps1 -SubscriptionId <sub> -ResourceGroup <rg> -App <appName>
#>
param(
  [Parameter(Mandatory = $true)][string]$SubscriptionId,
  [Parameter(Mandatory = $true)][string]$ResourceGroup,
  [Parameter(Mandatory = $true)][string]$App
)

$ErrorActionPreference = "Stop"
$sampleRoot = Split-Path -Parent $PSScriptRoot
Push-Location $sampleRoot
try {
  az account set --subscription $SubscriptionId

  Write-Host "Building the jar..." -ForegroundColor Cyan
  mvn -B -DskipTests package

  Write-Host "Deploying jar to $App..." -ForegroundColor Cyan
  az webapp deploy --resource-group $ResourceGroup --name $App --type jar `
    --src-path target/face-liveness-attestation-backend-sample.jar

  Write-Host "Redeployed." -ForegroundColor Green
}
finally {
  Pop-Location
}
