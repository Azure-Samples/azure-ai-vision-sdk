plugins {
    id("com.android.dynamic-feature")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.microsoft.azure.ai.vision.facelivenessdetectorsample.dynamicfeature"
    compileSdk = 34

    defaultConfig {
        minSdk = 24
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
}

dependencies {
    implementation(project(":app"))
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("com.google.android.play:feature-delivery-ktx:2.1.0")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.annotation:annotation:1.6.0")

    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.5.1")
    implementation("androidx.compose.runtime:runtime-livedata:1.4.3")
    implementation("androidx.core:core-ktx:1.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose-android:2.8.4")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.09.03"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    implementation("androidx.camera:camera-lifecycle:1.1.0")
    implementation("com.azure.android:azure-core-credential:1.0.0-beta.10")

    implementation ("net.sourceforge.streamsupport:android-retrofuture:1.7.4")
    implementation ("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.vectordrawable:vectordrawable-animated:1.2.0")
    
    implementation("com.azure.android:azure-core-http-okhttp:1.0.0-beta.12")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
    implementation("com.google.accompanist:accompanist-permissions:0.35.1-alpha")
    implementation("androidx.navigation:navigation-compose:2.8.0-alpha08")
    implementation("com.azure:azure-ai-vision-face-ui:+")
    implementation("com.azure.android:azure-core-http-httpurlconnection:1.0.0-beta.10")
}