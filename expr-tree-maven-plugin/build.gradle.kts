plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    api(project(":expr-tree-compiler-plugin"))
    implementation(libs.kotlin.maven.plugin)
    implementation(libs.maven.core)
}
