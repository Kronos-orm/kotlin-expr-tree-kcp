package example

import com.kotlinorm.experimental.exprtree.api.*

/** Target-neutral condition model for a pure Kotlin query adapter. */
sealed interface QueryValue {
    data class Column(val name: String, val sourceAlias: String? = null) : QueryValue
    data class Parameter(val slot: DeclId, val name: String, val value: Any?) : QueryValue
    data class Literal(val value: Any?) : QueryValue
    data class Call(val name: String, val receiver: QueryValue?, val arguments: List<QueryValue>) : QueryValue
}

sealed interface QueryCondition : QueryValue {
    data class Compare(val operator: String, val left: QueryValue, val right: QueryValue) : QueryCondition
    data class Logical(val operator: String, val left: QueryCondition, val right: QueryCondition) : QueryCondition
    data class Unary(val operator: String, val operand: QueryValue) : QueryCondition
    data class IsNull(val value: QueryValue, val negated: Boolean = false) : QueryCondition
}

data class QueryProperty(
    val callableId: String,
    val column: String,
    val sourceAlias: String? = null,
)

data class QueryAdapterContext(
    val properties: Map<String, QueryProperty> = emptyMap(),
    val captures: Map<DeclId, Any?> = emptyMap(),
)

data class QueryBinding(val slot: DeclId, val name: String, val value: Any?)

data class QueryAdapterDiagnostic(
    val code: String,
    val message: String,
    val source: SourceSpan? = null,
    val path: List<ExprId> = emptyList(),
)

data class QueryAdaptation(
    val condition: QueryCondition?,
    val bindings: List<QueryBinding>,
    val diagnostics: List<QueryAdapterDiagnostic>,
)

/**
 * Converts the model tree to a target-neutral condition. It intentionally has
 * no dependency on FIR, PSI, IR, or compiler artifacts.
 */
class QueryPredicateAdapter(private val context: QueryAdapterContext) {
    private val bindings = linkedMapOf<DeclId, QueryBinding>()
    private val diagnostics = mutableListOf<QueryAdapterDiagnostic>()

    fun adapt(tree: ExprTree<*, *>): QueryAdaptation {
        diagnostics += tree.validate().filter { it.severity == DiagnosticSeverity.ERROR }
            .map { QueryAdapterDiagnostic(it.code, it.message, it.source, it.path) }
        val condition = condition(tree.body, ExprPath(listOf(tree.body.id)))
        return QueryAdaptation(condition, bindings.values.toList(), diagnostics.toList())
    }

    private fun condition(node: ExprNode, path: ExprPath): QueryCondition? {
        return when (node) {
            is BinaryExpr -> {
                val name = operatorName(node.operator)
                val left = value(node.left, path.child(node.left.id))
                val right = value(node.right, path.child(node.right.id))
                if (name == "and" || name == "or") {
                    val l = left as? QueryCondition
                    val r = right as? QueryCondition
                    if (l != null && r != null) QueryCondition.Logical(name.uppercase(), l, r)
                    else unsupported(node, path, "logical operands must be conditions")
                } else if (left != null && right != null) QueryCondition.Compare(name, left, right)
                else null
            }
            is UnaryExpr -> {
                val operand = value(node.operand, path.child(node.operand.id))
                if (operand != null) QueryCondition.Unary(operatorName(node.operator), operand)
                else null
            }
            else -> value(node, path) as? QueryCondition
        }
    }

    private fun value(node: ExprNode, path: ExprPath): QueryValue? = when (node) {
        is ConstExpr -> QueryValue.Literal(node.value)
        is RefExpr -> when (node.kind) {
            RefKind.CAPTURE -> context.captures[node.declaration]?.let { captured ->
                bindings.putIfAbsent(node.declaration, QueryBinding(node.declaration, node.name, captured))
                QueryValue.Parameter(node.declaration, node.name, captured)
            } ?: unsupported(node, path, "missing capture ${node.name}")
            else -> QueryValue.Column(node.name)
        }
        is PropertyGetExpr -> {
            val mapping = context.properties[node.property.callableId]
            if (mapping == null) unsupported(node, path, "unmapped property ${node.property.callableId}")
            else QueryValue.Column(mapping.column, mapping.sourceAlias)
        }
        is CallExpr -> {
            val receiver = node.dispatchReceiver ?: node.extensionReceiver
            val receiverValue = receiver?.let { value(it, path.child(it.id)) }
            val args = node.arguments.mapIndexed { index, argument -> value(argument, path.child(argument.id)) }
            if (args.any { it == null }) null else QueryValue.Call(node.callable.callableId, receiverValue, args.filterNotNull())
        }
        is SafeCallExpr -> safeSelector(node.receiver, node.selector, path)
        is ElvisExpr -> {
            val left = value(node.left, path.child(node.left.id))
            val right = value(node.right, path.child(node.right.id))
            if (left != null && right != null) QueryValue.Call("elvis", null, listOf(left, right)) else null
        }
        is BinaryExpr, is UnaryExpr -> condition(node, path)
        is UnsupportedExpr -> unsupported(node, path, node.reason)
        else -> unsupported(node, path, "unsupported node ${node::class.simpleName}")
    }

    private fun unsupported(node: ExprNode, path: ExprPath, message: String): Nothing? {
        diagnostics += QueryAdapterDiagnostic("KET201", message, node.source, path.ids)
        return null
    }

    private fun safeSelector(receiver: ExprNode, selector: ExprNode, path: ExprPath): QueryValue? {
        val receiverValue = value(receiver, path.child(receiver.id)) ?: return null
        return when (selector) {
            is CallExpr -> {
                val args = selector.arguments.map { value(it, path.child(it.id)) }
                if (args.any { it == null }) null
                else QueryValue.Call(
                    selector.callable.callableId,
                    receiverValue,
                    args.filterNotNull(),
                )
            }
            is PropertyGetExpr -> QueryValue.Call(selector.property.callableId, receiverValue, emptyList())
            else -> value(selector, path.child(selector.id))
        }
    }

    private fun operatorName(callable: CallableRef): String = callable.callableId.substringAfterLast('.')
}
