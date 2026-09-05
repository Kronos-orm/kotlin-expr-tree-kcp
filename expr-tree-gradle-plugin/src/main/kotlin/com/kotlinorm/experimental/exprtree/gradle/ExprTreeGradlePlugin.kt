package com.kotlinorm.experimental.exprtree.gradle

import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption

class ExprTreeGradlePlugin : KotlinCompilerPluginSupportPlugin {
    override fun apply(target: Project) = Unit

    override fun isApplicable(kotlinCompilation: KotlinCompilation<*>): Boolean = true

    override fun getCompilerPluginId(): String = "expr-tree-compiler-plugin"

    override fun getPluginArtifact(): SubpluginArtifact = SubpluginArtifact(
        groupId = "com.kotlinorm.experimental",
        artifactId = "expr-tree-compiler-plugin",
        version = "0.1.0",
    )

    override fun applyToCompilation(kotlinCompilation: KotlinCompilation<*>): org.gradle.api.provider.Provider<List<SubpluginOption>> =
        kotlinCompilation.target.project.provider { emptyList() }
}
