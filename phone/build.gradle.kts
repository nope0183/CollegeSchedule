import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.nope.scheduletile.phone"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.nope.scheduletile.phone"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Подпись debug-ключом, чтобы релизный APK можно было поставить без настройки ключей.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    // Общий код: парсинг расписания, состояние, загрузка
    implementation(project(":core"))

    // Экран настроек (Compose + Material 3)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.activity.compose)

    // Виджет рабочего стола (Jetpack Glance, динамические цвета Material You)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)
}
