plugins {
    kotlin("jvm")
}

dependencies {
    api(project(":expr-tree-compiler-plugin"))
    implementation("org.jetbrains.kotlin:kotlin-maven-plugin:2.4.0")
    implementation("org.apache.maven:maven-core:3.9.11")
}

