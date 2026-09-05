import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.gradle.api.tasks.compile.JavaCompile

plugins {
    kotlin("jvm") version "2.4.0" apply false
    kotlin("kapt") version "2.4.0" apply false
}

allprojects {
    group = "com.kotlinorm.experimental"
    version = "0.1.0-SNAPSHOT"
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
