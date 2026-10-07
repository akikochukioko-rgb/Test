import java.util.Base64

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

/**
 * Prefer KEYSTORE_FILE + KEYSTORE_PASSWORD / KEY_ALIAS / KEY_PASSWORD from the environment (CI).
 * Fallback: decode app/rplayer-test.keystore.b64 and use tytebyte-dev credentials.
 *
 * KEYSTORE_FILE may be absolute, relative to this module (app/), or relative to repo root.
 */
fun resolveKeystore(): File {
    val fromEnv = System.getenv("KEYSTORE_FILE")
    if (!fromEnv.isNullOrBlank()) {
        val candidates = listOf(
            file(fromEnv),                     // as given (absolute or relative to app/)
            rootProject.file(fromEnv),         // relative to repo root
            file(fromEnv.removePrefix("app/")) // KEYSTORE_FILE=app/foo.keystore from CI
        )
        candidates.firstOrNull { it.exists() }?.let { return it }
        logger.warn("KEYSTORE_FILE=$fromEnv not found (tried ${candidates.map { it.absolutePath }}); using committed keystore")
    }
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

fun envOrDefault(name: String, default: String): String =
    System.getenv(name)?.takeIf { it.isNotBlank() } ?: default

val signingKeystore = resolveKeystore()
val storePass = envOrDefault("KEYSTORE_PASSWORD", "tytebytedevbsD5gW")
val keyAliasEnv = envOrDefault("KEY_ALIAS", "tytebyte-dev")
val keyPass = envOrDefault("KEY_PASSWORD", "tytebytedevbsD5gW")

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
        create("release") {
            storeFile = signingKeystore
            storePassword = storePass
            keyAlias = keyAliasEnv
            keyPassword = keyPass
        }
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
