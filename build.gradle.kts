// Top-level build file.
buildscript {
    dependencies {
        // Свежий Kotlin для встроенной поддержки Kotlin в AGP 9
        classpath(libs.kotlin.gradle.plugin)
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.compose.compiler) apply false
}
