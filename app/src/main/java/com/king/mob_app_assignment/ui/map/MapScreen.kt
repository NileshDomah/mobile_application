package com.king.mob_app_assignment.ui.map

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.background
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import com.king.mob_app_assignment.R
import com.king.mob_app_assignment.domain.model.Report
import com.king.mob_app_assignment.domain.model.ReportCategory
import com.king.mob_app_assignment.ui.components.AppTopBar
import com.king.mob_app_assignment.ui.theme.Elevation
import com.king.mob_app_assignment.ui.theme.translatedStringResource

// Marker pins only take a hue (BitmapDescriptorFactory.HUE_*), not an arbitrary RGB color - one
// distinct hue per issue TYPE (2026-08 restyle, replacing the earlier per-STATUS coloring) so a
// citizen can tell what kind of issue a pin represents at a glance without tapping it, matching
// the reference mockup's "different color pin per issue type" direction. CUSTOM/UNCATEGORIZED
// share OTHER's color rather than getting their own - CUSTOM is already a free-text catch-all
// conceptually adjacent to OTHER, and UNCATEGORIZED is a submit-time placeholder that's never
// meant to be a citizen-facing category at all (see ReportCategory.selectableEntries's doc).
private fun ReportCategory.markerHue(): Float = when (this) {
    ReportCategory.POTHOLE -> BitmapDescriptorFactory.HUE_RED
    ReportCategory.STREETLIGHT -> BitmapDescriptorFactory.HUE_YELLOW
    ReportCategory.GARBAGE -> BitmapDescriptorFactory.HUE_GREEN
    ReportCategory.WATER_LEAK -> BitmapDescriptorFactory.HUE_AZURE
    ReportCategory.OTHER, ReportCategory.CUSTOM, ReportCategory.UNCATEGORIZED -> BitmapDescriptorFactory.HUE_VIOLET
}

// Solid RGB equivalents of the hues above, for the legend swatches below (BitmapDescriptorFactory
// hues only apply to actual map markers, not arbitrary Compose UI) - kept as a single mapping
// alongside markerHue() rather than duplicated at each LegendEntry call site.
private fun ReportCategory.legendColor(): androidx.compose.ui.graphics.Color = when (this) {
    ReportCategory.POTHOLE -> androidx.compose.ui.graphics.Color(0xFFEA4335)
    ReportCategory.STREETLIGHT -> androidx.compose.ui.graphics.Color(0xFFFBBC04)
    ReportCategory.GARBAGE -> androidx.compose.ui.graphics.Color(0xFF34A853)
    ReportCategory.WATER_LEAK -> androidx.compose.ui.graphics.Color(0xFF1E88E5)
    ReportCategory.OTHER, ReportCategory.CUSTOM, ReportCategory.UNCATEGORIZED -> androidx.compose.ui.graphics.Color(0xFF9C27B0)
}

/**
 * Community-wide map of every report, pinned by status - the fourth bottom-nav tab alongside
 * Home/My Reports (or Admin's own tabs)/Profile. Uses the Google Maps Compose SDK (see
 * app/build.gradle.kts and AndroidManifest.xml's com.google.android.geo.API_KEY meta-data for the
 * key wiring) - the map style intentionally isn't the app's own MapLibre/Geoapify setup used
 * elsewhere (see map/GeoApiConfig.kt), a deliberate choice for this screen specifically.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MapScreen(
    viewModel: MapViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // Mirrors the same fine/coarse-location pair CaptureViewModel's flow already requests
    // elsewhere in the app - requested here too (rather than assumed already granted) since a
    // citizen could reach the Map tab without ever having opened Capture first. Coarse alone is
    // enough for "show my location on the map" (unlike Capture's precise-location need for the
    // report's own coordinates), so either permission being granted is treated as sufficient.
    val locationPermissionState = rememberPermissionState(Manifest.permission.ACCESS_FINE_LOCATION)
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    androidx.compose.runtime.LaunchedEffect(locationPermissionState.status) {
        if (locationPermissionState.status.isGranted) hasLocationPermission = true
    }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (!hasLocationPermission) locationPermissionState.launchPermissionRequest()
    }

    Scaffold(
        topBar = { AppTopBar(title = translatedStringResource(R.string.map_title)) },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            // MapContent (the actual GoogleMap composable) is composed immediately, NOT gated on
            // uiState.isLoading - Maps SDK's own cold-start cost (native view inflation, style/
            // tile fetch) and Firestore's report-list load are two independent, unrelated delays,
            // and there's no reason to serialize them (wait for Firestore, THEN start Maps) when
            // they can run in parallel. An empty reports list just means zero markers draw until
            // the real data arrives moments later - Maps itself already shows its own tile-loading
            // placeholder in the meantime, so there's nothing for a separate full-screen spinner
            // to usefully cover here.
            MapContent(reports = uiState.reports, myLocationEnabled = hasLocationPermission)
            // Top, not bottom - Maps' own my-location button (enabled above) renders bottom-right
            // by default, and this legend used to sit directly on top of it (and of the floating
            // chat FAB shared across main-graph screens) when placed at the bottom.
            MapLegend(modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp))
        }
    }
}

@Composable
private fun MapContent(reports: List<Report>, myLocationEnabled: Boolean) {
    // Starts at a harmless world-ish default - there is no single "home base" location concept
    // in this app (reports can be anywhere), so this is just "something reasonable to look at"
    // until real data arrives, not a meaningful default location.
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(0.0, 0.0), 2f)
    }

    // MapContent now composes before the report list has necessarily loaded (see MapScreen's own
    // comment on why), so centering can no longer happen once in rememberCameraPositionState's
    // initializer the way it used to - that ran too early to see any reports and would have left
    // the camera stuck at the world-view default forever. Re-centers once, the first time the
    // list goes from not-yet-populated to actually having a report to look at (the Unit -> non-
    // empty transition, not every subsequent list change - a later report added elsewhere
    // shouldn't yank the camera away from wherever the citizen has since panned to).
    var hasCenteredOnData by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(reports.isNotEmpty()) {
        if (reports.isNotEmpty() && !hasCenteredOnData) {
            hasCenteredOnData = true
            val first = reports.first()
            cameraPositionState.position = CameraPosition.fromLatLngZoom(LatLng(first.latitude, first.longitude), 13f)
        }
    }

    GoogleMap(
        modifier = Modifier.fillMaxSize(),
        cameraPositionState = cameraPositionState,
        // isMyLocationEnabled draws the live blue location dot; myLocationButtonEnabled adds the
        // tap-to-recenter-on-me control - both no-op safely if permission somehow isn't granted
        // (the SDK itself checks, this flag just tells it whether to try), but gating on our own
        // hasLocationPermission avoids Maps silently throwing a SecurityException in some SDK
        // versions when the flag is true without the permission actually held.
        properties = MapProperties(isMyLocationEnabled = myLocationEnabled),
        uiSettings = MapUiSettings(myLocationButtonEnabled = myLocationEnabled),
    ) {
        reports.forEach { report ->
            Marker(
                state = MarkerState(position = LatLng(report.latitude, report.longitude)),
                icon = remember(report.category) { BitmapDescriptorFactory.defaultMarker(report.category.markerHue()) },
                title = report.customLabel.ifBlank { report.category.name },
            )
        }
    }
}

@Composable
private fun MapLegend(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = Elevation.raised),
    ) {
        // BUG FIX (found via manual testing, 2026-11-20): a LazyRow here clipped the "Other"
        // entry off the right edge of the screen with no visual hint there was more content to
        // scroll to - the same clipping pattern already fixed in CaptureScreen's CategorySection
        // and photo grid. This is a static legend (5 fixed entries, not a scrollable filter list),
        // so FlowRow wrapping onto a second line when needed is strictly better than clipping.
        @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
        androidx.compose.foundation.layout.FlowRow(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            LegendEntry(color = ReportCategory.POTHOLE.legendColor(), label = translatedStringResource(R.string.map_legend_pothole))
            LegendEntry(color = ReportCategory.STREETLIGHT.legendColor(), label = translatedStringResource(R.string.map_legend_streetlight))
            LegendEntry(color = ReportCategory.GARBAGE.legendColor(), label = translatedStringResource(R.string.map_legend_garbage))
            LegendEntry(color = ReportCategory.WATER_LEAK.legendColor(), label = translatedStringResource(R.string.map_legend_water_leak))
            LegendEntry(color = ReportCategory.OTHER.legendColor(), label = translatedStringResource(R.string.map_legend_other))
        }
    }
}

@Composable
private fun LegendEntry(color: androidx.compose.ui.graphics.Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(10.dp).background(color = color, shape = CircleShape))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}
