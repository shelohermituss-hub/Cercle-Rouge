pluginManagement {
    repositories {
        google()
        // Miroir de Maven Central opéré par Google : évite les refus « 429 Too Many Requests » de Central.
        maven("https://maven-central.storage-download.googleapis.com/maven2/")
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        maven("https://maven-central.storage-download.googleapis.com/maven2/")
        mavenCentral()
    }
}
rootProject.name = "cercle-rouge"
include(":app", ":core-domain", ":alert-engine", ":privacy-engine", ":contacts-engine")
