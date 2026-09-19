buildscript {
    dependencies {
        // AGP 9 compiles Kotlin itself ("built-in Kotlin") using the KGP version it
        // bundles. Putting newer versions on the root buildscript classpath is the
        // supported way to run a more recent Kotlin/KSP than AGP's defaults.
        classpath(libs.kotlin.gradle.plugin)
        classpath(libs.ksp.gradle.plugin)
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.hilt.android) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.mannodermaus.android.junit5) apply false
}
