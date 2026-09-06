import com.kotlinorm.experimental.exprtree.api.CatchExpr
import com.kotlinorm.experimental.exprtree.api.IfExpr
import com.kotlinorm.experimental.exprtree.api.LocalDeclarationExpr
import com.kotlinorm.experimental.exprtree.api.TryExpr
import com.kotlinorm.experimental.exprtree.api.collect
import com.kotlinorm.experimental.exprtree.api.expr

fun captureTryCatchFinally(limit: Int, suffix: String) = expr<Int, String> { value ->
    try {
        val total = value + limit
        if (total >= 0) "$suffix:$total" else throw IllegalStateException("negative")
    } catch (failure: IllegalArgumentException) {
        "argument-${failure.message ?: suffix}"
    } catch (failure: IllegalStateException) {
        "state-${failure.message ?: suffix}"
    } finally {
        check(limit >= 0)
    }
}

fun captureTryCatchOnly(suffix: String) = expr<String, String> { value ->
    try {
        if (value.isNotEmpty()) value else throw IllegalArgumentException(suffix)
    } catch (failure: IllegalArgumentException) {
        failure.message ?: suffix
    }
}

fun captureTryFinallyOnly(prefix: String) = expr<Int, String> { value ->
    try {
        "$prefix:$value"
    } finally {
        require(value >= 0)
    }
}

fun box(): String {
    val captured = captureTryCatchFinally(2, "total")
    check(captured.tree.captures.map { it.name } == listOf("limit", "suffix"))
    if (!captured.captureValues.contentEquals(arrayOf<Any?>(2, "total"))) error(captured.captureValues.toList().toString())

    val tree = captured.tree.body as? TryExpr ?: error(captured.tree.body.toString())
    check(tree.catches.size == 2)
    check(tree.finallyBlock != null)
    check(tree.catches.map { it.parameter.name } == listOf("failure", "failure"))
    check(tree.catches.all { it.parameter.type.classifierId?.contains("Illegal") == true })

    val nodes = tree.collect()
    check(nodes.count { it is CatchExpr } == 2)
    check(nodes.any { it is LocalDeclarationExpr })
    check(nodes.any { it is IfExpr })

    val catchOnly = captureTryCatchOnly("empty").tree.body as? TryExpr ?: error("missing catch-only try")
    check(catchOnly.catches.size == 1)
    check(catchOnly.finallyBlock == null)

    val finallyOnly = captureTryFinallyOnly("value").tree.body as? TryExpr ?: error("missing finally-only try")
    check(finallyOnly.catches.isEmpty())
    check(finallyOnly.finallyBlock != null)
    return "OK"
}
