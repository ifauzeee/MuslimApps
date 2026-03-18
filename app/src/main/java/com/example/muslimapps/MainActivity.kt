package com.example.muslimapps

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.room.Room
import com.airbnb.lottie.compose.*
import com.example.muslimapps.data.*
import com.example.muslimapps.ui.theme.*
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.text.NumberFormat
import java.util.*

class MainActivity : ComponentActivity() {
    private lateinit var db: QuranDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = Room.databaseBuilder(applicationContext, QuranDatabase::class.java, "quran-db")
            .fallbackToDestructiveMigration().build()
        
        val sharedPref = getSharedPreferences("MuslimAppsSettings", Context.MODE_PRIVATE)
        
        setContent {
            var isDarkMode by remember { mutableStateOf(sharedPref.getBoolean("isDarkMode", false)) }
            MuslimAppsTheme(darkTheme = isDarkMode) {
                MainNavigation(
                    db = db,
                    isDarkMode = isDarkMode,
                    onThemeToggle = { 
                        isDarkMode = it
                        sharedPref.edit().putBoolean("isDarkMode", it).apply()
                    }
                )
            }
        }
    }
}

@Composable
fun MainNavigation(db: QuranDatabase, isDarkMode: Boolean, onThemeToggle: (Boolean) -> Unit) {
    var currentScreen by remember { mutableStateOf("dashboard") }
    var prayerTimesData by remember { mutableStateOf<Timings?>(null) }
    var selectedSurahId by remember { mutableStateOf<Int?>(null) }
    var selectedSurahName by remember { mutableStateOf("") }
    
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        AnimatedContent(targetState = currentScreen, label = "") { screen ->
            when (screen) {
                "dashboard" -> DashboardScreen(
                    isDarkMode = isDarkMode, onThemeToggle = onThemeToggle,
                    onNavigateToQuran = { currentScreen = "quran" },
                    onNavigateToKiblat = { currentScreen = "kiblat" },
                    onNavigateToTasbih = { currentScreen = "tasbih" },
                    onNavigateToDoa = { currentScreen = "doa" },
                    onNavigateToSchedule = { currentScreen = "schedule" },
                    onNavigateToMosque = { currentScreen = "mosque" },
                    onNavigateToZakat = { currentScreen = "zakat" },
                    onNavigateToAsmaul = { currentScreen = "asmaul" },
                    onNavigateToCalendar = { currentScreen = "calendar" },
                    onDataLoaded = { prayerTimesData = it }
                )
                "quran" -> QuranScreen(db = db, onBack = { currentScreen = "dashboard" },
                    onSurahClick = { id, name -> selectedSurahId = id; selectedSurahName = name; currentScreen = "quran_detail" })
                "quran_detail" -> SurahDetailScreen(db = db, surahId = selectedSurahId ?: 1, surahName = selectedSurahName, onBack = { currentScreen = "quran" })
                "kiblat" -> KiblatScreen(onBack = { currentScreen = "dashboard" })
                "tasbih" -> TasbihScreen(onBack = { currentScreen = "dashboard" })
                "doa" -> DoaScreen(onBack = { currentScreen = "dashboard" })
                "schedule" -> PrayerScheduleScreen(prayerTimesData, onBack = { currentScreen = "dashboard" })
                "mosque" -> MosqueMapScreen(onBack = { currentScreen = "dashboard" })
                "zakat" -> ZakatCalculatorScreen(onBack = { currentScreen = "dashboard" })
                "asmaul" -> AsmaulHusnaScreen(onBack = { currentScreen = "dashboard" })
                "calendar" -> HijriCalendarScreen(onBack = { currentScreen = "dashboard" })
            }
        }
    }
}

@Composable
fun DashboardScreen(
    isDarkMode: Boolean, onThemeToggle: (Boolean) -> Unit,
    onNavigateToQuran: () -> Unit, onNavigateToKiblat: () -> Unit,
    onNavigateToTasbih: () -> Unit, onNavigateToDoa: () -> Unit,
    onNavigateToSchedule: () -> Unit, onNavigateToMosque: () -> Unit,
    onNavigateToZakat: () -> Unit, onNavigateToAsmaul: () -> Unit,
    onNavigateToCalendar: () -> Unit, onDataLoaded: (Timings) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var prayerTimes by remember { mutableStateOf<Timings?>(null) }
    var locationName by remember { mutableStateOf("Mencari Lokasi...") }
    var hadith by remember { mutableStateOf("Sampaikanlah dariku walau hanya satu ayat. (HR. Bukhari)") }

    LaunchedEffect(Unit) {
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                location?.let {
                    scope.launch {
                        try {
                            val retrofit = Retrofit.Builder().baseUrl("https://api.aladhan.com/").addConverterFactory(GsonConverterFactory.create()).build()
                            val api = retrofit.create(PrayerApi::class.java)
                            val response = api.getPrayerTimings(it.latitude, it.longitude)
                            prayerTimes = response.data.timings
                            onDataLoaded(response.data.timings)
                            locationName = response.data.meta.timezone
                        } catch (e: Exception) { }
                    }
                }
            }
        }
    }

    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { HeaderSection(isDarkMode, onThemeToggle) }
        item { HeroPrayerCard(prayerTimes, locationName) }
        item { QuickActionsSection(onTasbih = onNavigateToTasbih, onMosque = onNavigateToMosque, onZakat = onNavigateToZakat, onCalendar = onNavigateToCalendar) }
        item { MenuGridSection(onQuran = onNavigateToQuran, onKiblat = onNavigateToKiblat, onDoa = onNavigateToDoa, onSchedule = onNavigateToSchedule, onAsmaul = onNavigateToAsmaul) }
        item { DailyHadithSection(hadith) }
    }
}

@Composable
fun HeaderSection(isDarkMode: Boolean, onThemeToggle: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column { Text("Assalamualaikum,", fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)); Text("Hamba Allah", fontSize = 22.sp, fontWeight = FontWeight.Bold) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onThemeToggle(!isDarkMode) }) { Icon(if (isDarkMode) Icons.Default.LightMode else Icons.Default.DarkMode, null, tint = MaterialTheme.colorScheme.primary) }
            Box(modifier = Modifier.size(45.dp).background(MaterialTheme.colorScheme.surface, CircleShape).shadow(2.dp, CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Person, null, tint = MaterialTheme.colorScheme.primary) }
        }
    }
}

@Composable
fun HeroPrayerCard(timings: Timings?, location: String) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).height(200.dp), shape = RoundedCornerShape(32.dp), elevation = CardDefaults.cardElevation(8.dp)) {
        Box(modifier = Modifier.fillMaxSize().background(Brush.linearGradient(listOf(DeepEmerald, EmeraldMain)))) {
            val composition by rememberLottieComposition(LottieCompositionSpec.Url("https://assets9.lottiefiles.com/packages/lf20_at6p7p0o.json"))
            LottieAnimation(composition = composition, iterations = LottieConstants.IterateForever, modifier = Modifier.size(250.dp).align(Alignment.CenterEnd).graphicsLayer(alpha = 0.2f))
            Column(modifier = Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text("Maghrib Selanjutnya", color = EmeraldLight, fontSize = 14.sp); Spacer(modifier = Modifier.height(8.dp))
                Text(timings?.Maghrib ?: "--:--", color = Color.White, fontSize = 54.sp, fontWeight = FontWeight.Bold); Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.LocationOn, null, tint = AmberGold, modifier = Modifier.size(14.dp)); Spacer(modifier = Modifier.width(4.dp)); Text(location, color = EmeraldLight, fontSize = 12.sp) }
            }
        }
    }
}

@Composable
fun QuickActionsSection(onTasbih: () -> Unit, onMosque: () -> Unit, onZakat: () -> Unit, onCalendar: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(top = 24.dp, start = 24.dp, end = 24.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        QuickActionButton("Zakat", Icons.Outlined.Payments, Color(0xFF10B981), onZakat)
        QuickActionButton("Kalender", Icons.Outlined.Event, Color(0xFFF59E0B), onCalendar)
        QuickActionButton("Mosque", Icons.Outlined.Place, Color(0xFF3B82F6), onMosque)
        QuickActionButton("Tasbih", Icons.Outlined.Fingerprint, Color(0xFFEC4899), onTasbih)
    }
}

@Composable
fun QuickActionButton(label: String, icon: ImageVector, color: Color, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(modifier = Modifier.size(60.dp).background(color.copy(alpha = 0.1f), RoundedCornerShape(18.dp)).clickable(onClick = onClick), contentAlignment = Alignment.Center) { Icon(icon, null, tint = color, modifier = Modifier.size(24.dp)) }
        Spacer(modifier = Modifier.height(8.dp)); Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun MenuGridSection(onQuran: () -> Unit, onKiblat: () -> Unit, onDoa: () -> Unit, onSchedule: () -> Unit, onAsmaul: () -> Unit) {
    Column(modifier = Modifier.padding(24.dp)) {
        Text("Layanan Utama", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) { MenuIconCard("Al-Quran", Icons.AutoMirrored.Outlined.MenuBook, EmeraldMain, Modifier.weight(1f), onQuran); MenuIconCard("Kiblat", Icons.Outlined.Explore, AmberGold, Modifier.weight(1f), onKiblat) }
        Spacer(modifier = Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) { MenuIconCard("Asmaul Husna", Icons.Outlined.AutoAwesome, Color(0xFF3B82F6), Modifier.weight(1f), onAsmaul); MenuIconCard("Doa & Jadwal", Icons.Outlined.FavoriteBorder, Color(0xFFEC4899), Modifier.weight(1f), onDoa) }
    }
}

@Composable
fun MenuIconCard(title: String, icon: ImageVector, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Card(modifier = modifier.height(110.dp).clickable(onClick = onClick), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(2.dp)) {
        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Box(modifier = Modifier.size(50.dp).background(color.copy(alpha = 0.1f), CircleShape), contentAlignment = Alignment.Center) { Icon(icon, null, tint = color, modifier = Modifier.size(26.dp)) }
            Spacer(modifier = Modifier.height(12.dp)); Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun DailyHadithSection(hadith: String) {
    Column(modifier = Modifier.padding(horizontal = 24.dp)) {
        Text("Hadits Hari Ini", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))) {
            Column(modifier = Modifier.padding(20.dp)) { Icon(Icons.Default.FormatQuote, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp)); Text(hadith, fontSize = 16.sp, fontWeight = FontWeight.Medium, lineHeight = 24.sp) }
        }
    }
}

@Composable
fun QuranScreen(db: QuranDatabase, onBack: () -> Unit, onSurahClick: (Int, String) -> Unit) {
    val context = LocalContext.current
    val sharedPref = context.getSharedPreferences("MuslimAppsSettings", Context.MODE_PRIVATE)
    val lastSurahId = sharedPref.getInt("lastSurahId", -1)
    val lastSurahName = sharedPref.getString("lastSurahName", "")
    var surahs by remember { mutableStateOf<List<Surah>>(emptyList()) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        scope.launch {
            val localSurahs = db.quranDao().getAllSurahs()
            if (localSurahs.isNotEmpty()) { surahs = localSurahs }
            else {
                // In real app, fetch from API and save to DB
                val mockSurahs = listOf(Surah(1, "Al-Fatihah", "The Opening", 7, "Meccan"), Surah(2, "Al-Baqarah", "The Cow", 286, "Medinan"))
                db.quranDao().insertSurahs(mockSurahs)
                surahs = mockSurahs
            }
        }
    }

    Scaffold(topBar = { Column(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) { Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBackIosNew, null, modifier = Modifier.size(20.dp)) }; Text("Al-Quran Digital", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.Center); IconButton(onClick = {}) { Icon(Icons.Default.Search, null) } }; HorizontalDivider() } }) { padding ->
        LazyColumn(modifier = Modifier.padding(padding), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (lastSurahId != -1) { item { Text("Terakhir Dibaca", color = EmeraldMain, modifier = Modifier.padding(bottom = 8.dp)); Card(modifier = Modifier.fillMaxWidth().clickable { onSurahClick(lastSurahId, lastSurahName ?: "") }, colors = CardDefaults.cardColors(containerColor = EmeraldMain.copy(alpha = 0.1f))) { Row(modifier = Modifier.padding(16.dp)) { Icon(Icons.Default.History, null, tint = EmeraldMain); Spacer(modifier = Modifier.width(12.dp)); Text(lastSurahName ?: "", fontWeight = FontWeight.Bold); Spacer(modifier = Modifier.weight(1f)); Text("Lanjutkan", fontSize = 12.sp) } }; Spacer(modifier = Modifier.height(16.dp)) } }
            items(surahs) { surah -> SurahItemCard(surah) { onSurahClick(surah.id, surah.name) } }
        }
    }
}

@Composable
fun SurahItemCard(surah: Surah, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(44.dp), contentAlignment = Alignment.Center) { Canvas(modifier = Modifier.size(36.dp).rotate(45f)) { drawRoundRect(color = EmeraldMain.copy(alpha = 0.1f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx())); drawRoundRect(color = EmeraldMain, style = Stroke(width = 1.dp.toPx()), cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx())) }; Text(surah.id.toString(), fontWeight = FontWeight.Bold, color = EmeraldMain) }
            Spacer(modifier = Modifier.width(16.dp)); Column(modifier = Modifier.weight(1f)) { Text(surah.name, fontSize = 16.sp, fontWeight = FontWeight.Bold); Text("${surah.revelationType} • ${surah.ayatCount} Ayat", fontSize = 12.sp, color = Color.Gray) }; Text(surah.englishName, fontSize = 18.sp, fontWeight = FontWeight.Medium, color = EmeraldMain)
        }
    }
}

@Composable
fun SurahDetailScreen(db: QuranDatabase, surahId: Int, surahName: String, onBack: () -> Unit) {
    val context = LocalContext.current
    var ayahs by remember { mutableStateOf<List<AyahEntity>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    val mediaPlayer = remember { MediaPlayer() }
    var currentPlayingId by remember { mutableIntStateOf(-1) }

    LaunchedEffect(surahId) {
        scope.launch {
            val localAyahs = db.quranDao().getAyahsForSurah(surahId)
            if (localAyahs.isNotEmpty()) { ayahs = localAyahs; isLoading = false }
            else {
                try {
                    val retrofit = Retrofit.Builder().baseUrl("https://api.alquran.cloud/").addConverterFactory(GsonConverterFactory.create()).build()
                    val api = retrofit.create(QuranApi::class.java)
                    val response = api.getSurahDetail(surahId)
                    val entities = response.data[0].ayahs.indices.map { i -> AyahEntity(surahId = surahId, numberInSurah = response.data[0].ayahs[i].numberInSurah, text = response.data[0].ayahs[i].text, translation = response.data[1].ayahs[i].text) }
                    db.quranDao().insertAyahs(entities); ayahs = entities; isLoading = false
                } catch (e: Exception) { isLoading = false }
            }
        }
    }

    DisposableEffect(Unit) { onDispose { mediaPlayer.release() } }

    Scaffold(topBar = { Column(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) { Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = { mediaPlayer.stop(); onBack() }) { Icon(Icons.Default.ArrowBackIosNew, null, modifier = Modifier.size(20.dp)) }; Text(surahName, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.Center); Spacer(modifier = Modifier.width(48.dp)) }; HorizontalDivider() } }) { padding ->
        if (isLoading) { Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = EmeraldMain) } }
        else { LazyColumn(modifier = Modifier.padding(padding), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) { items(ayahs) { ayah -> AyahItem(ayah, isPlaying = currentPlayingId == ayah.numberInSurah) { if (currentPlayingId == ayah.numberInSurah) { mediaPlayer.stop(); currentPlayingId = -1 } else { mediaPlayer.reset(); mediaPlayer.setDataSource("https://cdn.islamic.network/quran/audio/128/ar.alafasy/${ayah.numberInSurah}.mp3"); mediaPlayer.prepareAsync(); mediaPlayer.setOnPreparedListener { it.start(); currentPlayingId = ayah.numberInSurah }; mediaPlayer.setOnCompletionListener { currentPlayingId = -1 } } } } } }
    }
}

@Composable
fun AyahItem(ayah: AyahEntity, isPlaying: Boolean, onPlayClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Box(modifier = Modifier.size(30.dp).background(EmeraldMain.copy(alpha = 0.1f), CircleShape), contentAlignment = Alignment.Center) { Text(ayah.numberInSurah.toString(), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = EmeraldMain) }; Spacer(modifier = Modifier.width(10.dp)); IconButton(onClick = onPlayClick) { Icon(if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow, null, tint = EmeraldMain) }; Spacer(modifier = Modifier.width(10.dp)); HorizontalDivider(modifier = Modifier.weight(1f)) }
            Spacer(modifier = Modifier.height(20.dp)); Text(ayah.text, fontSize = 24.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth(), lineHeight = 44.sp); Spacer(modifier = Modifier.height(16.dp)); Text(ayah.translation, fontSize = 14.sp, color = Color.Gray, lineHeight = 22.sp)
        }
    }
}

@Composable
fun AsmaulHusnaScreen(onBack: () -> Unit) {
    Scaffold(topBar = { Column(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) { Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBackIosNew, null, modifier = Modifier.size(20.dp)) }; Text("Asmaul Husna", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.Center); Spacer(modifier = Modifier.width(48.dp)) }; HorizontalDivider() } }) { padding ->
        LazyColumn(modifier = Modifier.padding(padding), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { items(AsmaulHusnaData.items) { item -> AsmaulCard(item) } }
    }
}

@Composable
fun AsmaulCard(item: AsmaulHusnaItem) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(44.dp).background(EmeraldMain.copy(alpha = 0.1f), CircleShape), contentAlignment = Alignment.Center) { Text(item.id.toString(), fontWeight = FontWeight.Bold, color = EmeraldMain) }
            Spacer(modifier = Modifier.width(16.dp)); Column(modifier = Modifier.weight(1f)) { Text(item.latin, fontSize = 16.sp, fontWeight = FontWeight.Bold); Text(item.translation, fontSize = 12.sp, color = Color.Gray) }; Text(item.arabic, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = EmeraldMain)
        }
    }
}

@Composable
fun HijriCalendarScreen(onBack: () -> Unit) {
    val hijriDate = "15 Ramadhan 1446 H" // Mock
    val holidays = listOf("1 Ramadhan: Awal Puasa", "17 Ramadhan: Nuzulul Quran", "1 Syawal: Idul Fitri")
    Scaffold(topBar = { Column(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) { Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBackIosNew, null, modifier = Modifier.size(20.dp)) }; Text("Kalender Hijriah", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.Center); Spacer(modifier = Modifier.width(48.dp)) }; HorizontalDivider() } }) { padding ->
        Column(modifier = Modifier.padding(padding).padding(24.dp)) {
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = EmeraldMain)) { Column(modifier = Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text("Hari Ini", color = Color.White.copy(alpha = 0.8f)); Text(hijriDate, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White) } }
            Spacer(modifier = Modifier.height(24.dp)); Text("Hari Besar Mendatang", fontWeight = FontWeight.Bold, fontSize = 18.sp); Spacer(modifier = Modifier.height(16.dp))
            holidays.forEach { holiday -> Card(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) { Text(holiday, modifier = Modifier.padding(16.dp), fontWeight = FontWeight.Medium) } }
        }
    }
}

@Composable
fun PrayerScheduleScreen(timings: Timings?, onBack: () -> Unit) {
    val context = LocalContext.current
    val prayerList = timings?.let { listOf("Subuh" to it.Fajr, "Dzuhur" to it.Dhuhr, "Ashar" to it.Asr, "Maghrib" to it.Maghrib, "Isya" to it.Isha) } ?: emptyList()
    Scaffold(topBar = { Column(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) { Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBackIosNew, null, modifier = Modifier.size(20.dp)) }; Text("Jadwal Shalat", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.Center); Spacer(modifier = Modifier.width(48.dp)) }; HorizontalDivider() } }) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (prayerList.isEmpty()) { item { Box(modifier = Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) { Text("Memuat jadwal...") } } }
            else { items(prayerList) { (name, time) -> PrayerTimeItem(name, time) { if (it) scheduleAdzan(context, name, time) else cancelAdzan(context, name) } } }
        }
    }
}

@Composable
fun PrayerTimeItem(name: String, time: String, onToggle: (Boolean) -> Unit) {
    var isEnabled by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(2.dp)) {
        Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) { Text(name, fontSize = 18.sp, fontWeight = FontWeight.Bold); Text(time, fontSize = 24.sp, fontWeight = FontWeight.Medium, color = EmeraldMain) }
            Switch(checked = isEnabled, onCheckedChange = { isEnabled = it; onToggle(it) }, colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = EmeraldMain))
        }
    }
}

fun scheduleAdzan(context: Context, name: String, time: String) {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val intent = Intent(context, AdhanBroadcastReceiver::class.java).apply { putExtra("PRAYER_NAME", name) }
    val pendingIntent = PendingIntent.getBroadcast(context, name.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    val calendar = Calendar.getInstance()
    val parts = time.split(":")
    if (parts.size == 2) {
        calendar.set(Calendar.HOUR_OF_DAY, parts[0].toInt()); calendar.set(Calendar.MINUTE, parts[1].toInt()); calendar.set(Calendar.SECOND, 0)
        if (calendar.timeInMillis <= System.currentTimeMillis()) calendar.add(Calendar.DAY_OF_YEAR, 1)
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
        Toast.makeText(context, "Pengingat $name aktif", Toast.LENGTH_SHORT).show()
    }
}

fun cancelAdzan(context: Context, name: String) {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val pendingIntent = PendingIntent.getBroadcast(context, name.hashCode(), Intent(context, AdhanBroadcastReceiver::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    alarmManager.cancel(pendingIntent); Toast.makeText(context, "Pengingat $name dimatikan", Toast.LENGTH_SHORT).show()
}

@Composable
fun ZakatCalculatorScreen(onBack: () -> Unit) {
    var harta by remember { mutableStateOf("") }
    var result by remember { mutableDoubleStateOf(0.0) }
    val nishab = 85.0 * 1000000.0
    Scaffold(topBar = { Column(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) { Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBackIosNew, null, modifier = Modifier.size(20.dp)) }; Text("Kalkulator Zakat", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.Center); Spacer(modifier = Modifier.width(48.dp)) }; HorizontalDivider() } }) { padding ->
        Column(modifier = Modifier.padding(padding).padding(24.dp)) {
            Text("Hitung Zakat Maal (2.5%)", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = EmeraldMain); Spacer(modifier = Modifier.height(24.dp))
            OutlinedTextField(value = harta, onValueChange = { harta = it }, label = { Text("Total Harta (1 Tahun)") }, modifier = Modifier.fillMaxWidth(), prefix = { Text("Rp ") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), shape = RoundedCornerShape(16.dp))
            Spacer(modifier = Modifier.height(24.dp)); Button(onClick = { val h = harta.toDoubleOrNull() ?: 0.0; result = if (h >= nishab) h * 0.025 else 0.0 }, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = EmeraldMain)) { Text("Hitung Zakat") }
            if (result > 0) { Spacer(modifier = Modifier.height(32.dp)); Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = EmeraldMain.copy(alpha = 0.1f))) { Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text("Zakat yang harus dibayar:", color = EmeraldMain); Text(formatCurrency(result), fontSize = 32.sp, fontWeight = FontWeight.Black, color = EmeraldMain) } } }
        }
    }
}

fun formatCurrency(amount: Double): String = NumberFormat.getCurrencyInstance(Locale("id", "ID")).format(amount)

@Composable
fun MosqueMapScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        LocationServices.getFusedLocationProviderClient(context).lastLocation.addOnSuccessListener { location ->
            location?.let { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:${it.latitude},${it.longitude}?q=mosque")).apply { setPackage("com.google.android.apps.maps") }); onBack() }
        }
    }
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = EmeraldMain) }
}

@Composable
fun DoaScreen(onBack: () -> Unit) {
    val doas = listOf(DoaItem("Doa Sebelum Tidur", "بِاسْمِكَ اللهم أَمُوتُ وَأَحْيَا", "Bismika Allahumma amutu wa ahya", "Dengan nama-Mu ya Allah aku mati dan aku hidup."), DoaItem("Doa Bangun Tidur", "الْحَمْدُ لِلَّهِ الَّذِي أَحْيَانَا بَعْدَ مَا أَمَاتَنَا وَإِلَيْهِ النُّشُورُ", "Alhamdulillahil ladzi ahyana ba'da ma amatana wa ilaihin nusyur", "Segala puji bagi Allah yang telah menghidupkan kami setelah mematikan kami, dan kepada-Nya lah tempat kembali."))
    Scaffold(topBar = { Column(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) { Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBackIosNew, null, modifier = Modifier.size(20.dp)) }; Text("Doa-Doa Harian", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.Center); IconButton(onClick = {}) { Icon(Icons.Default.Search, null) } }; HorizontalDivider() } }) { padding ->
        LazyColumn(modifier = Modifier.padding(padding), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) { items(doas) { doa -> DoaCard(doa) } }
    }
}

data class DoaItem(val title: String, val arabic: String, val latin: String, val translation: String)
@Composable
fun DoaCard(doa: DoaItem) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
        Column(modifier = Modifier.padding(20.dp)) { Text(doa.title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = EmeraldMain); Spacer(modifier = Modifier.height(16.dp)); Text(doa.arabic, fontSize = 22.sp, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth()); Spacer(modifier = Modifier.height(12.dp)); Text(doa.latin, fontSize = 14.sp, color = AmberGold); Spacer(modifier = Modifier.height(8.dp)); Text(doa.translation, fontSize = 14.sp, color = Color.Gray) }
    }
}

@Composable
fun KiblatScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var azimuth by remember { mutableStateOf(0f) }
    DisposableEffect(Unit) {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val s = sm.getDefaultSensor(Sensor.TYPE_ORIENTATION)
        val l = object : SensorEventListener { override fun onSensorChanged(e: SensorEvent?) { e?.let { azimuth = it.values[0] } }; override fun onAccuracyChanged(s: Sensor?, a: Int) {} }
        sm.registerListener(l, s, SensorManager.SENSOR_DELAY_UI); onDispose { sm.unregisterListener(l) }
    }
    Column(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(DeepEmerald, Color(0xFF022C22)))), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(modifier = Modifier.fillMaxWidth().padding(16.dp)) { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBackIosNew, null, tint = Color.White) }; Text("Kompas Kiblat", modifier = Modifier.fillMaxWidth().padding(top = 10.dp), textAlign = TextAlign.Center, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
        Spacer(modifier = Modifier.height(40.dp)); Box(contentAlignment = Alignment.Center) {
            Box(modifier = Modifier.size(320.dp).border(2.dp, Color.White.copy(alpha = 0.2f), CircleShape))
            Box(modifier = Modifier.size(280.dp).rotate(-azimuth)) { Text("N", modifier = Modifier.align(Alignment.TopCenter).padding(10.dp), color = Color.White, fontWeight = FontWeight.Bold); Text("S", modifier = Modifier.align(Alignment.BottomCenter).padding(10.dp), color = Color.White, fontWeight = FontWeight.Bold) }
            Icon(Icons.Default.LocationOn, null, modifier = Modifier.size(60.dp).offset(y = (-110).dp), tint = AmberGold)
            Box(modifier = Modifier.size(180.dp).rotate(-azimuth)) { Canvas(modifier = Modifier.fillMaxSize()) { val center = Offset(size.width/2, size.height/2); drawPath(Path().apply { moveTo(center.x, center.y - 80.dp.toPx()); lineTo(center.x - 10.dp.toPx(), center.y); lineTo(center.x + 10.dp.toPx(), center.y); close() }, Color.White) } }
        }
    }
}

@Composable
fun TasbihScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var count by remember { mutableIntStateOf(0) }
    var target by remember { mutableIntStateOf(33) }
    val scale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    fun vibrate() { val v = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator else context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator; if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) v.vibrate(VibrationEffect.createOneShot(50, 255)) else v.vibrate(50) }
    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBackIosNew, null) }; Text("Tasbih Digital", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.Center); IconButton(onClick = { count = 0 }) { Icon(Icons.Default.Refresh, null, tint = EmeraldMain) } }
        Spacer(modifier = Modifier.height(40.dp)); Row(modifier = Modifier.background(MaterialTheme.colorScheme.surface, RoundedCornerShape(24.dp)).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf(33, 99, 100).forEach { valItem -> val isSelected = target == valItem; Box(modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(if (isSelected) EmeraldMain else Color.Transparent).clickable { target = valItem }.padding(horizontal = 24.dp, vertical = 8.dp)) { Text(valItem.toString(), color = if (isSelected) Color.White else Color.Gray, fontWeight = FontWeight.Bold) } } }
        Spacer(modifier = Modifier.weight(1f)); Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(count.toString(), fontSize = 100.sp, fontWeight = FontWeight.Black, color = EmeraldMain); Text("dari $target", fontSize = 20.sp, color = Color.Gray) }
        Spacer(modifier = Modifier.weight(1f)); Box(modifier = Modifier.size(240.dp).graphicsLayer(scaleX = scale.value, scaleY = scale.value).shadow(12.dp, CircleShape).background(Brush.radialGradient(listOf(EmeraldMain, DeepEmerald)), CircleShape).clickable { count++; vibrate(); scope.launch { scale.animateTo(1.1f, spring(Spring.DampingRatioMediumBouncy)); scale.animateTo(1f, spring(Spring.DampingRatioMediumBouncy)) } }, contentAlignment = Alignment.Center) { Icon(Icons.Default.TouchApp, null, tint = Color.White, modifier = Modifier.size(64.dp)) }
        Spacer(modifier = Modifier.height(80.dp))
    }
}
