import java.util.Date
import java.text.SimpleDateFormat
import org.apache.tools.ant.filters.ReplaceTokens
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

// Single source of truth for the liveness web app host(s). Used to:
//  - declare the ${livenessHost} App Link host in AndroidManifest.xml (primary)
//    plus a generated manifest overlay for any additional hosts
//  - generate BuildConfig.LIVENESS_HOST (primary) + BuildConfig.LIVENESS_HOSTS
//    (full whitelist) consumed by the App Link handler + DeviceAttestation
//  - generate res/xml/network_security_config.xml from its template (one
//    <domain> per host)
//
// Accepts a comma-separated LIST of hosts so the app can be associated with
// multiple App Link domains. Override at build time via the LIVENESS_HOST
// environment variable or the livenessHost Gradle property
// (e.g. -PlivenessHost=a.example.com,b.example.com or in gradle.properties).
val livenessHost: String = System.getenv("LIVENESS_HOST")
    ?: (project.findProperty("livenessHost") as? String)
    ?: error("LIVENESS_HOST not set")

// The parsed host list + primary (first). livenessHosts drives the whitelist
// (BuildConfig.LIVENESS_HOSTS), the network security config domains, and the
// extra App Link intent-filters; livenessHostPrimary drives the main manifest's
// App Link host + BuildConfig.LIVENESS_HOST.
val livenessHosts: List<String> = livenessHost
    .split(",")
    .map { it.trim() }
    .filter { it.isNotEmpty() }
val livenessHostPrimary: String = livenessHosts.firstOrNull()
    ?: error("livenessHost resolved to an empty list")

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.kotlin.plugin.compose")
}
fun determineVersionName(): String {
    val runtime = Runtime.getRuntime()
    val proc = runtime.exec("git rev-parse --short HEAD")
    proc.waitFor()
    return "1." + proc.inputStream.bufferedReader().readText().trim()
}

fun determineVersionCode(): Int {
    val currentDate = Date()
    val formatter = SimpleDateFormat("yyMMddHHmm")
    val dateString = formatter.format(currentDate)
    var ut = dateString.toUInt()
    ut -= 2000000000U
    return ut.toInt()
}

fun writeVersionInfo(versionCode: Int, versionName: String, outputDir: File) {
    val versionFile = File(outputDir, "aab_version_info.txt")
    val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Date())
    versionFile.writeText("Build Time: $timestamp\nVersion Code: $versionCode\nVersion Name: $versionName\n")
    println("Version Info written to: ${versionFile.absolutePath}")
    println("versionCode=$versionCode, versionName=$versionName")
}

android {
    namespace = "com.microsoft.azurevisionliveness"
    compileSdk = 36


    defaultConfig {
        applicationId = "com.microsoft.azurevisionliveness"
        minSdk = 31
        targetSdk = 36
        versionCode = determineVersionCode()
        versionName = "1.4"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        // Play Integrity cloud project number. Must be supplied at build time via
        // the CLOUD_PROJECT_NUMBER environment variable or the cloudProjectNumber
        // Gradle property (e.g. -PcloudProjectNumber=123 or in gradle.properties).
        val cloudProjectNumberRaw: String = System.getenv("CLOUD_PROJECT_NUMBER")
            ?: (project.findProperty("cloudProjectNumber") as? String)
            ?: error("CLOUD_PROJECT_NUMBER not set")
        val cloudProjectNumber: Long = cloudProjectNumberRaw.toLongOrNull()
            ?: error("CLOUD_PROJECT_NUMBER must be a valid long, got: $cloudProjectNumberRaw")
        buildConfigField("long", "CLOUD_PROJECT_NUMBER", "${cloudProjectNumber}L")

        // The primary host drives the App Link intent-filter declared in
        // src/main/AndroidManifest.xml; any additional hosts are contributed as
        // a generated manifest overlay (generateAppLinksManifestOverlay below).
        manifestPlaceholders["livenessHost"] = livenessHostPrimary
        // BuildConfig.LIVENESS_HOST = primary host (single-host defaults).
        // BuildConfig.LIVENESS_HOSTS = the full whitelist, consumed by the App
        // Link handler + ResultScreen to validate the host taken from an inbound
        // link before trusting it as an attestation backend.
        buildConfigField("String", "LIVENESS_HOST", "\"$livenessHostPrimary\"")
        buildConfigField(
            "String[]",
            "LIVENESS_HOSTS",
            "{" + livenessHosts.joinToString(", ") { "\"$it\"" } + "}"
        )
    }

    buildTypes {
        release {
            isMinifyEnabled = true
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
    buildFeatures {
        compose = true
        resValues = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    flavorDimensions += "publish"

    productFlavors {
        create("publish") {
            dimension = "publish"
            resValue("string", "app_name", "AzureVisionLiveness")
        }
    }
    sourceSets {
        getByName("main") {
            // Picks up the file produced by generateNetworkSecurityConfig.
            // Plain string (not a Provider) — AGP's SourceSet API rejects
            // Provider instances; task ordering is wired via preBuild below.
            res.srcDir("build/generated/res/network-security")
        }
        getByName("publish") {
            java.srcDirs(
                "src/main/java",
                "../../common/java",
                "../../sample/java"
            )
            kotlin.srcDirs(
                "src/main/java",
                "../../common/java",
                "../../sample/java"
            )
            res.srcDirs(
                "../../FaceLivenessDetectorSample/app/src/main/res"
            )
            assets.srcDirs(
                "../../FaceLivenessDetectorSample/app/src/main/assets"
            )
        }
    }


}

// Expand the liveness host token in the network security config template into
// one <domain> element per configured host, so the hostname list has a single
// source of truth in this build file.
val livenessHostDomains: String = livenessHosts.joinToString("\n        ") {
    "<domain includeSubdomains=\"true\">$it</domain>"
}
val generateNetworkSecurityConfig by tasks.registering(Copy::class) {
    from("src/main/res-template/xml") {
        include("network_security_config.xml")
    }
    into(layout.buildDirectory.dir("generated/res/network-security/xml"))
    filter(mapOf("tokens" to mapOf("livenessHostDomains" to livenessHostDomains)), ReplaceTokens::class.java)
    inputs.property("livenessHostDomains", livenessHostDomains)
}

tasks.named("preBuild") {
    dependsOn(generateNetworkSecurityConfig)
}

// Additional App Link domains beyond the primary are contributed as a generated
// manifest overlay that AGP merges (highest priority). The primary host is
// declared directly in src/main/AndroidManifest.xml via the ${livenessHost}
// placeholder, so a single-domain build needs no extra hosts (the overlay is an
// empty no-op manifest in that case).
abstract class GenerateAppLinksManifestOverlay : DefaultTask() {
    @get:Input
    abstract val extraHosts: ListProperty<String>

    @get:OutputFile
    abstract val outputManifest: RegularFileProperty

    @TaskAction
    fun generate() {
        val hosts = extraHosts.get()
        val body = if (hosts.isEmpty()) {
            "    <application />\n"
        } else {
            val dataEntries = hosts.joinToString("\n") {
                "                <data android:host=\"$it\" />"
            }
            "    <application>\n" +
                "        <activity android:name=\"com.microsoft.azurevisionliveness.AppCenterActivity\">\n" +
                "            <intent-filter android:autoVerify=\"true\">\n" +
                "                <action android:name=\"android.intent.action.VIEW\" />\n" +
                "                <category android:name=\"android.intent.category.DEFAULT\" />\n" +
                "                <category android:name=\"android.intent.category.BROWSABLE\" />\n" +
                "                <data android:scheme=\"https\" />\n" +
                dataEntries + "\n" +
                "                <data android:pathPrefix=\"/native\" />\n" +
                "            </intent-filter>\n" +
                "        </activity>\n" +
                "    </application>\n"
        }
        val file = outputManifest.get().asFile
        file.parentFile.mkdirs()
        file.writeText(
            "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\">\n" +
                body +
                "</manifest>\n"
        )
    }
}

val generateAppLinksManifestOverlay =
    tasks.register<GenerateAppLinksManifestOverlay>("generateAppLinksManifestOverlay") {
        extraHosts.set(livenessHosts.drop(1))
    }

androidComponents {
    onVariants { variant ->
        variant.sources.manifests.addGeneratedManifestFile(generateAppLinksManifestOverlay) {
            it.outputManifest
        }
    }
}

// Write version info after bundle task completes
tasks.matching { it.name.startsWith("bundle") && it.name.endsWith("Release") }.configureEach {
    doLast {
        val variantName = name.removePrefix("bundle")
        val outputDir = File(layout.buildDirectory.asFile.get(), "outputs/bundle/${variantName.replaceFirstChar { it.lowercase() }}")
        val versionCode = android.defaultConfig.versionCode ?: 0
        val versionName = android.defaultConfig.versionName ?: "unknown"
        outputDir.mkdirs()
        writeVersionInfo(versionCode, versionName, outputDir)
    }
}

dependencies {

    implementation("com.azure.android:azure-core-http-okhttp:1.0.0-beta.14")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("com.google.accompanist:accompanist-permissions:0.36.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.compose.runtime:runtime-livedata:1.7.6")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose-android:2.8.7")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.12.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    implementation(project(":azure-ai-vision-face-deviceattestation"))
    implementation(fileTree(mapOf("dir" to "../../../../../../../private_samples/vision/samples/kotlin/aar", "include" to listOf("*.aar"))))
// CameraX core library using camera2 implementation
    implementation("androidx.camera:camera-camera2:1.4.1")
// CameraX Lifecycle Library
    implementation("androidx.camera:camera-lifecycle:1.4.1")
    implementation("com.azure.android:azure-core-http-httpurlconnection:1.0.0-beta.14")
    implementation("com.azure.android:azure-core-credential:1.0.0-beta.14")

    implementation ("net.sourceforge.streamsupport:android-retrofuture:1.7.4")
    implementation ("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.vectordrawable:vectordrawable-animated:1.2.0")
    implementation("androidx.camera:camera-view:1.4.1")
    implementation("com.android.installreferrer:installreferrer:2.2")
}