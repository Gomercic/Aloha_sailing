package com.example.startline

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
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
    companion object {
        var hasShownWelcomeForCurrentProcess: Boolean = false
    }

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
@OptIn(ExperimentalFoundationApi::class)
fun StartLineScreen() {
    var showWelcomeScreen by rememberSaveable { mutableStateOf(!MainActivity.hasShownWelcomeForCurrentProcess) }
    var currentScreen by rememberSaveable { mutableStateOf(AppScreen.Main) }
    var startLineLayoutMode by rememberSaveable { mutableStateOf(StartLineLayoutMode.Classic) }
    var menuExpanded by rememberSaveable { mutableStateOf(false) }
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
    var raceTrackPoints by remember { mutableStateOf<List<RaceTrackPoint>>(emptyList()) }
    var isTrackRecording by remember { mutableStateOf(false) }
    var trackExportStatus by remember { mutableStateOf<String?>(null) }
    var trackLogRefreshTick by remember { mutableIntStateOf(0) }
    var trackMenuPath by remember { mutableStateOf<String?>(null) }
    var pendingDeleteTrackPath by remember { mutableStateOf<String?>(null) }
    var previewTrackName by remember { mutableStateOf<String?>(null) }
    var previewTrackPoints by remember { mutableStateOf<List<RaceTrackPoint>>(emptyList()) }
    var previewRaceStartEpochMillis by remember { mutableStateOf<Long?>(null) }
    var previewLeftBuoy by remember { mutableStateOf<Point2D?>(null) }
    var previewRightBuoy by remember { mutableStateOf<Point2D?>(null) }
    var previewRaceStartPoint by remember { mutableStateOf<Point2D?>(null) }
    var previewTrackRenderMode by rememberSaveable { mutableStateOf(TrackPreviewRenderMode.TrackOnly) }
    var raceStartEpochMillis by remember { mutableStateOf<Long?>(null) }
    var windShiftStartElapsedRealtimeMs by remember { mutableStateOf<Long?>(null) }
    var raceStartLat by remember { mutableStateOf<Double?>(null) }
    var raceStartLon by remember { mutableStateOf<Double?>(null) }
    var buoysLockedAfterRaceStart by remember { mutableStateOf(false) }
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
                    if (isTrackRecording) {
                        raceTrackPoints = raceTrackPoints + RaceTrackPoint(
                            latitude = latestLocation.latitude,
                            longitude = latestLocation.longitude,
                            epochMillis = System.currentTimeMillis()
                        )
                    }
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
    val distanceReferenceLocation = remember(currentLocation, gpsSamples) {
        when {
            gpsSamples.size >= 3 -> {
                val lastThree = gpsSamples.takeLast(3).map { it.location }
                averageLocations(lastThree)
            }
            gpsSamples.size == 2 -> {
                val lastTwo = gpsSamples.takeLast(2).map { it.location }
                averageLocations(lastTwo)
            }
            else -> currentLocation?.let { Location(it) }
        }
    }

    val gpsDistanceToLineInfo = remember(distanceReferenceLocation, leftBuoyLocation, rightBuoyLocation) {
        if (distanceReferenceLocation != null && leftBuoyLocation != null && rightBuoyLocation != null) {
            distanceToStartLineInfo(
                point = distanceReferenceLocation,
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
    val signedBowDistanceToLineMeters = remember(bowDistanceToLineMeters, gpsDistanceToLineInfo) {
        if (bowDistanceToLineMeters == null || gpsDistanceToLineInfo == null) {
            null
        } else {
            // User preference: invert start-line side sign convention in UI.
            val sign = if (gpsDistanceToLineInfo.signedDistanceMeters < 0.0) 1.0 else -1.0
            sign * bowDistanceToLineMeters
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
    val hasDistanceAndEta = signedBowDistanceToLineMeters != null && etaDeltaSeconds != null
    val isMetersNegative = (signedBowDistanceToLineMeters ?: 0.0) < 0.0
    val isSecondsNegative = (etaDeltaSeconds ?: 0) < 0
    val isBothPositive = hasDistanceAndEta && !isMetersNegative && !isSecondsNegative
    val negativeDistanceBlink = rememberInfiniteTransition(label = "negative_distance_blink")
    val blinkPhase by negativeDistanceBlink.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 450),
            repeatMode = RepeatMode.Reverse
        ),
        label = "negative_distance_phase"
    )
    val statusFrameColor = when {
        !hasDistanceAndEta -> Color(0xFF616161)
        isBothPositive -> Color(0xFF2E7D32)
        isMetersNegative -> lerp(Color(0xFFB71C1C), Color(0xFFFF8A80), blinkPhase)
        isSecondsNegative -> Color(0xFFC62828)
        else -> Color(0xFFC62828)
    }

    val onDoubleClickAction = rememberDoubleClickAction(timeoutMs = doubleClickTimeoutMs)
    val settingsScrollState = rememberScrollState()
    val trackLogScrollState = rememberScrollState()
    val windDebugScrollState = rememberScrollState()
    val screenTitle = when (currentScreen) {
        AppScreen.Main -> "Start Line"
        AppScreen.StartLinePage -> "StartLinePage"
        AppScreen.Settings -> "Settings"
        AppScreen.WindShift -> "WindShift"
        AppScreen.WindShiftDebug -> "Wind Debug"
        AppScreen.TrackLog -> "Track Log"
        AppScreen.TrackPreview -> "Track Preview"
    }
    val trackLogFiles = remember(currentScreen, trackLogRefreshTick) {
        if (currentScreen == AppScreen.TrackLog) {
            listSavedTrackFiles(context)
        } else {
            emptyList()
        }
    }
    val trackLogDistanceByPath = remember(trackLogFiles) {
        trackLogFiles.associate { file ->
            file.absolutePath to parseTrackDistanceMeters(file)
        }
    }
    val windAnalyzer = remember { ShiftWindAnalyzer() }
    val windShiftSeries = remember { mutableStateOf<List<DeviationPoint>>(emptyList()) }
    var windShiftWindowMinutes by rememberSaveable {
        mutableLongStateOf(DEFAULT_WIND_SHIFT_WINDOW_MINUTES)
    }
    var windShiftWindowInput by rememberSaveable {
        mutableStateOf(DEFAULT_WIND_SHIFT_WINDOW_MINUTES.toString())
    }
    var windShiftWindowError by rememberSaveable { mutableStateOf<String?>(null) }
    var windShiftHeadingWindowSeconds by rememberSaveable {
        mutableLongStateOf(DEFAULT_WIND_SHIFT_HEADING_WINDOW_SECONDS)
    }
    var windShiftHeadingWindowInput by rememberSaveable {
        mutableStateOf(DEFAULT_WIND_SHIFT_HEADING_WINDOW_SECONDS.toString())
    }
    var windShiftHeadingWindowError by rememberSaveable { mutableStateOf<String?>(null) }
    var windShiftStdFilterMode by rememberSaveable { mutableStateOf(WindShiftStdFilterMode.OneSigma) }
    var windShiftGraphWindowMinutes by rememberSaveable {
        mutableLongStateOf(DEFAULT_WIND_SHIFT_GRAPH_WINDOW_MINUTES)
    }
    var windShiftTrackOrientation by rememberSaveable { mutableStateOf(WindShiftTrackOrientation.NorthUp) }
    LaunchedEffect(showWelcomeScreen) {
        if (!showWelcomeScreen) return@LaunchedEffect
        delay(5_000L)
        showWelcomeScreen = false
        MainActivity.hasShownWelcomeForCurrentProcess = true
    }
    val applyWindShiftWindowMinutes: (Long) -> Unit = { rawValue ->
        val sanitized = rawValue.coerceIn(
            MIN_WIND_SHIFT_WINDOW_MINUTES,
            MAX_WIND_SHIFT_WINDOW_MINUTES
        )
        windShiftWindowMinutes = sanitized
        windShiftWindowInput = sanitized.toString()
        windShiftWindowError = null
    }
    val applyWindShiftGraphWindowMinutes: (Long) -> Unit = { rawValue ->
        windShiftGraphWindowMinutes = rawValue.coerceAtLeast(MIN_WIND_SHIFT_GRAPH_WINDOW_MINUTES)
    }
    val applyWindShiftHeadingWindowSeconds: (Long) -> Unit = { rawValue ->
        val sanitized = rawValue.coerceIn(
            MIN_WIND_SHIFT_HEADING_WINDOW_SECONDS,
            MAX_WIND_SHIFT_HEADING_WINDOW_SECONDS
        )
        windShiftHeadingWindowSeconds = sanitized
        windShiftHeadingWindowInput = sanitized.toString()
        windShiftHeadingWindowError = null
    }
    LaunchedEffect(
        gpsSamples,
        windShiftWindowMinutes,
        windShiftStartElapsedRealtimeMs,
        windShiftHeadingWindowSeconds,
        windShiftStdFilterMode
    ) {
        windShiftSeries.value = emptyList()
        windAnalyzer.historyWindowMinutes = windShiftWindowMinutes
        windAnalyzer.stdFilterSigma = windShiftStdFilterMode.sigmaMultiplier
        val startElapsedMs = windShiftStartElapsedRealtimeMs
        if (startElapsedMs == null) {
            windAnalyzer.clear()
            return@LaunchedEffect
        }
        val cogSamples = gpsSamplesToWindShiftCogSamples(
            samples = gpsSamples,
            startElapsedRealtimeMs = startElapsedMs,
            avgSpeedWindowSeconds = windShiftHeadingWindowSeconds,
            minAverageSpeedKnots = 1.0
        )
        windAnalyzer.replaceSamples(cogSamples)
        windShiftSeries.value = windAnalyzer.series
    }
    val startTrackRecording: (Location) -> Unit = { startLocation ->
        isTrackRecording = true
        raceTrackPoints = listOf(
            RaceTrackPoint(
                latitude = startLocation.latitude,
                longitude = startLocation.longitude,
                epochMillis = System.currentTimeMillis()
            )
        )
        raceStartEpochMillis = null
        windShiftStartElapsedRealtimeMs = null
        raceStartLat = null
        raceStartLon = null
        buoysLockedAfterRaceStart = false
        trackExportStatus = "Track recording started"
    }

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

            if (currentScreen == AppScreen.Main || currentScreen == AppScreen.WindShift) {
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

    LaunchedEffect(activity) {
        val currentActivity = activity ?: return@LaunchedEffect
        currentActivity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
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
                if (windShiftStartElapsedRealtimeMs == null) {
                    windShiftStartElapsedRealtimeMs = SystemClock.elapsedRealtime()
                }
                if (isTrackRecording && raceStartEpochMillis == null) {
                    raceStartEpochMillis = System.currentTimeMillis()
                    raceStartLat = currentLocation?.latitude
                    raceStartLon = currentLocation?.longitude
                    buoysLockedAfterRaceStart = true
                    val interimExport = exportRaceTrackToGpx(
                        context = context,
                        points = raceTrackPoints,
                        raceStartEpochMillis = raceStartEpochMillis,
                        leftBuoyLat = leftBuoyLat,
                        leftBuoyLon = leftBuoyLon,
                        rightBuoyLat = rightBuoyLat,
                        rightBuoyLon = rightBuoyLon,
                        raceStartLat = raceStartLat,
                        raceStartLon = raceStartLon
                    )
                    if (interimExport != null) {
                        trackLogRefreshTick += 1
                        trackExportStatus = "Interim GPX saved: ${interimExport.name}"
                    }
                }
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
            if (showWelcomeScreen) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = {
                                    showWelcomeScreen = false
                                    MainActivity.hasShownWelcomeForCurrentProcess = true
                                }
                            )
                        }
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.welcome_aloha),
                        contentDescription = "Welcome",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize(0.6f)
                            .align(Alignment.Center)
                    )
                }
                return@Surface
            }
            if (
                currentScreen == AppScreen.StartLinePage ||
                (currentScreen == AppScreen.Main && startLineLayoutMode == StartLineLayoutMode.LayoutPage)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    StartLinePage()
                    Button(
                        onClick = { currentScreen = AppScreen.Settings },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp)
                    ) {
                        Text("Settings")
                    }
                }
                return@Surface
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.Top
            ) {
                if (currentScreen == AppScreen.TrackPreview) {
                    TrackPreviewScreen(
                        trackName = previewTrackName ?: "Track",
                        trackPoints = previewTrackPoints,
                        raceStartEpochMillis = previewRaceStartEpochMillis,
                        leftBuoy = previewLeftBuoy,
                        rightBuoy = previewRightBuoy,
                        raceStartPoint = previewRaceStartPoint,
                        renderMode = previewTrackRenderMode,
                        onToggleRenderMode = {
                            previewTrackRenderMode =
                                if (previewTrackRenderMode == TrackPreviewRenderMode.TrackOnly) {
                                    TrackPreviewRenderMode.OpenMap
                                } else {
                                    TrackPreviewRenderMode.TrackOnly
                                }
                        },
                        onClose = {
                            currentScreen = AppScreen.TrackLog
                        }
                    )
                    return@Column
                }

                Box(modifier = Modifier.fillMaxWidth()) {
                    if (isTrackRecording) {
                        Text(
                            text = "● REC",
                            color = Color.Red,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .pointerInput(currentScreen) {
                                    detectTapGestures(
                                        onTap = {
                                            if (currentScreen == AppScreen.Main) {
                                                currentScreen = AppScreen.WindShift
                                            } else if (currentScreen == AppScreen.WindShift) {
                                                currentScreen = AppScreen.Main
                                            }
                                        }
                                    )
                                }
                        ) {
                            Text(
                                text = screenTitle,
                                style = MaterialTheme.typography.headlineMedium
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (currentScreen == AppScreen.Main || currentScreen == AppScreen.WindShift) {
                                val speedLabel = averageTrueSpeedKnots?.let {
                                    String.format(Locale.US, "%.1f kn", it)
                                } ?: "--.- kn"
                                Text(
                                    text = speedLabel,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                            }
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
                                        text = { Text("StartLinePage") },
                                        onClick = {
                                            currentScreen = AppScreen.StartLinePage
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
                                    DropdownMenuItem(
                                        text = { Text("WindShift") },
                                        onClick = {
                                            currentScreen = AppScreen.WindShift
                                            menuExpanded = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Wind Debug") },
                                        onClick = {
                                            currentScreen = AppScreen.WindShiftDebug
                                            menuExpanded = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Track Log") },
                                        onClick = {
                                            currentScreen = AppScreen.TrackLog
                                            menuExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                if (currentScreen == AppScreen.WindShift) {
                    val trackHistoryMinutes = max(90L, windShiftWindowMinutes + 15L)
                    val windShiftReferenceCourseDeg = when (windAnalyzer.mode) {
                        Mode.DUAL -> windAnalyzer.centerCourse
                        Mode.SINGLE -> windAnalyzer.singleMeanCourse?.let { mean ->
                            val offset = if (windAnalyzer.monoSignInverted) 40.0 else -40.0
                            normalizeDegrees(mean + offset)
                        }
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            tonalElevation = 2.dp
                        ) {
                            WindShiftDeviationGraph(
                                points = windShiftSeries.value,
                                isMonoMode = windAnalyzer.mode == Mode.SINGLE,
                                monoSignInverted = windAnalyzer.monoSignInverted,
                                currentDeviationDeg = windAnalyzer.currentDeviationDeg,
                                modeLabel = if (windAnalyzer.mode == Mode.DUAL) "DUAL MODE" else "SINGLE MODE",
                                calcIntervalLabel = "Calc: ${windAnalyzer.debugInfo.calcWindowMinutes} min",
                                availableDataLabel = "Data: ${formatDuration(windAnalyzer.debugInfo.availableDataMs / 1000.0)}",
                                graphWindowMinutes = windShiftGraphWindowMinutes,
                                onIncreaseGraphWindow = {
                                    applyWindShiftGraphWindowMinutes(windShiftGraphWindowMinutes + 5L)
                                },
                                onDecreaseGraphWindow = {
                                    applyWindShiftGraphWindowMinutes(windShiftGraphWindowMinutes - 5L)
                                },
                                onToggleMonoSign = {
                                    if (windAnalyzer.mode == Mode.SINGLE) {
                                        windAnalyzer.toggleMonoSign()
                                        windShiftSeries.value = windAnalyzer.series
                                    }
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .pointerInput(windShiftWindowMinutes) {
                                    detectTapGestures(
                                        onTap = { tap ->
                                            val topHalf = tap.y < size.height / 2f
                                            val leftHalf = tap.x < size.width / 2f
                                            val inRightHalf = tap.x >= size.width / 2f
                                            if (leftHalf && topHalf) {
                                                windShiftTrackOrientation =
                                                    if (windShiftTrackOrientation == WindShiftTrackOrientation.NorthUp) {
                                                        WindShiftTrackOrientation.WindAxisUp
                                                    } else {
                                                        WindShiftTrackOrientation.NorthUp
                                                    }
                                                return@detectTapGestures
                                            }
                                            if (!inRightHalf) return@detectTapGestures

                                            val updated = if (topHalf) {
                                                windShiftWindowMinutes + 3L
                                            } else {
                                                windShiftWindowMinutes - 3L
                                            }
                                            applyWindShiftWindowMinutes(updated)
                                        }
                                    )
                                },
                            tonalElevation = 2.dp
                        ) {
                            WindShiftTrackGraph(
                                samples = gpsSamples,
                                activeWindowMinutes = windShiftWindowMinutes,
                                historyMinutes = trackHistoryMinutes,
                                orientation = windShiftTrackOrientation,
                                referenceCourseDeg = windShiftReferenceCourseDeg,
                                onResetActiveWindow = {
                                    applyWindShiftWindowMinutes(DEFAULT_WIND_SHIFT_WINDOW_MINUTES)
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                    return@Column
                }
                if (currentScreen == AppScreen.WindShiftDebug) {
                    WindShiftDebugScreen(
                        debug = windAnalyzer.debugInfo,
                        graphWindowMinutes = windShiftGraphWindowMinutes,
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(windDebugScrollState)
                    )
                    return@Column
                }

                if (currentScreen == AppScreen.TrackLog) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(trackLogScrollState),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (trackLogFiles.isEmpty()) {
                            Text("Nema spremljenih trackova.")
                        } else {
                            trackLogFiles.forEach { trackFile ->
                                Box {
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .combinedClickable(
                                                onClick = { },
                                                onLongClick = { trackMenuPath = trackFile.absolutePath }
                                            ),
                                        tonalElevation = 2.dp
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Text(
                                                text = trackFile.name,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = formatTrackMeta(
                                                    trackFile = trackFile,
                                                    distanceMeters = trackLogDistanceByPath[trackFile.absolutePath]
                                                ),
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                    }
                                    DropdownMenu(
                                        expanded = trackMenuPath == trackFile.absolutePath,
                                        onDismissRequest = { trackMenuPath = null }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Show on Map") },
                                            onClick = {
                                                val parsed = parseRaceTrackFromGpx(trackFile)
                                                previewTrackName = trackFile.name
                                                previewTrackPoints = parsed.points
                                                previewRaceStartEpochMillis = parsed.raceStartEpochMillis
                                                previewLeftBuoy = parsed.leftBuoy
                                                previewRightBuoy = parsed.rightBuoy
                                                previewRaceStartPoint = parsed.raceStartPoint
                                                previewTrackRenderMode = TrackPreviewRenderMode.TrackOnly
                                                trackExportStatus = "Loaded track: ${trackFile.name}"
                                                currentScreen = AppScreen.TrackPreview
                                                trackMenuPath = null
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Delete") },
                                            onClick = {
                                                pendingDeleteTrackPath = trackFile.absolutePath
                                                trackMenuPath = null
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Share") },
                                            onClick = {
                                                shareTrackFile(context, trackFile)
                                                trackMenuPath = null
                                            }
                                        )
                                    }
                                }
                            }
                        }
                        pendingDeleteTrackPath?.let { deletePath ->
                            val fileToDelete = File(deletePath)
                            AlertDialog(
                                onDismissRequest = { pendingDeleteTrackPath = null },
                                title = { Text("Delete track") },
                                text = { Text("Delete ${fileToDelete.name}?") },
                                confirmButton = {
                                    TextButton(
                                        onClick = {
                                            val deleted = fileToDelete.delete()
                                            trackExportStatus = if (deleted) {
                                                "Deleted: ${fileToDelete.name}"
                                            } else {
                                                "Delete failed: ${fileToDelete.name}"
                                            }
                                            trackLogRefreshTick += 1
                                            pendingDeleteTrackPath = null
                                        }
                                    ) {
                                        Text("Delete")
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { pendingDeleteTrackPath = null }) {
                                        Text("Cancel")
                                    }
                                }
                            )
                        }
                        Spacer(modifier = Modifier.height(80.dp))
                    }
                    return@Column
                }

                if (currentScreen == AppScreen.Settings) {
                    CompositionLocalProvider(
                        LocalTextStyle provides LocalTextStyle.current.copy(fontSize = 12.sp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(settingsScrollState)
                        ) {
                            Text(
                                text = "Settings",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Orijentacija: Portrait (zaključano)")
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("StartLine layout")
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { startLineLayoutMode = StartLineLayoutMode.Classic }) {
                                    Text("Classic")
                                }
                                Button(onClick = { startLineLayoutMode = StartLineLayoutMode.LayoutPage }) {
                                    Text("StartLinePage")
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Trenutni layout: ${startLineLayoutMode.label}")

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
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("WindShift prozor (min)")
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = windShiftWindowInput,
                                onValueChange = { input ->
                                    val sanitized = input.filter { it.isDigit() }
                                    windShiftWindowInput = sanitized
                                    val parsed = sanitized.toLongOrNull()
                                    when {
                                        sanitized.isBlank() -> {
                                            windShiftWindowError = "Unesi minute za WindShift."
                                        }

                                        parsed == null -> {
                                            windShiftWindowError = "Vrijednost nije valjana."
                                        }

                                        parsed < MIN_WIND_SHIFT_WINDOW_MINUTES -> {
                                            windShiftWindowError =
                                                "Minimum je $MIN_WIND_SHIFT_WINDOW_MINUTES min."
                                        }

                                        parsed > MAX_WIND_SHIFT_WINDOW_MINUTES -> {
                                            windShiftWindowError =
                                                "Maksimum je $MAX_WIND_SHIFT_WINDOW_MINUTES min."
                                        }

                                        else -> {
                                            applyWindShiftWindowMinutes(parsed)
                                        }
                                    }
                                },
                                label = { Text("WindShift (min)") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.width(180.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    applyWindShiftWindowMinutes(DEFAULT_WIND_SHIFT_WINDOW_MINUTES)
                                }
                            ) {
                                Text("Reset")
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Trenutno: ${windShiftWindowMinutes} min")
                        if (windShiftWindowError != null) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(windShiftWindowError!!)
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("WindShift smjer: razmak točaka (s)")
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = windShiftHeadingWindowInput,
                                onValueChange = { input ->
                                    val sanitized = input.filter { it.isDigit() }
                                    windShiftHeadingWindowInput = sanitized
                                    val parsed = sanitized.toLongOrNull()
                                    when {
                                        sanitized.isBlank() -> {
                                            windShiftHeadingWindowError = "Unesi sekunde za WindShift smjer."
                                        }
                                        parsed == null -> {
                                            windShiftHeadingWindowError = "Vrijednost nije valjana."
                                        }
                                        parsed < MIN_WIND_SHIFT_HEADING_WINDOW_SECONDS -> {
                                            windShiftHeadingWindowError =
                                                "Minimum je $MIN_WIND_SHIFT_HEADING_WINDOW_SECONDS s."
                                        }
                                        parsed > MAX_WIND_SHIFT_HEADING_WINDOW_SECONDS -> {
                                            windShiftHeadingWindowError =
                                                "Maksimum je $MAX_WIND_SHIFT_HEADING_WINDOW_SECONDS s."
                                        }
                                        else -> {
                                            applyWindShiftHeadingWindowSeconds(parsed)
                                        }
                                    }
                                },
                                label = { Text("Smjer (s)") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.width(180.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    applyWindShiftHeadingWindowSeconds(DEFAULT_WIND_SHIFT_HEADING_WINDOW_SECONDS)
                                }
                            ) {
                                Text("Reset")
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Trenutno: ${windShiftHeadingWindowSeconds} s")
                        if (windShiftHeadingWindowError != null) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(windShiftHeadingWindowError!!)
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("WindShift SD filter")
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { windShiftStdFilterMode = WindShiftStdFilterMode.Off }) {
                                Text("Isključeno")
                            }
                            Button(onClick = { windShiftStdFilterMode = WindShiftStdFilterMode.OneSigma }) {
                                Text(">1 SD")
                            }
                            Button(onClick = { windShiftStdFilterMode = WindShiftStdFilterMode.TwoSigma }) {
                                Text(">2 SD")
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Trenutno: ${windShiftStdFilterMode.label}")
                            Spacer(modifier = Modifier.height(120.dp))
                        }
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
                                        if (isCountdownRunning) {
                                            isCountdownRunning = false
                                        } else {
                                            isCountdownRunning = true
                                        }
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
                                    val exportFile = exportRaceTrackToGpx(
                                        context = context,
                                        points = raceTrackPoints,
                                        raceStartEpochMillis = raceStartEpochMillis,
                                        leftBuoyLat = leftBuoyLat,
                                        leftBuoyLon = leftBuoyLon,
                                        rightBuoyLat = rightBuoyLat,
                                        rightBuoyLon = rightBuoyLon,
                                        raceStartLat = raceStartLat,
                                        raceStartLon = raceStartLon
                                    )
                                    if (raceTrackPoints.isNotEmpty() || isTrackRecording) {
                                        trackExportStatus = if (exportFile != null) {
                                            "GPX saved: ${exportFile.name}"
                                        } else {
                                            "Track not saved (need at least 2 points)"
                                        }
                                    }
                                    if (exportFile != null) {
                                        trackLogRefreshTick += 1
                                    }
                                    isTrackRecording = false
                                    raceTrackPoints = emptyList()
                                    raceStartEpochMillis = null
                                    windShiftStartElapsedRealtimeMs = null
                                    raceStartLat = null
                                    raceStartLon = null
                                    buoysLockedAfterRaceStart = false
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
                        if (buoysLockedAfterRaceStart) return@onDoubleClickAction
                        if (leftBuoySet) {
                            leftBuoyLat = null
                            leftBuoyLon = null
                        } else {
                            val snapshot = currentLocation
                            if (snapshot != null) {
                                leftBuoyLat = snapshot.latitude
                                leftBuoyLon = snapshot.longitude
                                if (rightBuoySet) {
                                    startTrackRecording(snapshot)
                                }
                            }
                        }
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .height(92.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (leftBuoySet) Color(0xFF2E7D32) else Color(0xFFC62828),
                    contentColor = Color.White
                )
            ) {
                Text(
                    text = if (leftBuoySet) "✓\nL" else "Set\nL",
                    fontSize = 18.sp,
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
                        if (buoysLockedAfterRaceStart) return@onDoubleClickAction
                        if (rightBuoySet) {
                            rightBuoyLat = null
                            rightBuoyLon = null
                        } else {
                            val snapshot = currentLocation
                            if (snapshot != null) {
                                rightBuoyLat = snapshot.latitude
                                rightBuoyLon = snapshot.longitude
                                if (leftBuoySet) {
                                    startTrackRecording(snapshot)
                                }
                            }
                        }
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .height(92.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (rightBuoySet) Color(0xFF2E7D32) else Color(0xFFC62828),
                    contentColor = Color.White
                )
            ) {
                Text(
                    text = if (rightBuoySet) "✓\nR" else "Set\nR",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color.Transparent,
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(6.dp, statusFrameColor)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "${
                        signedBowDistanceToLineMeters?.let { String.format(Locale.US, "%.0f", it) } ?: "--"
                    } m",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${etaDeltaSeconds?.let { String.format(Locale.US, "%+d", it) } ?: "--"} sec",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        trackExportStatus?.let { status ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = status,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium
            )
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
                raceTrackPoints = raceTrackPoints,
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

private fun averageLocations(locations: List<Location>): Location {
    val count = locations.size.coerceAtLeast(1)
    val avgLat = locations.sumOf { it.latitude } / count.toDouble()
    val avgLon = locations.sumOf { it.longitude } / count.toDouble()
    return Location("distance_reference_avg").apply {
        latitude = avgLat
        longitude = avgLon
    }
}

private const val METERS_PER_SECOND_TO_KNOTS = 1.943844
private const val EARTH_RADIUS_METERS = 6_371_000.0
private const val DEFAULT_DOUBLE_CLICK_TIMEOUT_MS = 700L
private const val DEFAULT_GPS_TO_BOW_DISTANCE_METERS = 0.0
private const val DEFAULT_AVERAGE_WINDOW_SECONDS = 5L
private const val DEFAULT_COUNTDOWN_START_MINUTES = 5L
private const val DEFAULT_WIND_SHIFT_WINDOW_MINUTES = 2L
private const val MIN_WIND_SHIFT_WINDOW_MINUTES = 2L
private const val MAX_WIND_SHIFT_WINDOW_MINUTES = 180L
private const val DEFAULT_WIND_SHIFT_HEADING_WINDOW_SECONDS = 10L
private const val MIN_WIND_SHIFT_HEADING_WINDOW_SECONDS = 1L
private const val MAX_WIND_SHIFT_HEADING_WINDOW_SECONDS = 30L
private const val DEFAULT_WIND_SHIFT_GRAPH_WINDOW_MINUTES = 10L
private const val MIN_WIND_SHIFT_GRAPH_WINDOW_MINUTES = 5L
private const val MAX_GPS_SAMPLE_AGE_MS = (MAX_WIND_SHIFT_WINDOW_MINUTES + 15L) * 60_000L
private val HIGH_CONTRAST_YELLOW = Color(0xFFFFFF99)

private data class Point2D(val x: Double, val y: Double)
private data class GpsSample(val timestampMs: Long, val location: Location)
private data class RaceTrackPoint(
    val latitude: Double,
    val longitude: Double,
    val epochMillis: Long
)
private data class AverageMotion(val speedMps: Double, val headingDeg: Double?)
private data class LineDistanceInfo(
    val distanceMeters: Double,
    val signedDistanceMeters: Double,
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
            signedDistanceMeters = distance,
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
    val cross = abx * apy - aby * apx
    val signedDistance = if (cross < 0.0) -distance else distance
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
        signedDistanceMeters = signedDistance,
        normalTowardLine = normalTowardLine
    )
}

private fun projectToMeters(location: Location, referenceLatitude: Double): Point2D {
    return projectToMeters(location.latitude, location.longitude, referenceLatitude)
}

private fun projectToMeters(latitude: Double, longitude: Double, referenceLatitude: Double): Point2D {
    val latRad = latitude * PI / 180.0
    val lonRad = longitude * PI / 180.0
    val refLatRad = referenceLatitude * PI / 180.0
    val x = EARTH_RADIUS_METERS * lonRad * cos(refLatRad)
    val y = EARTH_RADIUS_METERS * latRad
    return Point2D(x = x, y = y)
}

private fun calculateAverageMotion(samples: List<GpsSample>): AverageMotion? {
    if (samples.size < 2) return null

    val first = samples.first()
    val last = samples.last()
    val dtSeconds = (last.timestampMs - first.timestampMs) / 1000.0
    if (dtSeconds <= 0.0) return null

    val distanceMeters = first.location.distanceTo(last.location).toDouble()
    val averageSpeedMps = distanceMeters / dtSeconds
    val averageHeadingDeg = normalizeDegrees(first.location.bearingTo(last.location).toDouble())
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
    StartLinePage,
    Settings,
    WindShift,
    WindShiftDebug,
    TrackLog,
    TrackPreview
}

private enum class ScreenMode {
    Light,
    Dark
}

private enum class MapMode {
    NorthUp,
    StartLineUp
}

private enum class MapRenderMode {
    Canvas,
    Osm
}

private enum class WindShiftTrackOrientation {
    NorthUp,
    WindAxisUp
}

private enum class TrackPreviewRenderMode {
    TrackOnly,
    OpenMap
}

private enum class StartLineLayoutMode(val label: String) {
    Classic("Classic"),
    LayoutPage("StartLinePage")
}

private enum class WindShiftStdFilterMode(val label: String, val sigmaMultiplier: Double?) {
    Off("Isključeno", null),
    OneSigma(">1 SD", 1.0),
    TwoSigma(">2 SD", 2.0)
}

@Composable
private fun StartLineMap(
    leftBuoyLocation: Location?,
    rightBuoyLocation: Location?,
    currentLocation: Location?,
    raceTrackPoints: List<RaceTrackPoint>,
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
            val trackPoints = raceTrackPoints.map {
                projectToMeters(it.latitude, it.longitude, refLat)
            }

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
                            raceTrackPoints.takeLast(500).map {
                                GeoPoint(it.latitude, it.longitude)
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
        Text(
            text = "zoom in",
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 8.dp, end = 8.dp),
            color = Color.White,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "zoom out",
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 8.dp, end = 8.dp),
            color = Color.White,
            fontWeight = FontWeight.Bold
        )
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

private fun exportRaceTrackToGpx(
    context: Context,
    points: List<RaceTrackPoint>,
    raceStartEpochMillis: Long?,
    leftBuoyLat: Double?,
    leftBuoyLon: Double?,
    rightBuoyLat: Double?,
    rightBuoyLon: Double?,
    raceStartLat: Double?,
    raceStartLon: Double?
): File? {
    if (points.size < 2) return null

    val gpxDir = File(context.filesDir, "gpx").apply { mkdirs() }
    val fileName = formatTrackFileName(points.first().epochMillis)
    val gpxFile = File(gpxDir, fileName)
    gpxFile.writeText(
        buildGpxContent(
            points = points,
            raceStartEpochMillis = raceStartEpochMillis,
            leftBuoyLat = leftBuoyLat,
            leftBuoyLon = leftBuoyLon,
            rightBuoyLat = rightBuoyLat,
            rightBuoyLon = rightBuoyLon,
            raceStartLat = raceStartLat,
            raceStartLon = raceStartLon
        )
    )
    return gpxFile
}

private fun formatTrackFileName(epochMillis: Long): String {
    val formatter = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US)
    return "${formatter.format(Date(epochMillis))}.gpx"
}

private fun buildGpxContent(
    points: List<RaceTrackPoint>,
    raceStartEpochMillis: Long?,
    leftBuoyLat: Double?,
    leftBuoyLon: Double?,
    rightBuoyLat: Double?,
    rightBuoyLon: Double?,
    raceStartLat: Double?,
    raceStartLon: Double?
): String {
    val isoFormatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = java.util.TimeZone.getTimeZone("UTC")
    }
    val builder = StringBuilder()
    builder.append("""<?xml version="1.0" encoding="UTF-8"?>""").append('\n')
    builder.append(
        """<gpx version="1.1" creator="StartLine" xmlns="http://www.topografix.com/GPX/1/1" xmlns:startline="https://startline.app/extensions">"""
    ).append('\n')
    if (leftBuoyLat != null && leftBuoyLon != null) {
        builder.append("  <wpt lat=\"")
            .append(String.format(Locale.US, "%.8f", leftBuoyLat))
            .append("\" lon=\"")
            .append(String.format(Locale.US, "%.8f", leftBuoyLon))
            .append("\">\n")
        builder.append("    <name>left_buoy</name>\n")
        builder.append("  </wpt>\n")
    }
    if (rightBuoyLat != null && rightBuoyLon != null) {
        builder.append("  <wpt lat=\"")
            .append(String.format(Locale.US, "%.8f", rightBuoyLat))
            .append("\" lon=\"")
            .append(String.format(Locale.US, "%.8f", rightBuoyLon))
            .append("\">\n")
        builder.append("    <name>right_buoy</name>\n")
        builder.append("  </wpt>\n")
    }
    if (raceStartLat != null && raceStartLon != null) {
        builder.append("  <wpt lat=\"")
            .append(String.format(Locale.US, "%.8f", raceStartLat))
            .append("\" lon=\"")
            .append(String.format(Locale.US, "%.8f", raceStartLon))
            .append("\">\n")
        builder.append("    <name>race_start_point</name>\n")
        raceStartEpochMillis?.let { startMs ->
            builder.append("    <time>")
                .append(isoFormatter.format(Date(startMs)))
                .append("</time>\n")
        }
        builder.append("  </wpt>\n")
    }
    builder.append("  <trk>\n")
    builder.append("    <name>StartLine track</name>\n")
    raceStartEpochMillis?.let { startMs ->
        builder.append("    <extensions>\n")
        builder.append("      <startline:raceStartTime>")
            .append(isoFormatter.format(Date(startMs)))
            .append("</startline:raceStartTime>\n")
        if (leftBuoyLat != null && leftBuoyLon != null) {
            builder.append("      <startline:leftBuoyLat>")
                .append(String.format(Locale.US, "%.8f", leftBuoyLat))
                .append("</startline:leftBuoyLat>\n")
            builder.append("      <startline:leftBuoyLon>")
                .append(String.format(Locale.US, "%.8f", leftBuoyLon))
                .append("</startline:leftBuoyLon>\n")
        }
        if (rightBuoyLat != null && rightBuoyLon != null) {
            builder.append("      <startline:rightBuoyLat>")
                .append(String.format(Locale.US, "%.8f", rightBuoyLat))
                .append("</startline:rightBuoyLat>\n")
            builder.append("      <startline:rightBuoyLon>")
                .append(String.format(Locale.US, "%.8f", rightBuoyLon))
                .append("</startline:rightBuoyLon>\n")
        }
        if (raceStartLat != null && raceStartLon != null) {
            builder.append("      <startline:raceStartLat>")
                .append(String.format(Locale.US, "%.8f", raceStartLat))
                .append("</startline:raceStartLat>\n")
            builder.append("      <startline:raceStartLon>")
                .append(String.format(Locale.US, "%.8f", raceStartLon))
                .append("</startline:raceStartLon>\n")
        }
        builder.append("    </extensions>\n")
    }
    builder.append("    <trkseg>\n")
    points.forEach { point ->
        builder.append("      <trkpt lat=\"")
            .append(String.format(Locale.US, "%.8f", point.latitude))
            .append("\" lon=\"")
            .append(String.format(Locale.US, "%.8f", point.longitude))
            .append("\">\n")
        builder.append("        <time>")
            .append(isoFormatter.format(Date(point.epochMillis)))
            .append("</time>\n")
        builder.append("      </trkpt>\n")
    }
    builder.append("    </trkseg>\n")
    builder.append("  </trk>\n")
    builder.append("</gpx>\n")
    return builder.toString()
}

private fun listSavedTrackFiles(context: Context): List<File> {
    val gpxDir = File(context.filesDir, "gpx")
    if (!gpxDir.exists()) return emptyList()
    return gpxDir.listFiles { file ->
        file.isFile && file.extension.equals("gpx", ignoreCase = true)
    }?.sortedByDescending { it.lastModified() } ?: emptyList()
}

private fun formatTrackMeta(trackFile: File, distanceMeters: Double?): String {
    val modified = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(trackFile.lastModified()))
    val sizeKb = (trackFile.length() / 1024L).coerceAtLeast(1L)
    val distanceText = if (distanceMeters != null) {
        "${String.format(Locale.US, "%.0f", distanceMeters)} m"
    } else {
        "-"
    }
    return "$modified  •  $distanceText  •  ${sizeKb} KB"
}

private fun parseTrackDistanceMeters(trackFile: File): Double? {
    val points = runCatching { parseRaceTrackFromGpx(trackFile).points }
        .getOrDefault(emptyList())
    if (points.size < 2) return null
    var totalDistance = 0.0
    for (index in 1 until points.size) {
        val prev = points[index - 1]
        val curr = points[index]
        totalDistance += distanceMeters(prev.latitude, prev.longitude, curr.latitude, curr.longitude)
    }
    return totalDistance
}

private fun gpsSamplesToCogSamples(samples: List<GpsSample>): List<CogSample> {
    if (samples.size < 2) return emptyList()
    val sorted = samples.sortedBy { it.timestampMs }
    return (1 until sorted.size).mapNotNull { index ->
        val prev = sorted[index - 1]
        val curr = sorted[index]
        val dtSeconds = (curr.timestampMs - prev.timestampMs) / 1000.0
        if (dtSeconds <= 0.0) return@mapNotNull null
        val distanceMeters = prev.location.distanceTo(curr.location).toDouble()
        val speedKnots = (distanceMeters / dtSeconds) * METERS_PER_SECOND_TO_KNOTS
        val cogDeg = normalizeDegrees(prev.location.bearingTo(curr.location).toDouble())
        CogSample(
            timestampMs = curr.timestampMs,
            cogDeg = cogDeg,
            sogKnots = speedKnots
        )
    }
}

private fun gpsSamplesToWindShiftCogSamples(
    samples: List<GpsSample>,
    startElapsedRealtimeMs: Long,
    avgSpeedWindowSeconds: Long,
    minAverageSpeedKnots: Double
): List<CogSample> {
    if (samples.size < 2) return emptyList()
    val sorted = samples
        .asSequence()
        .filter { it.timestampMs >= startElapsedRealtimeMs }
        .sortedBy { it.timestampMs }
        .toList()
    if (sorted.size < 2) return emptyList()

    val avgWindowMs = avgSpeedWindowSeconds.coerceAtLeast(1L) * 1_000L
    val rawSamples = (1 until sorted.size).mapNotNull { index ->
        val curr = sorted[index]
        val targetTs = curr.timestampMs - avgWindowMs
        if (targetTs < sorted.first().timestampMs) return@mapNotNull null

        // Use two points separated by ~avgWindowMs (default 5s) for COG/SOG.
        var anchorIndex = -1
        var bestDiffMs = Long.MAX_VALUE
        for (candidateIndex in 0 until index) {
            val diffMs = kotlin.math.abs(sorted[candidateIndex].timestampMs - targetTs)
            if (diffMs < bestDiffMs) {
                bestDiffMs = diffMs
                anchorIndex = candidateIndex
            }
        }
        if (anchorIndex < 0) return@mapNotNull null
        val anchor = sorted[anchorIndex]

        val dtSeconds = (curr.timestampMs - anchor.timestampMs) / 1000.0
        if (dtSeconds <= 0.0) return@mapNotNull null

        val distanceMeters = anchor.location.distanceTo(curr.location).toDouble()
        val avgSpeedKnots = (distanceMeters / dtSeconds) * METERS_PER_SECOND_TO_KNOTS
        if (avgSpeedKnots < minAverageSpeedKnots) return@mapNotNull null

        val cogDeg = normalizeDegrees(anchor.location.bearingTo(curr.location).toDouble())
        CogSample(
            timestampMs = curr.timestampMs,
            cogDeg = cogDeg,
            sogKnots = avgSpeedKnots
        )
    }
    return aggregateWindShiftCogSamplesByStep(
        samples = rawSamples,
        stepSeconds = 10L,
        minAverageSpeedKnots = minAverageSpeedKnots
    )
}

private fun aggregateWindShiftCogSamplesByStep(
    samples: List<CogSample>,
    stepSeconds: Long,
    minAverageSpeedKnots: Double
): List<CogSample> {
    if (samples.isEmpty()) return emptyList()
    val bucketMs = stepSeconds.coerceAtLeast(1L) * 1_000L
    return samples
        .sortedBy { it.timestampMs }
        .groupBy { it.timestampMs / bucketMs }
        .toSortedMap()
        .values
        .mapNotNull { bucket ->
            val meanCog = ShiftWindAnalyzer.circularMean(bucket.map { it.cogDeg }) ?: return@mapNotNull null
            val meanSpeed = bucket.map { it.sogKnots }.average()
            if (meanSpeed < minAverageSpeedKnots) return@mapNotNull null
            CogSample(
                timestampMs = bucket.last().timestampMs,
                cogDeg = meanCog,
                sogKnots = meanSpeed
            )
        }
}

@Composable
private fun WindShiftDeviationGraph(
    points: List<DeviationPoint>,
    isMonoMode: Boolean,
    monoSignInverted: Boolean,
    currentDeviationDeg: Double?,
    modeLabel: String,
    calcIntervalLabel: String,
    availableDataLabel: String,
    graphWindowMinutes: Long,
    onIncreaseGraphWindow: () -> Unit,
    onDecreaseGraphWindow: () -> Unit,
    onToggleMonoSign: () -> Unit,
    modifier: Modifier = Modifier
) {
    val deviationThresholdDeg = 4.0
    val trendBarHeight = 12.dp
    val trendColor = when {
        currentDeviationDeg == null -> Color(0xFF78909C)
        kotlin.math.abs(currentDeviationDeg) <= deviationThresholdDeg -> Color(0xFF78909C)
        currentDeviationDeg > deviationThresholdDeg -> Color(0xFF2E7D32) // prema vjetru
        currentDeviationDeg < -deviationThresholdDeg -> Color(0xFFC62828) // od vjetra
        else -> Color(0xFF78909C)
    }

    Box(
        modifier = modifier
            .pointerInput(isMonoMode, monoSignInverted, graphWindowMinutes) {
                detectTapGestures(
                    onTap = { tap ->
                        val inRightHalf = tap.x >= size.width / 2f
                        if (inRightHalf) {
                            val inTopHalf = tap.y < size.height / 2f
                            if (inTopHalf) {
                                onIncreaseGraphWindow()
                            } else {
                                onDecreaseGraphWindow()
                            }
                            return@detectTapGestures
                        }
                        if (isMonoMode) {
                            val inLeftBottomQuarter = tap.x < size.width / 2f && tap.y >= size.height / 2f
                            if (inLeftBottomQuarter) {
                                onToggleMonoSign()
                            }
                        }
                    }
                )
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(trendBarHeight)
                .align(Alignment.TopCenter)
                .background(trendColor)
        )
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = trendBarHeight)
        ) {
            drawRect(Color(0xFF0E1E2F))
            val centerX = size.width / 2f
            val rightLabelX = size.width - 28f
            val labelPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.WHITE
                textSize = 24f
                isFakeBoldText = true
            }
            val controlsPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.WHITE
                textSize = 40f
                isFakeBoldText = true
                textAlign = android.graphics.Paint.Align.CENTER
            }

            drawLine(
                color = Color(0xFFFFEB3B),
                start = Offset(centerX, 0f),
                end = Offset(centerX, size.height),
                strokeWidth = 2f
            )
            drawContext.canvas.nativeCanvas.drawText("-40°", 12f, 56f, labelPaint)
            drawContext.canvas.nativeCanvas.drawText("0°", centerX - 14f, 56f, labelPaint)
            drawContext.canvas.nativeCanvas.drawText("+40°", size.width - 86f, 56f, labelPaint)
            drawContext.canvas.nativeCanvas.drawText("+", rightLabelX, size.height * 0.25f, controlsPaint)
            drawContext.canvas.nativeCanvas.drawText("-", rightLabelX, size.height * 0.75f, controlsPaint)

            if (isMonoMode) {
                val activeSideLabel = if (monoSignInverted) "STBD +" else "PORT +"
                val tackLabel = if (monoSignInverted) "desne uzde" else "lijeve uzde"
                drawContext.canvas.nativeCanvas.drawText(
                    activeSideLabel,
                    12f,
                    size.height - 42f,
                    labelPaint
                )
                drawContext.canvas.nativeCanvas.drawText(
                    tackLabel,
                    12f,
                    size.height - 16f,
                    labelPaint
                )
            }
            drawContext.canvas.nativeCanvas.drawText(
                modeLabel,
                12f,
                28f,
                labelPaint
            )
            drawContext.canvas.nativeCanvas.drawText(
                calcIntervalLabel,
                12f,
                54f,
                labelPaint
            )
            drawContext.canvas.nativeCanvas.drawText(
                availableDataLabel,
                12f,
                80f,
                labelPaint
            )
            val windowMs = (graphWindowMinutes * 60_000L).toDouble()
            val timePaint = android.graphics.Paint().apply {
                color = android.graphics.Color.LTGRAY
                textSize = 22f
                isFakeBoldText = false
            }
            repeat(6) { index ->
                val fraction = index / 5f
                val y = fraction * size.height
                val minutesBack = fraction * graphWindowMinutes.toFloat()
                val label = if (index == 0) {
                    "0m"
                } else {
                    "-${minutesBack.roundToInt()}m"
                }
                drawContext.canvas.nativeCanvas.drawText(
                    label,
                    centerX + 10f,
                    y + 8f,
                    timePaint
                )
            }

            if (points.size < 2) {
                drawContext.canvas.nativeCanvas.drawText(
                    "Nema dovoljno podataka za graf",
                    20f,
                    size.height / 2f,
                    labelPaint.apply { textSize = 30f }
                )
                return@Canvas
            }

            val sorted = points.sortedBy { it.timestampMs }
            val latestTs = sorted.last().timestampMs.toDouble()
            val windowStart = latestTs - windowMs
            val visiblePoints = sorted.filter { it.timestampMs >= windowStart }
            if (visiblePoints.size < 2) {
                drawContext.canvas.nativeCanvas.drawText(
                    "Nema dovoljno podataka za ${graphWindowMinutes} min",
                    20f,
                    size.height / 2f,
                    labelPaint.apply { textSize = 30f }
                )
                return@Canvas
            }

            val maxAbsDeviation = 40.0
            val halfWidth = size.width / 2f - 16f

            val leftGuideX = centerX - halfWidth
            val rightGuideX = centerX + halfWidth
            drawLine(
                color = Color(0x44FFFFFF),
                start = Offset(leftGuideX, 0f),
                end = Offset(leftGuideX, size.height),
                strokeWidth = 1f
            )
            drawLine(
                color = Color(0x44FFFFFF),
                start = Offset(rightGuideX, 0f),
                end = Offset(rightGuideX, size.height),
                strokeWidth = 1f
            )

            fun toOffset(point: DeviationPoint): Offset {
                val elapsedFromLatest = (latestTs - point.timestampMs).coerceAtLeast(0.0)
                val timeFraction = (elapsedFromLatest / windowMs).coerceIn(0.0, 1.0).toFloat()
                val y = timeFraction * size.height // newer top, older bottom
                val x = centerX + (point.deviationDeg / maxAbsDeviation).toFloat() * halfWidth
                return Offset(x, y)
            }

            visiblePoints.zipWithNext().forEach { (a, b) ->
                drawLine(
                    color = Color(0xFF6FC3FF),
                    start = toOffset(a),
                    end = toOffset(b),
                    strokeWidth = 3f
                )
            }

        }
    }
}

@Composable
private fun WindShiftTrackGraph(
    samples: List<GpsSample>,
    activeWindowMinutes: Long,
    historyMinutes: Long,
    orientation: WindShiftTrackOrientation,
    referenceCourseDeg: Double?,
    onResetActiveWindow: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize().padding(8.dp)) {
            drawRect(Color(0xFF0E1E2F))
            val controlsPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.WHITE
                textSize = 40f
                isFakeBoldText = true
                textAlign = android.graphics.Paint.Align.CENTER
            }
            val rightLabelX = size.width - 24f
            drawContext.canvas.nativeCanvas.drawText("+", rightLabelX, size.height * 0.25f, controlsPaint)
            drawContext.canvas.nativeCanvas.drawText("-", rightLabelX, size.height * 0.75f, controlsPaint)
            if (samples.size < 2) {
                drawContext.canvas.nativeCanvas.drawText(
                    "Nema dovoljno podataka za track",
                    20f,
                    size.height / 2f,
                    android.graphics.Paint().apply {
                        color = android.graphics.Color.WHITE
                        textSize = 30f
                        isFakeBoldText = true
                    }
                )
                return@Canvas
            }

            val sorted = samples.sortedBy { it.timestampMs }
            val latestTimestamp = sorted.last().timestampMs
            val historyStart = latestTimestamp - historyMinutes * 60_000L
            val activeStart = latestTimestamp - activeWindowMinutes * 60_000L
            val historySamples = sorted.filter { it.timestampMs >= historyStart }

            if (historySamples.size < 2) {
                drawContext.canvas.nativeCanvas.drawText(
                    "Nema dovoljno podataka u zadnjih ${historyMinutes} min",
                    20f,
                    size.height / 2f,
                    android.graphics.Paint().apply {
                        color = android.graphics.Color.WHITE
                        textSize = 28f
                        isFakeBoldText = true
                    }
                )
                return@Canvas
            }

            val refLat = historySamples.first().location.latitude
            val projectedRaw = historySamples.map { sample ->
                sample to projectToMeters(sample.location, refLat)
            }
            val rawMinX = projectedRaw.minOf { it.second.x }
            val rawMaxX = projectedRaw.maxOf { it.second.x }
            val rawMinY = projectedRaw.minOf { it.second.y }
            val rawMaxY = projectedRaw.maxOf { it.second.y }
            val rawCenter = Point2D(
                x = (rawMinX + rawMaxX) / 2.0,
                y = (rawMinY + rawMaxY) / 2.0
            )
            val rotationRad = if (orientation == WindShiftTrackOrientation.WindAxisUp && referenceCourseDeg != null) {
                Math.toRadians(referenceCourseDeg)
            } else {
                0.0
            }
            val projected = projectedRaw.map { (sample, point) ->
                sample to rotateAround(point, rawCenter, rotationRad)
            }

            val minX = projected.minOf { it.second.x }
            val maxX = projected.maxOf { it.second.x }
            val minY = projected.minOf { it.second.y }
            val maxY = projected.maxOf { it.second.y }
            val spanX = (maxX - minX).coerceAtLeast(20.0)
            val spanY = (maxY - minY).coerceAtLeast(20.0)
            val pad = 12f
            val scaleX = (size.width - 2 * pad) / spanX.toFloat()
            val scaleY = (size.height - 2 * pad) / spanY.toFloat()
            val scale = minOf(scaleX, scaleY)
            val centerDataX = (minX + maxX) / 2.0
            val centerDataY = (minY + maxY) / 2.0
            val latestPoint = projected.last().second

            fun toOffset(point: Point2D): Offset {
                return if (orientation == WindShiftTrackOrientation.WindAxisUp) {
                    val dx = (point.x - latestPoint.x).toFloat() * scale
                    val dy = (point.y - latestPoint.y).toFloat() * scale
                    val anchorY = (size.height * 0.12f).coerceAtLeast(24f)
                    Offset(
                        x = size.width / 2f + dx,
                        y = anchorY - dy // latest boat point pinned near the top area
                    )
                } else {
                    val dx = (point.x - centerDataX).toFloat() * scale
                    val dy = (point.y - centerDataY).toFloat() * scale
                    Offset(
                        x = size.width / 2f + dx,
                        y = size.height / 2f - dy
                    )
                }
            }

            projected.zipWithNext().forEach { (a, b) ->
                drawLine(
                    color = Color(0xFF6D8FA8),
                    start = toOffset(a.second),
                    end = toOffset(b.second),
                    strokeWidth = 3.8f
                )
            }

            projected.zipWithNext().forEach { (a, b) ->
                if (b.first.timestampMs >= activeStart) {
                    drawLine(
                        color = Color(0xFFC7F5FF),
                        start = toOffset(a.second),
                        end = toOffset(b.second),
                        strokeWidth = 7.2f
                    )
                }
            }
        }

        Text(
            text = "${activeWindowMinutes} min",
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 8.dp)
                .pointerInput(activeWindowMinutes) {
                    detectTapGestures(
                        onDoubleTap = {
                            onResetActiveWindow()
                        }
                    )
                },
            color = Color.White,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = if (orientation == WindShiftTrackOrientation.NorthUp) "North up" else "Wind axis up",
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 8.dp, top = 8.dp),
            color = Color.White,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun WindShiftDebugScreen(
    debug: WindShiftDebugInfo,
    graphWindowMinutes: Long,
    modifier: Modifier = Modifier
) {
    fun angle(value: Double?): String =
        value?.let { String.format(Locale.US, "%.1f°", normalizeDegrees(it)) } ?: "--"
    fun delta(value: Double?): String =
        value?.let { String.format(Locale.US, "%+.1f°", it) } ?: "--"

    Column(
        modifier = modifier.padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text("Mode: ${if (debug.mode == Mode.DUAL) "DUAL" else "SINGLE"}", fontWeight = FontWeight.Bold)
        Text("Calc interval: ${debug.calcWindowMinutes} min")
        Text("Graph window: ${graphWindowMinutes} min")
        Text("Available data: ${formatDuration(debug.availableDataMs / 1000.0)}")
        Text("Samples: ${debug.sampleCount}")
        Text("Current 5s heading: ${angle(debug.current5sHeadingDeg)}")
        HorizontalDivider()
        Text("SINGLE mean: ${angle(debug.singleMeanCourseDeg)}")
        Text("SINGLE deviation: ${delta(debug.singleDeviationDeg)}")
        HorizontalDivider()
        Text("DUAL port mean: ${angle(debug.portMeanCourseDeg)}")
        Text("DUAL starboard mean: ${angle(debug.starboardMeanCourseDeg)}")
        Text("Wind axis (mid): ${angle(debug.windAxisCourseDeg)}")
        Text("Port offset: ${delta(debug.portOffsetDeg)}")
        Text("Starboard offset: ${delta(debug.starboardOffsetDeg)}")
        Text("Target port: ${angle(debug.targetPortDeg)}")
        Text("Target starboard: ${angle(debug.targetStarboardDeg)}")
        Text("Current diff->port: ${delta(debug.diffToPortDeg)}")
        Text("Current diff->starboard: ${delta(debug.diffToStarboardDeg)}")
        if (debug.notes.isNotEmpty()) {
            HorizontalDivider()
            Text("Notes:", fontWeight = FontWeight.Bold)
            debug.notes.forEach { note ->
                Text("- $note")
            }
        }
    }
}

@Composable
private fun TrackPreviewScreen(
    trackName: String,
    trackPoints: List<RaceTrackPoint>,
    raceStartEpochMillis: Long?,
    leftBuoy: Point2D?,
    rightBuoy: Point2D?,
    raceStartPoint: Point2D?,
    renderMode: TrackPreviewRenderMode,
    onToggleRenderMode: () -> Unit,
    onClose: () -> Unit
) {
    val stats = remember(trackPoints, raceStartEpochMillis) {
        computeTrackSummary(trackPoints, raceStartEpochMillis)
    }
    var canvasUserScale by remember(trackName, trackPoints.size) { mutableFloatStateOf(1f) }
    var canvasUserPan by remember(trackName, trackPoints.size) { mutableStateOf(Offset.Zero) }
    val topToggleAreaHeightPx = with(LocalDensity.current) { 56.dp.toPx() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(renderMode, trackPoints, topToggleAreaHeightPx) {
                detectTapGestures(
                    onDoubleTap = { tap ->
                        if (tap.y > topToggleAreaHeightPx) {
                            onClose()
                        }
                    }
                )
            }
    ) {
        if (renderMode == TrackPreviewRenderMode.OpenMap) {
            TrackPreviewOpenMap(
                trackPoints = trackPoints,
                leftBuoy = leftBuoy,
                rightBuoy = rightBuoy,
                raceStartPoint = raceStartPoint,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            TrackPreviewCanvasMap(
                trackPoints = trackPoints,
                leftBuoy = leftBuoy,
                rightBuoy = rightBuoy,
                raceStartPoint = raceStartPoint,
                userScale = canvasUserScale,
                userPan = canvasUserPan,
                onTransform = { panDelta, zoomFactor ->
                    val nextScale = (canvasUserScale * zoomFactor).coerceIn(0.5f, 12f)
                    canvasUserScale = nextScale
                    canvasUserPan = canvasUserPan + panDelta
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        Surface(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
                .pointerInput(renderMode) {
                    detectTapGestures(
                        onTap = {
                            onToggleRenderMode()
                        }
                    )
                },
            color = Color(0xAA000000)
        ) {
            Text(
                text = "OpenMap",
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }

        Surface(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(8.dp),
            color = Color(0xAA000000)
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Text(trackName, color = Color.White, fontWeight = FontWeight.Bold)
                Text(
                    "Trajanje: ${formatDuration(stats.durationSeconds)}",
                    color = Color.White
                )
                Text(
                    "Put: ${String.format(Locale.US, "%.0f m", stats.distanceMeters)}",
                    color = Color.White
                )
                Text(
                    "Prosj. brzina: ${String.format(Locale.US, "%.1f kn", stats.averageSpeedKnots)}",
                    color = Color.White
                )
                Text(
                    "Max brzina: ${String.format(Locale.US, "%.1f kn", stats.maxSpeedKnots)}",
                    color = Color.White
                )
            }
        }
    }
}

@Composable
private fun TrackPreviewCanvasMap(
    trackPoints: List<RaceTrackPoint>,
    leftBuoy: Point2D?,
    rightBuoy: Point2D?,
    raceStartPoint: Point2D?,
    userScale: Float,
    userPan: Offset,
    onTransform: (panDelta: Offset, zoomFactor: Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier.pointerInput(onTransform) {
            detectTransformGestures { _, pan, zoom, _ ->
                onTransform(pan, zoom)
            }
        }
    ) {
        drawRect(color = Color(0xFF0E1E2F))
        val refLat = trackPoints.firstOrNull()?.latitude
            ?: leftBuoy?.x
            ?: rightBuoy?.x
            ?: raceStartPoint?.x
            ?: return@Canvas

        val projectedTrack = trackPoints.map { projectToMeters(it.latitude, it.longitude, refLat) }
        val projectedLeftBuoy = leftBuoy?.let { projectToMeters(it.x, it.y, refLat) }
        val projectedRightBuoy = rightBuoy?.let { projectToMeters(it.x, it.y, refLat) }
        val projectedRaceStart = raceStartPoint?.let { projectToMeters(it.x, it.y, refLat) }
        val allProjected = buildList {
            addAll(projectedTrack)
            projectedLeftBuoy?.let { add(it) }
            projectedRightBuoy?.let { add(it) }
            projectedRaceStart?.let { add(it) }
        }
        if (allProjected.isEmpty()) return@Canvas

        val minX = allProjected.minOf { it.x }
        val maxX = allProjected.maxOf { it.x }
        val minY = allProjected.minOf { it.y }
        val maxY = allProjected.maxOf { it.y }
        val spanX = (maxX - minX).coerceAtLeast(20.0)
        val spanY = (maxY - minY).coerceAtLeast(20.0)
        val pad = 16f
        val scaleX = (size.width - 2f * pad) / spanX.toFloat()
        val scaleY = (size.height - 2f * pad) / spanY.toFloat()
        val scale = minOf(scaleX, scaleY)
        val centerDataX = (minX + maxX) / 2.0
        val centerDataY = (minY + maxY) / 2.0

        fun toOffset(point: Point2D): Offset {
            val dx = (point.x - centerDataX).toFloat() * scale
            val dy = (point.y - centerDataY).toFloat() * scale
            val base = Offset(size.width / 2f + dx, size.height / 2f - dy)
            val center = Offset(size.width / 2f, size.height / 2f)
            val relative = base - center
            return center + relative * userScale + userPan
        }

        projectedTrack.zipWithNext().forEach { (a, b) ->
            drawLine(
                color = Color(0xFF9CEBFF),
                start = toOffset(a),
                end = toOffset(b),
                strokeWidth = 4f
            )
        }
        if (projectedLeftBuoy != null && projectedRightBuoy != null) {
            drawLine(
                color = Color(0xFFFFEB3B),
                start = toOffset(projectedLeftBuoy),
                end = toOffset(projectedRightBuoy),
                strokeWidth = 5f
            )
        }

        projectedLeftBuoy?.let {
            drawCircle(color = Color(0xFFEF5350), radius = 13f, center = toOffset(it))
        }
        projectedRightBuoy?.let {
            drawCircle(color = Color(0xFF66BB6A), radius = 13f, center = toOffset(it))
        }
        projectedRaceStart?.let {
            val center = toOffset(it)
            drawCircle(color = Color(0xFFFFD54F), radius = 12f, center = center)
            drawCircle(color = Color.Black, radius = 12f, center = center, style = Stroke(width = 2.5f))
        }
    }
}

@Composable
private fun TrackPreviewOpenMap(
    trackPoints: List<RaceTrackPoint>,
    leftBuoy: Point2D?,
    rightBuoy: Point2D?,
    raceStartPoint: Point2D?,
    modifier: Modifier = Modifier
) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            org.osmdroid.config.Configuration.getInstance().userAgentValue = ctx.packageName
            MapView(ctx).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                isTilesScaledToDpi = true
                controller.setZoom(15.0)
            }
        },
        update = { mapView ->
            mapView.overlays.clear()
            when {
                trackPoints.isNotEmpty() -> {
                    val center = trackPoints[trackPoints.size / 2]
                    mapView.controller.setCenter(GeoPoint(center.latitude, center.longitude))
                }
                raceStartPoint != null -> {
                    mapView.controller.setCenter(GeoPoint(raceStartPoint.x, raceStartPoint.y))
                }
                leftBuoy != null -> {
                    mapView.controller.setCenter(GeoPoint(leftBuoy.x, leftBuoy.y))
                }
                rightBuoy != null -> {
                    mapView.controller.setCenter(GeoPoint(rightBuoy.x, rightBuoy.y))
                }
            }
            if (trackPoints.size >= 2) {
                val overlay = Polyline(mapView).apply {
                    outlinePaint.color = android.graphics.Color.CYAN
                    outlinePaint.strokeWidth = 5f
                    setPoints(trackPoints.map { GeoPoint(it.latitude, it.longitude) })
                }
                mapView.overlays.add(overlay)
            }
            if (leftBuoy != null && rightBuoy != null) {
                val buoyLine = Polyline(mapView).apply {
                    outlinePaint.color = android.graphics.Color.YELLOW
                    outlinePaint.strokeWidth = 6f
                    setPoints(
                        listOf(
                            GeoPoint(leftBuoy.x, leftBuoy.y),
                            GeoPoint(rightBuoy.x, rightBuoy.y)
                        )
                    )
                }
                mapView.overlays.add(buoyLine)
            }
            leftBuoy?.let { point ->
                mapView.overlays.add(
                    createOsmCircleOverlay(
                        mapView = mapView,
                        geoPoint = GeoPoint(point.x, point.y),
                        fillColor = android.graphics.Color.argb(220, 239, 83, 80),
                        strokeColor = android.graphics.Color.RED
                    )
                )
            }
            rightBuoy?.let { point ->
                mapView.overlays.add(
                    createOsmCircleOverlay(
                        mapView = mapView,
                        geoPoint = GeoPoint(point.x, point.y),
                        fillColor = android.graphics.Color.argb(220, 102, 187, 106),
                        strokeColor = android.graphics.Color.GREEN
                    )
                )
            }
            raceStartPoint?.let { point ->
                mapView.overlays.add(
                    createOsmCircleOverlay(
                        mapView = mapView,
                        geoPoint = GeoPoint(point.x, point.y),
                        fillColor = android.graphics.Color.argb(220, 255, 213, 79),
                        strokeColor = android.graphics.Color.BLACK
                    )
                )
            }
            mapView.invalidate()
        }
    )
}

private data class TrackSummary(
    val durationSeconds: Double,
    val distanceMeters: Double,
    val averageSpeedKnots: Double,
    val maxSpeedKnots: Double
)

private fun computeTrackSummary(points: List<RaceTrackPoint>, raceStartEpochMillis: Long?): TrackSummary {
    val workingPoints = if (raceStartEpochMillis != null && raceStartEpochMillis > 0L) {
        points.filter { it.epochMillis >= raceStartEpochMillis }
    } else {
        points
    }
    if (workingPoints.size < 2) {
        return TrackSummary(
            durationSeconds = 0.0,
            distanceMeters = 0.0,
            averageSpeedKnots = 0.0,
            maxSpeedKnots = 0.0
        )
    }

    var totalDistanceMeters = 0.0
    var maxSpeedKnots = 0.0
    for (index in 1 until workingPoints.size) {
        val prev = workingPoints[index - 1]
        val curr = workingPoints[index]
        val segmentMeters = distanceMeters(prev.latitude, prev.longitude, curr.latitude, curr.longitude)
        totalDistanceMeters += segmentMeters
        if (prev.epochMillis > 0L && curr.epochMillis > prev.epochMillis) {
            val dtSeconds = (curr.epochMillis - prev.epochMillis) / 1000.0
            val segKnots = (segmentMeters / dtSeconds) * METERS_PER_SECOND_TO_KNOTS
            maxSpeedKnots = max(maxSpeedKnots, segKnots)
        }
    }

    val firstEpoch = workingPoints.first().epochMillis
    val lastEpoch = workingPoints.last().epochMillis
    val durationSeconds = if (firstEpoch > 0L && lastEpoch > firstEpoch) {
        (lastEpoch - firstEpoch) / 1000.0
    } else {
        0.0
    }
    val averageSpeedKnots = if (durationSeconds > 0.0) {
        (totalDistanceMeters / durationSeconds) * METERS_PER_SECOND_TO_KNOTS
    } else {
        0.0
    }

    return TrackSummary(
        durationSeconds = durationSeconds,
        distanceMeters = totalDistanceMeters,
        averageSpeedKnots = averageSpeedKnots,
        maxSpeedKnots = maxSpeedKnots
    )
}

private fun distanceMeters(
    lat1: Double,
    lon1: Double,
    lat2: Double,
    lon2: Double
): Double {
    val results = FloatArray(1)
    Location.distanceBetween(lat1, lon1, lat2, lon2, results)
    return results[0].toDouble()
}

private fun parseRaceTrackFromGpx(trackFile: File): ParsedTrackData {
    val gpx = trackFile.readText()
    val trkptRegex = Regex(
        """<trkpt\s+lat="([^"]+)"\s+lon="([^"]+)">(.*?)</trkpt>""",
        setOf(RegexOption.DOT_MATCHES_ALL)
    )
    val wptRegex = Regex(
        """<wpt\s+lat="([^"]+)"\s+lon="([^"]+)">(.*?)</wpt>""",
        setOf(RegexOption.DOT_MATCHES_ALL)
    )
    val nameRegex = Regex("""<name>([^<]+)</name>""")
    val timeRegex = Regex("""<time>([^<]+)</time>""")
    val raceStartRegex = Regex("""<startline:raceStartTime>([^<]+)</startline:raceStartTime>""")
    val parsedRaceStart = parseGpxTimeMillis(raceStartRegex.find(gpx)?.groupValues?.getOrNull(1))
    var raceStartFromWaypointTime: Long = 0L
    val waypointsByName = wptRegex.findAll(gpx).mapNotNull { match ->
        val lat = match.groupValues.getOrNull(1)?.toDoubleOrNull()
        val lon = match.groupValues.getOrNull(2)?.toDoubleOrNull()
        val body = match.groupValues.getOrNull(3).orEmpty()
        val rawName = nameRegex.find(body)?.groupValues?.getOrNull(1)?.trim()
        val canonicalName = canonicalWaypointName(rawName)
        if (canonicalName == "race_start_point" && raceStartFromWaypointTime <= 0L) {
            raceStartFromWaypointTime = parseGpxTimeMillis(timeRegex.find(body)?.groupValues?.getOrNull(1))
        }
        if (lat == null || lon == null || canonicalName == null) {
            null
        } else {
            canonicalName to Point2D(lat, lon)
        }
    }.toMap()

    val extLeftLat = parseExtensionDouble(gpx, "leftBuoyLat")
    val extLeftLon = parseExtensionDouble(gpx, "leftBuoyLon")
    val extRightLat = parseExtensionDouble(gpx, "rightBuoyLat")
    val extRightLon = parseExtensionDouble(gpx, "rightBuoyLon")
    val extRaceLat = parseExtensionDouble(gpx, "raceStartLat")
    val extRaceLon = parseExtensionDouble(gpx, "raceStartLon")

    val leftBuoy = waypointsByName["left_buoy"] ?: if (extLeftLat != null && extLeftLon != null) {
        Point2D(extLeftLat, extLeftLon)
    } else {
        null
    }
    val rightBuoy = waypointsByName["right_buoy"] ?: if (extRightLat != null && extRightLon != null) {
        Point2D(extRightLat, extRightLon)
    } else {
        null
    }
    val raceStartPoint = waypointsByName["race_start_point"] ?: if (extRaceLat != null && extRaceLon != null) {
        Point2D(extRaceLat, extRaceLon)
    } else {
        null
    }

    val points = trkptRegex.findAll(gpx).mapNotNull { match ->
        val lat = match.groupValues.getOrNull(1)?.toDoubleOrNull()
        val lon = match.groupValues.getOrNull(2)?.toDoubleOrNull()
        val body = match.groupValues.getOrNull(3).orEmpty()
        if (lat == null || lon == null) {
            null
        } else {
            val timeText = timeRegex.find(body)?.groupValues?.getOrNull(1)
            val epochMillis = parseGpxTimeMillis(timeText)
            RaceTrackPoint(
                latitude = lat,
                longitude = lon,
                epochMillis = epochMillis
            )
        }
    }.toList()
    return ParsedTrackData(
        points = points,
        raceStartEpochMillis = when {
            parsedRaceStart > 0L -> parsedRaceStart
            raceStartFromWaypointTime > 0L -> raceStartFromWaypointTime
            else -> null
        },
        leftBuoy = leftBuoy,
        rightBuoy = rightBuoy,
        raceStartPoint = raceStartPoint
    )
}

private data class ParsedTrackData(
    val points: List<RaceTrackPoint>,
    val raceStartEpochMillis: Long?,
    val leftBuoy: Point2D?,
    val rightBuoy: Point2D?,
    val raceStartPoint: Point2D?
)

private fun canonicalWaypointName(name: String?): String? {
    if (name.isNullOrBlank()) return null
    val normalized = name.trim().lowercase(Locale.US)
        .replace("-", "_")
        .replace(" ", "_")
    return when (normalized) {
        "left_buoy", "leftbuoy", "port_buoy", "portbuoy", "buoy_left", "left", "port" -> "left_buoy"
        "right_buoy", "rightbuoy", "starboard_buoy", "starboardbuoy", "buoy_right", "right", "starboard" -> "right_buoy"
        "race_start_point", "race_start", "race_start_pos", "race_start_position", "start_point", "start_position", "startline_start", "start" -> "race_start_point"
        else -> null
    }
}

private fun parseExtensionDouble(gpx: String, tag: String): Double? {
    val regex = Regex("""<startline:$tag>([^<]+)</startline:$tag>""")
    return regex.find(gpx)?.groupValues?.getOrNull(1)?.toDoubleOrNull()
}

private fun parseGpxTimeMillis(timeText: String?): Long {
    if (timeText.isNullOrBlank()) return 0L
    return runCatching {
        val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }
        parser.parse(timeText)?.time ?: 0L
    }.getOrDefault(0L)
}

private fun shareTrackFile(context: Context, trackFile: File) {
    val authority = "${context.packageName}.fileprovider"
    val uri = runCatching {
        FileProvider.getUriForFile(context, authority, trackFile)
    }.getOrElse {
        Uri.fromFile(trackFile)
    }
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "application/gpx+xml"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(shareIntent, "Share track"))
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