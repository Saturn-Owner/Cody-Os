import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Local-only signing key for installing release-type builds on the Echo for testing —
// self-signed, not for distribution. Not present -> release build stays unsigned/uninstallable,
// which is fine for CI-style builds that don't need to land on the device.
val keystorePropsFile = rootProject.file("keystore/keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
}

// Gateway endpoint — never hardcoded. Copy gradle.properties.example to
// gradle.properties (gitignored) and fill in your own Gateway's URLs; the
// placeholders below let the project still build out of the box for anyone
// who hasn't set that up yet — see GatewayConfig.kt for where these land.
val gatewayHttpsUrl = (project.findProperty("CODY_HOME_HTTPS_URL") as String?)
    ?: "https://your-domain.example/cody-home"
val gatewayWssUrl = (project.findProperty("CODY_HOME_WSS_URL") as String?)
    ?: "wss://your-domain.example/cody-home/ws"

android {
    namespace = "com.cody.home"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.cody.home"
        // Echo Show 5 (checkers) runs LineageOS 18.1 = Android 11 = API 30.
        minSdk = 30
        targetSdk = 30
        versionCode = 1
        versionName = "0.1.0-v1a"

        buildConfigField("String", "GATEWAY_HTTPS_URL", "\"$gatewayHttpsUrl\"")
        buildConfigField("String", "GATEWAY_WSS_URL", "\"$gatewayWssUrl\"")
    }

    signingConfigs {
        if (keystorePropsFile.exists()) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (keystorePropsFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    lint {
        // Play-Store-only policy check — irrelevant here, this app is never
        // distributed through Play. targetSdk=30 is deliberate: it matches the
        // Echo Show 5's actual Android 11, not a value to "catch up" blindly.
        disable += "ExpiredTargetSdkVersion"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")

    // Network layer: REST + WebSocket to the Cody Gateway.
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    // Encrypted on-device storage for the device_id/device_secret pair.
    implementation("androidx.security:security-crypto:1.0.0")
}
