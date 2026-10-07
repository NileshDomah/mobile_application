package com.king.mob_app_assignment.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DepartmentSuggesterTest {

    @Test
    fun `fixed categories map to their expected department`() {
        assertEquals(Department.ROADS, DepartmentSuggester.suggestFor(ReportCategory.POTHOLE))
        assertEquals(Department.ELECTRICAL, DepartmentSuggester.suggestFor(ReportCategory.STREETLIGHT))
        assertEquals(Department.SANITATION, DepartmentSuggester.suggestFor(ReportCategory.GARBAGE))
        assertEquals(Department.WATER, DepartmentSuggester.suggestFor(ReportCategory.WATER_LEAK))
    }

    @Test
    fun `other and uncategorized never suggest a department`() {
        assertNull(DepartmentSuggester.suggestFor(ReportCategory.OTHER))
        assertNull(DepartmentSuggester.suggestFor(ReportCategory.UNCATEGORIZED))
    }

    @Test
    fun `custom category suggests a department from keywords in the label`() {
        assertEquals(Department.ROADS, DepartmentSuggester.suggestFor(ReportCategory.CUSTOM, "Fallen tree blocking the sidewalk"))
        assertEquals(Department.ELECTRICAL, DepartmentSuggester.suggestFor(ReportCategory.CUSTOM, "Sparking wire near the pole"))
        assertEquals(Department.WATER, DepartmentSuggester.suggestFor(ReportCategory.CUSTOM, "Burst pipe flooding the street"))
        assertEquals(Department.SANITATION, DepartmentSuggester.suggestFor(ReportCategory.CUSTOM, "Overflowing trash dump"))
    }

    @Test
    fun `custom category with no matching keyword suggests nothing rather than guessing`() {
        assertNull(DepartmentSuggester.suggestFor(ReportCategory.CUSTOM, "Strange noise at night"))
        assertNull(DepartmentSuggester.suggestFor(ReportCategory.CUSTOM, ""))
    }

    @Test
    fun `keyword matching is case-insensitive`() {
        assertEquals(Department.WATER, DepartmentSuggester.suggestFor(ReportCategory.CUSTOM, "WATER LEAK near the curb"))
    }
}
