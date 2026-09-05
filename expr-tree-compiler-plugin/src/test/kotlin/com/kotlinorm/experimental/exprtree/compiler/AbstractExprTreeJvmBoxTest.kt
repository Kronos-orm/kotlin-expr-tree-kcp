package com.kotlinorm.experimental.exprtree.compiler

import org.jetbrains.kotlin.cli.jvm.config.addJvmClasspathRoots
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.test.FirParser
import org.jetbrains.kotlin.test.builders.TestConfigurationBuilder
import org.jetbrains.kotlin.test.directives.CodegenTestDirectives
import org.jetbrains.kotlin.test.directives.JvmEnvironmentConfigurationDirectives
import org.jetbrains.kotlin.test.model.TestModule
import org.jetbrains.kotlin.test.runners.codegen.AbstractFirBlackBoxCodegenTestBase
import org.jetbrains.kotlin.test.services.EnvironmentBasedStandardLibrariesPathProvider
import org.jetbrains.kotlin.test.services.EnvironmentConfigurator
import org.jetbrains.kotlin.test.services.KotlinStandardLibrariesPathProvider
import org.jetbrains.kotlin.test.services.RuntimeClasspathProvider
import org.jetbrains.kotlin.test.services.TestServices
import java.io.File

/** Runs expression-tree fixtures through FIR resolution, IR generation, JVM codegen, and box(). */
abstract class AbstractExprTreeJvmBoxTest : AbstractFirBlackBoxCodegenTestBase(FirParser.LightTree) {
    override fun createKotlinStandardLibrariesPathProvider(): KotlinStandardLibrariesPathProvider =
        EnvironmentBasedStandardLibrariesPathProvider

    override fun configure(builder: TestConfigurationBuilder) = with(builder) {
        super.configure(this)
        defaultDirectives {
            +JvmEnvironmentConfigurationDirectives.FULL_JDK
            +CodegenTestDirectives.IGNORE_DEXING
        }
        useConfigurators(::ExprTreeCompilerPluginConfigurator)
        useCustomRuntimeClasspathProviders(::ExprTreeRuntimeClasspathProvider)
    }

    protected val testDataDir: File
        get() = File(requireNotNull(System.getProperty("expr.tree.compiler.plugin.projectDir")))
            .resolve("testData")
}

abstract class AbstractExprTreeJvmBoxSuite(private val directory: String) : AbstractExprTreeJvmBoxTest() {
    protected fun box(name: String) {
        runTest(testDataDir.resolve("box").resolve(directory).resolve("$name.kt").path)
    }
}

@OptIn(ExperimentalCompilerApi::class)
private class ExprTreeCompilerPluginConfigurator(testServices: TestServices) : EnvironmentConfigurator(testServices) {
    private val registrar = ExprTreeCompilerPluginRegistrar()

    override fun configureCompilerConfiguration(configuration: CompilerConfiguration, module: TestModule) {
        configuration.addJvmClasspathRoots(exprTreeCompilerTestClasspath)
    }

    override fun CompilerPluginRegistrar.ExtensionStorage.registerCompilerExtensions(
        module: TestModule,
        configuration: CompilerConfiguration,
    ) {
        with(registrar) {
            registerExtensions(configuration)
        }
    }
}

private class ExprTreeRuntimeClasspathProvider(testServices: TestServices) : RuntimeClasspathProvider(testServices) {
    override fun runtimeClassPaths(module: TestModule): List<File> = exprTreeCompilerTestClasspath
}

private val exprTreeCompilerTestClasspath: List<File> by lazy {
    val classpath = System.getProperty("expr.tree.compiler.test.classpath")
        ?: error("Missing expr.tree.compiler.test.classpath system property")
    classpath.split(File.pathSeparator).filter(String::isNotBlank).map(::File)
}
