package com.example

import com.example.data.GlobalCities
import com.example.data.SurahDirectory
import com.example.domain.PrayerTimeCalculator
import com.example.domain.QiblaCalculator
import com.example.model.AsrMadhab
import com.example.model.CalculationMethod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NoorDomainTest {

    @Test
    fun qiblaBearingIsAccurateGlobally() {
        // New York -> ~58.4° NE
        val nyBearing = QiblaCalculator.calculateBearing(40.7128, -74.0060)
        assertTrue("Expected NY bearing ~58.4, got $nyBearing", nyBearing in 57.0..60.0)

        // London -> ~119° ESE
        val londonBearing = QiblaCalculator.calculateBearing(51.5072, -0.1276)
        assertTrue("Expected London bearing ~119, got $londonBearing", londonBearing in 117.0..121.0)

        // Tokyo -> ~293° WNW
        val tokyoBearing = QiblaCalculator.calculateBearing(35.6762, 139.6503)
        assertTrue("Expected Tokyo bearing ~293, got $tokyoBearing", tokyoBearing in 291.0..295.0)

        // Jakarta -> ~295° WNW
        val jakartaBearing = QiblaCalculator.calculateBearing(-6.2088, 106.8456)
        assertTrue("Expected Jakarta bearing ~295, got $jakartaBearing", jakartaBearing in 293.0..297.0)
    }

    @Test
    fun distanceToMakkahIsReasonable() {
        val makkahDist = QiblaCalculator.distanceToMakkahKm(21.4225, 39.8262)
        assertTrue(makkahDist < 1.0)

        val nyDist = QiblaCalculator.distanceToMakkahKm(40.7128, -74.0060)
        assertTrue("Expected NY distance ~10,300 km, got $nyDist", nyDist in 10_000.0..10_600.0)
    }

    @Test
    fun prayerTimesAreChronologicalGlobally() {
        val sampleCities = listOf("Makkah", "London", "New York", "Jakarta", "Tokyo")
        for (cityName in sampleCities) {
            val city = GlobalCities.cities.first { it.name == cityName }
            val schedule = PrayerTimeCalculator.calculateDailySchedule(
                latitude = city.latitude,
                longitude = city.longitude,
                timeZoneId = city.timeZoneId,
                method = CalculationMethod.MWL,
                madhab = AsrMadhab.SHAFI
            )
            assertEquals(6, schedule.entries.size)
            for (i in 0 until schedule.entries.size - 1) {
                assertTrue(
                    "Prayer order invalid in $cityName: ${schedule.entries[i].name} >= ${schedule.entries[i + 1].name}",
                    schedule.entries[i].timeMillis < schedule.entries[i + 1].timeMillis
                )
            }
        }
    }

    @Test
    fun surahDirectoryContainsAll114Surahs() {
        assertEquals(114, SurahDirectory.surahs.size)
        SurahDirectory.surahs.forEachIndexed { index, surah ->
            assertEquals(index + 1, surah.number)
            assertTrue(surah.nameArabic.isNotBlank())
            assertTrue(surah.nameEnglish.isNotBlank())
            assertTrue(surah.ayahsCount > 0)
        }
    }
}
