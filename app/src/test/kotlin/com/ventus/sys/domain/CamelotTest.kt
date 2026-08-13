package com.ventus.sys.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class CamelotTest {
    @Test
    fun `known key-mode pairs match app_py's arrays exactly`() {
        assertEquals("8B", Camelot.get(0, 1))
        assertEquals("1B", Camelot.get(11, 1))
        assertEquals("5A", Camelot.get(0, 0))
        assertEquals("10A", Camelot.get(11, 0))
    }

    @Test
    fun `invalid key or mode returns the double-dash sentinel`() {
        assertEquals("--", Camelot.get(-1, 1))
        assertEquals("--", Camelot.get(12, 1))
        assertEquals("--", Camelot.get(0, 2))
    }

    @Test
    fun `parse is the exact inverse of get for every valid pair`() {
        for (key in 0..11) {
            for (mode in listOf(0, 1)) {
                val camelot = Camelot.get(key, mode)
                assertEquals(key to mode, Camelot.parse(camelot))
            }
        }
    }

    @Test
    fun `parse returns the unknown sentinel for garbage input`() {
        assertEquals(-1 to 1, Camelot.parse("garbage"))
        assertEquals(-1 to 1, Camelot.parse(null))
        assertEquals(-1 to 1, Camelot.parse(""))
    }

    @Test
    fun `parse is case-insensitive and trims whitespace`() {
        assertEquals(0 to 1, Camelot.parse(" 8b "))
    }
}
