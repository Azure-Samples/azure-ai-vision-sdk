// Consolidated app settings for the Linux Python web app. This resource owns
// the entire app settings collection, so every setting the app needs lives here.

param appServiceName string

@description('Redis hostname output from redis.bicep')
param redisHost string

@description('Redis TLS port output from redis.bicep (10000 for Managed Redis, 6380 for the legacy cache)')
param redisPort int = 10000

param iosAppId string = ''
param iosAppClipId string = ''
param androidPackageName string = ''
param androidSha256Fingerprints string = ''
param iosAppStoreUrl string = ''
param androidPlayStoreUrl string = ''

@description('Session token TTL in seconds')
param sessionTokenTtl string = '600'

@description('Universal/App Link path pattern served in the AASA file')
param applinkPath string = '/native*'

@description('Optional Application Insights connection string')
param appInsightsConnectionString string = ''

resource webApp 'Microsoft.Web/sites@2024-04-01' existing = {
  name: appServiceName
}

resource appSettings 'Microsoft.Web/sites/config@2024-04-01' = {
  name: 'appsettings'
  parent: webApp
  properties: {
    // Build requirements.txt with Oryx during zip deploy.
    SCM_DO_BUILD_DURING_DEPLOYMENT: 'true'

    // Redis (AAD/managed-identity auth in production).
    USE_LOCAL_REDIS: 'false'
    REDIS_HOSTNAME: redisHost
    REDIS_PORT: string(redisPort)

    // Session TTL (also bounds the app + attestation session records).
    SESSION_TOKEN_TTL: sessionTokenTtl

    // Universal Link / App Link binding documents (served at /.well-known).
    IOS_APP_ID: iosAppId
    IOS_APP_CLIP_ID: iosAppClipId
    ANDROID_PACKAGE_NAME: androidPackageName
    ANDROID_SHA256_CERT_FINGERPRINTS: androidSha256Fingerprints
    APPLINK_PATH: applinkPath

    // Fallback landing page store links.
    IOS_APP_STORE_URL: iosAppStoreUrl
    ANDROID_PLAY_STORE_URL: androidPlayStoreUrl

    // Telemetry (optional).
    APPLICATIONINSIGHTS_CONNECTION_STRING: appInsightsConnectionString
  }
}
