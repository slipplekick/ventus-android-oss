package com.ventus.sys.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class PythonRoundTest {
    @Test
    fun `half-to-even matches Python round() at exact-half boundaries`() {
        // Python: round(0.5)=0, round(1.5)=2, round(2.5)=2, round(3.5)=4
        assertEquals(0, PythonRound.toInt(0.5))
        assertEquals(2, PythonRound.toInt(1.5))
        assertEquals(2, PythonRound.toInt(2.5))
        assertEquals(4, PythonRound.toInt(3.5))
    }

    @Test
    fun `non-half values round normally`() {
        assertEquals(85, PythonRound.toInt(84.6))
        assertEquals(84, PythonRound.toInt(84.4))
    }

    @Test
    fun `decimal rounding matches Python round(x, n)`() {
        // Python: round(1.005, 2) == 1.0 (binary float representation, not 1.01 —
        // this is a well-known Python float gotcha; BigDecimal(double) reproduces
        // it exactly because it captures the same true binary value.)
        assertEquals(1.0, PythonRound.toDecimals(1.005, 2), 0.0001)
        assertEquals(2.5, PythonRound.toDecimals(2.5, 1), 0.0001)
    }
}
