<#
  Full deployment: build the jar, provision infrastructure (App Service + Azure
  Managed Redis) via Bicep, then jar-deploy the app.

  Usage:
    ./deployment/deploy.ps1 -SubscriptionId <sub> [-Env dev] [-Location centralus] [-LocationAbbr cus]
#>
param(
  [Parameter(Mandatory = $true)][string]$SubscriptionId,
  [string]$Env = "dev",
  [string]$Location = "centralus",
  [string]$LocationAbbr = "cus",
  [string]$Owner = $env:USERNAME
)

$ErrorActionPreference = "Stop"
$sampleRoot = Split-Path -Parent $PSScriptRoot
Push-Location $sampleRoot
try {
  az account set --subscription $SubscriptionId

  Write-Host "Building the jar..." -ForegroundColor Cyan
  mvn -B -DskipTests -f ../pom.xml package

  Write-Host "Provisioning infrastructure..." -ForegroundColor Cyan
  $deployment = az deployment sub create `
    --location $Location `
    --template-file deployment/bicep/main.bicep `
    --parameters deployment/main.parameters.json `
      env=$Env location=$Location locationabbr=$LocationAbbr owner=$Owner | ConvertFrom-Json

  $app = $deployment.properties.outputs.webAppName.value
  $rg = $deployment.properties.outputs.resourceGroupName.value
  $url = $deployment.properties.outputs.webAppUrl.value

  Write-Host "Deploying jar to $app..." -ForegroundColor Cyan
  az webapp deploy --resource-group $rg --name $app --type jar `
    --src-path target/face-liveness-attestation-backend-sample.jar

  Write-Host "Deployed: $url" -ForegroundColor Green
}
finally {
  Pop-Location
}
