package app.liftos

import org.junit.Assert.assertEquals
import org.junit.Test

class SmokeTest {
    @Test
    fun entity() = assertEquals(3, SmokeEntity("a", 3).value)
}
