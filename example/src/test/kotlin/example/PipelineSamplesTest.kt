package example

import com.kotlinorm.experimental.exprtree.api.BinaryExpr
import com.kotlinorm.experimental.exprtree.api.StringTemplateExpr
import com.kotlinorm.experimental.exprtree.api.collect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PipelineSamplesTest {
    @Test
    fun `annotated filter and map lambdas carry their generated trees`() {
        val pipeline = userPipeline(18, "member")
        val filter = assertIs<FilterStep<User>>(pipeline.steps[0])
        val map = assertIs<MapStep<User, String>>(pipeline.steps[1])

        assertIs<BinaryExpr>(filter.predicate.tree.body)
        assertEquals(listOf("minimumAge"), filter.predicate.tree.captures.map { it.name })
        assertEquals(18, filter.predicate.bindings().asMap().values.single())
        assertTrue(filter.executable(User(age = 20, name = "Ada")))
        assertTrue(!filter.executable(User(age = 17, name = "Ada")))

        assertTrue(map.transform.tree.body.collect().any { it is StringTemplateExpr })
        assertEquals(listOf("prefix"), map.transform.tree.captures.map { it.name })
        assertEquals("member", map.transform.bindings().asMap().values.single())
        assertEquals("Ada-member", map.executable(User(age = 20, name = "Ada")))
    }
}
