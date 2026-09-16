/*
  Subscription-scope entry point.

  Creates the resource group and delegates resource creation to
  resource-group.bicep. Deploy with:

    az deployment sub create \
      --location <location> \
      --template-file deployment/bicep/main.bicep \
      --parameters @deployment/main.parameters.json
*/

targetScope = 'subscription'

@description('Short environment name, e.g. dev / test / prod')
param env string

@description('Azure region for all resources')
param location string

@description('Short region abbreviation used in resource names, e.g. eus / wus2')
param locationabbr string

@description('iOS appID for the AASA file: TeamID.BundleID')
param iosAppId string = ''

@description('Optional iOS App Clip appID: TeamID.BundleID.Clip')
param iosAppClipId string = ''

@description('Android application id / package name')
param androidPackageName string = ''

@description('Android signing-cert SHA-256 fingerprints (comma or space separated)')
param androidSha256Fingerprints string = ''

@description('Optional App Store URL for the fallback landing page')
param iosAppStoreUrl string = ''

@description('Optional Google Play URL for the fallback landing page')
param androidPlayStoreUrl string = ''

@description('Owner tag value applied to the resource group')
param owner string

resource resourceGroup 'Microsoft.Resources/resourceGroups@2021-04-01' = {
  name: 'liveness-dotnet-${env}-${locationabbr}'
  location: location
  tags: {
    owner: owner
  }
}

module resourceGroupResources 'resource-group.bicep' = {
  name: 'resourceGroupResources'
  scope: resourceGroup
  params: {
    env: env
    location: location
    locationabbr: locationabbr
    iosAppId: iosAppId
    iosAppClipId: iosAppClipId
    androidPackageName: androidPackageName
    androidSha256Fingerprints: androidSha256Fingerprints
    iosAppStoreUrl: iosAppStoreUrl
    androidPlayStoreUrl: androidPlayStoreUrl
  }
}

@description('Default hostname of the deployed web app')
output webAppHost string = resourceGroupResources.outputs.webAppHost

@description('Full https URL of the deployed web app')
output webAppUrl string = resourceGroupResources.outputs.webAppUrl

@description('Name of the deployed App Service (used by the deploy script for zip deploy)')
output webAppName string = resourceGroupResources.outputs.webAppName

@description('Name of the created resource group')
output resourceGroupName string = resourceGroup.name
