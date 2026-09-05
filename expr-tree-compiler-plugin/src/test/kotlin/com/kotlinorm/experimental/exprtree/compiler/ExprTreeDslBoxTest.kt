package com.kotlinorm.experimental.exprtree.compiler

import org.junit.jupiter.api.Test

class ExprTreeDslBoxTest : AbstractExprTreeJvmBoxSuite("dsl") {
    @Test
    fun annotatedLambdaParameters() = box("annotatedLambdaParameters")
}
