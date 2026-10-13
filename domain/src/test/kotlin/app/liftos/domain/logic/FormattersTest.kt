package app.liftos.domain.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FormattersTest {
    @Test
    fun `numbers drop trailing zeros`() {
        assertEquals("60", Formatters.number(60.0))
        assertEquals("62.5", Formatters.number(62.5))
        assertEquals("", Formatters.number(null))
        assertEquals("0.33", Formatters.number(1.0 / 3.0))
    }

    @Test
    fun `thousands separator`() {
        assertEquals("12,340", Formatters.thousands(12340.0))
        assertEquals("0", Formatters.thousands(0.0))
    }

    @Test
    fun `parses decimal comma and point`() {
        assertEquals(62.5, Formatters.parseDecimal("62,5")!!, 0.0)
        assertEquals(62.5, Formatters.parseDecimal(" 62.5 ")!!, 0.0)
        assertNull(Formatters.parseDecimal(""))
        assertNull(Formatters.parseDecimal("abc"))
        assertNull(Formatters.parseDecimal("."))
    }

    @Test
    fun `duration formatting`() {
        assertEquals("1:02:05", Formatters.duration(3_725_000))
        assertEquals("2:05", Formatters.duration(125_000))
        assertEquals("0:00", Formatters.duration(-5))
    }
}
