@description('Short environment name, e.g. dev / test / prod')
param env string

@description('Azure region for all resources')
param location string = resourceGroup().location

@description('Short region abbreviation used in resource names')
param locationabbr string

@description('Java runtime version for the Linux App Service')
param javaVersion string = '17-java17'

var isDev = contains(env, 'dev') || contains(env, 'ppe') || contains(env, 'test')
var webAppName = 'liveness-java-${env}-${locationabbr}'
var appServicePlanName = 'appServicePlan-${webAppName}'

resource appServicePlan 'Microsoft.Web/serverfarms@2023-12-01' = {
  name: appServicePlanName
  location: location
  properties: {
    reserved: true // Linux app service plan
  }
  sku: {
    name: isDev ? 'B1' : 'P1v3'
  }
  kind: 'linux'
}

resource appService 'Microsoft.Web/sites@2023-12-01' = {
  name: webAppName
  location: location
  kind: 'app,linux'
  identity: {
    type: 'SystemAssigned'
  }
  properties: {
    serverFarmId: appServicePlan.id
    httpsOnly: true
    siteConfig: {
      // Java SE stack: App Service runs the deployed app.jar and passes the
      // listening port to the app via the PORT environment variable.
      linuxFxVersion: 'JAVA|${javaVersion}'
      ftpsState: 'Disabled'
      minTlsVersion: '1.2'
      http20Enabled: true
      alwaysOn: !isDev ? true : false
    }
  }
}

output appServicePrincipalId string = appService.identity.principalId
output appServiceName string = appService.name
output webAppHost string = appService.properties.defaultHostName
