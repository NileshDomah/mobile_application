package com.king.mob_app_assignment.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RiskScorerTest {

    @Test
    fun `area key buckets nearby coordinates into the same cell`() {
        val a = RiskScorer.areaKeyFor(12.9716, 77.5946)
        val b = RiskScorer.areaKeyFor(12.9718, 77.5948) // a few metres away, same ~550m cell
        assertEquals(a, b)
    }

    @Test
    fun `area key differs for coordinates in different cells`() {
        val a = RiskScorer.areaKeyFor(12.9716, 77.5946)
        val b = RiskScorer.areaKeyFor(13.5000, 78.2000)
        assertTrue(a != b)
    }

    @Test
    fun `report volume points scale with count and cap at 30`() {
        val small = RiskScorer.score(reports(count = 2), incidentCount = 0)
        val large = RiskScorer.score(reports(count = 50), incidentCount = 0)

        assertEquals(8, small.first { it.label == "Report volume" }.points) // 2 * 4
        assertEquals(30, large.first { it.label == "Report volume" }.points) // capped
    }

    @Test
    fun `unresolved backlog reflects the ratio of non-resolved reports`() {
        val allResolved = RiskScorer.score(
            listOf(report(status = ReportStatus.RESOLVED), report(status = ReportStatus.RESOLVED), report(status = ReportStatus.RESOLVED)),
            incidentCount = 0,
        )
        assertEquals(0, allResolved.first { it.label == "Unresolved backlog" }.points)

        val allOpen = RiskScorer.score(
            listOf(report(status = ReportStatus.SUBMITTED), report(status = ReportStatus.SUBMITTED), report(status = ReportStatus.SUBMITTED)),
            incidentCount = 0,
        )
        assertEquals(20, allOpen.first { it.label == "Unresolved backlog" }.points)
    }

    @Test
    fun `confirmed incident points scale with count and cap at 15`() {
        val none = RiskScorer.score(reports(count = 3), incidentCount = 0)
        val many = RiskScorer.score(reports(count = 3), incidentCount = 10)

        assertEquals(0, none.first { it.label == "Confirmed incidents" }.points)
        assertEquals(15, many.first { it.label == "Confirmed incidents" }.points) // capped at 3*5
    }

    @Test
    fun `total score is always clamped to 0 to 100`() {
        val factors = RiskScorer.score(reports(count = 500, priority = ReportPriority.URGENT), incidentCount = 999)
        val total = RiskScorer.totalScore(factors)
        assertTrue(total in 0..100)
    }

    @Test
    fun `risk level thresholds match the documented bands`() {
        assertEquals(RiskLevel.LOW, RiskLevel.forScore(0))
        assertEquals(RiskLevel.LOW, RiskLevel.forScore(24))
        assertEquals(RiskLevel.MODERATE, RiskLevel.forScore(25))
        assertEquals(RiskLevel.MODERATE, RiskLevel.forScore(49))
        assertEquals(RiskLevel.HIGH, RiskLevel.forScore(50))
        assertEquals(RiskLevel.HIGH, RiskLevel.forScore(74))
        assertEquals(RiskLevel.CRITICAL, RiskLevel.forScore(75))
        assertEquals(RiskLevel.CRITICAL, RiskLevel.forScore(100))
    }

    @Test
    fun `average resolution hours is null when nothing has been resolved yet`() {
        val unresolved = listOf(report(status = ReportStatus.SUBMITTED))
        assertNull(RiskScorer.averageResolutionHours(unresolved))
    }

    @Test
    fun `average resolution hours computes the gap between submission and resolution`() {
        val submittedAt = 0L
        val resolvedAt = 2 * 60 * 60 * 1000L // 2 hours later
        val resolved = listOf(
            report(status = ReportStatus.RESOLVED, timestamp = submittedAt).copy(
                statusHistory = listOf(StatusHistoryEntry(status = ReportStatus.RESOLVED, timestamp = resolvedAt)),
            ),
        )
        assertEquals(2.0, RiskScorer.averageResolutionHours(resolved)!!, 0.001)
    }

    @Test
    fun `isRecurring is false when nothing has ever been resolved`() {
        assertFalse(RiskScorer.isRecurring(listOf(report(status = ReportStatus.SUBMITTED))))
    }

    @Test
    fun `isRecurring is true when a new report appears after the last resolution`() {
        val resolvedAt = 1_000L
        val resolved = report(status = ReportStatus.RESOLVED, timestamp = 0L).copy(
            statusHistory = listOf(StatusHistoryEntry(status = ReportStatus.RESOLVED, timestamp = resolvedAt)),
        )
        val newerReport = report(status = ReportStatus.SUBMITTED, timestamp = resolvedAt + 500L)
        assertTrue(RiskScorer.isRecurring(listOf(resolved, newerReport)))
    }

    @Test
    fun `recommendation escalates with risk level`() {
        val critical = RiskScorer.recommendationFor(RiskLevel.CRITICAL, isRecurring = false, unresolvedCount = 5)
        val low = RiskScorer.recommendationFor(RiskLevel.LOW, isRecurring = false, unresolvedCount = 0)
        assertTrue(critical.contains("Prioritize", ignoreCase = true))
        assertTrue(low.contains("No elevated action", ignoreCase = true))
    }

    private fun reports(count: Int, priority: ReportPriority = ReportPriority.NORMAL): List<Report> =
        (1..count).map { report(priority = priority) }

    private fun report(
        status: ReportStatus = ReportStatus.SUBMITTED,
        priority: ReportPriority = ReportPriority.NORMAL,
        timestamp: Long = System.currentTimeMillis(),
    ): Report = Report(status = status, priority = priority, timestamp = timestamp)
}
