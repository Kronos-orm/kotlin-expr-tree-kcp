import kotlinx.kover.gradle.plugin.dsl.CoverageUnit

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.kover)
}

dependencies {
    implementation(project(":expr-tree-runtime"))
    compileOnly(libs.kotlin.compiler.embeddable)
    compileOnly(libs.auto.service.annotations)
    kapt(libs.auto.service)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlin.compiler)
    testImplementation(libs.kotlin.compiler.internal.test.framework)
    testImplementation(project(":expr-tree-runtime"))
    testRuntimeOnly(libs.kotlin.stdlib)
    testRuntimeOnly(libs.kotlin.script.runtime)
    testRuntimeOnly(libs.kotlin.annotations.jvm)
}

tasks.test {
    useJUnitPlatform()
    maxHeapSize = providers.gradleProperty("exprTreeCompilerTestMaxHeap").getOrElse("2g")
    maxParallelForks = 1
    systemProperty("expr.tree.compiler.plugin.projectDir", projectDir.absolutePath)
    systemProperty("expr.tree.compiler.test.classpath", sourceSets.test.get().runtimeClasspath.asPath)
    setKotlinTestRuntimeJar("org.jetbrains.kotlin.test.kotlin-stdlib", "kotlin-stdlib")
    setKotlinTestRuntimeJar("org.jetbrains.kotlin.test.kotlin-test", "kotlin-test")
    setKotlinTestRuntimeJar("org.jetbrains.kotlin.test.kotlin-script-runtime", "kotlin-script-runtime")
    setKotlinTestRuntimeJar("org.jetbrains.kotlin.test.kotlin-annotations-jvm", "kotlin-annotations-jvm")
}

fun Test.setKotlinTestRuntimeJar(propertyName: String, jarName: String) {
    val jar = sourceSets.test.get().runtimeClasspath.files
        .firstOrNull { it.name.matches("""$jarName-\d.*\.jar""".toRegex()) }
        ?: return
    systemProperty(propertyName, jar.absolutePath)
}

kover {
    reports {
        total {
            html {
                onCheck = true
            }
            verify {
                rule("expression-tree compiler coverage guard") {
                    minBound(90, CoverageUnit.LINE)
                    minBound(70, CoverageUnit.BRANCH)
                }
            }
        }
    }
}
