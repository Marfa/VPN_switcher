plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

import java.util.Properties

val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) load(file.inputStream())
}

android {
    namespace = "dev.themarfa.vpnswitcher"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.themarfa.vpnswitcher"
        minSdk = 29
        targetSdk = 35
        versionCode = 18
        versionName = "0.4.4"
    }

    signingConfigs {
        create("release") {
            if (keystoreProperties.isNotEmpty()) {
                storeFile = rootProject.file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
        aidl = true
    }
}

dependencies {
    val shizukuVersion = "13.1.5"

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.coordinatorlayout:coordinatorlayout:1.2.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("dev.rikka.shizuku:api:$shizukuVersion")
    implementation("dev.rikka.shizuku:provider:$shizukuVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
