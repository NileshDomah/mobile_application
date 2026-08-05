package com.king.mob_app_assignment.map

import com.king.mob_app_assignment.BuildConfig

// Single place to configure the map provider. Uses Geoapify's vector style (MapLibre-compatible
// style.json), rendered via MapLibre Native for Android - the native counterpart to the
// maplibre-gl JS library, consuming the same style URL format.
//
// The real API key lives in local.properties (GEOAPIFY_API_KEY=...), which is already
// gitignored, and is injected here via BuildConfig rather than hardcoded as a literal string -
// never commit a real key directly into a source file that might end up in a public repo.
object GeoApiConfig {
    private const val TILE_STYLE = "osm-carto"

    fun styleUrl(): String =
        "https://maps.geoapify.com/v1/styles/$TILE_STYLE/style.json?apiKey=${BuildConfig.GEOAPIFY_API_KEY}"
}
