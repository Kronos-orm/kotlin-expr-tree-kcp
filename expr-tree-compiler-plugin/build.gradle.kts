plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.kapt)
}

dependencies {
    implementation(project(":expr-tree-runtime"))
    compileOnly(libs.kotlin.compiler.embeddable)
    compileOnly(libs.auto.service.annotations)
    kapt(libs.auto.service)

    testImplementation(libs.kotlin.test)
    testImplementation(project(":expr-tree-runtime"))
}

tasks.test {
    useJUnitPlatform()
}
