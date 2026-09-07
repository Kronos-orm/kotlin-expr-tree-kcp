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

    @Test
    fun controlTransfers() = box("controlTransfers")

    @Test
    fun operatorAndInfix() = box("operatorAndInfix")

    @Test
    fun destructuring() = box("destructuring")

    @Test
    fun ranges() = box("ranges")

    @Test
    fun indexAccess() = box("indexAccess")
}
