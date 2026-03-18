package com.example.muslimapps.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.muslimapps.data.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class QuranViewModel @Inject constructor(
    private val api: QuranApi,
    private val db: QuranDatabase
) : ViewModel() {

    private val _surahs = MutableStateFlow<List<Surah>>(emptyList())
    val surahs: StateFlow<List<Surah>> = _surahs

    private val _ayahs = MutableStateFlow<List<AyahEntity>>(emptyList())
    val ayahs: StateFlow<List<AyahEntity>> = _ayahs

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    fun loadSurahs() {
        viewModelScope.launch {
            val localSurahs = db.quranDao().getAllSurahs()
            if (localSurahs.isNotEmpty()) {
                _surahs.value = localSurahs
            } else {
                // Mock or Fetch from API
                val mockSurahs = listOf(
                    Surah(1, "Al-Fatihah", "The Opening", 7, "Meccan"),
                    Surah(2, "Al-Baqarah", "The Cow", 286, "Medinan"),
                    Surah(3, "Ali 'Imran", "Family of Imran", 200, "Medinan"),
                    Surah(114, "An-Nas", "Mankind", 6, "Meccan")
                )
                db.quranDao().insertSurahs(mockSurahs)
                _surahs.value = mockSurahs
            }
        }
    }

    fun loadAyahs(surahId: Int) {
        viewModelScope.launch {
            _isLoading.value = true
            val localAyahs = db.quranDao().getAyahsForSurah(surahId)
            if (localAyahs.isNotEmpty()) {
                _ayahs.value = localAyahs
            } else {
                try {
                    val response = api.getSurahDetail(surahId)
                    val entities = response.data[0].ayahs.indices.map { i ->
                        AyahEntity(
                            surahId = surahId,
                            numberInSurah = response.data[0].ayahs[i].numberInSurah,
                            text = response.data[0].ayahs[i].text,
                            translation = response.data[1].ayahs[i].text
                        )
                    }
                    db.quranDao().insertAyahs(entities)
                    _ayahs.value = entities
                } catch (e: Exception) {
                    // Handle error
                }
            }
            _isLoading.value = false
        }
    }
}
