package com.example.domain

import android.icu.text.SimpleDateFormat as IcuSimpleDateFormat
import android.icu.util.IslamicCalendar
import android.icu.util.TimeZone as IcuTimeZone
import android.icu.util.ULocale
import com.example.model.AsrMadhab
import com.example.model.CalculationMethod
import com.example.model.DailyPrayerSchedule
import com.example.model.PrayerName
import com.example.model.PrayerTimeEntry
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.tan

object PrayerTimeCalculator {

    fun calculateDailySchedule(
        latitude: Double,
        longitude: Double,
        timeZoneId: String,
        method: CalculationMethod,
        madhab: AsrMadhab,
        nowMillis: Long = System.currentTimeMillis()
    ): DailyPrayerSchedule {
        val tz = TimeZone.getTimeZone(timeZoneId)
        val todayCal = Calendar.getInstance(tz).apply { timeInMillis = nowMillis }
        val todayTimes = computeRawPrayerTimes(todayCal, latitude, longitude, tz, method, madhab)

        val timeFmt = SimpleDateFormat("h:mm a", Locale.getDefault()).apply { timeZone = tz }
        val gregorianFmt = SimpleDateFormat("EEEE, d MMM yyyy", Locale.getDefault()).apply { timeZone = tz }

        var nextIndex = todayTimes.indexOfFirst { it.second > nowMillis }

        val entries = todayTimes.mapIndexed { idx, (name, millis) ->
            PrayerTimeEntry(
                name = name,
                timeMillis = millis,
                formattedTime = timeFmt.format(Date(millis)),
                isNext = idx == nextIndex,
                isPassed = millis <= nowMillis
            )
        }

        val nextPrayer = if (nextIndex != -1) {
            entries[nextIndex]
        } else {
            val tomorrowCal = (todayCal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }
            val tomorrowFajrMillis = computeRawPrayerTimes(tomorrowCal, latitude, longitude, tz, method, madhab).first().second
            PrayerTimeEntry(
                name = PrayerName.FAJR,
                timeMillis = tomorrowFajrMillis,
                formattedTime = timeFmt.format(Date(tomorrowFajrMillis)),
                isNext = true,
                isPassed = false
            )
        }

        val hijriDate = formatHijriDate(nowMillis, timeZoneId)
        val gregorianDate = gregorianFmt.format(Date(nowMillis))

        return DailyPrayerSchedule(
            entries = entries,
            nextPrayer = nextPrayer,
            hijriDate = hijriDate,
            gregorianDate = gregorianDate
        )
    }

    private fun formatHijriDate(nowMillis: Long, timeZoneId: String): String {
        return try {
            val icuTz = IcuTimeZone.getTimeZone(timeZoneId)
            val hijriCal = IslamicCalendar(icuTz, ULocale("en@calendar=islamic-umalqura")).apply {
                calculationType = IslamicCalendar.CalculationType.ISLAMIC_UMALQURA
                timeInMillis = nowMillis
            }
            val fmt = IcuSimpleDateFormat("d MMMM yyyy 'AH'", ULocale("en@calendar=islamic-umalqura"))
            fmt.calendar = hijriCal
            fmt.format(Date(nowMillis))
        } catch (_: Exception) {
            ""
        }
    }

    private fun computeRawPrayerTimes(
        cal: Calendar,
        lat: Double,
        lon: Double,
        tz: TimeZone,
        method: CalculationMethod,
        madhab: AsrMadhab
    ): List<Pair<PrayerName, Long>> {
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH) + 1
        val day = cal.get(Calendar.DAY_OF_MONTH)

        val jd = julianDay(year, month, day) - lon / (15.0 * 24.0)
        val tzOffsetHours = tz.getOffset(cal.timeInMillis) / 3_600_000.0

        val (declination, eqTime) = sunPosition(jd + 0.5)
        val transit = 12.0 + tzOffsetHours - (lon / 15.0) - eqTime

        val sunriseAngle = 0.833
        val sunriseOffset = hourAngle(lat, declination, sunriseAngle) ?: 6.0
        val sunriseHours = transit - sunriseOffset
        val sunsetHours = transit + sunriseOffset

        val nightDuration = 24.0 - (sunsetHours - sunriseHours)

        val fajrOffset = hourAngle(lat, declination, method.fajrAngle)
        var fajrHours = if (fajrOffset != null) {
            transit - fajrOffset
        } else {
            sunriseHours - (method.fajrAngle / 60.0) * nightDuration
        }
        // High-latitude angle-based safety clamp
        val maxFajrPortion = (method.fajrAngle / 60.0) * nightDuration
        if (sunriseHours - fajrHours > maxFajrPortion) {
            fajrHours = sunriseHours - maxFajrPortion
        }

        val dhuhrHours = transit + 1.0 / 60.0 // 1 min after solar transit

        val asrAngle = -radToDeg(atan(1.0 / (madhab.shadowFactor + tan(degToRad(kotlin.math.abs(lat - declination))))))
        val asrOffset = hourAngle(lat, declination, asrAngle) ?: 3.5
        val asrHours = transit + asrOffset

        val maghribHours = sunsetHours + (method.maghribMinutesAfterSunset / 60.0)

        var ishaHours = when {
            method.ishaMinutesAfterMaghrib != null -> maghribHours + method.ishaMinutesAfterMaghrib / 60.0
            method.ishaAngle != null -> {
                val offset = hourAngle(lat, declination, method.ishaAngle)
                if (offset != null) {
                    transit + offset
                } else {
                    sunsetHours + (method.ishaAngle / 60.0) * nightDuration
                }
            }
            else -> maghribHours + 1.5
        }
        if (method.ishaAngle != null) {
            val maxIshaPortion = (method.ishaAngle / 60.0) * nightDuration
            if (ishaHours - sunsetHours > maxIshaPortion) {
                ishaHours = sunsetHours + maxIshaPortion
            }
        }

        return listOf(
            PrayerName.FAJR to hoursToMillis(cal, fajrHours, tz),
            PrayerName.SUNRISE to hoursToMillis(cal, sunriseHours, tz),
            PrayerName.DHUHR to hoursToMillis(cal, dhuhrHours, tz),
            PrayerName.ASR to hoursToMillis(cal, asrHours, tz),
            PrayerName.MAGHRIB to hoursToMillis(cal, maghribHours, tz),
            PrayerName.ISHA to hoursToMillis(cal, ishaHours, tz)
        )
    }

    private fun julianDay(year: Int, month: Int, day: Int): Double {
        var y = year
        var m = month
        if (m <= 2) {
            y -= 1
            m += 12
        }
        val a = floor(y / 100.0)
        val b = 2.0 - a + floor(a / 4.0)
        return floor(365.25 * (y + 4716)) + floor(30.6001 * (m + 1)) + day + b - 1524.5
    }

    private fun sunPosition(jd: Double): Pair<Double, Double> {
        val d = jd - 2451545.0
        val g = fixAngle(357.529 + 0.98560028 * d)
        val q = fixAngle(280.459 + 0.98564736 * d)
        val l = fixAngle(q + 1.915 * sin(degToRad(g)) + 0.020 * sin(degToRad(2 * g)))

        val e = 23.439 - 0.00000036 * d
        val ra = radToDeg(atan2(cos(degToRad(e)) * sin(degToRad(l)), cos(degToRad(l)))) / 15.0
        val eqTime = q / 15.0 - fixHour(ra)
        val decl = radToDeg(asin(sin(degToRad(e)) * sin(degToRad(l))))
        return decl to eqTime
    }

    private fun hourAngle(lat: Double, decl: Double, angle: Double): Double? {
        val num = -sin(degToRad(angle)) - sin(degToRad(lat)) * sin(degToRad(decl))
        val den = cos(degToRad(lat)) * cos(degToRad(decl))
        if (kotlin.math.abs(den) < 1e-9) return null
        val cosVal = num / den
        if (cosVal < -1.0 || cosVal > 1.0) return null
        return radToDeg(acos(cosVal)) / 15.0
    }

    private fun hoursToMillis(baseCal: Calendar, hours: Double, tz: TimeZone): Long {
        val normalized = ((hours % 24.0) + 24.0) % 24.0
        val h = normalized.toInt()
        val totalMinutes = (normalized - h) * 60.0
        val m = totalMinutes.toInt()
        val s = ((totalMinutes - m) * 60.0).toInt().coerceIn(0, 59)

        return (baseCal.clone() as Calendar).apply {
            timeZone = tz
            set(Calendar.HOUR_OF_DAY, h)
            set(Calendar.MINUTE, m)
            set(Calendar.SECOND, s)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private fun degToRad(d: Double) = d * PI / 180.0
    private fun radToDeg(r: Double) = r * 180.0 / PI
    private fun fixAngle(a: Double): Double {
        val r = a - 360.0 * floor(a / 360.0)
        return if (r < 0) r + 360.0 else r
    }
    private fun fixHour(h: Double): Double {
        val r = h - 24.0 * floor(h / 24.0)
        return if (r < 0) r + 24.0 else r
    }
}
