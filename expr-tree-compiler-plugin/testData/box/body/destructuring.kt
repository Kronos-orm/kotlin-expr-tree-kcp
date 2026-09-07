import com.kotlinorm.experimental.exprtree.api.DestructuringExpr
import com.kotlinorm.experimental.exprtree.api.DestructuringMode
import com.kotlinorm.experimental.exprtree.api.expr
import com.kotlinorm.experimental.exprtree.api.collect

data class DestructuringValue(val first: Int, val second: String)

fun destructuringSample() = expr<DestructuringValue, Int> {
    val (number, _) = it
    number
}

fun box(): String {
    val node = destructuringSample().tree.body.collect().filterIsInstance<DestructuringExpr>().single()
    check(node.mode == DestructuringMode.POSITIONAL)
    check(node.entries.size == 2)
    check(node.entries[0].declaration.name == "number")
    check(node.entries[0].componentIndex == 1)
    check(node.entries[1].componentIndex == 2)
    return "OK"
}
