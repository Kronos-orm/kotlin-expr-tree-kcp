package example

import com.kotlinorm.experimental.exprtree.api.*

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class QueryAdapterTest {
    @Test
    fun `adapter maps properties and captures without compiler types`() {
        val user = ParameterDecl(DeclId(1), "user", TypeRef("example.User"))
        val age = PropertyGetExpr(
            ExprId(2), TypeRef("kotlin.Int"), CallableRef("example.User.age"),
            RefExpr(ExprId(3), user.type, user.id, user.name, RefKind.PARAMETER),
        )
        val minimum = RefExpr(ExprId(4), TypeRef("kotlin.Int"), DeclId(9), "minAge", RefKind.CAPTURE)
        val tree = ExprTree<Any, Boolean>(
            parameters = listOf(user),
            captures = listOf(CaptureDecl(DeclId(9), "minAge", minimum.type)),
            body = BinaryExpr(ExprId(5), TypeRef("kotlin.Boolean"), CallableRef("kotlin.Int.compareTo"), age, minimum),
        )

        val result = QueryPredicateAdapter(
            QueryAdapterContext(
                properties = mapOf("example.User.age" to QueryProperty("example.User.age", "age", "u")),
                captures = mapOf(DeclId(9) to 18),
            )
        ).adapt(tree)

        val condition = assertIs<QueryCondition.Compare>(result.condition)
        assertEquals("compareTo", condition.operator)
        assertEquals(QueryValue.Column("age", "u"), condition.left)
        assertEquals(1, result.bindings.size)
        assertEquals(18, result.bindings.single().value)
        assertEquals(emptyList(), result.diagnostics)
    }

    @Test
    fun `unsupported node includes path diagnostic`() {
        val body = UnsupportedExpr(ExprId(7), TypeRef("kotlin.Boolean"), "loop")
        val result = QueryPredicateAdapter(QueryAdapterContext()).adapt(
            ExprTree<Any, Boolean>(parameters = emptyList(), captures = emptyList(), body = body)
        )
        assertEquals(null, result.condition)
        assertEquals("KET201", result.diagnostics.last().code)
        assertEquals(listOf(ExprId(7)), result.diagnostics.last().path)
    }
}
