package com.king.mob_app_assignment.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Civic/Trustworthy palette.
 *
 * Primary: bright civic blue - energetic and app-like rather than the previous muted municipal
 * teal, matching the "Shake to Report" reference direction (2026-08 restyle) while keeping the
 * same tonal-scale structure (10/20/30/40/80/90/95/100) so Theme.kt didn't need to change shape,
 * only these values.
 * Secondary/Accent: amber/orange - reserved for the urgent "report an issue" action
 * (shake/capture CTA) so it reads as the one thing that demands attention.
 * Tertiary: a supportive teal-green used for positive/resolved states and secondary accents.
 */

// ---------- Brand: Civic Blue (Primary) ----------
val CivicBlue10 = Color(0xFF00174A)
val CivicBlue20 = Color(0xFF002B7A)
val CivicBlue30 = Color(0xFF0040A8)
val CivicBlue40 = Color(0xFF1E5FD9) // primary (light) - bright civic blue
val CivicBlue80 = Color(0xFFB0C6FF) // primary (dark)
val CivicBlue90 = Color(0xFFDCE6FF)
val CivicBlue95 = Color(0xFFEEF2FF)
val CivicBlue100 = Color(0xFFFFFFFF)

// ---------- Accent: Civic Amber (Secondary / urgent CTA) ----------
val CivicAmber10 = Color(0xFF2B1600)
val CivicAmber20 = Color(0xFF442400)
val CivicAmber30 = Color(0xFF663600)
val CivicAmber40 = Color(0xFFB2650A) // accent (light) - "report issue" CTA
val CivicAmber80 = Color(0xFFFFB871) // accent (dark)
val CivicAmber90 = Color(0xFFFFDDB8)
val CivicAmber95 = Color(0xFFFFEEDA)

// ---------- Supportive: Civic Teal-Green (Tertiary / resolved) ----------
val CivicTeal10 = Color(0xFF00201C)
val CivicTeal20 = Color(0xFF00382F)
val CivicTeal30 = Color(0xFF005143)
val CivicTeal40 = Color(0xFF1C6E5D) // tertiary (light)
val CivicTeal80 = Color(0xFF83D5C0)
val CivicTeal90 = Color(0xFFA0F2DB)

// ---------- Neutrals ----------
val CivicNeutral10 = Color(0xFF1A1C1E)
val CivicNeutral20 = Color(0xFF2F3033)
val CivicNeutral90 = Color(0xFFE2E2E5)
val CivicNeutral95 = Color(0xFFF3F3F6)
val CivicNeutral99 = Color(0xFFFBFCFE)
val CivicNeutralVariant30 = Color(0xFF43474E)
val CivicNeutralVariant50 = Color(0xFF73777F)
val CivicNeutralVariant60 = Color(0xFF8D9199)
val CivicNeutralVariant80 = Color(0xFFC3C7CF)
val CivicNeutralVariant90 = Color(0xFFDFE2EB)

// ---------- Error ----------
val CivicError10 = Color(0xFF410002)
val CivicError20 = Color(0xFF690005)
val CivicError30 = Color(0xFF93000A)
val CivicError40 = Color(0xFFBA1A1A)
val CivicError80 = Color(0xFFFFB4AB)
val CivicError90 = Color(0xFFFFDAD6)

// ---------- Success (used for confirmations, not part of the core scheme roles) ----------
val CivicSuccess40 = Color(0xFF2E7D32)
val CivicSuccess80 = Color(0xFF8FDB93)

// ---------- Semantic report-status colors (used by StatusBadge, independent of theme roles) ----------
// Matches the map legend's Pending (orange) / In Progress (blue) / Resolved (green) convention -
// Submitted now reads as "Pending" (orange, "received, awaiting triage") and In Review as
// "In Progress" (blue, "actively being worked") - the inverse of the colors each held before this
// restyle, since the reference direction ties orange to the earliest/least-progressed state.
val StatusSubmittedLight = Color(0xFFC77700)
val StatusSubmittedLightContainer = Color(0xFFFFE4BC)
val StatusSubmittedDark = Color(0xFFFFBB63)
val StatusSubmittedDarkContainer = Color(0xFF6B4300)

// In review ("In Progress"): civic blue - "actively being worked, needs attention"
val StatusInReviewLight = Color(0xFF1E5FD9)
val StatusInReviewLightContainer = Color(0xFFDCE6FF)
val StatusInReviewDark = Color(0xFFB0C6FF)
val StatusInReviewDarkContainer = Color(0xFF0040A8)

// Resolved: green - "done, positive outcome"
val StatusResolvedLight = Color(0xFF2E7D32)
val StatusResolvedLightContainer = Color(0xFFD9F2DA)
val StatusResolvedDark = Color(0xFF8FDB93)
val StatusResolvedDarkContainer = Color(0xFF1B4B1E)

// ---------- Gradients ----------
// Every gradient in the app is derived from the same three brand hues above - never a new,
// unrelated color introduced purely for a gradient effect. This is what gives the CTA/hero
// surfaces a sense of depth without the palette ever feeling like it's fighting itself.

/**
 * Solid civic-blue fill - used behind hero/brand moments (auth header, Home's hero header,
 * Profile's header, PrimaryActionButton) and every other blue surface in the app. Kept as a
 * single-color Brush (rather than switching every call site to a plain Color) so callers don't
 * need to change; this used to blend blue into teal (CivicTeal30/40) for a diagonal wash, which
 * read as cyan-tinted rather than a clean light blue - flattened to one solid stop per theme
 * (2026-08 restyle) so "blue" means the same one color everywhere, not a gradient that drifts
 * toward a different hue partway across.
 */
fun heroGradient(darkTheme: Boolean): Brush = Brush.linearGradient(
    colors = if (darkTheme) {
        listOf(CivicBlue80, CivicBlue80)
    } else {
        listOf(CivicBlue40, CivicBlue40)
    },
)

/** Amber-forward gradient reserved for the single most urgent action on a screen (PrimaryActionButton). */
fun ctaGradient(darkTheme: Boolean): Brush = Brush.linearGradient(
    colors = if (darkTheme) {
        listOf(CivicAmber80, Color(0xFFFFA451))
    } else {
        listOf(Color(0xFFD97E1F), CivicAmber40)
    },
)

/** Subtle vertical sheen used on elevated cards/tiles - a whisper of depth, not a visible band. */
fun surfaceSheen(darkTheme: Boolean): Brush = Brush.verticalGradient(
    colors = if (darkTheme) {
        listOf(CivicNeutral20.copy(alpha = 0.6f), Color.Transparent)
    } else {
        listOf(CivicBlue100.copy(alpha = 0.5f), Color.Transparent)
    },
)
