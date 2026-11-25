package com.example.domain

import android.hardware.GeomagneticField
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

object QiblaCalculator {
    private const val KAABA_LAT = 21.422487
    private const val KAABA_LON = 39.826206
    private const val EARTH_RADIUS_KM = 6371.0

    /**
     * Returns Qibla bearing in degrees clockwise from True North [0, 360).
     */
    fun calculateBearing(latitude: Double, longitude: Double): Double {
        val lat1 = Math.toRadians(latitude)
        val lon1 = Math.toRadians(longitude)
        val lat2 = Math.toRadians(KAABA_LAT)
        val lon2 = Math.toRadians(KAABA_LON)

        val dLon = lon2 - lon1
        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)

        val bearingDeg = Math.toDegrees(atan2(y, x))
        return ((bearingDeg % 360.0) + 360.0) % 360.0
    }

    /**
     * Returns Haversine great-circle distance to the Kaaba in kilometers.
     */
    fun distanceToMakkahKm(latitude: Double, longitude: Double): Double {
        val dLat = Math.toRadians(KAABA_LAT - latitude)
        val dLon = Math.toRadians(KAABA_LON - longitude)
        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(latitude)) * cos(Math.toRadians(KAABA_LAT)) *
            sin(dLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS_KM * c
    }

    /**
     * Returns magnetic declination in degrees (positive = magnetic north is east of true north).
     */
    fun magneticDeclination(latitude: Double, longitude: Double): Float {
        return try {
            GeomagneticField(
                latitude.toFloat(),
                longitude.toFloat(),
                0f,
                System.currentTimeMillis()
            ).declination
        } catch (_: Exception) {
            0f
        }
    }

    fun cardinalDirectionLabel(degrees: Double): String {
        val dirs = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
        val index = (((degrees + 22.5) % 360.0) / 45.0).toInt().coerceIn(0, 7)
        return dirs[index]
    }
}
