package com.example.muslimapps.data

import androidx.room.*

@Entity(tableName = "surahs")
data class Surah(
    @PrimaryKey val id: Int,
    val name: String,
    val englishName: String,
    val ayatCount: Int,
    val revelationType: String
)

@Entity(tableName = "ayahs")
data class AyahEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val surahId: Int,
    val numberInSurah: Int,
    val text: String,
    val translation: String
)

@Dao
interface QuranDao {
    @Query("SELECT * FROM surahs")
    suspend fun getAllSurahs(): List<Surah>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSurahs(surahs: List<Surah>)

    @Query("SELECT * FROM ayahs WHERE surahId = :surahId ORDER BY numberInSurah ASC")
    suspend fun getAyahsForSurah(surahId: Int): List<AyahEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAyahs(ayahs: List<AyahEntity>)
}

@Database(entities = [Surah::class, AyahEntity::class], version = 2)
abstract class QuranDatabase : RoomDatabase() {
    abstract fun quranDao(): QuranDao
}
