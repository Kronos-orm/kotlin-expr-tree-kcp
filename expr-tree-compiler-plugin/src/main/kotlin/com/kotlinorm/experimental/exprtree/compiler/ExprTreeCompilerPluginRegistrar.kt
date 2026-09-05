package com.kotlinorm.experimental.exprtree.compiler

import com.google.auto.service.AutoService
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.compiler.plugin.registerExtension
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar

@OptIn(ExperimentalCompilerApi::class)
@AutoService(CompilerPluginRegistrar::class)
class ExprTreeCompilerPluginRegistrar : CompilerPluginRegistrar() {
    override val pluginId: String = "expr-tree-compiler-plugin"
    override val supportsK2: Boolean = true

    override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
        FirExtensionRegistrar.registerExtension(ExprTreeFirExtensionRegistrar())
        IrGenerationExtension.registerExtension(ExprTreeIrGenerationExtension())
    }
}
