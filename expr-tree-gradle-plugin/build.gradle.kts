plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-gradle-plugin`
    alias(libs.plugins.expr.tree.publishing)
}

dependencies {
    implementation(libs.kotlin.gradle.plugin.api)
}

group = "com.kotlinorm.experimental"
version = providers.gradleProperty("exprTreeVersion").getOrElse("0.1.0")

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

kotlin {
    compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8)
}

gradlePlugin {
    plugins {
        create("exprTree") {
            id = "com.kotlinorm.experimental.expr-tree"
            implementationClass = "com.kotlinorm.experimental.exprtree.gradle.ExprTreeGradlePlugin"
        }
    }
}
