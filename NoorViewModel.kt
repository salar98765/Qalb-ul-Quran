package com.example.viewmodel

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.DefaultDhikrPresets
import com.example.data.GlobalCities
import com.example.data.NoorPreferences
import com.example.data.QuranReciters
import com.example.data.QuranRepository
import com.example.data.SurahDirectory
import com.example.data.UserPreferencesRepository
import com.example.domain.AudioPlaybackState
import com.example.domain.PrayerTimeCalculator
import com.example.domain.QiblaCalculator
import com.example.domain.QuranAudioPlayer
import com.example.model.AppTab
import com.example.model.AsrMadhab
import com.example.model.Ayah
import com.example.model.CalculationMethod
import com.example.model.DailyPrayerSchedule
import com.example.model.DhikrPreset
import com.example.model.GlobalCity
import com.example.model.Reciter
import com.example.model.SurahMeta
import com.example.model.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.TimeZone
import kotlin.coroutines.resume

sealed interface SurahDetailUiState {
    data object Idle : SurahDetailUiState
    data class Loading(val surah: SurahMeta) : SurahDetailUiState
    data class Success(val surah: SurahMeta, val ayahs: List<Ayah>) : SurahDetailUiState
    data class Error(val surah: SurahMeta, val message: String) : SurahDetailUiState
}

enum class SurahFilter(val label: String) {
    ALL("All (114)"),
    MECCAN("Meccan"),
    MEDINAN("Medinan"),
    BOOKMARKED("Bookmarked")
}

class NoorViewModel(application: Application) : AndroidViewModel(application) {

    private val prefsRepo = UserPreferencesRepository(application)
    private val quranRepo = QuranRepository(application)

    private val audioPlayer = QuranAudioPlayer(
        scope = viewModelScope,
        onAyahCompleted = { surahNum, completedAyah ->
            handleAyahCompleted(surahNum, completedAyah)
        }
    )

    private val _nowMillis = MutableStateFlow(System.currentTimeMillis())
    private val _activeTab = MutableStateFlow(AppTab.PRAYER)
    val activeTab: StateFlow<AppTab> = _activeTab.asStateFlow()

    private val _surahSearchQuery = MutableStateFlow("")
    val surahSearchQuery: StateFlow<String> = _surahSearchQuery.asStateFlow()

    private val _surahFilter = MutableStateFlow(SurahFilter.ALL)
    val surahFilter: StateFlow<SurahFilter> = _surahFilter.asStateFlow()

    private val _selectedSurahState = MutableStateFlow<SurahDetailUiState>(SurahDetailUiState.Idle)
    val selectedSurahState: StateFlow<SurahDetailUiState> = _selectedSurahState.asStateFlow()

    private val _isDetectingLocation = MutableStateFlow(false)
    val isDetectingLocation: StateFlow<Boolean> = _isDetectingLocation.asStateFlow()

    private val _locationFeedback = MutableStateFlow<String?>(null)
    val locationFeedback: StateFlow<String?> = _locationFeedback.asStateFlow()

    val audioState: StateFlow<AudioPlaybackState> = audioPlayer.state

    val preferences: StateFlow<NoorPreferences> = prefsRepo.preferencesFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = NoorPreferences(
            themeMode = ThemeMode.SYSTEM,
            city = GlobalCities.defaultForTimeZone(TimeZone.getDefault().id),
            calculationMethod = CalculationMethod.MWL,
            asrMadhab = AsrMadhab.SHAFI,
            reciterId = QuranReciters.list.first().id,
            arabicFontSizeSp = 26f,
            showTranslation = true,
            lastReadSurah = 1,
            lastReadAyah = 1,
            bookmarks = emptySet(),
            activeDhikrId = DefaultDhikrPresets.list.first().id,
            tasbeehCount = 0,
            tasbeehRounds = 0,
            tasbeehLifetimeTotal = 0L,
            customDhikrList = emptyList(),
            hapticsEnabled = true,
            distanceInMiles = false
        )
    )

    val prayerSchedule: StateFlow<DailyPrayerSchedule> = combine(
        preferences,
        _nowMillis
    ) { prefs, now ->
        PrayerTimeCalculator.calculateDailySchedule(
            latitude = prefs.city.latitude,
            longitude = prefs.city.longitude,
            timeZoneId = prefs.city.timeZoneId,
            method = prefs.calculationMethod,
            madhab = prefs.asrMadhab,
            nowMillis = now
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = PrayerTimeCalculator.calculateDailySchedule(
            latitude = preferences.value.city.latitude,
            longitude = preferences.value.city.longitude,
            timeZoneId = preferences.value.city.timeZoneId,
            method = preferences.value.calculationMethod,
            madhab = preferences.value.asrMadhab
        )
    )

    val countdownText: StateFlow<String> = combine(
        prayerSchedule,
        _nowMillis
    ) { schedule, now ->
        val target = schedule.nextPrayer?.timeMillis ?: return@combine "--:--:--"
        val diffSec = ((target - now) / 1000L).coerceAtLeast(0L)
        val hours = diffSec / 3600L
        val mins = (diffSec % 3600L) / 60L
        val secs = diffSec % 60L
        String.format(Locale.US, "%02d:%02d:%02d", hours, mins, secs)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = "00:00:00"
    )

    val filteredSurahs: StateFlow<List<SurahMeta>> = combine(
        _surahSearchQuery,
        _surahFilter,
        preferences
    ) { query, filter, prefs ->
        val q = query.trim().lowercase(Locale.US)
        val bookmarkedSurahs = prefs.bookmarks.mapNotNull { it.substringBefore(":").toIntOrNull() }.toSet()
        SurahDirectory.surahs.filter { surah ->
            val matchesFilter = when (filter) {
                SurahFilter.ALL -> true
                SurahFilter.MECCAN -> surah.revelationType.equals("Meccan", ignoreCase = true)
                SurahFilter.MEDINAN -> surah.revelationType.equals("Medinan", ignoreCase = true)
                SurahFilter.BOOKMARKED -> surah.number in bookmarkedSurahs
            }
            val matchesQuery = q.isEmpty() ||
                surah.nameEnglish.lowercase(Locale.US).contains(q) ||
                surah.meaning.lowercase(Locale.US).contains(q) ||
                surah.nameArabic.contains(q) ||
                surah.number.toString() == q
            matchesFilter && matchesQuery
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = SurahDirectory.surahs
    )

    init {
        viewModelScope.launch {
            while (true) {
                _nowMillis.value = System.currentTimeMillis()
                delay(1000L)
            }
        }
    }

    fun selectTab(tab: AppTab) {
        _activeTab.value = tab
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { prefsRepo.setThemeMode(mode) }
    }

    fun cycleLightDarkTheme() {
        val current = preferences.value.themeMode
        val next = when (current) {
            ThemeMode.DARK -> ThemeMode.LIGHT
            ThemeMode.LIGHT -> ThemeMode.DARK
            ThemeMode.SYSTEM -> ThemeMode.DARK
        }
        setThemeMode(next)
    }

    fun selectCity(city: GlobalCity) {
        viewModelScope.launch {
            prefsRepo.setCity(city)
            _locationFeedback.value = null
        }
    }

    fun setCalculationMethod(method: CalculationMethod) {
        viewModelScope.launch { prefsRepo.setCalculationMethod(method) }
    }

    fun setAsrMadhab(madhab: AsrMadhab) {
        viewModelScope.launch { prefsRepo.setAsrMadhab(madhab) }
    }

    fun setReciter(reciter: Reciter) {
        viewModelScope.launch { prefsRepo.setReciterId(reciter.id) }
    }

    fun setArabicFontSize(sizeSp: Float) {
        viewModelScope.launch { prefsRepo.setArabicFontSize(sizeSp) }
    }

    fun setShowTranslation(show: Boolean) {
        viewModelScope.launch { prefsRepo.setShowTranslation(show) }
    }

    fun setHapticsEnabled(enabled: Boolean) {
        viewModelScope.launch { prefsRepo.setHapticsEnabled(enabled) }
    }

    fun setDistanceInMiles(miles: Boolean) {
        viewModelScope.launch { prefsRepo.setDistanceInMiles(miles) }
    }

    fun updateSurahSearch(query: String) {
        _surahSearchQuery.value = query
    }

    fun updateSurahFilter(filter: SurahFilter) {
        _surahFilter.value = filter
    }

    fun openSurah(surahNumber: Int) {
        val meta = SurahDirectory.surahs.firstOrNull { it.number == surahNumber } ?: return
        _selectedSurahState.value = SurahDetailUiState.Loading(meta)
        viewModelScope.launch {
            prefsRepo.setLastRead(surahNumber, 1)
            val result = quranRepo.getSurahAyahs(surahNumber)
            _selectedSurahState.value = result.fold(
                onSuccess = { ayahs -> SurahDetailUiState.Success(meta, ayahs) },
                onFailure = { err ->
                    SurahDetailUiState.Error(
                        meta,
                        err.localizedMessage ?: "Unable to fetch Surah text. Please check internet connection."
                    )
                }
            )
        }
    }

    fun closeSurahDetail() {
        _selectedSurahState.value = SurahDetailUiState.Idle
    }

    fun playAyah(surahNumber: Int, ayah: Ayah) {
        val reciter = QuranReciters.list.firstOrNull { it.id == preferences.value.reciterId }
            ?: QuranReciters.list.first()
        viewModelScope.launch { prefsRepo.setLastRead(surahNumber, ayah.numberInSurah) }
        audioPlayer.playUrl(
            url = reciter.ayahAudioUrl(ayah.globalNumber),
            surahNumber = surahNumber,
            ayahNumberInSurah = ayah.numberInSurah,
            globalAyahNumber = ayah.globalNumber
        )
    }

    fun playFullSurah(surah: SurahMeta) {
        val state = _selectedSurahState.value
        if (state is SurahDetailUiState.Success && state.surah.number == surah.number && state.ayahs.isNotEmpty()) {
            playAyah(surah.number, state.ayahs.first())
        } else {
            val reciter = QuranReciters.list.firstOrNull { it.id == preferences.value.reciterId }
                ?: QuranReciters.list.first()
            audioPlayer.playUrl(
                url = reciter.surahAudioUrl(surah.number),
                surahNumber = surah.number,
                ayahNumberInSurah = null,
                globalAyahNumber = null
            )
        }
    }

    fun toggleAudioPlayPause() {
        audioPlayer.togglePlayPause()
    }

    fun stopAudio() {
        audioPlayer.stop()
    }

    fun skipAyah(delta: Int) {
        val current = audioState.value
        val detail = _selectedSurahState.value as? SurahDetailUiState.Success ?: return
        if (current.surahNumber != detail.surah.number) return
        val currentAyahNum = current.ayahNumberInSurah ?: return
        val targetIdx = detail.ayahs.indexOfFirst { it.numberInSurah == currentAyahNum } + delta
        if (targetIdx in detail.ayahs.indices) {
            playAyah(detail.surah.number, detail.ayahs[targetIdx])
        }
    }

    private fun handleAyahCompleted(surahNumber: Int, completedAyahInSurah: Int) {
        val detail = _selectedSurahState.value as? SurahDetailUiState.Success ?: return
        if (detail.surah.number != surahNumber) return
        val nextAyah = detail.ayahs.firstOrNull { it.numberInSurah == completedAyahInSurah + 1 }
        if (nextAyah != null) {
            playAyah(surahNumber, nextAyah)
        }
    }

    fun toggleBookmark(surahNumber: Int, ayahNumber: Int) {
        viewModelScope.launch {
            prefsRepo.toggleBookmark(surahNumber, ayahNumber)
        }
    }

    fun allDhikrPresets(): List<DhikrPreset> =
        DefaultDhikrPresets.list + preferences.value.customDhikrList

    fun activeDhikrPreset(): DhikrPreset {
        val all = allDhikrPresets()
        return all.firstOrNull { it.id == preferences.value.activeDhikrId } ?: all.first()
    }

    fun incrementTasbeeh(): Boolean {
        val prefs = preferences.value
        val preset = activeDhikrPreset()
        val nextCount = prefs.tasbeehCount + 1
        val completedRound = nextCount >= preset.targetCount
        val finalCount = if (completedRound) 0 else nextCount
        val finalRounds = if (completedRound) prefs.tasbeehRounds + 1 else prefs.tasbeehRounds
        viewModelScope.launch {
            prefsRepo.updateTasbeehProgress(
                count = finalCount,
                rounds = finalRounds,
                lifetimeDelta = 1L
            )
        }
        return completedRound
    }

    fun resetCurrentTasbeeh() {
        viewModelScope.launch {
            prefsRepo.updateTasbeehProgress(
                count = 0,
                rounds = preferences.value.tasbeehRounds,
                lifetimeDelta = 0L
            )
        }
    }

    fun selectDhikrPreset(preset: DhikrPreset) {
        viewModelScope.launch {
            prefsRepo.setActiveDhikr(preset.id)
        }
    }

    fun addCustomDhikr(arabic: String, transliteration: String, meaning: String, target: Int) {
        val cleanTranslit = transliteration.trim().ifEmpty { "Custom Dhikr" }
        val cleanArabic = arabic.trim().ifEmpty { cleanTranslit }
        val cleanMeaning = meaning.trim().ifEmpty { "Personal remembrance" }
        val preset = DhikrPreset(
            id = "custom_${System.currentTimeMillis()}",
            arabic = cleanArabic,
            transliteration = cleanTranslit,
            meaning = cleanMeaning,
            targetCount = target.coerceIn(1, 9999),
            isCustom = true
        )
        viewModelScope.launch {
            prefsRepo.addCustomDhikr(preset)
        }
    }

    fun clearLocationFeedback() {
        _locationFeedback.value = null
    }

    @SuppressLint("MissingPermission")
    fun detectGpsLocation() {
        if (_isDetectingLocation.value) return
        _isDetectingLocation.value = true
        _locationFeedback.value = null

        viewModelScope.launch {
            val ctx = getApplication<Application>()
            val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            if (lm == null) {
                _isDetectingLocation.value = false
                _locationFeedback.value = "Location service unavailable on this device."
                return@launch
            }

            val location: Location? = withContext(Dispatchers.Main) {
                val lastBest = listOf(
                    LocationManager.GPS_PROVIDER,
                    LocationManager.NETWORK_PROVIDER,
                    LocationManager.PASSIVE_PROVIDER
                ).mapNotNull { provider ->
                    runCatching {
                        if (lm.isProviderEnabled(provider)) lm.getLastKnownLocation(provider) else null
                    }.getOrNull()
                }.maxByOrNull { it.time }

                if (lastBest != null && System.currentTimeMillis() - lastBest.time < 15 * 60_000L) {
                    lastBest
                } else {
                    withTimeoutOrNull(8000L) {
                        suspendCancellableCoroutine { cont ->
                            val provider = when {
                                lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
                                lm.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
                                else -> null
                            }
                            if (provider == null) {
                                cont.resume(lastBest)
                                return@suspendCancellableCoroutine
                            }
                            val listener = object : LocationListener {
                                override fun onLocationChanged(loc: Location) {
                                    lm.removeUpdates(this)
                                    if (cont.isActive) cont.resume(loc)
                                }
                            }
                            try {
                                lm.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper())
                            } catch (_: Exception) {
                                if (cont.isActive) cont.resume(lastBest)
                            }
                            cont.invokeOnCancellation {
                                runCatching { lm.removeUpdates(listener) }
                            }
                        }
                    } ?: lastBest
                }
            }

            if (location == null) {
                _isDetectingLocation.value = false
                _locationFeedback.value = "Could not acquire GPS fix. Select a global city manually."
                return@launch
            }

            val resolvedCity = resolveCityFromCoordinates(ctx, location.latitude, location.longitude)
            prefsRepo.setCity(resolvedCity)
            _isDetectingLocation.value = false
            _locationFeedback.value = "Location updated to ${resolvedCity.name}, ${resolvedCity.country}"
        }
    }

    @Suppress("DEPRECATION")
    private suspend fun resolveCityFromCoordinates(
        context: Context,
        lat: Double,
        lon: Double
    ): GlobalCity = withContext(Dispatchers.IO) {
        val tzId = TimeZone.getDefault().id
        try {
            if (Geocoder.isPresent()) {
                val geocoder = Geocoder(context, Locale.getDefault())
                val addresses = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    suspendCancellableCoroutine { cont ->
                        geocoder.getFromLocation(lat, lon, 1) { list ->
                            if (cont.isActive) cont.resume(list)
                        }
                    }
                } else {
                    geocoder.getFromLocation(lat, lon, 1)
                }
                val addr = addresses?.firstOrNull()
                if (addr != null) {
                    val cityName = addr.locality ?: addr.subAdminArea ?: addr.adminArea ?: "Current Location"
                    val countryName = addr.countryName ?: String.format(Locale.US, "%.2f°, %.2f°", lat, lon)
                    return@withContext GlobalCity(
                        name = cityName,
                        country = countryName,
                        latitude = lat,
                        longitude = lon,
                        timeZoneId = tzId,
                        isGpsDetected = true
                    )
                }
            }
        } catch (_: Exception) {
        }

        GlobalCity(
            name = "Current GPS",
            country = String.format(Locale.US, "%.2f°, %.2f°", lat, lon),
            latitude = lat,
            longitude = lon,
            timeZoneId = tzId,
            isGpsDetected = true
        )
    }

    fun currentQiblaBearing(): Double {
        val city = preferences.value.city
        return QiblaCalculator.calculateBearing(city.latitude, city.longitude)
    }

    fun currentDistanceToMakkah(): Double {
        val city = preferences.value.city
        val km = QiblaCalculator.distanceToMakkahKm(city.latitude, city.longitude)
        return if (preferences.value.distanceInMiles) km * 0.621371 else km
    }

    override fun onCleared() {
        super.onCleared()
        audioPlayer.release()
    }
}
