package example

import com.kotlinorm.experimental.exprtree.api.expr
import example.User

data class User(val age: Int, val name: String?)

fun sample(minAge: Int, prefix: String) = expr<User, Boolean> { user ->
    user.age >= minAge && user.name?.startsWith(prefix) == true
}

fun extendedSample(limit: Int, suffix: String) = expr<Int, Any?> { value ->
    var local = value
    local += limit
    if (local > 0) {
        when {
            local == 1 -> "one-$suffix"
            else -> "value=$local"
        }
    } else {
        local as Any?
    }
}

fun shadowedSample(value: Int) = expr<Int, Int> { input ->
    val value = input + 1
    value
}

fun typeOperatorSample() = expr<Any?, Boolean> { candidate ->
    candidate is String
}

fun safeCastOperatorSample() = expr<Any?, Boolean> { candidate ->
    (candidate as? String)?.isNotEmpty() == true
}

fun subjectWhenSample(limit: Int, suffix: String) = expr<Int, String> { value ->
    when (value + limit) {
        1 -> "one-$suffix"
        else -> "other"
    }
}

fun sampleWithLambdaLocal(minAge: Int) = expr<User, Boolean> { user ->
    val age = user.age
    age >= minAge
}
