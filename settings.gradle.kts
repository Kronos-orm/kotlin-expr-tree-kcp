pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
    }
}

rootProject.name = "kotlin-expr-tree-kcp"

include(":expr-tree-compiler-plugin")
include(":expr-tree-runtime")
include(":expr-tree-gradle-plugin")
include(":expr-tree-maven-plugin")
include(":example")
