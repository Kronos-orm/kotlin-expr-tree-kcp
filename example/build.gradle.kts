plugins {
    alias(libs.plugins.kotlin.jvm)
    id("com.kotlinorm.experimental.expr-tree")
}

dependencies {
    implementation(project(":expr-tree-runtime"))
}
