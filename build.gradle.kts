// Top-level build file — declares plugin versions once so app/build.gradle.kts
// can apply them without repeating version numbers (the version catalog in
// gradle/libs.versions.toml is the other half of this — that's where the
// actual dependency coordinates live).
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.detekt) apply false
}
