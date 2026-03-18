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
import android.media.AudioAttributes
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
import androidx.compose.foundation.lazy.rememberLazyListState
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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sharedPref = getSharedPreferences("MuslimAppsSettings", Context.MODE_PRIVATE)
        
        setContent {
            var isDarkMode by remember { 
                mutableStateOf(sharedPref.getBoolean("isDarkMode", false)) 
            }
            
            MuslimAppsTheme(darkTheme = isDarkMode) {
                MainNavigation(
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
fun MainNavigation(isDarkMode: Boolean, onThemeToggle: (Boolean) -> Unit) {
    var currentScreen by remember { mutableStateOf("dashboard") }
    var prayerTimesData by remember { mutableStateOf<Timings?>(null) }
    var selectedSurahId by remember { mutableStateOf<Int?>(null) }
    var selectedSurahName by remember { mutableStateOf("") }
    
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        AnimatedContent(
            targetState = currentScreen,
            transitionSpec = {
                fadeIn(animationSpec = tween(500)) togetherWith fadeOut(animationSpec = tween(500))
            }, label = ""
        ) { screen ->
            when (screen) {
                "dashboard" -> DashboardScreen(
                    isDarkMode = isDarkMode,
                    onThemeToggle = onThemeToggle,
                    onNavigateToQuran = { currentScreen = "quran" },
                    onNavigateToKiblat = { currentScreen = "kiblat" },
                    onNavigateToTasbih = { currentScreen = "tasbih" },
                    onNavigateToDoa = { currentScreen = "doa" },
                    onNavigateToSchedule = { currentScreen = "schedule" },
                    onNavigateToMosque = { currentScreen = "mosque" },
                    onNavigateToZakat = { currentScreen = "zakat" },
                    onDataLoaded = { prayerTimesData = it }
                )
                "quran" -> QuranScreen(
                    onBack = { currentScreen = "dashboard" },
                    onSurahClick = { id, name -> 
                        selectedSurahId = id
                        selectedSurahName = name
                        currentScreen = "quran_detail" 
                    }
                )
                "quran_detail" -> SurahDetailScreen(
                    surahId = selectedSurahId ?: 1,
                    surahName = selectedSurahName,
                    onBack = { currentScreen = "quran" }
                )
                "kiblat" -> KiblatScreen(onBack = { currentScreen = "dashboard" })
                "tasbih" -> TasbihScreen(onBack = { currentScreen = "dashboard" })
                "doa" -> DoaScreen(onBack = { currentScreen = "dashboard" })
                "schedule" -> PrayerScheduleScreen(prayerTimesData, onBack = { currentScreen = "dashboard" })
                "mosque" -> MosqueMapScreen(onBack = { currentScreen = "dashboard" })
                "zakat" -> ZakatCalculatorScreen(onBack = { currentScreen = "dashboard" })
            }
        }
    }
}

@Composable
fun DashboardScreen(
    isDarkMode: Boolean,
    onThemeToggle: (Boolean) -> Unit,
    onNavigateToQuran: () -> Unit, 
    onNavigateToKiblat: () -> Unit,
    onNavigateToTasbih: () -> Unit,
    onNavigateToDoa: () -> Unit,
    onNavigateToSchedule: () -> Unit,
    onNavigateToMosque: () -> Unit,
    onNavigateToZakat: () -> Unit,
    onDataLoaded: (Timings) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var prayerTimes by remember { mutableStateOf<Timings?>(null) }
    var locationName by remember { mutableStateOf("Mencari Lokasi...") }

    val requestPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true) { }
    }

    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.POST_NOTIFICATIONS))
        } else {
            val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                location?.let {
                    scope.launch {
                        try {
                            val retrofit = Retrofit.Builder()
                                .baseUrl("https://api.aladhan.com/")
                                .addConverterFactory(GsonConverterFactory.create())
                                .build()
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

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item { HeaderSection(isDarkMode, onThemeToggle) }
        item { HeroPrayerCard(prayerTimes, locationName) }
        item { QuickActionsSection(onTasbih = onNavigateToTasbih, onMosque = onNavigateToMosque, onZakat = onNavigateToZakat) }
        item { MenuGridSection(onQuran = onNavigateToQuran, onKiblat = onNavigateToKiblat, onDoa = onNavigateToDoa, onSchedule = onNavigateToSchedule) }
        item { DailyInspirationSection() }
    }
}

@Composable
fun HeaderSection(isDarkMode: Boolean, onThemeToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text("Assalamualaikum,", fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
            Text("Hamba Allah", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onThemeToggle(!isDarkMode) }) {
                Icon(
                    if (isDarkMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                    contentDescription = "Toggle Theme",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Box(
                modifier = Modifier.size(45.dp).background(MaterialTheme.colorScheme.surface, CircleShape).shadow(2.dp, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
fun HeroPrayerCard(timings: Timings?, location: String) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).height(200.dp),
        shape = RoundedCornerShape(32.dp),
        elevation = CardDefaults.cardElevation(8.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize().background(Brush.linearGradient(colors = listOf(DeepEmerald, EmeraldMain)))) {
            val composition by rememberLottieComposition(LottieCompositionSpec.Url("https://assets9.lottiefiles.com/packages/lf20_at6p7p0o.json"))
            LottieAnimation(composition = composition, iterations = LottieConstants.IterateForever, modifier = Modifier.size(250.dp).align(Alignment.CenterEnd).graphicsLayer(alpha = 0.2f))

            Column(modifier = Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text(text = "Maghrib Selanjutnya", color = EmeraldLight, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = timings?.Maghrib ?: "--:--", color = Color.White, fontSize = 54.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.LocationOn, contentDescription = null, tint = AmberGold, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = location, color = EmeraldLight, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun QuickActionsSection(onTasbih: () -> Unit, onMosque: () -> Unit, onZakat: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(top = 24.dp, start = 24.dp, end = 24.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        QuickActionButton("Zakat", Icons.Outlined.Payments, Color(0xFF10B981), onClick = onZakat)
        QuickActionButton("Calendar", Icons.Outlined.Event, Color(0xFFF59E0B))
        QuickActionButton("Mosque", Icons.Outlined.Place, Color(0xFF3B82F6), onClick = onMosque)
        QuickActionButton("Tasbih", Icons.Outlined.Fingerprint, Color(0xFFEC4899), onClick = onTasbih)
    }
}

@Composable
fun QuickActionButton(label: String, icon: ImageVector, color: Color, onClick: () -> Unit = {}) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(modifier = Modifier.size(60.dp).background(color.copy(alpha = 0.1f), RoundedCornerShape(18.dp)).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = label, tint = color, modifier = Modifier.size(24.dp))
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onBackground)
    }
}

@Composable
fun MenuGridSection(onQuran: () -> Unit, onKiblat: () -> Unit, onDoa: () -> Unit, onSchedule: () -> Unit) {
    Column(modifier = Modifier.padding(24.dp)) {
        Text("Layanan Utama", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        Spacer(modifier = Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            MenuIconCard("Al-Quran", Icons.AutoMirrored.Outlined.MenuBook, EmeraldMain, Modifier.weight(1f), onQuran)
            MenuIconCard("Kiblat", Icons.Outlined.Explore, AmberGold, Modifier.weight(1f), onKiblat)
        }
        Spacer(modifier = Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            MenuIconCard("Jadwal", Icons.Outlined.Schedule, Color(0xFF3B82F6), Modifier.weight(1f), onClick = onSchedule)
            MenuIconCard("Doa-Doa", Icons.Outlined.FavoriteBorder, Color(0xFFEC4899), Modifier.weight(1f), onClick = onDoa)
        }
    }
}

@Composable
fun MenuIconCard(title: String, icon: ImageVector, color: Color, modifier: Modifier = Modifier, onClick: () -> Unit = {}) {
    Card(modifier = modifier.height(110.dp).clickable(onClick = onClick), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(2.dp)) {
        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Box(modifier = Modifier.size(50.dp).background(color.copy(alpha = 0.1f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = title, tint = color, modifier = Modifier.size(26.dp))
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
fun DailyInspirationSection() {
    Column(modifier = Modifier.padding(horizontal = 24.dp)) {
        Text("Inspirasi Hari Ini", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        Spacer(modifier = Modifier.height(16.dp))
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))) {
            Column(modifier = Modifier.padding(20.dp)) {
                Icon(Icons.Default.FormatQuote, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                Text("\"Maka sesungguhnya bersama kesulitan ada kemudahan.\"", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onPrimaryContainer, lineHeight = 24.sp)
                Text("QS. Al-Insyirah: 5", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}

@Composable
fun PrayerScheduleScreen(timings: Timings?, onBack: () -> Unit) {
    val context = LocalContext.current
    val prayerList = timings?.let {
        listOf(
            "Subuh" to it.Fajr,
            "Dzuhur" to it.Dhuhr,
            "Ashar" to it.Asr,
            "Maghrib" to it.Maghrib,
            "Isya" to it.Isha
        )
    } ?: emptyList()

    Scaffold(
        topBar = {
            Column(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
                Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBackIosNew, null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp)) }
                    Text("Jadwal Shalat", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.width(48.dp))
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (prayerList.isEmpty()) {
                item { Box(modifier = Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) { Text("Memuat jadwal...", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)) } }
            } else {
                items(prayerList) { (name, time) ->
                    PrayerTimeItem(name, time, onToggleAdzan = { enabled ->
                        if (enabled) {
                            scheduleAdzan(context, name, time)
                        } else {
                            cancelAdzan(context, name)
                        }
                    })
                }
            }
        }
    }
}

@Composable
fun PrayerTimeItem(name: String, time: String, onToggleAdzan: (Boolean) -> Unit) {
    var isEnabled by remember { mutableStateOf(false) } 
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(name, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text(time, fontSize = 24.sp, fontWeight = FontWeight.Medium, color = EmeraldMain)
            }
            Switch(
                checked = isEnabled,
                onCheckedChange = { 
                    isEnabled = it
                    onToggleAdzan(it)
                },
                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = EmeraldMain)
            )
        }
    }
}

fun scheduleAdzan(context: Context, prayerName: String, time: String) {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val intent = Intent(context, AdhanBroadcastReceiver::class.java).apply {
        putExtra("PRAYER_NAME", prayerName)
    }
    
    val pendingIntent = PendingIntent.getBroadcast(
        context, 
        prayerName.hashCode(), 
        intent, 
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val calendar = Calendar.getInstance()
    val parts = time.split(":")
    if (parts.size == 2) {
        calendar.set(Calendar.HOUR_OF_DAY, parts[0].toInt())
        calendar.set(Calendar.MINUTE, parts[1].toInt())
        calendar.set(Calendar.SECOND, 0)

        if (calendar.timeInMillis <= System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
            }
        } else {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
        }
        
        Toast.makeText(context, "Pengingat $prayerName aktif pada $time", Toast.LENGTH_SHORT).show()
    }
}

fun cancelAdzan(context: Context, prayerName: String) {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val intent = Intent(context, AdhanBroadcastReceiver::class.java)
    val pendingIntent = PendingIntent.getBroadcast(
        context, 
        prayerName.hashCode(), 
        intent, 
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    alarmManager.cancel(pendingIntent)
    Toast.makeText(context, "Pengingat $prayerName dimatikan", Toast.LENGTH_SHORT).show()
}

@Composable
fun QuranScreen(onBack: () -> Unit, onSurahClick: (Int, String) -> Unit) {
    val context = LocalContext.current
    val sharedPref = context.getSharedPreferences("MuslimAppsSettings", Context.MODE_PRIVATE)
    val lastSurahId = sharedPref.getInt("lastSurahId", -1)
    val lastSurahName = sharedPref.getString("lastSurahName", "")

    val surahs = remember { listOf(
        Surah(1, "Al-Fatihah", "The Opening", 7, "Meccan"), 
        Surah(2, "Al-Baqarah", "The Cow", 286, "Medinan"), 
        Surah(3, "Ali 'Imran", "Family of Imran", 200, "Medinan"), 
        Surah(114, "An-Nas", "Mankind", 6, "Meccan")
    ) }

    Scaffold(topBar = { 
        Column(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) { 
            Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) { 
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBackIosNew, null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp)) }
                Text("Al-Quran Digital", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                IconButton(onClick = {}) { Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.onSurface) } 
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline) 
        } 
    }, containerColor = MaterialTheme.colorScheme.background) { padding ->
        LazyColumn(modifier = Modifier.padding(padding), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { 
            if (lastSurahId != -1) {
                item {
                    Text("Terakhir Dibaca", fontWeight = FontWeight.Bold, color = EmeraldMain, modifier = Modifier.padding(bottom = 8.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { onSurahClick(lastSurahId, lastSurahName ?: "") },
                        colors = CardDefaults.cardColors(containerColor = EmeraldMain.copy(alpha = 0.1f)),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.History, contentDescription = null, tint = EmeraldMain)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(lastSurahName ?: "", fontWeight = FontWeight.Bold, color = EmeraldMain)
                            Spacer(modifier = Modifier.weight(1f))
                            Text("Lanjutkan", fontSize = 12.sp, color = EmeraldMain)
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
            items(surahs) { surah -> SurahItemCard(surah, onClick = { onSurahClick(surah.id, surah.name) }) } 
        }
    }
}

@Composable
fun SurahItemCard(surah: Surah, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                Canvas(modifier = Modifier.size(36.dp).rotate(45f)) { drawRoundRect(color = EmeraldMain.copy(alpha = 0.1f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx())); drawRoundRect(color = EmeraldMain, style = Stroke(width = 1.dp.toPx()), cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx())) }
                Text(surah.id.toString(), fontWeight = FontWeight.Bold, color = EmeraldMain, fontSize = 14.sp)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) { Text(surah.name, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface); Text("${surah.revelationType} • ${surah.ayatCount} Ayat", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)) }
            Text(surah.englishName, fontSize = 18.sp, fontWeight = FontWeight.Medium, color = EmeraldMain, textAlign = TextAlign.End)
        }
    }
}

@Composable
fun SurahDetailScreen(surahId: Int, surahName: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val sharedPref = context.getSharedPreferences("MuslimAppsSettings", Context.MODE_PRIVATE)
    var arabicAyahs by remember { mutableStateOf<List<Ayah>>(emptyList()) }
    var indoAyahs by remember { mutableStateOf<List<Ayah>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    val mediaPlayer = remember { MediaPlayer() }
    var currentPlayingId by remember { mutableIntStateOf(-1) }

    LaunchedEffect(surahId) {
        sharedPref.edit().putInt("lastSurahId", surahId).putString("lastSurahName", surahName).apply()
        scope.launch {
            try {
                val retrofit = Retrofit.Builder().baseUrl("https://api.alquran.cloud/").addConverterFactory(GsonConverterFactory.create()).build()
                val api = retrofit.create(QuranApi::class.java)
                val response = api.getSurahDetail(surahId)
                arabicAyahs = response.data[0].ayahs
                indoAyahs = response.data[1].ayahs
                isLoading = false
            } catch (e: Exception) { isLoading = false }
        }
    }

    DisposableEffect(Unit) {
        onDispose { mediaPlayer.release() }
    }

    Scaffold(
        topBar = {
            Column(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
                Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { mediaPlayer.stop(); onBack() }) { Icon(Icons.Default.ArrowBackIosNew, null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp)) }
                    Text(surahName, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.width(48.dp))
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            }
        }
    ) { padding ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = EmeraldMain) }
        } else {
            LazyColumn(modifier = Modifier.padding(padding), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                items(arabicAyahs.indices.toList()) { index ->
                    val ayah = arabicAyahs[index]
                    AyahItem(
                        arabic = ayah, 
                        translation = indoAyahs[index],
                        isPlaying = currentPlayingId == ayah.number,
                        onPlayClick = {
                            if (currentPlayingId == ayah.number) {
                                mediaPlayer.stop()
                                currentPlayingId = -1
                            } else {
                                mediaPlayer.reset()
                                mediaPlayer.setDataSource("https://cdn.islamic.network/quran/audio/128/ar.alafasy/${ayah.number}.mp3")
                                mediaPlayer.prepareAsync()
                                mediaPlayer.setOnPreparedListener { it.start(); currentPlayingId = ayah.number }
                                mediaPlayer.setOnCompletionListener { currentPlayingId = -1 }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun AyahItem(arabic: Ayah, translation: Ayah, isPlaying: Boolean, onPlayClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(30.dp).background(EmeraldMain.copy(alpha = 0.1f), CircleShape), contentAlignment = Alignment.Center) {
                    Text(arabic.numberInSurah.toString(), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = EmeraldMain)
                }
                Spacer(modifier = Modifier.width(10.dp))
                IconButton(onClick = onPlayClick, modifier = Modifier.size(24.dp)) {
                    Icon(
                        if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow, 
                        null, 
                        tint = EmeraldMain,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = arabic.text,
                fontSize = 24.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth(),
                lineHeight = 44.sp
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = translation.text,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                lineHeight = 22.sp
            )
        }
    }
}

@Composable
fun ZakatCalculatorScreen(onBack: () -> Unit) {
    var hartaValue by remember { mutableStateOf("") }
    var result by remember { mutableDoubleStateOf(0.0) }
    val nishabEmas = 85.0
    val hargaEmasGram = 1000000.0
    val nishabTahun = nishabEmas * hargaEmasGram

    Scaffold(
        topBar = {
            Column(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
                Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBackIosNew, null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp)) }
                    Text("Kalkulator Zakat", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.width(48.dp))
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(24.dp)) {
            Text("Hitung Zakat Maal (2.5%)", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = EmeraldMain)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Nishab saat ini: ${formatCurrency(nishabTahun)} (setara 85gr Emas)", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            Spacer(modifier = Modifier.height(24.dp))
            OutlinedTextField(
                value = hartaValue,
                onValueChange = { hartaValue = it },
                label = { Text("Total Harta (Simpanan 1 Tahun)") },
                modifier = Modifier.fillMaxWidth(),
                prefix = { Text("Rp ") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = EmeraldMain, focusedLabelColor = EmeraldMain)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = {
                    val harta = hartaValue.toDoubleOrNull() ?: 0.0
                    result = if (harta >= nishabTahun) harta * 0.025 else 0.0
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldMain)
            ) { Text("Hitung Zakat", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
            if (result > 0) {
                Spacer(modifier = Modifier.height(32.dp))
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = EmeraldMain.copy(alpha = 0.1f))) {
                    Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Zakat yang harus dibayar:", fontSize = 14.sp, color = EmeraldMain)
                        Text(formatCurrency(result), fontSize = 32.sp, fontWeight = FontWeight.Black, color = EmeraldMain)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Wajib dikeluarkan karena telah mencapai Nishab.", fontSize = 12.sp, textAlign = TextAlign.Center)
                    }
                }
            } else if (hartaValue.isNotEmpty()) {
                Spacer(modifier = Modifier.height(32.dp))
                Text("Harta belum mencapai Nishab. Tidak wajib zakat maal.", color = Color.Gray, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

fun formatCurrency(amount: Double): String {
    val format = NumberFormat.getCurrencyInstance(Locale("id", "ID"))
    return format.format(amount)
}

@Composable
fun MosqueMapScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                location?.let {
                    val uri = Uri.parse("geo:${it.latitude},${it.longitude}?q=mosque")
                    val mapIntent = Intent(Intent.ACTION_VIEW, uri)
                    mapIntent.setPackage("com.google.android.apps.maps")
                    context.startActivity(mapIntent)
                    onBack()
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = EmeraldMain)
            Spacer(modifier = Modifier.height(16.dp))
            Text("Membuka Google Maps...")
        }
    }
}

@Composable
fun DoaScreen(onBack: () -> Unit) {
    val doas = remember { listOf(
        DoaItem("Doa Sebelum Tidur", "بِاسْمِكَ اللهم أَمُوتُ وَأَحْيَا", "Bismika Allahumma amutu wa ahya", "Dengan nama-Mu ya Allah aku mati dan aku hidup."),
        DoaItem("Doa Bangun Tidur", "الْحَمْدُ لِلَّهِ الَّذِي أَحْيَانَا بَعْدَ مَا أَمَاتَنَا وَإِلَيْهِ النُّشُورُ", "Alhamdulillahil ladzi ahyana ba'da ma amatana wa ilaihin nusyur", "Segala puji bagi Allah yang telah menghidupkan kami setelah mematikan kami, dan kepada-Nya lah tempat kembali."),
        DoaItem("Doa Masuk Masjid", "اللَّهُمَّ افْتَحْ لِي أَبْوَابَ رَحْمَتِكَ", "Allahummaftahli abwaba rahmatik", "Ya Allah, bukakanlah bagiku pintu-pintu rahmat-Mu."),
        DoaItem("Doa Keluar Masjid", "اللَّهُمَّ إِنِّI أَسْأَلُكَ مِنْ فَضْلِكَ", "Allahumma inni as'aluka min fadhlik", "Ya Allah, aku memohon kepada-Mu akan karunia-Mu.")
    ) }

    Scaffold(topBar = { Column(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) { Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBackIosNew, null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp)) }
    Text("Doa-Doa Harian", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
    IconButton(onClick = {}) { Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.onSurface) } }
    HorizontalDivider(color = MaterialTheme.colorScheme.outline) } }, containerColor = MaterialTheme.colorScheme.background) { padding ->
        LazyColumn(modifier = Modifier.padding(padding), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) { items(doas) { doa -> DoaCard(doa) } }
    }
}

data class DoaItem(val title: String, val arabic: String, val latin: String, val translation: String)

@Composable
fun DoaCard(doa: DoaItem) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(doa.title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = EmeraldMain)
            Spacer(modifier = Modifier.height(16.dp))
            Text(doa.arabic, fontSize = 22.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(12.dp))
            Text(doa.latin, fontSize = 14.sp, color = AmberGold, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(8.dp))
            Text(doa.translation, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
        }
    }
}

@Composable
fun KiblatScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var azimuth by remember { mutableStateOf(0f) }
    DisposableEffect(Unit) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ORIENTATION)
        val listener = object : SensorEventListener { override fun onSensorChanged(event: SensorEvent?) { event?.let { azimuth = it.values[0] } }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {} }
        sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        onDispose { sensorManager.unregisterListener(listener) }
    }
    Column(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(DeepEmerald, Color(0xFF022C22)))), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(modifier = Modifier.fillMaxWidth().padding(16.dp)) { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBackIosNew, null, tint = Color.White) }
            Text("Kompas Kiblat", modifier = Modifier.fillMaxWidth().padding(top = 10.dp), textAlign = TextAlign.Center, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
        Spacer(modifier = Modifier.height(40.dp))
        val composition by rememberLottieComposition(LottieCompositionSpec.Url("https://assets10.lottiefiles.com/packages/lf20_0pcyf1m7.json"))
        LottieAnimation(composition = composition, iterations = LottieConstants.IterateForever, modifier = Modifier.size(150.dp).graphicsLayer(alpha = 0.3f))
        Box(contentAlignment = Alignment.Center) {
            Box(modifier = Modifier.size(320.dp).border(2.dp, Color.White.copy(alpha = 0.2f), CircleShape))
            Box(modifier = Modifier.size(280.dp).rotate(-azimuth)) {
                Canvas(modifier = Modifier.fillMaxSize()) { drawCircle(Color.White.copy(alpha = 0.1f), radius = size.width/2) }
                Text("N", modifier = Modifier.align(Alignment.TopCenter).padding(10.dp), color = Color.White, fontWeight = FontWeight.Bold)
                Text("S", modifier = Modifier.align(Alignment.BottomCenter).padding(10.dp), color = Color.White, fontWeight = FontWeight.Bold)
                Text("E", modifier = Modifier.align(Alignment.CenterEnd).padding(10.dp), color = Color.White, fontWeight = FontWeight.Bold)
                Text("W", modifier = Modifier.align(Alignment.CenterStart).padding(10.dp), color = Color.White, fontWeight = FontWeight.Bold)
            }
            Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(60.dp).rotate(0f).offset(y = (-110).dp), tint = AmberGold)
            Box(modifier = Modifier.size(180.dp).rotate(-azimuth)) { Canvas(modifier = Modifier.fillMaxSize()) { val center = Offset(size.width/2, size.height/2)
                val path = Path().apply { moveTo(center.x, center.y - 80.dp.toPx()); lineTo(center.x - 10.dp.toPx(), center.y); lineTo(center.x + 10.dp.toPx(), center.y); close() }
                drawPath(path, Color.White) } }
        }
        Spacer(modifier = Modifier.height(60.dp))
        Card(modifier = Modifier.padding(horizontal = 40.dp), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.1f))) {
            Text("Arahkan jarum emas ke titik atas untuk menemukan arah Ka'bah", modifier = Modifier.padding(20.dp), color = EmeraldLight, textAlign = TextAlign.Center, fontSize = 14.sp) }
    }
}

@Composable
fun TasbihScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var count by remember { mutableIntStateOf(0) }
    var target by remember { mutableIntStateOf(33) }
    val scale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    fun vibrate() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator }
        else { @Suppress("DEPRECATION") context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) { vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE)) }
        else { @Suppress("DEPRECATION") vibrator.vibrate(50) }
    }
    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBackIosNew, null) }
            Text("Tasbih Digital", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            IconButton(onClick = { count = 0 }) { Icon(Icons.Default.Refresh, null, tint = EmeraldMain) }
        }
        Spacer(modifier = Modifier.height(40.dp))
        Row(modifier = Modifier.background(MaterialTheme.colorScheme.surface, RoundedCornerShape(24.dp)).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(33, 99, 100).forEach { valItem ->
                val isSelected = target == valItem
                Box(modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(if (isSelected) EmeraldMain else Color.Transparent).clickable { target = valItem }.padding(horizontal = 24.dp, vertical = 8.dp)) {
                    Text(valItem.toString(), color = if (isSelected) Color.White else TextGray, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(count.toString(), fontSize = 100.sp, fontWeight = FontWeight.Black, color = EmeraldMain); Text("dari $target", fontSize = 20.sp, color = TextGray) }
        Spacer(modifier = Modifier.weight(1f))
        Box(modifier = Modifier.size(240.dp).graphicsLayer(scaleX = scale.value, scaleY = scale.value).shadow(12.dp, CircleShape).background(Brush.radialGradient(listOf(EmeraldMain, DeepEmerald)), CircleShape).clickable {
            count++; vibrate(); scope.launch { scale.animateTo(1.1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy)); scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy)) }
        }, contentAlignment = Alignment.Center) { Icon(Icons.Default.TouchApp, null, tint = Color.White, modifier = Modifier.size(64.dp)) }
        Spacer(modifier = Modifier.height(80.dp))
    }
}
