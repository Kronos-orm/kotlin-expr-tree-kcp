import com.kotlinorm.experimental.exprtree.api.CapturedExpr
import com.kotlinorm.experimental.exprtree.api.ConstExpr
import com.kotlinorm.experimental.exprtree.api.PropertyGetExpr
import com.kotlinorm.experimental.exprtree.api.RefExpr
import com.kotlinorm.experimental.exprtree.api.RefKind
import com.kotlinorm.experimental.exprtree.api.debugString
import com.kotlinorm.experimental.exprtree.api.expr

data class User(val age: Int, val name: String?)

data class UserEnvelope(val user: User)

val topLevelUser = User(43, "Grace")

object CaptureFixtures {
    val memberUser = User(44, "Linus")
}

class CaptureOwner(val user: User) {
    fun captureMember() = expr<Int, User> { user }
}

fun localObjectCapture(): CapturedExpr<Int, User> {
    val user = User(42, "Ada")
    return expr { user }
}

fun topLevelPropertyCapture() = expr<Int, User> { topLevelUser }

fun objectMemberChain() = expr<Int, String> { CaptureFixtures.memberUser.name!! }

fun memberPropertyCapture() = CaptureOwner(User(45, "Margaret")).captureMember()

fun nestedPropertyChain(): CapturedExpr<Int, Int> {
    val root = User(46, "Dennis")
    return expr { root.age.plus(1) }
}

fun nestedRootCapture(): CapturedExpr<Int, String?> {
    val a = UserEnvelope(User(47, "Barbara"))
    return expr { a.user.name }
}

fun mutableCapture(): CapturedExpr<Int, Int> {
    var limit = 3
    return expr { limit + 1 }
}

fun box(): String {
    val local = localObjectCapture()
    if (local.tree.captures.map { it.name } != listOf("user")) error(local.tree.debugString())
    if (!local.captureValues.contentEquals(arrayOf(User(42, "Ada")))) error(local.captureValues.toList().toString())
    check((local.tree.body as RefExpr).kind == RefKind.CAPTURE)

    val topLevel = topLevelPropertyCapture()
    check(topLevel.captureValues.size == topLevel.tree.captures.size)
    if (topLevel.tree.captures.isNotEmpty()) error(topLevel.tree.debugString())
    check(topLevel.tree.body is PropertyGetExpr)

    val chain = objectMemberChain()
    check(chain.captureValues.size == chain.tree.captures.size)
    if (chain.tree.captures.isNotEmpty()) error(chain.tree.debugString())
    check(chain.tree.body !is ConstExpr)

    val member = memberPropertyCapture()
    check(member.captureValues.size == member.tree.captures.size)
    if (member.captureValues.singleOrNull() !is CaptureOwner) error(member.captureValues.toList().toString())
    if (member.tree.captures.single().captureKind.name != "THIS") error(member.tree.captures.toString())

    val nested = nestedPropertyChain()
    check(nested.captureValues.size == nested.tree.captures.size)
    if (nested.captureValues.singleOrNull() != User(46, "Dennis")) error(nested.captureValues.toList().toString())

    val rootChain = nestedRootCapture()
    if (rootChain.tree.captures.map { it.name } != listOf("a")) error(rootChain.tree.captures.toString())
    if (rootChain.captureValues.singleOrNull() != UserEnvelope(User(47, "Barbara"))) error(rootChain.captureValues.toList().toString())

    val mutable = mutableCapture()
    if (mutable.tree.captures.map { it.name } != listOf("limit")) error(mutable.tree.captures.toString())
    if (mutable.tree.captures.single().captureKind.name != "MUTABLE_CELL") error(mutable.tree.captures.toString())
    if (mutable.captureValues.singleOrNull() != 3) error(mutable.captureValues.toList().toString())
    return "OK"
}
