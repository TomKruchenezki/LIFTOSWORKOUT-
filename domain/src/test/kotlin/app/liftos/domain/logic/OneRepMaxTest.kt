package app.liftos.domain.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class OneRepMaxTest {
    @Test
    fun `epley formula`() {
        assertEquals(100.0, OneRepMax.epley(100.0, 1)!!, 0.0001)
        assertEquals(100.0 * (1 + 10 / 30.0), OneRepMax.epley(100.0, 10)!!, 0.0001)
    }

    @Test
    fun `brzycki formula`() {
        assertEquals(100.0 * 36.0 / 27.0, OneRepMax.brzycki(100.0, 10)!!, 0.0001)
    }

    @Test
    fun `refuses unreliable or invalid inputs`() {
        assertNull(OneRepMax.epley(100.0, 13))
        assertNull(OneRepMax.epley(100.0, 0))
        assertNull(OneRepMax.epley(0.0, 5))
        assertNotNull(OneRepMax.epley(100.0, 12))
    }
}
