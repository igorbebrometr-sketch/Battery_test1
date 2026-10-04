package com.example.batterytest

import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.BatteryManager
import android.os.Build
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.LocaleListCompat
import com.example.batterytest.ui.theme.BatteryTestTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

private data class BatteryInfo(
    val levelPercent: Int?,
    val cycleCount: Int?,
    val statusText: String,
    val isCharging: Boolean,
    val healthText: String,
    val voltageMv: Int?,
    val temperatureC: Float?
)

class MainActivity : AppCompatActivity() {
    private var gatekeeperAccepted = false

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState?.getBoolean("gatekeeper_accepted") == true) {
            gatekeeperAccepted = true
            initMainInterface()
        } else {
            showGatekeeper()
        }
    }

    override fun onSaveInstanceState(outState: android.os.Bundle) {
        outState.putBoolean("gatekeeper_accepted", gatekeeperAccepted)
        super.onSaveInstanceState(outState)
    }

    private fun showGatekeeper() {
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.gatekeeper_title)
            .setMessage(R.string.gatekeeper_message)
            .setCancelable(false)
            .setPositiveButton(R.string.yes) { _, _ ->
                gatekeeperAccepted = true
                initMainInterface()
            }
            .setNegativeButton(R.string.no) { _, _ -> finishAffinity() }
            .create()
        dialog.setCanceledOnTouchOutside(false)
        dialog.show()
    }

    private fun initMainInterface() {
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        val preferences = getSharedPreferences("battery_test_settings", MODE_PRIVATE)
        findViewById<ComposeView>(R.id.compose_view).setContent {
            var darkTheme by remember { mutableStateOf(preferences.getBoolean("dark_theme", false)) }
            var languageTag by remember {
                mutableStateOf(AppCompatDelegate.getApplicationLocales().toLanguageTags())
            }
            BatteryTestTheme(darkTheme = darkTheme) {
                BatteryScreen(
                    darkTheme = darkTheme,
                    languageTag = languageTag,
                    onOpenBatterySettings = ::openSamsungBatterySettings,
                    onLanguageChange = { selectedTag ->
                        languageTag = selectedTag
                        AppCompatDelegate.setApplicationLocales(
                            LocaleListCompat.forLanguageTags(selectedTag)
                        )
                    },
                    onDarkThemeChange = { enabled ->
                        darkTheme = enabled
                        preferences.edit().putBoolean("dark_theme", enabled).apply()
                    }
                )
            }
        }
    }

    private fun openSamsungBatterySettings() {
        val detailsIntent = Intent("com.samsung.android.sm.ACTION_POWER_USAGE_SUMMARY")
            .setPackage("com.samsung.android.lool")
        try {
            startActivity(detailsIntent)
        } catch (_: ActivityNotFoundException) {
            openSamsungBatteryOverview()
        } catch (_: SecurityException) {
            openSamsungBatteryOverview()
        }
    }

    private fun openSamsungBatteryOverview() {
        val batteryIntent = Intent().setComponent(
            ComponentName(
                "com.samsung.android.lool",
                "com.samsung.android.sm.battery.ui.BatteryActivity"
            )
        )
        try {
            startActivity(batteryIntent)
        } catch (_: ActivityNotFoundException) {
            openPowerUsageSummary()
        } catch (_: SecurityException) {
            openPowerUsageSummary()
        }
    }

    private fun openPowerUsageSummary() {
        try {
            startActivity(Intent("android.intent.action.POWER_USAGE_SUMMARY"))
        } catch (_: ActivityNotFoundException) {
            Unit
        } catch (_: SecurityException) {
            Unit
        }
    }
}

@Composable
fun BatteryScreen(
    darkTheme: Boolean,
    languageTag: String,
    onDarkThemeChange: (Boolean) -> Unit,
    onLanguageChange: (String) -> Unit,
    onOpenBatterySettings: () -> Unit
) {
    val context = LocalContext.current
    var battery by remember { mutableStateOf(getBatteryInfo(context, null)) }
    var showSettings by remember { mutableStateOf(false) }
    var languageMenuExpanded by remember { mutableStateOf(false) }
    val deviceModel = remember { getDeviceModel() }
    val sensorManager = remember(context) { context.getSystemService(SensorManager::class.java) }
    val accelerometer = remember(sensorManager) {
        sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    }
    var deviceTilt by remember { mutableStateOf(Offset.Zero) }
    val chargingCurrentMicroAmps by produceState<Int?>(initialValue = null, battery.isCharging) {
        if (!battery.isCharging) {
            value = null
        } else {
            while (true) {
                value = withContext(Dispatchers.IO) { readChargingCurrent(context) }
                delay(1000L)
            }
        }
    }
    val cycleCount by produceState<Int?>(initialValue = battery.cycleCount, battery.cycleCount) {
        value = withContext(Dispatchers.IO) {
            battery.cycleCount ?: readSystemCycleCount()
        }
    }

    DisposableEffect(context) {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent != null) {
                    battery = getBatteryInfo(context ?: return, intent)
                }
            }
        }

        context.registerReceiver(receiver, filter)
        onDispose {
            context.unregisterReceiver(receiver)
        }
    }

    DisposableEffect(sensorManager, accelerometer, battery.isCharging) {
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val gravity = SensorManager.GRAVITY_EARTH
                deviceTilt = Offset(
                    x = (event.values[0] / gravity).coerceIn(-1f, 1f),
                    y = (event.values[1] / gravity - 1f).coerceIn(-1f, 1f)
                )
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        if (battery.isCharging && sensorManager != null && accelerometer != null) {
            sensorManager.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_UI)
        }
        onDispose { sensorManager?.unregisterListener(listener) }
    }

    val animatedTiltX by animateFloatAsState(
        targetValue = deviceTilt.x,
        animationSpec = spring(dampingRatio = 0.62f, stiffness = 180f),
        label = "waterTiltX"
    )
    val animatedTiltY by animateFloatAsState(
        targetValue = deviceTilt.y,
        animationSpec = spring(dampingRatio = 0.62f, stiffness = 180f),
        label = "waterTiltY"
    )

    val foreground = MaterialTheme.colorScheme.onBackground
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    val languageOptions = listOf(
        "" to stringResource(R.string.language_system_default),
        "ru" to stringResource(R.string.language_russian),
        "uk" to stringResource(R.string.language_ukrainian),
        "en" to stringResource(R.string.language_english)
    )
    val selectedLanguage = languageOptions.firstOrNull { (tag, _) ->
        if (tag.isEmpty()) languageTag.isEmpty() else languageTag.startsWith(tag, ignoreCase = true)
    }?.second ?: languageOptions.first().second

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(if (showSettings) R.string.settings_title else R.string.battery_title),
                fontSize = 32.sp,
                color = foreground,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 22.dp)
            )
            TextButton(onClick = { showSettings = !showSettings }) {
                Text(stringResource(if (showSettings) R.string.back else R.string.settings_title))
            }
        }

        if (showSettings) {
            Spacer(modifier = Modifier.height(28.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(20.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.dark_theme), color = foreground, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        Text(stringResource(R.string.theme_description), color = secondary, fontSize = 13.sp)
                    }
                    Switch(checked = darkTheme, onCheckedChange = onDarkThemeChange)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(20.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.language_title), color = foreground, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        Text(selectedLanguage, color = secondary, fontSize = 13.sp)
                    }
                    Box {
                        OutlinedButton(onClick = { languageMenuExpanded = true }) {
                            Text(stringResource(R.string.change_language))
                        }
                        DropdownMenu(
                            expanded = languageMenuExpanded,
                            onDismissRequest = { languageMenuExpanded = false }
                        ) {
                            languageOptions.forEach { (tag, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        languageMenuExpanded = false
                                        onLanguageChange(tag)
                                    }
                                )
                            }
                        }
                    }
                }
            }

        } else {
            Spacer(modifier = Modifier.height(30.dp))
            BatteryVisual(
                percent = battery.levelPercent,
                status = battery.statusText,
                isCharging = battery.isCharging,
                chargingCurrentMicroAmps = chargingCurrentMicroAmps,
                tiltX = animatedTiltX,
                tiltY = animatedTiltY
            )

            Spacer(modifier = Modifier.height(26.dp))
            Text(stringResource(R.string.battery_status_title), fontSize = 20.sp, color = foreground, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(12.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Button(
                        onClick = onOpenBatterySettings,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.find_battery_wear))
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    MetricRow(
                        label = stringResource(R.string.device_model),
                        value = deviceModel
                    )
                    MetricRow(
                        label = stringResource(R.string.cycle_count),
                        value = cycleCount?.let { context.getString(R.string.cycles_value, it) }
                            ?: stringResource(R.string.no_data)
                    )
                    MetricRow(
                        label = stringResource(R.string.system_battery_health),
                        value = battery.healthText
                    )
                    MetricRow(
                        label = stringResource(R.string.voltage),
                        value = battery.voltageMv?.let { context.getString(R.string.millivolts_value, it) }
                            ?: stringResource(R.string.no_data)
                    )
                    MetricRow(
                        label = stringResource(R.string.battery_temperature),
                        value = battery.temperatureC?.let { context.getString(R.string.temperature_value, it) }
                            ?: stringResource(R.string.no_data),
                        isLast = true
                    )
                }
            }
        }
    }
}

private fun getDeviceModel(): String {
    val manufacturer = Build.MANUFACTURER.trim()
        .replaceFirstChar { it.titlecase(Locale.ROOT) }
    val model = Build.MODEL.trim()
    if (model.isBlank()) return manufacturer.ifBlank { Build.DEVICE }
    if (manufacturer.isBlank() || model.startsWith(manufacturer, ignoreCase = true)) return model
    return "$manufacturer $model"
}

private fun readSystemCycleCount(): Int? {
    for (batteryDirectory in getBatteryDirectories()) {
        for (fileName in listOf("fg_cycle", "cycle_count")) {
            val cycleCount = readNonNegativeLong(File(batteryDirectory, fileName))
                ?.takeIf { it <= Int.MAX_VALUE }
                ?.toInt()
            if (cycleCount != null) return cycleCount
        }
    }
    return null
}

private fun getBatteryDirectories(): List<File> {
    val powerSupplyDirectory = File("/sys/class/power_supply")
    return buildList {
        powerSupplyDirectory.listFiles()
            ?.filter { it.isDirectory }
            ?.let(::addAll)
        add(File(powerSupplyDirectory, "battery"))
        add(File(powerSupplyDirectory, "Battery"))
    }.distinctBy { it.absolutePath }
}

private fun readNonNegativeLong(file: File): Long? = runCatching {
    file.readText().trim().toLongOrNull()?.takeIf { it >= 0L }
}.getOrNull()

private fun readChargingCurrent(context: Context): Int? {
    val batteryManager = context.getSystemService(BatteryManager::class.java) ?: return null
    val currentNow = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        .takeIf { it > 0 }
    return currentNow ?: batteryManager
        .getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE)
        .takeIf { it > 0 }
}

@Composable
fun BatteryVisual(
    percent: Int?,
    status: String,
    isCharging: Boolean,
    chargingCurrentMicroAmps: Int?,
    tiltX: Float,
    tiltY: Float
) {
    val safePercent = (percent ?: 0).coerceIn(0, 100)
    val batteryColor = MaterialTheme.colorScheme.primary
    val outlineColor = MaterialTheme.colorScheme.outline
    val wavePhase: Float
    val bubbleProgress: Float
    val rainProgress: Float
    val chargeSpeed = ((chargingCurrentMicroAmps ?: 700_000) / 1_000_000f).coerceIn(0.35f, 2.5f)
    val rainDurationMillis = (1800f / chargeSpeed).roundToInt().coerceIn(650, 5000)
    if (isCharging) {
        val waterTransition = rememberInfiniteTransition(label = "batteryWater")
        val animatedWave by waterTransition.animateFloat(
            initialValue = 0f,
            targetValue = (2 * PI).toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 2400, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "waterWave"
        )
        val animatedBubbles by waterTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1800, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "waterBubbles"
        )
        val animatedRain by waterTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = rainDurationMillis, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "rainDrops"
        )
        wavePhase = animatedWave
        bubbleProgress = animatedBubbles
        rainProgress = animatedRain
    } else {
        wavePhase = 0f
        bubbleProgress = 0f
        rainProgress = 0f
    }
    val animatedProgress by animateFloatAsState(
        targetValue = safePercent / 100f,
        animationSpec = tween(durationMillis = 700),
        label = "batteryProgress"
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(contentAlignment = Alignment.Center) {
            androidx.compose.foundation.Canvas(modifier = Modifier.size(width = 190.dp, height = 240.dp)) {
                drawBatteryOutline(
                    accent = batteryColor,
                    outline = outlineColor,
                    progress = animatedProgress,
                    isCharging = isCharging,
                    wavePhase = wavePhase,
                    bubbleProgress = bubbleProgress,
                    rainProgress = rainProgress,
                    tiltX = tiltX,
                    tiltY = tiltY,
                    rainDropCount = rainDropCount(chargingCurrentMicroAmps, isCharging)
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = percent?.let { stringResource(R.string.percent_value, it) }
                        ?: stringResource(R.string.no_data),
                    fontSize = 48.sp,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = status,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 15.sp
                )
            }
        }
    }
}

private fun DrawScope.drawBatteryOutline(
    accent: Color,
    outline: Color,
    progress: Float,
    isCharging: Boolean,
    wavePhase: Float,
    bubbleProgress: Float,
    rainProgress: Float,
    tiltX: Float,
    tiltY: Float,
    rainDropCount: Int
) {
    val bodyWidth = size.width * 0.72f
    val bodyHeight = size.height * 0.76f
    val bodyLeft = (size.width - bodyWidth) / 2f
    val bodyTop = size.height * 0.16f
    val bodyTopLeft = Offset(bodyLeft, bodyTop)
    val capWidth = bodyWidth * 0.36f
    val capHeight = size.height * 0.08f
    val capLeft = (size.width - capWidth) / 2f
    val capTop = bodyTop - capHeight + 3f

    drawRoundRect(
        color = outline.copy(alpha = 0.45f),
        topLeft = bodyTopLeft,
        size = Size(bodyWidth, bodyHeight),
        cornerRadius = CornerRadius(36f, 36f),
        style = Stroke(width = 8f)
    )

    drawRoundRect(
        color = outline.copy(alpha = 0.45f),
        topLeft = Offset(capLeft, capTop),
        size = Size(capWidth, capHeight),
        cornerRadius = CornerRadius(10f, 10f),
        style = Stroke(width = 8f)
    )

    val innerLeft = bodyLeft + 10f
    val innerRight = bodyLeft + bodyWidth - 10f
    val innerTop = bodyTop + 10f
    val innerBottom = bodyTop + bodyHeight - 10f
    val fillHeight = (innerBottom - innerTop) * progress
    if (fillHeight <= 0f) return

    val waterAtTop = progress >= 0.98f
    val waterTop = if (waterAtTop) innerTop else innerBottom - fillHeight
    val waveAmplitude = if (isCharging && !waterAtTop && fillHeight > 16f) 4f else 0f
    val phase = if (isCharging) wavePhase else 0f
    val freeSurfaceSpace = (waterTop - innerTop - waveAmplitude - 1f).coerceAtLeast(0f)
    val verticalShift = (tiltY * 5f).coerceIn(-freeSurfaceSpace, freeSurfaceSpace)
    val slopeLimit = (freeSurfaceSpace - abs(verticalShift)).coerceAtLeast(0f) * 2f
    val surfaceSlope = (tiltX * (innerRight - innerLeft) * 0.22f)
        .coerceIn(-slopeLimit, slopeLimit)

    if (waterAtTop) {
        drawRoundRect(
            color = accent,
            topLeft = Offset(innerLeft, innerTop),
            size = Size(innerRight - innerLeft, innerBottom - innerTop),
            cornerRadius = CornerRadius(28f, 28f)
        )
    } else {
        val wavePath = Path().apply {
            for (step in 0..32) {
                val normalizedX = step / 32f
                val x = innerLeft + (innerRight - innerLeft) * normalizedX
                val slopeY = (normalizedX - 0.5f) * surfaceSlope
                val y = (waterTop + slopeY + verticalShift +
                    sin(normalizedX * 2f * PI.toFloat() + phase) * waveAmplitude)
                    .coerceAtLeast(innerTop + 1f)
                if (step == 0) moveTo(x, y) else lineTo(x, y)
            }
            lineTo(innerRight, innerBottom)
            lineTo(innerLeft, innerBottom)
            close()
        }
        drawPath(path = wavePath, color = accent)
    }

    val airHeight = waterTop - innerTop
    if (isCharging && !waterAtTop && airHeight > 14f) {
        for (dropIndex in 0 until rainDropCount) {
            val horizontalPosition = ((dropIndex * 37) % rainDropCount + 0.5f) / rainDropCount
            val travel = (rainProgress + dropIndex / rainDropCount.toFloat()) % 1f
            val dropY = innerTop + travel * airHeight
            val dropX = innerLeft + (innerRight - innerLeft) * horizontalPosition
            drawLine(
                color = accent.copy(alpha = 0.62f),
                start = Offset(dropX, dropY),
                end = Offset(dropX, dropY + 7f),
                strokeWidth = 2.5f
            )
            drawCircle(
                color = accent.copy(alpha = 0.72f),
                radius = 1.8f,
                center = Offset(dropX, dropY + 7f)
            )
        }
    }

    if (isCharging && fillHeight > 28f) {
        val bubbleCount = 4
        for (bubbleIndex in 0 until bubbleCount) {
            val horizontalPosition = ((bubbleIndex * 37) % bubbleCount + 0.5f) / bubbleCount
            val travel = (bubbleProgress + bubbleIndex / bubbleCount.toFloat()) % 1f
            val bubbleX = innerLeft + (innerRight - innerLeft) * horizontalPosition
            val bubbleSlopeOffset = ((horizontalPosition - 0.5f) * surfaceSlope + verticalShift)
                .coerceIn(-freeSurfaceSpace, freeSurfaceSpace)
            val bubbleY = innerBottom - 7f - travel * (fillHeight - 14f) + bubbleSlopeOffset
            if (bubbleY > waterTop + 6f) {
                val radius = if (bubbleIndex % 2 == 0) 3.5f else 2.5f
                drawCircle(
                    color = Color.White.copy(alpha = 0.72f),
                    radius = radius,
                    center = Offset(x = bubbleX, y = bubbleY)
                )
            }
        }
    }
}

private fun rainDropCount(chargingCurrentMicroAmps: Int?, isCharging: Boolean): Int {
    if (!isCharging) return 0
    val current = chargingCurrentMicroAmps ?: return 4
    val currentRatio = (current / 2_500_000f).coerceIn(0f, 1f)
    return (2 + (currentRatio * 10f).roundToInt()).coerceIn(2, 12)
}

@Composable
private fun MetricRow(label: String, value: String, isLast: Boolean = false) {
    val dividerColor = MaterialTheme.colorScheme.outlineVariant
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
            Text(text = value, color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        }
        if (!isLast) {
            androidx.compose.foundation.Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
            ) {
                drawLine(dividerColor, Offset.Zero, Offset(size.width, 0f))
            }
        }
    }
}

private fun getBatteryInfo(context: Context, intent: Intent?): BatteryInfo {
    val batteryIntent = intent ?: context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

    val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
    val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: 100
    val percentage = if (scale > 0 && level >= 0) ((level * 100) / scale).coerceIn(0, 100) else null

    val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
    val health = batteryIntent?.getIntExtra(BatteryManager.EXTRA_HEALTH, -1) ?: -1
    val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING
    val cycleCount = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        batteryIntent?.getIntExtra(BatteryManager.EXTRA_CYCLE_COUNT, -1)?.takeIf { it >= 0 }
    } else {
        null
    }
    val voltage = batteryIntent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)?.takeIf { it > 0 }
    val temperature = batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)
        ?.takeIf { it > 0 }
        ?.div(10f)

    val statusText = when (status) {
        BatteryManager.BATTERY_STATUS_CHARGING -> context.getString(R.string.status_charging)
        BatteryManager.BATTERY_STATUS_DISCHARGING -> context.getString(R.string.status_not_charging)
        BatteryManager.BATTERY_STATUS_FULL -> context.getString(R.string.status_full)
        BatteryManager.BATTERY_STATUS_NOT_CHARGING -> context.getString(R.string.status_not_charging)
        else -> context.getString(R.string.no_data)
    }

    val healthText = when (health) {
        BatteryManager.BATTERY_HEALTH_GOOD -> context.getString(R.string.health_good)
        BatteryManager.BATTERY_HEALTH_OVERHEAT -> context.getString(R.string.health_overheat)
        BatteryManager.BATTERY_HEALTH_DEAD -> context.getString(R.string.health_dead)
        BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> context.getString(R.string.health_over_voltage)
        BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> context.getString(R.string.health_failure)
        BatteryManager.BATTERY_HEALTH_COLD -> context.getString(R.string.health_cold)
        else -> context.getString(R.string.no_data)
    }

    return BatteryInfo(
        levelPercent = percentage,
        cycleCount = cycleCount,
        statusText = statusText,
        isCharging = isCharging,
        healthText = healthText,
        voltageMv = voltage,
        temperatureC = temperature
    )
}
