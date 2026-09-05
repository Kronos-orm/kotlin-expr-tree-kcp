package example

import com.kotlinorm.experimental.exprtree.api.BinaryExpr
import com.kotlinorm.experimental.exprtree.api.BlockExpr
import com.kotlinorm.experimental.exprtree.api.LocalDeclarationExpr
import com.kotlinorm.experimental.exprtree.api.RefKind
import com.kotlinorm.experimental.exprtree.api.SafeCallExpr
import com.kotlinorm.experimental.exprtree.api.collect
import com.kotlinorm.experimental.exprtree.api.debugString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class GeneratedAstTest {
    @Test
    fun `compiler generates runtime ast and binds lexical captures`() {
        val captured = sample(18, "A")
        val root = assertIs<BinaryExpr>(captured.tree.body)
        assertEquals("kotlin.Boolean.and", root.operator.callableId)
        assertEquals(2, captured.tree.captures.size)
        assertEquals(listOf("minAge", "prefix"), captured.tree.captures.map { it.name })
        assertEquals(18, captured.bindings()[captured.tree.captures[0].id])
        assertEquals("A", captured.bindings()[captured.tree.captures[1].id])
        assertIs<SafeCallExpr>(assertIs<BinaryExpr>(root.right).left)
        assertTrue(captured.tree.debugString().contains("BinaryExpr"))
        assertTrue(captured.tree.body.collectRefs().all { it.kind != RefKind.LOCAL })

        val adapted = QueryPredicateAdapter(
            QueryAdapterContext(
                properties = mapOf(
                    "example.User.age" to QueryProperty("example.User.age", "age", "u"),
                    "example.User.name" to QueryProperty("example.User.name", "name", "u"),
                ),
                captures = captured.bindings().asMap(),
            ),
        ).adapt(captured.tree)
        assertIs<QueryCondition.Logical>(adapted.condition)
        assertEquals(listOf(18, "A"), adapted.bindings.map { it.value })
        assertTrue(adapted.diagnostics.isEmpty(), adapted.diagnostics.toString())
    }

    @Test
    fun `lambda locals remain AST locals instead of capture slots`() {
        val captured = sampleWithLambdaLocal(18)

        assertEquals(listOf("minAge"), captured.tree.captures.map { it.name })
        assertEquals(1, captured.captureValues.size)
        assertEquals(18, captured.bindings()[captured.tree.captures.single().id])

        val body = assertIs<BlockExpr>(captured.tree.body)
        assertIs<LocalDeclarationExpr>(body.statements.first())
        assertIs<BinaryExpr>(body.statements.last())
    }
}

private fun com.kotlinorm.experimental.exprtree.api.ExprNode.collectRefs() =
    collect().filterIsInstance<com.kotlinorm.experimental.exprtree.api.RefExpr>()
