package com.example.muslimapps.data

import retrofit2.http.GET
import retrofit2.http.Query

interface PrayerApi {
    @GET("v1/timings")
    suspend fun getPrayerTimings(
        @Query("latitude") lat: Double,
        @Query("longitude") lng: Double,
        @Query("method") method: Int = 11
    ): PrayerResponse
}

data class PrayerResponse(
    val data: PrayerData
)

data class PrayerData(
    val timings: Timings,
    val meta: Meta
)

data class Timings(
    val Fajr: String,
    val Dhuhr: String,
    val Asr: String,
    val Maghrib: String,
    val Isha: String
)

data class Meta(
    val timezone: String
)
