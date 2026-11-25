package com.example.data

import android.content.Context
import com.example.model.Ayah
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import java.io.File
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class AlQuranEditionsResponse(
    val code: Int,
    val status: String,
    val data: List<EditionSurahDto>
)

@JsonClass(generateAdapter = true)
data class EditionSurahDto(
    val number: Int,
    val ayahs: List<AyahDto>
)

@JsonClass(generateAdapter = true)
data class AyahDto(
    val number: Int,
    val numberInSurah: Int,
    val text: String
)

@JsonClass(generateAdapter = true)
data class CachedAyah(
    val numberInSurah: Int,
    val globalNumber: Int,
    val arabicText: String,
    val englishText: String
)

interface AlQuranCloudApi {
    @GET("surah/{number}/editions/quran-uthmani,en.sahih")
    suspend fun getSurahWithTranslation(
        @Path("number") surahNumber: Int
    ): AlQuranEditionsResponse
}

class QuranRepository(context: Context) {
    private val cacheDir = File(context.filesDir, "quran_cache").apply { mkdirs() }

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val listType = Types.newParameterizedType(List::class.java, CachedAyah::class.java)
    private val cacheAdapter = moshi.adapter<List<CachedAyah>>(listType)

    private val api: AlQuranCloudApi by lazy {
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()

        Retrofit.Builder()
            .baseUrl("https://api.alquran.cloud/v1/")
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(AlQuranCloudApi::class.java)
    }

    suspend fun getSurahAyahs(surahNumber: Int): Result<List<Ayah>> = withContext(Dispatchers.IO) {
        val cacheFile = File(cacheDir, "surah_$surahNumber.json")
        if (cacheFile.exists()) {
            try {
                val cached = cacheAdapter.fromJson(cacheFile.readText())
                if (!cached.isNullOrEmpty()) {
                    return@withContext Result.success(
                        cached.map { Ayah(it.numberInSurah, it.globalNumber, it.arabicText, it.englishText) }
                    )
                }
            } catch (_: Exception) {
                // Ignore corrupted cache and refetch
            }
        }

        try {
            val response = api.getSurahWithTranslation(surahNumber)
            val arabicEdition = response.data.getOrNull(0)
            val englishEdition = response.data.getOrNull(1)
            if (arabicEdition != null && arabicEdition.ayahs.isNotEmpty()) {
                val merged = arabicEdition.ayahs.mapIndexed { idx, ar ->
                    val en = englishEdition?.ayahs?.getOrNull(idx)?.text ?: ""
                    Ayah(
                        numberInSurah = ar.numberInSurah,
                        globalNumber = ar.number,
                        arabicText = ar.text,
                        englishText = en
                    )
                }
                try {
                    val toCache = merged.map {
                        CachedAyah(it.numberInSurah, it.globalNumber, it.arabicText, it.englishText)
                    }
                    cacheFile.writeText(cacheAdapter.toJson(toCache))
                } catch (_: Exception) {
                }
                return@withContext Result.success(merged)
            }
        } catch (e: Exception) {
            val fallback = SurahDirectory.offlineFallback(surahNumber)
            if (fallback != null) {
                return@withContext Result.success(fallback)
            }
            return@withContext Result.failure(e)
        }

        val fallback = SurahDirectory.offlineFallback(surahNumber)
        if (fallback != null) {
            Result.success(fallback)
        } else {
            Result.failure(IllegalStateException("Unable to load Surah $surahNumber"))
        }
    }
}
