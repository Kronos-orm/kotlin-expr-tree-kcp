plugins {
    kotlin("jvm")
    `java-gradle-plugin`
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin-api:2.4.0")
}

gradlePlugin {
    plugins {
        create("exprTree") {
            id = "com.kotlinorm.experimental.expr-tree"
            implementationClass = "com.kotlinorm.experimental.exprtree.gradle.ExprTreeGradlePlugin"
        }
    }
}
