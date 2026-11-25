package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.AsrMadhab
import com.example.model.CalculationMethod
import com.example.model.DhikrPreset
import com.example.model.GlobalCity
import com.example.model.ThemeMode
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.TimeZone

private val Context.noorDataStore: DataStore<Preferences> by preferencesDataStore(name = "noor_preferences")

@JsonClass(generateAdapter = true)
data class CustomDhikrDto(
    val id: String,
    val arabic: String,
    val transliteration: String,
    val meaning: String,
    val targetCount: Int
)

data class NoorPreferences(
    val themeMode: ThemeMode,
    val city: GlobalCity,
    val calculationMethod: CalculationMethod,
    val asrMadhab: AsrMadhab,
    val reciterId: String,
    val arabicFontSizeSp: Float,
    val showTranslation: Boolean,
    val lastReadSurah: Int,
    val lastReadAyah: Int,
    val bookmarks: Set<String>,
    val activeDhikrId: String,
    val tasbeehCount: Int,
    val tasbeehRounds: Int,
    val tasbeehLifetimeTotal: Long,
    val customDhikrList: List<DhikrPreset>,
    val hapticsEnabled: Boolean,
    val distanceInMiles: Boolean
)

class UserPreferencesRepository(private val context: Context) {

    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val customDhikrType = Types.newParameterizedType(List::class.java, CustomDhikrDto::class.java)
    private val customDhikrAdapter = moshi.adapter<List<CustomDhikrDto>>(customDhikrType)

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val CITY_NAME = stringPreferencesKey("city_name")
        val CITY_COUNTRY = stringPreferencesKey("city_country")
        val CITY_LAT = doublePreferencesKey("city_lat")
        val CITY_LON = doublePreferencesKey("city_lon")
        val CITY_TZ = stringPreferencesKey("city_tz")
        val CITY_IS_GPS = booleanPreferencesKey("city_is_gps")
        val CALC_METHOD = stringPreferencesKey("calc_method")
        val ASR_MADHAB = stringPreferencesKey("asr_madhab")
        val RECITER_ID = stringPreferencesKey("reciter_id")
        val ARABIC_FONT_SP = floatPreferencesKey("arabic_font_sp")
        val SHOW_TRANSLATION = booleanPreferencesKey("show_translation")
        val LAST_SURAH = intPreferencesKey("last_surah")
        val LAST_AYAH = intPreferencesKey("last_ayah")
        val BOOKMARKS = stringSetPreferencesKey("bookmarks")
        val ACTIVE_DHIKR_ID = stringPreferencesKey("active_dhikr_id")
        val TASBEEH_COUNT = intPreferencesKey("tasbeeh_count")
        val TASBEEH_ROUNDS = intPreferencesKey("tasbeeh_rounds")
        val TASBEEH_LIFETIME = longPreferencesKey("tasbeeh_lifetime")
        val CUSTOM_DHIKRS = stringPreferencesKey("custom_dhikrs")
        val HAPTICS_ENABLED = booleanPreferencesKey("haptics_enabled")
        val DISTANCE_MILES = booleanPreferencesKey("distance_miles")
    }

    val preferencesFlow: Flow<NoorPreferences> = context.noorDataStore.data.map { prefs ->
        val defaultCity = GlobalCities.defaultForTimeZone(TimeZone.getDefault().id)
        val city = GlobalCity(
            name = prefs[Keys.CITY_NAME] ?: defaultCity.name,
            country = prefs[Keys.CITY_COUNTRY] ?: defaultCity.country,
            latitude = prefs[Keys.CITY_LAT] ?: defaultCity.latitude,
            longitude = prefs[Keys.CITY_LON] ?: defaultCity.longitude,
            timeZoneId = prefs[Keys.CITY_TZ] ?: defaultCity.timeZoneId,
            isGpsDetected = prefs[Keys.CITY_IS_GPS] ?: false
        )

        val customList = try {
            val raw = prefs[Keys.CUSTOM_DHIKRS]
            if (raw.isNullOrBlank()) emptyList()
            else customDhikrAdapter.fromJson(raw)?.map {
                DhikrPreset(it.id, it.arabic, it.transliteration, it.meaning, it.targetCount, isCustom = true)
            } ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }

        NoorPreferences(
            themeMode = prefs[Keys.THEME_MODE]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
            city = city,
            calculationMethod = prefs[Keys.CALC_METHOD]?.let { runCatching { CalculationMethod.valueOf(it) }.getOrNull() } ?: CalculationMethod.MWL,
            asrMadhab = prefs[Keys.ASR_MADHAB]?.let { runCatching { AsrMadhab.valueOf(it) }.getOrNull() } ?: AsrMadhab.SHAFI,
            reciterId = prefs[Keys.RECITER_ID] ?: QuranReciters.list.first().id,
            arabicFontSizeSp = prefs[Keys.ARABIC_FONT_SP] ?: 26f,
            showTranslation = prefs[Keys.SHOW_TRANSLATION] ?: true,
            lastReadSurah = prefs[Keys.LAST_SURAH] ?: 1,
            lastReadAyah = prefs[Keys.LAST_AYAH] ?: 1,
            bookmarks = prefs[Keys.BOOKMARKS] ?: emptySet(),
            activeDhikrId = prefs[Keys.ACTIVE_DHIKR_ID] ?: DefaultDhikrPresets.list.first().id,
            tasbeehCount = prefs[Keys.TASBEEH_COUNT] ?: 0,
            tasbeehRounds = prefs[Keys.TASBEEH_ROUNDS] ?: 0,
            tasbeehLifetimeTotal = prefs[Keys.TASBEEH_LIFETIME] ?: 0L,
            customDhikrList = customList,
            hapticsEnabled = prefs[Keys.HAPTICS_ENABLED] ?: true,
            distanceInMiles = prefs[Keys.DISTANCE_MILES] ?: false
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.noorDataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setCity(city: GlobalCity) {
        context.noorDataStore.edit {
            it[Keys.CITY_NAME] = city.name
            it[Keys.CITY_COUNTRY] = city.country
            it[Keys.CITY_LAT] = city.latitude
            it[Keys.CITY_LON] = city.longitude
            it[Keys.CITY_TZ] = city.timeZoneId
            it[Keys.CITY_IS_GPS] = city.isGpsDetected
        }
    }

    suspend fun setCalculationMethod(method: CalculationMethod) {
        context.noorDataStore.edit { it[Keys.CALC_METHOD] = method.name }
    }

    suspend fun setAsrMadhab(madhab: AsrMadhab) {
        context.noorDataStore.edit { it[Keys.ASR_MADHAB] = madhab.name }
    }

    suspend fun setReciterId(reciterId: String) {
        context.noorDataStore.edit { it[Keys.RECITER_ID] = reciterId }
    }

    suspend fun setArabicFontSize(sizeSp: Float) {
        context.noorDataStore.edit { it[Keys.ARABIC_FONT_SP] = sizeSp.coerceIn(18f, 40f) }
    }

    suspend fun setShowTranslation(show: Boolean) {
        context.noorDataStore.edit { it[Keys.SHOW_TRANSLATION] = show }
    }

    suspend fun setLastRead(surah: Int, ayah: Int) {
        context.noorDataStore.edit {
            it[Keys.LAST_SURAH] = surah
            it[Keys.LAST_AYAH] = ayah
        }
    }

    suspend fun toggleBookmark(surah: Int, ayah: Int) {
        val key = "$surah:$ayah"
        context.noorDataStore.edit { prefs ->
            val current = prefs[Keys.BOOKMARKS]?.toMutableSet() ?: mutableSetOf()
            if (current.contains(key)) current.remove(key) else current.add(key)
            prefs[Keys.BOOKMARKS] = current
        }
    }

    suspend fun updateTasbeehProgress(count: Int, rounds: Int, lifetimeDelta: Long) {
        context.noorDataStore.edit { prefs ->
            prefs[Keys.TASBEEH_COUNT] = count
            prefs[Keys.TASBEEH_ROUNDS] = rounds
            val currentLifetime = prefs[Keys.TASBEEH_LIFETIME] ?: 0L
            prefs[Keys.TASBEEH_LIFETIME] = (currentLifetime + lifetimeDelta).coerceAtLeast(0L)
        }
    }

    suspend fun setActiveDhikr(id: String) {
        context.noorDataStore.edit {
            it[Keys.ACTIVE_DHIKR_ID] = id
            it[Keys.TASBEEH_COUNT] = 0
        }
    }

    suspend fun addCustomDhikr(preset: DhikrPreset) {
        context.noorDataStore.edit { prefs ->
            val existing = try {
                val raw = prefs[Keys.CUSTOM_DHIKRS]
                if (raw.isNullOrBlank()) mutableListOf()
                else customDhikrAdapter.fromJson(raw)?.toMutableList() ?: mutableListOf()
            } catch (_: Exception) {
                mutableListOf()
            }
            existing.add(
                CustomDhikrDto(
                    id = preset.id,
                    arabic = preset.arabic,
                    transliteration = preset.transliteration,
                    meaning = preset.meaning,
                    targetCount = preset.targetCount
                )
            )
            prefs[Keys.CUSTOM_DHIKRS] = customDhikrAdapter.toJson(existing)
            prefs[Keys.ACTIVE_DHIKR_ID] = preset.id
            prefs[Keys.TASBEEH_COUNT] = 0
        }
    }

    suspend fun setHapticsEnabled(enabled: Boolean) {
        context.noorDataStore.edit { it[Keys.HAPTICS_ENABLED] = enabled }
    }

    suspend fun setDistanceInMiles(miles: Boolean) {
        context.noorDataStore.edit { it[Keys.DISTANCE_MILES] = miles }
    }
}
