import java.util.Base64

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

/**
 * Prefer CI secrets (KEYSTORE_BASE64 + passwords).
 * Fallback: committed test keystore for local debug builds.
 */
fun resolveKeystore(): File {
    val fromEnv = System.getenv("KEYSTORE_FILE")
    if (!fromEnv.isNullOrBlank()) {
        val f = file(fromEnv)
        if (f.exists()) return f
    }
    // Local / fallback test keystore
    val ks = file("rplayer-test.keystore")
    if (!ks.exists()) {
        val b64File = file("rplayer-test.keystore.b64")
        if (b64File.exists()) {
            val b64 = b64File.readText().replace(Regex("\\s"), "")
            ks.writeBytes(Base64.getDecoder().decode(b64))
        }
    }
    return ks
}

val signingKeystore = resolveKeystore()
val storePass = System.getenv("KEYSTORE_PASSWORD") ?: "rplayer-test"
val keyAliasEnv = System.getenv("KEY_ALIAS") ?: "rplayer"
val keyPass = System.getenv("KEY_PASSWORD") ?: "rplayer-test"

android {
    namespace = "eu.akaiko.rplayer"
    compileSdk = 36

    defaultConfig {
        applicationId = "eu.akaiko.rplayer"
        minSdk = 29
        targetSdk = 36
        versionCode = 9
        versionName = "1.8"
    }

    signingConfigs {
        create("release") {
            storeFile = signingKeystore
            storePassword = storePass
            keyAlias = keyAliasEnv
            keyPassword = keyPass
        }
        // Keep a named "test" config for clarity (same file when no secrets)
        create("test") {
            storeFile = signingKeystore
            storePassword = storePass
            keyAlias = keyAliasEnv
            keyPassword = keyPass
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("release")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
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
