#!/usr/bin/env pwsh
# Provisions infrastructure (Bicep) then builds + zip-deploys the .NET app.
#
#   ./deploy.ps1 -Location eastus
param(
    [string]$Location = "eastus",
    [string]$ParametersFile = "$PSScriptRoot/main.parameters.json"
)
$ErrorActionPreference = "Stop"
$appDir = Resolve-Path "$PSScriptRoot/.."

Write-Host "Deploying infrastructure..." -ForegroundColor Cyan
$outputs = az deployment sub create `
    --location $Location `
    --template-file "$PSScriptRoot/bicep/main.bicep" `
    --parameters "@$ParametersFile" `
    --query properties.outputs -o json | ConvertFrom-Json
$webAppName = $outputs.webAppName.value
$resourceGroup = $outputs.resourceGroupName.value
$webAppUrl = $outputs.webAppUrl.value

Write-Host "Publishing app..." -ForegroundColor Cyan
$publishDir = Join-Path $appDir "publish"
if (Test-Path $publishDir) { Remove-Item -Recurse -Force $publishDir }
dotnet publish "$appDir/FaceLivenessAttestationBackendSample.csproj" -c Release -o $publishDir

Write-Host "Packaging + deploying to $webAppName..." -ForegroundColor Cyan
$zip = Join-Path $appDir "publish.zip"
if (Test-Path $zip) { Remove-Item -Force $zip }
Compress-Archive -Path "$publishDir/*" -DestinationPath $zip
az webapp deploy --resource-group $resourceGroup --name $webAppName --src-path $zip --type zip

Write-Host "Deployed: $webAppUrl" -ForegroundColor Green
