import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

// Release signing — self-signed key, never committed (see .gitignore).
// Loaded from a properties file rather than
// hardcoded here so the actual passwords never land in source control even
// by accident. Missing file means release builds simply aren't signed
// (assembleDebug/ktlintCheck/detekt etc. are all unaffected) rather than a
// hard Gradle sync failure for anyone who clones this repo without the key.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties =
    Properties().apply {
        if (keystorePropertiesFile.exists()) {
            keystorePropertiesFile.inputStream().use { load(it) }
        }
    }

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

android {
    namespace = "com.ventus.sys"
    // Bumped from 35 to 36 solely because androidx.browser:1.10.0 (pinned
    // for the AppAuth #977-adjacent Custom Tabs fix) requires it — targetSdk
    // stays 35 (compileSdk just grants access to newer APIs at compile
    // time; targetSdk is the actual runtime-behavior opt-in and changing it
    // is a separate, more deliberate decision). This is a same-major-line
    // AGP 8.x patch bump (8.7.2 -> 8.13.0), not the AGP 9 breaking jump,
    // which is deliberately deferred for later.
    compileSdk = 36

    defaultConfig {
        applicationId = "com.ventus.sys"
        // minSdk 26 (Android 8.0) — floor chosen for EncryptedSharedPreferences
        // and modern foreground-service APIs without excluding much of the
        // real install base (Android 8.0 released 2017).
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0-mvp"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Redirect URI for the PKCE OAuth flow: ventus://oauth/callback
        // AppAuth reads these manifest placeholders to wire up the
        // RedirectUriReceiverActivity's intent-filter (see AndroidManifest.xml).
        manifestPlaceholders["appAuthRedirectScheme"] = "ventus"
    }

    signingConfigs {
        if (keystorePropertiesFile.exists()) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // R8 shrinking + obfuscation - see proguard-rules.pro for exactly
            // which libraries need explicit keep rules (kotlinx.serialization,
            // Retrofit, AppAuth, security-crypto/Tink) and why. Live-tested
            // end-to-end on a real device before shipping: login, live
            // scoring, vault, and every other screen against a real Spotify
            // account, not just "it built."
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

ktlint {
    // Kotlin's official style (used by the Kotlin team itself, not the older
    // "android" experimental style) — matches kotlin.code.style=official
    // already set in gradle.properties.
    version.set("1.8.0")
    android.set(true)
    ignoreFailures.set(false)
}

detekt {
    // Generated via `./gradlew detektGenerateConfig` then tuned against two
    // real findings against the existing placeholder source (FunctionNaming
    // vs. @Composable's PascalCase convention, MagicNumber vs. Color.kt's
    // hex literals) — see config/detekt/detekt.yml for the specifics.
    config.setFrom(file("$rootDir/config/detekt/detekt.yml"))
    buildUponDefaultConfig = true
}

dependencies {
    implementation(libs.core.ktx)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.navigation.compose)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.retrofit.core)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp.core)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.appauth)
    implementation(libs.androidx.browser)
    implementation(libs.security.crypto)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    implementation(libs.coil.compose)

    // Debug-only leak detector — NowPlayingService's foreground-service
    // lifetime is exactly the kind of long-lived-object pattern that leaks
    // an Activity/ViewModel if a callback outlives its owner, and several
    // StateFlow-collecting screens sit on top of it. Never shipped in a
    // release build (debugImplementation only).
    debugImplementation(libs.leakcanary.android)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.room.testing)
    androidTestImplementation(platform(libs.compose.bom))
}
