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
    is ConstExpr, is RefExpr, is UnsupportedExpr, is BreakExpr, is ContinueExpr -> emptyList()
    is PropertyAccessExpr -> listOfNotNull(receiver, value)
    is IndexAccessExpr -> listOf(receiver) + indices + listOfNotNull(value)
    is CallExpr -> listOfNotNull(dispatchReceiver, extensionReceiver) + contextArguments + arguments
    is DestructuringExpr -> listOf(initializer) + entries.map { it.initializer }
    is IncDecExpr -> listOf(operand)
    is CallableReferenceExpr -> listOfNotNull(receiver)
    is SmartCastExpr -> listOf(expression)
    is UnaryExpr -> listOf(operand)
    is BinaryExpr -> listOf(left, right)
    is RangeExpr -> listOf(start, end)
    is SafeCallExpr -> listOf(receiver, selector)
    is ElvisExpr -> listOf(left, right)
    is BlockExpr -> statements
    is LambdaExpr -> listOfNotNull(body)
    is LocalDeclarationExpr -> listOfNotNull(initializer)
    is AssignmentExpr -> listOf(target, value)
    is IfExpr -> listOfNotNull(condition, thenBranch, elseBranch)
    is ReturnExpr -> listOf(value)
    is WhileExpr -> listOf(condition, body)
    is DoWhileExpr -> listOf(body, condition)
    is ForLoopExpr -> listOf(iterable, body)
    is ThrowExpr -> listOf(value)
    is TryExpr -> listOf(tryBlock) + catches + listOfNotNull(finallyBlock)
    is CatchExpr -> listOf(body)
    is WhenEntryExpr -> conditions + body
    is WhenExpr -> listOfNotNull(subject?.initializer) + entries
    is StringTemplateExpr -> parts.mapNotNull { (it as? StringTemplatePart.Expression)?.expression }
    is TypeOperatorExpr -> listOf(operand)
}

fun <C> ExprNode.transformChildren(transformer: ExprTransformer<C>, context: C): ExprNode {
    fun t(node: ExprNode) = transformer.transform(node, context)
    return when (this) {
        is ConstExpr, is RefExpr, is UnsupportedExpr, is BreakExpr, is ContinueExpr -> this
        is PropertyAccessExpr -> copy(receiver = receiver?.let(::t), value = value?.let(::t))
        is IndexAccessExpr -> copy(receiver = t(receiver), indices = indices.map(::t), value = value?.let(::t))
        is CallExpr -> copy(
            dispatchReceiver = dispatchReceiver?.let(::t),
            extensionReceiver = extensionReceiver?.let(::t),
            arguments = arguments.map(::t),
            contextArguments = contextArguments.map(::t),
        )
        is DestructuringExpr -> copy(initializer = t(initializer), entries = entries.map { it.copy(initializer = t(it.initializer)) })
        is IncDecExpr -> copy(operand = t(operand))
        is CallableReferenceExpr -> copy(receiver = receiver?.let(::t))
        is SmartCastExpr -> copy(expression = t(expression))
        is UnaryExpr -> copy(operand = t(operand))
        is BinaryExpr -> copy(left = t(left), right = t(right))
        is RangeExpr -> copy(start = t(start), end = t(end))
        is SafeCallExpr -> copy(receiver = t(receiver), selector = t(selector))
        is ElvisExpr -> copy(left = t(left), right = t(right))
        is BlockExpr -> copy(statements = statements.map(::t))
        is LambdaExpr -> copy(body = body?.let(::t))
        is LocalDeclarationExpr -> copy(initializer = initializer?.let(::t))
        is AssignmentExpr -> copy(target = t(target), value = t(value))
        is IfExpr -> copy(condition = t(condition), thenBranch = t(thenBranch), elseBranch = elseBranch?.let(::t))
        is ReturnExpr -> copy(value = t(value))
        is WhileExpr -> copy(condition = t(condition), body = t(body))
        is DoWhileExpr -> copy(body = t(body), condition = t(condition))
        is ForLoopExpr -> copy(iterable = t(iterable), body = t(body))
        is ThrowExpr -> copy(value = t(value))
        is TryExpr -> copy(
            tryBlock = t(tryBlock),
            catches = catches.map { t(it) as CatchExpr },
            finallyBlock = finallyBlock?.let(::t),
        )
        is CatchExpr -> copy(body = t(body))
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
            is RefExpr -> append(" ").append(node.kind).append(" ").append(node.name).append(node.label?.let { "@$it" } ?: "").append(node.qualifierType?.classifierId?.let { "<$it>" } ?: "")
            is PropertyAccessExpr -> append(" ").append(node.operation).append(" ").append(node.property.callableId)
            is IndexAccessExpr -> append(" ").append(node.operation).append(" ").append(node.callable.callableId).append(" indices=").append(node.indices.size)
            is CallExpr -> append(" ").append(node.callable.callableId)
            is DestructuringExpr -> append(" ").append(node.mode)
            is IncDecExpr -> append(" ").append(node.operation).append(if (node.prefix) " prefix" else " postfix")
            is CallableReferenceExpr -> append(" ").append(node.callable.callableId)
            is SmartCastExpr -> append(" ").append(node.smartCastType.classifierId ?: "?")
            is UnaryExpr -> append(" ").append(node.operator.callableId)
            is BinaryExpr -> append(" ").append(node.operator.callableId)
            is RangeExpr -> append(" ").append(node.operation)
            is AssignmentExpr -> append(" ").append(node.operator)
            is LocalDeclarationExpr -> append(" ").append(if (node.declaration.mutable) "var " else "val ").append(node.declaration.name)
            is IfExpr -> append(" if")
            is ReturnExpr -> append(" return").append(node.targetLabel?.let { "@$it" } ?: "")
            is WhileExpr -> append(" while").append(node.label?.let { "@$it" } ?: "")
            is DoWhileExpr -> append(" do-while").append(node.label?.let { "@$it" } ?: "")
            is ForLoopExpr -> append(" for").append(node.label?.let { "@$it" } ?: "")
            is BreakExpr -> append(" break").append(node.targetLabel?.let { "@$it" } ?: "")
            is ContinueExpr -> append(" continue").append(node.targetLabel?.let { "@$it" } ?: "")
            is ThrowExpr -> append(" throw")
            is TryExpr -> append(" try")
            is CatchExpr -> append(" catch ").append(node.parameter.name)
            is WhenExpr -> append(" when").append(if (node.subject == null) "" else " subject=" + node.subject.declaration.name)
            is WhenEntryExpr -> append(if (node.isElse) " else" else " entry")
            is StringTemplateExpr -> append(" parts=").append(node.parts.size)
            is TypeOperatorExpr -> append(" ").append(node.operator).append(" ").append(node.targetType.classifierId ?: "?")
            is UnsupportedExpr -> append(" reason=").append(node.reason).append(node.sourceText?.let { " source=\"$it\"" } ?: "")
            else -> Unit
        }
        append('\n')
        node.children().forEach { appendNode(it, depth + 1) }
    }
    append("ExprTree(schema=").append(schemaVersion).append(")\n")
    appendNode(body, 0)
}
