package com.example.muslimapps.data

import retrofit2.http.GET
import retrofit2.http.Path

interface QuranApi {
    @GET("v1/surah/{number}/editions/quran-uthmani,id.indonesian")
    suspend fun getSurahDetail(@Path("number") number: Int): SurahDetailResponse
}

data class SurahDetailResponse(
    val data: List<SurahEditionData>
)

data class SurahEditionData(
    val number: Int,
    val name: String,
    val englishName: String,
    val ayahs: List<Ayah>
)

data class Ayah(
    val number: Int,
    val text: String,
    val numberInSurah: Int,
    val juz: Int
)
