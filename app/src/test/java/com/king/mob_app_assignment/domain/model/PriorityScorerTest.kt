package com.king.mob_app_assignment.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PriorityScorerTest {

    @Test
    fun `higher severity produces a higher severity factor`() {
        val low = PriorityScorer.score(incident(severity = ReportPriority.LOW), reportCount = 0)
        val urgent = PriorityScorer.score(incident(severity = ReportPriority.URGENT), reportCount = 0)

        val lowPoints = low.first { it.label == "Severity" }.points
        val urgentPoints = urgent.first { it.label == "Severity" }.points

        assertTrue(urgentPoints > lowPoints)
    }

    @Test
    fun `report count points are capped at 20`() {
        val factors = PriorityScorer.score(incident(), reportCount = 50)
        assertEquals(20, factors.first { it.label == "Report count" }.points)
    }

    @Test
    fun `recurrence factor only appears when an incident reopened after a prior resolution`() {
        val neverResolved = PriorityScorer.score(incident(status = IncidentStatus.DETECTED, history = emptyList()), reportCount = 0)
        assertFalse(neverResolved.any { it.label == "Recurrence" })

        val reopened = PriorityScorer.score(
            incident(
                status = IncidentStatus.ASSIGNED,
                history = listOf(IncidentHistoryEntry(status = IncidentStatus.RESOLVED)),
            ),
            reportCount = 0,
        )
        assertTrue(reopened.any { it.label == "Recurrence" })

        // Still marked RESOLVED/VERIFIED/CLOSED itself - not "reopened," so no recurrence factor
        // even though the history contains a RESOLVED entry.
        val stillResolved = PriorityScorer.score(
            incident(
                status = IncidentStatus.RESOLVED,
                history = listOf(IncidentHistoryEntry(status = IncidentStatus.RESOLVED)),
            ),
            reportCount = 0,
        )
        assertFalse(stillResolved.any { it.label == "Recurrence" })
    }

    @Test
    fun `water leak is flagged as an emergency classification`() {
        val factors = PriorityScorer.score(incident(category = ReportCategory.WATER_LEAK), reportCount = 0)
        assertTrue(factors.any { it.label == "Emergency classification" })
    }

    @Test
    fun `custom category is flagged as an emergency only when the label matches a hazard keyword`() {
        val hazardous = PriorityScorer.score(
            incident(category = ReportCategory.CUSTOM, customLabel = "Downed power line across the road"),
            reportCount = 0,
        )
        assertTrue(hazardous.any { it.label == "Emergency classification" })

        val benign = PriorityScorer.score(
            incident(category = ReportCategory.CUSTOM, customLabel = "Loose paving stone"),
            reportCount = 0,
        )
        assertFalse(benign.any { it.label == "Emergency classification" })
    }

    @Test
    fun `total score never exceeds the 0 to 100 range`() {
        val factors = PriorityScorer.score(
            incident(
                severity = ReportPriority.URGENT,
                category = ReportCategory.WATER_LEAK,
                status = IncidentStatus.ASSIGNED,
                history = listOf(IncidentHistoryEntry(status = IncidentStatus.RESOLVED)),
                createdAt = 0L,
            ),
            reportCount = 999,
        )
        val total = PriorityScorer.totalScore(factors)
        assertTrue(total in 0..100)
    }

    private fun incident(
        severity: ReportPriority = ReportPriority.NORMAL,
        category: ReportCategory = ReportCategory.OTHER,
        customLabel: String = "",
        status: IncidentStatus = IncidentStatus.DETECTED,
        history: List<IncidentHistoryEntry> = emptyList(),
        createdAt: Long = System.currentTimeMillis(),
    ): Incident = Incident(
        severity = severity,
        category = category,
        customLabel = customLabel,
        status = status,
        history = history,
        createdAt = createdAt,
    )
}
