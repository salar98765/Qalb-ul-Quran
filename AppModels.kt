package com.example.model

enum class ThemeMode(val label: String) {
    LIGHT("White"),
    DARK("Dark"),
    SYSTEM("System")
}

enum class AppTab(val label: String) {
    PRAYER("Prayers"),
    QURAN("Quran"),
    QIBLA("Qibla"),
    TASBEEH("Tasbeeh"),
    PROFILE("Profile")
}

data class GlobalCity(
    val name: String,
    val country: String,
    val latitude: Double,
    val longitude: Double,
    val timeZoneId: String,
    val isGpsDetected: Boolean = false
)

enum class CalculationMethod(
    val displayName: String,
    val region: String,
    val fajrAngle: Double,
    val ishaAngle: Double? = null,
    val ishaMinutesAfterMaghrib: Int? = null,
    val maghribMinutesAfterSunset: Int = 0
) {
    MWL("Muslim World League", "Global / Europe / Americas", 18.0, ishaAngle = 17.0),
    ISNA("ISNA", "North America", 15.0, ishaAngle = 15.0),
    UMM_AL_QURA("Umm al-Qura", "Saudi Arabia / Makkah", 18.5, ishaMinutesAfterMaghrib = 90),
    EGYPT("Egyptian General Authority", "Africa / Middle East", 19.5, ishaAngle = 17.5),
    KARACHI("Univ. of Islamic Sciences", "Pakistan / India / Bangladesh", 18.0, ishaAngle = 18.0),
    SINGAPORE("MUIS / JAKIM", "Southeast Asia", 20.0, ishaAngle = 18.0),
    DIYANET("Diyanet İşleri", "Turkey / Balkans", 18.0, ishaAngle = 17.0)
}

enum class AsrMadhab(val displayName: String, val shadowFactor: Double) {
    SHAFI("Standard (Shafi'i, Maliki, Hanbali)", 1.0),
    HANAFI("Hanafi (Later Asr)", 2.0)
}

enum class PrayerName(val displayName: String, val arabicName: String) {
    FAJR("Fajr", "الفجر"),
    SUNRISE("Sunrise", "الشروق"),
    DHUHR("Dhuhr", "الظهر"),
    ASR("Asr", "العصر"),
    MAGHRIB("Maghrib", "المغرب"),
    ISHA("Isha", "العشاء")
}

data class PrayerTimeEntry(
    val name: PrayerName,
    val timeMillis: Long,
    val formattedTime: String,
    val isNext: Boolean = false,
    val isPassed: Boolean = false
)

data class DailyPrayerSchedule(
    val entries: List<PrayerTimeEntry>,
    val nextPrayer: PrayerTimeEntry?,
    val hijriDate: String,
    val gregorianDate: String
)

data class SurahMeta(
    val number: Int,
    val nameArabic: String,
    val nameEnglish: String,
    val meaning: String,
    val revelationType: String,
    val ayahsCount: Int
)

data class Ayah(
    val numberInSurah: Int,
    val globalNumber: Int,
    val arabicText: String,
    val englishText: String
)

data class Reciter(
    val id: String,
    val name: String,
    val style: String
) {
    fun ayahAudioUrl(globalAyahNumber: Int): String =
        "https://cdn.islamic.network/quran/audio/128/$id/$globalAyahNumber.mp3"

    fun surahAudioUrl(surahNumber: Int): String =
        "https://cdn.islamic.network/quran/audio-surah/128/$id/$surahNumber.mp3"
}

data class DhikrPreset(
    val id: String,
    val arabic: String,
    val transliteration: String,
    val meaning: String,
    val targetCount: Int,
    val isCustom: Boolean = false
)
