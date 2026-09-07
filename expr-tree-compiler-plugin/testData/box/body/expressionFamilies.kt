import com.kotlinorm.experimental.exprtree.api.AssignmentExpr
import com.kotlinorm.experimental.exprtree.api.BlockExpr
import com.kotlinorm.experimental.exprtree.api.BinaryExpr
import com.kotlinorm.experimental.exprtree.api.ConstExpr
import com.kotlinorm.experimental.exprtree.api.ElvisExpr
import com.kotlinorm.experimental.exprtree.api.IfExpr
import com.kotlinorm.experimental.exprtree.api.LocalDeclarationExpr
import com.kotlinorm.experimental.exprtree.api.SafeCallExpr
import com.kotlinorm.experimental.exprtree.api.StringTemplateExpr
import com.kotlinorm.experimental.exprtree.api.TypeOperator
import com.kotlinorm.experimental.exprtree.api.TypeOperatorExpr
import com.kotlinorm.experimental.exprtree.api.UnsupportedExpr
import com.kotlinorm.experimental.exprtree.api.WhenExpr
import com.kotlinorm.experimental.exprtree.api.collect
import com.kotlinorm.experimental.exprtree.api.debugString
import com.kotlinorm.experimental.exprtree.api.expr

fun expressionFamilies(limit: Int, suffix: String) = expr<Int, Any?> { value ->
    var local = value
    local += limit
    if (local > 0) {
        when {
            local == 1 -> "one-$suffix"
            else -> "value=$local"
        }
    } else {
        local as Any?
    }
}

fun box(): String {
    val captured = expressionFamilies(2, "x")
    if (!captured.captureValues.contentEquals(arrayOf<Any?>(2, "x"))) error(captured.captureValues.toList().toString())
    val body = captured.tree.body as? BlockExpr ?: error(captured.tree.debugString())
    val nodes = body.collect()
    check(nodes.any { it is LocalDeclarationExpr })
    check(nodes.any { it is AssignmentExpr })
    check(nodes.any { it is IfExpr })
    check(nodes.any { it is WhenExpr })
    check(nodes.any { it is StringTemplateExpr })
    check(nodes.any { it is TypeOperatorExpr && it.operator == TypeOperator.AS })
    if (!nodes.any { it is UnsupportedExpr && it.reason.contains("DesugaredAssignment") && !it.sourceText.isNullOrBlank() }) {
        error(captured.tree.debugString())
    }

    val safe = expr<String?, Boolean> { it?.isNotEmpty() == true }
    check(safe.tree.body.collect().any { it is SafeCallExpr })
    check(safe.tree.body.collect().any { it is BinaryExpr })
    check(safe.tree.body.collect().any { it is ConstExpr && it.value == true })
    return "OK"
}
