package com.ventus.sys.domain

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Python's built-in `round()` uses round-half-to-even (banker's rounding) —
 * `round(0.5) == 0`, `round(1.5) == 2`, `round(2.5) == 2`. Kotlin/Java's
 * `Math.round()` and `kotlin.math.roundToInt()` both round half-away-from-zero
 * instead (`round(0.5) == 1`). Every place app.py calls `round()` on a value
 * that feeds into a displayed/compared number (scores, deltas, taste-profile
 * metrics) needs this, not the JVM default, or the golden-fixture tests
 * comparing against real desktop output will mismatch on exact .5 boundaries.
 */
object PythonRound {
    fun toInt(value: Double): Int = BigDecimal(value).setScale(0, RoundingMode.HALF_EVEN).toInt()

    fun toDecimals(
        value: Double,
        decimals: Int,
    ): Double = BigDecimal(value).setScale(decimals, RoundingMode.HALF_EVEN).toDouble()
}
