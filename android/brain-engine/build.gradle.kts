// Cerveau IA, logique pure (ADR-0029) : journal de situation, garde de sécurité, règles, base de connaissances,
// lecture des appels de fonction du modèle. Testable en JVM, sans Android.
plugins { alias(libs.plugins.kotlin.jvm) }
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
dependencies {
    api(project(":alert-engine"))
    testImplementation(libs.kotlin.test)
}
