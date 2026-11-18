plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ktlint) apply false
}

subprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
}

/**
 * Couverture de la logique (1.22) : JaCoCo sur les modules Kotlin purs (cerveau, moteurs, contacts, preuves…). Le seuil est
 * vérifié par `check` ; le rapport HTML est dans `<module>/build/reports/jacoco/`.
 */
val logicModules = setOf("core-domain", "alert-engine", "brain-engine", "contacts-engine", "location-engine", "evidence-engine", "audio-engine", "sensor-engine")
configure(subprojects.filter { it.name in logicModules }) {
    apply(plugin = "jacoco")
    plugins.withId("org.jetbrains.kotlin.jvm") { configureCoverage() }
}

fun Project.configureCoverage() {
    tasks.withType<Test>().configureEach { finalizedBy("jacocoTestReport") }
    tasks.named<JacocoReport>("jacocoTestReport") {
        dependsOn("test")
        reports { xml.required.set(true); html.required.set(true) }
    }
    tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
        dependsOn("test")
        violationRules {
            rule {
                limit {
                    counter = "LINE"
                    minimum = (findProperty("cr.coverage.min") as String? ?: "0.90").toBigDecimal()
                }
            }
        }
    }
    tasks.named("check") { dependsOn("jacocoTestCoverageVerification") }
}

/** Interdit `!!` dans le code de production (exigence de la définition de « terminé »). */
val noBangBang = tasks.register("noBangBang") {
    val sources = fileTree(rootDir) { include("**/src/main/**/*.kt"); exclude("**/build/**") }
    inputs.files(sources)
    doLast {
        val offenders = sources.files.filter { f -> f.readLines().any { "!!" in it.substringBefore("//") } }
        check(offenders.isEmpty()) { "`!!` interdit dans le code de production :\n" + offenders.joinToString("\n") }
    }
}

/** Les clés de chaînes FR (values/) doivent exister en créole (values-ht/) et en anglais (values-en/). */
val i18nCheck = tasks.register("i18nCheck") {
    val res = file("app/src/main/res")
    inputs.dir(res)
    doLast {
        // Les chaînes marquées translatable="false" (ex. le nom de marque) ne se traduisent pas.
        val keyRegex = Regex("""<string name="([^"]+)"(?![^>]*translatable="false")""")
        fun keys(dir: String) = res.resolve("$dir/strings.xml").takeIf { it.exists() }
            ?.readText()?.let { t -> keyRegex.findAll(t).map { it.groupValues[1] }.toSet() } ?: emptySet()
        val fr = keys("values")
        val missing = listOf("values-ht", "values-en").associateWith { fr - keys(it) }.filterValues { it.isNotEmpty() }
        check(missing.isEmpty()) { "Traductions manquantes : $missing" }
    }
}

tasks.register("check") {
    group = "verification"
    description = "Vérifications du projet : ktlint, tests, Lint, `!!`, i18n."
    dependsOn(noBangBang, i18nCheck)
    dependsOn(subprojects.map { "${it.path}:check" })
}
