import kotlinx.kover.gradle.plugin.dsl.CoverageUnit

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kover)
}

dependencies { testImplementation(libs.kotlin.test) }

tasks.test { useJUnitPlatform() }

kover {
    reports {
        total {
            html {
                onCheck = true
            }
            verify {
                rule("expression-tree runtime coverage guard") {
                    minBound(90, CoverageUnit.LINE)
                    minBound(70, CoverageUnit.BRANCH)
                }
            }
        }
    }
}
