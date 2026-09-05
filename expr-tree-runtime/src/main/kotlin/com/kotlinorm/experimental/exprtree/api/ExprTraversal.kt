package com.kotlinorm.experimental.exprtree.api

/** Exhaustive-by-family visitor; implementers can dispatch on the sealed node type. */
interface ExprVisitor<R, C> {
    fun visit(node: ExprNode, context: C): R
}

/** Immutable structural transformer. Returning the input node preserves identity. */
interface ExprTransformer<C> {
    fun transform(node: ExprNode, context: C): ExprNode
}

fun <C> ExprNode.walk(visitor: ExprVisitor<Unit, C>, context: C) {
    visitor.visit(this, context)
    children().forEach { it.walk(visitor, context) }
}

fun ExprNode.children(): List<ExprNode> = when (this) {
    is ConstExpr, is RefExpr, is UnsupportedExpr -> emptyList()
    is PropertyGetExpr -> listOfNotNull(receiver)
    is CallExpr -> listOfNotNull(dispatchReceiver, extensionReceiver) + arguments
    is UnaryExpr -> listOf(operand)
    is BinaryExpr -> listOf(left, right)
    is SafeCallExpr -> listOf(receiver, selector)
    is ElvisExpr -> listOf(left, right)
    is BlockExpr -> statements
    is LambdaExpr -> listOfNotNull(body)
    is LocalDeclarationExpr -> listOfNotNull(initializer)
    is AssignmentExpr -> listOf(target, value)
    is IfExpr -> listOfNotNull(condition, thenBranch, elseBranch)
    is WhenEntryExpr -> conditions + body
    is WhenExpr -> listOfNotNull(subject?.initializer) + entries
    is StringTemplateExpr -> parts.mapNotNull { (it as? StringTemplatePart.Expression)?.expression }
    is TypeOperatorExpr -> listOf(operand)
}

fun <C> ExprNode.transformChildren(transformer: ExprTransformer<C>, context: C): ExprNode {
    fun t(node: ExprNode) = transformer.transform(node, context)
    return when (this) {
        is ConstExpr, is RefExpr, is UnsupportedExpr -> this
        is PropertyGetExpr -> copy(receiver = receiver?.let(::t))
        is CallExpr -> copy(
            dispatchReceiver = dispatchReceiver?.let(::t),
            extensionReceiver = extensionReceiver?.let(::t),
            arguments = arguments.map(::t),
        )
        is UnaryExpr -> copy(operand = t(operand))
        is BinaryExpr -> copy(left = t(left), right = t(right))
        is SafeCallExpr -> copy(receiver = t(receiver), selector = t(selector))
        is ElvisExpr -> copy(left = t(left), right = t(right))
        is BlockExpr -> copy(statements = statements.map(::t))
        is LambdaExpr -> copy(body = body?.let(::t))
        is LocalDeclarationExpr -> copy(initializer = initializer?.let(::t))
        is AssignmentExpr -> copy(target = t(target), value = t(value))
        is IfExpr -> copy(condition = t(condition), thenBranch = t(thenBranch), elseBranch = elseBranch?.let(::t))
        is WhenEntryExpr -> copy(conditions = conditions.map(::t), body = t(body))
        is WhenExpr -> copy(
            subject = subject?.copy(initializer = t(subject.initializer)),
            entries = entries.map { t(it) as WhenEntryExpr },
        )
        is StringTemplateExpr -> copy(parts = parts.map { part ->
            when (part) {
                is StringTemplatePart.Text -> part
                is StringTemplatePart.Expression -> part.copy(expression = t(part.expression))
            }
        })
        is TypeOperatorExpr -> copy(operand = t(operand))
    }
}

fun ExprNode.collect(): List<ExprNode> {
    val result = mutableListOf<ExprNode>()
    val seen = mutableSetOf<ExprId>()
    walk(object : ExprVisitor<Unit, Unit> {
        override fun visit(node: ExprNode, context: Unit) {
            if (seen.add(node.id)) result += node
        }
    }, Unit)
    return result
}

/** Stable location of a node within a tree, expressed as expression IDs. */
data class ExprPath(val ids: List<ExprId> = emptyList()) {
    fun child(id: ExprId): ExprPath = ExprPath(ids + id)
}

fun ExprNode.walkWithPath(visitor: (node: ExprNode, path: ExprPath) -> Unit) {
    fun visit(node: ExprNode, path: ExprPath) {
        visitor(node, path)
        node.children().forEach { child -> visit(child, path.child(child.id)) }
    }
    visit(this, ExprPath(listOf(id)))
}

fun ExprNode.find(path: ExprPath): ExprNode? {
    if (path.ids.isEmpty() || path.ids.first() != id) return null
    var current: ExprNode = this
    for (expected in path.ids.drop(1)) {
        current = current.children().firstOrNull { it.id == expected } ?: return null
    }
    return current
}

/** Applies a transformer bottom-up and only copies parents whose children changed. */
fun <C> ExprNode.transformRecursively(transformer: ExprTransformer<C>, context: C): ExprNode {
    fun visit(node: ExprNode): ExprNode {
        val rewritten = node.transformChildren(object : ExprTransformer<C> {
            override fun transform(node: ExprNode, context: C): ExprNode = visit(node)
        }, context)
        return transformer.transform(rewritten, context)
    }
    return visit(this)
}

/** Structural checks used by adapters and debug tooling before consuming a tree. */
fun ExprTree<*, *>.validate(): List<TreeDiagnostic> {
    val diagnostics = diagnostics.toMutableList()
    val seen = mutableSetOf<ExprId>()
    body.walkWithPath { node, path ->
        if (!seen.add(node.id)) {
            diagnostics += TreeDiagnostic(
                code = "KET100",
                message = "Duplicate expression id ${node.id.value}",
                severity = DiagnosticSeverity.ERROR,
                source = node.source,
                path = path.ids,
            )
        }
        val source = node.source
        if (source != null && (source.startOffset < 0 || source.endOffset < source.startOffset)) {
            diagnostics += TreeDiagnostic(
                code = "KET101",
                message = "Invalid source span for expression ${node.id.value}",
                severity = DiagnosticSeverity.ERROR,
                source = source,
                path = path.ids,
            )
        }
        val subjectSource = (node as? WhenExpr)?.subject?.source
        if (subjectSource != null && (subjectSource.startOffset < 0 || subjectSource.endOffset < subjectSource.startOffset)) {
            diagnostics += TreeDiagnostic(
                code = "KET101",
                message = "Invalid source span for when subject ${node.id.value}",
                severity = DiagnosticSeverity.ERROR,
                source = subjectSource,
                path = path.ids,
            )
        }
    }
    return diagnostics
}

/** Human-readable, deterministic tree output for tests and explain tooling. */
fun ExprTree<*, *>.debugString(): String = buildString {
    fun appendNode(node: ExprNode, depth: Int) {
        repeat(depth) { append("  ") }
        append(node::class.simpleName ?: "Expr")
        append("#").append(node.id.value)
        append(":").append(node.type.classifierId ?: "?")
        when (node) {
            is ConstExpr -> append(" value=").append(node.value)
            is RefExpr -> append(" ").append(node.kind).append(" ").append(node.name)
            is PropertyGetExpr -> append(" ").append(node.property.callableId)
            is CallExpr -> append(" ").append(node.callable.callableId)
            is UnaryExpr -> append(" ").append(node.operator.callableId)
            is BinaryExpr -> append(" ").append(node.operator.callableId)
            is AssignmentExpr -> append(" ").append(node.operator)
            is LocalDeclarationExpr -> append(" ").append(if (node.declaration.mutable) "var " else "val ").append(node.declaration.name)
            is IfExpr -> append(" if")
            is WhenExpr -> append(" when").append(if (node.subject == null) "" else " subject=" + node.subject.declaration.name)
            is WhenEntryExpr -> append(if (node.isElse) " else" else " entry")
            is StringTemplateExpr -> append(" parts=").append(node.parts.size)
            is TypeOperatorExpr -> append(" ").append(node.operator).append(" ").append(node.targetType.classifierId ?: "?")
            is UnsupportedExpr -> append(" reason=").append(node.reason)
            else -> Unit
        }
        append('\n')
        node.children().forEach { appendNode(it, depth + 1) }
    }
    append("ExprTree(schema=").append(schemaVersion).append(")\n")
    appendNode(body, 0)
}
