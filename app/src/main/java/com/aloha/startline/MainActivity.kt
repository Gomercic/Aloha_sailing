package com.aloha.startline

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
import android.view.GestureDetector
import android.view.MotionEvent
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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.aloha.startline.nas.NasAnchoringPayload
import com.aloha.startline.nas.NasCallResult
import com.aloha.startline.nas.NasSyncPreferences
import com.aloha.startline.nas.NasTelemetryClient
import com.aloha.startline.nas.NasTrackPoint
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.Polygon

/**
 * Osmdroid [MapView] koji obradi dvostruki tap u četvrtinama prije ugrađenog zooma na double-tap.
 * Callback se postavlja u AndroidView `update` jer se factory ne izvršava ponovno.
 */
private class CornerDoubleTapMapView(context: Context) : MapView(context) {
    var onCornerDoubleTap: ((topHalf: Boolean, leftHalf: Boolean) -> Unit)? = null

    private val cornerGestureDetector =
        GestureDetector(
            context,
            object : GestureDetector.SimpleOnGestureListener() {}
        ).apply {
            setOnDoubleTapListener(
                object : GestureDetector.OnDoubleTapListener {
                    override fun onDoubleTap(e: MotionEvent): Boolean {
                        val w = width.toFloat().coerceAtLeast(1f)
                        val h = height.toFloat().coerceAtLeast(1f)
                        val topHalf = e.y < h / 2f
                        val leftHalf = e.x < w / 2f
                        onCornerDoubleTap?.invoke(topHalf, leftHalf)
                        return true
                    }

                    override fun onDoubleTapEvent(e: MotionEvent) = false

                    override fun onSingleTapConfirmed(e: MotionEvent) = false
                }
            )
        }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (cornerGestureDetector.onTouchEvent(event)) {
            return true
        }
        return super.onTouchEvent(event)
    }
}

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
    val context = LocalContext.current
    val activity = context as? Activity
    var showWelcomeScreen by rememberSaveable {
        mutableStateOf(!MainActivity.hasShownWelcomeForCurrentProcess)
    }
    var currentScreen by rememberSaveable {
        mutableStateOf(AppScreen.Main)
    }
    var showExitConfirmDialog by rememberSaveable { mutableStateOf(false) }
    var menuExpanded by rememberSaveable { mutableStateOf(false) }
    var windShiftSdDropdownExpanded by rememberSaveable { mutableStateOf(false) }
    var anchorAreaModeDropdownExpanded by rememberSaveable { mutableStateOf(false) }
    var screenMode by rememberSaveable { mutableStateOf(ScreenMode.Dark) }
    var mapMode by rememberSaveable { mutableStateOf(MapMode.NorthUp) }
    var mapRenderMode by rememberSaveable { mutableStateOf(MapRenderMode.Canvas) }
    var mapZoom by rememberSaveable { mutableStateOf(1.0f) }
    var anchorMapRenderMode by rememberSaveable { mutableStateOf(AnchorMapRenderMode.Canvas) }
    var anchorMapZoom by rememberSaveable { mutableStateOf(1.0f) }
    var anchorAutoZoomEnabled by rememberSaveable { mutableStateOf(true) }
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
    /** Kurs zadnje snimljene race točke (segment last→fix), za prag promjene kursa. */
    var raceTrackLastRecordedCourseDeg by remember { mutableStateOf<Double?>(null) }
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
    var gpsLocationAverageSeconds by remember { mutableLongStateOf(DEFAULT_GPS_LOCATION_AVERAGE_SECONDS) }
    var gpsLocationAverageInput by remember { mutableStateOf(DEFAULT_GPS_LOCATION_AVERAGE_SECONDS.toString()) }
    var gpsLocationAverageError by remember { mutableStateOf<String?>(null) }
    var countdownStartMinutes by remember { mutableLongStateOf(DEFAULT_COUNTDOWN_START_MINUTES) }
    var countdownStartInput by remember { mutableStateOf(DEFAULT_COUNTDOWN_START_MINUTES.toString()) }
    var countdownStartError by remember { mutableStateOf<String?>(null) }
    var remainingCountdownSeconds by remember {
        mutableLongStateOf(DEFAULT_COUNTDOWN_START_MINUTES * 60L)
    }
    /** Sekunde nakon što je odbrojavanje došlo do 0; štoperica ide naprijed dok traje trka / dok se ne resetira. */
    var postZeroElapsedSeconds by remember { mutableLongStateOf(0L) }
    var isCountdownRunning by remember { mutableStateOf(false) }
    var anchorLat by rememberSaveable { mutableStateOf<Double?>(null) }
    var anchorLon by rememberSaveable { mutableStateOf<Double?>(null) }
    var anchorRadiusMeters by rememberSaveable { mutableDoubleStateOf(DEFAULT_ANCHOR_RADIUS_METERS) }
    var anchorRadiusInput by rememberSaveable {
        mutableStateOf(DEFAULT_ANCHOR_RADIUS_METERS.roundToInt().toString())
    }
    var anchorRadiusError by rememberSaveable { mutableStateOf<String?>(null) }
    var anchorSegmentCenterDeg by rememberSaveable {
        mutableDoubleStateOf(DEFAULT_ANCHOR_SEGMENT_CENTER_DEG)
    }
    var anchorSegmentCenterInput by rememberSaveable {
        mutableStateOf(DEFAULT_ANCHOR_SEGMENT_CENTER_DEG.roundToInt().toString())
    }
    var anchorSegmentCenterError by rememberSaveable { mutableStateOf<String?>(null) }
    var anchorSegmentWidthDeg by rememberSaveable {
        mutableDoubleStateOf(DEFAULT_ANCHOR_SEGMENT_WIDTH_DEG)
    }
    var anchorSegmentWidthInput by rememberSaveable {
        mutableStateOf(DEFAULT_ANCHOR_SEGMENT_WIDTH_DEG.roundToInt().toString())
    }
    var anchorSegmentWidthError by rememberSaveable { mutableStateOf<String?>(null) }
    var anchorConeApexOffsetMeters by rememberSaveable {
        mutableDoubleStateOf(DEFAULT_ANCHOR_CONE_APEX_OFFSET_METERS)
    }
    var anchorConeApexOffsetInput by rememberSaveable {
        mutableStateOf(DEFAULT_ANCHOR_CONE_APEX_OFFSET_METERS.roundToInt().toString())
    }
    var anchorConeApexOffsetError by rememberSaveable { mutableStateOf<String?>(null) }
    var anchorAreaMode by rememberSaveable { mutableStateOf(AnchorAreaMode.Circle) }
    var anchorAlarmEnabled by rememberSaveable { mutableStateOf(true) }
    var showSetNewAnchorDialog by rememberSaveable { mutableStateOf(false) }
    var anchorTrackPoints by remember { mutableStateOf<List<RaceTrackPoint>>(emptyList()) }
    var anchorLastTrackLogEpochMs by remember { mutableLongStateOf(0L) }

    val nasPrefs = remember(context) { NasSyncPreferences(context) }
    val nasClient = remember { NasTelemetryClient() }
    var nasShipCodeInput by remember { mutableStateOf("") }
    /** Mrežna greška pri NAS syncu; uspjeh prikazuje se u aktivnost-liniji ispod. */
    var nasInternetError by remember { mutableStateOf<String?>(null) }
    var nasInternetSessionActive by remember { mutableStateOf(false) }
    var nasInternetRole by remember { mutableStateOf(NasInternetRole.Sending) }
    var nasInternetRoleMenuExpanded by remember { mutableStateOf(false) }
    var nasSessionStartElapsedMs by remember { mutableLongStateOf(0L) }
    var nasSendingOkCount by remember { mutableIntStateOf(0) }
    var nasReceivingOkCount by remember { mutableIntStateOf(0) }
    var nasUiTick by remember { mutableIntStateOf(0) }
    var showNasReceiveConfirmDialog by remember { mutableStateOf(false) }
    /** Kad je false, ispod naslova se ne prikazuju ship code / mod / Start (osim ako je sesija ili dijalog aktivan). */
    var nasInternetSectionExpanded by remember { mutableStateOf(false) }
    val nasSkipLocalTrackLog = remember { AtomicBoolean(false) }
    LaunchedEffect(Unit) {
        nasShipCodeInput = nasPrefs.shipCode
    }
    SideEffect {
        nasSkipLocalTrackLog.set(
            nasInternetRole == NasInternetRole.Receiving && nasInternetSessionActive
        )
    }

    val nasUAnchorLat by rememberUpdatedState(anchorLat)
    val nasUAnchorLon by rememberUpdatedState(anchorLon)
    val nasUAnchorAreaMode by rememberUpdatedState(anchorAreaMode)
    val nasUAnchorRadius by rememberUpdatedState(anchorRadiusMeters)
    val nasUSegCenter by rememberUpdatedState(anchorSegmentCenterDeg)
    val nasUSegWidth by rememberUpdatedState(anchorSegmentWidthDeg)
    val nasUConeApex by rememberUpdatedState(anchorConeApexOffsetMeters)
    val nasUAlarm by rememberUpdatedState(anchorAlarmEnabled)
    val nasUTrack by rememberUpdatedState(anchorTrackPoints)
    val nasURole by rememberUpdatedState(nasInternetRole)

    val countdownTone = remember { ToneGenerator(AudioManager.STREAM_ALARM, 100) }
    val anchorAlarmTone = remember { ToneGenerator(AudioManager.STREAM_ALARM, 100) }
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
                    if (
                        anchorLat != null && anchorLon != null &&
                        !nasSkipLocalTrackLog.get()
                    ) {
                        val epochNow = System.currentTimeMillis()
                        if (
                            anchorLastTrackLogEpochMs == 0L ||
                            epochNow - anchorLastTrackLogEpochMs >= ANCHOR_TRACK_LOG_INTERVAL_MS
                        ) {
                            anchorTrackPoints = anchorTrackPoints + RaceTrackPoint(
                                latitude = latestLocation.latitude,
                                longitude = latestLocation.longitude,
                                epochMillis = epochNow
                            )
                            anchorLastTrackLogEpochMs = epochNow
                        }
                    }
                    if (isTrackRecording && raceTrackPoints.isNotEmpty()) {
                        val epochNow = System.currentTimeMillis()
                        val lastPt = raceTrackPoints.last()
                        val lastLoc = Location("race_track_last").apply {
                            latitude = lastPt.latitude
                            longitude = lastPt.longitude
                        }
                        val moveMeters = lastLoc.distanceTo(latestLocation).toDouble()
                        val segmentCourseDeg =
                            if (moveMeters >= RACE_TRACK_MIN_MOVE_FOR_HEADING_M) {
                                normalizeDegrees(lastLoc.bearingTo(latestLocation).toDouble())
                            } else {
                                null
                            }
                        val timeOk =
                            epochNow - lastPt.epochMillis >= RACE_TRACK_LOG_INTERVAL_MS
                        val headingOk =
                            segmentCourseDeg != null &&
                                raceTrackLastRecordedCourseDeg != null &&
                                abs(
                                    signedShortestAngleDegrees(
                                        raceTrackLastRecordedCourseDeg!!,
                                        segmentCourseDeg
                                    )
                                ) >= RACE_TRACK_HEADING_CHANGE_DEG
                        if (timeOk || headingOk) {
                            raceTrackPoints = raceTrackPoints + RaceTrackPoint(
                                latitude = latestLocation.latitude,
                                longitude = latestLocation.longitude,
                                epochMillis = epochNow
                            )
                            raceTrackLastRecordedCourseDeg =
                                segmentCourseDeg ?: raceTrackLastRecordedCourseDeg
                        }
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
        ContextCompat.startForegroundService(
            context,
            Intent(context, RuntimeForegroundService::class.java)
        )
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
    val gpsSamplesForLocationAverage = remember(gpsSamples, gpsLocationAverageSeconds) {
        val now = SystemClock.elapsedRealtime()
        val windowMs = gpsLocationAverageSeconds * 1_000L
        gpsSamples.filter { now - it.timestampMs <= windowMs }
    }
    val averagedGpsLocation = remember(gpsSamplesForLocationAverage, currentLocation) {
        if (gpsSamplesForLocationAverage.isNotEmpty()) {
            averageLocations(gpsSamplesForLocationAverage.map { it.location })
        } else {
            currentLocation?.let { Location(it) }
        }
    }
    val distanceReferenceLocation = remember(averagedGpsLocation) {
        averagedGpsLocation?.let { Location(it) }
    }
    val anchorSetLocationAverage = remember(gpsSamples, currentLocation) {
        val now = SystemClock.elapsedRealtime()
        val tenSecondsWindow = ANCHOR_SET_AVERAGE_SECONDS * 1_000L
        val samples = gpsSamples.filter { now - it.timestampMs <= tenSecondsWindow }
        if (samples.isNotEmpty()) {
            averageLocations(samples.map { it.location })
        } else {
            currentLocation?.let { Location(it) }
        }
    }
    val anchorLocation = remember(anchorLat, anchorLon) {
        locationFromCoordinates(anchorLat, anchorLon)
    }
    val anchorConusApexLocation = remember(anchorLocation, anchorSegmentCenterDeg, anchorConeApexOffsetMeters) {
        if (anchorLocation == null) {
            null
        } else {
            offsetLocationByMeters(
                start = anchorLocation,
                bearingDeg = anchorSegmentCenterDeg,
                distanceMeters = anchorConeApexOffsetMeters
            )
        }
    }
    /** U Receive + NAS sesiji: pozicija udaljenog broda (zadnja točka tracka). Inače lokalni GPS. */
    val anchoringReferenceBoatLocation =
        if (nasInternetRole == NasInternetRole.Receiving && nasInternetSessionActive) {
            anchorTrackPoints.lastOrNull()?.let { p ->
                Location("remote_track_tip").apply {
                    latitude = p.latitude
                    longitude = p.longitude
                }
            }
        } else {
            currentLocation
        }
    val currentToAnchorDistanceMeters = remember(anchoringReferenceBoatLocation, anchorLocation) {
        val cur = anchoringReferenceBoatLocation
        val anc = anchorLocation
        if (cur != null && anc != null) {
            cur.distanceTo(anc).toDouble()
        } else {
            null
        }
    }
    val currentToAnchorBearingDeg = remember(anchoringReferenceBoatLocation, anchorLocation) {
        val cur = anchoringReferenceBoatLocation
        val anc = anchorLocation
        if (cur != null && anc != null) {
            normalizeDegrees(anc.bearingTo(cur).toDouble())
        } else {
            null
        }
    }
    val isAnchorInsideSafeArea = remember(
        currentToAnchorDistanceMeters,
        currentToAnchorBearingDeg,
        anchorRadiusMeters,
        anchorSegmentCenterDeg,
        anchorSegmentWidthDeg,
        anchorConeApexOffsetMeters,
        anchorAreaMode,
        anchorLocation,
        anchorConusApexLocation,
        anchoringReferenceBoatLocation
    ) {
        val anchor = anchorLocation
        val boat = anchoringReferenceBoatLocation
        if (anchor == null || boat == null) {
            true
        } else {
            if (anchorAreaMode == AnchorAreaMode.Circle) {
                val insideCircle = currentToAnchorDistanceMeters != null &&
                    currentToAnchorDistanceMeters <= anchorRadiusMeters
                insideCircle
            } else {
                val apex = anchorConusApexLocation ?: anchor
                val distanceFromApex = boat.distanceTo(apex).toDouble()
                val bearingFromApexToBoat = normalizeDegrees(apex.bearingTo(boat).toDouble())
                val insideSegment = isAngleInsideSegment(
                    angleDeg = bearingFromApexToBoat,
                    centerDeg = anchorSegmentCenterDeg,
                    widthDeg = anchorSegmentWidthDeg
                )
                val insideConeRadius = distanceFromApex <= anchorRadiusMeters
                insideConeRadius && insideSegment
            }
        }
    }
    val anchorAlarmActive = anchorLocation != null && !isAnchorInsideSafeArea
    val shouldPlayAnchorAlarm = anchorAlarmActive && anchorAlarmEnabled

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
            // Nakon starta (0 na štoperici) u usporedbu s ETA ne ulazi više „preostalo do starta”.
            val secondsUntilStart =
                if (remainingCountdownSeconds > 0L) remainingCountdownSeconds.toDouble() else 0.0
            (eta - secondsUntilStart).roundToInt()
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
    LaunchedEffect(currentScreen, nasInternetSessionActive) {
        if (currentScreen != AppScreen.Anchoring || !nasInternetSessionActive) return@LaunchedEffect
        while (true) {
            val ship = nasPrefs.shipCode
            if (ship.isNotBlank()) {
                when (nasURole) {
                    NasInternetRole.Sending -> {
                        val areaModeStr = when (nasUAnchorAreaMode) {
                            AnchorAreaMode.Circle -> "circle"
                            AnchorAreaMode.Conus -> "conus"
                        }
                        val trackForNas = nasUTrack.takeLast(200).map {
                            NasTrackPoint(it.latitude, it.longitude, it.epochMillis)
                        }
                        val payload = NasAnchoringPayload(
                            anchorLat = nasUAnchorLat,
                            anchorLon = nasUAnchorLon,
                            areaMode = areaModeStr,
                            radiusMeters = nasUAnchorRadius,
                            segmentCenterDeg = nasUSegCenter,
                            segmentWidthDeg = nasUSegWidth,
                            coneApexOffsetMeters = nasUConeApex,
                            alarmEnabled = nasUAlarm,
                            trackPoints = trackForNas
                        )
                        when (
                            val r = withContext(Dispatchers.IO) {
                                nasClient.putAnchoring(
                                    nasPrefs.baseUrl,
                                    nasPrefs.apiKey,
                                    ship,
                                    payload
                                )
                            }
                        ) {
                            is NasCallResult.Ok -> {
                                nasSendingOkCount++
                                nasInternetError = null
                            }
                            is NasCallResult.Err ->
                                nasInternetError = r.message
                        }
                    }
                    NasInternetRole.Receiving -> {
                        when (
                            val r = withContext(Dispatchers.IO) {
                                nasClient.getAnchoring(
                                    nasPrefs.baseUrl,
                                    nasPrefs.apiKey,
                                    ship
                                )
                            }
                        ) {
                            is NasCallResult.Ok -> {
                                val p = r.value
                                if (p.anchorLat != null && p.anchorLon != null) {
                                    anchorLat = p.anchorLat
                                    anchorLon = p.anchorLon
                                }
                                if (p.trackPoints.isNotEmpty()) {
                                    anchorTrackPoints = p.trackPoints.map { tp ->
                                        RaceTrackPoint(
                                            latitude = tp.latitude,
                                            longitude = tp.longitude,
                                            epochMillis = tp.epochMillis
                                        )
                                    }
                                    anchorLastTrackLogEpochMs =
                                        p.trackPoints.maxOf { it.epochMillis }
                                }
                                nasReceivingOkCount++
                                nasInternetError = null
                            }
                            is NasCallResult.Err ->
                                nasInternetError = r.message
                        }
                    }
                }
            }
            delay(NAS_INTERNET_SYNC_INTERVAL_MS)
        }
    }
    LaunchedEffect(nasInternetSessionActive, currentScreen) {
        if (!nasInternetSessionActive || currentScreen != AppScreen.Anchoring) return@LaunchedEffect
        while (true) {
            delay(1_000L)
            nasUiTick++
        }
    }
    val settingsScrollState = rememberScrollState()
    val trackLogScrollState = rememberScrollState()
    val windDebugScrollState = rememberScrollState()
    val screenTitle = when (currentScreen) {
        AppScreen.Main -> "Start Line"
        AppScreen.Settings -> "Settings"
        AppScreen.WindShift -> "WindShift"
        AppScreen.Anchoring -> "Anchoring"
        AppScreen.WindShiftDebug -> "Wind Debug"
        AppScreen.TrackLog -> "Track Log"
        AppScreen.TrackPreview -> "Track Preview"
        AppScreen.Help -> "Help"
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
    var windShiftGraphDisplayMode by rememberSaveable {
        mutableStateOf(WindShiftGraphDisplayMode.Auto)
    }
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
        raceTrackLastRecordedCourseDeg = null
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
            anchorAlarmTone.release()
        }
    }

    LaunchedEffect(shouldPlayAnchorAlarm) {
        if (!shouldPlayAnchorAlarm) return@LaunchedEffect
        while (true) {
            anchorAlarmTone.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 850)
            delay(1_000L)
        }
    }

    DisposableEffect(activity, currentScreen, showWelcomeScreen) {
        val currentActivity = activity
        if (currentActivity == null) {
            onDispose { }
        } else {
            val window = currentActivity.window
            val originalParams = WindowManager.LayoutParams().apply {
                copyFrom(window.attributes)
            }
            val originalKeepScreenOn = window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0

            if (
                !showWelcomeScreen &&
                (
                    currentScreen == AppScreen.Main ||
                        currentScreen == AppScreen.WindShift ||
                        currentScreen == AppScreen.Anchoring
                    )
            ) {
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
                if (newRemaining == 0L) {
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
                    postZeroElapsedSeconds = 0L
                }
            } else {
                postZeroElapsedSeconds++
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
            if (showExitConfirmDialog) {
                AlertDialog(
                    onDismissRequest = { showExitConfirmDialog = false },
                    title = { Text("Exit") },
                    text = { Text("Are you sure?") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showExitConfirmDialog = false
                                MainActivity.hasShownWelcomeForCurrentProcess = false
                                context.startService(
                                    Intent(context, RuntimeForegroundService::class.java).apply {
                                        action = RuntimeForegroundService.ACTION_STOP_SERVICE
                                    }
                                )
                                activity?.finish()
                            }
                        ) {
                            Text("Yes")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showExitConfirmDialog = false }) {
                            Text("No")
                        }
                    }
                )
            }
            if (showWelcomeScreen) {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = {
                                    showWelcomeScreen = false
                                    MainActivity.hasShownWelcomeForCurrentProcess = true
                                }
                            )
                        },
                    color = Color.White
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Image(
                            painter = painterResource(id = R.drawable.welcome_aloha),
                            contentDescription = "Welcome",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize(0.72f)
                                .align(Alignment.Center)
                        )
                        Text(
                            text = "Tomislav Gomerčić",
                            color = Color(0xFF6A6A6A),
                            fontSize = 9.sp,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(end = 10.dp, bottom = 8.dp)
                        )
                    }
                }
                return@Surface
            }
            if (currentScreen == AppScreen.Main) {
                StartLine(
                    headerContent = {
                        AppHeader(
                            screenTitle = screenTitle,
                            currentScreen = currentScreen,
                            isTrackRecording = isTrackRecording,
                            averageTrueSpeedKnots = averageTrueSpeedKnots,
                            menuExpanded = menuExpanded,
                            onMenuExpandedChange = { menuExpanded = it },
                            onScreenSelected = { selected -> currentScreen = selected },
                            onToggleMainWindShift = {
                                if (currentScreen == AppScreen.Main) {
                                    currentScreen = AppScreen.WindShift
                                } else if (currentScreen == AppScreen.WindShift) {
                                    currentScreen = AppScreen.Main
                                }
                            },
                            onExitClick = { showExitConfirmDialog = true },
                            titleColor = Color.White,
                            speedColor = HIGH_CONTRAST_YELLOW,
                            menuColor = Color.White
                        )
                    },
                    countdownDisplayText = formatCountdownOrOvertime(
                        remainingCountdownSeconds,
                        postZeroElapsedSeconds
                    ),
                    countdownOvertimeActive = remainingCountdownSeconds == 0L &&
                        (postZeroElapsedSeconds > 0L || isCountdownRunning),
                    isCountdownRunning = isCountdownRunning,
                    speedDisplayText = String.format(Locale.US, "%.1f kn", speedKnots),
                    leftBuoySet = leftBuoySet,
                    rightBuoySet = rightBuoySet,
                    lineLengthDisplayText = startLineLengthMeters?.let {
                        String.format(Locale.US, "%.0f m", it)
                    } ?: "-- m",
                    lineEtaDisplayText = lineCrossingEtaSeconds?.let { formatDuration(it) } ?: "--:--",
                    distanceDisplayText = "${
                        signedBowDistanceToLineMeters?.let { String.format(Locale.US, "%.0f", it) } ?: "--"
                    } m",
                    etaDeltaDisplayText = "${etaDeltaSeconds?.let { String.format(Locale.US, "%+d", it) } ?: "--"} sec",
                    statusFrameColor = statusFrameColor,
                    onDoubleClickAction = onDoubleClickAction,
                    onCountdownRound = {
                        postZeroElapsedSeconds = 0L
                        remainingCountdownSeconds =
                            ((remainingCountdownSeconds + 30L) / 60L) * 60L
                    },
                    onCountdownStartStop = {
                        if (remainingCountdownSeconds > 0L ||
                            postZeroElapsedSeconds > 0L ||
                            isCountdownRunning
                        ) {
                            isCountdownRunning = !isCountdownRunning
                        }
                    },
                    onCountdownMinus = {
                        if (remainingCountdownSeconds > 0L) {
                            remainingCountdownSeconds =
                                (remainingCountdownSeconds - 60L).coerceAtLeast(0L)
                        } else {
                            postZeroElapsedSeconds =
                                (postZeroElapsedSeconds - 60L).coerceAtLeast(0L)
                        }
                    },
                    onCountdownPlus = {
                        if (remainingCountdownSeconds == 0L) {
                            postZeroElapsedSeconds = 0L
                        }
                        remainingCountdownSeconds += 60L
                    },
                    onCountdownReset = {
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
                        raceTrackLastRecordedCourseDeg = null
                        raceStartEpochMillis = null
                        windShiftStartElapsedRealtimeMs = null
                        raceStartLat = null
                        raceStartLon = null
                        buoysLockedAfterRaceStart = false
                        postZeroElapsedSeconds = 0L
                        remainingCountdownSeconds = countdownStartMinutes * 60L
                    },
                    onSpeedMinus = {
                        speedKnots = (speedKnots - 0.2).coerceAtLeast(0.0)
                    },
                    onSpeedFromGps = {
                        if (approachSpeedKnots != null) {
                            speedKnots = approachSpeedKnots
                            speedStatus = "Brzina približavanja liniji (${avgWindowSeconds}s)"
                        } else {
                            speedStatus = "Nema podataka za brzinu približavanja liniji"
                        }
                    },
                    onSpeedPlus = {
                        speedKnots += 0.2
                    },
                    onLeftBuoyToggle = {
                        if (!buoysLockedAfterRaceStart) {
                            if (leftBuoySet) {
                                leftBuoyLat = null
                                leftBuoyLon = null
                            } else {
                                val snapshot = averagedGpsLocation
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
                    onRightBuoyToggle = {
                        if (!buoysLockedAfterRaceStart) {
                            if (rightBuoySet) {
                                rightBuoyLat = null
                                rightBuoyLon = null
                            } else {
                                val snapshot = averagedGpsLocation
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
                    mapContent = {
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
                            onToggleCanvasOsmFromMap = {
                                mapRenderMode = if (mapRenderMode == MapRenderMode.Canvas) {
                                    MapRenderMode.Osm
                                } else {
                                    MapRenderMode.Canvas
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
                )
                return@Surface
            }
            val isWindShiftScreen = currentScreen == AppScreen.WindShift
            val isAnchoringScreen = currentScreen == AppScreen.Anchoring
            val isSettingsScreen = currentScreen == AppScreen.Settings
            val isTrackLogScreen = currentScreen == AppScreen.TrackLog
            val isHelpScreen = currentScreen == AppScreen.Help
            val useStartLikeHeaderStyle =
                isWindShiftScreen || isAnchoringScreen || isSettingsScreen || isTrackLogScreen || isHelpScreen
            val view = LocalView.current
            val halfStatusBarTopPaddingPx = remember(view) {
                (ViewCompat.getRootWindowInsets(view)
                    ?.getInsets(WindowInsetsCompat.Type.statusBars())
                    ?.top ?: 0) / 2f
            }
            val halfStatusBarTopPadding = LocalDensity.current.run { halfStatusBarTopPaddingPx.toDp() }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(if (useStartLikeHeaderStyle) Color.Black else Color.Transparent)
                    .padding(top = if (useStartLikeHeaderStyle) halfStatusBarTopPadding else 0.dp)
                    .padding(if (useStartLikeHeaderStyle) 6.dp else 16.dp),
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

                AppHeader(
                    screenTitle = screenTitle,
                    currentScreen = currentScreen,
                    isTrackRecording = isTrackRecording,
                    averageTrueSpeedKnots = averageTrueSpeedKnots,
                    menuExpanded = menuExpanded,
                    onMenuExpandedChange = { menuExpanded = it },
                    onScreenSelected = { selected -> currentScreen = selected },
                    onToggleMainWindShift = {
                        if (currentScreen == AppScreen.Main) {
                            currentScreen = AppScreen.WindShift
                        } else if (currentScreen == AppScreen.WindShift) {
                            currentScreen = AppScreen.Main
                        }
                    },
                    onExitClick = { showExitConfirmDialog = true },
                    titleColor = if (useStartLikeHeaderStyle) Color.White else MaterialTheme.colorScheme.onBackground,
                    speedColor = if (useStartLikeHeaderStyle) HIGH_CONTRAST_YELLOW else MaterialTheme.colorScheme.onBackground,
                    menuColor = if (useStartLikeHeaderStyle) Color.White else MaterialTheme.colorScheme.onBackground
                )

                Spacer(
                    modifier = Modifier.height(
                        if (isWindShiftScreen || isAnchoringScreen) 0.dp else 24.dp
                    )
                )

                if (currentScreen == AppScreen.Anchoring) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        val compactButtonColors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF616161),
                            contentColor = Color.White
                        )
                        val anchorInputColors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            disabledTextColor = Color.White,
                            errorTextColor = Color.White,
                            cursorColor = Color.White,
                            focusedContainerColor = Color(0xFF1F1F1F),
                            unfocusedContainerColor = Color(0xFF1F1F1F),
                            disabledContainerColor = Color(0xFF1F1F1F),
                            errorContainerColor = Color(0xFF1F1F1F),
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            disabledBorderColor = Color.Transparent,
                            errorBorderColor = Color.Transparent
                        )
                        val anchorInputTextStyle = LocalTextStyle.current.copy(
                            color = Color.White,
                            fontSize = 9.sp,
                            lineHeight = 10.sp
                        )
                        val compactInputModifier = Modifier
                            .width(64.dp)
                            .heightIn(min = 22.dp)
                        val compactText = 10.sp
                        fun adjustAnchorRadius(delta: Int) {
                            val current = anchorRadiusInput.toIntOrNull() ?: anchorRadiusMeters.roundToInt()
                            val updated = (current + delta).coerceIn(
                                MIN_ANCHOR_RADIUS_METERS.toInt(),
                                MAX_ANCHOR_RADIUS_METERS.toInt()
                            )
                            anchorRadiusMeters = updated.toDouble()
                            anchorRadiusInput = updated.toString()
                            anchorRadiusError = null
                        }
                        fun adjustAnchorDirection(delta: Int) {
                            val current = anchorSegmentCenterInput.toIntOrNull() ?: anchorSegmentCenterDeg.roundToInt()
                            val wrapped = ((current + delta) % 360 + 360) % 360
                            anchorSegmentCenterDeg = wrapped.toDouble()
                            anchorSegmentCenterInput = wrapped.toString()
                            anchorSegmentCenterError = null
                        }
                        fun adjustAnchorWidth(delta: Int) {
                            val current = anchorSegmentWidthInput.toIntOrNull() ?: anchorSegmentWidthDeg.roundToInt()
                            val updated = (current + delta).coerceIn(
                                MIN_ANCHOR_SEGMENT_WIDTH_DEG.toInt(),
                                MAX_ANCHOR_SEGMENT_WIDTH_DEG.toInt()
                            )
                            anchorSegmentWidthDeg = updated.toDouble()
                            anchorSegmentWidthInput = updated.toString()
                            anchorSegmentWidthError = null
                        }
                        fun adjustAnchorApex(delta: Int) {
                            val current = anchorConeApexOffsetInput.toIntOrNull() ?: anchorConeApexOffsetMeters.roundToInt()
                            val updated = (current + delta).coerceIn(
                                MIN_ANCHOR_CONE_APEX_OFFSET_METERS.toInt(),
                                MAX_ANCHOR_CONE_APEX_OFFSET_METERS.toInt()
                            )
                            anchorConeApexOffsetMeters = updated.toDouble()
                            anchorConeApexOffsetInput = updated.toString()
                            anchorConeApexOffsetError = null
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val nasReceiveRemoteBlocksManual =
                                nasInternetRole == NasInternetRole.Receiving && nasInternetSessionActive
                            fun onSetAnchorClick() {
                                if (nasReceiveRemoteBlocksManual) return
                                if (anchorLocation != null) {
                                    showSetNewAnchorDialog = true
                                } else {
                                    val snapshot = anchorSetLocationAverage
                                    if (snapshot != null) {
                                        anchorLat = snapshot.latitude
                                        anchorLon = snapshot.longitude
                                        anchorTrackPoints = listOf(
                                            RaceTrackPoint(
                                                latitude = snapshot.latitude,
                                                longitude = snapshot.longitude,
                                                epochMillis = System.currentTimeMillis()
                                            )
                                        )
                                        anchorLastTrackLogEpochMs = System.currentTimeMillis()
                                        anchorAlarmEnabled = true
                                    }
                                }
                            }
                            if (nasInternetRole == NasInternetRole.Receiving) {
                                Button(
                                    onClick = { onSetAnchorClick() },
                                    enabled = !nasReceiveRemoteBlocksManual,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(56.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFFFF8F00),
                                        contentColor = Color.White,
                                        disabledContainerColor = Color(0xFF8D5A00),
                                        disabledContentColor = Color(0xFFE0E0E0)
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = "Anchor location is set",
                                            fontSize = 9.sp,
                                            lineHeight = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "by remote boat",
                                            fontSize = 9.sp,
                                            lineHeight = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            } else {
                                ActionButton(
                                    text = "Set anchor",
                                    background = Color(0xFF2E7D32),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(56.dp),
                                    fontSize = 14.sp,
                                    lineHeight = 14.sp,
                                    onClick = { onSetAnchorClick() }
                                )
                            }
                            Button(
                                onClick = {
                                    if (anchorAlarmEnabled) {
                                        onDoubleClickAction("anchor_stop_continue") {
                                            anchorAlarmEnabled = false
                                        }
                                    } else {
                                        anchorAlarmEnabled = true
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(56.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (anchorLocation != null) Color(0xFFD32F2F) else Color(0xFF616161),
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                if (anchorAlarmEnabled) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = "STOP",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "(double click)",
                                            fontSize = 9.sp,
                                            lineHeight = 10.sp
                                        )
                                    }
                                } else {
                                    Text(
                                        text = "Continue",
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                        if (showSetNewAnchorDialog) {
                            AlertDialog(
                                onDismissRequest = { showSetNewAnchorDialog = false },
                                title = { Text("Set New Anchor Location") },
                                text = { Text("Do you want to set a new anchor location?") },
                                confirmButton = {
                                    TextButton(
                                        onClick = {
                                            val snapshot = anchorSetLocationAverage
                                            if (snapshot != null) {
                                                anchorLat = snapshot.latitude
                                                anchorLon = snapshot.longitude
                                                anchorTrackPoints = listOf(
                                                    RaceTrackPoint(
                                                        latitude = snapshot.latitude,
                                                        longitude = snapshot.longitude,
                                                        epochMillis = System.currentTimeMillis()
                                                    )
                                                )
                                                anchorLastTrackLogEpochMs = System.currentTimeMillis()
                                                anchorAlarmEnabled = true
                                            }
                                            showSetNewAnchorDialog = false
                                        }
                                    ) {
                                        Text("Yes")
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showSetNewAnchorDialog = false }) {
                                        Text("No")
                                    }
                                }
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Mode:", color = Color.White, fontSize = compactText)
                            Box {
                                Button(
                                    onClick = { anchorAreaModeDropdownExpanded = true },
                                    modifier = Modifier.height(28.dp),
                                    colors = compactButtonColors,
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                                ) {
                                    Text(anchorAreaMode.label, fontSize = compactText)
                                }
                                DropdownMenu(
                                    expanded = anchorAreaModeDropdownExpanded,
                                    onDismissRequest = { anchorAreaModeDropdownExpanded = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(AnchorAreaMode.Circle.label, fontSize = compactText) },
                                        onClick = {
                                            anchorAreaMode = AnchorAreaMode.Circle
                                            anchorAreaModeDropdownExpanded = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(AnchorAreaMode.Conus.label, fontSize = compactText) },
                                        onClick = {
                                            anchorAreaMode = AnchorAreaMode.Conus
                                            anchorAreaModeDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(2.dp))
                            Text("R(m):", color = Color.White, fontSize = compactText)
                            OutlinedTextField(
                                value = anchorRadiusInput,
                                onValueChange = { input ->
                                    val sanitized = input.filter { it.isDigit() }
                                    anchorRadiusInput = sanitized
                                    val parsed = sanitized.toIntOrNull()
                                    when {
                                        sanitized.isBlank() -> anchorRadiusError = "Unesi radius."
                                        parsed == null -> anchorRadiusError = "Neispravan broj."
                                        parsed < MIN_ANCHOR_RADIUS_METERS -> anchorRadiusError =
                                            "Min $MIN_ANCHOR_RADIUS_METERS m."
                                        parsed > MAX_ANCHOR_RADIUS_METERS -> anchorRadiusError =
                                            "Max $MAX_ANCHOR_RADIUS_METERS m."
                                        else -> {
                                            anchorRadiusMeters = parsed.toDouble()
                                            anchorRadiusError = null
                                        }
                                    }
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                colors = anchorInputColors,
                                textStyle = anchorInputTextStyle,
                                modifier = compactInputModifier.longPressVerticalStepAdjust(
                                    onStepUp = { adjustAnchorRadius(1) },
                                    onStepDown = { adjustAnchorRadius(-1) }
                                )
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            Text(
                                text = when {
                                    !anchorAlarmEnabled -> "STOP"
                                    shouldPlayAnchorAlarm -> "ALARM"
                                    anchorAlarmActive -> "SILENT"
                                    else -> "SAFE"
                                },
                                color = when {
                                    !anchorAlarmEnabled -> Color(0xFFB0BEC5)
                                    shouldPlayAnchorAlarm -> Color(0xFFFF6E6E)
                                    anchorAlarmActive -> Color(0xFFFFD54F)
                                    else -> Color(0xFF7CFC8A)
                                },
                                fontWeight = FontWeight.Bold,
                                fontSize = compactText
                            )
                        }
                        if (anchorRadiusError != null) {
                            Text(anchorRadiusError!!, color = Color(0xFFFF8080), fontSize = 9.sp)
                        }
                        if (anchorAreaMode == AnchorAreaMode.Conus) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Dir(°):", color = Color.White, fontSize = compactText)
                                OutlinedTextField(
                                    value = anchorSegmentCenterInput,
                                    onValueChange = { input ->
                                        val sanitized = input.filter { it.isDigit() }
                                        anchorSegmentCenterInput = sanitized
                                        val parsed = sanitized.toIntOrNull()
                                        when {
                                            sanitized.isBlank() -> anchorSegmentCenterError = "Unesi smjer."
                                            parsed == null -> anchorSegmentCenterError = "Neispravan broj."
                                            parsed !in 0..359 -> anchorSegmentCenterError = "Raspon 0..359."
                                            else -> {
                                                anchorSegmentCenterDeg = parsed.toDouble()
                                                anchorSegmentCenterError = null
                                            }
                                        }
                                    },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    colors = anchorInputColors,
                                    textStyle = anchorInputTextStyle,
                                    modifier = compactInputModifier.longPressVerticalStepAdjust(
                                        onStepUp = { adjustAnchorDirection(1) },
                                        onStepDown = { adjustAnchorDirection(-1) }
                                    )
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Text("W(°):", color = Color.White, fontSize = compactText)
                                OutlinedTextField(
                                    value = anchorSegmentWidthInput,
                                    onValueChange = { input ->
                                        val sanitized = input.filter { it.isDigit() }
                                        anchorSegmentWidthInput = sanitized
                                        val parsed = sanitized.toIntOrNull()
                                        when {
                                            sanitized.isBlank() -> anchorSegmentWidthError = "Unesi širinu."
                                            parsed == null -> anchorSegmentWidthError = "Neispravan broj."
                                            parsed < MIN_ANCHOR_SEGMENT_WIDTH_DEG.toInt() ->
                                                anchorSegmentWidthError = "Min ${MIN_ANCHOR_SEGMENT_WIDTH_DEG.toInt()}."
                                            parsed > MAX_ANCHOR_SEGMENT_WIDTH_DEG.toInt() ->
                                                anchorSegmentWidthError = "Max ${MAX_ANCHOR_SEGMENT_WIDTH_DEG.toInt()}."
                                            else -> {
                                                anchorSegmentWidthDeg = parsed.toDouble()
                                                anchorSegmentWidthError = null
                                            }
                                        }
                                    },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    colors = anchorInputColors,
                                    textStyle = anchorInputTextStyle,
                                    modifier = compactInputModifier.longPressVerticalStepAdjust(
                                        onStepUp = { adjustAnchorWidth(1) },
                                        onStepDown = { adjustAnchorWidth(-1) }
                                    )
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Text("A(m):", color = Color.White, fontSize = compactText)
                                OutlinedTextField(
                                    value = anchorConeApexOffsetInput,
                                    onValueChange = { input ->
                                        val sanitized = sanitizeSignedIntegerInput(input)
                                        anchorConeApexOffsetInput = sanitized
                                        val parsed = sanitized.toIntOrNull()
                                        when {
                                            sanitized.isBlank() -> anchorConeApexOffsetError = "Unesi apex."
                                            parsed == null -> anchorConeApexOffsetError = "Neispravan broj."
                                            parsed < MIN_ANCHOR_CONE_APEX_OFFSET_METERS ->
                                                anchorConeApexOffsetError = "Min ${MIN_ANCHOR_CONE_APEX_OFFSET_METERS.toInt()}."
                                            parsed > MAX_ANCHOR_CONE_APEX_OFFSET_METERS ->
                                                anchorConeApexOffsetError = "Max ${MAX_ANCHOR_CONE_APEX_OFFSET_METERS.toInt()}."
                                            else -> {
                                                anchorConeApexOffsetMeters = parsed.toDouble()
                                                anchorConeApexOffsetError = null
                                            }
                                        }
                                    },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    colors = anchorInputColors,
                                    textStyle = anchorInputTextStyle,
                                    modifier = compactInputModifier.longPressVerticalStepAdjust(
                                        onStepUp = { adjustAnchorApex(1) },
                                        onStepDown = { adjustAnchorApex(-1) }
                                    )
                                )
                            }
                            if (anchorSegmentCenterError != null) {
                                Text(anchorSegmentCenterError!!, color = Color(0xFFFF8080), fontSize = 9.sp)
                            }
                            if (anchorSegmentWidthError != null) {
                                Text(anchorSegmentWidthError!!, color = Color(0xFFFF8080), fontSize = 9.sp)
                            }
                            if (anchorConeApexOffsetError != null) {
                                Text(anchorConeApexOffsetError!!, color = Color(0xFFFF8080), fontSize = 9.sp)
                            }
                        }
                        val distanceLabel = currentToAnchorDistanceMeters?.let {
                            String.format(Locale.US, "%.1f m", it)
                        } ?: "--"
                        val bearingLabel = currentToAnchorBearingDeg?.let {
                            String.format(Locale.US, "%.0f°", it)
                        } ?: "--"
                        Text(
                            text = "D:$distanceLabel  B:$bearingLabel  T:${anchorTrackPoints.size}",
                            color = Color.White,
                            fontSize = compactText
                        )
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(0.5f),
                            color = Color(0xFF111111),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(
                                1.dp,
                                if (anchorAlarmActive) Color(0xFFFF8A80) else Color(0xFF4A4A4A)
                            ),
                            tonalElevation = 0.dp
                        ) {
                            AnchoringMap(
                                anchorLocation = anchorLocation,
                                displayBoatLocation = anchoringReferenceBoatLocation,
                                trackPoints = anchorTrackPoints,
                                radiusMeters = anchorRadiusMeters,
                                areaMode = anchorAreaMode,
                                segmentCenterDeg = anchorSegmentCenterDeg,
                                segmentWidthDeg = anchorSegmentWidthDeg,
                                coneApexOffsetMeters = anchorConeApexOffsetMeters,
                                alarmActive = anchorAlarmActive,
                                renderMode = anchorMapRenderMode,
                                mapZoom = anchorMapZoom,
                                onToggleRenderMode = {
                                    anchorMapRenderMode = if (anchorMapRenderMode == AnchorMapRenderMode.Canvas) {
                                        AnchorMapRenderMode.OpenMap
                                    } else {
                                        AnchorMapRenderMode.Canvas
                                    }
                                },
                                autoZoomEnabled = anchorAutoZoomEnabled,
                                onToggleAutoZoom = {
                                    anchorAutoZoomEnabled = !anchorAutoZoomEnabled
                                },
                                onZoomBy = { zoomFactor ->
                                    anchorMapZoom = (anchorMapZoom * zoomFactor).coerceIn(0.18f, 6.0f)
                                }
                            )
                        }
                        HorizontalDivider(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            color = Color(0xFF5C5C5C),
                            thickness = 1.dp
                        )
                        val nasInternetDetailsVisible =
                            nasInternetSectionExpanded ||
                                nasInternetSessionActive ||
                                showNasReceiveConfirmDialog
                        Button(
                            onClick = {
                                if (nasInternetSessionActive || showNasReceiveConfirmDialog) return@Button
                                nasInternetSectionExpanded = !nasInternetSectionExpanded
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(0.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (nasInternetDetailsVisible) {
                                    Color(0xFF3A3A3A)
                                } else {
                                    Color(0xFF2C2C2C)
                                },
                                contentColor = Color.White
                            ),
                            contentPadding = PaddingValues(vertical = 14.dp, horizontal = 10.dp)
                        ) {
                            Text(
                                text = "Follow anchoring via internet",
                                fontSize = 16.sp,
                                lineHeight = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        if (nasInternetDetailsVisible) {
                        Spacer(modifier = Modifier.height(6.dp))
                        val nasShipFieldTextStyle = LocalTextStyle.current.copy(
                            color = Color.White,
                            fontSize = 12.sp,
                            lineHeight = 14.sp
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Enter Ship Code: ",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            OutlinedTextField(
                                value = nasShipCodeInput,
                                onValueChange = { if (!nasInternetSessionActive) nasShipCodeInput = it },
                                enabled = !nasInternetSessionActive,
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                                colors = anchorInputColors,
                                textStyle = nasShipFieldTextStyle,
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 40.dp)
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(modifier = Modifier.weight(1f)) {
                                Button(
                                    onClick = {
                                        if (!nasInternetSessionActive) {
                                            nasInternetRoleMenuExpanded = true
                                        }
                                    },
                                    enabled = !nasInternetSessionActive,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(44.dp),
                                    colors = compactButtonColors,
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(nasInternetRole.label, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                                DropdownMenu(
                                    expanded = nasInternetRoleMenuExpanded,
                                    onDismissRequest = { nasInternetRoleMenuExpanded = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(NasInternetRole.Sending.label, fontSize = 12.sp) },
                                        onClick = {
                                            nasInternetRole = NasInternetRole.Sending
                                            nasInternetRoleMenuExpanded = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(NasInternetRole.Receiving.label, fontSize = 12.sp) },
                                        onClick = {
                                            nasInternetRole = NasInternetRole.Receiving
                                            nasInternetRoleMenuExpanded = false
                                        }
                                    )
                                }
                            }
                            Button(
                                onClick = {
                                    if (nasInternetSessionActive) {
                                        onDoubleClickAction("nas_internet_stop") {
                                            nasInternetSessionActive = false
                                            nasSessionStartElapsedMs = 0L
                                            nasSendingOkCount = 0
                                            nasReceivingOkCount = 0
                                            nasInternetError = null
                                        }
                                    } else {
                                        val code = nasShipCodeInput.trim()
                                        if (code.isBlank()) {
                                            nasInternetError = "Enter ship code."
                                            return@Button
                                        }
                                        nasInternetError = null
                                        if (nasInternetRole == NasInternetRole.Receiving) {
                                            showNasReceiveConfirmDialog = true
                                        } else {
                                            nasPrefs.saveShipCode(code)
                                            nasSessionStartElapsedMs = SystemClock.elapsedRealtime()
                                            nasSendingOkCount = 0
                                            nasReceivingOkCount = 0
                                            nasInternetSessionActive = true
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (nasInternetSessionActive) {
                                        Color(0xFFD32F2F)
                                    } else {
                                        Color(0xFF2E7D32)
                                    },
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
                            ) {
                                if (nasInternetSessionActive) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = "STOP",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "(double click)",
                                            fontSize = 8.sp,
                                            lineHeight = 9.sp
                                        )
                                    }
                                } else {
                                    Text(
                                        text = "Start",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                        if (nasInternetSessionActive && nasSessionStartElapsedMs > 0L) {
                            val nasActivityLineText = remember(
                                nasInternetSessionActive,
                                nasSessionStartElapsedMs,
                                nasInternetRole,
                                nasSendingOkCount,
                                nasReceivingOkCount,
                                nasUiTick
                            ) {
                                val sec =
                                    (SystemClock.elapsedRealtime() - nasSessionStartElapsedMs)
                                        .coerceAtLeast(0L) / 1000L
                                when (nasInternetRole) {
                                    NasInternetRole.Sending ->
                                        "Sending… $nasSendingOkCount updates · " +
                                            formatNasSessionDuration(sec)
                                    NasInternetRole.Receiving ->
                                        "Receiving… $nasReceivingOkCount updates · " +
                                            formatNasSessionDuration(sec)
                                }
                            }
                            Text(
                                text = nasActivityLineText,
                                color = Color(0xFFB8E0D0),
                                fontSize = 11.sp,
                                lineHeight = 12.sp,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }
                        nasInternetError?.let { err ->
                            Text(
                                text = err,
                                color = Color(0xFFFF8A80),
                                fontSize = 10.sp,
                                lineHeight = 11.sp,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                        if (showNasReceiveConfirmDialog) {
                            AlertDialog(
                                onDismissRequest = { showNasReceiveConfirmDialog = false },
                                containerColor = Color(0xFF5C1A1A),
                                titleContentColor = Color.White,
                                textContentColor = Color.White,
                                title = {
                                    Text(
                                        text = "Receive from remote boat",
                                        fontWeight = FontWeight.Bold
                                    )
                                },
                                text = {
                                    Text(
                                        "Are you sure you want to take over the anchor location " +
                                            "and boat position from the remote boat via the internet?"
                                    )
                                },
                                confirmButton = {
                                    TextButton(
                                        onClick = {
                                            val code = nasShipCodeInput.trim()
                                            if (code.isNotBlank()) {
                                                nasPrefs.saveShipCode(code)
                                                nasSessionStartElapsedMs = SystemClock.elapsedRealtime()
                                                nasSendingOkCount = 0
                                                nasReceivingOkCount = 0
                                                nasInternetError = null
                                                nasInternetSessionActive = true
                                            }
                                            showNasReceiveConfirmDialog = false
                                        }
                                    ) {
                                        Text("Yes", color = Color(0xFFFFAB91), fontWeight = FontWeight.Bold)
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showNasReceiveConfirmDialog = false }) {
                                        Text("Cancel", color = Color.White)
                                    }
                                }
                            )
                        }
                        }
                        Spacer(modifier = Modifier.weight(0.45f))
                    }
                    return@Column
                }

                if (currentScreen == AppScreen.WindShift) {
                    val trackHistoryMinutes =
                        (windShiftWindowMinutes + WIND_SHIFT_TRACK_HISTORY_MARGIN_MINUTES).coerceAtMost(
                            MAX_WIND_SHIFT_WINDOW_MINUTES + WIND_SHIFT_TRACK_HISTORY_MARGIN_MINUTES
                        )
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
                            color = Color(0xFF111111),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color(0xFF4A4A4A)),
                            tonalElevation = 0.dp
                        ) {
                            WindShiftDeviationGraph(
                                points = windShiftSeries.value,
                                isMonoMode = windAnalyzer.mode == Mode.SINGLE,
                                monoSignInverted = windAnalyzer.monoSignInverted,
                                currentDeviationDeg = windAnalyzer.currentDeviationDeg,
                                modeLabel = if (windAnalyzer.mode == Mode.DUAL) "DUAL MODE" else "SINGLE MODE",
                                calcIntervalLabel = "Calc: ${windAnalyzer.debugInfo.calcWindowMinutes} min",
                                availableDataLabel = "Data: ${formatDuration(windAnalyzer.debugInfo.availableDataMs / 1000.0)}",
                                graphDisplayMode = windShiftGraphDisplayMode,
                                graphWindowMinutes = windShiftGraphWindowMinutes,
                                onIncreaseGraphWindow = {
                                    applyWindShiftGraphWindowMinutes(windShiftGraphWindowMinutes + 5L)
                                },
                                onDecreaseGraphWindow = {
                                    applyWindShiftGraphWindowMinutes(windShiftGraphWindowMinutes - 5L)
                                },
                                onToggleGraphDisplayMode = {
                                    windShiftGraphDisplayMode =
                                        if (windShiftGraphDisplayMode == WindShiftGraphDisplayMode.Auto) {
                                            WindShiftGraphDisplayMode.Normal
                                        } else {
                                            WindShiftGraphDisplayMode.Auto
                                        }
                                },
                                onToggleMonoSign = {
                                    if (windAnalyzer.mode == Mode.SINGLE) {
                                        windAnalyzer.toggleMonoSign()
                                        windShiftSeries.value = windAnalyzer.series
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(2.dp)
                            )
                        }
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .then(
                                    if (mapRenderMode == MapRenderMode.Canvas) {
                                        Modifier.pointerInput(
                                            windShiftWindowMinutes,
                                            mapRenderMode,
                                            windShiftTrackOrientation
                                        ) {
                                            detectTapGestures(
                                                onTap = { tap ->
                                                    val topHalf = tap.y < size.height / 2f
                                                    val bottomHalf = !topHalf
                                                    val leftHalf = tap.x < size.width / 2f
                                                    val inRightHalf = tap.x >= size.width / 2f
                                                    if (leftHalf && bottomHalf) {
                                                        mapRenderMode = if (mapRenderMode == MapRenderMode.Canvas) {
                                                            MapRenderMode.Osm
                                                        } else {
                                                            MapRenderMode.Canvas
                                                        }
                                                        return@detectTapGestures
                                                    }
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
                                        }
                                    } else {
                                        Modifier
                                    }
                                ),
                            color = Color(0xFF111111),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color(0xFF4A4A4A)),
                            tonalElevation = 0.dp
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(2.dp)
                            ) {
                                if (mapRenderMode == MapRenderMode.Canvas) {
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
                                } else {
                                    WindShiftTrackOsmMap(
                                        samples = gpsSamples,
                                        activeWindowMinutes = windShiftWindowMinutes,
                                        historyMinutes = trackHistoryMinutes,
                                        mapZoom = mapZoom,
                                        orientation = windShiftTrackOrientation,
                                        referenceCourseDeg = windShiftReferenceCourseDeg,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    Box(
                                        Modifier
                                            .fillMaxSize()
                                            .pointerInput(
                                                windShiftWindowMinutes,
                                                mapRenderMode,
                                                windShiftTrackOrientation,
                                                windShiftReferenceCourseDeg
                                            ) {
                                                detectTapGestures(
                                                    onTap = { tap ->
                                                        val topHalf = tap.y < size.height / 2f
                                                        val bottomHalf = !topHalf
                                                        val leftHalf = tap.x < size.width / 2f
                                                        val inRightHalf = tap.x >= size.width / 2f
                                                        if (leftHalf && bottomHalf) {
                                                            mapRenderMode = if (mapRenderMode == MapRenderMode.Canvas) {
                                                                MapRenderMode.Osm
                                                            } else {
                                                                MapRenderMode.Canvas
                                                            }
                                                            return@detectTapGestures
                                                        }
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
                                            }
                                    )
                                    Text(
                                        text = "${windShiftWindowMinutes} min",
                                        modifier = Modifier
                                            .align(Alignment.CenterEnd)
                                            .padding(end = 8.dp)
                                            .pointerInput(windShiftWindowMinutes) {
                                                detectTapGestures(
                                                    onDoubleTap = {
                                                        applyWindShiftWindowMinutes(
                                                            DEFAULT_WIND_SHIFT_WINDOW_MINUTES
                                                        )
                                                    }
                                                )
                                            },
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = if (windShiftTrackOrientation == WindShiftTrackOrientation.NorthUp) {
                                            "North up"
                                        } else {
                                            "Wind up"
                                        },
                                        modifier = Modifier
                                            .align(Alignment.TopStart)
                                            .padding(start = 8.dp, top = 8.dp),
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
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
                    CompositionLocalProvider(
                        LocalContentColor provides Color.White,
                        LocalTextStyle provides LocalTextStyle.current.copy(
                            color = Color.White
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(trackLogScrollState),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (trackLogFiles.isEmpty()) {
                                Text(
                                    "No saved tracks.",
                                    color = Color.White
                                )
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
                                            color = Color(0xFF2A2A2A),
                                            tonalElevation = 2.dp
                                        ) {
                                            Column(modifier = Modifier.padding(12.dp)) {
                                                Text(
                                                    text = trackFile.name,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White
                                                )
                                                Text(
                                                    text = formatTrackMeta(
                                                        trackFile = trackFile,
                                                        distanceMeters = trackLogDistanceByPath[trackFile.absolutePath]
                                                    ),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = Color.White
                                                )
                                            }
                                        }
                                        DropdownMenu(
                                            expanded = trackMenuPath == trackFile.absolutePath,
                                            onDismissRequest = { trackMenuPath = null },
                                            containerColor = Color(0xFF424242)
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text("Show on Map", color = Color.White) },
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
                                                text = { Text("Delete", color = Color.White) },
                                                onClick = {
                                                    pendingDeleteTrackPath = trackFile.absolutePath
                                                    trackMenuPath = null
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Share", color = Color.White) },
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
                                    containerColor = Color(0xFF424242),
                                    titleContentColor = Color.White,
                                    textContentColor = Color.White,
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
                                            },
                                            colors = ButtonDefaults.textButtonColors(
                                                contentColor = HIGH_CONTRAST_YELLOW
                                            )
                                        ) {
                                            Text("Delete")
                                        }
                                    },
                                    dismissButton = {
                                        TextButton(
                                            onClick = { pendingDeleteTrackPath = null },
                                            colors = ButtonDefaults.textButtonColors(
                                                contentColor = Color.White
                                            )
                                        ) {
                                            Text("Cancel")
                                        }
                                    }
                                )
                            }
                            Spacer(modifier = Modifier.height(80.dp))
                        }
                    }
                    return@Column
                }

                if (currentScreen == AppScreen.Settings) {
                    CompositionLocalProvider(
                        LocalContentColor provides Color.White,
                        LocalTextStyle provides LocalTextStyle.current.copy(
                            fontSize = 12.sp,
                            color = Color.White
                        )
                    ) {
                        val settingsInputColors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            disabledBorderColor = Color.Transparent,
                            errorBorderColor = Color.Transparent,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            errorContainerColor = Color.Transparent,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            disabledTextColor = Color.White,
                            errorTextColor = Color.White,
                            cursorColor = Color.White,
                            focusedLabelColor = Color.White,
                            unfocusedLabelColor = Color.White
                        )
                        val settingsButtonColors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF757575),
                            contentColor = Color.White
                        )
                        val settingsButtonShape = RoundedCornerShape(10.dp)
                        val settingsInputTextStyle = LocalTextStyle.current.copy(color = Color.White)
                        val settingsInputFieldModifier = Modifier
                            .width(82.dp)
                            .heightIn(min = 48.dp)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.Black)
                                .verticalScroll(settingsScrollState)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Countdown minutes:")
                                Spacer(modifier = Modifier.weight(1f))
                                OutlinedTextField(
                                    value = countdownStartInput,
                                    onValueChange = { input ->
                                        val sanitized = input.filter { it.isDigit() }
                                        countdownStartInput = sanitized
                                        val parsed = sanitized.toLongOrNull()
                                        when {
                                            sanitized.isBlank() -> countdownStartError =
                                                "Enter initial countdown minutes."
                                            parsed == null -> countdownStartError = "Invalid value."
                                            parsed < 0L -> countdownStartError = "Cannot be negative."
                                            else -> {
                                                countdownStartError = null
                                                countdownStartMinutes = parsed
                                                if (!isCountdownRunning) {
                                                    postZeroElapsedSeconds = 0L
                                                    remainingCountdownSeconds = parsed * 60L
                                                }
                                            }
                                        }
                                    },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    colors = settingsInputColors,
                                    textStyle = settingsInputTextStyle,
                                    modifier = settingsInputFieldModifier
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Button(
                                    onClick = {
                                        countdownStartMinutes = DEFAULT_COUNTDOWN_START_MINUTES
                                        countdownStartInput = DEFAULT_COUNTDOWN_START_MINUTES.toString()
                                        countdownStartError = null
                                        if (!isCountdownRunning) {
                                            postZeroElapsedSeconds = 0L
                                            remainingCountdownSeconds = DEFAULT_COUNTDOWN_START_MINUTES * 60L
                                        }
                                    },
                                    modifier = Modifier.height(32.dp),
                                    shape = settingsButtonShape,
                                    colors = settingsButtonColors,
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                                ) {
                                    Text("Reset", fontSize = 11.sp)
                                }
                            }
                            if (countdownStartError != null) {
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(countdownStartError!!)
                            }

                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("GPS to bow distance (m):")
                                Spacer(modifier = Modifier.weight(1f))
                                OutlinedTextField(
                                    value = gpsToBowDistanceInput,
                                    onValueChange = { input ->
                                        val sanitized = input.filter { it.isDigit() || it == '.' }
                                        gpsToBowDistanceInput = sanitized
                                        val parsed = sanitized.toDoubleOrNull()
                                        when {
                                            sanitized.isBlank() -> gpsToBowDistanceError =
                                                "Enter distance in metres."
                                            parsed == null -> gpsToBowDistanceError = "Invalid value."
                                            parsed < 0.0 -> gpsToBowDistanceError = "Cannot be negative."
                                            else -> {
                                                gpsToBowDistanceError = null
                                                gpsToBowDistanceMeters = parsed
                                            }
                                        }
                                    },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    colors = settingsInputColors,
                                    textStyle = settingsInputTextStyle,
                                    modifier = settingsInputFieldModifier
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Button(
                                    onClick = {
                                        gpsToBowDistanceMeters = DEFAULT_GPS_TO_BOW_DISTANCE_METERS
                                        gpsToBowDistanceInput = DEFAULT_GPS_TO_BOW_DISTANCE_METERS.toString()
                                        gpsToBowDistanceError = null
                                    },
                                    modifier = Modifier.height(32.dp),
                                    shape = settingsButtonShape,
                                    colors = settingsButtonColors,
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                                ) {
                                    Text("Reset", fontSize = 11.sp)
                                }
                            }
                            if (gpsToBowDistanceError != null) {
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(gpsToBowDistanceError!!)
                            }

                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Speed/heading average (s):")
                                Spacer(modifier = Modifier.weight(1f))
                                OutlinedTextField(
                                    value = avgWindowInput,
                                    onValueChange = { input ->
                                        val sanitized = input.filter { it.isDigit() }
                                        avgWindowInput = sanitized
                                        val parsed = sanitized.toLongOrNull()
                                        when {
                                            sanitized.isBlank() -> avgWindowError = "Enter time in seconds."
                                            parsed == null -> avgWindowError = "Invalid value."
                                            parsed < 1L -> avgWindowError = "Minimum is 1 s."
                                            else -> {
                                                avgWindowError = null
                                                avgWindowSeconds = parsed
                                            }
                                        }
                                    },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    colors = settingsInputColors,
                                    textStyle = settingsInputTextStyle,
                                    modifier = settingsInputFieldModifier
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Button(
                                    onClick = {
                                        avgWindowSeconds = DEFAULT_AVERAGE_WINDOW_SECONDS
                                        avgWindowInput = DEFAULT_AVERAGE_WINDOW_SECONDS.toString()
                                        avgWindowError = null
                                    },
                                    modifier = Modifier.height(32.dp),
                                    shape = settingsButtonShape,
                                    colors = settingsButtonColors,
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                                ) {
                                    Text("Reset", fontSize = 11.sp)
                                }
                            }
                            if (avgWindowError != null) {
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(avgWindowError!!)
                            }

                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("GPS location average (s):")
                                Spacer(modifier = Modifier.weight(1f))
                                OutlinedTextField(
                                    value = gpsLocationAverageInput,
                                    onValueChange = { input ->
                                        val sanitized = input.filter { it.isDigit() }
                                        gpsLocationAverageInput = sanitized
                                        val parsed = sanitized.toLongOrNull()
                                        when {
                                            sanitized.isBlank() -> gpsLocationAverageError =
                                                "Enter time in seconds."
                                            parsed == null -> gpsLocationAverageError = "Invalid value."
                                            parsed < MIN_GPS_LOCATION_AVERAGE_SECONDS -> gpsLocationAverageError =
                                                "Minimum is $MIN_GPS_LOCATION_AVERAGE_SECONDS s."
                                            parsed > MAX_GPS_LOCATION_AVERAGE_SECONDS -> gpsLocationAverageError =
                                                "Maximum is $MAX_GPS_LOCATION_AVERAGE_SECONDS s."
                                            else -> {
                                                gpsLocationAverageError = null
                                                gpsLocationAverageSeconds = parsed
                                            }
                                        }
                                    },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    colors = settingsInputColors,
                                    textStyle = settingsInputTextStyle,
                                    modifier = settingsInputFieldModifier
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Button(
                                    onClick = {
                                        gpsLocationAverageSeconds = DEFAULT_GPS_LOCATION_AVERAGE_SECONDS
                                        gpsLocationAverageInput = DEFAULT_GPS_LOCATION_AVERAGE_SECONDS.toString()
                                        gpsLocationAverageError = null
                                    },
                                    modifier = Modifier.height(32.dp),
                                    shape = settingsButtonShape,
                                    colors = settingsButtonColors,
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                                ) {
                                    Text("Reset", fontSize = 11.sp)
                                }
                            }
                            if (gpsLocationAverageError != null) {
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(gpsLocationAverageError!!)
                            }

                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Wind Shift window (min):")
                                Spacer(modifier = Modifier.weight(1f))
                                OutlinedTextField(
                                    value = windShiftWindowInput,
                                    onValueChange = { input ->
                                        val sanitized = input.filter { it.isDigit() }
                                        windShiftWindowInput = sanitized
                                        val parsed = sanitized.toLongOrNull()
                                        when {
                                            sanitized.isBlank() -> windShiftWindowError = "Enter minutes for Wind Shift."
                                            parsed == null -> windShiftWindowError = "Invalid value."
                                            parsed < MIN_WIND_SHIFT_WINDOW_MINUTES ->
                                                windShiftWindowError = "Minimum is $MIN_WIND_SHIFT_WINDOW_MINUTES min."
                                            parsed > MAX_WIND_SHIFT_WINDOW_MINUTES ->
                                                windShiftWindowError = "Maximum is $MAX_WIND_SHIFT_WINDOW_MINUTES min."
                                            else -> {
                                                applyWindShiftWindowMinutes(parsed)
                                            }
                                        }
                                    },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    colors = settingsInputColors,
                                    textStyle = settingsInputTextStyle,
                                    modifier = settingsInputFieldModifier
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Button(
                                    onClick = { applyWindShiftWindowMinutes(DEFAULT_WIND_SHIFT_WINDOW_MINUTES) },
                                    modifier = Modifier.height(32.dp),
                                    shape = settingsButtonShape,
                                    colors = settingsButtonColors,
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                                ) {
                                    Text("Reset", fontSize = 11.sp)
                                }
                            }
                            if (windShiftWindowError != null) {
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(windShiftWindowError!!)
                            }

                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Wind Shift heading (s):")
                                Spacer(modifier = Modifier.weight(1f))
                                OutlinedTextField(
                                    value = windShiftHeadingWindowInput,
                                    onValueChange = { input ->
                                        val sanitized = input.filter { it.isDigit() }
                                        windShiftHeadingWindowInput = sanitized
                                        val parsed = sanitized.toLongOrNull()
                                        when {
                                            sanitized.isBlank() -> windShiftHeadingWindowError =
                                                "Enter seconds for Wind Shift heading."
                                            parsed == null -> windShiftHeadingWindowError = "Invalid value."
                                            parsed < MIN_WIND_SHIFT_HEADING_WINDOW_SECONDS ->
                                                windShiftHeadingWindowError = "Minimum is $MIN_WIND_SHIFT_HEADING_WINDOW_SECONDS s."
                                            parsed > MAX_WIND_SHIFT_HEADING_WINDOW_SECONDS ->
                                                windShiftHeadingWindowError = "Maximum is $MAX_WIND_SHIFT_HEADING_WINDOW_SECONDS s."
                                            else -> {
                                                applyWindShiftHeadingWindowSeconds(parsed)
                                            }
                                        }
                                    },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    colors = settingsInputColors,
                                    textStyle = settingsInputTextStyle,
                                    modifier = settingsInputFieldModifier
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Button(
                                    onClick = {
                                        applyWindShiftHeadingWindowSeconds(DEFAULT_WIND_SHIFT_HEADING_WINDOW_SECONDS)
                                    },
                                    modifier = Modifier.height(32.dp),
                                    shape = settingsButtonShape,
                                    colors = settingsButtonColors,
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                                ) {
                                    Text("Reset", fontSize = 11.sp)
                                }
                            }
                            if (windShiftHeadingWindowError != null) {
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(windShiftHeadingWindowError!!)
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Wind Shift SD filter:")
                                Box {
                                    Button(
                                        onClick = { windShiftSdDropdownExpanded = true },
                                        modifier = Modifier.height(32.dp),
                                        shape = settingsButtonShape,
                                        colors = settingsButtonColors
                                    ) {
                                        Text(windShiftStdFilterMode.label, fontSize = 11.sp)
                                    }
                                    DropdownMenu(
                                        expanded = windShiftSdDropdownExpanded,
                                        onDismissRequest = { windShiftSdDropdownExpanded = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Off") },
                                            onClick = {
                                                windShiftStdFilterMode = WindShiftStdFilterMode.Off
                                                windShiftSdDropdownExpanded = false
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(">1 SD") },
                                            onClick = {
                                                windShiftStdFilterMode = WindShiftStdFilterMode.OneSigma
                                                windShiftSdDropdownExpanded = false
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(">2 SD") },
                                            onClick = {
                                                windShiftStdFilterMode = WindShiftStdFilterMode.TwoSigma
                                                windShiftSdDropdownExpanded = false
                                            }
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(24.dp))
                            TextButton(
                                onClick = { currentScreen = AppScreen.Help },
                                colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF90CAF9))
                            ) {
                                Text("Help", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                            TextButton(
                                onClick = { showExitConfirmDialog = true },
                                colors = ButtonDefaults.textButtonColors(contentColor = Color.White)
                            ) {
                                Text("Exit", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(24.dp))
                        }
                    }
                    return@Column
                }

                if (currentScreen == AppScreen.Help) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black)
                    ) {
                        HelpScreen(modifier = Modifier.fillMaxSize())
                    }
                    return@Column
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
private const val DEFAULT_GPS_LOCATION_AVERAGE_SECONDS = 2L
private const val MIN_GPS_LOCATION_AVERAGE_SECONDS = 1L
private const val MAX_GPS_LOCATION_AVERAGE_SECONDS = 30L
private const val DEFAULT_COUNTDOWN_START_MINUTES = 5L
private const val DEFAULT_WIND_SHIFT_WINDOW_MINUTES = 2L
private const val MIN_WIND_SHIFT_WINDOW_MINUTES = 2L
private const val MAX_WIND_SHIFT_WINDOW_MINUTES = 180L
/** Margin iznad wind-shift prozora za donji track i retention GPS uzoraka. */
private const val WIND_SHIFT_TRACK_HISTORY_MARGIN_MINUTES = 15L
private const val DEFAULT_WIND_SHIFT_HEADING_WINDOW_SECONDS = 10L
private const val MIN_WIND_SHIFT_HEADING_WINDOW_SECONDS = 1L
private const val MAX_WIND_SHIFT_HEADING_WINDOW_SECONDS = 30L
private const val DEFAULT_WIND_SHIFT_GRAPH_WINDOW_MINUTES = 10L
private const val MIN_WIND_SHIFT_GRAPH_WINDOW_MINUTES = 5L
private const val MAX_GPS_SAMPLE_AGE_MS =
    (MAX_WIND_SHIFT_WINDOW_MINUTES + WIND_SHIFT_TRACK_HISTORY_MARGIN_MINUTES) * 60_000L
private const val ANCHOR_SET_AVERAGE_SECONDS = 10L
private const val ANCHOR_TRACK_LOG_INTERVAL_MS = 30_000L
private const val RACE_TRACK_LOG_INTERVAL_MS = 10_000L
private const val RACE_TRACK_HEADING_CHANGE_DEG = 10.0
private const val RACE_TRACK_MIN_MOVE_FOR_HEADING_M = 5.0
private const val NAS_INTERNET_SYNC_INTERVAL_MS = 30_000L
private const val DEFAULT_ANCHOR_RADIUS_METERS = 35.0
private const val MIN_ANCHOR_RADIUS_METERS = 1.0
private const val MAX_ANCHOR_RADIUS_METERS = 500.0
private const val DEFAULT_ANCHOR_SEGMENT_CENTER_DEG = 0.0
private const val DEFAULT_ANCHOR_SEGMENT_WIDTH_DEG = 120.0
private const val MIN_ANCHOR_SEGMENT_WIDTH_DEG = 10.0
private const val MAX_ANCHOR_SEGMENT_WIDTH_DEG = 360.0
private const val DEFAULT_ANCHOR_CONE_APEX_OFFSET_METERS = -5.0
private const val MIN_ANCHOR_CONE_APEX_OFFSET_METERS = -500.0
private const val MAX_ANCHOR_CONE_APEX_OFFSET_METERS = 500.0
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

private fun offsetLocationByMeters(start: Location, bearingDeg: Double, distanceMeters: Double): Location {
    val angularDistance = distanceMeters / EARTH_RADIUS_METERS
    val bearingRad = Math.toRadians(bearingDeg)
    val startLatRad = Math.toRadians(start.latitude)
    val startLonRad = Math.toRadians(start.longitude)

    val sinStartLat = sin(startLatRad)
    val cosStartLat = cos(startLatRad)
    val sinAngular = sin(angularDistance)
    val cosAngular = cos(angularDistance)

    val endLatRad = kotlin.math.asin(
        sinStartLat * cosAngular + cosStartLat * sinAngular * cos(bearingRad)
    )
    val endLonRad = startLonRad + atan2(
        sin(bearingRad) * sinAngular * cosStartLat,
        cosAngular - sinStartLat * sin(endLatRad)
    )
    return Location("anchor_apex").apply {
        latitude = Math.toDegrees(endLatRad)
        longitude = Math.toDegrees(endLonRad)
    }
}

private fun sanitizeSignedIntegerInput(input: String): String {
    val filtered = input.filter { it.isDigit() || it == '-' }
    val hasLeadingMinus = filtered.startsWith('-')
    val digitsOnly = if (hasLeadingMinus) {
        filtered.drop(1).replace("-", "")
    } else {
        filtered.replace("-", "")
    }
    return (if (hasLeadingMinus) "-" else "") + digitsOnly
}

private fun Modifier.longPressVerticalStepAdjust(
    stepPx: Float = 16f,
    onStepUp: () -> Unit,
    onStepDown: () -> Unit
): Modifier = this.pointerInput(Unit) {
    var accumulatedDragY = 0f
    detectDragGestures(
        onDragStart = { accumulatedDragY = 0f },
        onDragEnd = { accumulatedDragY = 0f },
        onDragCancel = { accumulatedDragY = 0f },
        onDrag = { change, dragAmount ->
            change.consume()
            accumulatedDragY += dragAmount.y
            while (accumulatedDragY <= -stepPx) {
                onStepUp()
                accumulatedDragY += stepPx
            }
            while (accumulatedDragY >= stepPx) {
                onStepDown()
                accumulatedDragY -= stepPx
            }
        }
    )
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

private fun formatNasSessionDuration(totalSec: Long): String {
    val s = totalSec.coerceAtLeast(0L)
    if (s < 60L) return "${s}s"
    val m = s / 60L
    val rs = s % 60L
    if (m < 60L) return "${m}m ${rs}s"
    val h = m / 60L
    val rm = m % 60L
    return "${h}h ${rm}m ${rs}s"
}

private fun formatCountdown(totalSeconds: Long): String {
    val safeSeconds = totalSeconds.coerceAtLeast(0L)
    val minutes = safeSeconds / 60L
    val seconds = safeSeconds % 60L
    return String.format(Locale.US, "%02d:%02d", minutes, seconds)
}

/** Štoperica nakon nule: ispod 60 min MM:SS, od 60 min nadalje H:MM. */
private fun formatPostZeroElapsed(totalSeconds: Long): String {
    val s = totalSeconds.coerceAtLeast(0L)
    return if (s < 3600L) {
        formatCountdown(s)
    } else {
        val hours = s / 3600L
        val minutes = (s % 3600L) / 60L
        String.format(Locale.US, "%d:%02d", hours, minutes)
    }
}

/** Prije nule: preostalo; na/poslije nule: proteklo od starta (00:00, 00:01, …). */
private fun formatCountdownOrOvertime(remainingCountdownSeconds: Long, postZeroElapsedSeconds: Long): String {
    return if (remainingCountdownSeconds > 0L) {
        formatCountdown(remainingCountdownSeconds)
    } else {
        formatPostZeroElapsed(postZeroElapsedSeconds)
    }
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

@Composable
private fun AppHeader(
    screenTitle: String,
    currentScreen: AppScreen,
    isTrackRecording: Boolean,
    averageTrueSpeedKnots: Double?,
    menuExpanded: Boolean,
    onMenuExpandedChange: (Boolean) -> Unit,
    onScreenSelected: (AppScreen) -> Unit,
    onToggleMainWindShift: () -> Unit,
    onExitClick: () -> Unit,
    menuIconFontSize: TextUnit = 26.sp,
    titleColor: Color = MaterialTheme.colorScheme.onBackground,
    speedColor: Color = MaterialTheme.colorScheme.onBackground,
    menuColor: Color = MaterialTheme.colorScheme.onBackground
) {
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
                        detectTapGestures(onTap = { onToggleMainWindShift() })
                    }
            ) {
                Text(
                    text = screenTitle,
                    style = MaterialTheme.typography.headlineMedium,
                    color = titleColor
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (
                    currentScreen == AppScreen.Main ||
                    currentScreen == AppScreen.WindShift
                ) {
                    val speedLabel = averageTrueSpeedKnots?.let {
                        String.format(Locale.US, "%.1f kn", it)
                    } ?: "--.- kn"
                    Text(
                        text = speedLabel,
                        fontWeight = FontWeight.Bold,
                        color = speedColor
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Box {
                    Text(
                        text = "☰",
                        fontSize = menuIconFontSize,
                        color = menuColor,
                        modifier = Modifier
                            .padding(4.dp)
                            .pointerInput(menuExpanded) {
                                detectTapGestures(onTap = { onMenuExpandedChange(true) })
                            }
                    )
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { onMenuExpandedChange(false) }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Start Line") },
                            onClick = {
                                onScreenSelected(AppScreen.Main)
                                onMenuExpandedChange(false)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("WindShift") },
                            onClick = {
                                onScreenSelected(AppScreen.WindShift)
                                onMenuExpandedChange(false)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Anchoring") },
                            onClick = {
                                onScreenSelected(AppScreen.Anchoring)
                                onMenuExpandedChange(false)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Settings") },
                            onClick = {
                                onScreenSelected(AppScreen.Settings)
                                onMenuExpandedChange(false)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Track Log") },
                            onClick = {
                                onScreenSelected(AppScreen.TrackLog)
                                onMenuExpandedChange(false)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Wind Debug") },
                            onClick = {
                                onScreenSelected(AppScreen.WindShiftDebug)
                                onMenuExpandedChange(false)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Help") },
                            onClick = {
                                onScreenSelected(AppScreen.Help)
                                onMenuExpandedChange(false)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Exit") },
                            onClick = {
                                onMenuExpandedChange(false)
                                onExitClick()
                            }
                        )
                    }
                }
            }
        }
    }
}

private enum class AppScreen {
    Main,
    Settings,
    WindShift,
    Anchoring,
    WindShiftDebug,
    TrackLog,
    TrackPreview,
    Help
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

private enum class WindShiftGraphDisplayMode {
    Auto,
    Normal
}

private enum class TrackPreviewRenderMode {
    TrackOnly,
    OpenMap
}

private enum class NasInternetRole(val label: String) {
    Sending("Sending mode"),
    Receiving("Receiving mode")
}

private enum class AnchorAreaMode(val label: String) {
    Circle("Circle"),
    Conus("Conus")
}

private enum class AnchorMapRenderMode {
    Canvas,
    OpenMap
}

private enum class WindShiftStdFilterMode(val label: String, val sigmaMultiplier: Double?) {
    Off("Off", null),
    OneSigma(">1 SD", 1.0),
    TwoSigma(">2 SD", 2.0)
}

private fun signedShortestAngleDegrees(fromDeg: Double, toDeg: Double): Double {
    var diff = (toDeg - fromDeg) % 360.0
    if (diff > 180.0) diff -= 360.0
    if (diff < -180.0) diff += 360.0
    return diff
}

private fun isAngleInsideSegment(angleDeg: Double, centerDeg: Double, widthDeg: Double): Boolean {
    if (widthDeg >= 360.0) return true
    val halfWidth = widthDeg / 2.0
    val delta = signedShortestAngleDegrees(centerDeg, angleDeg)
    return abs(delta) <= halfWidth
}

@Composable
private fun AnchoringMap(
    anchorLocation: Location?,
    /** Na karti: u Receive + aktivnoj sesiji = zadnja točka remote tracka; inače GPS ovog uređaja. */
    displayBoatLocation: Location?,
    trackPoints: List<RaceTrackPoint>,
    radiusMeters: Double,
    areaMode: AnchorAreaMode,
    segmentCenterDeg: Double,
    segmentWidthDeg: Double,
    coneApexOffsetMeters: Double,
    alarmActive: Boolean,
    renderMode: AnchorMapRenderMode,
    mapZoom: Float,
    onToggleRenderMode: () -> Unit,
    autoZoomEnabled: Boolean,
    onToggleAutoZoom: () -> Unit,
    onZoomBy: (Float) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D0D0D))
            .pointerInput(renderMode) {
                detectTapGestures(
                    onTap = { tap ->
                        val w = size.width.toFloat()
                        val h = size.height.toFloat()
                        val topHalf = tap.y < h / 2f
                        val bottomHalf = !topHalf
                        val leftHalf = tap.x < w / 2f
                        val topLeftQuarter = topHalf && leftHalf
                        val bottomLeftQuarter = bottomHalf && leftHalf
                        when {
                            topLeftQuarter -> onToggleRenderMode()
                            bottomLeftQuarter -> onToggleAutoZoom()
                        }
                    }
                )
            }
            .then(
                if (renderMode == AnchorMapRenderMode.Canvas) {
                    Modifier.pointerInput(mapZoom) {
                        detectTransformGestures { _, _, zoom, _ ->
                            onZoomBy(zoom)
                        }
                    }
                } else {
                    Modifier
                }
            )
    ) {
        if (renderMode == AnchorMapRenderMode.Canvas) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF0D0D0D))
            ) {
                val allLocations = buildList {
                    if (anchorLocation != null) add(anchorLocation)
                    if (displayBoatLocation != null) add(displayBoatLocation)
                    trackPoints.forEach { p ->
                        add(
                            Location("anchor_track").apply {
                                latitude = p.latitude
                                longitude = p.longitude
                            }
                        )
                    }
                }.toMutableList()
                if (allLocations.isEmpty()) {
                    drawContext.canvas.nativeCanvas.drawText(
                        "Set anchor to start monitoring",
                        24f,
                        size.height / 2f,
                        android.graphics.Paint().apply {
                            color = android.graphics.Color.LTGRAY
                            textSize = 34f
                            isAntiAlias = true
                        }
                    )
                    return@Canvas
                }
                val conusApexLocation = if (anchorLocation != null && areaMode == AnchorAreaMode.Conus) {
                    offsetLocationByMeters(
                        start = anchorLocation,
                        bearingDeg = segmentCenterDeg,
                        distanceMeters = coneApexOffsetMeters
                    )
                } else {
                    null
                }
                if (conusApexLocation != null) {
                    allLocations += conusApexLocation
                }
                val referenceLat = anchorLocation?.latitude ?: allLocations.first().latitude
                val projected = allLocations.map { projectToMeters(it, referenceLat) }.toMutableList()
                val anchorProjected = anchorLocation?.let { projectToMeters(it, referenceLat) }
                val apexProjected = conusApexLocation?.let { projectToMeters(it, referenceLat) }
                val currentProjected = displayBoatLocation?.let { projectToMeters(it, referenceLat) }
                val radiusCenter = if (areaMode == AnchorAreaMode.Conus) apexProjected ?: anchorProjected else anchorProjected
                if (radiusCenter != null) {
                    projected += Point2D(radiusCenter.x - radiusMeters, radiusCenter.y - radiusMeters)
                    projected += Point2D(radiusCenter.x + radiusMeters, radiusCenter.y + radiusMeters)
                }

                val minX = projected.minOf { it.x }
                val maxX = projected.maxOf { it.x }
                val minY = projected.minOf { it.y }
                val maxY = projected.maxOf { it.y }
                val midX = (minX + maxX) / 2.0
                val midY = (minY + maxY) / 2.0
                val spanX = (maxX - minX).coerceAtLeast(1.0)
                val spanY = (maxY - minY).coerceAtLeast(1.0)
                val padding = 36f
                val baseScale = minOf(
                    (size.width - 2f * padding) / spanX.toFloat(),
                    (size.height - 2f * padding) / spanY.toFloat()
                )
                val scale = baseScale * mapZoom

                fun toOffset(point: Point2D): Offset {
                    val x = ((point.x - midX).toFloat() * scale) + size.width / 2f
                    val y = size.height / 2f - ((point.y - midY).toFloat() * scale)
                    return Offset(x, y)
                }

                val trackOffsets = trackPoints.map {
                    toOffset(projectToMeters(it.latitude, it.longitude, referenceLat))
                }
                if (trackOffsets.size >= 2) {
                    for (i in 0 until trackOffsets.lastIndex) {
                        drawLine(
                            color = Color(0xFFFFF176),
                            start = trackOffsets[i],
                            end = trackOffsets[i + 1],
                            strokeWidth = 3f
                        )
                    }
                }

                if (anchorProjected != null) {
                    val anchorOffset = toOffset(anchorProjected)
                    val radiusPx = (radiusMeters.toFloat() * scale).coerceAtLeast(8f)
                    if (areaMode == AnchorAreaMode.Circle) {
                        drawCircle(
                            color = if (alarmActive) Color(0xFFFF8A80) else Color(0xFF66BB6A),
                            radius = radiusPx,
                            center = anchorOffset,
                            style = Stroke(width = 3f)
                        )
                    } else {
                        val apex = apexProjected ?: anchorProjected
                        val apexOffset = toOffset(apex)
                        val segStart = (segmentCenterDeg - segmentWidthDeg / 2.0 - 90.0).toFloat()
                        drawArc(
                            color = if (alarmActive) Color(0x44FF8A80) else Color(0x4432CD32),
                            startAngle = segStart,
                            sweepAngle = segmentWidthDeg.toFloat(),
                            useCenter = true,
                            topLeft = Offset(apexOffset.x - radiusPx, apexOffset.y - radiusPx),
                            size = androidx.compose.ui.geometry.Size(radiusPx * 2f, radiusPx * 2f)
                        )
                        drawArc(
                            color = if (alarmActive) Color(0xFFFF8A80) else Color(0xFF66BB6A),
                            startAngle = segStart,
                            sweepAngle = segmentWidthDeg.toFloat(),
                            useCenter = false,
                            topLeft = Offset(apexOffset.x - radiusPx, apexOffset.y - radiusPx),
                            size = androidx.compose.ui.geometry.Size(radiusPx * 2f, radiusPx * 2f),
                            style = Stroke(width = 3f)
                        )
                        val leftEdgeDeg = segmentCenterDeg - segmentWidthDeg / 2.0
                        val rightEdgeDeg = segmentCenterDeg + segmentWidthDeg / 2.0
                        val leftVec = headingToUnitVector(normalizeDegrees(leftEdgeDeg))
                        val rightVec = headingToUnitVector(normalizeDegrees(rightEdgeDeg))
                        val edgeColor = if (alarmActive) Color(0xFFFF8A80) else Color(0xFF66BB6A)
                        drawLine(
                            color = edgeColor,
                            start = apexOffset,
                            end = Offset(
                                x = apexOffset.x + leftVec.x.toFloat() * radiusPx,
                                y = apexOffset.y - leftVec.y.toFloat() * radiusPx
                            ),
                            strokeWidth = 3f
                        )
                        drawLine(
                            color = edgeColor,
                            start = apexOffset,
                            end = Offset(
                                x = apexOffset.x + rightVec.x.toFloat() * radiusPx,
                                y = apexOffset.y - rightVec.y.toFloat() * radiusPx
                            ),
                            strokeWidth = 3f
                        )
                        drawCircle(
                            color = Color(0xFFFFD54F),
                            radius = 6f,
                            center = apexOffset
                        )
                        drawLine(
                            color = Color(0xFF7E7E7E),
                            start = anchorOffset,
                            end = apexOffset,
                            strokeWidth = 2f
                        )
                    }
                    drawCircle(
                        color = Color(0xFF00BCD4),
                        radius = 8f,
                        center = anchorOffset
                    )
                }

                if (currentProjected != null) {
                    drawCircle(
                        color = if (alarmActive) Color.Red else Color.White,
                        radius = 9f,
                        center = toOffset(currentProjected)
                    )
                }
            }
        } else {
            AnchoringOpenMap(
                anchorLocation = anchorLocation,
                displayBoatLocation = displayBoatLocation,
                trackPoints = trackPoints,
                radiusMeters = radiusMeters,
                areaMode = areaMode,
                segmentCenterDeg = segmentCenterDeg,
                segmentWidthDeg = segmentWidthDeg,
                coneApexOffsetMeters = coneApexOffsetMeters,
                autoZoomEnabled = autoZoomEnabled,
                modifier = Modifier.fillMaxSize()
            )
        }
        Surface(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp),
            color = Color(0xAA000000)
        ) {
            Text(
                text = if (renderMode == AnchorMapRenderMode.OpenMap) "OPEN MAP" else "CANVAS",
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp
            )
        }
        Surface(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(8.dp),
            color = Color(0xAA000000)
        ) {
            Text(
                text = if (autoZoomEnabled) "AUTO ZOOM ON" else "AUTO ZOOM OFF",
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun AnchoringOpenMap(
    anchorLocation: Location?,
    displayBoatLocation: Location?,
    trackPoints: List<RaceTrackPoint>,
    radiusMeters: Double,
    areaMode: AnchorAreaMode,
    segmentCenterDeg: Double,
    segmentWidthDeg: Double,
    coneApexOffsetMeters: Double,
    autoZoomEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    var hasAutoFitted by remember(
        anchorLocation?.latitude,
        anchorLocation?.longitude,
        displayBoatLocation?.latitude,
        displayBoatLocation?.longitude,
        areaMode,
        segmentCenterDeg,
        segmentWidthDeg,
        coneApexOffsetMeters,
        radiusMeters,
        autoZoomEnabled
    ) { mutableStateOf(false) }
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            org.osmdroid.config.Configuration.getInstance().userAgentValue = ctx.packageName
            MapView(ctx).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                setBuiltInZoomControls(false)
                isTilesScaledToDpi = true
                controller.setZoom(15.0)
            }
        },
        update = { mapView ->
            mapView.overlays.clear()
            val conusApexLocation = if (anchorLocation != null && areaMode == AnchorAreaMode.Conus) {
                offsetLocationByMeters(
                    start = anchorLocation,
                    bearingDeg = segmentCenterDeg,
                    distanceMeters = coneApexOffsetMeters
                )
            } else {
                null
            }
            val centerLocation = displayBoatLocation ?: anchorLocation ?: conusApexLocation
            if (autoZoomEnabled && !hasAutoFitted) {
                val fitPoints = mutableListOf<GeoPoint>()
                when (areaMode) {
                    AnchorAreaMode.Circle -> {
                        anchorLocation?.let { anchor ->
                            fitPoints += GeoPoint(anchor.latitude, anchor.longitude)
                            listOf(0.0, 90.0, 180.0, 270.0).forEach { bearing ->
                                val edge = offsetLocationByMeters(
                                    start = anchor,
                                    bearingDeg = bearing,
                                    distanceMeters = radiusMeters
                                )
                                fitPoints += GeoPoint(edge.latitude, edge.longitude)
                            }
                        }
                    }
                    AnchorAreaMode.Conus -> {
                        conusApexLocation?.let { apex ->
                            fitPoints += GeoPoint(apex.latitude, apex.longitude)
                            val halfWidth = segmentWidthDeg / 2.0
                            val startBearing = segmentCenterDeg - halfWidth
                            val segments = max(12, (segmentWidthDeg / 6.0).roundToInt())
                            for (i in 0..segments) {
                                val bearing = startBearing + (segmentWidthDeg * i / segments.toDouble())
                                val edge = offsetLocationByMeters(
                                    start = apex,
                                    bearingDeg = normalizeDegrees(bearing),
                                    distanceMeters = radiusMeters
                                )
                                fitPoints += GeoPoint(edge.latitude, edge.longitude)
                            }
                        }
                    }
                }
                if (fitPoints.size >= 2 && mapView.width > 0 && mapView.height > 0) {
                    val maxLat = fitPoints.maxOf { it.latitude }
                    val minLat = fitPoints.minOf { it.latitude }
                    val maxLon = fitPoints.maxOf { it.longitude }
                    val minLon = fitPoints.minOf { it.longitude }
                    val bbox = BoundingBox(maxLat, maxLon, minLat, minLon)
                    mapView.zoomToBoundingBox(bbox, true, 80)
                    hasAutoFitted = true
                } else {
                    centerLocation?.let { mapView.controller.setCenter(GeoPoint(it.latitude, it.longitude)) }
                }
            } else if (!autoZoomEnabled) {
                centerLocation?.let { mapView.controller.setCenter(GeoPoint(it.latitude, it.longitude)) }
            }

            if (trackPoints.size >= 2) {
                val trackOverlay = Polyline(mapView).apply {
                    outlinePaint.color = android.graphics.Color.YELLOW
                    outlinePaint.strokeWidth = 4f
                    setPoints(trackPoints.takeLast(500).map { GeoPoint(it.latitude, it.longitude) })
                }
                mapView.overlays.add(trackOverlay)
            }
            if (areaMode == AnchorAreaMode.Circle) {
                anchorLocation?.let { center ->
                    mapView.overlays.add(
                        createOsmCircleOverlay(
                            mapView = mapView,
                            geoPoint = GeoPoint(center.latitude, center.longitude),
                            fillColor = android.graphics.Color.argb(80, 102, 187, 106),
                            strokeColor = android.graphics.Color.GREEN,
                            radiusMeters = radiusMeters
                        )
                    )
                }
            } else if (conusApexLocation != null) {
                val halfWidth = segmentWidthDeg / 2.0
                val startBearing = segmentCenterDeg - halfWidth
                val segments = max(12, (segmentWidthDeg / 6.0).roundToInt())
                val sectorPoints = mutableListOf<GeoPoint>()
                sectorPoints.add(GeoPoint(conusApexLocation.latitude, conusApexLocation.longitude))
                for (i in 0..segments) {
                    val bearing = startBearing + (segmentWidthDeg * i / segments.toDouble())
                    val edgePoint = offsetLocationByMeters(
                        start = conusApexLocation,
                        bearingDeg = normalizeDegrees(bearing),
                        distanceMeters = radiusMeters
                    )
                    sectorPoints.add(GeoPoint(edgePoint.latitude, edgePoint.longitude))
                }
                sectorPoints.add(GeoPoint(conusApexLocation.latitude, conusApexLocation.longitude))
                mapView.overlays.add(
                    Polygon(mapView).apply {
                        points = sectorPoints
                        fillPaint.color = android.graphics.Color.argb(80, 102, 187, 106)
                        outlinePaint.color = android.graphics.Color.GREEN
                        outlinePaint.strokeWidth = 3f
                    }
                )
            }
            if (anchorLocation != null && conusApexLocation != null && areaMode == AnchorAreaMode.Conus) {
                val connector = Polyline(mapView).apply {
                    outlinePaint.color = android.graphics.Color.GRAY
                    outlinePaint.strokeWidth = 4f
                    setPoints(
                        listOf(
                            GeoPoint(anchorLocation.latitude, anchorLocation.longitude),
                            GeoPoint(conusApexLocation.latitude, conusApexLocation.longitude)
                        )
                    )
                }
                mapView.overlays.add(connector)
            }
            displayBoatLocation?.let { boat ->
                val boatMarker = Marker(mapView).apply {
                    position = GeoPoint(boat.latitude, boat.longitude)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    title = "Boat"
                }
                mapView.overlays.add(boatMarker)
            }
            mapView.invalidate()
        }
    )
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
    /** Dvostruki tap lijevo-dolje: Canvas ↔ OSM samo kad je North up. */
    onToggleCanvasOsmFromMap: () -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit
) {
    val canvasMapGestures = Modifier.pointerInput(mapMode, mapZoom, mapRenderMode) {
        detectTapGestures(
            onDoubleTap = { tap ->
                val w = size.width.toFloat()
                val h = size.height.toFloat()
                val topHalf = tap.y < h / 2f
                val bottomHalf = !topHalf
                val rightHalf = tap.x >= w / 2f
                val leftHalf = tap.x < w / 2f

                when {
                    topHalf && leftHalf -> onToggleMapMode()
                    topHalf && rightHalf -> onZoomIn()
                    bottomHalf && rightHalf -> onZoomOut()
                    bottomHalf && leftHalf -> onToggleCanvasOsmFromMap()
                }
            }
        )
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (mapRenderMode == MapRenderMode.Canvas) canvasMapGestures else Modifier
            )
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
            val baseScale = minOf(lineScale, fitScale)
            val scale = baseScale * mapZoom

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
            // + / − uz desni rub kao WindShiftTrackGraph (vizualna pomoć za dvostruki tap gore-desno / dolje-desno)
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        org.osmdroid.config.Configuration.getInstance().userAgentValue = ctx.packageName
                        CornerDoubleTapMapView(ctx).apply {
                            setTileSource(TileSourceFactory.MAPNIK)
                            setMultiTouchControls(false)
                            setBuiltInZoomControls(false)
                            isTilesScaledToDpi = true
                            controller.setZoom(15.0)
                        }
                    },
                    update = { mapView ->
                        val cornerMap = mapView as CornerDoubleTapMapView
                        cornerMap.onCornerDoubleTap = { topHalf, leftHalf ->
                            when {
                                topHalf && leftHalf -> onToggleMapMode()
                                topHalf && !leftHalf -> onZoomIn()
                                !topHalf && !leftHalf -> onZoomOut()
                                !topHalf && leftHalf -> onToggleCanvasOsmFromMap()
                            }
                        }
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
                val zoomHintStyle = androidx.compose.material3.LocalTextStyle.current.copy(
                    color = Color.White,
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "+",
                    style = zoomHintStyle,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .wrapContentWidth(align = Alignment.End)
                        .padding(end = 12.dp)
                        .offset(y = maxHeight * 0.25f - 22.dp)
                )
                Text(
                    text = "-",
                    style = zoomHintStyle,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .wrapContentWidth(align = Alignment.End)
                        .padding(end = 12.dp)
                        .offset(y = maxHeight * 0.75f - 22.dp)
                )
            }
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
    strokeColor: Int,
    radiusMeters: Double = 6.0
): Polygon {
    return Polygon(mapView).apply {
        points = Polygon.pointsAsCircle(geoPoint, radiusMeters)
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
    graphDisplayMode: WindShiftGraphDisplayMode,
    graphWindowMinutes: Long,
    onIncreaseGraphWindow: () -> Unit,
    onDecreaseGraphWindow: () -> Unit,
    onToggleGraphDisplayMode: () -> Unit,
    onToggleMonoSign: () -> Unit,
    modifier: Modifier = Modifier
) {
    val deviationThresholdDeg = 5.0
    val trendBarHeight = 12.dp
    val textScale = 1.5f
    val isAutoMode = graphDisplayMode == WindShiftGraphDisplayMode.Auto
    val isPositiveLeft = isAutoMode && isMonoMode && !monoSignInverted
    val latestDeviationPoint = points.maxByOrNull { it.timestampMs }
    val lastDualCloserToPort = latestDeviationPoint?.dualCloserToPort == true
    val displayCurrentDeviation = when {
        !isAutoMode && isMonoMode && monoSignInverted -> currentDeviationDeg?.times(-1.0)
        isAutoMode && !isMonoMode && lastDualCloserToPort -> currentDeviationDeg?.times(-1.0)
        else -> currentDeviationDeg
    }
    val trendColor = when {
        displayCurrentDeviation == null -> Color(0xFF78909C)
        kotlin.math.abs(displayCurrentDeviation) <= deviationThresholdDeg -> Color(0xFF78909C)
        displayCurrentDeviation > deviationThresholdDeg -> Color(0xFF2E7D32) // prema vjetru
        displayCurrentDeviation < -deviationThresholdDeg -> Color(0xFFC62828) // od vjetra
        else -> Color(0xFF78909C)
    }
    val graphModeLabel = if (isAutoMode) "AUTO GRAPH" else "NORMAL GRAPH"

    Box(
        modifier = modifier
            .pointerInput(isMonoMode, monoSignInverted, graphWindowMinutes, graphDisplayMode) {
                detectTapGestures(
                    onTap = { tap ->
                        val inLeftHalf = tap.x < size.width / 2f
                        val inTopHalf = tap.y < size.height / 2f
                        if (inLeftHalf && inTopHalf) {
                            onToggleGraphDisplayMode()
                            return@detectTapGestures
                        }
                        val inRightHalf = tap.x >= size.width / 2f
                        if (inRightHalf) {
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
            val rightLabelX = size.width - (28f * textScale)
            val labelPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.WHITE
                textSize = 24f * textScale
                isFakeBoldText = true
            }
            val controlsPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.WHITE
                textSize = 40f * textScale
                isFakeBoldText = true
                textAlign = android.graphics.Paint.Align.CENTER
            }

            drawLine(
                color = Color(0xFFFFEB3B),
                start = Offset(centerX, 0f),
                end = Offset(centerX, size.height),
                strokeWidth = 2f
            )
            val leftScaleLabel = if (isPositiveLeft) "+40°" else "-40°"
            val rightScaleLabel = if (isPositiveLeft) "-40°" else "+40°"
            drawContext.canvas.nativeCanvas.drawText(leftScaleLabel, 12f, 56f * textScale, labelPaint)
            drawContext.canvas.nativeCanvas.drawText("0°", centerX - (14f * textScale), 56f * textScale, labelPaint)
            drawContext.canvas.nativeCanvas.drawText(rightScaleLabel, size.width - (86f * textScale), 56f * textScale, labelPaint)
            drawContext.canvas.nativeCanvas.drawText("+", rightLabelX, size.height * 0.25f, controlsPaint)
            drawContext.canvas.nativeCanvas.drawText("-", rightLabelX, size.height * 0.75f, controlsPaint)

            if (isMonoMode) {
                val activeSideLabel = if (monoSignInverted) "STBD +" else "PORT +"
                val tackLabel = if (monoSignInverted) "desne uzde" else "lijeve uzde"
                drawContext.canvas.nativeCanvas.drawText(
                    activeSideLabel,
                    12f,
                    size.height - (42f * textScale),
                    labelPaint
                )
                drawContext.canvas.nativeCanvas.drawText(
                    tackLabel,
                    12f,
                    size.height - (16f * textScale),
                    labelPaint
                )
            }
            drawContext.canvas.nativeCanvas.drawText(
                modeLabel,
                12f,
                28f * textScale,
                labelPaint
            )
            drawContext.canvas.nativeCanvas.drawText(
                graphModeLabel,
                12f,
                78f * textScale,
                labelPaint
            )
            val windowMs = (graphWindowMinutes * 60_000L).toDouble()
            val timePaint = android.graphics.Paint().apply {
                color = android.graphics.Color.LTGRAY
                textSize = 22f * textScale
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
                    centerX + (10f * textScale),
                    y + (8f * textScale),
                    timePaint
                )
            }

            if (points.size < 2) {
                drawContext.canvas.nativeCanvas.drawText(
                    "Not enough data for the graph",
                    20f,
                    size.height / 2f,
                    labelPaint.apply { textSize = 30f * textScale }
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
                    labelPaint.apply { textSize = 30f * textScale }
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
                val displayDeviation = when {
                    !isAutoMode && isMonoMode && monoSignInverted -> -point.deviationDeg
                    isAutoMode && !isMonoMode && point.dualCloserToPort == true -> -point.deviationDeg
                    else -> point.deviationDeg
                }
                val x = if (isPositiveLeft) {
                    centerX - (displayDeviation / maxAbsDeviation).toFloat() * halfWidth
                } else {
                    centerX + (displayDeviation / maxAbsDeviation).toFloat() * halfWidth
                }
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
private fun WindShiftTrackOsmMap(
    samples: List<GpsSample>,
    activeWindowMinutes: Long,
    historyMinutes: Long,
    mapZoom: Float,
    orientation: WindShiftTrackOrientation,
    referenceCourseDeg: Double?,
    modifier: Modifier = Modifier
) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            org.osmdroid.config.Configuration.getInstance().userAgentValue = ctx.packageName
            MapView(ctx).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(false)
                setBuiltInZoomControls(false)
                isTilesScaledToDpi = true
                mapOrientation = 0f
                controller.setZoom(15.0)
            }
        },
        update = { mapView ->
            mapView.mapOrientation = when {
                orientation == WindShiftTrackOrientation.NorthUp -> 0f
                referenceCourseDeg != null -> -referenceCourseDeg.toFloat()
                else -> 0f
            }
            val sorted = samples.sortedBy { it.timestampMs }
            if (sorted.size < 2) {
                mapView.overlays.clear()
                mapView.invalidate()
                return@AndroidView
            }
            val latestTs = sorted.last().timestampMs
            val historyStart = latestTs - historyMinutes * 60_000L
            val visible = sorted.filter { it.timestampMs >= historyStart }
            if (visible.size < 2) {
                mapView.overlays.clear()
                mapView.invalidate()
                return@AndroidView
            }
            val activeStart = latestTs - activeWindowMinutes * 60_000L
            val latestLoc = visible.last().location
            val zoomLevel = (16.0 + kotlin.math.log2(mapZoom.toDouble())).coerceIn(3.0, 20.0)
            mapView.controller.setZoom(zoomLevel)
            mapView.controller.setCenter(GeoPoint(latestLoc.latitude, latestLoc.longitude))
            mapView.overlays.clear()
            val historyPoints = visible.map { GeoPoint(it.location.latitude, it.location.longitude) }
            mapView.overlays.add(
                Polyline(mapView).apply {
                    outlinePaint.color = android.graphics.Color.rgb(109, 143, 168)
                    outlinePaint.strokeWidth = 6f
                    setPoints(historyPoints)
                }
            )
            val activeVisible = visible.filter { it.timestampMs >= activeStart }
            if (activeVisible.size >= 2) {
                mapView.overlays.add(
                    Polyline(mapView).apply {
                        outlinePaint.color = android.graphics.Color.rgb(199, 245, 255)
                        outlinePaint.strokeWidth = 12f
                        setPoints(
                            activeVisible.map {
                                GeoPoint(it.location.latitude, it.location.longitude)
                            }
                        )
                    }
                )
            }
            mapView.invalidate()
        }
    )
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
    Box(
        modifier = modifier
            .background(Color(0xFF0E1E2F))
            .padding(6.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
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
    var openMapZoom by remember(trackName, trackPoints.size) { mutableFloatStateOf(15f) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(renderMode, trackPoints) {
                detectTapGestures(
                    onTap = { tap ->
                        val w = size.width.toFloat()
                        val h = size.height.toFloat()
                        val topHalf = tap.y < h / 2f
                        val rightHalf = tap.x >= w / 2f
                        val topLeftQuarter = tap.x < w / 2f && tap.y < h / 2f
                        when {
                            topLeftQuarter -> onToggleRenderMode()
                            topHalf && rightHalf -> {
                                if (renderMode == TrackPreviewRenderMode.OpenMap) {
                                    openMapZoom = (openMapZoom + 1f).coerceIn(3f, 20f)
                                } else {
                                    canvasUserScale = (canvasUserScale * 1.15f).coerceIn(0.5f, 12f)
                                }
                            }
                            !topHalf && rightHalf -> {
                                if (renderMode == TrackPreviewRenderMode.OpenMap) {
                                    openMapZoom = (openMapZoom - 1f).coerceIn(3f, 20f)
                                } else {
                                    canvasUserScale = (canvasUserScale / 1.15f).coerceIn(0.5f, 12f)
                                }
                            }
                        }
                    },
                    onDoubleTap = { tap ->
                        if (tap.y > size.height.toFloat() * 0.18f) {
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
                zoomLevel = openMapZoom,
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
                .align(Alignment.TopStart)
                .padding(8.dp),
            color = Color(0xAA000000)
        ) {
            Text(
                text = if (renderMode == TrackPreviewRenderMode.OpenMap) "Active: OpenMap" else "Active: Canvas",
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }

        Surface(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
                .pointerInput(renderMode, canvasUserScale, openMapZoom) {
                    detectTapGestures(
                        onTap = {
                            if (renderMode == TrackPreviewRenderMode.OpenMap) {
                                openMapZoom = (openMapZoom + 1f).coerceIn(3f, 20f)
                            } else {
                                canvasUserScale = (canvasUserScale * 1.15f).coerceIn(0.5f, 12f)
                            }
                        }
                    )
                },
            color = Color(0xAA000000)
        ) {
            Text(
                text = "+",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }

        Surface(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(8.dp)
                .pointerInput(renderMode, canvasUserScale, openMapZoom) {
                    detectTapGestures(
                        onTap = {
                            if (renderMode == TrackPreviewRenderMode.OpenMap) {
                                openMapZoom = (openMapZoom - 1f).coerceIn(3f, 20f)
                            } else {
                                canvasUserScale = (canvasUserScale / 1.15f).coerceIn(0.5f, 12f)
                            }
                        }
                    )
                },
            color = Color(0xAA000000)
        ) {
            Text(
                text = "-",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
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
    zoomLevel: Float,
    modifier: Modifier = Modifier
) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            org.osmdroid.config.Configuration.getInstance().userAgentValue = ctx.packageName
            MapView(ctx).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                setBuiltInZoomControls(false)
                isTilesScaledToDpi = true
                controller.setZoom(zoomLevel.toDouble())
            }
        },
        update = { mapView ->
            mapView.overlays.clear()
            mapView.controller.setZoom(zoomLevel.toDouble())
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