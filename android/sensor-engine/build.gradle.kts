// Logique pure de sensor-engine (état de l'appareil, détections par capteurs) : testable en JVM, sans Android.
plugins { alias(libs.plugins.kotlin.jvm) }
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
dependencies {
    api(project(":core-domain"))
    testImplementation(libs.kotlin.test)
}
