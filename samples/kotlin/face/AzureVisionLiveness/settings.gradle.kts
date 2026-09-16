pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "AzureVisionLiveness"
include(":app")
include(":azure-ai-vision-face-deviceattestation")
project(":azure-ai-vision-face-deviceattestation").projectDir =
    file("../../../../client_libraries/android/AzureAIVisionFaceDeviceAttestation")
