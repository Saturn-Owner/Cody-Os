import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Nur lokaler Signierschlüssel für Release-artige Test-Builds auf dem Echo.
// Selbstsigniert, nicht für Distribution. Fehlt er, bleibt der Release-Build
// unsigniert/nicht installierbar; für CI-artige Builds ist das in Ordnung.
val keystorePropsFile = rootProject.file("keystore/keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
}

// Gateway-Endpunkt — nie fest verdrahten. Kopiere gradle.properties.example nach
// gradle.properties (gitignored) und trage deine Gateway-URLs ein. Die
// Platzhalter unten lassen das Projekt auch ohne lokale Konfiguration bauen.
val gatewayHttpsUrl = (project.findProperty("CODY_HOME_HTTPS_URL") as String?)
    ?: "https://your-domain.example/cody-home"
val gatewayWssUrl = (project.findProperty("CODY_HOME_WSS_URL") as String?)
    ?: "wss://your-domain.example/cody-home/ws"

android {
    namespace = "com.cody.home"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.cody.home"
        // Echo Show 5 (checkers) läuft mit LineageOS 18.1 = Android 11 = API 30.
        minSdk = 30
        targetSdk = 30
        versionCode = 1
        versionName = "0.1.0-alpha"

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
        // Reine Play-Store-Policy-Prüfung — hier irrelevant, weil diese App
        // nicht über Play verteilt wird. targetSdk=30 ist bewusst gewählt:
        // Es entspricht Android 11 auf dem Echo Show 5.
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

    // Netzwerk-Layer: REST + WebSocket zum Cody Home Gateway.
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    // Verschlüsselte Gerätespeicherung für das device_id/device_secret-Paar.
    implementation("androidx.security:security-crypto:1.0.0")
}
