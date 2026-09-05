package com.kotlinorm.experimental.exprtree.compiler

import org.jetbrains.kotlin.fir.extensions.FirExtensionApiInternals
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar

@OptIn(FirExtensionApiInternals::class)
class ExprTreeFirExtensionRegistrar : FirExtensionRegistrar() {
    override fun ExtensionRegistrarContext.configurePlugin() {
        +::ExprTreeCheckersExtension
    }
}
