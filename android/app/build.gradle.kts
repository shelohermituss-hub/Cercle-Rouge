plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.cerclerouge.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.cerclerouge.app"
        minSdk = 29
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    lint {
        warningsAsErrors = true
        abortOnError = true
        // Les versions sont figées volontairement dans libs.versions.toml (mise à jour manuelle et revue).
        disable += setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion")
    }
}
android.testOptions.unitTests.isIncludeAndroidResources = true

// Robolectric télécharge android-all : même miroir que settings.gradle.kts (Maven Central limite le débit).
tasks.withType<Test>().configureEach {
    jvmArgs("--add-exports=java.base/jdk.internal.access=ALL-UNNAMED", "--add-opens=java.base/java.io=ALL-UNNAMED")
    systemProperty("robolectric.dependency.repo.url", "https://maven-central.storage-download.googleapis.com/maven2")
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

dependencies {
    implementation(project(":alert-engine"))
    implementation(project(":privacy-engine"))
    implementation(project(":contacts-engine"))
    implementation(project(":location-engine"))
    implementation(project(":audio-engine"))
    implementation(project(":evidence-engine"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.biometric)
    implementation(libs.tink.android)
    implementation(libs.maplibre)
    // Position fusionnée de Google (batterie, précision) ; repli LocationManager sans services Google (Q15, ADR-0019).
    implementation(libs.play.services.location)
    // Orbes animées (MIT, portage de « Thinking Orbs »). NON transitive : sa description tire Compose Multiplatform et
    // Material3 1.5 alpha, qui écrasaient nos versions et faisaient planter les champs de texte (NoSuchElementException).
    implementation(libs.orbmotion) { isTransitive = false }
    testImplementation(libs.kotlin.test)
    // Rendu des écrans sur la JVM (captures PNG dans build/screenshots), sans émulateur.
    testImplementation(libs.robolectric)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
