import com.kotlinorm.experimental.exprtree.api.RangeExpr
import com.kotlinorm.experimental.exprtree.api.RangeOperation
import com.kotlinorm.experimental.exprtree.api.expr
import com.kotlinorm.experimental.exprtree.api.collect

fun rangeSamples() = listOf(
    expr<Int, IntRange> { it..3 },
    expr<Int, IntRange> { it..<3 },
    expr<Int, IntProgression> { it until 3 },
    expr<Int, IntProgression> { it downTo 0 },
)

fun box(): String {
    val ranges = rangeSamples().flatMap { it.tree.body.collect() }.filterIsInstance<RangeExpr>()
    check(ranges.map { it.operation }.toSet() == setOf(RangeOperation.RANGE_TO, RangeOperation.RANGE_UNTIL, RangeOperation.UNTIL, RangeOperation.DOWN_TO))
    return "OK"
}
