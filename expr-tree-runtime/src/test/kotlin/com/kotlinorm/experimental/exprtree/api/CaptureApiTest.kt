package com.kotlinorm.experimental.exprtree.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CaptureApiTest {
    private val capture = CaptureDecl(DeclId(7), "minimum", TypeRef("kotlin.Int"))
    private val tree = ExprTree<Any, Boolean>(
        parameters = emptyList(),
        captures = listOf(capture),
        body = ConstExpr(ExprId(1), TypeRef("kotlin.Boolean"), true),
    )

    @Test
    fun `captured expression binds values by declared slot order`() {
        val bindings = CapturedExpr(tree, arrayOf<Any?>(18)).bindings()
        assertEquals(18, bindings[capture.id])
        assertEquals(mapOf(capture.id to 18), bindings.asMap())
    }

    @Test
    fun `captured expression rejects a mismatched slot count`() {
        assertFailsWith<IllegalArgumentException> { CapturedExpr(tree, emptyArray()) }
    }

    @Test
    fun `captured expression rejects incompatible ABI and schema versions`() {
        assertFailsWith<ExprTreeAbiException> { CapturedExpr(tree, arrayOf<Any?>(18), abiVersion = 2) }
        assertFailsWith<ExprTreeAbiException> {
            CapturedExpr(tree.copy(schemaVersion = 99), arrayOf<Any?>(18))
        }
    }

    @Test
    fun `registry provides generated tree lookup without compiler dependencies`() {
        ExprTreeRegistry.clear()
        ExprTreeRegistry.register("sample#1", tree)
        val captured: CapturedExpr<Any, Boolean> = captureTree("sample#1", arrayOf<Any?>(18))

        assertEquals(18, captured.bindings()[capture.id])
        assertTrue("sample#1" in ExprTreeRegistry.keys())
        assertFailsWith<ExprTreeAbiException> { ExprTreeRegistry.lookup("missing") }
    }

    @Test
    fun `capture carrier preserves invocation and nullable lookup`() {
        val executable: (Any) -> Boolean = { it is Int && it > 0 }
        assertNull(executable.capturedExprOrNull())
        val carrier = CapturedLambda<Any, Boolean>(executable, CapturedExpr(tree, arrayOf<Any?>(18)))
        assertEquals(true, carrier(2))
        assertEquals(carrier.capturedExpr, carrier.capturedExprOrNull())
        assertEquals(carrier.capturedExpr, carrier.capturedExpr)
        assertEquals(carrier.capturedExpr.hashCode(), carrier.capturedExpr.hashCode())
    }

    @Test
    fun `registry rejects blank keys incompatible schemas and conflicting values`() {
        assertFailsWith<IllegalArgumentException> { ExprTreeRegistry.register("", tree) }
        assertFailsWith<IllegalArgumentException> {
            ExprTreeRegistry.register("bad-schema", tree.copy(schemaVersion = 99))
        }
        ExprTreeRegistry.register("conflict", tree)
        assertFailsWith<IllegalArgumentException> {
            ExprTreeRegistry.register("conflict", tree.copy(body = ConstExpr(ExprId(2), tree.body.type, false)))
        }
        assertFailsWith<ExprTreeAbiException> { captureTree<Any, Boolean>("missing", emptyArray()) }
    }

    @Test
    fun `capture bindings reject duplicate declarations`() {
        assertFailsWith<IllegalArgumentException> {
            CaptureBindings.of(CaptureBinding(DeclId(1), 1), CaptureBinding(DeclId(1), 2))
        }
        assertEquals(mapOf(DeclId(1) to "x"), CaptureBindings.of(CaptureBinding(DeclId(1), "x")).asMap())
    }
}
