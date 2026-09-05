package com.kotlinorm.experimental.exprtree.compiler

import org.junit.jupiter.api.Test

class ExprTreeNestedBoxTest : AbstractExprTreeJvmBoxSuite("nested") {
    @Test
    fun nestedLambdasAndScopes() = box("nestedLambdasAndScopes")
}
