import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.nope.scheduletile"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.nope.scheduletile"
        minSdk = 30
        targetSdk = 36
        versionCode = 2
        versionName = "2.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Подпись debug-ключом, чтобы релизный APK можно было поставить через adb без настройки ключей.
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

    // Экран настроек: Compose for Wear OS, Material 3 Expressive
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.activity.compose)
    implementation(libs.wear.compose.material3)
    implementation(libs.wear.compose.foundation)

    // Виджет (Tile) на Material 3
    implementation(libs.androidx.tiles)
    implementation(libs.androidx.protolayout)
    implementation(libs.androidx.protolayout.material3)

    // Комплекция для циферблата
    implementation(libs.androidx.watchface.complications.data.source.ktx)
}
