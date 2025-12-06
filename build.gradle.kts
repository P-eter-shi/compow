// Top-level build file where you can add configuration options common to all sub-projects/modules.
// The legacy 'buildscript' block has been removed.
// Plugin versions are now managed by the version catalog (libs.versions.toml)
// and plugin repositories are managed in settings.gradle.kts.

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.google.services) apply false
}