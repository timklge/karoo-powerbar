import java.util.Base64
import com.android.build.gradle.tasks.ProcessApplicationManifest

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.compose.compiler)
    kotlin("plugin.serialization") version "2.0.20"
}

android {
    namespace = "de.timklge.karoopowerbar"
    compileSdk = 34

    defaultConfig {
        applicationId = "de.timklge.karoopowerbar"
        minSdk = 26
        targetSdk = 33
        versionCode = 100 + (System.getenv("BUILD_NUMBER")?.toInt() ?: 1)
        versionName = System.getenv("RELEASE_VERSION") ?: "1.0"
    }

    signingConfigs {
        create("release") {
            val env: MutableMap<String, String> = System.getenv()
            keyAlias = env["KEY_ALIAS"]
            keyPassword = env["KEY_PASSWORD"]

            val base64keystore: String = env["KEYSTORE_BASE64"] ?: ""
            val keystoreFile: File = File.createTempFile("keystore", ".jks")
            keystoreFile.writeBytes(Base64.getDecoder().decode(base64keystore))
            storeFile = keystoreFile
            storePassword = env["KEYSTORE_PASSWORD"]
        }
    }
    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
        buildConfig = true
    }
}

tasks.register("generateManifest") {
    description = "Generates manifest.json with current version information"
    group = "build"

    doLast {
        val baseUrl = System.getenv("BASE_URL") ?: "https://github.com/timklge/karoo-powerbar/releases/latest/download"
        val manifestFile = file("$projectDir/manifest.json")
        val manifest = mapOf(
            "label" to "Powerbar",
            "packageName" to "de.timklge.karoopowerbar",
            "iconUrl" to "$baseUrl/karoo-powerbar.png",
            "latestApkUrl" to "$baseUrl/app-release.apk",
            "latestVersion" to android.defaultConfig.versionName,
            "latestVersionCode" to android.defaultConfig.versionCode,
            "developer" to "github.com/timklge",
            "description" to "Open-source extension that adds colored progress bars representing power, heart rate etc. to the edge of the screen, similar to the LED bars on older Wahoo computers",
            "releaseNotes" to "* Add lap and ride average pedal balance sources",
            "screenshotUrls" to listOf(
                "$baseUrl/powerbar_min.gif",
                "$baseUrl/powerbar0.png",
                "$baseUrl/powerbar2.png",
                "$baseUrl/powerbar1.png",
                "$baseUrl/powerbar3.png",
            )
        )

        val gson = groovy.json.JsonBuilder(manifest).toPrettyString()
        manifestFile.writeText(gson)
        println("Generated manifest.json with version ${android.defaultConfig.versionName} (${android.defaultConfig.versionCode})")

        if (System.getenv()["BASE_URL"] != null){
            val androidManifestFile = file("$projectDir/src/main/AndroidManifest.xml")
            var androidManifestContent = androidManifestFile.readText()
            androidManifestContent = androidManifestContent.replace("\$BASE_URL\$", baseUrl)
            androidManifestFile.writeText(androidManifestContent)
            println("Replaced \$BASE_URL$ in AndroidManifest.xml")
        }
    }
}

tasks.named("assemble") {
    dependsOn("generateManifest")
}

tasks.withType<ProcessApplicationManifest>().configureEach {
    if (name == "processDebugMainManifest" || name == "processReleaseMainManifest") {
        dependsOn(tasks.named("generateManifest"))
    }
}

dependencies {
    implementation(libs.hammerhead.karoo.ext)
    implementation(libs.androidx.core.ktx)
    implementation(libs.bundles.androidx.lifeycle)
    implementation(libs.androidx.activity.compose)
    implementation(libs.bundles.compose.ui)
    implementation(libs.androidx.navigation.runtime.ktx)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.color)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.cardview)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.mapbox.sdk.turf)
}

