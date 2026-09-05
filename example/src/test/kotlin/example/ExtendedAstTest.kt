package example

import com.kotlinorm.experimental.exprtree.api.AssignmentExpr
import com.kotlinorm.experimental.exprtree.api.BlockExpr
import com.kotlinorm.experimental.exprtree.api.IfExpr
import com.kotlinorm.experimental.exprtree.api.LocalDeclarationExpr
import com.kotlinorm.experimental.exprtree.api.RefExpr
import com.kotlinorm.experimental.exprtree.api.StringTemplateExpr
import com.kotlinorm.experimental.exprtree.api.TypeOperatorExpr
import com.kotlinorm.experimental.exprtree.api.TypeOperator
import com.kotlinorm.experimental.exprtree.api.WhenExpr
import com.kotlinorm.experimental.exprtree.api.BinaryExpr
import com.kotlinorm.experimental.exprtree.api.collect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ExtendedAstTest {
    @Test
    fun `extended expressions compile into generic runtime nodes`() {
        val captured = extendedSample(2, "x")
        val body = assertNotNull(captured.tree.body as? BlockExpr)
        assertNotNull(body.statements.filterIsInstance<LocalDeclarationExpr>().single().source)
        assertNotNull(body.statements.filterIsInstance<AssignmentExpr>().single().source)
        assertNotNull(body.statements.filterIsInstance<IfExpr>().single().source)
        val ifNode = body.statements.filterIsInstance<IfExpr>().single()
        val whenNode = ifNode.thenBranch as WhenExpr
        assertNotNull(whenNode.source)
        assertEquals(2, whenNode.entries.size)
        assertNotNull(whenNode.collect().filterIsInstance<StringTemplateExpr>().first().source)
        assertNotNull(captured.tree.body.collect().filterIsInstance<TypeOperatorExpr>().first().source)
        assertTrue(captured.tree.body.collect().all { node ->
            val source = node.source
            source == null || (source.startOffset >= 0 && source.endOffset >= source.startOffset)
        })
        assertEquals(listOf(2, "x"), captured.bindings().asMap().values.toList())
    }

    @Test
    fun `shadowed local is not emitted as lexical capture`() {
        val captured = shadowedSample(7)
        assertTrue(captured.tree.captures.isEmpty())
        val refs = captured.tree.body.collect().filterIsInstance<RefExpr>()
        assertTrue(refs.any { it.name == "value" })
        assertTrue(refs.any { it.kind == com.kotlinorm.experimental.exprtree.api.RefKind.LOCAL })
        assertTrue(captured.bindings().asMap().isEmpty())
    }

    @Test
    fun `is and safe cast preserve their type operators`() {
        val operators = (typeOperatorSample().tree.body.collect() + safeCastOperatorSample().tree.body.collect())
            .filterIsInstance<TypeOperatorExpr>()
            .map { it.operator }
        assertTrue(TypeOperator.IS in operators)
        assertTrue(TypeOperator.SAFE_AS in operators)
    }

    @Test
    fun `subject when binds its synthetic subject as a local instead of a capture`() {
        val captured = subjectWhenSample(1, "x")
        val whenNode = assertNotNull(captured.tree.body as? WhenExpr)
        val subject = assertNotNull(whenNode.subject)

        assertTrue(subject.declaration.name == "whenSubject")
        assertTrue(subject.initializer is BinaryExpr)
        assertEquals(listOf("limit", "suffix"), captured.tree.captures.map { it.name })
        assertEquals(listOf(1, "x"), captured.bindings().asMap().values.toList())
        assertTrue(whenNode.entries.first().conditions
            .flatMap { it.collect() }
            .filterIsInstance<RefExpr>()
            .any { it.kind == com.kotlinorm.experimental.exprtree.api.RefKind.LOCAL && it.declaration == subject.declaration.id })
    }
}
