package com.kotlinorm.experimental.exprtree.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.assertNull

class ExprTreeTest {
    @Test
    fun `generic model preserves a resolved binary predicate`() {
        val user = ParameterDecl(DeclId(1), "user", TypeRef("example.User"))
        val minimum = CaptureDecl(DeclId(2), "minimum", TypeRef("kotlin.Int"))
        val age = PropertyGetExpr(
            id = ExprId(3),
            type = TypeRef("kotlin.Int", Nullability.NON_NULL),
            property = CallableRef("example.User.age"),
            receiver = RefExpr(ExprId(4), user.type, user.id, user.name, RefKind.PARAMETER),
        )
        val predicate = BinaryExpr(
            id = ExprId(5),
            type = TypeRef("kotlin.Boolean", Nullability.NON_NULL),
            operator = CallableRef("kotlin.Int.compareTo", isOperator = true),
            left = age,
            right = RefExpr(ExprId(6), minimum.type, minimum.id, minimum.name, RefKind.CAPTURE),
        )

        val tree = ExprTree<Any, Boolean>(
            parameters = listOf(user),
            captures = listOf(minimum),
            body = predicate,
        )

        assertEquals(user.id, (age.receiver as RefExpr).declaration)
        assertEquals(minimum.id, (predicate.right as RefExpr).declaration)
        assertIs<BinaryExpr>(tree.body)
        assertEquals("compareTo", predicate.operator.name)
    }

    @Test
    fun `model preserves semantic metadata without a serialization transport`() {
        val body = BinaryExpr(
            ExprId(10), TypeRef("kotlin.Boolean", Nullability.NON_NULL),
            CallableRef("kotlin.Int.compareTo", isOperator = true),
            RefExpr(ExprId(11), TypeRef("kotlin.Int", Nullability.NON_NULL), DeclId(1), "value", RefKind.PARAMETER,
                SourceSpan("fixture.kt", 2, 7), OriginRef(OriginKind.SOURCE)),
            ConstExpr(ExprId(12), TypeRef("kotlin.Int", Nullability.NON_NULL), 3),
            SourceSpan("fixture.kt", 2, 12), OriginRef(OriginKind.NORMALIZED, listOf(ExprId(11)), "comparison"),
        )
        val tree = ExprTree<Any?, Boolean>(
            schemaVersion = 1,
            parameters = listOf(ParameterDecl(DeclId(1), "value", TypeRef("kotlin.Int", Nullability.NON_NULL))),
            captures = emptyList(), body = body,
            diagnostics = listOf(TreeDiagnostic("E001", "unsupported", DiagnosticSeverity.WARNING, SourceSpan("fixture.kt", 2, 12), listOf(ExprId(10)))),
            metadata = TreeMetadata("test", "2.4.0", "jvm", listOf("raw", "normalized"), mapOf("z" to "last", "a" to "first")),
        )
        assertEquals(1, tree.schemaVersion)
        assertEquals("test", tree.metadata.producerVersion)
        assertEquals("E001", tree.diagnostics.single().code)
        assertEquals(body, tree.body)
    }

    @Test
    fun `visitor reaches every node family and transformer preserves unchanged subtrees`() {
        val leaf = ConstExpr(ExprId(1), TypeRef("kotlin.Int"), 1)
        val nodes: List<ExprNode> = listOf(
            leaf,
            RefExpr(ExprId(2), TypeRef("kotlin.Int"), DeclId(1), "x", RefKind.LOCAL),
            PropertyGetExpr(ExprId(3), TypeRef("kotlin.Int"), CallableRef("p"), leaf),
            CallExpr(ExprId(4), TypeRef("kotlin.Int"), CallableRef("f"), leaf, null, listOf(leaf)),
            UnaryExpr(ExprId(5), TypeRef("kotlin.Int"), CallableRef("neg"), leaf),
            BinaryExpr(ExprId(6), TypeRef("kotlin.Int"), CallableRef("plus"), leaf, leaf),
            SafeCallExpr(ExprId(7), TypeRef("kotlin.Int"), leaf, leaf),
            ElvisExpr(ExprId(8), TypeRef("kotlin.Int"), leaf, leaf),
            BlockExpr(ExprId(9), TypeRef("kotlin.Int"), listOf(leaf)),
            LambdaExpr(ExprId(10), TypeRef("kotlin.Function0"), emptyList(), emptyList(), leaf),
            UnsupportedExpr(ExprId(11), TypeRef("kotlin.Nothing"), "test"),
            LocalDeclarationExpr(ExprId(12), TypeRef("kotlin.Int"), LocalDecl(DeclId(12), "local", TypeRef("kotlin.Int")), leaf),
            AssignmentExpr(ExprId(13), TypeRef("kotlin.Unit"), RefExpr(ExprId(14), TypeRef("kotlin.Int"), DeclId(12), "local", RefKind.LOCAL), leaf),
            IfExpr(ExprId(15), TypeRef("kotlin.Int"), leaf, leaf, leaf),
            TryExpr(
                ExprId(16), TypeRef("kotlin.Int"), leaf,
                listOf(CatchExpr(ExprId(17), TypeRef("kotlin.Int"), LocalDecl(DeclId(17), "failure", TypeRef("kotlin.Throwable")), leaf)),
                leaf,
            ),
            WhenEntryExpr(ExprId(18), TypeRef("kotlin.Int"), listOf(leaf), leaf),
            WhenExpr(
                ExprId(19),
                TypeRef("kotlin.Int"),
                WhenSubject(LocalDecl(DeclId(19), "whenSubject", TypeRef("kotlin.Int")), leaf),
                listOf(WhenEntryExpr(ExprId(20), TypeRef("kotlin.Int"), emptyList(), leaf, isElse = true)),
            ),
            StringTemplateExpr(ExprId(21), TypeRef("kotlin.String"), listOf(StringTemplatePart.Text("value="), StringTemplatePart.Expression(leaf))),
            TypeOperatorExpr(ExprId(22), TypeRef("kotlin.Boolean"), TypeOperator.IS, leaf, TypeRef("kotlin.String")),
        )
        val root = BlockExpr(ExprId(99), TypeRef("kotlin.Int"), nodes)
        val visited = root.collect()
        assertTrue(visited.size >= 1 + nodes.size)
        assertTrue(nodes.all { visited.any { v -> v.id == it.id } })
        val unchanged = object : ExprTransformer<Unit> { override fun transform(node: ExprNode, context: Unit) = node }
        assertEquals(root, unchanged.transform(root, Unit).transformChildren(unchanged, Unit))

        val replacement = ConstExpr(ExprId(100), leaf.type, 2)
        val rewritten = root.transformRecursively(object : ExprTransformer<Unit> {
            override fun transform(node: ExprNode, context: Unit): ExprNode = if (node == leaf) replacement else node
        }, Unit)
        assertTrue(rewritten.collect().none { it.id == leaf.id })
        val debug = rewritten.debugTreeForTest()
        assertTrue(debug.contains("LocalDeclarationExpr"))
        assertTrue(debug.contains("AssignmentExpr"))
        assertTrue(debug.contains("IfExpr"))
        assertTrue(debug.contains("TryExpr"))
        assertTrue(debug.contains("CatchExpr"))
        assertTrue(debug.contains("WhenExpr"))
        assertTrue(debug.contains("StringTemplateExpr"))
        assertTrue(debug.contains("TypeOperatorExpr"))
    }

    @Test
    fun `extended nodes expose all expression children to recursive transforms`() {
        val source = ConstExpr(ExprId(30), TypeRef("kotlin.Int"), 1)
        val replacement = ConstExpr(ExprId(31), TypeRef("kotlin.Int"), 2)
        val entry = WhenEntryExpr(ExprId(34), TypeRef("kotlin.Int"), listOf(source), source)
        val root = StringTemplateExpr(
            ExprId(39), TypeRef("kotlin.String"), listOf(
                StringTemplatePart.Text("x="),
                StringTemplatePart.Expression(source),
                StringTemplatePart.Expression(
                    IfExpr(ExprId(38), TypeRef("kotlin.Int"), source, entry, source),
                ),
            ),
        )
        val rewritten = root.transformRecursively(object : ExprTransformer<Unit> {
            override fun transform(node: ExprNode, context: Unit): ExprNode =
                if (node == source) replacement else node
        }, Unit) as StringTemplateExpr

        assertEquals(2, rewritten.children().size)
        assertEquals(replacement, (rewritten.parts[1] as StringTemplatePart.Expression).expression)
        val ifNode = (rewritten.parts[2] as StringTemplatePart.Expression).expression as IfExpr
        assertEquals(replacement, ifNode.condition)
        assertEquals(replacement, ifNode.elseBranch)
        assertEquals(replacement, (ifNode.thenBranch as WhenEntryExpr).body)
        assertTrue(rewritten.debugTreeForTest().contains("StringTemplateExpr"))
    }

    @Test
    fun `validation traverses extended nodes`() {
        val invalidTypeCheck = TypeOperatorExpr(
            id = ExprId(50),
            type = TypeRef("kotlin.Boolean"),
            operator = TypeOperator.IS,
            operand = ConstExpr(ExprId(51), TypeRef("kotlin.Any"), "value"),
            targetType = TypeRef("kotlin.String"),
            source = SourceSpan("fixture.kt", 8, 3),
        )
        val tree = ExprTree<Any?, Boolean>(
            parameters = emptyList(),
            captures = emptyList(),
            body = WhenExpr(
                id = ExprId(52),
                type = TypeRef("kotlin.Boolean"),
                subject = null,
                entries = listOf(WhenEntryExpr(ExprId(53), TypeRef("kotlin.Boolean"), listOf(invalidTypeCheck), ConstExpr(ExprId(54), TypeRef("kotlin.Boolean"), true))),
            ),
        )

        val diagnostics = tree.validate()
        assertEquals("KET101", diagnostics.single().code)
        assertEquals(ExprId(50), diagnostics.single().path.last())
    }

    @Test
    fun `when subject initializer participates in traversal and transforms`() {
        val original = ConstExpr(ExprId(60), TypeRef("kotlin.Int"), 1)
        val replacement = ConstExpr(ExprId(61), TypeRef("kotlin.Int"), 2)
        val whenExpr = WhenExpr(
            id = ExprId(62),
            type = TypeRef("kotlin.String"),
            subject = WhenSubject(LocalDecl(DeclId(62), "whenSubject", original.type), original),
            entries = listOf(WhenEntryExpr(ExprId(63), TypeRef("kotlin.String"), emptyList(), ConstExpr(ExprId(64), TypeRef("kotlin.String"), "one"), true)),
        )

        val transformed = whenExpr.transformRecursively(object : ExprTransformer<Unit> {
            override fun transform(node: ExprNode, context: Unit): ExprNode = if (node == original) replacement else node
        }, Unit) as WhenExpr

        assertTrue(original in whenExpr.children())
        assertEquals(replacement, transformed.subject?.initializer)
    }

    @Test
    fun `find resolves valid paths and rejects empty missing or foreign paths`() {
        val leaf = ConstExpr(ExprId(70), TypeRef("kotlin.Int"), 1)
        val root = BinaryExpr(ExprId(71), TypeRef("kotlin.Int"), CallableRef("kotlin.Int.plus"), leaf, leaf.copy(id = ExprId(72)))
        assertEquals(leaf, root.find(ExprPath(listOf(ExprId(71), ExprId(70)))))
        assertNull(root.find(ExprPath()))
        assertNull(root.find(ExprPath(listOf(ExprId(99)))))
        assertNull(root.find(ExprPath(listOf(ExprId(71), ExprId(98)))))
    }

    @Test
    fun `validation reports duplicate ids and invalid when subject spans`() {
        val duplicate = ConstExpr(ExprId(80), TypeRef("kotlin.Int"), 1)
        val invalidSubject = WhenExpr(
            ExprId(81), TypeRef("kotlin.Int"),
            WhenSubject(LocalDecl(DeclId(81), "subject", duplicate.type), duplicate, SourceSpan("x.kt", 4, 2)),
            listOf(WhenEntryExpr(ExprId(82), TypeRef("kotlin.Int"), emptyList(), duplicate, true)),
        )
        val diagnostics = ExprTree<Any?, Int>(
            parameters = emptyList(),
            captures = emptyList(),
            body = BlockExpr(ExprId(83), duplicate.type, listOf(duplicate, duplicate, invalidSubject)),
        ).validate()
        assertTrue(diagnostics.any { it.code == "KET100" })
        assertTrue(diagnostics.any { it.code == "KET101" && it.message.contains("when subject") })
    }

    private fun ExprNode.debugTreeForTest(): String = ExprTree<Any?, Any?>(
        parameters = emptyList(), captures = emptyList(), body = this,
    ).debugString()
}
