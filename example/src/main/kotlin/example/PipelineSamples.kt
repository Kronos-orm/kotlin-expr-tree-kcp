package example

import com.kotlinorm.experimental.exprtree.api.CapturedExpr
import com.kotlinorm.experimental.exprtree.api.ExprCapture
import com.kotlinorm.experimental.exprtree.api.capturedExprOrNull

sealed interface QueryStep

data class FilterStep<T>(
    val predicate: CapturedExpr<T, Boolean>,
    val executable: (T) -> Boolean,
) : QueryStep

data class MapStep<T, R>(
    val transform: CapturedExpr<T, R>,
    val executable: (T) -> R,
) : QueryStep

data class Query<T>(val steps: List<QueryStep> = emptyList()) {
    fun filter(@ExprCapture predicate: (T) -> Boolean): Query<T> {
        val captured = requireNotNull(predicate.capturedExprOrNull()) {
            "filter requires an expression lambda"
        }
        return copy(steps = steps + FilterStep(captured, predicate))
    }

    fun <R> map(@ExprCapture transform: (T) -> R): Query<R> {
        val captured = requireNotNull(transform.capturedExprOrNull()) {
            "map requires an expression lambda"
        }
        return Query(steps + MapStep(captured, transform))
    }
}

fun <T> query(): Query<T> = Query()

fun userPipeline(minimumAge: Int, prefix: String) = query<User>()
    .filter { it.age >= minimumAge }
    .map { "${it.name ?: "unknown"}-$prefix" }
