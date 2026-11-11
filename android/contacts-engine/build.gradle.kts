// Logique pure de contacts-engine (contacts, destinataires, anti-spam, gabarits SMS, position) : testable en JVM, sans Android (R-CE-01).
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
