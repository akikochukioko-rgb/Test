plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

/**
 * Signing credentials come only from the environment (CI / local):
 *   KEYSTORE_FILE, KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD
 *
 * KEYSTORE_FILE may be absolute, relative to this module (app/), or relative to repo root.
 * No keystore is committed to the repository.
 */
fun resolveKeystore(): File? {
    val fromEnv = System.getenv("KEYSTORE_FILE") ?: return null
    if (fromEnv.isBlank()) return null
    val candidates = listOf(
        file(fromEnv),
        rootProject.file(fromEnv),
        file(fromEnv.removePrefix("app/"))
    )
    return candidates.firstOrNull { it.exists() }.also {
        if (it == null) {
            logger.warn("KEYSTORE_FILE=$fromEnv not found (tried ${candidates.map { c -> c.absolutePath }})")
        }
    }
}

fun envRequired(name: String): String =
    System.getenv(name)?.takeIf { it.isNotBlank() }
        ?: error("Missing required environment variable: $name")

val signingKeystore = resolveKeystore()
val hasSigning = signingKeystore != null

android {
    // Must match Kotlin source package (io.github.tytebyte_dev.rplayer)
    namespace = "io.github.tytebyte_dev.rplayer"
    compileSdk = 36

    defaultConfig {
        // Public app id (installs / Play Store)
        applicationId = "io.github.tytebyte_dev.rplayer"
        minSdk = 29
        targetSdk = 36
        versionCode = 21
        versionName = "2.1"
    }

    signingConfigs {
        if (hasSigning) {
            create("release") {
                storeFile = signingKeystore
                storePassword = envRequired("KEYSTORE_PASSWORD")
                keyAlias = envRequired("KEY_ALIAS")
                keyPassword = envRequired("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            // Prefer release keystore when provided (CI); otherwise default debug key
            if (hasSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        release {
            isMinifyEnabled = false
            if (hasSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
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
