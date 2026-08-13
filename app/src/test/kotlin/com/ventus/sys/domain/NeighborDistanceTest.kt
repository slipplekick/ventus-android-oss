package com.ventus.sys.domain

import com.ventus.sys.domain.model.TrackFeatures
import org.junit.Assert.assertEquals
import org.junit.Test

class NeighborDistanceTest {
    @Test
    fun `identical tracks have zero distance`() {
        val t = TrackFeatures("a", 69.0, 67.0, 60.0, 120.0, 10.0, 5.0, -8.0)
        assertEquals(0.0, NeighborDistance.distance(t, t), 0.0001)
    }

    @Test
    fun `unlike ScoreEngine, acousticness always contributes regardless of magnitude`() {
        // ScoreEngine excludes acousticness below 2.0 entirely; NeighborDistance
        // must NOT — this is the deliberate divergence documented on the class.
        val target = TrackFeatures("t", 69.0, 67.0, 60.0, 120.0, 0.0, 0.0, -8.0)
        val a = TrackFeatures("a", 69.0, 67.0, 60.0, 120.0, 0.0, 0.0, -8.0)
        val b = TrackFeatures("b", 69.0, 67.0, 60.0, 120.0, 1.5, 0.0, -8.0)
        assert(NeighborDistance.distance(b, target) > NeighborDistance.distance(a, target))
    }

    @Test
    fun `nearest sorts ascending and respects the limit`() {
        val target = TrackFeatures("t", 50.0, 50.0, 50.0, 120.0, 0.0, 0.0, -8.0)
        val far = TrackFeatures("far", 0.0, 0.0, 0.0, 60.0, 0.0, 0.0, -20.0)
        val close = TrackFeatures("close", 51.0, 50.0, 50.0, 120.0, 0.0, 0.0, -8.0)
        val medium = TrackFeatures("medium", 40.0, 45.0, 45.0, 110.0, 0.0, 0.0, -10.0)

        val result = NeighborDistance.nearest(target, listOf(far, close, medium), limit = 2)
        assertEquals(listOf("close", "medium"), result.map { it.id })
    }
}
