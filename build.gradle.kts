import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.gradle.api.tasks.compile.JavaCompile

plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.kapt) apply false
    alias(libs.plugins.kover) apply false
    alias(libs.plugins.expr.tree.publishing) apply false
}

allprojects {
    group = "com.kotlinorm.experimental"
    version = providers.gradleProperty("exprTreeVersion").getOrElse("0.1.0")

    configurations.configureEach {
        resolutionStrategy.dependencySubstitution {
            substitute(module("com.kotlinorm.experimental:expr-tree-compiler-plugin"))
                .using(project(":expr-tree-compiler-plugin"))
        }
    }
}

subprojects {
    plugins.withId("org.jetbrains.kotlin.jvm") {
        tasks.withType<KotlinCompile>().configureEach {
            compilerOptions {
                jvmTarget.set(JvmTarget.JVM_1_8)
            }
        }
        tasks.withType<JavaCompile>().configureEach {
            sourceCompatibility = "1.8"
            targetCompatibility = "1.8"
        }
    }
}

subprojects {
    if (name in setOf("expr-tree-runtime", "expr-tree-compiler-plugin", "expr-tree-maven-plugin")) {
        pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
            apply(plugin = "exprtree.publishing")
        }
    }
}

val publishableModules = listOf("expr-tree-runtime", "expr-tree-compiler-plugin", "expr-tree-maven-plugin")

tasks.register("publishAllToMavenLocal") {
    group = "publishing"
    dependsOn(publishableModules.map { ":$it:publishToMavenLocal" })
    dependsOn(gradle.includedBuild("expr-tree-gradle-plugin").task(":publishToMavenLocal"))
}

tasks.register("publishAllToMavenCentral") {
    group = "publishing"
    dependsOn(publishableModules.map { ":$it:publishAllPublicationsToMavenCentralRepository" })
    dependsOn(gradle.includedBuild("expr-tree-gradle-plugin").task(":publishAllPublicationsToMavenCentralRepository"))
}
