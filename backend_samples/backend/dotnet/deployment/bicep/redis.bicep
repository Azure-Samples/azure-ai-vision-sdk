// Azure Managed Redis (built on Redis Enterprise) with Entra ID auth — no access keys.
// Docs: https://learn.microsoft.com/azure/azure-managed-redis/

@description('Short environment name, e.g. dev / test / prod')
param env string

@description('Azure region for the cache')
param location string = resourceGroup().location

@description('Short region abbreviation used in resource names')
param locationabbr string

@description('Object (principal) ID of the Web App managed identity')
param appServiceServicePrincipal string

var isDev = contains(env, 'dev') || contains(env, 'ppe') || contains(env, 'test')
var redisName = 'liveness-dotnet-${env}-${locationabbr}'

resource managedRedis 'Microsoft.Cache/redisEnterprise@2025-07-01' = {
  name: redisName
  location: location
  sku: {
    name: isDev ? 'Balanced_B0' : 'Balanced_B3'
  }
  properties: {
    publicNetworkAccess: 'Enabled'
  }
  tags: {
    environment: env
  }
}

resource database 'Microsoft.Cache/redisEnterprise/databases@2025-07-01' = {
  name: 'default'
  parent: managedRedis
  properties: {
    clientProtocol: 'Encrypted'
    port: 10000
    clusteringPolicy: 'EnterpriseCluster'
    evictionPolicy: 'NoEviction'
    accessKeysAuthentication: 'Disabled'
  }
}

resource accessPolicyAssignment 'Microsoft.Cache/redisEnterprise/databases/accessPolicyAssignments@2025-07-01' = {
  name: 'webAppPolicy'
  parent: database
  properties: {
    accessPolicyName: 'default'
    user: {
      objectId: appServiceServicePrincipal
    }
  }
}

output redisId string = managedRedis.id
output redisHostName string = managedRedis.properties.hostName
output redisPort int = database.properties.port
