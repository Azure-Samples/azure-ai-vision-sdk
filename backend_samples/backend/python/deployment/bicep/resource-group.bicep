/*
  Resource-group-scope deployment: App Service (Linux, Python), Azure Cache for
  Redis (AAD auth), and the consolidated app settings.

  Order matters:
    1. app-service  -> creates the site + system-assigned identity
    2. redis        -> grants that identity Redis data access (AAD)
    3. app-settings -> writes the full app settings collection (incl. Redis host)
*/

targetScope = 'resourceGroup'

@description('Short environment name, e.g. dev / test / prod')
param env string

@description('Azure region for all resources')
param location string = resourceGroup().location

@description('Short region abbreviation used in resource names')
param locationabbr string

param iosAppId string = ''
param iosAppClipId string = ''
param androidPackageName string = ''
param androidSha256Fingerprints string = ''
param iosAppStoreUrl string = ''
param androidPlayStoreUrl string = ''

module appService 'app-service.bicep' = {
  name: 'appServiceDeploy'
  params: {
    env: env
    location: location
    locationabbr: locationabbr
  }
}

module azureCacheForRedis 'redis.bicep' = {
  name: 'azureCacheForRedisDeploy'
  params: {
    env: env
    location: location
    locationabbr: locationabbr
    appServiceServicePrincipal: appService.outputs.appServicePrincipalId
  }
}

module appSettings 'app-settings.bicep' = {
  name: 'appSettingsDeploy'
  params: {
    appServiceName: appService.outputs.appServiceName
    redisHost: azureCacheForRedis.outputs.redisHostName
    redisPort: azureCacheForRedis.outputs.redisPort
    iosAppId: iosAppId
    iosAppClipId: iosAppClipId
    androidPackageName: androidPackageName
    androidSha256Fingerprints: androidSha256Fingerprints
    iosAppStoreUrl: iosAppStoreUrl
    androidPlayStoreUrl: androidPlayStoreUrl
  }
}

output webAppHost string = appService.outputs.webAppHost
output webAppUrl string = 'https://${appService.outputs.webAppHost}'
output webAppName string = appService.outputs.appServiceName
