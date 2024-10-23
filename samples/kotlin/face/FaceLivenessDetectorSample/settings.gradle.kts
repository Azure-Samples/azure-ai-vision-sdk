pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

val mavenUrl: String by settings
val mavenUser: String by settings
val mavenPassword: String by settings

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        dependencyResolutionManagement {
            repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
            repositories {
                google()
                mavenCentral()
                maven {
                    url = uri(mavenUrl)
                    credentials {
                        username = mavenUser
                        password = mavenPassword
                    }
                }
            }
        }
    }
}

rootProject.name = "FaceLivenessDetectorSample"
include(":app")
 