package com.king.mob_app_assignment.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Civic/Trustworthy palette.
 *
 * Primary: deep municipal blue/teal - conveys official trust, calm authority.
 * Secondary/Accent: amber/orange - reserved for the urgent "report an issue" action
 * (shake/capture CTA) so it reads as the one thing that demands attention.
 * Tertiary: a supportive teal-green used for positive/resolved states and secondary accents.
 */

// ---------- Brand: Civic Blue (Primary) ----------
val CivicBlue10 = Color(0xFF00131D)
val CivicBlue20 = Color(0xFF002635)
val CivicBlue30 = Color(0xFF003B52)
val CivicBlue40 = Color(0xFF0B4F6C) // primary (light)
val CivicBlue80 = Color(0xFF9FCBE0) // primary (dark)
val CivicBlue90 = Color(0xFFCCE7F5)
val CivicBlue95 = Color(0xFFE6F3FA)
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
// Submitted: neutral civic blue - "received, awaiting triage"
val StatusSubmittedLight = Color(0xFF0B4F6C)
val StatusSubmittedLightContainer = Color(0xFFD6EAF5)
val StatusSubmittedDark = Color(0xFF9FCBE0)
val StatusSubmittedDarkContainer = Color(0xFF003B52)

// In review: amber - "actively being worked, needs attention"
val StatusInReviewLight = Color(0xFFB2650A)
val StatusInReviewLightContainer = Color(0xFFFFE3C2)
val StatusInReviewDark = Color(0xFFFFB871)
val StatusInReviewDarkContainer = Color(0xFF663600)

// Resolved: green - "done, positive outcome"
val StatusResolvedLight = Color(0xFF2E7D32)
val StatusResolvedLightContainer = Color(0xFFD9F2DA)
val StatusResolvedDark = Color(0xFF8FDB93)
val StatusResolvedDarkContainer = Color(0xFF1B4B1E)
