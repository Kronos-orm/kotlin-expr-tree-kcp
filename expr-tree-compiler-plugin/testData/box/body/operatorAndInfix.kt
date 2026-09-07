import com.kotlinorm.experimental.exprtree.api.BinaryExpr
import com.kotlinorm.experimental.exprtree.api.CallExpr
import com.kotlinorm.experimental.exprtree.api.RefExpr
import com.kotlinorm.experimental.exprtree.api.expr
import com.kotlinorm.experimental.exprtree.api.collect
import com.kotlinorm.experimental.exprtree.api.CapturedExpr
import com.kotlinorm.experimental.exprtree.api.CallableReferenceExpr
import com.kotlinorm.experimental.exprtree.api.RangeExpr
import com.kotlinorm.experimental.exprtree.api.ConstExpr

data class OperatorValue(val value: Int) {
    operator fun plus(other: OperatorValue): OperatorValue = OperatorValue(value + other.value)
}

infix fun Int.untilValue(other: Int): Int = this + other

fun infixExpression() = expr<Int, Int> { it untilValue 2 }

fun overloadedOperatorExpression() = expr<OperatorValue, OperatorValue> {
    it + OperatorValue(1)
}

class ContextValue(val amount: Int)

context(ctx: ContextValue)
fun contextAdd(value: Int): Int = value + ctx.amount

context(ctx: ContextValue)
fun contextExpression(): CapturedExpr<Int, Int> = expr { contextAdd(it) }

fun referencedValue(value: Int): Int = value + 1

fun callableReferenceExpression() = expr<Int, (Int) -> Int> { ::referencedValue }

fun operatorFamilies() = expr<Int, Boolean> {
    val arithmetic = it * 2 / 2 % 3
    val unary = -arithmetic
    val bitwise = (arithmetic and 1) or 2 xor 3
    arithmetic in 0..10 && !false && unary < 100 && bitwise >= 0
}

fun nullExpression() = expr<Int, String?> { null }


fun box(): String {
    val infixCall = infixExpression().tree.body.collect().filterIsInstance<CallExpr>().single()
    check(infixCall.callable.callableId.endsWith("untilValue"))
    check(infixCall.extensionReceiver is RefExpr)
    check(infixCall.dispatchReceiver == null)

    val plus = overloadedOperatorExpression().tree.body.collect().filterIsInstance<BinaryExpr>().single()
    check(plus.operator.isOperator)
    check(plus.operator.operatorToken == "+")
    check(plus.operator.callableId.endsWith("OperatorValue.plus"))

    val contextCall = with(ContextValue(3)) { contextExpression() }.tree.body.collect().filterIsInstance<CallExpr>().single()
    check(contextCall.callable.contextParameters.size == 1)
    check(contextCall.contextArguments.size == 1)

    val reference = callableReferenceExpression().tree.body.collect().filterIsInstance<CallableReferenceExpr>().single()
    check(reference.callable.name == "referencedValue")

    val families = operatorFamilies().tree.body.collect()
    check(families.any { it is BinaryExpr && it.operator.operatorToken == "*" })
    check(families.any { it is BinaryExpr && it.operator.operatorToken == "/" })
    check(families.any { it is BinaryExpr && it.operator.operatorToken == "%" })
    check(families.any { it is BinaryExpr && it.operator.operatorToken == "in" })
    check(families.any { it is RangeExpr && it.callable.operatorToken == ".." })
    check(families.any { it is BinaryExpr && it.operator.operatorToken == "&" })
    check(families.any { it is BinaryExpr && it.operator.operatorToken == "|" })
    check(families.any { it is BinaryExpr && it.operator.operatorToken == "^" })
    check(nullExpression().tree.body is ConstExpr && (nullExpression().tree.body as ConstExpr).value == null)

    return "OK"
}
