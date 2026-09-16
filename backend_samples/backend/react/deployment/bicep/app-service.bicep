@description('Short environment name, e.g. dev / test / prod')
param env string

@description('Azure region for all resources')
param location string = resourceGroup().location

@description('Short region abbreviation used in resource names')
param locationabbr string

@description('Node.js runtime version for the Linux App Service')
param nodeVersion string = '20-lts'

var isDev = contains(env, 'dev') || contains(env, 'ppe') || contains(env, 'test')
var webAppName = 'liveness-react-${env}-${locationabbr}'
var appServicePlanName = 'appServicePlan-${webAppName}'

// The Next.js standalone build emits a self-contained `server.js` at the deploy
// root (see deploy.ps1 / deploy.sh). It listens on $PORT, which App Service sets.
var startupCommand = 'node server.js'

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
      linuxFxVersion: 'NODE|${nodeVersion}'
      appCommandLine: startupCommand
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
