plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

import java.util.Base64

/** Decode committed base64 keystore once so every CI/local build uses the same signature. */
fun ensureTestKeystore(): File {
    val ks = file("rplayer-test.keystore")
    if (!ks.exists()) {
        val b64 = file("rplayer-test.keystore.b64").readText().replace(Regex("\\s"), "")
        ks.writeBytes(Base64.getDecoder().decode(b64))
    }
    return ks
}

val testKeystore = ensureTestKeystore()

android {
    namespace = "eu.akaiko.rplayer"
    compileSdk = 36

    defaultConfig {
        applicationId = "eu.akaiko.rplayer"
        minSdk = 29      // Android 10
        targetSdk = 36   // Android 16
        versionCode = 3
        versionName = "1.2"
    }

    signingConfigs {
        create("test") {
            storeFile = testKeystore
            storePassword = "rplayer-test"
            keyAlias = "rplayer"
            keyPassword = "rplayer-test"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("test")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("test")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.01.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-session:1.5.1")
    implementation("androidx.media3:media3-datasource-okhttp:1.5.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("io.coil-kt:coil-compose:2.7.0")
}
