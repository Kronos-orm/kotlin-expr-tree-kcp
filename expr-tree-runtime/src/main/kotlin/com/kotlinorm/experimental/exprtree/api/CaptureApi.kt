package com.kotlinorm.experimental.exprtree.api

@Target(AnnotationTarget.FUNCTION, AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.BINARY)
annotation class ExprCapture

/**
 * Marker API. The compiler plugin replaces this call with a generated
 * [CapturedExpr] containing only runtime-model objects and lexical values.
 */
@ExprCapture
fun <T, R> expr(block: (T) -> R): CapturedExpr<T, R> =
    error("expr() requires the expression-tree compiler plugin call-site bridge")

object ExprTreeAbi {
    const val CURRENT_VERSION: Int = 1
    const val CURRENT_SCHEMA_VERSION: Int = 1
}

class ExprTreeAbiException(message: String) : IllegalArgumentException(message)

data class CapturedExpr<T, R>(
    val tree: ExprTree<T, R>,
    val captureValues: Array<Any?>,
    val abiVersion: Int = ExprTreeAbi.CURRENT_VERSION,
) {
    init {
        if (abiVersion != ExprTreeAbi.CURRENT_VERSION) {
            throw ExprTreeAbiException(
                "unsupported expression-tree ABI $abiVersion; expected ${ExprTreeAbi.CURRENT_VERSION}"
            )
        }
        if (tree.schemaVersion != ExprTreeAbi.CURRENT_SCHEMA_VERSION) {
            throw ExprTreeAbiException(
                "unsupported expression-tree schema ${tree.schemaVersion}; expected ${ExprTreeAbi.CURRENT_SCHEMA_VERSION}"
            )
        }
        require(captureValues.size == tree.captures.size) {
            "capture slot count ${captureValues.size} does not match tree declaration count ${tree.captures.size}"
        }
    }

    fun bindings(): CaptureBindings = CaptureBindings(
        tree.captures.mapIndexed { index, declaration -> declaration.id to captureValues[index] }.toMap()
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as CapturedExpr<*, *>

        if (tree != other.tree || abiVersion != other.abiVersion) return false
        if (!captureValues.contentEquals(other.captureValues)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = tree.hashCode()
        result = 31 * result + abiVersion
        result = 31 * result + captureValues.contentHashCode()
        return result
    }
}

/**
 * A function value that preserves ordinary Kotlin invocation while carrying
 * the expression tree generated for its source lambda.
 */
interface ExprTreeCarrier<T, R> {
    val capturedExpr: CapturedExpr<T, R>
}

class CapturedLambda<T, R>(
    private val delegate: (T) -> R,
    override val capturedExpr: CapturedExpr<T, R>,
) : (T) -> R, ExprTreeCarrier<T, R> {
    override fun invoke(value: T): R = delegate(value)
}

@Suppress("UNCHECKED_CAST")
fun <T, R> ((T) -> R).capturedExprOrNull(): CapturedExpr<T, R>? =
    (this as? ExprTreeCarrier<T, R>)?.capturedExpr

/** Process-local registry used by generated factories and test fixtures. */
object ExprTreeRegistry {
    private val entries = linkedMapOf<String, ExprTree<*, *>>()

    @Synchronized
    fun register(key: String, tree: ExprTree<*, *>) {
        require(key.isNotBlank()) { "tree key must not be blank" }
        require(tree.schemaVersion == ExprTreeAbi.CURRENT_SCHEMA_VERSION) {
            "unsupported expression-tree schema ${tree.schemaVersion}"
        }
        val previous = entries[key]
        require(previous == null || previous == tree) { "tree key already registered with a different tree: $key" }
        entries[key] = tree
    }

    @Synchronized
    fun lookup(key: String): ExprTree<*, *> = entries[key]
        ?: throw ExprTreeAbiException("no expression tree registered for key: $key")

    @Synchronized
    fun clear() = entries.clear()

    @Synchronized
    fun keys(): Set<String> = entries.keys.toSet()
}

fun <T, R> captureTree(key: String, values: Array<Any?>): CapturedExpr<T, R> {
    @Suppress("UNCHECKED_CAST")
    return CapturedExpr(ExprTreeRegistry.lookup(key) as ExprTree<T, R>, values)
}

data class CaptureBinding(val slot: DeclId, val value: Any?)

class CaptureBindings internal constructor(private val values: Map<DeclId, Any?>) {
    operator fun get(slot: DeclId): Any? = values[slot]
    fun asMap(): Map<DeclId, Any?> = values.toMap()

    companion object {
        fun of(vararg bindings: CaptureBinding): CaptureBindings {
            val values = bindings.associate { it.slot to it.value }
            require(values.size == bindings.size) { "duplicate capture slot" }
            return CaptureBindings(values)
        }
    }
}
