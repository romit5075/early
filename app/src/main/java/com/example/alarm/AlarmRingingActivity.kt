package com.example.alarm

import android.Manifest
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.WakeApplication
import com.example.data.model.Alarm
import com.example.data.model.WakeRecord
import com.example.ui.screens.CameraScanningView
import com.example.ui.theme.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class AlarmRingingActivity : ComponentActivity() {

    private var alarmId = -1
    private var alarm: Alarm? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        alarmId = intent.getIntExtra("ALARM_ID", -1)

        // Bypass security barriers: Turn screen on, show even when locked
        configureLockScreenTakeover()

        // Fetch alarm specs
        val app = applicationContext as WakeApplication
        CoroutineScope(Dispatchers.IO).launch {
            alarm = app.repository.getAlarmById(alarmId)
        }

        setContent {
            MyApplicationTheme {
                ActiveAlarmScreen(
                    alarm = alarm,
                    onDismissSuccess = {
                        stopService(Intent(this@AlarmRingingActivity, AlarmService::class.java))
                        finish()
                    }
                )
            }
        }
    }

    private fun configureLockScreenTakeover() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                        or WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                        or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    // INTERCEPT VOLUME KEYS (Anti-Cheat Measure)
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            Log.w("RingingActivity", "Volume keys intercepted! Maximizing stream levels.")
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxVolume, 0)
            return true // Consume key, do not change level
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            return true
        }
        return super.onKeyUp(keyCode, event)
    }
}

// --- COMPOSE RENDER SECTION FOR ACTIVE RINGING ---
@Composable
fun ActiveAlarmScreen(
    alarm: Alarm?,
    onDismissSuccess: () -> Unit
) {
    var isScanningMode by remember { mutableStateOf(false) }
    var ringState by remember { mutableStateOf("ringing") } // ringing, scanning, success
    var cameraPermissionGranted by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        cameraPermissionGranted = granted
    }

    // Track state timer ticking
    var timeTicks by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        while (true) {
            val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
            timeTicks = sdf.format(Date())
            delay(1000L)
        }
    }

    // Verify Camera permissions when jumping to Scanner
    fun triggerScanner() {
        val perm = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
        if (perm == PackageManager.PERMISSION_GRANTED) {
            cameraPermissionGranted = true
            ringState = "scanning"
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
    ) {
        when (ringState) {
            "ringing" -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Spacer(modifier = Modifier.height(32.dp))

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Filled.Alarm,
                            contentDescription = "Ringing Icon",
                            tint = ErrorColor,
                            modifier = Modifier
                                .size(80.dp)
                                .border(BorderStroke(2.dp, ErrorColor), CircleShape)
                                .padding(12.dp)
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        Text(
                            text = timeTicks,
                            fontSize = 64.sp,
                            fontWeight = FontWeight.Black,
                            color = TextPrimary,
                            fontFamily = FontFamily.Monospace,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = alarm?.label?.uppercase() ?: "WAKE UP IMMEDIATELY!",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = SoftOrange,
                            textAlign = TextAlign.Center
                        )

                        Text(
                            text = "Physical scan is required to dismiss sound.",
                            color = TextSecondary,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }

                    // PULSING SCAN LAUNCHER BUTTON
                    Button(
                        onClick = { triggerScanner() },
                        colors = ButtonDefaults.buttonColors(containerColor = ErrorColor),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                    ) {
                        Icon(Icons.Filled.PhotoCamera, null, tint = Color.White)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            "SCAN QR TO STOP ALARM",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 16.sp,
                            color = Color.White
                        )
                    }
                }
            }

            "scanning" -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { ringState = "ringing" },
                            colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceCard)
                        ) {
                            Text("← Return", color = TextPrimary)
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Text("Searching registered trigger", fontWeight = FontWeight.Bold, color = SoftOrange)
                    }

                    if (cameraPermissionGranted) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .border(3.dp, AmberOrange, RoundedCornerShape(16.dp))
                        ) {
                            CameraScanningView(
                                onBarcodeFound = { barcode ->
                                    Log.d("ScanningAlarm", "Scanned: $barcode target: ${alarm?.assignedQrCode}")
                                    // Target matching
                                    val isMatch = if (alarm != null && alarm.assignedQrCode.isNotEmpty()) {
                                        barcode == alarm.assignedQrCode
                                    } else {
                                        // Default: Any QR code or product works
                                        true
                                    }

                                    if (isMatch) {
                                        // Register succesful wake record in room DB
                                        val app = context.applicationContext as WakeApplication
                                        val repo = app.repository
                                        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                                        val timeFormatter = SimpleDateFormat("hh:mm a", Locale.getDefault())

                                        coroutineScope.launch(Dispatchers.IO) {
                                            repo.insertWakeRecord(
                                                WakeRecord(
                                                    dateStr = formatter.format(Date()),
                                                    wakeTime = timeFormatter.format(Date()),
                                                    status = "ON_TIME"
                                                )
                                            )
                                            // Trigger success visual state
                                            ringState = "success"
                                        }
                                    } else {
                                        coroutineScope.launch(Dispatchers.Main) {
                                            Toast.makeText(context, "Wrong Code Scan! Seek your scheduled location.", Toast.LENGTH_LONG).show()
                                            // Increase loudness on wrong action
                                            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                                            val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
                                            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, max, 0)
                                        }
                                    }
                                }
                            )

                            // Overlay Box instructions
                            Box(
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .size(240.dp)
                                    .border(2.dp, TealSuccess, RoundedCornerShape(20.dp))
                            )

                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .background(Color.Black.copy(alpha = 0.8f))
                                    .padding(12.dp)
                            ) {
                                Text(
                                    text = if (alarm != null && alarm.assignedQrCode.isNotEmpty()) {
                                        "Target: Scan physical QR/Barcode configured for this alarm."
                                    } else {
                                        "Scan any product barcode or generated QR in bathroom/kitchen."
                                    },
                                    color = TextPrimary,
                                    fontSize = 13.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Permissions missing", color = ErrorColor)
                        }
                    }
                }
            }

            "success" -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                        .background(DarkBg),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = "Success",
                        tint = TealSuccess,
                        modifier = Modifier.size(100.dp)
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        "Good Morning! You did it! 🎉",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black,
                        color = TextPrimary,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        "You successfully got out of bed and scanned the target. Your brain is officially active now!",
                        fontSize = 15.sp,
                        color = TextSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    Button(
                        onClick = onDismissSuccess,
                        colors = ButtonDefaults.buttonColors(containerColor = TealSuccess),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(50.dp)
                    ) {
                        Text("LET'S GO", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            }
        }
    }
}
