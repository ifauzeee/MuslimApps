package com.example.muslimapps.di

import android.content.Context
import androidx.room.Room
import com.example.muslimapps.data.PrayerApi
import com.example.muslimapps.data.QuranApi
import com.example.muslimapps.data.QuranDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideRetrofit(): Retrofit = Retrofit.Builder()
        .baseUrl("https://api.aladhan.com/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    @Provides
    @Singleton
    fun providePrayerApi(retrofit: Retrofit): PrayerApi = retrofit.create(PrayerApi::class.java)

    @Provides
    @Singleton
    fun provideQuranApi(): QuranApi = Retrofit.Builder()
        .baseUrl("https://api.alquran.cloud/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(QuranApi::class.java)

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): QuranDatabase =
        Room.databaseBuilder(context, QuranDatabase::class.java, "quran-db")
            .fallbackToDestructiveMigration()
            .build()
}
