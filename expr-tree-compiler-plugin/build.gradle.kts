plugins {
    kotlin("jvm")
    kotlin("kapt")
}

tasks.jar {
    archiveBaseName.set("expr-tree-compiler-plugin")
    val runtimeJar = project(":expr-tree-runtime").tasks.named<Jar>("jar")
    dependsOn(runtimeJar)
    from(runtimeJar.map { zipTree(it.archiveFile) })
}

val kotlinVersion = "2.4.0"

dependencies {
    implementation(project(":expr-tree-runtime"))
    compileOnly("org.jetbrains.kotlin:kotlin-compiler-embeddable:$kotlinVersion")
    compileOnly("com.google.auto.service:auto-service-annotations:1.1.1")
    kapt("com.google.auto.service:auto-service:1.1.1")

    testImplementation(kotlin("test"))
    testImplementation(project(":expr-tree-runtime"))
}

tasks.test {
    useJUnitPlatform()
}
