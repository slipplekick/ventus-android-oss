package com.ventus.sys.domain

import com.ventus.sys.domain.TasteProfileCalculator.ProfileInputRow
import com.ventus.sys.domain.model.TasteProfile
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class TasteProfileCalculatorTest {
    private val today = LocalDate.of(2026, 8, 12)

    @Test
    fun `empty rows returns the fallback profile`() {
        val result = TasteProfileCalculator.calculate(emptyList(), today)
        assertEquals(TasteProfile.FALLBACK, result)
    }

    @Test
    fun `single row returns that row's own values (no averaging needed)`() {
        val row =
            ProfileInputRow(
                energy = 80.0,
                valence = 70.0,
                danceability = 65.0,
                bpm = 128.0,
                acousticness = 5.0,
                instrumentalness = 2.0,
                loudness = -6.0,
                addedAt = today,
            )
        val result = TasteProfileCalculator.calculate(listOf(row), today)
        assertEquals(80.0, result.energy, 0.01)
        assertEquals(70.0, result.valence, 0.01)
        assertEquals(65.0, result.dance, 0.01)
        assertEquals(128.0, result.bpm, 0.01)
    }

    @Test
    fun `recent tracks (within 30 days) are weighted 6x heavier than stale ones (over 365 days)`() {
        // weight 3.0 vs 0.5 — app.py:422's w(dt) buckets.
        val recent = ProfileInputRow(100.0, 100.0, 100.0, 120.0, 0.0, 0.0, -8.0, today.minusDays(1))
        val stale = ProfileInputRow(0.0, 0.0, 0.0, 120.0, 0.0, 0.0, -8.0, today.minusDays(400))
        val result = TasteProfileCalculator.calculate(listOf(recent, stale), today)
        // weighted mean = (100*3.0 + 0*0.5) / 3.5 = 85.71...
        assertEquals(85.71, result.energy, 0.01)
    }

    @Test
    fun `scale-if-fraction rule leaves already-0-100-scale values untouched`() {
        // energy=80 (already > 1) must NOT be multiplied by 100.
        val row = ProfileInputRow(80.0, 0.0, 0.0, 120.0, 0.0, 0.0, -8.0, today)
        val result = TasteProfileCalculator.calculate(listOf(row), today)
        assertEquals(80.0, result.energy, 0.01)
    }

    @Test
    fun `bpm and loudness are never rescaled even when small`() {
        val row = ProfileInputRow(0.0, 0.0, 0.0, 0.5, 0.0, 0.0, -8.0, today)
        val result = TasteProfileCalculator.calculate(listOf(row), today)
        // bpm=0.5 stays 0.5, NOT scaled to 50 (unlike energy/valence/dance/ac/ins).
        assertEquals(0.5, result.bpm, 0.01)
    }
}
