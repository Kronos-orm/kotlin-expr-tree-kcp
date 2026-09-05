plugins {
    kotlin("jvm")
}

evaluationDependsOn(":expr-tree-compiler-plugin")

dependencies {
    implementation(project(":expr-tree-runtime"))
    testImplementation(kotlin("test"))
}

val compilerPluginJar = project(":expr-tree-compiler-plugin").tasks.named("jar")

tasks.compileKotlin {
    dependsOn(compilerPluginJar)
    compilerOptions.freeCompilerArgs.addAll(
        "-Xplugin=${project(":expr-tree-compiler-plugin").layout.buildDirectory.file("libs/expr-tree-compiler-plugin-${project.version}.jar").get().asFile.absolutePath}",
    )
}
