import java.io.FileInputStream
import java.util.Properties
import java.util.Date
import java.text.SimpleDateFormat

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
}
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
keystoreProperties.load(FileInputStream(keystorePropertiesFile))

android {
    namespace = "com.example.facelivenessdetectorsample"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.facelivenessdetectorsample"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }
    buildTypes {
        release {
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.4"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation("com.azure.android:azure-core-http-okhttp:1.0.0-beta.12")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
    implementation("com.google.accompanist:accompanist-permissions:0.35.1-alpha")
    implementation("androidx.navigation:navigation-compose:2.8.0-alpha08")
    implementation("com.azure.ai:azure-ai-vision-face-ui:0.18.0-beta.1")
    implementation("com.azure.android:azure-core-http-httpurlconnection:1.0.0-beta.10")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.09.03"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}