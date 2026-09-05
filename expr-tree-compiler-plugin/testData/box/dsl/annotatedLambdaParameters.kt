import com.kotlinorm.experimental.exprtree.api.BinaryExpr
import com.kotlinorm.experimental.exprtree.api.CapturedExpr
import com.kotlinorm.experimental.exprtree.api.ExprCapture
import com.kotlinorm.experimental.exprtree.api.StringTemplateExpr
import com.kotlinorm.experimental.exprtree.api.capturedExprOrNull

data class User(val age: Int, val name: String?)

sealed interface Step
data class Filter<T>(val expression: CapturedExpr<T, Boolean>, val executable: (T) -> Boolean) : Step
data class MapValue<T, R>(val expression: CapturedExpr<T, R>, val executable: (T) -> R) : Step

data class Pipeline<T>(val steps: List<Step> = emptyList()) {
    fun filter(@ExprCapture predicate: (T) -> Boolean): Pipeline<T> = Pipeline(
        steps + Filter(requireNotNull(predicate.capturedExprOrNull()), predicate)
    )

    fun <R> map(@ExprCapture transform: (T) -> R): Pipeline<R> = Pipeline(
        steps + MapValue(requireNotNull(transform.capturedExprOrNull()), transform)
    )
}

fun box(): String {
    val minimum = 18
    val prefix = "member"
    val pipeline = Pipeline<User>()
        .filter { it.age >= minimum }
        .map { "${it.name ?: "unknown"}-$prefix" }

    val filter = pipeline.steps[0] as Filter<User>
    check(filter.expression.tree.body is BinaryExpr)
    check(filter.expression.tree.captures.map { it.name } == listOf("minimum"))
    if (!filter.expression.captureValues.contentEquals(arrayOf<Any?>(minimum))) error(filter.expression.captureValues.toList().toString())
    check(filter.executable(User(20, "Ada")))
    check(!filter.executable(User(17, "Ada")))

    val mapped = pipeline.steps[1] as MapValue<User, String>
    check(mapped.expression.tree.body is StringTemplateExpr)
    check(mapped.expression.tree.captures.map { it.name } == listOf("prefix"))
    if (!mapped.expression.captureValues.contentEquals(arrayOf<Any?>(prefix))) error(mapped.expression.captureValues.toList().toString())
    check(mapped.executable(User(20, "Ada")) == "Ada-member")
    return "OK"
}
