package com.kotlinorm.experimental.exprtree.maven

import org.apache.maven.plugin.MojoExecution
import org.apache.maven.project.MavenProject
import org.jetbrains.kotlin.maven.KotlinMavenPluginExtension
import org.jetbrains.kotlin.maven.PluginOption

class ExprTreeMavenPlugin : KotlinMavenPluginExtension {
    override fun isApplicable(project: MavenProject, execution: MojoExecution): Boolean = true

    override fun getCompilerPluginId(): String = "expr-tree-compiler-plugin"

    override fun getPluginOptions(project: MavenProject, execution: MojoExecution): MutableList<PluginOption> = mutableListOf()
}
