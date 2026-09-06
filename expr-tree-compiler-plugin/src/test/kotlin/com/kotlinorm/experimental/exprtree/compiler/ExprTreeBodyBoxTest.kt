package com.kotlinorm.experimental.exprtree.compiler

import org.junit.jupiter.api.Test

class ExprTreeBodyBoxTest : AbstractExprTreeJvmBoxSuite("body") {
    @Test
    fun expressionFamilies() = box("expressionFamilies")

    @Test
    fun expressionMatrix() = box("expressionMatrix")

    @Test
    fun branchBoundaries() = box("branchBoundaries")

    @Test
    fun tryCatchFinally() = box("tryCatchFinally")
}
