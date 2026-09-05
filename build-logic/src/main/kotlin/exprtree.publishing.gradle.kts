import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinJvm
import com.vanniktech.maven.publish.GradlePlugin
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import org.gradle.plugins.signing.Sign

plugins {
    id("com.vanniktech.maven.publish")
}

group = "com.kotlinorm.experimental"
version = providers.gradleProperty("exprTreeVersion").getOrElse("0.1.0-SNAPSHOT")

configure<MavenPublishBaseExtension> {
    val platform = if (pluginManager.hasPlugin("java-gradle-plugin")) {
        GradlePlugin(JavadocJar.Javadoc(), sourcesJar = true)
    } else {
        KotlinJvm(JavadocJar.Javadoc(), sourcesJar = true)
    }
    configure(platform)
    coordinates(group.toString(), project.name, version.toString())

    pom {
        name.set("${group}:${project.name}")
        description.set("Kotlin expression tree ${project.name} module")
        url.set("https://github.com/ousc/kotlin-expr-tree-kcp")
        licenses {
            license {
                name.set("MIT License")
                url.set("https://opensource.org/license/mit")
                distribution.set("https://opensource.org/license/mit")
            }
        }
        developers {
            developer {
                id.set("ousc")
                name.set("ousc")
                email.set("750154432@qq.com")
            }
        }
        scm {
            url.set("https://github.com/ousc/kotlin-expr-tree-kcp")
            connection.set("scm:git:https://github.com/ousc/kotlin-expr-tree-kcp.git")
            developerConnection.set("scm:git:ssh://git@github.com/ousc/kotlin-expr-tree-kcp.git")
        }
    }

    publishToMavenCentral()
}

tasks.withType<Sign>().configureEach {
    onlyIf {
        providers.gradleProperty("signingInMemoryKey").isPresent
    }
}
