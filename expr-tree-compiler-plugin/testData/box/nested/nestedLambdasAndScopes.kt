import com.kotlinorm.experimental.exprtree.api.LambdaExpr
import com.kotlinorm.experimental.exprtree.api.RefKind
import com.kotlinorm.experimental.exprtree.api.collect
import com.kotlinorm.experimental.exprtree.api.expr

fun nestedWithoutCapture() = expr<Int, (Int) -> Int> {
    { value -> value + 1 }
}

fun nestedWithLocalCapture() = expr<Int, (Int) -> Int> {
    val increment = 2
    { value -> value + increment }
}

fun box(): String {
    val plain = nestedWithoutCapture()
    val plainLambda = plain.tree.body.collect().filterIsInstance<LambdaExpr>().single()
    check(plainLambda.parameters.single().name == "value")
    check(plainLambda.captures.isEmpty())
    check(plain.captureValues.isEmpty())

    val local = nestedWithLocalCapture()
    val localLambda = local.tree.body.collect().filterIsInstance<LambdaExpr>().single()
    check(localLambda.captures.map { it.name } == listOf("increment")) { localLambda.captures }
    check(localLambda.body?.collect()?.any { it is com.kotlinorm.experimental.exprtree.api.RefExpr && it.kind == RefKind.CAPTURE } == true)
    check(local.captureValues.isEmpty())
    return "OK"
}
