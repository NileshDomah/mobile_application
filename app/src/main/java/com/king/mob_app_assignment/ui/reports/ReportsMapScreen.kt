package com.king.mob_app_assignment.ui.reports

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.king.mob_app_assignment.domain.model.Report
import com.king.mob_app_assignment.domain.model.ReportStatus
import com.king.mob_app_assignment.map.GeoApiConfig
import com.king.mob_app_assignment.ui.components.EmptyState
import com.king.mob_app_assignment.ui.components.LoadingIndicator
import com.king.mob_app_assignment.ui.theme.StatusInReviewLight
import com.king.mob_app_assignment.ui.theme.StatusResolvedLight
import com.king.mob_app_assignment.ui.theme.StatusSubmittedLight
import org.maplibre.android.MapLibre
import org.maplibre.android.annotations.Icon
import org.maplibre.android.annotations.IconFactory
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView

private const val DEFAULT_ZOOM = 14.0
private const val WORLD_ZOOM = 1.5
private val DEFAULT_CENTER = LatLng(0.0, 0.0)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsMapScreen(
    onReportClick: (Report) -> Unit,
    viewModel: ReportsMapViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    Scaffold { padding ->
        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                LoadingIndicator()
            }
            return@Scaffold
        }

        if (uiState.reports.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                EmptyState(message = "No reports to plot yet.")
            }
            return@Scaffold
        }

        var mapViewRef by remember { mutableStateOf<MapView?>(null) }
        val reportsByMarkerId = remember { mutableMapOf<Long, Report>() }

        AndroidView(
            modifier = Modifier.fillMaxSize().padding(padding),
            factory = { ctx ->
                MapLibre.getInstance(ctx)
                MapView(ctx).apply {
                    mapViewRef = this
                    getMapAsync { maplibreMap ->
                        maplibreMap.setStyle(GeoApiConfig.styleUrl())

                        maplibreMap.setOnMarkerClickListener { marker ->
                            reportsByMarkerId[marker.id]?.let(onReportClick)
                            true
                        }

                        val reports = uiState.reports
                        val firstPoint = reports.firstOrNull()?.let { LatLng(it.latitude, it.longitude) }
                        maplibreMap.cameraPosition = CameraPosition.Builder()
                            .target(firstPoint ?: DEFAULT_CENTER)
                            .zoom(if (firstPoint != null) DEFAULT_ZOOM else WORLD_ZOOM)
                            .build()

                        reports.forEach { report ->
                            val marker = maplibreMap.addMarker(
                                MarkerOptions()
                                    .setPosition(LatLng(report.latitude, report.longitude))
                                    .setTitle(report.category.displayLabel())
                                    .setSnippet(report.address.ifBlank { "${report.latitude}, ${report.longitude}" })
                                    .setIcon(pinIcon(context, report.status)),
                            )
                            reportsByMarkerId[marker.id] = report
                        }
                    }
                }
            },
        )

        DisposableEffect(mapViewRef) {
            val mapView = mapViewRef
            mapView?.onStart()
            mapView?.onResume()
            onDispose {
                mapView?.onPause()
                mapView?.onStop()
                mapView?.onDestroy()
            }
        }
    }
}

private fun pinIcon(context: android.content.Context, status: ReportStatus): Icon {
    val color = when (status) {
        ReportStatus.SUBMITTED -> StatusSubmittedLight
        ReportStatus.IN_REVIEW -> StatusInReviewLight
        ReportStatus.RESOLVED -> StatusResolvedLight
    }
    val sizeDp = 28
    val sizePx = (sizeDp * context.resources.displayMetrics.density).toInt()
    val bitmap = android.graphics.Bitmap.createBitmap(sizePx, sizePx, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        setColor(
            android.graphics.Color.argb(
                (color.alpha * 255).toInt(),
                (color.red * 255).toInt(),
                (color.green * 255).toInt(),
                (color.blue * 255).toInt(),
            ),
        )
        style = android.graphics.Paint.Style.FILL
    }
    val strokePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        setColor(android.graphics.Color.WHITE)
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = 2 * context.resources.displayMetrics.density
    }
    val radius = sizePx / 2f - strokePaint.strokeWidth
    canvas.drawCircle(sizePx / 2f, sizePx / 2f, radius, paint)
    canvas.drawCircle(sizePx / 2f, sizePx / 2f, radius, strokePaint)
    return IconFactory.getInstance(context).fromBitmap(bitmap)
}
