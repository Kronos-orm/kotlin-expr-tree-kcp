import com.kotlinorm.experimental.exprtree.api.IndexAccessExpr
import com.kotlinorm.experimental.exprtree.api.IndexAccessOperation
import com.kotlinorm.experimental.exprtree.api.PropertyAccessExpr
import com.kotlinorm.experimental.exprtree.api.PropertyAccessOperation
import com.kotlinorm.experimental.exprtree.api.collect
import com.kotlinorm.experimental.exprtree.api.debugString
import com.kotlinorm.experimental.exprtree.api.expr

class Matrix(private val values: Array<Array<String>>) {
    operator fun get(row: Int, column: Int): String = values[row][column]
    operator fun set(row: Int, column: Int, value: String) { values[row][column] = value }
}

class PropertyBox(var value: String)

fun indexedRead(matrix: Matrix) = expr<Int, String> { matrix[it, 1] }
fun indexedWrite(matrix: Matrix) = expr<Int, Unit> { matrix[it, 1] = "changed" }
fun propertyWrite(box: PropertyBox) = expr<Int, Unit> { box.value = "changed" }

fun box(): String {
    val captured = indexedRead(Matrix(arrayOf(arrayOf("a", "b"))))
    val index = captured.tree.body.collect().filterIsInstance<IndexAccessExpr>().singleOrNull()
        ?: error(captured.tree.debugString())
    check(index.indices.size == 2)
    check(index.callable.name == "get")
    check(index.operation == IndexAccessOperation.GET)
    val write = indexedWrite(Matrix(arrayOf(arrayOf("a", "b"))))
    val set = write.tree.body.collect().filterIsInstance<IndexAccessExpr>().singleOrNull { it.operation == IndexAccessOperation.SET }
        ?: error(write.tree.debugString())
    check(set.indices.size == 2)
    check(set.callable.name == "set")
    val property = propertyWrite(PropertyBox("original"))
    val propertySet = property.tree.body.collect().filterIsInstance<PropertyAccessExpr>().singleOrNull()
        ?: error(property.tree.debugString())
    check(propertySet.operation == PropertyAccessOperation.SET)
    check(propertySet.property.name.contains("set", ignoreCase = true)) { property.tree.debugString() }
    return "OK"
}
