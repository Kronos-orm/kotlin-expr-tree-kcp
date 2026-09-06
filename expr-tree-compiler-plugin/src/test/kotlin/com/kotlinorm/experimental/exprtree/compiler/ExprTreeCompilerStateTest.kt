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
        val summary = CapturedCallSummary(listOf(CapturedLambdaSummary(0, "/source/state.kt", 42, tree)))
        ExprCaptureRegistry.record(summary)
        assertEquals(listOf(tree), ExprCaptureRegistry.snapshot())
        assertEquals(tree, ExprCaptureRegistry.treeForLambdaAt("/source/state.kt", 42))
        assertEquals(null, ExprCaptureRegistry.treeForLambdaAt("other.kt", 42))
        assertEquals(tree, ExprCaptureRegistry.takeTreeForLambdaAt("/source/state.kt", 42))

        ExprCaptureRegistry.record(summary)
        assertEquals(tree, ExprCaptureRegistry.takeTreeForLambdaAt("state.kt", 42))
        assertEquals(null, ExprCaptureRegistry.takeTreeForLambdaAt("other.kt", 42))
        assertEquals(emptyList(), ExprCaptureRegistry.snapshot())
        ExprCaptureRegistry.clear()
        assertEquals(emptyList(), ExprCaptureRegistry.snapshot())
    }

    @Test
    fun `capture registry rejects an ambiguous filename fallback`() {
        ExprCaptureRegistry.clear()
        val tree = ExprTree<Any?, Any?>(
            parameters = emptyList(),
            captures = emptyList(),
            body = ConstExpr(ExprId(1), TypeRef("kotlin.Int"), 1),
        )
        ExprCaptureRegistry.record(
            CapturedCallSummary(
                listOf(
                    CapturedLambdaSummary(0, "/first/shared.kt", 7, tree),
                    CapturedLambdaSummary(1, "/second/shared.kt", 7, tree),
                )
            )
        )

        assertEquals(null, ExprCaptureRegistry.takeTreeForLambdaAt("shared.kt", 7))
        assertEquals(2, ExprCaptureRegistry.snapshot().size)
        ExprCaptureRegistry.clear()
    }
}
