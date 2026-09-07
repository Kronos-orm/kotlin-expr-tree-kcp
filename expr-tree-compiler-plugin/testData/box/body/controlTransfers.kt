import com.kotlinorm.experimental.exprtree.api.BreakExpr
import com.kotlinorm.experimental.exprtree.api.ContinueExpr
import com.kotlinorm.experimental.exprtree.api.DoWhileExpr
import com.kotlinorm.experimental.exprtree.api.ForLoopExpr
import com.kotlinorm.experimental.exprtree.api.ReturnExpr
import com.kotlinorm.experimental.exprtree.api.ThrowExpr
import com.kotlinorm.experimental.exprtree.api.WhileExpr
import com.kotlinorm.experimental.exprtree.api.collect
import com.kotlinorm.experimental.exprtree.api.debugString
import com.kotlinorm.experimental.exprtree.api.expr

fun captureControlTransfers(limit: Int) = expr<Int, Int> { value ->
    outer@ while (value < limit) {
        if (value < 0) continue@outer
        if (value > 10) break@outer
        break
    }
    do {
        if (value == 0) continue
        break
    } while (value < 0)
    for (item in listOf(value)) {
        if (item < 0) continue
    }
    if (value < 0) return@expr value
    throw IllegalStateException(value.toString())
}

fun box(): String {
    val tree = captureControlTransfers(10).tree
    val nodes = tree.body.collect()
    check(nodes.any { it is WhileExpr })
    check(nodes.any { it is DoWhileExpr })
    check(nodes.any { it is ForLoopExpr }) { tree.debugString() }
    check(nodes.any { it is BreakExpr })
    check(nodes.any { it is ContinueExpr })
    check(nodes.any { it is ReturnExpr })
    check(nodes.any { it is ThrowExpr })
    val loop = nodes.filterIsInstance<WhileExpr>().single()
    check(nodes.filterIsInstance<BreakExpr>().any { it.targetId == loop.targetId && it.targetLabel == "outer" })
    check(nodes.filterIsInstance<ContinueExpr>().any { it.targetId == loop.targetId && it.targetLabel == "outer" })
    return "OK"
}
