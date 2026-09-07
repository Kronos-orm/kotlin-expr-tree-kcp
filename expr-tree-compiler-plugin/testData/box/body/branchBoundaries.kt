import com.kotlinorm.experimental.exprtree.api.ConstExpr
import com.kotlinorm.experimental.exprtree.api.AssignmentOperator
import com.kotlinorm.experimental.exprtree.api.AssignmentExpr
import com.kotlinorm.experimental.exprtree.api.BlockExpr
import com.kotlinorm.experimental.exprtree.api.IfExpr
import com.kotlinorm.experimental.exprtree.api.expr
import com.kotlinorm.experimental.exprtree.api.collect
import com.kotlinorm.experimental.exprtree.api.debugString

fun ordinary(block: (Int) -> Int): Int = block(1)
fun ordinaryValue(value: Int): Int = value

fun branchValues() = listOf(
    expr<Int, Double> { 1.0 },
    expr<Int, Float> { 1.0f },
    expr<Int, Char> { 'x' },
    expr<Int, String?> { null },
    expr<Int, Long> { 7L },
    expr<Int, Double> { 1.25 },
    expr<Int, Float> { 2.5f },
    expr<Boolean, Unit> { if (it) println("true") },
)

fun compoundAssignments(seed: Int) = expr<Int, Int> {
    var value = seed
    value += 1
    value -= 1
    value *= 2
    value /= 2
    value %= 5
    value
}

fun unreachableFunctionReference() {
    if (false) expr<Int, Int>(::ordinaryValue)
}

fun box(): String {
    val trees = branchValues()
    unreachableFunctionReference()
    if (trees[0].tree.body !is ConstExpr || trees[1].tree.body !is ConstExpr || trees[2].tree.body !is ConstExpr) {
        error(trees.map { it.tree.body }.toString())
    }
    val nullBody = trees[3].tree.body as? ConstExpr ?: error(trees[3].tree.debugString())
    check(nullBody.value == null)
    check(nullBody.type.nullability == com.kotlinorm.experimental.exprtree.api.Nullability.NULLABLE)
    if (trees[7].tree.body.collect().none { it is IfExpr }) error(trees[7].tree.debugString())
    if (ordinary { it + 1 } != 2) error("ordinary lambda was rewritten")
    if (trees.any { it.captureValues.size != it.tree.captures.size }) error("capture mismatch")
    val assignments = compoundAssignments(3).tree.body as? BlockExpr ?: error("missing assignment block")
    val operators = assignments.statements.filterIsInstance<AssignmentExpr>().map { it.operator }
    if (operators != listOf(
            AssignmentOperator.PLUS_ASSIGN,
            AssignmentOperator.MINUS_ASSIGN,
            AssignmentOperator.TIMES_ASSIGN,
            AssignmentOperator.DIV_ASSIGN,
            AssignmentOperator.REM_ASSIGN,
        )) error(operators.toString())
    return "OK"
}
