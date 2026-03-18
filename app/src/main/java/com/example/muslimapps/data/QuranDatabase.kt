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

@Dao
interface QuranDao {
    @Query("SELECT * FROM surahs")
    suspend fun getAllSurahs(): List<Surah>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSurahs(surahs: List<Surah>)
}

@Database(entities = [Surah::class], version = 1)
abstract class QuranDatabase : RoomDatabase() {
    abstract fun quranDao(): QuranDao
}
