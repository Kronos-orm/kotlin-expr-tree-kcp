import com.kotlinorm.experimental.exprtree.api.BinaryExpr
import com.kotlinorm.experimental.exprtree.api.CallExpr
import com.kotlinorm.experimental.exprtree.api.ConstExpr
import com.kotlinorm.experimental.exprtree.api.ElvisExpr
import com.kotlinorm.experimental.exprtree.api.ExprNode
import com.kotlinorm.experimental.exprtree.api.IfExpr
import com.kotlinorm.experimental.exprtree.api.LambdaExpr
import com.kotlinorm.experimental.exprtree.api.PropertyAccessExpr
import com.kotlinorm.experimental.exprtree.api.RefExpr
import com.kotlinorm.experimental.exprtree.api.SafeCallExpr
import com.kotlinorm.experimental.exprtree.api.TypeOperator
import com.kotlinorm.experimental.exprtree.api.TypeOperatorExpr
import com.kotlinorm.experimental.exprtree.api.UnaryExpr
import com.kotlinorm.experimental.exprtree.api.WhenExpr
import com.kotlinorm.experimental.exprtree.api.collect
import com.kotlinorm.experimental.exprtree.api.debugString
import com.kotlinorm.experimental.exprtree.api.expr

data class MatrixUser(val age: Int, val name: String?)

fun increment(value: Int): Int = value + 1

fun allExpressionFamilies() = listOf(
    expr<Int, Int> { it + 1 },
    expr<Int, Int> { -it },
    expr<Int, Boolean> { it == 1 },
    expr<Int, Boolean> { it > 0 },
    expr<Int, Boolean> { it in 1..3 },
    expr<Int, Int> { increment(it) },
    expr<MatrixUser, String> { it.name ?: "unknown" },
    expr<MatrixUser, Boolean> { it.name?.isNotEmpty() == true },
    expr<Any?, Boolean> { it is String },
    expr<Any?, Boolean> { it !is String },
    expr<Any?, String> { it as String },
    expr<Any?, String?> { it as? String },
    expr<Int, String> { when (it + 1) { 1 -> "one" else -> "other" } },
    expr<Int, Int> { if (it > 0) 1 else 0 },
    expr<Int, Int> { run { it + 1 } },
    expr<Int, (Int) -> Int> { { value -> value + it } },
)

fun box(): String {
    val trees = allExpressionFamilies().map { it.tree.body.collect() }
    check(trees.flatten().any { it is ConstExpr && it.value?.toString() == "1" }) {
        allExpressionFamilies().joinToString("\n") { it.tree.debugString() }
    }
    check(trees.flatten().any { it is RefExpr })
    check(trees.flatten().any { it is BinaryExpr })
    check(trees.flatten().any { it is UnaryExpr })
    check(trees.flatten().any { it is CallExpr })
    check(trees.flatten().any { it is PropertyAccessExpr })
    check(trees.flatten().any { it is ElvisExpr })
    check(trees.flatten().any { it is SafeCallExpr })
    check(trees.flatten().any { it is TypeOperatorExpr && it.operator == TypeOperator.IS })
    check(trees.flatten().any { it is TypeOperatorExpr && it.operator == TypeOperator.IS_NOT })
    check(trees.flatten().any { it is TypeOperatorExpr && it.operator == TypeOperator.AS })
    check(trees.flatten().any { it is TypeOperatorExpr && it.operator == TypeOperator.SAFE_AS })
    check(trees.flatten().any { it is WhenExpr })
    check(trees.flatten().any { it is IfExpr })
    check(trees.flatten().any { it is LambdaExpr })
    check(allExpressionFamilies().all { it.captureValues.size == it.tree.captures.size })
    return "OK"
}
