package com.kotlinorm.experimental.exprtree.compiler

import com.kotlinorm.experimental.exprtree.api.ConstExpr
import com.kotlinorm.experimental.exprtree.api.ExprId
import com.kotlinorm.experimental.exprtree.api.ExprTree
import com.kotlinorm.experimental.exprtree.api.TypeRef
import kotlin.test.Test
import kotlin.test.assertEquals

class ExprTreeCompilerStateTest {
    @Test
    fun `capture registry can be reset and inspected`() {
        ExprCaptureRegistry.clear()
        val tree = ExprTree<Any?, Any?>(
            parameters = emptyList(),
            captures = emptyList(),
            body = ConstExpr(ExprId(1), TypeRef("kotlin.Int"), 1),
        )
        val summary = CapturedCallSummary(listOf(CapturedLambdaSummary(0, 42, tree)))
        ExprCaptureRegistry.record(summary)
        assertEquals(listOf(summary), ExprCaptureRegistry.snapshot())
        assertEquals(tree, ExprCaptureRegistry.treeForLambdaAt(42))
        assertEquals(null, ExprCaptureRegistry.treeForLambdaAt(99))
        ExprCaptureRegistry.clear()
        assertEquals(emptyList(), ExprCaptureRegistry.snapshot())
    }
}
