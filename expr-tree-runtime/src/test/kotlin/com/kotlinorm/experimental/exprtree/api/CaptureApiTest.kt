package com.kotlinorm.experimental.exprtree.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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
}
