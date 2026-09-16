// Consolidated app settings for the Linux .NET web app. This resource owns the
// entire app settings collection. Settings bind to the AppSettings options class
// via the ASP.NET Core "AppSettings__Key" environment-variable convention.
//
// NOTE: GoogleServiceAccountJson is a secret and is intentionally NOT set here —
// set AppSettings__GoogleServiceAccountJson separately (ideally from Key Vault).

param appServiceName string

@description('Redis hostname output from redis.bicep')
param redisHost string

@description('Redis TLS port output from redis.bicep (10000 for Managed Redis)')
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

resource webApp 'Microsoft.Web/sites@2024-04-01' existing = {
  name: appServiceName
}

resource appSettings 'Microsoft.Web/sites/config@2024-04-01' = {
  name: 'appsettings'
  parent: webApp
  properties: {
    // The app is deployed prebuilt (dotnet publish + zip), so no build runs on deploy.
    SCM_DO_BUILD_DURING_DEPLOYMENT: 'false'

    // Redis (Entra/managed-identity auth in production).
    AppSettings__UseLocalRedis: 'false'
    AppSettings__RedisHostname: redisHost
    AppSettings__RedisPort: string(redisPort)

    // Session token storage TTL.
    AppSettings__SessionTokenTtl: sessionTokenTtl

    // Universal Link / App Link binding documents (served at /.well-known).
    AppSettings__IosAppId: iosAppId
    AppSettings__IosAppClipId: iosAppClipId
    AppSettings__AndroidPackageName: androidPackageName
    AppSettings__AndroidSha256CertFingerprints: androidSha256Fingerprints
    AppSettings__ApplinkPath: applinkPath

    // Fallback landing page store links.
    AppSettings__IosAppStoreUrl: iosAppStoreUrl
    AppSettings__AndroidPlayStoreUrl: androidPlayStoreUrl
  }
}
