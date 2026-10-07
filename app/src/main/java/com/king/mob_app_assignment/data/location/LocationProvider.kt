package com.king.mob_app_assignment.data.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.os.Build
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.Granularity
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.king.mob_app_assignment.util.CrashReporter
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class LocationProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val fusedClient = LocationServices.getFusedLocationProviderClient(context)

    /**
     * Report locations need the actual GPS-chip fix, not a coarse cell/Wi-Fi-derived estimate
     * that a plain fused-provider request can silently fall back to (or hand back a stale cached
     * value for) when GPS hasn't locked yet. This method:
     *  - Forces [Priority.PRIORITY_HIGH_ACCURACY] AND [Granularity.GRANULARITY_FINE], the fused
     *    provider's own signal that a GPS-chip fix is required, not a network-derived one.
     *  - Rejects a returned fix if its reported accuracy radius is worse than
     *    [MAX_ACCEPTABLE_ACCURACY_METERS] - a low-accuracy fix (e.g. from Wi-Fi/cell towers while
     *    GPS is still acquiring a lock) is treated the same as no fix at all, rather than being
     *    silently accepted and stored as if it were precise.
     *  - Times out after [LOCATION_TIMEOUT_MILLIS] rather than hanging indefinitely if GPS never
     *    locks (e.g. indoors, no sky view) - the caller (CaptureViewModel) already treats a null
     *    location as "coordinates unavailable" and proceeds without blocking report submission.
     */
    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(): Location? {
        val request = CurrentLocationRequest.Builder()
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .setGranularity(Granularity.GRANULARITY_FINE)
            .setDurationMillis(LOCATION_TIMEOUT_MILLIS)
            .build()

        val location = withTimeoutOrNull(LOCATION_TIMEOUT_MILLIS) {
            runCatching { fusedClient.getCurrentLocation(request, null).await() }.getOrNull()
        }

        if (location == null) {
            CrashReporter.log("LocationProvider: no location fix within timeout")
            return null
        }

        if (location.hasAccuracy() && location.accuracy > MAX_ACCEPTABLE_ACCURACY_METERS) {
            // A fix this imprecise (typically a cell/Wi-Fi-derived estimate GPS hasn't yet
            // refined) is not the "exact GPS location" a citizen report needs - treated as no
            // fix, same as a timeout, rather than silently stored as if it were exact.
            CrashReporter.log("LocationProvider: rejected fix with accuracy ${location.accuracy}m (limit $MAX_ACCEPTABLE_ACCURACY_METERS m)")
            return null
        }

        return location
    }

    private companion object {
        const val LOCATION_TIMEOUT_MILLIS = 15_000L
        const val MAX_ACCEPTABLE_ACCURACY_METERS = 50f
    }

    suspend fun reverseGeocode(latitude: Double, longitude: Double): String? {
        if (!Geocoder.isPresent()) return null
        val geocoder = Geocoder(context, Locale.getDefault())
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                suspendCancellableCoroutine { continuation ->
                    geocoder.getFromLocation(latitude, longitude, 1) { addresses ->
                        continuation.resume(addresses.firstOrNull()?.getAddressLine(0))
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                geocoder.getFromLocation(latitude, longitude, 1)?.firstOrNull()?.getAddressLine(0)
            }
        } catch (e: Exception) {
            null
        }
    }
}
