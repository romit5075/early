package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.data.model.Alarm
import com.example.data.model.QrCode
import com.example.data.model.WakeRecord
import com.example.ui.theme.*
import com.example.ui.viewmodel.MainViewModel
import com.example.utils.QrGenerator
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors

// --- MAIN ENTRANCE NAVIGATION ---
fun getNextAlarmTimeRemaining(alarms: List<Alarm>): String {
    val activeAlarms = alarms.filter { it.isEnabled }
    if (activeAlarms.isEmpty()) return "No alarms active"
    
    val now = Calendar.getInstance()
    var minDiff = Long.MAX_VALUE
    
    for (alarm in activeAlarms) {
        val triggerTime = com.example.alarm.AlarmScheduler.calculateNextTriggerTime(alarm)
        val diff = triggerTime - now.timeInMillis
        if (diff in 0 until minDiff) {
            minDiff = diff
        }
    }
    
    if (minDiff == Long.MAX_VALUE) return "No active alarms"
    
    val totalMins = minDiff / (1000 * 60)
    val hours = totalMins / 60
    val mins = totalMins % 60
    
    return when {
        hours > 0 -> "In $hours hr${if (hours > 1) "s" else ""}, $mins min${if (mins != 1L) "s" else ""}"
        else -> "In $mins min${if (mins != 1L) "s" else ""}"
    }
}

@Composable
fun NextAlarmCard(
    alarms: List<Alarm>,
    onUpgradeClick: () -> Unit,
    isPremium: Boolean
) {
    val remainingTime = remember(alarms) { getNextAlarmTimeRemaining(alarms) }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp)
            .clickable { if (!isPremium) onUpgradeClick() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = BorderStroke(1.dp, Color(0xFF422C11))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(Color(0xFF2A1D10), Color(0xFF1A1A1A))
                    )
                )
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(AmberOrange),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.AccessTime,
                            contentDescription = "Next Alarm Icon",
                            tint = Color.Black,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "NEXT ALARM",
                            style = androidx.compose.ui.text.TextStyle(
                                color = SoftOrange,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.5.sp
                            )
                        )
                        Text(
                            text = remainingTime,
                            style = androidx.compose.ui.text.TextStyle(
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }
                }
                
                if (!isPremium) {
                    Text(
                        "Go Pro Key",
                        color = AmberOrange,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .border(1.dp, AmberOrange, RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun MainAppScreen(viewModel: MainViewModel) {
    var currentTab by remember { mutableStateOf("alarms") }
    val isPremium by viewModel.isPremium.collectAsState()
    var showUpgradeDialog by remember { mutableStateOf(false) }
    val currentStreak by viewModel.streakFlow.collectAsState()
    val alarms by viewModel.alarms.collectAsState()

    val context = LocalContext.current
    val status by viewModel.operationStatus.collectAsState()

    LaunchedEffect(status) {
        status?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearStatus()
        }
    }

    Scaffold(
        bottomBar = {
            Column(modifier = Modifier.background(DarkSurface)) {
                HorizontalDivider(color = WhiteBorderAlpha, thickness = 1.dp)
                NavigationBar(
                    containerColor = DarkSurface,
                    tonalElevation = 0.dp,
                    modifier = Modifier.navigationBarsPadding()
                ) {
                    NavigationBarItem(
                        selected = currentTab == "alarms",
                        onClick = { currentTab = "alarms" },
                        icon = { Icon(Icons.Filled.Alarm, "Alarms") },
                        label = { Text("ALARMS", style = androidx.compose.ui.text.TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Bold)) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = AmberOrange,
                            selectedTextColor = AmberOrange,
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary,
                            indicatorColor = Color(0xFF3A2A1A)
                        )
                    )
                    NavigationBarItem(
                        selected = currentTab == "qr_setup",
                        onClick = { currentTab = "qr_setup" },
                        icon = { Icon(Icons.Filled.QrCodeScanner, "QR Setup") },
                        label = { Text("QR SETUP", style = androidx.compose.ui.text.TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Bold)) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = AmberOrange,
                            selectedTextColor = AmberOrange,
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary,
                            indicatorColor = Color(0xFF3A2A1A)
                        )
                    )
                    NavigationBarItem(
                        selected = currentTab == "stats",
                        onClick = { currentTab = "stats" },
                        icon = { Icon(Icons.Filled.TrendingUp, "Stats") },
                        label = { Text("STATS", style = androidx.compose.ui.text.TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Bold)) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = AmberOrange,
                            selectedTextColor = AmberOrange,
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary,
                            indicatorColor = Color(0xFF3A2A1A)
                        )
                    )
                }
            }
        },
        containerColor = DarkBg
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // PREMIUM BANNER AD TRIGGER
                HeaderBar(
                    isPremium = isPremium,
                    onTogglePremium = { viewModel.setPremium(it) },
                    onUpgradeClick = { showUpgradeDialog = true },
                    currentStreak = currentStreak
                )

                // ADS SIMULATOR FOR FREE USERS
                if (!isPremium) {
                    SimulatedAdBanner()
                }

                Box(modifier = Modifier.weight(1f)) {
                    when (currentTab) {
                        "alarms" -> AlarmsListScreen(
                            viewModel = viewModel,
                            onUpgradeRequired = { showUpgradeDialog = true }
                        )
                        "qr_setup" -> QrSetupScreen(
                            viewModel = viewModel,
                            onUpgradeRequired = { showUpgradeDialog = true }
                        )
                        "stats" -> StatsScreen(
                            viewModel = viewModel
                        )
                    }
                }
            }

            if (showUpgradeDialog) {
                UpgradePremiumDialog(
                    onDismiss = { showUpgradeDialog = false },
                    onConfirmUpgrade = {
                        viewModel.setPremium(true)
                        showUpgradeDialog = false
                        Toast.makeText(context, "Welcome to QR Wake PREMIUM! 🎉", Toast.LENGTH_LONG).show()
                    }
                )
            }
        }
    }
}

@Composable
fun HeaderBar(
    isPremium: Boolean,
    onTogglePremium: (Boolean) -> Unit,
    onUpgradeClick: () -> Unit,
    currentStreak: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkBg)
            .padding(top = 24.dp, start = 24.dp, end = 24.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(
                text = "Alarms",
                fontSize = 30.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                letterSpacing = (-0.5).sp
            )
            Text(
                text = "$currentStreak Day Streak 🔥",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = SoftOrange
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!isPremium) {
                Button(
                    onClick = onUpgradeClick,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A2A2A)),
                    border = BorderStroke(1.dp, Color(0xFF333333)),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.padding(end = 12.dp)
                ) {
                    Icon(Icons.Filled.Star, "Go premium", tint = AmberOrange, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Go Pro", color = AmberOrange, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF2A2A2A))
                    .border(1.dp, Color(0xFF383838), CircleShape)
                    .clickable { onTogglePremium(!isPremium) }
                    .testTag("premium_dev_toggle"),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(if (isPremium) TealSuccess.copy(alpha = 0.8f) else AmberOrange.copy(alpha = 0.8f))
                )
            }
        }
    }
}

@Composable
fun SimulatedAdBanner() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp)
            .border(1.dp, Color(0xFF422C11), RoundedCornerShape(16.dp))
            .background(Color(0xFF2A1D10), RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .clickable { },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text("SPONSORED AD", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = SoftOrange, letterSpacing = 1.sp)
            Text("Smash tiredness! Coffee promo 50% discount", fontSize = 13.sp, color = Color.White)
        }
        Icon(Icons.Filled.ArrowForward, contentDescription = "Ad", tint = AmberOrange)
    }
}

// --- ALARMS LIST / HOME SCREEN ---
@Composable
fun AlarmsListScreen(
    viewModel: MainViewModel,
    onUpgradeRequired: () -> Unit
) {
    val alarms by viewModel.alarms.collectAsState()
    var showAddScreen by remember { mutableStateOf(false) }
    var selectedAlarmForEdit by remember { mutableStateOf<Alarm?>(null) }

    if (showAddScreen) {
        AddEditAlarmScreen(
            viewModel = viewModel,
            alarmToEdit = selectedAlarmForEdit,
            onDismiss = {
                showAddScreen = false
                selectedAlarmForEdit = null
            },
            onUpgradeRequired = onUpgradeRequired
        )
    } else {
        Box(modifier = Modifier.fillMaxSize()) {
            if (alarms.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Outlined.Alarm,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(72.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "No wake tasks yet",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Alarms here will lock your phone until you scan your physical QR code trigger in another room.",
                        textAlign = TextAlign.Center,
                        fontSize = 14.sp,
                        color = TextSecondary
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        NextAlarmCard(
                            alarms = alarms,
                            onUpgradeClick = onUpgradeRequired,
                            isPremium = viewModel.isPremium.value
                        )
                    }

                    item {
                        Text(
                            "My Scheduled Morning Wakes",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = SoftOrange,
                            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 4.dp)
                        )
                    }

                    items(alarms) { alarm ->
                        Box(modifier = Modifier.padding(horizontal = 24.dp)) {
                            AlarmCard(
                                alarm = alarm,
                                onToggle = { enabled -> viewModel.toggleAlarm(alarm, enabled) },
                                onClick = {
                                    selectedAlarmForEdit = alarm
                                    showAddScreen = true
                                },
                                onDelete = { viewModel.deleteAlarm(alarm) }
                            )
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(80.dp))
                    }
                }
            }

            FloatingActionButton(
                onClick = {
                    selectedAlarmForEdit = null
                    showAddScreen = true
                },
                containerColor = AmberOrange,
                contentColor = Color.Black,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(24.dp)
                    .testTag("add_alarm_fab")
            ) {
                Icon(Icons.Filled.Add, "Add Alarm", modifier = Modifier.size(28.dp))
            }
        }
    }
}

@Composable
fun AlarmCard(
    alarm: Alarm,
    onToggle: (Boolean) -> Unit,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("alarm_card_${alarm.id}"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard),
        border = BorderStroke(1.dp, WhiteBorderAlpha)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.Start
                ) {
                    val hour12 = if (alarm.hour % 12 == 0) 12 else alarm.hour % 12
                    val amPm = if (alarm.hour >= 12) "PM" else "AM"
                    Text(
                        text = String.format(Locale.getDefault(), "%02d:%02d", hour12, alarm.minute),
                        fontSize = 38.sp,
                        fontWeight = FontWeight.Light,
                        color = if (alarm.isEnabled) Color.White else Color.White.copy(alpha = 0.4f),
                        letterSpacing = (-0.5).sp
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = amPm,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (alarm.isEnabled) Color.White.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.3f),
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                val repeatDaysText = if (alarm.days.isNotEmpty()) {
                    " • " + alarm.days.split(",").joinToString(", ")
                } else {
                    ""
                }
                Text(
                    text = "${alarm.label.ifEmpty { "Wake Alert" }}$repeatDaysText",
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.5f),
                    fontWeight = FontWeight.Normal
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val qrLabel = if (alarm.assignedQrCode.isNotEmpty()) {
                        "QR: ACTIVE"
                    } else {
                        "ANY QR CODE"
                    }
                    Box(
                        modifier = Modifier
                            .background(Color(0xFF2A2A2A), RoundedCornerShape(100.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(if (alarm.isEnabled) AmberOrange else Color.White.copy(alpha = 0.3f))
                            )
                            Text(
                                text = qrLabel,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (alarm.isEnabled) Color.White.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.4f),
                                letterSpacing = 1.sp
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .background(Color(0xFF2A2A2A), RoundedCornerShape(100.dp))
                            .clickable { onDelete() }
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Filled.Delete, null, tint = ErrorColor, modifier = Modifier.size(10.dp))
                            Text(
                                text = "DELETE",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = ErrorColor
                            )
                        }
                    }
                }
            }

            Switch(
                checked = alarm.isEnabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.Black,
                    checkedTrackColor = AmberOrange,
                    uncheckedThumbColor = Color(0xFF666666),
                    uncheckedTrackColor = Color(0xFF333333),
                    uncheckedBorderColor = Color.Transparent
                )
            )
        }
    }
}

// --- ADD OR EDIT SCREEN ---
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddEditAlarmScreen(
    viewModel: MainViewModel,
    alarmToEdit: Alarm?,
    onDismiss: () -> Unit,
    onUpgradeRequired: () -> Unit
) {
    var hour by remember { mutableStateOf(alarmToEdit?.hour ?: 7) }
    var minute by remember { mutableStateOf(alarmToEdit?.minute ?: 0) }
    var label by remember { mutableStateOf(alarmToEdit?.label ?: "") }
    var snoozeEnabled by remember { mutableStateOf(alarmToEdit?.isSnoozeEnabled ?: false) }
    var selectedSound by remember { mutableStateOf(alarmToEdit?.soundUri ?: "default_sound_1") }
    var isPm by remember { mutableStateOf(hour >= 12) }

    // Convert days list
    var selectedDays by remember {
        mutableStateOf(
            alarmToEdit?.days?.split(",")?.filter { it.isNotEmpty() }?.toSet() ?: emptySet()
        )
    }

    val qrCodes by viewModel.qrCodes.collectAsState()
    var assignedQr by remember { mutableStateOf(alarmToEdit?.assignedQrCode ?: "") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .background(DarkBg)
            .padding(16.dp)
    ) {
        // TOP BACK BAR
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onDismiss) {
                Icon(Icons.Filled.ArrowBack, "Back", tint = TextPrimary)
            }
            Text(
                text = if (alarmToEdit == null) "New Wake Task" else "Update Wake Task",
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = TextPrimary
            )
            Button(
                onClick = {
                    val finalHour = when {
                        isPm && hour < 12 -> hour + 12
                        !isPm && hour == 12 -> 0
                        isPm && hour == 12 -> 12
                        !isPm -> hour
                        else -> hour
                    }
                    viewModel.saveAlarm(
                        id = alarmToEdit?.id ?: 0,
                        hour = finalHour,
                        minute = minute,
                        label = label,
                        days = selectedDays,
                        isSnoozeEnabled = snoozeEnabled,
                        soundUri = selectedSound,
                        assignedQrCode = assignedQr,
                        onLimitExceeded = onUpgradeRequired,
                        onSuccess = onDismiss
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = AmberOrange)
            ) {
                Text("SAVE", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // DESIGN CIRCULAR VALUE SELECTOR
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Set Awakening Time",
                    color = TextSecondary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Elegant circular aesthetic representation of analog selections
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Hour Dial Sliders
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                        Text(
                            text = String.format(Locale.getDefault(), "%02d", if (hour == 0 || hour == 12) 12 else hour % 12),
                            fontSize = 64.sp,
                            color = AmberOrange,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Slider(
                            value = (if (hour % 12 == 0) 12 else hour % 12).toFloat(),
                            onValueChange = { hour = it.toInt() },
                            valueRange = 1f..12f,
                            steps = 10,
                            colors = SliderDefaults.colors(
                                thumbColor = AmberOrange,
                                activeTrackColor = AmberOrange,
                                inactiveTrackColor = DarkSurface
                            )
                        )
                        Text("Hour", color = TextSecondary, fontSize = 11.sp)
                    }

                    Text(":", fontSize = 48.sp, fontWeight = FontWeight.Bold, color = TextPrimary, modifier = Modifier.padding(bottom = 20.dp))

                    // Minute Dial Slider
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                        Text(
                            text = String.format(Locale.getDefault(), "%02d", minute),
                            fontSize = 64.sp,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Slider(
                            value = minute.toFloat(),
                            onValueChange = { minute = it.toInt() },
                            valueRange = 0f..59f,
                            colors = SliderDefaults.colors(
                                thumbColor = TextPrimary,
                                activeTrackColor = TextPrimary,
                                inactiveTrackColor = DarkSurface
                            )
                        )
                        Text("Minute", color = TextSecondary, fontSize = 11.sp)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // AMPM Toggle
                Row(
                    modifier = Modifier
                        .background(DarkSurface, CircleShape)
                        .padding(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (!isPm) AmberOrange else Color.Transparent)
                            .clickable { isPm = false }
                            .padding(horizontal = 24.dp, vertical = 6.dp)
                    ) {
                        Text("AM", color = if (!isPm) Color.Black else TextSecondary, fontWeight = FontWeight.Bold)
                    }
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (isPm) AmberOrange else Color.Transparent)
                            .clickable { isPm = true }
                            .padding(horizontal = 24.dp, vertical = 6.dp)
                    ) {
                        Text("PM", color = if (isPm) Color.Black else TextSecondary, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // LABELLING INPUT
        Text("Wake Label", fontWeight = FontWeight.SemiBold, color = SoftOrange, fontSize = 14.sp)
        Spacer(modifier = Modifier.height(6.dp))
        OutlinedTextField(
            value = label,
            onValueChange = { label = it },
            placeholder = { Text("Gym time! No excuses", color = TextSecondary) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("add_alarm_label"),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                focusedBorderColor = AmberOrange,
                unfocusedBorderColor = DarkSurfaceCard,
                focusedContainerColor = DarkSurfaceCard,
                unfocusedContainerColor = DarkSurfaceCard
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        // DAYS OF WEEK SELECTOR CHIPS
        Text("Repeat Days", fontWeight = FontWeight.SemiBold, color = SoftOrange, fontSize = 14.sp)
        Spacer(modifier = Modifier.height(8.dp))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun").forEach { day ->
                val selected = selectedDays.contains(day)
                FilterChip(
                    selected = selected,
                    onClick = {
                        selectedDays = if (selected) {
                            selectedDays - day
                        } else {
                            selectedDays + day
                        }
                    },
                    label = { Text(day, fontWeight = FontWeight.Medium) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AmberOrange,
                        selectedLabelColor = Color.Black,
                        containerColor = DarkSurfaceCard,
                        labelColor = TextPrimary
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // QR CODE SELECTOR dropdown
        Text("Dismiss Trigger Device", fontWeight = FontWeight.SemiBold, color = SoftOrange, fontSize = 14.sp)
        Spacer(modifier = Modifier.height(6.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard),
            shape = RoundedCornerShape(12.dp)
        ) {
            var expandedDropdown by remember { mutableStateOf(false) }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expandedDropdown = true }
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.QrCode, null, tint = AmberOrange)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = if (assignedQr.isEmpty()) "Any QR or Barcode stops alarm" else {
                            qrCodes.find { it.data == assignedQr }?.name ?: "Selected physical point"
                        },
                        color = TextPrimary
                    )
                }
                Icon(Icons.Filled.ArrowDropDown, null, tint = TextSecondary)
            }

            DropdownMenu(
                expanded = expandedDropdown,
                onDismissRequest = { expandedDropdown = false },
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .background(DarkSurface)
            ) {
                DropdownMenuItem(
                    text = { Text("Any physical scan (Easiest)", color = TextPrimary) },
                    onClick = {
                        assignedQr = ""
                        expandedDropdown = false
                    }
                )

                qrCodes.forEach { qr ->
                    DropdownMenuItem(
                        text = { Text(qr.name, color = TextPrimary) },
                        onClick = {
                            assignedQr = qr.data
                            expandedDropdown = false
                        }
                    )
                }

                if (qrCodes.isEmpty()) {
                    DropdownMenuItem(
                        text = { Text("⚠️ No physical triggers created. Go to QR Setup tab!", color = ErrorColor) },
                        onClick = { expandedDropdown = false }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // SNOOZE OPTION (Locked/Limited by design to prevent cheat)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(DarkSurfaceCard, RoundedCornerShape(12.dp))
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Allow Snooze (Failsafe)", fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Text("Highly restricted: 1 time only, 2 mins max", fontSize = 12.sp, color = TextSecondary)
            }
            Switch(
                checked = snoozeEnabled,
                onCheckedChange = { snoozeEnabled = it },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.Black,
                    checkedTrackColor = AmberOrange,
                    uncheckedThumbColor = TextSecondary,
                    uncheckedTrackColor = DarkBg
                )
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // SOUND SELECTOR ROW
        Text(" despertar Sound", fontWeight = FontWeight.SemiBold, color = SoftOrange, fontSize = 14.sp)
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("default_sound_1" to "Energetic Sirens", "sound_2" to "Gentle Bell").forEach { (id, name) ->
                val selected = selectedSound == id
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selected) AmberOrange else DarkSurfaceCard)
                        .clickable { selectedSound = id }
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(name, color = if (selected) Color.Black else TextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

// --- QR SETUP SCREEN ---
@Composable
fun QrSetupScreen(
    viewModel: MainViewModel,
    onUpgradeRequired: () -> Unit
) {
    val qrCodes by viewModel.qrCodes.collectAsState()
    var selectedTab by remember { mutableStateOf("generate") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
            .padding(16.dp)
    ) {
        Text(
            "Configure Physical Wake Triggers",
            style = MaterialTheme.typography.titleLarge,
            color = TextPrimary,
            fontWeight = FontWeight.Bold
        )
        Text(
            "Register items in different rooms to force you out of bed.",
            fontSize = 13.sp,
            color = TextSecondary
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Horizontal tabs
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(DarkSurface, RoundedCornerShape(12.dp))
                .padding(4.dp)
        ) {
            Button(
                onClick = { selectedTab = "generate" },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selectedTab == "generate") AmberOrange else Color.Transparent,
                    contentColor = if (selectedTab == "generate") Color.Black else TextSecondary
                ),
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Generate QR Pattern", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
            Button(
                onClick = { selectedTab = "scan" },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selectedTab == "scan") AmberOrange else Color.Transparent,
                    contentColor = if (selectedTab == "scan") Color.Black else TextSecondary
                ),
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Register Product Barcode", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Box(modifier = Modifier.weight(1f)) {
            when (selectedTab) {
                "generate" -> GenerateQrPanel(viewModel, onUpgradeRequired)
                "scan" -> ScanRegisterBarcodePanel(viewModel, onUpgradeRequired)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // PLACEMENT TIPS EXPANSION BOX
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, WhiteBorderAlpha)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Lightbulb, null, tint = AmberOrange)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Top Placing Tips", fontWeight = FontWeight.Bold, color = TextPrimary)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text("🚿 Bathroom Mirror: Print generated QR & tape it. Wash your face straight away!", fontSize = 12.sp, color = TextSecondary)
                Spacer(modifier = Modifier.height(4.dp))
                Text("☕ Kitchen Coffee Machine: Use the barcode scanner on toothpaste or shampoo product barcodes!", fontSize = 12.sp, color = TextSecondary)
                Spacer(modifier = Modifier.height(4.dp))
                Text("🚪 Front Door: Register a fridge magnet or main door trigger preventing sleep fallback.", fontSize = 12.sp, color = TextSecondary)
            }
        }
    }
}

@Composable
fun GenerateQrPanel(viewModel: MainViewModel, onUpgradeRequired: () -> Unit) {
    var qrTextLabel by remember { mutableStateOf("") }
    var qrTriggerValue by remember { mutableStateOf("") }
    var generatedBitmap by remember { mutableStateOf<Bitmap?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Construct a Print-Out QR Code", fontWeight = FontWeight.Bold, color = SoftOrange)
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = qrTextLabel,
                    onValueChange = { qrTextLabel = it },
                    label = { Text("Placement Label (e.g. Bathroom Mirror)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        focusedBorderColor = AmberOrange,
                        unfocusedBorderColor = DarkSurface
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        val uniqueCode = "QRWAKE_${System.currentTimeMillis()}"
                        qrTriggerValue = uniqueCode
                        generatedBitmap = QrGenerator.generate(uniqueCode, 350)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AmberOrange),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("GENERATE PATTERN", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        animatedVisibility(visible = generatedBitmap != null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Card(
                    modifier = Modifier
                        .size(240.dp)
                        .padding(8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    generatedBitmap?.let { bitmap ->
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Generated QR Code",
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        if (generatedBitmap != null && qrTriggerValue.isNotEmpty()) {
                            viewModel.registerQrCode(
                                name = qrTextLabel,
                                data = qrTriggerValue,
                                isGenerated = true,
                                onLimitExceeded = onUpgradeRequired,
                                onSuccess = {
                                    qrTextLabel = ""
                                    qrTriggerValue = ""
                                    generatedBitmap = null
                                }
                            )
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TealSuccess)
                ) {
                    Icon(Icons.Filled.CloudDone, null, tint = Color.Black)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("REGISTER THIS PHYSICAL TRIGGER", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun ScanRegisterBarcodePanel(viewModel: MainViewModel, onUpgradeRequired: () -> Unit) {
    var hasCameraPermission by remember { mutableStateOf(false) }
    var scannedBarcodeResult by remember { mutableStateOf<String?>(null) }
    var labelText by remember { mutableStateOf("") }

    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(Unit) {
        val permission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
        if (permission == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            hasCameraPermission = true
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (scannedBarcodeResult == null) {
            if (hasCameraPermission) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .border(2.dp, AmberOrange, RoundedCornerShape(16.dp))
                ) {
                    CameraScanningView(
                        onBarcodeFound = { barcode ->
                            scannedBarcodeResult = barcode
                            Log.d("ScanRegister", "Found physical product barcode: $barcode")
                        }
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .background(Color.Black.copy(alpha = 0.7f))
                            .padding(8.dp)
                    ) {
                        Text("Point camera at any toothpaste, bottle, or barcode", color = TextPrimary, fontSize = 11.sp)
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(DarkSurfaceCard),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Camera permission is required to register existing products", color = TextSecondary, textAlign = TextAlign.Center)
                }
            }
        } else {
            // Setup naming
            Card(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Filled.FactCheck, null, modifier = Modifier.size(64.dp), tint = TealSuccess)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Barcode Identified", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text("Value: ${scannedBarcodeResult ?: ""}", fontSize = 12.sp, color = TextSecondary)

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = labelText,
                        onValueChange = { labelText = it },
                        label = { Text("Name of product (e.g. Toothbrush Holder/Fridgemagnet)") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            focusedBorderColor = AmberOrange,
                            unfocusedBorderColor = DarkSurface
                        )
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = { scannedBarcodeResult = null },
                            colors = ButtonDefaults.buttonColors(containerColor = ErrorColor)
                        ) {
                            Text("RE-SCAN")
                        }

                        Button(
                            onClick = {
                                scannedBarcodeResult?.let { value ->
                                    viewModel.registerQrCode(
                                        name = labelText,
                                        data = value,
                                        isGenerated = false,
                                        onLimitExceeded = onUpgradeRequired,
                                        onSuccess = {
                                            labelText = ""
                                            scannedBarcodeResult = null
                                        }
                                    )
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = TealSuccess)
                        ) {
                            Text("SAVE TRIGGER")
                        }
                    }
                }
            }
        }
    }
}

// --- COMPOSE CAMERAX VIEW INTEGRATION ---
@Composable
fun CameraScanningView(onBarcodeFound: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val executor = remember { Executors.newSingleThreadExecutor() }
    val scanner = remember { BarcodeScanning.getClient() }

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }

            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder().build().apply {
                    setSurfaceProvider(previewView.surfaceProvider)
                }

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                imageAnalysis.setAnalyzer(executor) { imageProxy ->
                    val mediaImage = imageProxy.image
                    if (mediaImage != null) {
                        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                        scanner.process(image)
                            .addOnSuccessListener { barcodes ->
                                for (barcode in barcodes) {
                                    val code = barcode.rawValue ?: continue
                                    onBarcodeFound(code)
                                    break
                                }
                            }
                            .addOnFailureListener {
                                Log.e("CameraScanningView", "Failed parsing barcode", it)
                            }
                            .addOnCompleteListener {
                                imageProxy.close()
                            }
                    } else {
                        imageProxy.close()
                    }
                }

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageAnalysis
                    )
                } catch (e: Exception) {
                    Log.e("CameraScanningView", "Error binding lifecycles", e)
                }
            }, ContextCompat.getMainExecutor(ctx))

            previewView
        },
        modifier = Modifier.fillMaxSize()
    )
}

// --- STATS / STREAK DASHBOARD SCREEN ---
@Composable
fun StatsScreen(viewModel: MainViewModel) {
    val records by viewModel.wakeRecords.collectAsState()
    val currentStreak by viewModel.streakFlow.collectAsState()
    val earliestWake by viewModel.earliestWakeTime.collectAsState()
    val completedMonth by viewModel.completedAlarmsMonth.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .background(DarkBg)
            .padding(16.dp)
    ) {
        // Streak Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, WhiteBorderAlpha)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Awakening Streak", fontWeight = FontWeight.Bold, color = TextSecondary)
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "$currentStreak days on time!",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black,
                        color = AmberOrange
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("🔥", fontSize = 28.sp)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text("Consistent waking reduces cognitive fog and boosts dopamine levels.", fontSize = 11.sp, color = TextSecondary, textAlign = TextAlign.Center)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Grid Stats Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, WhiteBorderAlpha)
            ) {
                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.Timeline, null, tint = SoftOrange)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Earliest record", fontSize = 12.sp, color = TextSecondary)
                    Text(earliestWake, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                }
            }

            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, WhiteBorderAlpha)
            ) {
                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.EmojiEvents, null, tint = TealSuccess)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Active Month", fontSize = 12.sp, color = TextSecondary)
                    Text("$completedMonth wakes", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Week Calendar Grid
        Text("Weekly Awakening Log", fontWeight = FontWeight.Bold, color = SoftOrange, fontSize = 15.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, WhiteBorderAlpha, RoundedCornerShape(16.dp))
                .background(DarkSurfaceCard, RoundedCornerShape(16.dp))
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val cal = Calendar.getInstance()

            // Display last 7 days calendar
            (0..6).reversed().forEach { offset ->
                val dayCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -offset) }
                val dateVal = sdf.format(dayCal.time)
                val dayRec = records.find { it.dateStr == dateVal }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    val label = when (dayCal.get(Calendar.DAY_OF_WEEK)) {
                        Calendar.MONDAY -> "M"
                        Calendar.TUESDAY -> "T"
                        Calendar.WEDNESDAY -> "W"
                        Calendar.THURSDAY -> "T"
                        Calendar.FRIDAY -> "F"
                        Calendar.SATURDAY -> "S"
                        else -> "S"
                    }
                    Text(label, color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)

                    Spacer(modifier = Modifier.height(8.dp))

                    // Green for Successful physical scan wake, Red for late or missed, Grey for none set
                    val bg = when (dayRec?.status) {
                        "ON_TIME" -> TealSuccess
                        "LATE" -> ErrorColor
                        "MISSED" -> ErrorColor
                        else -> DarkSurface
                    }

                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(bg),
                        contentAlignment = Alignment.Center
                    ) {
                        if (dayRec?.status == "ON_TIME") {
                            Icon(Icons.Filled.Check, null, tint = Color.Black, modifier = Modifier.size(16.dp))
                        } else if (dayRec != null) {
                            Icon(Icons.Filled.Close, null, tint = Color.White, modifier = Modifier.size(16.dp))
                        } else {
                            Text(
                                text = dayCal.get(Calendar.DAY_OF_MONTH).toString(),
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Daily Motivation Block
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF13110E)),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, SoftOrange.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "MORNING MOTIVATION",
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = AmberOrange
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = viewModel.dailyQuote,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

// --- UPGRADE PREMIUM DIALOG ---
@Composable
fun UpgradePremiumDialog(
    onDismiss: () -> Unit,
    onConfirmUpgrade: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Star, null, tint = AmberOrange, modifier = Modifier.size(32.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("QR Wake Premium", color = TextPrimary)
            }
        },
        text = {
            Column {
                Text("Crack sleep cycles and achieve absolute wake-up discipline! Unlock infinite alarm triggers and custom tracking.", color = TextSecondary)
                Spacer(modifier = Modifier.height(12.dp))
                Text("🔥 Unlimited alarms (Free: 2 max)", fontSize = 13.sp, color = TextPrimary)
                Text("🏷️ Multiple QR/barcode placements (Free: 1 max)", fontSize = 13.sp, color = TextPrimary)
                Text("🚫 Complete Ad-free awakening experience", fontSize = 13.sp, color = TextPrimary)
                Text("📈 Lifetime awakening statistics dashboard", fontSize = 13.sp, color = TextPrimary)
                Spacer(modifier = Modifier.height(16.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DarkSurface, RoundedCornerShape(8.dp))
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Pay One-Time: Rs. 99 ($1.19)", fontWeight = FontWeight.Bold, color = AmberOrange, fontSize = 18.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirmUpgrade,
                colors = ButtonDefaults.buttonColors(containerColor = AmberOrange)
            ) {
                Text("UPGRADE NOW", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("KEEP IT FREE", color = TextSecondary)
            }
        },
        containerColor = DarkSurfaceCard
    )
}

// Animated helper for custom entry animations
@Composable
fun animatedVisibility(
    visible: Boolean,
    content: @Composable () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically()
    ) {
        content()
    }
}
