import java.util.Base64

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

/**
 * Signing is required for every build (debug and release).
 * Credentials come only from the environment / CI secrets — never committed:
 *
 *   KEYSTORE_BASE64   — preferred: keystore bytes as Base64 (GitHub secret)
 *   KEYSTORE_FILE     — optional alternative: path to an existing .keystore/.jks
 *   KEYSTORE_PASSWORD
 *   KEY_ALIAS
 *   KEY_PASSWORD
 */
fun resolveKeystore(): File {
    val fromFile = System.getenv("KEYSTORE_FILE")?.takeIf { it.isNotBlank() }
    if (fromFile != null) {
        val candidates = listOf(
            file(fromFile),
            rootProject.file(fromFile),
            file(fromFile.removePrefix("app/"))
        )
        candidates.firstOrNull { it.exists() }?.let { return it }
        error(
            "KEYSTORE_FILE=$fromFile not found (tried ${candidates.map { it.absolutePath }})"
        )
    }

    val b64 = System.getenv("KEYSTORE_BASE64")?.replace(Regex("\\s"), "")
    if (!b64.isNullOrBlank()) {
        val ks = file("release.keystore")
        ks.writeBytes(Base64.getDecoder().decode(b64))
        return ks
    }

    error(
        "Signing keystore required. Set KEYSTORE_BASE64 (or KEYSTORE_FILE) plus " +
            "KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD."
    )
}

fun envRequired(name: String): String =
    System.getenv(name)?.takeIf { it.isNotBlank() }
        ?: error("Missing required environment variable: $name")

val signingKeystore = resolveKeystore()
val storePass = envRequired("KEYSTORE_PASSWORD")
val keyAliasEnv = envRequired("KEY_ALIAS")
val keyPass = envRequired("KEY_PASSWORD")

android {
    // Must match Kotlin source package (io.github.tytebyte_dev.rplayer)
    namespace = "io.github.tytebyte_dev.rplayer"
    compileSdk = 36

    defaultConfig {
        // Public app id (installs / Play Store)
        applicationId = "io.github.tytebyte_dev.rplayer"
        minSdk = 29
        targetSdk = 36
        versionCode = 22
        versionName = "2.2"
    }

    signingConfigs {
        create("release") {
            storeFile = signingKeystore
            storePassword = storePass
            keyAlias = keyAliasEnv
            keyPassword = keyPass
        }
    }

    buildTypes {
        debug {
            // Same keystore as release — no Android debug key
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
    // Pause (and other non-core icons) live here; material-icons-core alone is not enough
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-session:1.5.1")
    implementation("androidx.media3:media3-datasource-okhttp:1.5.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("io.coil-kt:coil-compose:2.7.0")
}
