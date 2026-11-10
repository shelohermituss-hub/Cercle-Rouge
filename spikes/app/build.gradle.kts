plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.cerclerouge.spikes"
    compileSdk = 36

    defaultConfig {
        // Identifiant distinct de l'app réelle : les prototypes sont jetables (S0.18–S0.21).
        applicationId = "com.cerclerouge.spikes"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "0.1-spike"
        // ./gradlew assembleDebug -PphoneOnly : APK plus léger, sans les bibliothèques x86 de l'émulateur.
        if (project.hasProperty("phoneOnly")) ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    androidResources { noCompress += "tflite" }
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

dependencies {
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("com.google.ai.edge.litert:litert:1.4.2")
    implementation("com.google.crypto.tink:tink-android:1.24.0")
    implementation("com.google.android.gms:play-services-location:21.4.0")
}
