package com.example.startline

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.media.AudioManager
import android.media.ToneGenerator
import android.location.Location
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import java.util.Locale
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.Polygon

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    StartLineScreen()
                }
            }
        }
    }
}

@Composable
@SuppressLint("MissingPermission")
fun StartLineScreen() {
    val configuration = LocalConfiguration.current
    val defaultOrientationMode = if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
        OrientationMode.Landscape
    } else {
        OrientationMode.Portrait
    }
    var currentScreen by rememberSaveable { mutableStateOf(AppScreen.Main) }
    var menuExpanded by rememberSaveable { mutableStateOf(false) }
    var orientationMode by rememberSaveable { mutableStateOf(defaultOrientationMode) }
    var screenMode by rememberSaveable { mutableStateOf(ScreenMode.Light) }
    var mapMode by rememberSaveable { mutableStateOf(MapMode.NorthUp) }
    var mapRenderMode by rememberSaveable { mutableStateOf(MapRenderMode.Canvas) }
    var mapZoom by rememberSaveable { mutableStateOf(1.0f) }
    var speedKnots by rememberSaveable { mutableStateOf(6.0) }
    var speedStatus by rememberSaveable { mutableStateOf("Ručno podešena brzina") }
    var leftBuoyLat by rememberSaveable { mutableStateOf<Double?>(null) }
    var leftBuoyLon by rememberSaveable { mutableStateOf<Double?>(null) }
    var rightBuoyLat by rememberSaveable { mutableStateOf<Double?>(null) }
    var rightBuoyLon by rememberSaveable { mutableStateOf<Double?>(null) }
    val leftBuoySet = leftBuoyLat != null && leftBuoyLon != null
    val rightBuoySet = rightBuoyLat != null && rightBuoyLon != null
    val leftBuoyLocation = remember(leftBuoyLat, leftBuoyLon) {
        locationFromCoordinates(leftBuoyLat, leftBuoyLon)
    }
    val rightBuoyLocation = remember(rightBuoyLat, rightBuoyLon) {
        locationFromCoordinates(rightBuoyLat, rightBuoyLon)
    }
    var hasLocationPermission by remember { mutableStateOf(false) }
    var permissionDenied by remember { mutableStateOf(false) }
    var currentLocation by remember { mutableStateOf<Location?>(null) }
    var gpsSamples by remember { mutableStateOf<List<GpsSample>>(emptyList()) }
    var doubleClickTimeoutMs by remember { mutableLongStateOf(DEFAULT_DOUBLE_CLICK_TIMEOUT_MS) }
    var timeoutInput by remember { mutableStateOf(DEFAULT_DOUBLE_CLICK_TIMEOUT_MS.toString()) }
    var timeoutError by remember { mutableStateOf<String?>(null) }
    var gpsToBowDistanceMeters by remember { mutableDoubleStateOf(DEFAULT_GPS_TO_BOW_DISTANCE_METERS) }
    var gpsToBowDistanceInput by remember { mutableStateOf(DEFAULT_GPS_TO_BOW_DISTANCE_METERS.toString()) }
    var gpsToBowDistanceError by remember { mutableStateOf<String?>(null) }
    var avgWindowSeconds by remember { mutableLongStateOf(DEFAULT_AVERAGE_WINDOW_SECONDS) }
    var avgWindowInput by remember { mutableStateOf(DEFAULT_AVERAGE_WINDOW_SECONDS.toString()) }
    var avgWindowError by remember { mutableStateOf<String?>(null) }
    var countdownStartMinutes by remember { mutableLongStateOf(DEFAULT_COUNTDOWN_START_MINUTES) }
    var countdownStartInput by remember { mutableStateOf(DEFAULT_COUNTDOWN_START_MINUTES.toString()) }
    var countdownStartError by remember { mutableStateOf<String?>(null) }
    var remainingCountdownSeconds by remember {
        mutableLongStateOf(DEFAULT_COUNTDOWN_START_MINUTES * 60L)
    }
    var isCountdownRunning by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val activity = context as? Activity
    val countdownTone = remember { ToneGenerator(AudioManager.STREAM_ALARM, 100) }
    val fusedLocationClient = remember {
        LocationServices.getFusedLocationProviderClient(context)
    }

    val locationCallback = remember {
        object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { latestLocation ->
                    currentLocation = latestLocation
                    val now = SystemClock.elapsedRealtime()
                    gpsSamples = (gpsSamples + GpsSample(now, Location(latestLocation)))
                        .filter { now - it.timestampMs <= MAX_GPS_SAMPLE_AGE_MS }
                }
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val fineGranted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseGranted = result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        hasLocationPermission = fineGranted || coarseGranted
        permissionDenied = !hasLocationPermission
    }

    LaunchedEffect(Unit) {
        hasLocationPermission = hasLocationPermission(context)
        if (!hasLocationPermission) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    DisposableEffect(hasLocationPermission) {
        if (!hasLocationPermission) {
            onDispose { }
        } else {
            val locationRequest = LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY,
                2_000L
            )
                .setMinUpdateIntervalMillis(1_000L)
                .build()

            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    currentLocation = location
                }
            }

            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )

            onDispose {
                fusedLocationClient.removeLocationUpdates(locationCallback)
            }
        }
    }

    val startLineLengthMeters = remember(leftBuoyLocation, rightBuoyLocation) {
        if (leftBuoyLocation != null && rightBuoyLocation != null) {
            leftBuoyLocation!!.distanceTo(rightBuoyLocation!!).toDouble()
        } else {
            null
        }
    }

    val samplesInWindow = remember(gpsSamples, avgWindowSeconds) {
        val now = SystemClock.elapsedRealtime()
        val windowMs = avgWindowSeconds * 1_000L
        gpsSamples.filter { now - it.timestampMs <= windowMs }
    }

    val averageMotion = remember(samplesInWindow) {
        calculateAverageMotion(samplesInWindow)
    }
    val averageTrueSpeedKnots = averageMotion?.speedMps?.times(METERS_PER_SECOND_TO_KNOTS)
    val averageGpsHeadingDeg = averageMotion?.headingDeg

    val gpsDistanceToLineInfo = remember(currentLocation, leftBuoyLocation, rightBuoyLocation) {
        if (currentLocation != null && leftBuoyLocation != null && rightBuoyLocation != null) {
            distanceToStartLineInfo(
                point = currentLocation!!,
                lineStart = leftBuoyLocation!!,
                lineEnd = rightBuoyLocation!!
            )
        } else {
            null
        }
    }

    val bowDistanceToLineMeters = remember(gpsDistanceToLineInfo, averageGpsHeadingDeg, gpsToBowDistanceMeters) {
        if (gpsDistanceToLineInfo == null) {
            null
        } else {
            val normalTowardLine = gpsDistanceToLineInfo.normalTowardLine
            val heading = averageGpsHeadingDeg
            if (normalTowardLine == null || heading == null) {
                gpsDistanceToLineInfo.distanceMeters
            } else {
                val courseUnit = headingToUnitVector(heading)
                val towardLineFactor = dot(courseUnit, normalTowardLine).coerceAtLeast(0.0)
                val bowCorrectionMeters = gpsToBowDistanceMeters * towardLineFactor
                (gpsDistanceToLineInfo.distanceMeters - bowCorrectionMeters).coerceAtLeast(0.0)
            }
        }
    }

    val approachSpeedKnots = remember(averageMotion, averageGpsHeadingDeg, gpsDistanceToLineInfo) {
        if (averageMotion == null || averageGpsHeadingDeg == null || gpsDistanceToLineInfo == null) {
            null
        } else {
            val normalTowardLine = gpsDistanceToLineInfo.normalTowardLine
            if (normalTowardLine == null) {
                null
            } else {
                val courseUnit = headingToUnitVector(averageGpsHeadingDeg)
                val towardLineFactor = dot(courseUnit, normalTowardLine).coerceAtLeast(0.0)
                averageMotion.speedMps * towardLineFactor * METERS_PER_SECOND_TO_KNOTS
            }
        }
    }

    val etaToLineSeconds = remember(bowDistanceToLineMeters, speedKnots) {
        val speedMps = speedKnots / METERS_PER_SECOND_TO_KNOTS
        if (bowDistanceToLineMeters != null && speedMps > 0.0) {
            bowDistanceToLineMeters / speedMps
        } else {
            null
        }
    }

    val lineCrossingEtaSeconds = remember(startLineLengthMeters, averageMotion) {
        val speedMps = averageMotion?.speedMps ?: 0.0
        if (startLineLengthMeters != null && speedMps > 0.0) {
            startLineLengthMeters / speedMps
        } else {
            null
        }
    }

    val etaDeltaSeconds = remember(etaToLineSeconds, remainingCountdownSeconds) {
        etaToLineSeconds?.let { eta ->
            (eta - remainingCountdownSeconds.toDouble()).roundToInt()
        }
    }
    val timingColor = when {
        etaDeltaSeconds == null -> Color(0xFFB0BEC5)
        etaDeltaSeconds > 0 -> Color(0xFFC8E6C9) // kasni -> svijetlo zeleno
        etaDeltaSeconds < 0 -> Color(0xFFFFCDD2) // prerano/prebrzo -> svijetlo crveno
        else -> Color(0xFFE0E0E0)
    }

    val onDoubleClickAction = rememberDoubleClickAction(timeoutMs = doubleClickTimeoutMs)
    val settingsScrollState = rememberScrollState()

    DisposableEffect(Unit) {
        onDispose {
            countdownTone.release()
        }
    }

    DisposableEffect(activity, currentScreen) {
        val currentActivity = activity
        if (currentActivity == null) {
            onDispose { }
        } else {
            val window = currentActivity.window
            val originalParams = WindowManager.LayoutParams().apply {
                copyFrom(window.attributes)
            }
            val originalKeepScreenOn = window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0

            if (currentScreen == AppScreen.Main) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                val updated = WindowManager.LayoutParams().apply {
                    copyFrom(window.attributes)
                    screenBrightness = 1.0f
                }
                window.attributes = updated
            }

            onDispose {
                if (!originalKeepScreenOn) {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
                window.attributes = originalParams
            }
        }
    }

    LaunchedEffect(activity, orientationMode) {
        val currentActivity = activity ?: return@LaunchedEffect
        currentActivity.requestedOrientation = when (orientationMode) {
            OrientationMode.Portrait -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            OrientationMode.Landscape -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
    }

    LaunchedEffect(isCountdownRunning) {
        while (isCountdownRunning) {
            delay(1_000L)
            if (remainingCountdownSeconds > 0L) {
                val newRemaining = remainingCountdownSeconds - 1L
                remainingCountdownSeconds = newRemaining
                playCountdownCue(
                    toneGenerator = countdownTone,
                    remainingSeconds = newRemaining
                )
            }
            if (remainingCountdownSeconds <= 0L) {
                remainingCountdownSeconds = 0L
                isCountdownRunning = false
            }
        }
    }

    val colorScheme = if (screenMode == ScreenMode.Dark) {
        darkColorScheme(
            background = Color.Black,
            surface = Color.Black,
            onBackground = HIGH_CONTRAST_YELLOW,
            onSurface = HIGH_CONTRAST_YELLOW,
            primary = HIGH_CONTRAST_YELLOW,
            onPrimary = Color.Black,
            secondary = HIGH_CONTRAST_YELLOW,
            onSecondary = Color.Black
        )
    } else {
        lightColorScheme()
    }

    MaterialTheme(
        colorScheme = colorScheme
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.Top
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Start Line",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Text("☰")
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Start Line") },
                                onClick = {
                                    currentScreen = AppScreen.Main
                                    menuExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Settings") },
                                onClick = {
                                    currentScreen = AppScreen.Settings
                                    menuExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                if (currentScreen == AppScreen.Settings) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(settingsScrollState)
                    ) {
                        Text(
                            text = "Settings",
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Orijentacija")
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { orientationMode = OrientationMode.Portrait }) {
                                Text("Portrait")
                            }
                            Button(onClick = { orientationMode = OrientationMode.Landscape }) {
                                Text("Landscape")
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Trenutno: ${orientationMode.label}")

                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Podloga karte")
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { mapRenderMode = MapRenderMode.Canvas }) {
                                Text("Canvas")
                            }
                            Button(onClick = { mapRenderMode = MapRenderMode.Osm }) {
                                Text("OSM")
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Trenutna podloga: " + if (mapRenderMode == MapRenderMode.Canvas) "Canvas" else "OSM"
                        )

                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Screen mode")
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { screenMode = ScreenMode.Light }) {
                                Text("Bijeli")
                            }
                            Button(onClick = { screenMode = ScreenMode.Dark }) {
                                Text("Crni")
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Trenutni mode: " + if (screenMode == ScreenMode.Light) "Bijeli" else "Crni"
                        )

                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Timeout za dvoklik (ms)")
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = timeoutInput,
                                onValueChange = { input ->
                                    val sanitized = input.filter { it.isDigit() }
                                    timeoutInput = sanitized
                                    val parsed = sanitized.toLongOrNull()
                                    when {
                                        sanitized.isBlank() -> {
                                            timeoutError = "Unesi timeout u milisekundama."
                                        }

                                        parsed == null -> {
                                            timeoutError = "Vrijednost timeout-a nije valjana."
                                        }

                                        parsed < 100L -> {
                                            timeoutError = "Minimum je 100 ms."
                                        }

                                        else -> {
                                            timeoutError = null
                                            doubleClickTimeoutMs = parsed
                                        }
                                    }
                                },
                                label = { Text("Timeout (ms)") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.width(180.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    doubleClickTimeoutMs = DEFAULT_DOUBLE_CLICK_TIMEOUT_MS
                                    timeoutInput = DEFAULT_DOUBLE_CLICK_TIMEOUT_MS.toString()
                                    timeoutError = null
                                }
                            ) {
                                Text("Reset")
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Trenutni timeout: ${doubleClickTimeoutMs} ms")
                        if (timeoutError != null) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(timeoutError!!)
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Udaljenost GPS do pramca (m)")
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = gpsToBowDistanceInput,
                                onValueChange = { input ->
                                    val sanitized = input.filter { it.isDigit() || it == '.' }
                                    gpsToBowDistanceInput = sanitized
                                    val parsed = sanitized.toDoubleOrNull()
                                    when {
                                        sanitized.isBlank() -> gpsToBowDistanceError =
                                            "Unesi udaljenost u metrima."

                                        parsed == null -> gpsToBowDistanceError = "Vrijednost nije valjana."
                                        parsed < 0.0 -> gpsToBowDistanceError = "Ne može biti negativno."
                                        else -> {
                                            gpsToBowDistanceError = null
                                            gpsToBowDistanceMeters = parsed
                                        }
                                    }
                                },
                                label = { Text("GPS->pramac (m)") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.width(180.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    gpsToBowDistanceMeters = DEFAULT_GPS_TO_BOW_DISTANCE_METERS
                                    gpsToBowDistanceInput = DEFAULT_GPS_TO_BOW_DISTANCE_METERS.toString()
                                    gpsToBowDistanceError = null
                                }
                            ) {
                                Text("Reset")
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Trenutno: ${String.format(Locale.US, "%.1f", gpsToBowDistanceMeters)} m")
                        if (gpsToBowDistanceError != null) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(gpsToBowDistanceError!!)
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Vrijeme za prosječnu brzinu i smjer (s)")
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = avgWindowInput,
                                onValueChange = { input ->
                                    val sanitized = input.filter { it.isDigit() }
                                    avgWindowInput = sanitized
                                    val parsed = sanitized.toLongOrNull()
                                    when {
                                        sanitized.isBlank() -> avgWindowError = "Unesi vrijeme u sekundama."
                                        parsed == null -> avgWindowError = "Vrijednost nije valjana."
                                        parsed < 1L -> avgWindowError = "Minimum je 1 s."
                                        else -> {
                                            avgWindowError = null
                                            avgWindowSeconds = parsed
                                        }
                                    }
                                },
                                label = { Text("Prozor (s)") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.width(180.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    avgWindowSeconds = DEFAULT_AVERAGE_WINDOW_SECONDS
                                    avgWindowInput = DEFAULT_AVERAGE_WINDOW_SECONDS.toString()
                                    avgWindowError = null
                                }
                            ) {
                                Text("Reset")
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Trenutno: ${avgWindowSeconds} s")
                        if (avgWindowError != null) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(avgWindowError!!)
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Minute štoperice (countdown)")
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = countdownStartInput,
                                onValueChange = { input ->
                                    val sanitized = input.filter { it.isDigit() }
                                    countdownStartInput = sanitized
                                    val parsed = sanitized.toLongOrNull()
                                    when {
                                        sanitized.isBlank() -> countdownStartError =
                                            "Unesi početne minute štoperice."

                                        parsed == null -> countdownStartError = "Vrijednost nije valjana."
                                        parsed < 0L -> countdownStartError = "Ne može biti negativno."
                                        else -> {
                                            countdownStartError = null
                                            countdownStartMinutes = parsed
                                            if (!isCountdownRunning) {
                                                remainingCountdownSeconds = parsed * 60L
                                            }
                                        }
                                    }
                                },
                                label = { Text("Početne minute") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.width(180.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    countdownStartMinutes = DEFAULT_COUNTDOWN_START_MINUTES
                                    countdownStartInput = DEFAULT_COUNTDOWN_START_MINUTES.toString()
                                    countdownStartError = null
                                    if (!isCountdownRunning) {
                                        remainingCountdownSeconds = DEFAULT_COUNTDOWN_START_MINUTES * 60L
                                    }
                                }
                            ) {
                                Text("Reset")
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Trenutno: ${countdownStartMinutes} min")
                        if (countdownStartError != null) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(countdownStartError!!)
                        }
                        Spacer(modifier = Modifier.height(120.dp))
                    }
                    return@Column
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    OutlinedButton(
                        onClick = {
                            onDoubleClickAction("countdown_round") {
                                remainingCountdownSeconds =
                                    ((remainingCountdownSeconds + 30L) / 60L) * 60L
                            }
                        },
                        contentPadding = ButtonDefaults.ButtonWithIconContentPadding
                    ) {
                        Text(
                            text = formatCountdown(remainingCountdownSeconds),
                            fontSize = 96.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                onDoubleClickAction("countdown_start") {
                                    if (remainingCountdownSeconds > 0L) {
                                        isCountdownRunning = !isCountdownRunning
                                    }
                                }
                            },
                            modifier = Modifier.width(84.dp)
                        ) {
                            Text(
                                text = if (isCountdownRunning) "Stop" else "Start",
                                fontSize = 10.sp,
                                maxLines = 1,
                                softWrap = false
                            )
                        }

                        Button(
                            onClick = {
                                onDoubleClickAction("countdown_minus") {
                                    remainingCountdownSeconds =
                                        (remainingCountdownSeconds - 60L).coerceAtLeast(0L)
                                }
                            },
                            modifier = Modifier.width(52.dp)
                        ) {
                            Text("-")
                        }

                        Button(
                            onClick = {
                                onDoubleClickAction("countdown_plus") {
                                    remainingCountdownSeconds += 60L
                                }
                            },
                            modifier = Modifier.width(52.dp)
                        ) {
                            Text("+")
                        }

                        Button(
                            onClick = {
                                onDoubleClickAction("countdown_reset") {
                                    isCountdownRunning = false
                                    remainingCountdownSeconds = countdownStartMinutes * 60L
                                }
                            },
                            modifier = Modifier.width(84.dp)
                        ) {
                            Text(
                                text = "Reset",
                                fontSize = 10.sp,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }

        Spacer(modifier = Modifier.height(16.dp))

        HorizontalDivider(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.wrapContentWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = {
                    onDoubleClickAction("speed_minus") {
                        speedKnots = (speedKnots - 0.2).coerceAtLeast(0.0)
                    }
                }
            ) {
                Text("-")
            }

            OutlinedButton(
                onClick = {
                    onDoubleClickAction("speed_from_gps") {
                        if (approachSpeedKnots != null) {
                            speedKnots = approachSpeedKnots
                            speedStatus = "Brzina približavanja liniji (${avgWindowSeconds}s)"
                        } else {
                            speedStatus = "Nema podataka za brzinu približavanja liniji"
                        }
                    }
                },
                modifier = Modifier.width(88.dp)
            ) {
                Text(String.format(Locale.US, "%.1f kn", speedKnots))
            }

            Button(
                onClick = {
                    onDoubleClickAction("speed_plus") {
                        speedKnots += 0.2
                    }
                }
            ) {
                Text("+")
            }

            OutlinedButton(
                onClick = {},
                enabled = false,
                modifier = Modifier.size(70.dp),
                contentPadding = ButtonDefaults.ButtonWithIconContentPadding
            ) {
                Text(
                    text = "${averageTrueSpeedKnots?.let { String.format(Locale.US, "%.1f", it) } ?: "--"} kn",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = {
                    onDoubleClickAction("left_buoy") {
                        if (leftBuoySet) {
                            leftBuoyLat = null
                            leftBuoyLon = null
                        } else {
                            val snapshot = currentLocation
                            if (snapshot != null) {
                                leftBuoyLat = snapshot.latitude
                                leftBuoyLon = snapshot.longitude
                            }
                        }
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .size(72.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (leftBuoySet) Color(0xFF2E7D32) else Color(0xFFC62828),
                    contentColor = Color.White
                )
            ) {
                Text(
                    text = if (leftBuoySet) "✓" else "-",
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = "${
                    startLineLengthMeters?.let { String.format(Locale.US, "%.0f m", it) } ?: "-- m"
                } - ${
                    lineCrossingEtaSeconds?.let { formatDuration(it) } ?: "--:--"
                }",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold
            )

            Button(
                onClick = {
                    onDoubleClickAction("right_buoy") {
                        if (rightBuoySet) {
                            rightBuoyLat = null
                            rightBuoyLon = null
                        } else {
                            val snapshot = currentLocation
                            if (snapshot != null) {
                                rightBuoyLat = snapshot.latitude
                                rightBuoyLon = snapshot.longitude
                            }
                        }
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .size(72.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (rightBuoySet) Color(0xFF2E7D32) else Color(0xFFC62828),
                    contentColor = Color.White
                )
            ) {
                Text(
                    text = if (rightBuoySet) "✓" else "-",
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(90.dp),
                color = timingColor,
                tonalElevation = 2.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "${
                            bowDistanceToLineMeters?.let { String.format(Locale.US, "%.0f", it) } ?: "--"
                        } m",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Surface(
                modifier = Modifier.size(90.dp),
                color = timingColor,
                tonalElevation = 2.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "${etaDeltaSeconds?.let { String.format(Locale.US, "%+d", it) } ?: "--"} s",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            StartLineMap(
                leftBuoyLocation = leftBuoyLocation,
                rightBuoyLocation = rightBuoyLocation,
                currentLocation = currentLocation,
                gpsSamples = gpsSamples,
                averageGpsHeadingDeg = averageGpsHeadingDeg,
                mapMode = mapMode,
                mapRenderMode = mapRenderMode,
                mapZoom = mapZoom,
                onToggleMapMode = {
                    mapMode = if (mapMode == MapMode.NorthUp) {
                        MapMode.StartLineUp
                    } else {
                        MapMode.NorthUp
                    }
                },
                onZoomIn = {
                    mapZoom = (mapZoom * 1.25f).coerceAtMost(6.0f)
                },
                onZoomOut = {
                    mapZoom = (mapZoom / 1.25f).coerceAtLeast(0.18f)
                }
            )
        }
            }
        }
    }
}

private fun hasLocationPermission(context: Context): Boolean {
    val fine = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
    val coarse = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
    return fine || coarse
}

private fun locationFromCoordinates(lat: Double?, lon: Double?): Location? {
    if (lat == null || lon == null) return null
    return Location("saved_buoy").apply {
        latitude = lat
        longitude = lon
    }
}

private const val METERS_PER_SECOND_TO_KNOTS = 1.943844
private const val EARTH_RADIUS_METERS = 6_371_000.0
private const val DEFAULT_DOUBLE_CLICK_TIMEOUT_MS = 700L
private const val DEFAULT_GPS_TO_BOW_DISTANCE_METERS = 0.0
private const val DEFAULT_AVERAGE_WINDOW_SECONDS = 5L
private const val DEFAULT_COUNTDOWN_START_MINUTES = 5L
private const val MAX_GPS_SAMPLE_AGE_MS = 120_000L
private val HIGH_CONTRAST_YELLOW = Color(0xFFFFFF99)

private data class Point2D(val x: Double, val y: Double)
private data class GpsSample(val timestampMs: Long, val location: Location)
private data class AverageMotion(val speedMps: Double, val headingDeg: Double?)
private data class LineDistanceInfo(
    val distanceMeters: Double,
    val normalTowardLine: Point2D?
)

private fun distanceToStartLineInfo(
    point: Location,
    lineStart: Location,
    lineEnd: Location
): LineDistanceInfo {
    val start = projectToMeters(lineStart, lineStart.latitude)
    val end = projectToMeters(lineEnd, lineStart.latitude)
    val p = projectToMeters(point, lineStart.latitude)

    val abx = end.x - start.x
    val aby = end.y - start.y
    val apx = p.x - start.x
    val apy = p.y - start.y
    val abLengthSquared = abx * abx + aby * aby
    if (abLengthSquared == 0.0) {
        val distance = lineStart.distanceTo(point).toDouble()
        return LineDistanceInfo(
            distanceMeters = distance,
            normalTowardLine = null
        )
    }

    val rawT = (apx * abx + apy * aby) / abLengthSquared
    val t = rawT.coerceIn(0.0, 1.0)
    val nearestX = start.x + t * abx
    val nearestY = start.y + t * aby
    val towardLineX = nearestX - p.x
    val towardLineY = nearestY - p.y
    val distance = kotlin.math.sqrt(towardLineX * towardLineX + towardLineY * towardLineY)
    val normalTowardLine = if (distance > 0.0) {
        Point2D(
            x = towardLineX / distance,
            y = towardLineY / distance
        )
    } else {
        null
    }
    return LineDistanceInfo(
        distanceMeters = distance,
        normalTowardLine = normalTowardLine
    )
}

private fun projectToMeters(location: Location, referenceLatitude: Double): Point2D {
    val latRad = location.latitude * PI / 180.0
    val lonRad = location.longitude * PI / 180.0
    val refLatRad = referenceLatitude * PI / 180.0
    val x = EARTH_RADIUS_METERS * lonRad * cos(refLatRad)
    val y = EARTH_RADIUS_METERS * latRad
    return Point2D(x = x, y = y)
}

private fun calculateAverageMotion(samples: List<GpsSample>): AverageMotion? {
    if (samples.size < 2) return null

    var totalDistanceMeters = 0.0
    var totalTimeSeconds = 0.0
    var weightedSin = 0.0
    var weightedCos = 0.0

    for (index in 1 until samples.size) {
        val prev = samples[index - 1]
        val curr = samples[index]
        val dtSeconds = (curr.timestampMs - prev.timestampMs) / 1000.0
        if (dtSeconds <= 0.0) continue

        val segmentDistance = prev.location.distanceTo(curr.location).toDouble()
        totalDistanceMeters += segmentDistance
        totalTimeSeconds += dtSeconds

        val bearing = normalizeDegrees(prev.location.bearingTo(curr.location).toDouble())
        val bearingRad = Math.toRadians(bearing)
        weightedSin += sin(bearingRad) * dtSeconds
        weightedCos += cos(bearingRad) * dtSeconds
    }

    if (totalTimeSeconds <= 0.0) return null
    val averageSpeedMps = totalDistanceMeters / totalTimeSeconds
    val averageHeadingDeg = if (weightedSin == 0.0 && weightedCos == 0.0) {
        null
    } else {
        normalizeDegrees(Math.toDegrees(atan2(weightedSin, weightedCos)))
    }
    return AverageMotion(
        speedMps = averageSpeedMps,
        headingDeg = averageHeadingDeg
    )
}

private fun headingToUnitVector(headingDeg: Double): Point2D {
    val rad = Math.toRadians(headingDeg)
    return Point2D(
        x = sin(rad),
        y = cos(rad)
    )
}

private fun dot(a: Point2D, b: Point2D): Double = a.x * b.x + a.y * b.y

private fun normalizeDegrees(degrees: Double): Double {
    var normalized = degrees % 360.0
    if (normalized < 0) normalized += 360.0
    return normalized
}

private fun formatDuration(seconds: Double): String {
    if (!seconds.isFinite() || seconds < 0.0) return "--:--"
    val totalSeconds = seconds.roundToInt()
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val remainingSeconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, remainingSeconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, remainingSeconds)
    }
}

private fun formatCountdown(totalSeconds: Long): String {
    val safeSeconds = totalSeconds.coerceAtLeast(0L)
    val minutes = safeSeconds / 60L
    val seconds = safeSeconds % 60L
    return String.format(Locale.US, "%02d:%02d", minutes, seconds)
}

private fun playCountdownCue(toneGenerator: ToneGenerator, remainingSeconds: Long) {
    if (remainingSeconds <= 0L) {
        toneGenerator.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 3_000)
        return
    }

    if (remainingSeconds <= 30L && remainingSeconds % 10L == 0L) {
        toneGenerator.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 1_500)
        return
    }

    if (remainingSeconds % 60L == 0L) {
        toneGenerator.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 1_000)
        return
    }

    if (remainingSeconds % 30L == 0L) {
        toneGenerator.startTone(ToneGenerator.TONE_CDMA_HIGH_L, 300)
    }
}

private enum class AppScreen {
    Main,
    Settings
}

private enum class ScreenMode {
    Light,
    Dark
}

private enum class OrientationMode(val label: String) {
    Portrait("Portrait"),
    Landscape("Landscape")
}

private enum class MapMode {
    NorthUp,
    StartLineUp
}

private enum class MapRenderMode {
    Canvas,
    Osm
}

@Composable
private fun StartLineMap(
    leftBuoyLocation: Location?,
    rightBuoyLocation: Location?,
    currentLocation: Location?,
    gpsSamples: List<GpsSample>,
    averageGpsHeadingDeg: Double?,
    mapMode: MapMode,
    mapRenderMode: MapRenderMode,
    mapZoom: Float,
    onToggleMapMode: () -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(mapMode, mapZoom) {
                detectTapGestures(
                    onDoubleTap = { tap ->
                        val w = size.width.toFloat()
                        val h = size.height.toFloat()
                        val topHalf = tap.y < h / 2f
                        val rightHalf = tap.x >= w / 2f
                        val leftHalf = tap.x < w / 2f

                        when {
                            topHalf && leftHalf -> onToggleMapMode()
                            topHalf && rightHalf -> onZoomIn()
                            !topHalf && rightHalf -> onZoomOut()
                        }
                    }
                )
            }
    ) {
        if (mapRenderMode == MapRenderMode.Canvas) {
            Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(color = Color(0xFF0E1E2F))

            val refLat = leftBuoyLocation?.latitude ?: currentLocation?.latitude
            if (refLat == null) {
                drawMapHint(this, "Postavi bove za prikaz linije")
                return@Canvas
            }

            val leftPoint = leftBuoyLocation?.let { projectToMeters(it, refLat) }
            val rightPoint = rightBuoyLocation?.let { projectToMeters(it, refLat) }
            val boatPoint = currentLocation?.let { projectToMeters(it, refLat) }
            val trackPoints = gpsSamples.map { projectToMeters(it.location, refLat) }

            val lineCenter = if (leftPoint != null && rightPoint != null) {
                Point2D(
                    x = (leftPoint.x + rightPoint.x) / 2.0,
                    y = (leftPoint.y + rightPoint.y) / 2.0
                )
            } else {
                boatPoint ?: Point2D(0.0, 0.0)
            }

            val lineAngle = if (leftPoint != null && rightPoint != null) {
                atan2(rightPoint.y - leftPoint.y, rightPoint.x - leftPoint.x)
            } else {
                0.0
            }
            val rotation = if (mapMode == MapMode.StartLineUp) -lineAngle else 0.0

            val transformedTrack = trackPoints.map { rotateAround(it, lineCenter, rotation) }
            val transformedLeft = leftPoint?.let { rotateAround(it, lineCenter, rotation) }
            val transformedRight = rightPoint?.let { rotateAround(it, lineCenter, rotation) }
            val transformedBoat = boatPoint?.let { rotateAround(it, lineCenter, rotation) }
            val transformedCenter = rotateAround(lineCenter, lineCenter, rotation)

            val all = mutableListOf<Point2D>()
            transformedTrack.takeLast(200).forEach { all.add(it) }
            transformedLeft?.let { all.add(it) }
            transformedRight?.let { all.add(it) }
            transformedBoat?.let { all.add(it) }
            if (all.isEmpty()) all.add(transformedCenter)

            val dxMax = all.maxOf { kotlin.math.abs(it.x - transformedCenter.x) }
            val dyMax = all.maxOf { kotlin.math.abs(it.y - transformedCenter.y) }
            val spanX = max(dxMax * 2.0 + 40.0, 60.0)
            val spanY = max(dyMax * 2.0 + 40.0, 60.0)

            val baseScaleX = size.width / spanX.toFloat()
            val baseScaleY = size.height / spanY.toFloat()
            val fitScale = minOf(baseScaleX, baseScaleY)
            val lineScale = if (transformedLeft != null && transformedRight != null) {
                val lineLen = kotlin.math.sqrt(
                    (transformedRight.x - transformedLeft.x) * (transformedRight.x - transformedLeft.x) +
                        (transformedRight.y - transformedLeft.y) * (transformedRight.y - transformedLeft.y)
                )
                if (lineLen > 0.0) (size.width * 0.5f) / lineLen.toFloat() else fitScale
            } else {
                fitScale
            }
            val scale = lineScale * mapZoom

            // Keep start line anchored to a fixed screen band; do not pan by boat heading/position.
            val targetLineY = if (mapMode == MapMode.StartLineUp) {
                size.height * 0.18f
            } else {
                size.height * 0.33f
            }
            val targetLineX = size.width / 2f

            fun toScreen(p: Point2D): Offset {
                val rx = (p.x - transformedCenter.x).toFloat()
                val ry = (p.y - transformedCenter.y).toFloat()
                return Offset(
                    x = targetLineX + rx * scale,
                    y = targetLineY - ry * scale
                )
            }

            transformedTrack.takeLast(300).zipWithNext().forEach { (a, b) ->
                drawLine(
                    color = Color(0xFF6FC3FF),
                    start = toScreen(a),
                    end = toScreen(b),
                    strokeWidth = 3f
                )
            }

            if (transformedLeft != null && transformedRight != null) {
                drawLine(
                    color = Color.Yellow,
                    start = toScreen(transformedLeft),
                    end = toScreen(transformedRight),
                    strokeWidth = 4f
                )
                drawCircle(Color(0xFFEF5350), radius = 16f, center = toScreen(transformedLeft))
                drawCircle(Color(0xFF66BB6A), radius = 16f, center = toScreen(transformedRight))
            }

            transformedBoat?.let {
                val boatCenter = toScreen(it)
                drawCircle(Color.White, radius = 11f, center = boatCenter, style = Stroke(width = 3f))
                drawCircle(Color(0xFF00E676), radius = 6f, center = boatCenter)
            }

            val modeLabel = if (mapMode == MapMode.NorthUp) "North up" else "Start line up"
            drawContext.canvas.nativeCanvas.drawText(
                "$modeLabel   x${String.format(Locale.US, "%.2f", mapZoom)}",
                20f,
                36f,
                android.graphics.Paint().apply {
                    color = android.graphics.Color.WHITE
                    textSize = 32f
                    isFakeBoldText = true
                }
            )
            }
        } else {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    org.osmdroid.config.Configuration.getInstance().userAgentValue = ctx.packageName
                    MapView(ctx).apply {
                        setTileSource(TileSourceFactory.MAPNIK)
                        setMultiTouchControls(false)
                        isTilesScaledToDpi = true
                        controller.setZoom(15.0)
                    }
                },
                update = { mapView ->
                    val centerLocation = when {
                        leftBuoyLocation != null && rightBuoyLocation != null -> {
                            val centerLat = (leftBuoyLocation.latitude + rightBuoyLocation.latitude) / 2.0
                            val centerLon = (leftBuoyLocation.longitude + rightBuoyLocation.longitude) / 2.0
                            GeoPoint(centerLat, centerLon)
                        }
                        currentLocation != null -> GeoPoint(currentLocation.latitude, currentLocation.longitude)
                        else -> GeoPoint(45.0, 15.0)
                    }

                    val lineBearing = if (leftBuoyLocation != null && rightBuoyLocation != null) {
                        leftBuoyLocation.bearingTo(rightBuoyLocation).toDouble()
                    } else {
                        0.0
                    }
                    mapView.mapOrientation = if (mapMode == MapMode.StartLineUp) {
                        -lineBearing.toFloat()
                    } else {
                        0f
                    }

                    val zoomLevel = (16.0 + kotlin.math.log2(mapZoom.toDouble())).coerceIn(3.0, 20.0)
                    mapView.controller.setZoom(zoomLevel)
                    mapView.controller.setCenter(centerLocation)

                    mapView.overlays.clear()

                    val trackOverlay = Polyline(mapView).apply {
                        outlinePaint.color = android.graphics.Color.CYAN
                        outlinePaint.strokeWidth = 4f
                        setPoints(
                            gpsSamples.takeLast(500).map {
                                GeoPoint(it.location.latitude, it.location.longitude)
                            }
                        )
                    }
                    mapView.overlays.add(trackOverlay)

                    if (leftBuoyLocation != null && rightBuoyLocation != null) {
                        val startLineOverlay = Polyline(mapView).apply {
                            outlinePaint.color = android.graphics.Color.YELLOW
                            outlinePaint.strokeWidth = 6f
                            setPoints(
                                listOf(
                                    GeoPoint(leftBuoyLocation.latitude, leftBuoyLocation.longitude),
                                    GeoPoint(rightBuoyLocation.latitude, rightBuoyLocation.longitude)
                                )
                            )
                        }
                        mapView.overlays.add(startLineOverlay)
                    }

                    leftBuoyLocation?.let { left ->
                        mapView.overlays.add(
                            createOsmCircleOverlay(
                                mapView = mapView,
                                geoPoint = GeoPoint(left.latitude, left.longitude),
                                fillColor = android.graphics.Color.argb(220, 239, 83, 80),
                                strokeColor = android.graphics.Color.RED
                            )
                        )
                    }
                    rightBuoyLocation?.let { right ->
                        mapView.overlays.add(
                            createOsmCircleOverlay(
                                mapView = mapView,
                                geoPoint = GeoPoint(right.latitude, right.longitude),
                                fillColor = android.graphics.Color.argb(220, 102, 187, 106),
                                strokeColor = android.graphics.Color.GREEN
                            )
                        )
                    }

                    currentLocation?.let { boat ->
                        val boatMarker = Marker(mapView).apply {
                            position = GeoPoint(boat.latitude, boat.longitude)
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                            title = "Brod"
                        }
                        mapView.overlays.add(boatMarker)
                    }

                    mapView.invalidate()
                }
            )
        }
    }
}

private fun rotateAround(point: Point2D, center: Point2D, angleRad: Double): Point2D {
    val dx = point.x - center.x
    val dy = point.y - center.y
    val cosA = cos(angleRad)
    val sinA = sin(angleRad)
    return Point2D(
        x = center.x + dx * cosA - dy * sinA,
        y = center.y + dx * sinA + dy * cosA
    )
}

private fun rotateVector(vector: Point2D, angleRad: Double): Point2D {
    val cosA = cos(angleRad)
    val sinA = sin(angleRad)
    return Point2D(
        x = vector.x * cosA - vector.y * sinA,
        y = vector.x * sinA + vector.y * cosA
    )
}

private fun drawMapHint(scope: androidx.compose.ui.graphics.drawscope.DrawScope, text: String) {
    scope.drawRect(color = Color(0xFF0E1E2F))
    scope.drawContext.canvas.nativeCanvas.drawText(
        text,
        scope.size.width / 2f - 170f,
        scope.size.height / 2f,
        android.graphics.Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = 34f
            isFakeBoldText = true
        }
    )
}

private fun createOsmCircleOverlay(
    mapView: MapView,
    geoPoint: GeoPoint,
    fillColor: Int,
    strokeColor: Int
): Polygon {
    return Polygon(mapView).apply {
        points = Polygon.pointsAsCircle(geoPoint, 6.0)
        this.fillPaint.color = fillColor
        this.outlinePaint.color = strokeColor
        this.outlinePaint.strokeWidth = 3f
    }
}

@Composable
private fun rememberDoubleClickAction(
    timeoutMs: Long
): (actionKey: String, onConfirmed: () -> Unit) -> Unit {
    var pendingActionKey by remember { mutableStateOf<String?>(null) }
    var pendingAtMs by remember { mutableLongStateOf(0L) }

    LaunchedEffect(pendingActionKey, pendingAtMs) {
        val activeKey = pendingActionKey ?: return@LaunchedEffect
        delay(timeoutMs)
        if (pendingActionKey == activeKey) {
            pendingActionKey = null
            pendingAtMs = 0L
        }
    }

    return remember(timeoutMs) {
        { actionKey: String, onConfirmed: () -> Unit ->
            val now = SystemClock.elapsedRealtime()
            val withinWindow = pendingActionKey == actionKey && now - pendingAtMs <= timeoutMs
            if (withinWindow) {
                pendingActionKey = null
                pendingAtMs = 0L
                onConfirmed()
            } else {
                pendingActionKey = actionKey
                pendingAtMs = now
            }
        }
    }
}