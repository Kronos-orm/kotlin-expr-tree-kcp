package com.kotlinorm.experimental.exprtree.compiler

import org.junit.jupiter.api.Test

class ExprTreeCaptureBoxTest : AbstractExprTreeJvmBoxSuite("capture") {
    @Test
    fun lexicalValuesAndObjectReferences() = box("lexicalValuesAndObjectReferences")
}
