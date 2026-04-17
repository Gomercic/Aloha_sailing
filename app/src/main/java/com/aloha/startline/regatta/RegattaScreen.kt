package com.aloha.startline.regatta

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Point
import android.graphics.RectF
import android.location.Location
import android.net.Uri
import android.graphics.drawable.GradientDrawable
import android.view.MotionEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.draw.clipToBounds
import androidx.core.content.FileProvider
import com.aloha.startline.nas.NasCallResult
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.text.KeyboardOptions
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.Polyline
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.Calendar

private const val NOTICE_TIMESTAMP_PATTERN = "dd.MM.yyyy HH:mm"

@Composable
@OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    ExperimentalMaterial3Api::class
)
fun RegattaScreen(
    apiClient: RegattaApiClient,
    prefs: RegattaPreferences,
    currentLocation: Location?,
    openJoinFormOnEnter: Boolean = false,
    onSessionStateChanged: (Boolean) -> Unit = {},
    onOpenRaceStartLine: () -> Unit = {},
    onJoinModeChanged: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var mode by rememberSaveable { mutableStateOf("home") }
    var noticeListReturnMode by rememberSaveable { mutableStateOf("organizer") }
    var organizerName by rememberSaveable { mutableStateOf(prefs.organizerName) }
    var eventName by rememberSaveable { mutableStateOf("") }
    var joinCode by rememberSaveable { mutableStateOf(prefs.lastJoinCode) }
    var organizerCode by rememberSaveable { mutableStateOf(prefs.lastOrganizerAccessValue) }
    var organizerLoginJoinCode by rememberSaveable { mutableStateOf(prefs.lastJoinCode) }
    var organizerLoginHash by rememberSaveable { mutableStateOf(prefs.lastOrganizerAccessValue) }
    var regattaStartDate by rememberSaveable { mutableStateOf("") }
    var regattaEndDate by rememberSaveable { mutableStateOf("") }
    var maxBoatsInput by rememberSaveable { mutableStateOf("50") }
    var quickAccessInput by rememberSaveable {
        mutableStateOf(prefs.lastJoinCode)
    }
    var isPublicEvent by rememberSaveable { mutableStateOf(false) }
    var revealOrganizerCode by rememberSaveable { mutableStateOf("") }
    var boatName by rememberSaveable { mutableStateOf(prefs.lastBoatName) }
    var skipperName by rememberSaveable { mutableStateOf(prefs.lastSkipperName) }
    var clubName by rememberSaveable { mutableStateOf(prefs.lastClubName) }
    var lengthValue by rememberSaveable { mutableStateOf(prefs.lastBoatLengthValue) }
    var lengthUnit by rememberSaveable { mutableStateOf(prefs.lastBoatLengthUnit) }
    var lengthUnitMenuExpanded by remember { mutableStateOf(false) }
    var groupCode by rememberSaveable { mutableStateOf("") }
    var joinedEventId by rememberSaveable { mutableStateOf(prefs.joinedEventId) }
    var joinedRaceId by rememberSaveable { mutableStateOf(prefs.joinedRaceId) }
    var joinedBoatId by rememberSaveable { mutableStateOf(prefs.joinedBoatId) }
    var eventSnapshot by remember { mutableStateOf<RegattaEventSnapshot?>(null) }
    var liveSnapshot by remember { mutableStateOf<RegattaLiveSnapshot?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isBusy by remember { mutableStateOf(false) }
    var noticeBoardInput by rememberSaveable { mutableStateOf("") }
    var publicHistory by remember { mutableStateOf<List<PublicRegattaEventSummary>>(emptyList()) }
    var publicHistoryLoading by remember { mutableStateOf(false) }
    var historySelectedEvent by remember { mutableStateOf<RegattaEventSnapshot?>(null) }
    var historySelectedRace by remember { mutableStateOf<RegattaRaceSummary?>(null) }
    var historySelectedRaceLive by remember { mutableStateOf<RegattaLiveSnapshot?>(null) }
    var historyRegattaRaceSnapshots by remember { mutableStateOf<List<RegattaLiveSnapshot>>(emptyList()) }
    var historySelectedBoatResult by remember { mutableStateOf<RaceScoreRow?>(null) }
    var historyMapBoatMenuExpanded by remember { mutableStateOf(false) }
    var historyMapSelectedBoatId by remember { mutableStateOf("") }
    var historyMapBoatTrack by remember { mutableStateOf<List<RegattaBoatTrackPoint>>(emptyList()) }
    var historyDetailsLoading by remember { mutableStateOf(false) }
    var showDeleteBoatConfirm by remember { mutableStateOf(false) }
    var pendingDeleteBoat by remember { mutableStateOf<RegattaBoatSummary?>(null) }
    var showDeleteNoticeConfirm by remember { mutableStateOf(false) }
    var pendingDeleteNotice by remember { mutableStateOf<RegattaNoticePost?>(null) }
    var showDeleteRaceConfirm by remember { mutableStateOf(false) }
    var pendingDeleteRace by remember { mutableStateOf<RegattaRaceSummary?>(null) }
    var organizerLoginAskedCreateNew by remember { mutableStateOf(false) }
    var organizerRaceDateInput by rememberSaveable { mutableStateOf("") }
    var organizerRaceStartTimeInput by rememberSaveable { mutableStateOf("10:00") }
    var organizerRaceEndTimeInput by rememberSaveable { mutableStateOf("16:00") }
    var organizerRaceLengthNmInput by rememberSaveable { mutableStateOf("") }
    var organizerEditingRaceId by rememberSaveable { mutableStateOf("") }
    var organizerGateSelection by rememberSaveable { mutableStateOf("start") }
    var organizerRaceEditorStartGate by remember { mutableStateOf<RegattaGate?>(null) }
    var organizerRaceEditorFinishGate by remember { mutableStateOf<RegattaGate?>(null) }
    var organizerRaceEditorHelperGates by remember { mutableStateOf<List<RegattaGate>>(emptyList()) }
    var organizerSelectedMapPoint by remember { mutableStateOf<RegattaPoint?>(null) }
    var organizerGateMenuExpanded by remember { mutableStateOf(false) }
    var scoringTargetGateMenuExpanded by remember { mutableStateOf(false) }
    var organizerManualBoatMenuExpanded by remember { mutableStateOf(false) }
    var organizerManualBoatId by rememberSaveable { mutableStateOf("") }
    var organizerCrossingMenuId by remember { mutableStateOf<String?>(null) }
    var organizerCreateFlow by rememberSaveable { mutableStateOf(false) }
    var organizerPublicResultMenuExpanded by remember { mutableStateOf(false) }
    var joinPrefillApplied by rememberSaveable { mutableStateOf(false) }
    var joinSnapshotLoadedForCode by rememberSaveable { mutableStateOf("") }
    var backupTakeoverAvailable by rememberSaveable { mutableStateOf(false) }
    var startTimeInput by rememberSaveable { mutableStateOf("") }
    var scoringTargetGateId by rememberSaveable { mutableStateOf("") }
    var startA by remember { mutableStateOf<RegattaPoint?>(null) }
    var startB by remember { mutableStateOf<RegattaPoint?>(null) }
    var finishA by remember { mutableStateOf<RegattaPoint?>(null) }
    var finishB by remember { mutableStateOf<RegattaPoint?>(null) }
    var pendingHelperName by rememberSaveable { mutableStateOf("Gate 1") }
    var pendingHelperA by remember { mutableStateOf<RegattaPoint?>(null) }
    var pendingHelperB by remember { mutableStateOf<RegattaPoint?>(null) }
    var helperGates by remember { mutableStateOf<List<PendingGateDraft>>(emptyList()) }
    val groupInputs = remember { mutableStateMapOf<String, String>() }
    val penaltyValueInputs = remember { mutableStateMapOf<String, String>() }
    val penaltyReasonInputs = remember { mutableStateMapOf<String, String>() }
    val crossingStatusInputs = remember { mutableStateMapOf<String, String>() }
    val background = Color.Black
    val cardColor = Color(0xFF111111)
    val actionGreen = Color(0xFF2E7D32)
    val actionBlue = Color(0xFF1565C0)
    val actionOrange = Color(0xFFEF6C00)
    val muted = Color(0xFFBDBDBD)

    fun handleJoinRegattaNotFound() {
        errorMessage = "Regatta not found."
    }

    LaunchedEffect(openJoinFormOnEnter) {
        if (!openJoinFormOnEnter) return@LaunchedEffect
        // Always open the login/join landing screen when requested from the app menu.
        mode = "home"
        errorMessage = null
        statusMessage = null
    }

    fun refreshPublicHistory(openHistoryMode: Boolean) {
        publicHistoryLoading = true
        errorMessage = null
        statusMessage = null
        scope.launch {
            when (val result = withContext(Dispatchers.IO) { apiClient.listPublicEvents() }) {
                is NasCallResult.Ok -> {
                    publicHistory = result.value.sortedWith(
                        compareByDescending<PublicRegattaEventSummary> { it.startDate.orEmpty() }
                            .thenByDescending { it.updatedAt }
                    )
                    historySelectedEvent = null
                    historySelectedRace = null
                    historySelectedRaceLive = null
                    historyRegattaRaceSnapshots = emptyList()
                    historySelectedBoatResult = null
                    if (openHistoryMode) {
                        mode = "history"
                    }
                }
                is NasCallResult.Err -> errorMessage = result.message
            }
            publicHistoryLoading = false
        }
    }

    fun snapshotPoint(): RegattaPoint? {
        val location = currentLocation ?: return null
        return RegattaPoint(location.latitude, location.longitude)
    }

    fun organizerScoringSelectionForRace(race: RegattaRaceSummary?): String {
        if (race == null) return "finish"
        val finishGateId = race.gates
            .firstOrNull { it.type.equals("finish", ignoreCase = true) }
            ?.id
        val selected = race.scoringTargetGateId?.trim().orEmpty()
        if (selected.isBlank()) return "finish"
        if (finishGateId != null && selected == finishGateId) return "finish"
        return if (race.gates.any { it.id == selected && it.type.equals("gate", ignoreCase = true) }) {
            selected
        } else {
            "finish"
        }
    }

    fun resolveOrganizerScoringGateId(
        selectedScoringGate: String,
        helperGatesBeforeSave: List<PendingGateDraft>,
        refreshedRace: RegattaRaceSummary?,
        previousFinishGateId: String?
    ): String? {
        if (refreshedRace == null) return null
        val finishGate = refreshedRace.gates.firstOrNull { it.type.equals("finish", ignoreCase = true) }
        val helperRaceGates = refreshedRace.gates
            .filter { it.type.equals("gate", ignoreCase = true) }
            .sortedBy { it.order }
        val selection = selectedScoringGate.trim()
        if (selection.isBlank()) return finishGate?.id ?: helperRaceGates.firstOrNull()?.id
        if (selection == "finish") return finishGate?.id
        if (previousFinishGateId != null && selection == previousFinishGateId) return finishGate?.id
        if (refreshedRace.gates.any { it.id == selection && !it.type.equals("start", ignoreCase = true) }) {
            return selection
        }
        val helperIndex = helperGatesBeforeSave.indexOfFirst { it.id == selection }
        if (helperIndex >= 0) {
            return helperRaceGates.getOrNull(helperIndex)?.id
        }
        return finishGate?.id ?: helperRaceGates.firstOrNull()?.id
    }

    fun computeAutoRaceStateLabel(race: RegattaRaceSummary, nowEpochMs: Long = System.currentTimeMillis()): String {
        val startEpoch = race.countdownTargetEpochMs ?: return "prepared"
        val activeStartEpoch = startEpoch - 30L * 60L * 1_000L
        if (nowEpochMs < activeStartEpoch) return "prepared"
        val raceDate = race.raceDate ?: SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(startEpoch))
        val endTime = race.endTime ?: return "active"
        val endEpoch = runCatching {
            val format = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).apply { isLenient = false }
            format.parse("$raceDate $endTime")?.time
        }.getOrNull() ?: return "active"
        val activeUntilEpoch = endEpoch + 15L * 60L * 1_000L
        return if (nowEpochMs <= activeUntilEpoch) "active" else "finished"
    }

    var organizerToken by rememberSaveable { mutableStateOf("") }
    var organizerEventIds by remember { mutableStateOf(prefs.organizerEventIds) }
    val isOrganizer = organizerToken.isNotBlank()

    fun persistJoinBoatDetails() {
        prefs.saveLastBoatDetails(
            boatName = boatName,
            skipperName = skipperName,
            clubName = clubName,
            lengthValue = lengthValue,
            lengthUnit = lengthUnit
        )
    }

    fun resumeSavedJoinSession(openRaceStartLine: Boolean) {
        if (joinedEventId.isBlank() || joinedBoatId.isBlank()) return
        if (joinedRaceId.isBlank()) {
            joinedRaceId = eventSnapshot?.activeRaceId.orEmpty()
        }
        backupTakeoverAvailable = false
        prefs.saveJoinedEvent(joinedEventId, joinedRaceId, joinedBoatId)
        statusMessage = if (openRaceStartLine) {
            "Boat is already active. Reconnected to saved session. Opening Race Start Line."
        } else {
            "Boat is already active. Reconnected to saved session."
        }
        if (openRaceStartLine) {
            onOpenRaceStartLine()
        }
    }

    fun resolveAlreadyActiveJoinConflict(openRaceStartLine: Boolean, apiErrorMessage: String) {
        backupTakeoverAvailable = true
        val hasSavedSession = joinedEventId.isNotBlank() && joinedBoatId.isNotBlank()
        if (hasSavedSession) {
            resumeSavedJoinSession(openRaceStartLine = openRaceStartLine)
            return
        }
        errorMessage = if (openRaceStartLine) {
            "Boat is already active. If this backup phone should take over, press Backup takeover."
        } else {
            "Boat is already active. Press Backup takeover if this phone should take over."
        }
        statusMessage = null
        if (!apiErrorMessage.contains("already active", ignoreCase = true)) {
            errorMessage = apiErrorMessage
        }
    }

    fun attemptBackupTakeover(openRaceStartLine: Boolean) {
        val normalizedJoinCode = joinCode.trim().uppercase(Locale.US)
        if (normalizedJoinCode.isBlank()) {
            errorMessage = "Enter join code first."
            return
        }
        val normalizedBoatName = boatName.trim()
        if (normalizedBoatName.isBlank()) {
            errorMessage = "Enter boat name first."
            return
        }
        isBusy = true
        errorMessage = null
        statusMessage = null
        scope.launch {
            val lookupResult = withContext(Dispatchers.IO) {
                apiClient.getEventByJoinCode(normalizedJoinCode)
            }
            when (lookupResult) {
                is NasCallResult.Ok -> {
                    val snapshotResult = withContext(Dispatchers.IO) {
                        apiClient.getEventSnapshot(lookupResult.value.eventId)
                    }
                    when (snapshotResult) {
                        is NasCallResult.Ok -> {
                            val snapshot = snapshotResult.value
                            eventSnapshot = snapshot
                            val normalizedSkipperName = skipperName.trim()
                            val candidates = snapshot.boats.filter { boat ->
                                val sameBoatName = boat.boatName.trim().equals(normalizedBoatName, ignoreCase = true)
                                val skipperMatches = normalizedSkipperName.isBlank() ||
                                    boat.skipperName.trim().equals(normalizedSkipperName, ignoreCase = true)
                                sameBoatName && skipperMatches
                            }
                            when {
                                candidates.isEmpty() -> {
                                    errorMessage = "No matching boat found for takeover. Check boat/skipper names."
                                }
                                candidates.size > 1 -> {
                                    errorMessage = "Multiple boats match. Enter skipper name exactly, then try again."
                                }
                                else -> {
                                    val targetBoat = candidates.first()
                                    val boatDeviceId = targetBoat.deviceId.orEmpty().trim()
                                    if (boatDeviceId.equals(prefs.deviceId, ignoreCase = true)) {
                                        joinedEventId = snapshot.eventId
                                        joinedRaceId = snapshot.activeRaceId.orEmpty()
                                        joinedBoatId = targetBoat.id
                                        prefs.saveJoinedEvent(joinedEventId, joinedRaceId, joinedBoatId)
                                        backupTakeoverAvailable = false
                                        statusMessage = "Boat is already active on this device. No takeover needed."
                                    } else {
                                        joinedEventId = snapshot.eventId
                                        joinedRaceId = snapshot.activeRaceId.orEmpty()
                                        joinedBoatId = targetBoat.id
                                        prefs.saveLastJoinCode(normalizedJoinCode)
                                        prefs.saveJoinedEvent(joinedEventId, joinedRaceId, joinedBoatId)
                                        backupTakeoverAvailable = false
                                        val boatCodeMessage = targetBoat.boatCode?.let { " Boat code: $it." }.orEmpty()
                                        statusMessage = "Backup takeover active.$boatCodeMessage"
                                        if (openRaceStartLine) {
                                            onOpenRaceStartLine()
                                        }
                                    }
                                }
                            }
                        }
                        is NasCallResult.Err -> {
                            errorMessage = snapshotResult.message
                        }
                    }
                }
                is NasCallResult.Err -> {
                    errorMessage = lookupResult.message
                }
            }
            isBusy = false
        }
    }

    fun tryResumeJoinWithoutApi(openRaceStartLine: Boolean): Boolean {
        val hasSavedSession = joinedEventId.isNotBlank() && joinedBoatId.isNotBlank()
        val sameJoinCode = joinCode.trim().equals(prefs.lastJoinCode.trim(), ignoreCase = true)
        if (hasSavedSession && sameJoinCode) {
            resumeSavedJoinSession(openRaceStartLine = openRaceStartLine)
            return true
        }
        return false
    }

    fun applyJoinPrefill(prefill: RegattaJoinPrefillResult) {
        prefill.boatName?.let { boatName = it }
        prefill.skipperName?.let { skipperName = it }
        prefill.clubName?.let { clubName = it }
        prefill.lengthValue?.let { value ->
            lengthValue = String.format(Locale.US, "%.2f", value)
        }
        lengthUnit = prefill.lengthUnit ?: "m"
        persistJoinBoatDetails()
    }

    fun applyBoatSummaryPrefill(boat: RegattaBoatSummary) {
        boatName = boat.boatName
        skipperName = boat.skipperName
        clubName = boat.clubName
        lengthValue = boat.lengthValue
            ?.let { String.format(Locale.US, "%.2f", it) }
            .orEmpty()
        lengthUnit = boat.lengthUnit ?: "m"
        persistJoinBoatDetails()
    }

    fun normalizeEditorHelperGates(gates: List<RegattaGate>): List<RegattaGate> {
        return gates.mapIndexed { index, gate ->
            gate.copy(
                order = index + 2,
                type = "gate",
                name = "Gate ${index + 1}"
            )
        }
    }

    fun createFallbackGate(id: String, order: Int, type: String, name: String): RegattaGate {
        val reference = currentLocation?.let { RegattaPoint(it.latitude, it.longitude) } ?: RegattaPoint(0.0, 0.0)
        return RegattaGate(
            id = id,
            order = order,
            type = type,
            name = name,
            pointA = reference,
            pointB = reference
        )
    }

    fun openOrganizerRaceEditor(race: RegattaRaceSummary) {
        organizerEditingRaceId = race.id
        organizerRaceDateInput = race.raceDate.orEmpty()
        organizerRaceStartTimeInput = race.startTime ?: "10:00"
        organizerRaceEndTimeInput = race.endTime ?: "16:00"
        organizerRaceLengthNmInput = race.raceLengthNm
            .takeIf { it > 0.0 }
            ?.let { String.format(Locale.US, "%.2f", it) }
            .orEmpty()
        organizerRaceEditorStartGate = race.gates
            .firstOrNull { it.type.equals("start", ignoreCase = true) }
            ?: createFallbackGate("start-fallback", 1, "start", "Start line")
        organizerRaceEditorFinishGate = race.gates
            .firstOrNull { it.type.equals("finish", ignoreCase = true) }
            ?: createFallbackGate("finish-fallback", 999, "finish", "Finish line")
        organizerRaceEditorHelperGates = normalizeEditorHelperGates(
            race.gates
                .filter { it.type.equals("gate", ignoreCase = true) }
                .sortedBy { it.order }
        )
        organizerGateSelection = "start"
        organizerSelectedMapPoint = null
        mode = "organizer_race_editor"
    }

    fun refreshOrganizerBoatList() {
        if (joinedEventId.isBlank()) {
            errorMessage = "Organizer session missing."
            return
        }
        isBusy = true
        errorMessage = null
        scope.launch {
            when (val snapshotResult = withContext(Dispatchers.IO) {
                apiClient.getEventSnapshot(joinedEventId)
            }) {
                is NasCallResult.Ok -> eventSnapshot = snapshotResult.value
                is NasCallResult.Err -> errorMessage = snapshotResult.message
            }
            isBusy = false
        }
    }

    LaunchedEffect(joinedEventId, joinedBoatId, organizerToken) {
        onSessionStateChanged(
            joinedEventId.isNotBlank() || joinedBoatId.isNotBlank() || organizerToken.isNotBlank()
        )
    }
    LaunchedEffect(joinedEventId, joinedBoatId) {
        onJoinModeChanged(joinedEventId.isNotBlank() && joinedBoatId.isNotBlank())
    }
    LaunchedEffect(mode, joinCode, joinedBoatId) {
        if (mode != "join") {
            joinPrefillApplied = false
        }
    }
    LaunchedEffect(mode, joinCode) {
        if (mode != "join") return@LaunchedEffect
        val normalizedJoinCode = joinCode.trim().uppercase(Locale.US)
        if (normalizedJoinCode.isBlank()) return@LaunchedEffect
        if (joinSnapshotLoadedForCode == normalizedJoinCode) return@LaunchedEffect
        when (val lookupResult = withContext(Dispatchers.IO) { apiClient.getEventByJoinCode(normalizedJoinCode) }) {
            is NasCallResult.Ok -> {
                when (val snapshotResult = withContext(Dispatchers.IO) {
                    apiClient.getEventSnapshot(lookupResult.value.eventId)
                }) {
                    is NasCallResult.Ok -> {
                        eventSnapshot = snapshotResult.value
                        joinSnapshotLoadedForCode = normalizedJoinCode
                    }
                    is NasCallResult.Err -> Unit
                }
            }
            is NasCallResult.Err -> Unit
        }
    }
    LaunchedEffect(mode, joinCode) {
        if (mode != "join") return@LaunchedEffect
        if (joinPrefillApplied) return@LaunchedEffect
        val normalizedJoinCode = joinCode.trim().uppercase(Locale.US)
        if (normalizedJoinCode.isBlank()) return@LaunchedEffect
        when (val prefillResult = withContext(Dispatchers.IO) {
            apiClient.getJoinPrefill(
                joinCode = normalizedJoinCode,
                deviceId = prefs.deviceId
            )
        }) {
            is NasCallResult.Ok -> {
                if (prefillResult.value.found) {
                    applyJoinPrefill(prefillResult.value)
                    statusMessage = "Existing boat found on this phone. Data prefilled."
                    joinPrefillApplied = true
                }
            }
            is NasCallResult.Err -> Unit
        }
    }
    LaunchedEffect(mode, eventSnapshot, joinedBoatId) {
        if (mode != "join") return@LaunchedEffect
        if (joinPrefillApplied) return@LaunchedEffect
        val boatId = joinedBoatId.trim()
        if (boatId.isBlank()) return@LaunchedEffect
        eventSnapshot?.boats
            ?.firstOrNull { it.id == boatId }
            ?.let {
                applyBoatSummaryPrefill(it)
                joinPrefillApplied = true
            }
    }
    LaunchedEffect(joinedEventId, joinedRaceId) {
        if (joinedEventId.isBlank()) return@LaunchedEffect
        while (true) {
            val snapshotResult = withContext(Dispatchers.IO) {
                apiClient.getEventSnapshot(joinedEventId)
            }
            when (snapshotResult) {
                is NasCallResult.Ok -> {
                    eventSnapshot = snapshotResult.value
                    if (joinedRaceId.isBlank()) {
                        joinedRaceId = snapshotResult.value.activeRaceId.orEmpty()
                        prefs.saveJoinedEvent(joinedEventId, joinedRaceId, joinedBoatId)
                    }
                }
                is NasCallResult.Err -> errorMessage = snapshotResult.message
            }
            if (joinedRaceId.isNotBlank()) {
                when (val liveResult = withContext(Dispatchers.IO) { apiClient.getLiveSnapshot(joinedRaceId) }) {
                    is NasCallResult.Ok -> liveSnapshot = liveResult.value
                    is NasCallResult.Err -> errorMessage = liveResult.message
                }
            }
            delay(30_000L)
        }
    }
    LaunchedEffect(liveSnapshot) {
        liveSnapshot?.participants?.forEach { participant ->
            if (!groupInputs.containsKey(participant.boatId)) {
                groupInputs[participant.boatId] = participant.groupCode.orEmpty()
            }
            if (!penaltyValueInputs.containsKey(participant.boatId)) {
                penaltyValueInputs[participant.boatId] = "0"
            }
            if (!penaltyReasonInputs.containsKey(participant.boatId)) {
                penaltyReasonInputs[participant.boatId] = ""
            }
        }
        liveSnapshot?.crossings?.forEach { crossing ->
            if (!crossingStatusInputs.containsKey(crossing.id)) {
                crossingStatusInputs[crossing.id] = crossing.status
            }
        }
        if (organizerManualBoatId.isBlank()) {
            organizerManualBoatId = liveSnapshot?.participants?.firstOrNull()?.boatId.orEmpty()
        }
    }
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = background
    ) {
        val shouldScrollContent = mode != "history_race_map"
        val contentModifier = if (shouldScrollContent) {
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(12.dp)
        } else {
            Modifier
                .fillMaxSize()
                .padding(12.dp)
        }
        Column(
            modifier = contentModifier,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (mode != "organizer" && mode != "organizer_editor" && mode != "organizer_race_editor") {
                val headerTitle = when (mode) {
                    "organizer_boat_list" -> "Boat list NEW"
                    "organizer_races" -> "Race list"
                    "organizer_results" -> "Results"
                    "organizer_notices" -> "Notice list"
                    "home" -> ""
                    else -> if (!mode.startsWith("organizer")) "Regatta" else ""
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        if (headerTitle.isNotBlank()) {
                            Text(
                                text = headerTitle,
                                color = Color.White,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    if (mode != "home") {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(
                                onClick = {
                                    mode = when {
                                        mode == "organizer" -> "home"
                                        mode.startsWith("organizer_") -> "organizer_editor"
                                        else -> "home"
                                    }
                                    errorMessage = null
                                    statusMessage = null
                                },
                                modifier = Modifier.height(28.dp),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                            ) {
                                Text("Home", fontSize = 11.sp)
                            }
                            if (mode == "organizer_boat_list") {
                                TextButton(
                                    enabled = !isBusy,
                                    onClick = { refreshOrganizerBoatList() },
                                    modifier = Modifier.height(28.dp),
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                                ) {
                                    Text(if (isBusy) "..." else "Refresh", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }

            statusMessage?.let { Text(text = it, color = Color(0xFFB8E0D0)) }
            errorMessage?.let { Text(text = it, color = Color(0xFFFF8A80)) }

            if (mode == "home") {
                Text("Join Regatta", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                OutlinedTextField(
                    value = quickAccessInput,
                    onValueChange = {
                        val normalized = it.uppercase(Locale.US)
                        quickAccessInput = normalized
                        prefs.saveLastJoinCode(normalized)
                    },
                    label = { Text("Join code") },
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    enabled = !isBusy,
                    onClick = {
                        val input = quickAccessInput.trim()
                        if (input.isBlank()) {
                            errorMessage = "Enter join code."
                            return@Button
                        }
                        isBusy = true
                        errorMessage = null
                        statusMessage = null
                        scope.launch {
                            val normalizedJoinCode = input.uppercase(Locale.US)
                            prefs.saveLastJoinCode(normalizedJoinCode)
                            joinCode = normalizedJoinCode
                            when (withContext(Dispatchers.IO) { apiClient.getEventByJoinCode(normalizedJoinCode) }) {
                                is NasCallResult.Ok -> {
                                    mode = "join"
                                    when (val prefillResult = withContext(Dispatchers.IO) {
                                        apiClient.getJoinPrefill(
                                            joinCode = normalizedJoinCode,
                                            deviceId = prefs.deviceId
                                        )
                                    }) {
                                        is NasCallResult.Ok -> {
                                            if (prefillResult.value.found) {
                                                applyJoinPrefill(prefillResult.value)
                                                statusMessage = "Existing boat found on this phone. Data prefilled."
                                            } else {
                                                statusMessage = "Regatta found. Complete join details."
                                            }
                                        }
                                        is NasCallResult.Err -> {
                                            statusMessage = "Regatta found. Complete join details."
                                        }
                                    }
                                }
                                is NasCallResult.Err -> handleJoinRegattaNotFound()
                            }
                            isBusy = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = actionBlue)
                ) {
                    Text(if (isBusy) "Working..." else "Join")
                }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { refreshPublicHistory(openHistoryMode = true) },
                    enabled = !publicHistoryLoading,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF424242))
                ) {
                    Text(if (publicHistoryLoading) "Loading..." else "Regatta list")
                }
                Spacer(Modifier.weight(1f))
                TextButton(
                    onClick = { mode = "organizer" },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 32.dp)
                ) {
                    Text("Regatta Organizer Login", color = muted)
                }
            }

            if (mode == "history") {
                RegattaSection(title = "Public Regatta History", cardColor = cardColor) {
                    Button(
                        onClick = { mode = "home" },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF424242))
                    ) {
                        Text("Back to home")
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { refreshPublicHistory(openHistoryMode = false) },
                        enabled = !publicHistoryLoading,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF424242))
                    ) {
                        Text(if (publicHistoryLoading) "Refreshing..." else "Refresh list")
                    }
                    Spacer(Modifier.height(8.dp))
                    if (publicHistory.isEmpty()) {
                        Text("No public regattas found.", color = muted)
                    } else {
                        publicHistory.forEach { regata ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = !historyDetailsLoading) {
                                        historyDetailsLoading = true
                                        errorMessage = null
                                        statusMessage = null
                                        scope.launch {
                                            when (val snapshotResult = withContext(Dispatchers.IO) {
                                                apiClient.getEventSnapshot(regata.eventId)
                                            }) {
                                                is NasCallResult.Ok -> {
                                                    historySelectedEvent = snapshotResult.value
                                                    historySelectedRace = null
                                                    historySelectedRaceLive = null
                                                    historyRegattaRaceSnapshots = emptyList()
                                                    historySelectedBoatResult = null
                                                    mode = "history_event"
                                                }
                                                is NasCallResult.Err -> errorMessage = snapshotResult.message
                                            }
                                            historyDetailsLoading = false
                                        }
                                    }
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFF171717))
                                        .padding(10.dp)
                                ) {
                                    Text(regata.name, color = Color.White, fontWeight = FontWeight.Bold)
                                    Text("Races: ${regata.racesCount}", color = Color(0xFFCFD8DC), fontSize = 12.sp)
                                    Text(
                                        "Dates: ${regata.startDate ?: "--"} -> ${regata.endDate ?: "--"}",
                                        color = Color(0xFFCFD8DC),
                                        fontSize = 12.sp
                                    )
                                    Text("Points: ${regata.pointsCount}", color = Color(0xFFCFD8DC), fontSize = 12.sp)
                                    if (regata.updatedAt.isNotBlank()) {
                                        Text("Updated: ${regata.updatedAt}", color = Color(0xFFCFD8DC), fontSize = 12.sp)
                                    }
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                        }
                    }
                }
            }

            if (mode == "history_event") {
                val selectedEvent = historySelectedEvent
                RegattaSection(title = "Regatta Races", cardColor = cardColor) {
                    if (selectedEvent == null) {
                        Text("Regatta not loaded.", color = muted)
                    } else {
                        Text(selectedEvent.name, color = Color.White, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = { mode = "history" },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF424242))
                        ) { Text("Back to history") }
                        Spacer(Modifier.height(10.dp))
                        if (selectedEvent.races.isEmpty()) {
                            Text("No races found.", color = muted)
                        } else {
                            selectedEvent.races.forEach { race ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(enabled = !historyDetailsLoading) {
                                            historyDetailsLoading = true
                                            errorMessage = null
                                            statusMessage = null
                                            scope.launch {
                                                when (val liveResult = withContext(Dispatchers.IO) {
                                                    apiClient.getLiveSnapshot(race.id)
                                                }) {
                                                    is NasCallResult.Ok -> {
                                                        val selectedEvent = historySelectedEvent
                                                        val raceIdsUpToSelected = selectedEvent?.races
                                                            ?.takeWhile { it.id != race.id }
                                                            ?.map { it.id }
                                                            .orEmpty() + race.id
                                                        val snapshotsForRegattaTable = mutableListOf<RegattaLiveSnapshot>()
                                                        var loadingFailed = false
                                                        raceIdsUpToSelected.forEach { raceId ->
                                                            when (val snapshotResult = withContext(Dispatchers.IO) {
                                                                apiClient.getLiveSnapshot(raceId)
                                                            }) {
                                                                is NasCallResult.Ok -> snapshotsForRegattaTable += snapshotResult.value
                                                                is NasCallResult.Err -> {
                                                                    errorMessage = snapshotResult.message
                                                                    loadingFailed = true
                                                                    return@forEach
                                                                }
                                                            }
                                                        }
                                                        if (loadingFailed) {
                                                            historyDetailsLoading = false
                                                            return@launch
                                                        }
                                                        historySelectedRace = race
                                                        historySelectedRaceLive = liveResult.value
                                                        historyRegattaRaceSnapshots = snapshotsForRegattaTable
                                                        historySelectedBoatResult = null
                                                        historyMapSelectedBoatId = ""
                                                        historyMapBoatTrack = emptyList()
                                                        mode = "history_race"
                                                    }
                                                    is NasCallResult.Err -> errorMessage = liveResult.message
                                                }
                                                historyDetailsLoading = false
                                            }
                                        }
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color(0xFF171717))
                                            .padding(10.dp)
                                    ) {
                                        Text(
                                            race.name.ifBlank { "Race ${race.sequenceNumber}" },
                                            color = Color.White,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            "Start: ${race.countdownTargetEpochMs?.let(::formatEpoch) ?: "--"}",
                                            color = Color(0xFFCFD8DC),
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                                Spacer(Modifier.height(6.dp))
                            }
                        }
                    }
                }
            }

            if (mode == "history_race") {
                val selectedEvent = historySelectedEvent
                val selectedRace = historySelectedRace
                val raceLive = historySelectedRaceLive
                RegattaSection(title = "Results", cardColor = cardColor) {
                    if (selectedEvent == null || selectedRace == null || raceLive == null) {
                        Text("Results not available.", color = muted)
                    } else {
                        val raceRows = computeRaceScoreRows(raceLive, selectedEvent.boats)
                        val (gateTimelineHeaders, gateTimelineRows) = computeRaceGateTimelineRows(
                            live = raceLive,
                            boats = selectedEvent.boats
                        )
                        val gateTimelineRowsByGroup = gateTimelineRows
                            .groupBy { normalizeGroupLabel(it.groupCode) }
                            .toSortedMap()
                        val raceRowsByGroup = raceRows
                            .groupBy { normalizeGroupLabel(it.groupCode) }
                            .toSortedMap()
                        val selectedRaceIds = selectedEvent.races
                            .takeWhile { it.id != selectedRace.id }
                            .map { it.id } + selectedRace.id
                        val regattaRows = computeEventScoreTable(
                            event = selectedEvent,
                            raceSnapshots = historyRegattaRaceSnapshots,
                            raceIds = selectedRaceIds
                        )
                        val regattaRowsByGroup = regattaRows
                            .groupBy { normalizeGroupLabel(it.groupCode) }
                            .toSortedMap()
                        Text(
                            selectedRace.name.ifBlank { "Race ${selectedRace.sequenceNumber}" },
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Start: ${selectedRace.countdownTargetEpochMs?.let(::formatEpoch) ?: "--"}",
                            color = Color(0xFFCFD8DC),
                            fontSize = 12.sp
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = { mode = "history_event" },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF424242))
                        ) { Text("Back to races") }
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = { mode = "history_race_map" },
                            colors = ButtonDefaults.buttonColors(containerColor = actionBlue)
                        ) { Text("Map") }
                        Spacer(Modifier.height(10.dp))
                        Text("Gate crossings", color = Color.White, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        val gateTimelineHorizontalScroll = rememberScrollState()
                        if (gateTimelineHeaders.isEmpty()) {
                            Text("No gates defined for crossings.", color = muted)
                        } else if (gateTimelineRows.isEmpty()) {
                            Text("No boats to display crossings.", color = muted)
                        } else {
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier
                                        .horizontalScroll(gateTimelineHorizontalScroll)
                                        .background(Color(0xFF263238))
                                        .padding(8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text("Boat", color = Color.White, fontSize = 11.sp, modifier = Modifier.width(130.dp))
                                    Text("Group", color = Color.White, fontSize = 11.sp, modifier = Modifier.width(80.dp))
                                    gateTimelineHeaders.forEach { gateHeader ->
                                        Text(gateHeader, color = Color.White, fontSize = 11.sp, modifier = Modifier.width(84.dp))
                                    }
                                    Text("Corrected", color = Color.White, fontSize = 11.sp, modifier = Modifier.width(90.dp))
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            gateTimelineRowsByGroup.forEach { (groupName, rowsInGroup) ->
                                Text("Group: $groupName", color = Color(0xFFFFF59D), fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.height(4.dp))
                                rowsInGroup.forEach { row ->
                                    Card(modifier = Modifier.fillMaxWidth()) {
                                        Row(
                                            modifier = Modifier
                                                .horizontalScroll(gateTimelineHorizontalScroll)
                                                .background(Color(0xFF171717))
                                                .padding(10.dp),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(row.boatName, color = Color.White, fontSize = 11.sp, modifier = Modifier.width(130.dp))
                                            Text(groupName, color = Color.White, fontSize = 11.sp, modifier = Modifier.width(80.dp))
                                            row.gateTimes.forEach { crossingEpoch ->
                                                Text(
                                                    crossingEpoch?.let(::formatEpochTime) ?: "--",
                                                    color = Color.White,
                                                    fontSize = 11.sp,
                                                    modifier = Modifier.width(84.dp)
                                                )
                                            }
                                            Text(
                                                row.correctedElapsedMs?.let(::formatDurationFromMillis) ?: "--",
                                                color = Color.White,
                                                fontSize = 11.sp,
                                                modifier = Modifier.width(90.dp)
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(6.dp))
                                }
                                Spacer(Modifier.height(6.dp))
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        Text("Race results", color = Color.White, fontWeight = FontWeight.Bold)
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF263238))
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Boat", color = Color.White, fontSize = 11.sp, modifier = Modifier.weight(2f))
                                Text("Group", color = Color.White, fontSize = 11.sp, modifier = Modifier.weight(1f))
                                Text("Elapsed", color = Color.White, fontSize = 11.sp, modifier = Modifier.weight(1f))
                                Text("Penalty", color = Color.White, fontSize = 11.sp, modifier = Modifier.weight(1f))
                                Text("Corr.", color = Color.White, fontSize = 11.sp, modifier = Modifier.weight(1f))
                                Text("Points", color = Color.White, fontSize = 11.sp, modifier = Modifier.weight(1f))
                                Text("Pos.", color = Color.White, fontSize = 11.sp, modifier = Modifier.weight(0.7f))
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        if (raceRows.isEmpty()) {
                            Text("No race data.", color = muted)
                        } else {
                            raceRowsByGroup.forEach { (groupName, rowsInGroup) ->
                                Text("Group: $groupName", color = Color(0xFFFFF59D), fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.height(4.dp))
                                val orderedGroup = rowsInGroup.sortedWith(
                                    compareBy<RaceScoreRow>(
                                        { it.racePoints },
                                        { it.correctedElapsedMs ?: Long.MAX_VALUE },
                                        { it.boatName.lowercase(Locale.US) }
                                    )
                                )
                                orderedGroup.forEachIndexed { groupIndex, row ->
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                historySelectedBoatResult = row
                                                mode = "history_result"
                                            }
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(Color(0xFF171717))
                                                .padding(10.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(row.boatName, color = Color.White, fontSize = 11.sp, modifier = Modifier.weight(2f))
                                                Text(groupName, color = Color.White, fontSize = 11.sp, modifier = Modifier.weight(1f))
                                                Text(
                                                    if (row.status.equals("DNF", ignoreCase = true)) "DNF" else row.realElapsedMs?.let(::formatDurationFromMillis) ?: "--",
                                                    color = Color.White,
                                                    fontSize = 11.sp,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                Text(String.format(Locale.US, "%.1f%%", row.penaltyPercent), color = Color.White, fontSize = 11.sp, modifier = Modifier.weight(1f))
                                                Text(
                                                    if (row.status.equals("DNF", ignoreCase = true)) "DNF" else row.correctedElapsedMs?.let(::formatDurationFromMillis) ?: "--",
                                                    color = Color.White,
                                                    fontSize = 11.sp,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                Text(String.format(Locale.US, "%.2f", row.racePoints), color = Color.White, fontSize = 11.sp, modifier = Modifier.weight(1f))
                                                Text((groupIndex + 1).toString(), color = Color.White, fontSize = 11.sp, modifier = Modifier.weight(0.7f))
                                            }
                                        }
                                    }
                                    Spacer(Modifier.height(6.dp))
                                }
                                Spacer(Modifier.height(6.dp))
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        Text("Regatta standings", color = Color.White, fontWeight = FontWeight.Bold)
                        val raceHeader = selectedRaceIds.indices.joinToString(" | ") { "R${it + 1}" }
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF263238))
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Boat", color = Color.White, fontSize = 11.sp, modifier = Modifier.weight(2f))
                                Text("Group", color = Color.White, fontSize = 11.sp, modifier = Modifier.weight(1f))
                                Text(raceHeader, color = Color.White, fontSize = 11.sp, modifier = Modifier.weight(2f))
                                Text("Total", color = Color.White, fontSize = 11.sp, modifier = Modifier.weight(1f))
                                Text("Pos.", color = Color.White, fontSize = 11.sp, modifier = Modifier.weight(0.7f))
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        if (regattaRows.isEmpty()) {
                            Text("No data for regatta standings.", color = muted)
                        } else {
                            regattaRowsByGroup.forEach { (groupName, rowsInGroup) ->
                                Text("Group: $groupName", color = Color(0xFFFFF59D), fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.height(4.dp))
                                val orderedGroup = rowsInGroup.sortedWith(
                                    compareBy<EventScoreRow>(
                                        { it.totalPoints },
                                        { it.boatName.lowercase(Locale.US) }
                                    )
                                )
                                orderedGroup.forEachIndexed { groupIndex, row ->
                                    val raceColumns = row.racePoints.mapIndexed { index, points ->
                                        "R${index + 1}:${points?.let { String.format(Locale.US, "%.2f", it) } ?: "-"}"
                                    }.joinToString(" | ")
                                    Card(modifier = Modifier.fillMaxWidth()) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(Color(0xFF171717))
                                                .padding(10.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(row.boatName, color = Color.White, fontSize = 11.sp, modifier = Modifier.weight(2f))
                                                Text(groupName, color = Color.White, fontSize = 11.sp, modifier = Modifier.weight(1f))
                                                Text(raceColumns, color = Color.White, fontSize = 11.sp, modifier = Modifier.weight(2f))
                                                Text(String.format(Locale.US, "%.2f", row.totalPoints), color = Color.White, fontSize = 11.sp, modifier = Modifier.weight(1f))
                                                Text((groupIndex + 1).toString(), color = Color.White, fontSize = 11.sp, modifier = Modifier.weight(0.7f))
                                            }
                                        }
                                    }
                                    Spacer(Modifier.height(6.dp))
                                }
                                Spacer(Modifier.height(6.dp))
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = {
                                val combinedCsv = runCatching {
                                    buildCombinedResultsCsv(
                                        regattaName = selectedEvent.name,
                                        regattaStartDate = selectedEvent.startDate,
                                        regattaEndDate = selectedEvent.endDate,
                                        raceName = selectedRace.name,
                                        raceDate = selectedRace.raceDate,
                                        raceStartTime = selectedRace.startTime,
                                        raceEndTime = selectedRace.endTime,
                                        raceRowsByGroup = raceRowsByGroup,
                                        regattaRowsByGroup = regattaRowsByGroup,
                                        selectedRaceIds = selectedRaceIds
                                    )
                                }.getOrElse {
                                    buildCombinedResultsCsvFallback(
                                        regattaName = selectedEvent.name,
                                        regattaStartDate = selectedEvent.startDate,
                                        regattaEndDate = selectedEvent.endDate,
                                        raceName = selectedRace.name,
                                        raceDate = selectedRace.raceDate,
                                        raceStartTime = selectedRace.startTime,
                                        raceEndTime = selectedRace.endTime,
                                        raceRowsByGroup = raceRowsByGroup,
                                        regattaRowsByGroup = regattaRowsByGroup,
                                        selectedRaceIds = selectedRaceIds
                                    )
                                }
                                val file = writeCsvFile(
                                    context = context,
                                    fileName = "results_${sanitizeFileName(selectedEvent.name)}_${sanitizeFileName(selectedRace.name)}.csv",
                                    csvContent = combinedCsv
                                )
                                runCatching {
                                    shareCsvFile(context, file, "Share results CSV")
                                }.onFailure {
                                    errorMessage = "CSV export failed."
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = actionGreen)
                        ) { Text("Share CSV (all tables)") }
                    }
                }
            }

            if (mode == "history_result") {
                val row = historySelectedBoatResult
                val selectedRace = historySelectedRace
                RegattaSection(title = "Result details", cardColor = cardColor) {
                    if (row == null) {
                        Text("No result selected.", color = muted)
                    } else {
                        Text(row.boatName, color = Color.White, fontWeight = FontWeight.Bold)
                        Text("Race: ${selectedRace?.name ?: "--"}", color = Color(0xFFCFD8DC), fontSize = 12.sp)
                        Spacer(Modifier.height(8.dp))
                        Text("Skipper: ${row.skipperName.ifBlank { "--" }}", color = Color.White, fontSize = 13.sp)
                        Text("Group: ${row.groupCode.ifBlank { "--" }}", color = Color.White, fontSize = 13.sp)
                        Text("Status: ${row.status}", color = Color.White, fontSize = 13.sp)
                        Text("Completed gates: ${row.completedGates}", color = Color.White, fontSize = 13.sp)
                        Text(
                            "Real time: ${if (row.status.equals("DNF", ignoreCase = true)) "DNF" else row.realElapsedMs?.let(::formatDurationFromMillis) ?: "--"}",
                            color = Color.White,
                            fontSize = 13.sp
                        )
                        Text(
                            "Corrected time: ${if (row.status.equals("DNF", ignoreCase = true)) "DNF" else row.correctedElapsedMs?.let(::formatDurationFromMillis) ?: "--"}",
                            color = Color.White,
                            fontSize = 13.sp
                        )
                        Text(
                            "Penalty: ${String.format(Locale.US, "%.1f%%", row.penaltyPercent)}",
                            color = Color.White,
                            fontSize = 13.sp
                        )
                        Text(
                            "Race points: ${String.format(Locale.US, "%.2f", row.racePoints)}",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(10.dp))
                        Button(
                            onClick = { mode = "history_race" },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF424242))
                        ) { Text("Back to results") }
                    }
                }
            }

            if (mode == "history_race_map") {
                val selectedRace = historySelectedRace
                val raceLive = historySelectedRaceLive
                val participants = raceLive?.participants.orEmpty()
                val selectedBoatId = historyMapSelectedBoatId.ifBlank { participants.firstOrNull()?.boatId.orEmpty() }
                if (selectedBoatId.isNotBlank() && selectedBoatId != historyMapSelectedBoatId) {
                    historyMapSelectedBoatId = selectedBoatId
                }
                LaunchedEffect(mode, selectedRace?.id, selectedBoatId) {
                    if (selectedRace == null || selectedBoatId.isBlank()) {
                        historyMapBoatTrack = emptyList()
                        return@LaunchedEffect
                    }
                    while (mode == "history_race_map") {
                        when (val trackResult = withContext(Dispatchers.IO) {
                            apiClient.getBoatTrack(selectedRace.id, selectedBoatId)
                        }) {
                            is NasCallResult.Ok -> historyMapBoatTrack = trackResult.value
                            is NasCallResult.Err -> {
                                historyMapBoatTrack = emptyList()
                                errorMessage = trackResult.message
                            }
                        }
                        delay(5_000L)
                    }
                }
                RegattaSection(
                    title = "Map",
                    cardColor = cardColor,
                    modifier = Modifier.weight(1f, fill = true),
                    expandContentVertically = true
                ) {
                    if (selectedRace == null || raceLive == null) {
                        Text("Map data not available.", color = muted)
                    } else {
                        // MapView draws in a native layer and can paint over earlier Compose siblings.
                        // Draw the map first, then overlay controls with an opaque strip on top.
                        Box(modifier = Modifier.fillMaxSize()) {
                            HistoryResultsMapView(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clipToBounds(),
                                selectedRaceLive = raceLive,
                                participants = participants,
                                selectedBoatId = selectedBoatId,
                                selectedBoatTrack = historyMapBoatTrack,
                                boatsById = historySelectedEvent?.boats.orEmpty().associateBy { it.id }
                            )
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.TopStart)
                                    .background(cardColor)
                                    .zIndex(1f)
                            ) {
                                Button(
                                    onClick = { mode = "history_race" },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF424242))
                                ) { Text("Back to results") }
                                Spacer(Modifier.height(8.dp))
                                Box(modifier = Modifier.fillMaxWidth()) {
                                    OutlinedButton(
                                        onClick = { historyMapBoatMenuExpanded = true },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        val selectedLabel = participants.firstOrNull { it.boatId == selectedBoatId }?.boatName
                                            ?: "Select boat"
                                        Text(selectedLabel)
                                    }
                                    DropdownMenu(
                                        expanded = historyMapBoatMenuExpanded,
                                        onDismissRequest = { historyMapBoatMenuExpanded = false }
                                    ) {
                                        participants.forEach { participant ->
                                            val hasRecentSync = participant.lastSignalEpochMs?.let { lastSignal ->
                                                (System.currentTimeMillis() - lastSignal) <= 90_000L
                                            } == true
                                            val statusLabel = if (hasRecentSync) "Sync ON" else "Sync OFF"
                                            val statusColor = if (hasRecentSync) Color(0xFF43A047) else Color(0xFFE53935)
                                            DropdownMenuItem(
                                                text = {
                                                    Row(
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text("●", color = statusColor, fontSize = 12.sp)
                                                        Text("${participant.boatName} · $statusLabel")
                                                    }
                                                },
                                                onClick = {
                                                    historyMapSelectedBoatId = participant.boatId
                                                    historyMapBoatMenuExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                            }
                        }
                    }
                }
            }

            if (false && mode == "new") {
                RegattaSection(title = "Create Regatta", cardColor = cardColor) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { mode = "organizer" },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF424242))
                        ) {
                            Text("Cancel create")
                        }
                        Button(
                            onClick = { mode = "home" },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF616161))
                        ) {
                            Text("Back to home")
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = organizerName,
                        onValueChange = { organizerName = it },
                        label = { Text("Organizer name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = eventName,
                        onValueChange = { eventName = it },
                        label = { Text("Regatta name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = joinCode,
                        onValueChange = { joinCode = it.uppercase(Locale.US) },
                        label = { Text("Join code") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = organizerCode,
                        onValueChange = { organizerCode = it.uppercase(Locale.US) },
                        label = { Text("Organizer admin code") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = regattaStartDate,
                        onValueChange = { regattaStartDate = it },
                        label = { Text("Start date (YYYY-MM-DD)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = regattaEndDate,
                        onValueChange = { regattaEndDate = it },
                        label = { Text("End date (YYYY-MM-DD)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = maxBoatsInput,
                        onValueChange = { maxBoatsInput = it.filter(Char::isDigit) },
                        label = { Text("Max boats") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Public results",
                        color = Color.White
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { isPublicEvent = false },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (!isPublicEvent) actionBlue else Color(0xFF424242)
                            )
                        ) {
                            Text("Ne")
                        }
                        Button(
                            onClick = { isPublicEvent = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isPublicEvent) actionBlue else Color(0xFF424242)
                            )
                        ) {
                            Text("Da")
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(
                        enabled = !isBusy,
                        onClick = {
                            isBusy = true
                            errorMessage = null
                            statusMessage = null
                            scope.launch {
                                prefs.saveOrganizerName(organizerName)
                                when (
                                    val result = withContext(Dispatchers.IO) {
                                        apiClient.createEvent(
                                            RegattaDraft(
                                                name = eventName,
                                                joinCode = joinCode,
                                                organizerName = organizerName,
                                                organizerCode = organizerCode,
                                                startDate = regattaStartDate,
                                                endDate = regattaEndDate,
                                                isPublic = isPublicEvent,
                                                maxBoats = maxBoatsInput.toIntOrNull()?.coerceAtLeast(1) ?: 50
                                            )
                                        )
                                    }
                                ) {
                                    is NasCallResult.Ok -> {
                                        joinedEventId = result.value.eventId
                                        joinedRaceId = result.value.raceId.orEmpty()
                                        joinedBoatId = ""
                                        organizerToken = result.value.organizerToken.orEmpty()
                                        val organizerAccessValue = result.value.organizerCode.orEmpty()
                                        if (organizerAccessValue.isNotBlank()) {
                                            quickAccessInput = organizerAccessValue
                                            organizerCode = organizerAccessValue
                                            prefs.saveLastOrganizerAccessValue(organizerAccessValue)
                                        }
                                        prefs.saveOrganizerSession(joinedEventId, organizerToken)
                                        organizerEventIds = prefs.organizerEventIds
                                        revealOrganizerCode = result.value.organizerCode.orEmpty()
                                        prefs.saveJoinedEvent(joinedEventId, joinedRaceId, joinedBoatId)
                                        statusMessage = "Regatta created. Day 1 / Race 1 is ready."
                                        mode = "organizer"
                                    }
                                    is NasCallResult.Err -> errorMessage = result.message
                                }
                                isBusy = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = actionGreen)
                    ) {
                        Text(if (isBusy) "Creating..." else "Create")
                    }
                    if (revealOrganizerCode.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Organizer code: $revealOrganizerCode",
                            color = Color(0xFFFFF59D),
                            fontSize = 12.sp
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = {
                                val clipboard = context.getSystemService(ClipboardManager::class.java)
                                clipboard?.setPrimaryClip(
                                    ClipData.newPlainText("Organizer code", revealOrganizerCode)
                                )
                                statusMessage = "Organizer code copied."
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF424242))
                        ) {
                            Text("Copy Organizer Code")
                        }
                    }
                }
            }

            if (mode == "organizer" || mode == "organizer_editor") {
                RegattaSection(title = "", cardColor = cardColor) {
                    if (mode == "organizer" && !organizerCreateFlow) {
                        Text("Enter your organizer hash to log in.", color = muted)
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = organizerLoginHash,
                            onValueChange = {
                                val normalized = it.uppercase(Locale.US)
                                organizerLoginHash = normalized
                                prefs.saveLastOrganizerAccessValue(normalized)
                            },
                            label = { Text("Organizer hash") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(
                            enabled = !isBusy,
                            onClick = {
                                val organizerHashInput = organizerLoginHash.trim().uppercase(Locale.US)
                                if (organizerHashInput.isBlank()) {
                                    organizerLoginAskedCreateNew = true
                                    return@Button
                                }
                                isBusy = true
                                errorMessage = null
                                statusMessage = null
                                scope.launch {
                                    val mappedEventId = prefs.eventIdForOrganizerToken(organizerHashInput)
                                    if (!mappedEventId.isNullOrBlank()) {
                                        joinedEventId = mappedEventId
                                        organizerToken = organizerHashInput
                                        val snapshotResult = withContext(Dispatchers.IO) {
                                            apiClient.getEventSnapshot(mappedEventId)
                                        }
                                        if (snapshotResult is NasCallResult.Ok) {
                                            eventSnapshot = snapshotResult.value
                                            joinedRaceId = snapshotResult.value.activeRaceId.orEmpty()
                                            eventName = snapshotResult.value.name
                                            organizerName = snapshotResult.value.organizerName
                                            joinCode = snapshotResult.value.joinCode
                                            regattaStartDate = snapshotResult.value.startDate.orEmpty()
                                            regattaEndDate = snapshotResult.value.endDate.orEmpty()
                                            maxBoatsInput = snapshotResult.value.maxBoats.toString()
                                            isPublicEvent = snapshotResult.value.isPublic
                                        }
                                        prefs.saveJoinedEvent(joinedEventId, joinedRaceId, joinedBoatId)
                                        statusMessage = "Organizer access restored."
                                        mode = "organizer_editor"
                                        organizerCreateFlow = false
                                    } else {
                                        when (val authResult = withContext(Dispatchers.IO) {
                                            apiClient.authenticateOrganizer(
                                                organizerCode = organizerHashInput
                                            )
                                        }) {
                                            is NasCallResult.Ok -> {
                                                joinedEventId = authResult.value.eventId
                                                organizerToken = authResult.value.organizerToken
                                                organizerCode = organizerHashInput
                                                prefs.saveLastOrganizerAccessValue(organizerHashInput)
                                                prefs.saveOrganizerSession(joinedEventId, organizerToken)
                                                organizerEventIds = prefs.organizerEventIds
                                                when (val snapshotResult = withContext(Dispatchers.IO) {
                                                    apiClient.getEventSnapshot(joinedEventId)
                                                }) {
                                                    is NasCallResult.Ok -> {
                                                        eventSnapshot = snapshotResult.value
                                                        eventName = snapshotResult.value.name
                                                        organizerName = snapshotResult.value.organizerName
                                                        joinCode = snapshotResult.value.joinCode
                                                        regattaStartDate = snapshotResult.value.startDate.orEmpty()
                                                        regattaEndDate = snapshotResult.value.endDate.orEmpty()
                                                        maxBoatsInput = snapshotResult.value.maxBoats.toString()
                                                        isPublicEvent = snapshotResult.value.isPublic
                                                        val activeRace = snapshotResult.value.races.firstOrNull {
                                                            it.id == snapshotResult.value.activeRaceId
                                                        } ?: snapshotResult.value.races.firstOrNull()
                                                        organizerRaceDateInput = activeRace?.raceDate.orEmpty()
                                                        organizerRaceStartTimeInput = activeRace?.startTime ?: "10:00"
                                                        organizerRaceEndTimeInput = activeRace?.endTime ?: "16:00"
                                                        organizerRaceLengthNmInput = activeRace?.raceLengthNm
                                                            ?.takeIf { it > 0.0 }
                                                            ?.let { String.format(Locale.US, "%.2f", it) }
                                                            .orEmpty()
                                                        startTimeInput = activeRace?.countdownTargetEpochMs
                                                            ?.let(::formatEpochInput)
                                                            .orEmpty()
                                                        organizerGateSelection = "start"
                                                        mode = "organizer_editor"
                                                        organizerCreateFlow = false
                                                    }
                                                    is NasCallResult.Err -> errorMessage = snapshotResult.message
                                                }
                                            }
                                            is NasCallResult.Err -> {
                                                organizerLoginAskedCreateNew = true
                                            }
                                        }
                                    }
                                    isBusy = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = actionOrange)
                        ) {
                            Text(if (isBusy) "Logging in..." else "Login")
                        }
                    } else {
                        val event = eventSnapshot
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Regatta Editor", color = Color.White, fontWeight = FontWeight.Bold)
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(
                                    onClick = {
                                        mode = "home"
                                        errorMessage = null
                                        statusMessage = null
                                    },
                                    modifier = Modifier.height(28.dp),
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                                ) {
                                    Text("Home", fontSize = 11.sp)
                                }
                                TextButton(
                                    enabled = !isBusy && joinedEventId.isNotBlank(),
                                    onClick = {
                                        isBusy = true
                                        errorMessage = null
                                        statusMessage = null
                                        scope.launch {
                                            when (val snapshotResult = withContext(Dispatchers.IO) {
                                                apiClient.getEventSnapshot(joinedEventId)
                                            }) {
                                                is NasCallResult.Ok -> {
                                                    eventSnapshot = snapshotResult.value
                                                    if (joinedRaceId.isBlank()) {
                                                        joinedRaceId = snapshotResult.value.activeRaceId.orEmpty()
                                                        prefs.saveJoinedEvent(joinedEventId, joinedRaceId, joinedBoatId)
                                                    }
                                                    if (joinedRaceId.isNotBlank()) {
                                                        when (val liveResult = withContext(Dispatchers.IO) {
                                                            apiClient.getLiveSnapshot(joinedRaceId)
                                                        }) {
                                                            is NasCallResult.Ok -> liveSnapshot = liveResult.value
                                                            is NasCallResult.Err -> {
                                                                liveSnapshot = null
                                                                errorMessage = liveResult.message
                                                            }
                                                        }
                                                    } else {
                                                        liveSnapshot = null
                                                    }
                                                    statusMessage = "Organizer data refreshed."
                                                }
                                                is NasCallResult.Err -> errorMessage = snapshotResult.message
                                            }
                                            isBusy = false
                                        }
                                    },
                                    modifier = Modifier.height(28.dp),
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                                ) {
                                    Text(if (isBusy) "..." else "Refresh", fontSize = 11.sp)
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = eventName,
                            onValueChange = { eventName = it },
                            label = { Text("Regatta name") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = joinCode,
                                onValueChange = { joinCode = it.uppercase(Locale.US) },
                                label = { Text("Join code") },
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = organizerCode,
                                onValueChange = { organizerCode = it.uppercase(Locale.US) },
                                label = { Text("Organizer admin code") },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(modifier = Modifier.weight(1f)) {
                                OutlinedTextField(
                                    value = regattaStartDate,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Start date") },
                                    trailingIcon = { Text("📅") },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Box(
                                    modifier = Modifier
                                        .matchParentSize()
                                        .clickable {
                                            val current = runCatching {
                                                val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                                                    isLenient = false
                                                }.parse(regattaStartDate.trim())
                                                Calendar.getInstance().apply { time = parsed ?: Date() }
                                            }.getOrElse { Calendar.getInstance() }
                                            DatePickerDialog(
                                                context,
                                                { _, year, month, dayOfMonth ->
                                                    regattaStartDate = String.format(
                                                        Locale.US,
                                                        "%04d-%02d-%02d",
                                                        year,
                                                        month + 1,
                                                        dayOfMonth
                                                    )
                                                },
                                                current.get(Calendar.YEAR),
                                                current.get(Calendar.MONTH),
                                                current.get(Calendar.DAY_OF_MONTH)
                                            ).show()
                                        }
                                )
                            }
                            Box(modifier = Modifier.weight(1f)) {
                                OutlinedTextField(
                                    value = regattaEndDate,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("End date") },
                                    trailingIcon = { Text("📅") },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Box(
                                    modifier = Modifier
                                        .matchParentSize()
                                        .clickable {
                                            val current = runCatching {
                                                val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                                                    isLenient = false
                                                }.parse(regattaEndDate.trim())
                                                Calendar.getInstance().apply { time = parsed ?: Date() }
                                            }.getOrElse { Calendar.getInstance() }
                                            DatePickerDialog(
                                                context,
                                                { _, year, month, dayOfMonth ->
                                                    regattaEndDate = String.format(
                                                        Locale.US,
                                                        "%04d-%02d-%02d",
                                                        year,
                                                        month + 1,
                                                        dayOfMonth
                                                    )
                                                },
                                                current.get(Calendar.YEAR),
                                                current.get(Calendar.MONTH),
                                                current.get(Calendar.DAY_OF_MONTH)
                                            ).show()
                                        }
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = maxBoatsInput,
                                onValueChange = { maxBoatsInput = it.filter(Char::isDigit) },
                                label = { Text("Max boats (optional)") },
                                modifier = Modifier.weight(1f),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("Public result", color = Color.White)
                        Spacer(Modifier.height(6.dp))
                        Box {
                            OutlinedTextField(
                                value = if (isPublicEvent) "Yes" else "No",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Public result") },
                                trailingIcon = { Text("▼", color = muted) },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .pointerInput(organizerPublicResultMenuExpanded) {
                                        detectTapGestures(onTap = { organizerPublicResultMenuExpanded = true })
                                    }
                            )
                            DropdownMenu(
                                expanded = organizerPublicResultMenuExpanded,
                                onDismissRequest = { organizerPublicResultMenuExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("No") },
                                    onClick = {
                                        isPublicEvent = false
                                        organizerPublicResultMenuExpanded = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Yes") },
                                    onClick = {
                                        isPublicEvent = true
                                        organizerPublicResultMenuExpanded = false
                                    }
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Button(
                            enabled = !isBusy,
                            onClick = {
                                if (eventName.isBlank()) {
                                    errorMessage = "Regatta name is required."
                                    return@Button
                                }
                                if (organizerCode.isBlank()) {
                                    errorMessage = "Organizer admin code is required."
                                    return@Button
                                }
                                if (joinCode.isBlank()) {
                                    errorMessage = "Join code is required."
                                    return@Button
                                }
                                isBusy = true
                                errorMessage = null
                                scope.launch {
                                    val draft = RegattaDraft(
                                        name = eventName,
                                        joinCode = joinCode,
                                        organizerName = organizerName.ifBlank { "Organizer" },
                                        organizerCode = organizerCode,
                                        startDate = regattaStartDate,
                                        endDate = regattaEndDate,
                                        isPublic = isPublicEvent,
                                        maxBoats = maxBoatsInput.toIntOrNull()?.coerceAtLeast(1) ?: 50
                                    )
                                    if (joinedEventId.isNotBlank() && organizerToken.isNotBlank()) {
                                        when (val updateResult = withContext(Dispatchers.IO) {
                                            apiClient.updateEvent(
                                                eventId = joinedEventId,
                                                organizerToken = organizerToken,
                                                draft = draft
                                            )
                                        }) {
                                            is NasCallResult.Ok -> statusMessage = "Regatta saved."
                                            is NasCallResult.Err -> errorMessage = updateResult.message
                                        }
                                    } else {
                                        when (val createResult = withContext(Dispatchers.IO) {
                                            apiClient.createEvent(draft)
                                        }) {
                                            is NasCallResult.Ok -> {
                                                joinedEventId = createResult.value.eventId
                                                joinedRaceId = createResult.value.raceId.orEmpty()
                                                joinedBoatId = ""
                                                organizerToken = createResult.value.organizerToken.orEmpty()
                                                val organizerAccessValue = createResult.value.organizerCode.orEmpty()
                                                if (organizerAccessValue.isNotBlank()) {
                                                    quickAccessInput = organizerAccessValue
                                                    organizerLoginHash = organizerAccessValue
                                                    organizerCode = organizerAccessValue
                                                    prefs.saveLastOrganizerAccessValue(organizerAccessValue)
                                                }
                                                prefs.saveOrganizerSession(joinedEventId, organizerToken)
                                                organizerEventIds = prefs.organizerEventIds
                                                revealOrganizerCode = createResult.value.organizerCode.orEmpty()
                                                prefs.saveJoinedEvent(joinedEventId, joinedRaceId, joinedBoatId)
                                                organizerCreateFlow = false
                                                statusMessage = "Regatta created and saved."
                                            }
                                            is NasCallResult.Err -> errorMessage = createResult.message
                                        }
                                    }
                                    if (joinedEventId.isNotBlank()) {
                                        when (val snapshotResult = withContext(Dispatchers.IO) {
                                            apiClient.getEventSnapshot(joinedEventId)
                                        }) {
                                            is NasCallResult.Ok -> eventSnapshot = snapshotResult.value
                                            is NasCallResult.Err -> errorMessage = snapshotResult.message
                                        }
                                    }
                                    isBusy = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = actionGreen)
                        ) {
                            Text(if (isBusy) "Saving..." else "Save")
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    if (joinedEventId.isBlank()) {
                                        errorMessage = "Organizer session missing."
                                        return@Button
                                    }
                                    errorMessage = null
                                    statusMessage = null
                                    mode = "organizer_boat_list"
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF424242)),
                                modifier = Modifier.weight(1f)
                            ) { Text("Boat list") }
                            Button(
                                onClick = { mode = "organizer_races" },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF424242)),
                                modifier = Modifier.weight(1f)
                            ) { Text("Race list") }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { mode = "organizer_results" },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF424242)),
                                modifier = Modifier.weight(1f)
                            ) { Text("Results") }
                            Button(
                                onClick = {
                                    noticeListReturnMode = "organizer"
                                    mode = "organizer_notices"
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF424242)),
                                modifier = Modifier.weight(1f)
                            ) { Text("Notice list") }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }

            if (mode == "join") {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(cardColor)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Boat data",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    OutlinedTextField(
                        value = boatName,
                        onValueChange = {
                            boatName = it
                            persistJoinBoatDetails()
                        },
                        label = { Text("Boat name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = skipperName,
                        onValueChange = {
                            skipperName = it
                            persistJoinBoatDetails()
                        },
                        label = { Text("Skipper name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = clubName,
                        onValueChange = {
                            clubName = it
                            persistJoinBoatDetails()
                        },
                        label = { Text("Club name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = lengthValue,
                            onValueChange = {
                                lengthValue = it
                                persistJoinBoatDetails()
                            },
                            label = { Text("Length") },
                            modifier = Modifier.weight(1f)
                        )
                        ExposedDropdownMenuBox(
                            expanded = lengthUnitMenuExpanded,
                            onExpandedChange = { lengthUnitMenuExpanded = !lengthUnitMenuExpanded },
                            modifier = Modifier.weight(1f)
                        ) {
                            OutlinedTextField(
                                value = if (lengthUnit.equals("ft", ignoreCase = true)) "Feet" else "Meters",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Unit") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = lengthUnitMenuExpanded) },
                                modifier = Modifier
                                    .menuAnchor()
                                    .fillMaxWidth()
                            )
                            DropdownMenu(
                                expanded = lengthUnitMenuExpanded,
                                onDismissRequest = { lengthUnitMenuExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Meters") },
                                    onClick = {
                                        lengthUnit = "m"
                                        persistJoinBoatDetails()
                                        lengthUnitMenuExpanded = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Feet") },
                                    onClick = {
                                        lengthUnit = "ft"
                                        persistJoinBoatDetails()
                                        lengthUnitMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            enabled = !isBusy,
                            onClick = {
                                isBusy = true
                                errorMessage = null
                                statusMessage = null
                                scope.launch {
                                    when (
                                        val result = withContext(Dispatchers.IO) {
                                            apiClient.joinEvent(
                                                joinCode = joinCode,
                                                draft = BoatRegistrationDraft(
                                                    boatName = boatName,
                                                    skipperName = skipperName,
                                                    clubName = clubName,
                                                    lengthValue = lengthValue,
                                                    lengthUnit = lengthUnit,
                                                    groupCode = ""
                                                ),
                                                deviceId = prefs.deviceId
                                            )
                                        }
                                    ) {
                                        is NasCallResult.Ok -> {
                                            joinedEventId = result.value.eventId
                                            joinedRaceId = result.value.raceId.orEmpty()
                                            joinedBoatId = result.value.boatId.orEmpty()
                                            prefs.saveLastJoinCode(joinCode)
                                            prefs.saveJoinedEvent(joinedEventId, joinedRaceId, joinedBoatId)
                                            val boatCodeMessage = result.value.boatCode?.let { " Boat code: $it." }.orEmpty()
                                            statusMessage = "Data saved.$boatCodeMessage"
                                        }
                                        is NasCallResult.Err -> {
                                            if (result.message.contains("REGATTA_FULL", ignoreCase = true)) {
                                                backupTakeoverAvailable = false
                                                errorMessage = "Regatta is full. Cannot register a new boat."
                                            } else if (
                                                result.message.contains("409") &&
                                                result.message.contains("already active", ignoreCase = true)
                                            ) {
                                                resolveAlreadyActiveJoinConflict(
                                                    openRaceStartLine = false,
                                                    apiErrorMessage = result.message
                                                )
                                            } else if (result.message.contains("404")) {
                                                backupTakeoverAvailable = false
                                                handleJoinRegattaNotFound()
                                            } else {
                                                backupTakeoverAvailable = false
                                                errorMessage = result.message
                                            }
                                        }
                                    }
                                    isBusy = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = actionGreen),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (isBusy) "Saving..." else "Save")
                        }
                        Button(
                            enabled = !isBusy,
                            onClick = {
                                isBusy = true
                                errorMessage = null
                                statusMessage = null
                                scope.launch {
                                    when (
                                        val result = withContext(Dispatchers.IO) {
                                            apiClient.joinEvent(
                                                joinCode = joinCode,
                                                draft = BoatRegistrationDraft(
                                                    boatName = boatName,
                                                    skipperName = skipperName,
                                                    clubName = clubName,
                                                    lengthValue = lengthValue,
                                                    lengthUnit = lengthUnit,
                                                    groupCode = ""
                                                ),
                                                deviceId = prefs.deviceId
                                            )
                                        }
                                    ) {
                                        is NasCallResult.Ok -> {
                                            joinedEventId = result.value.eventId
                                            joinedRaceId = result.value.raceId.orEmpty()
                                            joinedBoatId = result.value.boatId.orEmpty()
                                            prefs.saveLastJoinCode(joinCode)
                                            prefs.saveJoinedEvent(joinedEventId, joinedRaceId, joinedBoatId)
                                            val boatCodeMessage = result.value.boatCode?.let { " Boat code: $it." }.orEmpty()
                                            statusMessage = "Data saved.$boatCodeMessage Opening Race Start Line."
                                            onOpenRaceStartLine()
                                        }
                                        is NasCallResult.Err -> {
                                            if (result.message.contains("REGATTA_FULL", ignoreCase = true)) {
                                                backupTakeoverAvailable = false
                                                errorMessage = "Regatta is full. Cannot register a new boat."
                                            } else if (
                                                result.message.contains("409") &&
                                                result.message.contains("already active", ignoreCase = true)
                                            ) {
                                                resolveAlreadyActiveJoinConflict(
                                                    openRaceStartLine = true,
                                                    apiErrorMessage = result.message
                                                )
                                            } else if (result.message.contains("404")) {
                                                backupTakeoverAvailable = false
                                                handleJoinRegattaNotFound()
                                            } else {
                                                backupTakeoverAvailable = false
                                                errorMessage = result.message
                                            }
                                        }
                                    }
                                    isBusy = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = actionBlue),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (isBusy) "Saving..." else "Go to race")
                        }
                    }
                    if (backupTakeoverAvailable) {
                        Spacer(Modifier.height(8.dp))
                        Button(
                            enabled = !isBusy,
                            onClick = { attemptBackupTakeover(openRaceStartLine = false) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = actionOrange)
                        ) {
                            Text(if (isBusy) "Working..." else "Backup takeover")
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Use this only if the primary phone stopped working and this backup phone must continue the same boat session.",
                            color = muted,
                            fontSize = 11.sp
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    val joinEvent = eventSnapshot
                    val joinNotices = joinEvent?.noticePosts.orEmpty()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Notice board", color = Color.White, fontWeight = FontWeight.SemiBold)
                        TextButton(
                            enabled = !isBusy,
                            onClick = {
                                val normalizedJoinCode = joinCode.trim().uppercase(Locale.US)
                                if (normalizedJoinCode.isBlank()) {
                                    errorMessage = "Enter join code first."
                                    return@TextButton
                                }
                                isBusy = true
                                errorMessage = null
                                scope.launch {
                                    when (val lookupResult = withContext(Dispatchers.IO) {
                                        apiClient.getEventByJoinCode(normalizedJoinCode)
                                    }) {
                                        is NasCallResult.Ok -> {
                                            when (val snapshotResult = withContext(Dispatchers.IO) {
                                                apiClient.getEventSnapshot(lookupResult.value.eventId)
                                            }) {
                                                is NasCallResult.Ok -> {
                                                    eventSnapshot = snapshotResult.value
                                                    joinSnapshotLoadedForCode = normalizedJoinCode
                                                    statusMessage = "Notice board refreshed."
                                                }
                                                is NasCallResult.Err -> errorMessage = snapshotResult.message
                                            }
                                        }
                                        is NasCallResult.Err -> errorMessage = lookupResult.message
                                    }
                                    isBusy = false
                                }
                            }
                        ) {
                            Text(if (isBusy) "..." else "Refresh", fontSize = 12.sp)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    if (joinNotices.isEmpty()) {
                        Text("No notices from organizer.", color = muted, fontSize = 12.sp)
                    } else {
                        Text("Notices (${joinNotices.size})", color = Color(0xFFCFD8DC), fontSize = 12.sp)
                        Spacer(Modifier.height(6.dp))
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            joinNotices.forEach { post ->
                                Card(modifier = Modifier.fillMaxWidth()) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color(0xFF171717))
                                            .padding(8.dp)
                                    ) {
                                        Text(post.noticeText.ifBlank { "--" }, color = Color.White, fontSize = 12.sp)
                                        Text(
                                            formatIsoDateTime(post.publishedAt),
                                            color = Color(0xFFB0BEC5),
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            }

            if (mode == "organizer_boat_list") {
                LaunchedEffect(mode, joinedEventId) {
                    if (joinedEventId.isBlank()) return@LaunchedEffect
                    when (val snapshotResult = withContext(Dispatchers.IO) {
                        apiClient.getEventSnapshot(joinedEventId)
                    }) {
                        is NasCallResult.Ok -> eventSnapshot = snapshotResult.value
                        is NasCallResult.Err -> errorMessage = snapshotResult.message
                    }
                }
                val event = eventSnapshot
                val eventBoats = event?.boats.orEmpty()
                val raceIdForPenalty = event?.activeRaceId
                    ?.takeIf { it.isNotBlank() }
                    ?: event?.races?.firstOrNull()?.id.orEmpty()
                LaunchedEffect(eventBoats) {
                    eventBoats.forEach { boat ->
                        if (!groupInputs.containsKey(boat.id)) {
                            groupInputs[boat.id] = boat.groupCode.orEmpty()
                        }
                        if (!penaltyValueInputs.containsKey(boat.id)) {
                            penaltyValueInputs[boat.id] = ""
                        }
                    }
                }
                RegattaSection(title = "", cardColor = cardColor) {
                    if (eventBoats.isEmpty()) {
                        Text("No boats.", color = muted)
                    } else {
                        eventBoats.forEach { boat ->
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFF171717))
                                        .padding(8.dp)
                                ) {
                                    Text(boat.boatName, color = Color.White, fontWeight = FontWeight.Bold)
                                    val lengthMetersValue = when {
                                        boat.lengthValue == null -> null
                                        boat.lengthUnit.equals("ft", ignoreCase = true) -> boat.lengthValue * 0.3048
                                        else -> boat.lengthValue
                                    }
                                    val lengthFeetValue = lengthMetersValue?.let { meters -> meters / 0.3048 }
                                    val lengthMetersText = lengthMetersValue?.let { String.format(Locale.US, "%.2f", it) } ?: "--"
                                    val lengthFeetText = lengthFeetValue?.let { String.format(Locale.US, "%.2f", it) } ?: "--"
                                    Text("Boat length: $lengthMetersText m, $lengthFeetText ft", color = Color(0xFFCFD8DC), fontSize = 12.sp)
                                    Text("Skipper: ${boat.skipperName.ifBlank { "--" }}", color = Color(0xFFCFD8DC), fontSize = 12.sp)
                                    Text("Club: ${boat.clubName.ifBlank { "--" }}", color = Color(0xFFCFD8DC), fontSize = 12.sp)
                                    Spacer(Modifier.height(4.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedTextField(
                                            value = groupInputs[boat.id].orEmpty(),
                                            onValueChange = { groupInputs[boat.id] = it },
                                            label = { Text("Group") },
                                            modifier = Modifier.weight(1f)
                                        )
                                        OutlinedTextField(
                                            value = penaltyValueInputs[boat.id].orEmpty(),
                                            onValueChange = { penaltyValueInputs[boat.id] = it },
                                            label = { Text("Penality (%)") },
                                            modifier = Modifier.weight(1f),
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                        )
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(
                                            enabled = !isBusy,
                                            onClick = {
                                                if (joinedEventId.isBlank() || organizerToken.isBlank()) {
                                                    errorMessage = "Organizer session missing."
                                                    return@Button
                                                }
                                                val penaltyText = penaltyValueInputs[boat.id].orEmpty().trim()
                                                val penaltyValue = if (penaltyText.isBlank()) {
                                                    null
                                                } else {
                                                    penaltyText.toDoubleOrNull()
                                                }
                                                if (penaltyText.isNotBlank() && penaltyValue == null) {
                                                    errorMessage = "Penality must be a number."
                                                    return@Button
                                                }
                                                isBusy = true
                                                errorMessage = null
                                                statusMessage = null
                                                scope.launch {
                                                    when (val updateResult = withContext(Dispatchers.IO) {
                                                        apiClient.updateBoatGroup(
                                                            eventId = joinedEventId,
                                                            boatId = boat.id,
                                                            organizerToken = organizerToken,
                                                            groupCode = groupInputs[boat.id].orEmpty()
                                                        )
                                                    }) {
                                                        is NasCallResult.Ok -> {
                                                            if (penaltyValue != null) {
                                                                if (raceIdForPenalty.isBlank()) {
                                                                    errorMessage = "No active race for penality."
                                                                } else {
                                                                when (val penaltyResult = withContext(Dispatchers.IO) {
                                                                    apiClient.createPenalty(
                                                                        raceId = raceIdForPenalty,
                                                                        boatId = boat.id,
                                                                        organizerToken = organizerToken,
                                                                        type = "percent",
                                                                        value = penaltyValue,
                                                                        reason = "Manual from boat list"
                                                                    )
                                                                }) {
                                                                    is NasCallResult.Ok<*> -> Unit
                                                                    is NasCallResult.Err -> errorMessage = penaltyResult.message
                                                                }
                                                                }
                                                            }
                                                            when (val snapshotResult = withContext(Dispatchers.IO) {
                                                                apiClient.getEventSnapshot(joinedEventId)
                                                            }) {
                                                                is NasCallResult.Ok -> {
                                                                    eventSnapshot = snapshotResult.value
                                                                    statusMessage = if (penaltyValue != null) {
                                                                        "Boat updated. Group and penality saved."
                                                                    } else {
                                                                        "Boat updated. Group saved."
                                                                    }
                                                                }
                                                                is NasCallResult.Err -> errorMessage = snapshotResult.message
                                                            }
                                                        }
                                                        is NasCallResult.Err -> errorMessage = updateResult.message
                                                    }
                                                    isBusy = false
                                                }
                                            },
                                            modifier = Modifier.weight(1f),
                                            colors = ButtonDefaults.buttonColors(containerColor = actionGreen)
                                        ) { Text("Save") }
                                        Button(
                                            enabled = !isBusy,
                                            onClick = {
                                                pendingDeleteBoat = boat
                                                showDeleteBoatConfirm = true
                                            },
                                            modifier = Modifier.weight(1f),
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8D1B1B))
                                        ) { Text("Delete") }
                                    }
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                        }
                    }
                }
            }

            if (mode == "organizer_races") {
                val event = eventSnapshot
                RegattaSection(title = "", cardColor = cardColor) {
                    Button(
                        enabled = !isBusy,
                        onClick = {
                            if (joinedEventId.isBlank() || organizerToken.isBlank()) {
                                errorMessage = "Organizer session missing."
                                return@Button
                            }
                            isBusy = true
                            errorMessage = null
                            statusMessage = null
                            scope.launch {
                                when (val result = withContext(Dispatchers.IO) {
                                    apiClient.createRace(
                                        eventId = joinedEventId,
                                        organizerToken = organizerToken,
                                        name = "",
                                        dayNumber = 1
                                    )
                                }) {
                                    is NasCallResult.Ok -> {
                                        when (val snapshotResult = withContext(Dispatchers.IO) {
                                            apiClient.getEventSnapshot(joinedEventId)
                                        }) {
                                            is NasCallResult.Ok -> {
                                                eventSnapshot = snapshotResult.value
                                            }
                                            is NasCallResult.Err -> errorMessage = snapshotResult.message
                                        }
                                    }
                                    is NasCallResult.Err -> errorMessage = result.message
                                }
                                isBusy = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = actionGreen),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(if (isBusy) "Adding..." else "Add Race") }
                    Spacer(Modifier.height(8.dp))
                    if (event == null || event.races.isEmpty()) {
                        Text("No races.", color = muted)
                    } else {
                        event.races.sortedBy { it.sequenceNumber }.forEach { race ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        onClick = { openOrganizerRaceEditor(race) },
                                        onLongClick = {
                                            pendingDeleteRace = race
                                            showDeleteRaceConfirm = true
                                        }
                                    )
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFF171717))
                                        .padding(10.dp)
                                ) {
                                    Text(
                                        race.name.ifBlank { "Race ${race.sequenceNumber}" },
                                        color = Color.White,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        "Date: ${race.raceDate.orEmpty().ifBlank { "--" }}",
                                        color = Color(0xFFCFD8DC),
                                        fontSize = 12.sp
                                    )
                                    Text(
                                        "State: ${computeAutoRaceStateLabel(race)}",
                                        color = Color(0xFFCFD8DC),
                                        fontSize = 12.sp
                                    )
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                        }
                    }
                }
            }

            if (mode == "organizer_race_editor") {
                val event = eventSnapshot
                val race = event?.races?.firstOrNull { it.id == organizerEditingRaceId }
                val helperGates = organizerRaceEditorHelperGates
                val gateOptions = buildList {
                    add("start")
                    helperGates.forEachIndexed { index, _ -> add("gate:${index + 1}") }
                    add("finish")
                }
                if (organizerGateSelection !in gateOptions) {
                    organizerGateSelection = "start"
                }
                val raceLabel = race?.let { "Race R${it.sequenceNumber}" } ?: "Race"
                val selectedGateLabel = when {
                    organizerGateSelection == "start" -> "Start line"
                    organizerGateSelection == "finish" -> "Finish line"
                    organizerGateSelection.startsWith("gate:") -> {
                        val gateNumber = organizerGateSelection.substringAfter("gate:").toIntOrNull() ?: 1
                        "Gate $gateNumber"
                    }
                    else -> "Start line"
                }
                RegattaSection(title = "", cardColor = cardColor) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Race editor", color = Color.White, fontWeight = FontWeight.Bold)
                        TextButton(
                            onClick = { mode = "organizer_races" },
                            modifier = Modifier.height(28.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                        ) {
                            Text("Back", fontSize = 11.sp)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(raceLabel, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    Spacer(Modifier.height(8.dp))
                    Box {
                        OutlinedTextField(
                            value = organizerRaceDateInput,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Date") },
                            trailingIcon = { Text("📅") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clickable {
                                    val current = runCatching {
                                        val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                                            isLenient = false
                                        }.parse(organizerRaceDateInput.trim())
                                        Calendar.getInstance().apply { time = parsed ?: Date() }
                                    }.getOrElse { Calendar.getInstance() }
                                    DatePickerDialog(
                                        context,
                                        { _, year, month, dayOfMonth ->
                                            organizerRaceDateInput = String.format(
                                                Locale.US,
                                                "%04d-%02d-%02d",
                                                year,
                                                month + 1,
                                                dayOfMonth
                                            )
                                        },
                                        current.get(Calendar.YEAR),
                                        current.get(Calendar.MONTH),
                                        current.get(Calendar.DAY_OF_MONTH)
                                    ).show()
                                }
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TimePickerField(
                            value = organizerRaceStartTimeInput,
                            onValueChange = { organizerRaceStartTimeInput = it },
                            label = { Text("Start time (hh:mm)") },
                            modifier = Modifier.weight(1f)
                        )
                        TimePickerField(
                            value = organizerRaceEndTimeInput,
                            onValueChange = { organizerRaceEndTimeInput = it },
                            label = { Text("End time (hh:mm)") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Box {
                        OutlinedTextField(
                            value = selectedGateLabel,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Selected gate") },
                            trailingIcon = { Text("▼", color = muted) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .pointerInput(organizerGateMenuExpanded) {
                                    detectTapGestures(onTap = { organizerGateMenuExpanded = true })
                                }
                        )
                        DropdownMenu(
                            expanded = organizerGateMenuExpanded,
                            onDismissRequest = { organizerGateMenuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Start line") },
                                onClick = {
                                    organizerGateSelection = "start"
                                    organizerGateMenuExpanded = false
                                }
                            )
                            helperGates.forEachIndexed { index, _ ->
                                DropdownMenuItem(
                                    text = { Text("Gate ${index + 1}") },
                                    onClick = {
                                        organizerGateSelection = "gate:${index + 1}"
                                        organizerGateMenuExpanded = false
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Finish line") },
                                onClick = {
                                    organizerGateSelection = "finish"
                                    organizerGateMenuExpanded = false
                                }
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                val insertIndex = when {
                                    organizerGateSelection == "start" -> 0
                                    organizerGateSelection == "finish" -> helperGates.size
                                    organizerGateSelection.startsWith("gate:") ->
                                        (organizerGateSelection.substringAfter("gate:").toIntOrNull() ?: helperGates.size)
                                            .coerceIn(1, helperGates.size)
                                    else -> helperGates.size
                                }
                                val templateGate = when {
                                    organizerGateSelection == "start" -> organizerRaceEditorStartGate
                                    organizerGateSelection == "finish" -> helperGates.lastOrNull()
                                        ?: organizerRaceEditorFinishGate
                                        ?: organizerRaceEditorStartGate
                                    organizerGateSelection.startsWith("gate:") -> {
                                        val gateIndex = (organizerGateSelection.substringAfter("gate:").toIntOrNull() ?: 1) - 1
                                        helperGates.getOrNull(gateIndex)
                                    }
                                    else -> organizerRaceEditorStartGate
                                } ?: createFallbackGate(UUID.randomUUID().toString(), 0, "gate", "")
                                val newGate = templateGate.copy(
                                    id = UUID.randomUUID().toString(),
                                    order = 0,
                                    type = "gate",
                                    name = ""
                                )
                                val mutable = helperGates.toMutableList()
                                mutable.add(insertIndex, newGate)
                                organizerRaceEditorHelperGates = normalizeEditorHelperGates(mutable)
                                organizerGateSelection = "gate:${insertIndex + 1}"
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = actionGreen),
                            modifier = Modifier.weight(1f)
                        ) { Text("Add gate") }
                        Button(
                            onClick = {
                                if (organizerGateSelection == "start" || organizerGateSelection == "finish") {
                                    errorMessage = "Start line and Finish line cannot be deleted."
                                    return@Button
                                }
                                if (organizerGateSelection.startsWith("gate:")) {
                                    val gateIndex = (organizerGateSelection.substringAfter("gate:").toIntOrNull() ?: 1) - 1
                                    if (gateIndex in helperGates.indices) {
                                        val mutable = helperGates.toMutableList()
                                        mutable.removeAt(gateIndex)
                                        organizerRaceEditorHelperGates = normalizeEditorHelperGates(mutable)
                                        organizerGateSelection = if (mutable.isEmpty()) "start" else {
                                            "gate:${(gateIndex + 1).coerceAtMost(mutable.size)}"
                                        }
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8E2424)),
                            modifier = Modifier.weight(1f)
                        ) { Text("Delete gate") }
                    }
                    Spacer(Modifier.height(8.dp))
                    val selectedGateIndex = if (organizerGateSelection.startsWith("gate:")) {
                        (organizerGateSelection.substringAfter("gate:").toIntOrNull() ?: 1) - 1
                    } else {
                        -1
                    }
                    val selectedGateLeft = when {
                        organizerGateSelection == "start" -> organizerRaceEditorStartGate?.pointA
                        organizerGateSelection == "finish" -> organizerRaceEditorFinishGate?.pointA
                        selectedGateIndex in helperGates.indices -> helperGates[selectedGateIndex].pointA
                        else -> null
                    }
                    val selectedGateRight = when {
                        organizerGateSelection == "start" -> organizerRaceEditorStartGate?.pointB
                        organizerGateSelection == "finish" -> organizerRaceEditorFinishGate?.pointB
                        selectedGateIndex in helperGates.indices -> helperGates[selectedGateIndex].pointB
                        else -> null
                    }
                    val selectedMapPointLabel = organizerSelectedMapPoint?.let(::formatPoint) ?: "--"
                    fun applyPointToSelectedGate(isLeft: Boolean, point: RegattaPoint) {
                        when {
                            organizerGateSelection == "start" -> {
                                val gate = organizerRaceEditorStartGate ?: return
                                organizerRaceEditorStartGate = if (isLeft) gate.copy(pointA = point) else gate.copy(pointB = point)
                            }
                            organizerGateSelection == "finish" -> {
                                val gate = organizerRaceEditorFinishGate ?: return
                                organizerRaceEditorFinishGate = if (isLeft) gate.copy(pointA = point) else gate.copy(pointB = point)
                            }
                            selectedGateIndex in helperGates.indices -> {
                                val mutable = helperGates.toMutableList()
                                val gate = mutable[selectedGateIndex]
                                mutable[selectedGateIndex] = if (isLeft) gate.copy(pointA = point) else gate.copy(pointB = point)
                                organizerRaceEditorHelperGates = normalizeEditorHelperGates(mutable)
                            }
                        }
                    }
                    Text("Buoy points for $selectedGateLabel", color = Color.White, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Text("Selected map point: $selectedMapPointLabel", color = Color(0xFFCFD8DC), fontSize = 12.sp)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Left buoy", color = Color.White, fontWeight = FontWeight.SemiBold)
                            Text(
                                selectedGateLeft?.let(::formatPoint) ?: "--",
                                color = Color(0xFFCFD8DC),
                                fontSize = 12.sp
                            )
                            Button(
                                onClick = {
                                    val gpsPoint = snapshotPoint()
                                    if (gpsPoint == null) {
                                        errorMessage = "GPS position unavailable."
                                    } else {
                                        applyPointToSelectedGate(isLeft = true, point = gpsPoint)
                                        statusMessage = "Left buoy set from GPS."
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF424242)),
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("From GPS") }
                            Button(
                                onClick = {
                                    val mapPoint = organizerSelectedMapPoint
                                    if (mapPoint == null) {
                                        errorMessage = "Long press map to select a point first."
                                    } else {
                                        applyPointToSelectedGate(isLeft = true, point = mapPoint)
                                        statusMessage = "Left buoy set from map."
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF424242)),
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("from Map") }
                        }
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Right buoy", color = Color.White, fontWeight = FontWeight.SemiBold)
                            Text(
                                selectedGateRight?.let(::formatPoint) ?: "--",
                                color = Color(0xFFCFD8DC),
                                fontSize = 12.sp
                            )
                            Button(
                                onClick = {
                                    val gpsPoint = snapshotPoint()
                                    if (gpsPoint == null) {
                                        errorMessage = "GPS position unavailable."
                                    } else {
                                        applyPointToSelectedGate(isLeft = false, point = gpsPoint)
                                        statusMessage = "Right buoy set from GPS."
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF424242)),
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("From GPS") }
                            Button(
                                onClick = {
                                    val mapPoint = organizerSelectedMapPoint
                                    if (mapPoint == null) {
                                        errorMessage = "Long press map to select a point first."
                                    } else {
                                        applyPointToSelectedGate(isLeft = false, point = mapPoint)
                                        statusMessage = "Right buoy set from map."
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF424242)),
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("from Map") }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    OrganizerGateMap(
                        startA = organizerRaceEditorStartGate?.pointA,
                        startB = organizerRaceEditorStartGate?.pointB,
                        finishA = organizerRaceEditorFinishGate?.pointA,
                        finishB = organizerRaceEditorFinishGate?.pointB,
                        helperGates = organizerRaceEditorHelperGates.map { gate ->
                            PendingGateDraft(
                                id = gate.id,
                                name = gate.name,
                                pointA = gate.pointA,
                                pointB = gate.pointB
                            )
                        },
                        selectedMapPoint = organizerSelectedMapPoint,
                        fallbackCenter = currentLocation?.let { RegattaPoint(it.latitude, it.longitude) },
                        selectedGateKey = organizerGateSelection,
                        onMapPointSelected = {
                            organizerSelectedMapPoint = it
                            statusMessage = "Map point selected. Use from Map."
                        }
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        enabled = !isBusy && organizerEditingRaceId.isNotBlank() && organizerToken.isNotBlank(),
                        onClick = {
                            val raceId = organizerEditingRaceId
                            val startGate = organizerRaceEditorStartGate
                            val finishGate = organizerRaceEditorFinishGate
                            if (raceId.isBlank() || startGate == null || finishGate == null) {
                                errorMessage = "Race data is incomplete."
                                return@Button
                            }
                            if (!organizerRaceStartTimeInput.matches(Regex("""^\d{2}:\d{2}$""")) ||
                                !organizerRaceEndTimeInput.matches(Regex("""^\d{2}:\d{2}$"""))
                            ) {
                                errorMessage = "Start and end time must be in hh:mm format."
                                return@Button
                            }
                            val raceLengthNm = organizerRaceLengthNmInput.toDoubleOrNull()
                                ?.takeIf { it > 0.0 }
                                ?: 0.01
                            val helperForSave = normalizeEditorHelperGates(organizerRaceEditorHelperGates)
                            val gatesForSave = buildList {
                                add(startGate.copy(order = 1, type = "start", name = "Start line"))
                                addAll(helperForSave)
                                add(
                                    finishGate.copy(
                                        order = helperForSave.size + 2,
                                        type = "finish",
                                        name = "Finish line"
                                    )
                                )
                            }
                            isBusy = true
                            errorMessage = null
                            statusMessage = null
                            scope.launch {
                                when (val detailsResult = withContext(Dispatchers.IO) {
                                    apiClient.updateRaceDetails(
                                        raceId = raceId,
                                        organizerToken = organizerToken,
                                        raceDate = organizerRaceDateInput,
                                        startTime = organizerRaceStartTimeInput,
                                        endTime = organizerRaceEndTimeInput,
                                        raceLengthNm = raceLengthNm
                                    )
                                }) {
                                    is NasCallResult.Err -> errorMessage = detailsResult.message
                                    is NasCallResult.Ok -> {
                                        when (val courseResult = withContext(Dispatchers.IO) {
                                            apiClient.updateCourse(
                                                raceId = raceId,
                                                gates = gatesForSave.map { gate ->
                                                    GateDraft(
                                                        order = gate.order,
                                                        type = gate.type,
                                                        name = gate.name,
                                                        pointA = gate.pointA,
                                                        pointB = gate.pointB
                                                    )
                                                },
                                                organizerToken = organizerToken
                                            )
                                        }) {
                                            is NasCallResult.Ok -> {
                                                when (val snapshotResult = withContext(Dispatchers.IO) {
                                                    apiClient.getEventSnapshot(joinedEventId)
                                                }) {
                                                    is NasCallResult.Ok -> {
                                                        eventSnapshot = snapshotResult.value
                                                        snapshotResult.value.races
                                                            .firstOrNull { it.id == raceId }
                                                            ?.let { refreshedRace ->
                                                                openOrganizerRaceEditor(refreshedRace)
                                                            }
                                                        statusMessage = "Race updated."
                                                    }
                                                    is NasCallResult.Err -> errorMessage = snapshotResult.message
                                                }
                                            }
                                            is NasCallResult.Err -> errorMessage = courseResult.message
                                        }
                                    }
                                }
                                isBusy = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = actionBlue)
                    ) { Text(if (isBusy) "Saving..." else "Save race") }
                }
            }

            if (mode == "organizer_results") {
                val selectedEvent = eventSnapshot
                val selectedRace = selectedEvent?.races
                    ?.firstOrNull { it.id == liveSnapshot?.raceId }
                    ?: selectedEvent?.races?.firstOrNull { it.id == joinedRaceId }
                    ?: selectedEvent?.races?.firstOrNull { it.id == selectedEvent.activeRaceId }
                    ?: selectedEvent?.races?.firstOrNull()
                val raceLive = liveSnapshot
                RegattaSection(title = "Results", cardColor = cardColor) {
                    if (selectedEvent == null || selectedRace == null || raceLive == null) {
                        Text("Results not available.", color = muted)
                    } else {
                        val raceRows = computeRaceScoreRows(raceLive, selectedEvent.boats)
                        val (gateTimelineHeaders, gateTimelineRows) = computeRaceGateTimelineRows(
                            live = raceLive,
                            boats = selectedEvent.boats
                        )
                        val gateTimelineRowsByGroup = gateTimelineRows
                            .groupBy { normalizeGroupLabel(it.groupCode) }
                            .toSortedMap()
                        val raceRowsByGroup = raceRows
                            .groupBy { normalizeGroupLabel(it.groupCode) }
                            .toSortedMap()
                        val selectedRaceIds = selectedEvent.races
                            .takeWhile { it.id != selectedRace.id }
                            .map { it.id } + selectedRace.id
                        val regattaRows = computeEventScoreTable(
                            event = selectedEvent,
                            raceSnapshots = listOf(raceLive),
                            raceIds = selectedRaceIds
                        )
                        val regattaRowsByGroup = regattaRows
                            .groupBy { normalizeGroupLabel(it.groupCode) }
                            .toSortedMap()
                        Text(
                            selectedRace.name.ifBlank { "Race ${selectedRace.sequenceNumber}" },
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Start: ${selectedRace.countdownTargetEpochMs?.let(::formatEpoch) ?: "--"}",
                            color = Color(0xFFCFD8DC),
                            fontSize = 12.sp
                        )
                        Spacer(Modifier.height(10.dp))
                        Text("Gate crossings", color = Color.White, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        val gateTimelineHorizontalScroll = rememberScrollState()
                        if (gateTimelineHeaders.isEmpty()) {
                            Text("No gates defined for crossings.", color = muted)
                        } else if (gateTimelineRows.isEmpty()) {
                            Text("No boats to display crossings.", color = muted)
                        } else {
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier
                                        .horizontalScroll(gateTimelineHorizontalScroll)
                                        .background(Color(0xFF263238))
                                        .padding(8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text("Boat", color = Color.White, fontSize = 11.sp, modifier = Modifier.width(130.dp))
                                    Text("Group", color = Color.White, fontSize = 11.sp, modifier = Modifier.width(80.dp))
                                    gateTimelineHeaders.forEach { gateHeader ->
                                        Text(gateHeader, color = Color.White, fontSize = 11.sp, modifier = Modifier.width(84.dp))
                                    }
                                    Text("Corrected", color = Color.White, fontSize = 11.sp, modifier = Modifier.width(90.dp))
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            gateTimelineRowsByGroup.forEach { (groupName, rowsInGroup) ->
                                Text("Group: $groupName", color = Color(0xFFFFF59D), fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.height(4.dp))
                                rowsInGroup.forEach { row ->
                                    Card(modifier = Modifier.fillMaxWidth()) {
                                        Row(
                                            modifier = Modifier
                                                .horizontalScroll(gateTimelineHorizontalScroll)
                                                .background(Color(0xFF171717))
                                                .padding(10.dp),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(row.boatName, color = Color.White, fontSize = 11.sp, modifier = Modifier.width(130.dp))
                                            Text(groupName, color = Color.White, fontSize = 11.sp, modifier = Modifier.width(80.dp))
                                            row.gateTimes.forEach { crossingEpoch ->
                                                Text(
                                                    crossingEpoch?.let(::formatEpochTime) ?: "--",
                                                    color = Color.White,
                                                    fontSize = 11.sp,
                                                    modifier = Modifier.width(84.dp)
                                                )
                                            }
                                            Text(
                                                row.correctedElapsedMs?.let(::formatDurationFromMillis) ?: "--",
                                                color = Color.White,
                                                fontSize = 11.sp,
                                                modifier = Modifier.width(90.dp)
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(6.dp))
                                }
                                Spacer(Modifier.height(6.dp))
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        Text("Race results", color = Color.White, fontWeight = FontWeight.Bold)
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF263238))
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Boat", color = Color.White, fontSize = 11.sp, modifier = Modifier.weight(2f))
                                Text("Group", color = Color.White, fontSize = 11.sp, modifier = Modifier.weight(1f))
                                Text("Elapsed", color = Color.White, fontSize = 11.sp, modifier = Modifier.weight(1f))
                                Text("Penalty", color = Color.White, fontSize = 11.sp, modifier = Modifier.weight(1f))
                                Text("Corr.", color = Color.White, fontSize = 11.sp, modifier = Modifier.weight(1f))
                                Text("Points", color = Color.White, fontSize = 11.sp, modifier = Modifier.weight(1f))
                                Text("Pos.", color = Color.White, fontSize = 11.sp, modifier = Modifier.weight(0.7f))
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        if (raceRows.isEmpty()) {
                            Text("No race data.", color = muted)
                        } else {
                            raceRowsByGroup.forEach { (groupName, rowsInGroup) ->
                                Text("Group: $groupName", color = Color(0xFFFFF59D), fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.height(4.dp))
                                val orderedGroup = rowsInGroup.sortedWith(
                                    compareBy<RaceScoreRow>(
                                        { it.racePoints },
                                        { it.correctedElapsedMs ?: Long.MAX_VALUE },
                                        { it.boatName.lowercase(Locale.US) }
                                    )
                                )
                                orderedGroup.forEachIndexed { groupIndex, row ->
                                    Card(modifier = Modifier.fillMaxWidth()) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(Color(0xFF171717))
                                                .padding(10.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(row.boatName, color = Color.White, fontSize = 11.sp, modifier = Modifier.weight(2f))
                                                Text(groupName, color = Color.White, fontSize = 11.sp, modifier = Modifier.weight(1f))
                                                Text(
                                                    if (row.status.equals("DNF", ignoreCase = true)) "DNF" else row.realElapsedMs?.let(::formatDurationFromMillis) ?: "--",
                                                    color = Color.White,
                                                    fontSize = 11.sp,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                Text(String.format(Locale.US, "%.1f%%", row.penaltyPercent), color = Color.White, fontSize = 11.sp, modifier = Modifier.weight(1f))
                                                Text(
                                                    if (row.status.equals("DNF", ignoreCase = true)) "DNF" else row.correctedElapsedMs?.let(::formatDurationFromMillis) ?: "--",
                                                    color = Color.White,
                                                    fontSize = 11.sp,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                Text(String.format(Locale.US, "%.2f", row.racePoints), color = Color.White, fontSize = 11.sp, modifier = Modifier.weight(1f))
                                                Text((groupIndex + 1).toString(), color = Color.White, fontSize = 11.sp, modifier = Modifier.weight(0.7f))
                                            }
                                        }
                                    }
                                    Spacer(Modifier.height(6.dp))
                                }
                                Spacer(Modifier.height(6.dp))
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        Text("Regatta standings", color = Color.White, fontWeight = FontWeight.Bold)
                        val raceHeader = selectedRaceIds.indices.joinToString(" | ") { "R${it + 1}" }
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF263238))
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Boat", color = Color.White, fontSize = 11.sp, modifier = Modifier.weight(2f))
                                Text("Group", color = Color.White, fontSize = 11.sp, modifier = Modifier.weight(1f))
                                Text(raceHeader, color = Color.White, fontSize = 11.sp, modifier = Modifier.weight(2f))
                                Text("Total", color = Color.White, fontSize = 11.sp, modifier = Modifier.weight(1f))
                                Text("Pos.", color = Color.White, fontSize = 11.sp, modifier = Modifier.weight(0.7f))
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        if (regattaRows.isEmpty()) {
                            Text("No data for regatta standings.", color = muted)
                        } else {
                            regattaRowsByGroup.forEach { (groupName, rowsInGroup) ->
                                Text("Group: $groupName", color = Color(0xFFFFF59D), fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.height(4.dp))
                                val orderedGroup = rowsInGroup.sortedWith(
                                    compareBy<EventScoreRow>(
                                        { it.totalPoints },
                                        { it.boatName.lowercase(Locale.US) }
                                    )
                                )
                                orderedGroup.forEachIndexed { groupIndex, row ->
                                    val raceColumns = row.racePoints.mapIndexed { index, points ->
                                        "R${index + 1}:${points?.let { String.format(Locale.US, "%.2f", it) } ?: "-"}"
                                    }.joinToString(" | ")
                                    Card(modifier = Modifier.fillMaxWidth()) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(Color(0xFF171717))
                                                .padding(10.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(row.boatName, color = Color.White, fontSize = 11.sp, modifier = Modifier.weight(2f))
                                                Text(groupName, color = Color.White, fontSize = 11.sp, modifier = Modifier.weight(1f))
                                                Text(raceColumns, color = Color.White, fontSize = 11.sp, modifier = Modifier.weight(2f))
                                                Text(String.format(Locale.US, "%.2f", row.totalPoints), color = Color.White, fontSize = 11.sp, modifier = Modifier.weight(1f))
                                                Text((groupIndex + 1).toString(), color = Color.White, fontSize = 11.sp, modifier = Modifier.weight(0.7f))
                                            }
                                        }
                                    }
                                    Spacer(Modifier.height(6.dp))
                                }
                                Spacer(Modifier.height(6.dp))
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = {
                                val combinedCsv = runCatching {
                                    buildCombinedResultsCsv(
                                        regattaName = selectedEvent.name,
                                        regattaStartDate = selectedEvent.startDate,
                                        regattaEndDate = selectedEvent.endDate,
                                        raceName = selectedRace.name,
                                        raceDate = selectedRace.raceDate,
                                        raceStartTime = selectedRace.startTime,
                                        raceEndTime = selectedRace.endTime,
                                        raceRowsByGroup = raceRowsByGroup,
                                        regattaRowsByGroup = regattaRowsByGroup,
                                        selectedRaceIds = selectedRaceIds
                                    )
                                }.getOrElse {
                                    buildCombinedResultsCsvFallback(
                                        regattaName = selectedEvent.name,
                                        regattaStartDate = selectedEvent.startDate,
                                        regattaEndDate = selectedEvent.endDate,
                                        raceName = selectedRace.name,
                                        raceDate = selectedRace.raceDate,
                                        raceStartTime = selectedRace.startTime,
                                        raceEndTime = selectedRace.endTime,
                                        raceRowsByGroup = raceRowsByGroup,
                                        regattaRowsByGroup = regattaRowsByGroup,
                                        selectedRaceIds = selectedRaceIds
                                    )
                                }
                                val file = writeCsvFile(
                                    context = context,
                                    fileName = "results_${sanitizeFileName(selectedEvent.name)}_${sanitizeFileName(selectedRace.name)}.csv",
                                    csvContent = combinedCsv
                                )
                                runCatching {
                                    shareCsvFile(context, file, "Share results CSV")
                                }.onFailure {
                                    errorMessage = "CSV export failed."
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = actionGreen)
                        ) { Text("Share CSV (all tables)") }
                    }
                }
            }

            if (mode == "organizer_notices") {
                val event = eventSnapshot
                val notices = event?.noticePosts.orEmpty()
                RegattaSection(title = "", cardColor = cardColor) {
                    if (noticeListReturnMode == "organizer") {
                        OutlinedTextField(
                            value = noticeBoardInput,
                            onValueChange = { noticeBoardInput = it },
                            label = { Text("New note text") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(
                            enabled = !isBusy,
                            onClick = {
                                val text = noticeBoardInput.trim()
                                if (text.isBlank()) {
                                    errorMessage = "Enter note text."
                                    return@Button
                                }
                                isBusy = true
                                errorMessage = null
                                statusMessage = null
                                scope.launch {
                                    when (val result = withContext(Dispatchers.IO) {
                                        apiClient.updateNoticeBoard(
                                            eventId = joinedEventId,
                                            organizerToken = organizerToken,
                                            noticeText = text
                                        )
                                    }) {
                                        is NasCallResult.Ok -> {
                                            noticeBoardInput = ""
                                            when (val snapshotResult = withContext(Dispatchers.IO) {
                                                apiClient.getEventSnapshot(joinedEventId)
                                            }) {
                                                is NasCallResult.Ok -> {
                                                    eventSnapshot = snapshotResult.value
                                                    statusMessage = "Note added."
                                                }
                                                is NasCallResult.Err -> errorMessage = snapshotResult.message
                                            }
                                        }
                                        is NasCallResult.Err -> errorMessage = result.message
                                    }
                                    isBusy = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = actionGreen)
                        ) { Text(if (isBusy) "Saving..." else "Add new") }
                        Spacer(Modifier.height(8.dp))
                    }
                    if (notices.isEmpty()) {
                        Text("No notices.", color = muted)
                    } else {
                        notices.forEach { post ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .pointerInput(post.id, noticeListReturnMode) {
                                        detectTapGestures(
                                            onLongPress = {
                                                if (noticeListReturnMode == "organizer") {
                                                    pendingDeleteNotice = post
                                                    showDeleteNoticeConfirm = true
                                                }
                                            }
                                        )
                                    }
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFF171717))
                                        .padding(10.dp)
                                ) {
                                    Text(
                                        post.noticeText.ifBlank { "--" },
                                        color = Color.White,
                                        fontSize = 12.sp
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        formatIsoDateTime(post.publishedAt),
                                        color = Color(0xFFB0BEC5),
                                        fontSize = 11.sp
                                    )
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                        }
                    }
                }
            }

            
        }
    }
    if (organizerLoginAskedCreateNew) {
        AlertDialog(
            onDismissRequest = { organizerLoginAskedCreateNew = false },
            title = { Text("New regatta?") },
            text = { Text("No organizer hash entered. Would you like to create a new regatta?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        organizerLoginAskedCreateNew = false
                        organizerToken = ""
                        joinedEventId = ""
                        joinedRaceId = ""
                        joinedBoatId = ""
                        eventName = ""
                        joinCode = ""
                        organizerCode = ""
                        regattaStartDate = ""
                        regattaEndDate = ""
                        maxBoatsInput = "50"
                        isPublicEvent = false
                        organizerCreateFlow = true
                                        mode = "organizer_editor"
                    }
                ) { Text("Yes") }
            },
            dismissButton = {
                TextButton(onClick = { organizerLoginAskedCreateNew = false }) { Text("No") }
            }
        )
    }
    if (showDeleteBoatConfirm && pendingDeleteBoat != null) {
        val boat = pendingDeleteBoat!!
        AlertDialog(
            onDismissRequest = {
                showDeleteBoatConfirm = false
                pendingDeleteBoat = null
            },
            title = { Text("Delete boat") },
            text = { Text("Are you sure? This will delete all boat tracks for ${boat.boatName}.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val eventId = joinedEventId
                        val token = organizerToken
                        showDeleteBoatConfirm = false
                        pendingDeleteBoat = null
                        scope.launch {
                            when (val result = withContext(Dispatchers.IO) {
                                apiClient.deleteBoat(eventId, boat.id, token)
                            }) {
                                is NasCallResult.Ok -> {
                                    statusMessage = "Boat ${boat.boatName} deleted."
                                    when (val snapshotResult = withContext(Dispatchers.IO) {
                                        apiClient.getEventSnapshot(eventId)
                                    }) {
                                        is NasCallResult.Ok -> eventSnapshot = snapshotResult.value
                                        is NasCallResult.Err -> errorMessage = snapshotResult.message
                                    }
                                }
                                is NasCallResult.Err -> errorMessage = result.message
                            }
                        }
                    }
                ) { Text("Yes") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDeleteBoatConfirm = false
                        pendingDeleteBoat = null
                    }
                ) { Text("Cancel") }
            }
        )
    }
    if (showDeleteNoticeConfirm && pendingDeleteNotice != null) {
        val post = pendingDeleteNotice!!
        AlertDialog(
            onDismissRequest = {
                showDeleteNoticeConfirm = false
                pendingDeleteNotice = null
            },
            title = { Text("Delete note") },
            text = { Text("Delete selected note?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val eventId = joinedEventId
                        val token = organizerToken
                        showDeleteNoticeConfirm = false
                        pendingDeleteNotice = null
                        scope.launch {
                            when (val result = withContext(Dispatchers.IO) {
                                apiClient.deleteNoticePost(
                                    eventId = eventId,
                                    noticeId = post.id,
                                    organizerToken = token
                                )
                            }) {
                                is NasCallResult.Ok -> {
                                    statusMessage = "Note deleted."
                                    when (val snapshotResult = withContext(Dispatchers.IO) {
                                        apiClient.getEventSnapshot(eventId)
                                    }) {
                                        is NasCallResult.Ok -> eventSnapshot = snapshotResult.value
                                        is NasCallResult.Err -> errorMessage = snapshotResult.message
                                    }
                                }
                                is NasCallResult.Err -> errorMessage = result.message
                            }
                        }
                    }
                ) { Text("Yes") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDeleteNoticeConfirm = false
                        pendingDeleteNotice = null
                    }
                ) { Text("No") }
            }
        )
    }
    if (showDeleteRaceConfirm && pendingDeleteRace != null) {
        val race = pendingDeleteRace!!
        AlertDialog(
            onDismissRequest = {
                showDeleteRaceConfirm = false
                pendingDeleteRace = null
            },
            title = { Text("Delete race") },
            text = { Text("Delete ${race.name.ifBlank { "Race ${race.sequenceNumber}" }}?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val eventId = joinedEventId
                        val token = organizerToken
                        showDeleteRaceConfirm = false
                        pendingDeleteRace = null
                        scope.launch {
                            when (val result = withContext(Dispatchers.IO) {
                                apiClient.deleteRace(
                                    raceId = race.id,
                                    organizerToken = token,
                                    eventId = eventId
                                )
                            }) {
                                is NasCallResult.Ok -> {
                                    statusMessage = "Race deleted."
                                    when (val snapshotResult = withContext(Dispatchers.IO) {
                                        apiClient.getEventSnapshot(eventId)
                                    }) {
                                        is NasCallResult.Ok -> eventSnapshot = snapshotResult.value
                                        is NasCallResult.Err -> errorMessage = snapshotResult.message
                                    }
                                }
                                is NasCallResult.Err -> errorMessage = result.message
                            }
                        }
                    }
                ) { Text("Yes") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDeleteRaceConfirm = false
                        pendingDeleteRace = null
                    }
                ) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun RegattaCourseMap(
    gates: List<RegattaGate>,
    participants: List<RegattaParticipantLive>
) {
    val startGate = gates.firstOrNull { it.type.equals("start", ignoreCase = true) }
    val finishGate = gates.firstOrNull { it.type.equals("finish", ignoreCase = true) }
    val helperGates = gates.filter { it.type.equals("gate", ignoreCase = true) }
    val participantPositions = participants.filter {
        it.lastLatitude != null && it.lastLongitude != null
    }
    val participantStartSnapshots = participants.filter {
        it.startSnapshotLatitude != null && it.startSnapshotLongitude != null
    }
    if (startGate == null && finishGate == null && helperGates.isEmpty() && participantPositions.isEmpty() &&
        participantStartSnapshots.isEmpty()
    ) {
        Text("No course lines or boat positions available for active race yet.", color = Color(0xFFBDBDBD), fontSize = 12.sp)
        return
    }

    AndroidView(
        modifier = Modifier
            .fillMaxWidth()
            .height(240.dp),
        factory = { ctx ->
            org.osmdroid.config.Configuration.getInstance().userAgentValue = ctx.packageName
            MapView(ctx).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                setBuiltInZoomControls(false)
                isTilesScaledToDpi = true
                controller.setZoom(16.0)
            }
        },
        update = { mapView ->
            mapView.overlays.clear()

            val boundsPoints = mutableListOf<GeoPoint>()

            fun addGateLine(gate: RegattaGate, color: Int) {
                val pointA = GeoPoint(gate.pointA.latitude, gate.pointA.longitude)
                val pointB = GeoPoint(gate.pointB.latitude, gate.pointB.longitude)
                boundsPoints += pointA
                boundsPoints += pointB
                mapView.overlays.add(
                    Polyline(mapView).apply {
                        outlinePaint.color = color
                        outlinePaint.strokeWidth = 8f
                        setPoints(listOf(pointA, pointB))
                        title = gate.name
                    }
                )
                mapView.overlays.add(
                    Marker(mapView).apply {
                        position = pointA
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        icon = createGateBuoyDrawable(android.graphics.Color.RED)
                        title = "${gate.name} L"
                    }
                )
                mapView.overlays.add(
                    Marker(mapView).apply {
                        position = pointB
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        icon = createGateBuoyDrawable(android.graphics.Color.GREEN)
                        title = "${gate.name} R"
                    }
                )
            }

            startGate?.let { addGateLine(it, android.graphics.Color.YELLOW) }
            helperGates.forEach { addGateLine(it, android.graphics.Color.rgb(66, 165, 245)) }
            finishGate?.let { addGateLine(it, android.graphics.Color.RED) }

            participantPositions.forEach { participant ->
                val latitude = participant.lastLatitude ?: return@forEach
                val longitude = participant.lastLongitude ?: return@forEach
                val boatPoint = GeoPoint(latitude, longitude)
                boundsPoints += boatPoint
                mapView.overlays.add(
                    Marker(mapView).apply {
                        position = boatPoint
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        title = participant.boatName
                        subDescription = buildString {
                            participant.lastSpeedKnots?.let {
                                append(String.format(Locale.US, "%.1f kn", it))
                            }
                            participant.lastSignalEpochMs?.let {
                                if (isNotEmpty()) append(" · ")
                                append(formatEpoch(it))
                            }
                        }.ifBlank { null }
                    }
                )
            }
            participantStartSnapshots.forEach { participant ->
                val latitude = participant.startSnapshotLatitude ?: return@forEach
                val longitude = participant.startSnapshotLongitude ?: return@forEach
                val startPoint = GeoPoint(latitude, longitude)
                boundsPoints += startPoint
                mapView.overlays.add(
                    Marker(mapView).apply {
                        position = startPoint
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        title = "${participant.boatName} T0"
                        subDescription = participant.startSnapshotEpochMs?.let(::formatEpoch)
                    }
                )
            }

            when {
                boundsPoints.size >= 2 -> {
                    mapView.zoomToBoundingBox(BoundingBox.fromGeoPointsSafe(boundsPoints), true, 48)
                }
                boundsPoints.size == 1 -> {
                    mapView.controller.setCenter(boundsPoints.first())
                    mapView.controller.setZoom(17.0)
                }
            }
            mapView.invalidate()
        }
    )
}

@Composable
private fun OrganizerGateMap(
    startA: RegattaPoint?,
    startB: RegattaPoint?,
    finishA: RegattaPoint?,
    finishB: RegattaPoint?,
    helperGates: List<PendingGateDraft>,
    selectedMapPoint: RegattaPoint?,
    fallbackCenter: RegattaPoint?,
    selectedGateKey: String,
    onMapPointSelected: (RegattaPoint) -> Unit
) {
    var allowAutoCamera by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        delay(2_000L)
        allowAutoCamera = false
    }
    AndroidView(
        modifier = Modifier
            .fillMaxWidth()
            .height(260.dp)
            .clipToBounds(),
        factory = { ctx ->
            org.osmdroid.config.Configuration.getInstance().userAgentValue = ctx.packageName
            MapView(ctx).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                setBuiltInZoomControls(false)
                isTilesScaledToDpi = true
                setUseDataConnection(true)
                controller.setZoom(15.0)
                overlays.add(
                    MapEventsOverlay(
                        object : MapEventsReceiver {
                            override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                                return false
                            }

                            override fun longPressHelper(p: GeoPoint): Boolean {
                                onMapPointSelected(RegattaPoint(p.latitude, p.longitude))
                                return true
                            }
                        }
                    )
                )
            }
        },
        update = { mapView ->
            val tapOverlay = mapView.overlays.firstOrNull()
            mapView.overlays.clear()
            tapOverlay?.let { mapView.overlays.add(it) }
            val bounds = mutableListOf<GeoPoint>()
            fun addLine(name: String, a: RegattaPoint?, b: RegattaPoint?, color: Int, gateKey: String) {
                if (a == null || b == null) return
                val pa = GeoPoint(a.latitude, a.longitude)
                val pb = GeoPoint(b.latitude, b.longitude)
                bounds += pa
                bounds += pb
                val isSelected = gateKey == selectedGateKey
                mapView.overlays.add(
                    Polyline(mapView).apply {
                        outlinePaint.color = color
                        outlinePaint.strokeWidth = if (isSelected) 12f else 7f
                        setPoints(listOf(pa, pb))
                        title = name
                    }
                )
                mapView.overlays.add(
                    Marker(mapView).apply {
                        position = pa
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        icon = createGateBuoyDrawable(android.graphics.Color.RED, if (isSelected) 34 else 22)
                        title = "$name L"
                    }
                )
                mapView.overlays.add(
                    Marker(mapView).apply {
                        position = pb
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        icon = createGateBuoyDrawable(android.graphics.Color.GREEN, if (isSelected) 34 else 22)
                        title = "$name R"
                    }
                )
            }
            addLine("Start", startA, startB, android.graphics.Color.YELLOW, "start")
            helperGates.forEachIndexed { index, gate ->
                addLine(gate.name, gate.pointA, gate.pointB, android.graphics.Color.rgb(66, 165, 245), "gate:${index + 1}")
            }
            addLine("Finish", finishA, finishB, android.graphics.Color.RED, "finish")
            selectedMapPoint?.let { point ->
                val gp = GeoPoint(point.latitude, point.longitude)
                bounds += gp
                mapView.overlays.add(
                    Marker(mapView).apply {
                        position = gp
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        title = "Selected map point"
                    }
                )
            }
            if (allowAutoCamera) {
                when {
                    fallbackCenter != null -> {
                        mapView.controller.setCenter(GeoPoint(fallbackCenter.latitude, fallbackCenter.longitude))
                        mapView.controller.setZoom(15.0)
                    }
                    bounds.isNotEmpty() -> {
                        mapView.controller.setCenter(bounds.first())
                        mapView.controller.setZoom(15.0)
                    }
                }
            }
            mapView.invalidate()
        }
    )
}

@Composable
private fun HistoryResultsMapView(
    modifier: Modifier = Modifier,
    selectedRaceLive: RegattaLiveSnapshot,
    participants: List<RegattaParticipantLive>,
    selectedBoatId: String,
    selectedBoatTrack: List<RegattaBoatTrackPoint>,
    boatsById: Map<String, RegattaBoatSummary> = emptyMap()
) {
    var autoZoomedBoatId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(selectedBoatId) {
        if (selectedBoatId.isBlank()) autoZoomedBoatId = null
    }
    val raceGates = selectedRaceLive.gates
    val visibleParticipants = participants.filter {
        it.lastLatitude != null && it.lastLongitude != null
    }
    val hasMapData = raceGates.isNotEmpty() || visibleParticipants.isNotEmpty() || selectedBoatTrack.isNotEmpty()
    Box(modifier = modifier.clipToBounds()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
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
                val boundsPoints = mutableListOf<GeoPoint>()
                val boatLabels = mutableListOf<BoatLabelSpec>()

                raceGates.forEach { gate ->
                val pointA = GeoPoint(gate.pointA.latitude, gate.pointA.longitude)
                val pointB = GeoPoint(gate.pointB.latitude, gate.pointB.longitude)
                boundsPoints += pointA
                boundsPoints += pointB
                mapView.overlays.add(
                    Polyline(mapView).apply {
                        outlinePaint.color = when {
                            gate.type.equals("start", ignoreCase = true) -> android.graphics.Color.YELLOW
                            gate.type.equals("finish", ignoreCase = true) -> android.graphics.Color.RED
                            else -> android.graphics.Color.rgb(66, 165, 245)
                        }
                        outlinePaint.strokeWidth = 7f
                        setPoints(listOf(pointA, pointB))
                        title = gate.name
                    }
                )
                mapView.overlays.add(
                    Marker(mapView).apply {
                        position = pointA
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        icon = createGateBuoyDrawable(android.graphics.Color.RED)
                        title = "${gate.name} L"
                    }
                )
                mapView.overlays.add(
                    Marker(mapView).apply {
                        position = pointB
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        icon = createGateBuoyDrawable(android.graphics.Color.GREEN)
                        title = "${gate.name} R"
                    }
                )
            }

            var selectedPoint: GeoPoint? = null
            visibleParticipants.forEach { participant ->
                val lat = participant.lastLatitude ?: return@forEach
                val lon = participant.lastLongitude ?: return@forEach
                val point = GeoPoint(lat, lon)
                boundsPoints += point
                val selected = participant.boatId == selectedBoatId
                if (selected) selectedPoint = point
                val boat = boatsById[participant.boatId]
                val lengthFeet = boat?.lengthValue?.let { value ->
                    if (boat.lengthUnit.equals("ft", ignoreCase = true)) value else value / 0.3048
                }
                val skipperLabel = participant.skipperName.ifBlank { "--" }
                val lengthLabel = lengthFeet?.let { String.format(Locale.US, "%.1f ft", it) } ?: "-- ft"
                val speedLabel = participant.lastSpeedKnots?.let {
                    String.format(Locale.US, "%.1f kn", it)
                } ?: "-- kn"
                boatLabels += BoatLabelSpec(
                    point = point,
                    text = "$skipperLabel · $lengthLabel · $speedLabel",
                    highlighted = selected
                )
                mapView.overlays.add(
                    Marker(mapView).apply {
                        position = point
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        icon = createGateBuoyDrawable(
                            color = if (selected) android.graphics.Color.YELLOW else android.graphics.Color.CYAN,
                            sizePx = if (selected) 26 else 20
                        )
                        title = participant.boatName
                        subDescription = participant.lastSpeedKnots?.let {
                            String.format(Locale.US, "%.1f kn", it)
                        } ?: "-- kn"
                    }
                )
            }
            if (boatLabels.isNotEmpty()) {
                mapView.overlays.add(BoatLabelsOverlay(boatLabels))
            }

            val selectedTrackGeo = selectedBoatTrack.map { GeoPoint(it.latitude, it.longitude) }
            if (selectedTrackGeo.size >= 2) {
                boundsPoints += selectedTrackGeo
                mapView.overlays.add(
                    Polyline(mapView).apply {
                        outlinePaint.color = android.graphics.Color.CYAN
                        outlinePaint.strokeWidth = 6f
                        setPoints(selectedTrackGeo)
                        title = "Selected boat track"
                    }
                )
            }
            val selectedParticipant = selectedRaceLive.participants.firstOrNull { it.boatId == selectedBoatId }
            selectedParticipant?.let { participant ->
                val lat = participant.lastLatitude ?: selectedBoatTrack.lastOrNull()?.latitude
                val lon = participant.lastLongitude ?: selectedBoatTrack.lastOrNull()?.longitude
                if (lat != null && lon != null) {
                    val point = GeoPoint(lat, lon)
                    boundsPoints += point
                    mapView.overlays.add(
                        Marker(mapView).apply {
                            position = point
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                            icon = createGateBuoyDrawable(android.graphics.Color.YELLOW, sizePx = 28)
                            title = participant.boatName
                        }
                    )
                }
            }

            val selectedBoatKey = selectedBoatId.trim().takeIf { it.isNotBlank() }
            val shouldAutoZoomSelectedBoat =
                selectedPoint != null && selectedBoatKey != null && autoZoomedBoatId != selectedBoatKey
            when {
                shouldAutoZoomSelectedBoat -> {
                    mapView.controller.setCenter(selectedPoint)
                    mapView.controller.setZoom(18.0)
                    autoZoomedBoatId = selectedBoatKey
                }
                selectedBoatKey == null && boundsPoints.size >= 2 ->
                    mapView.zoomToBoundingBox(BoundingBox.fromGeoPointsSafe(boundsPoints), true, 48)
                selectedBoatKey == null && boundsPoints.size == 1 -> {
                    mapView.controller.setCenter(boundsPoints.first())
                    mapView.controller.setZoom(16.0)
                }
            }
            mapView.invalidate()
            }
        )
        if (!hasMapData) {
            Text(
                text = "No map data yet",
                color = Color(0xFFB0BEC5),
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}

private data class BoatLabelSpec(
    val point: GeoPoint,
    val text: String,
    val highlighted: Boolean
)

private class BoatLabelsOverlay(
    private val labels: List<BoatLabelSpec>
) : Overlay() {
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textSize = 24f
        style = Paint.Style.FILL
    }
    private val selectedTextPaint = Paint(textPaint).apply {
        color = android.graphics.Color.BLACK
    }
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.argb(170, 38, 50, 56)
        style = Paint.Style.FILL
    }
    private val selectedBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.argb(200, 255, 235, 59)
        style = Paint.Style.FILL
    }
    private val pointPx = Point()

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return
        val projection = mapView.projection ?: return
        labels.forEach { label ->
            projection.toPixels(label.point, pointPx)
            val paint = if (label.highlighted) selectedTextPaint else textPaint
            val textWidth = paint.measureText(label.text)
            val textHeight = paint.textSize
            val left = pointPx.x + 16f
            val top = pointPx.y - textHeight - 24f
            val rect = RectF(
                left - 8f,
                top - 6f,
                left + textWidth + 8f,
                top + textHeight + 4f
            )
            canvas.drawRoundRect(
                rect,
                10f,
                10f,
                if (label.highlighted) selectedBgPaint else bgPaint
            )
            canvas.drawText(label.text, left, top + textHeight - 2f, paint)
        }
    }
}

private fun createGateBuoyDrawable(color: Int, sizePx: Int = 22): GradientDrawable {
    return GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color)
        setStroke(2, android.graphics.Color.BLACK)
        setSize(sizePx, sizePx)
    }
}

@Composable
private fun RaceStateButton(
    label: String,
    color: Color,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(containerColor = color)
    ) {
        Text(label)
    }
}

@Composable
private fun CaptureButton(
    label: String,
    onCapture: () -> Unit
) {
    Button(
        onClick = onCapture,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF455A64))
    ) {
        Text(label)
    }
}

@Composable
private fun TimePickerField(
    value: String,
    onValueChange: (String) -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Box(modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = label,
            trailingIcon = { Text("🕒") },
            modifier = Modifier.fillMaxWidth()
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable {
                    val parts = value.trim().split(":")
                    val hour = parts.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 23) ?: 10
                    val minute = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59) ?: 0
                    TimePickerDialog(
                        context,
                        { _, selectedHour, selectedMinute ->
                            onValueChange(
                                String.format(Locale.US, "%02d:%02d", selectedHour, selectedMinute)
                            )
                        },
                        hour,
                        minute,
                        true
                    ).show()
                }
        )
    }
}

@Composable
private fun RegattaSection(
    title: String,
    cardColor: Color,
    modifier: Modifier = Modifier,
    expandContentVertically: Boolean = false,
    content: @Composable () -> Unit
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (expandContentVertically) Modifier.fillMaxHeight() else Modifier)
                .background(cardColor)
                .padding(12.dp)
        ) {
            if (title.isNotBlank()) {
                Text(
                    text = title,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
            }
            content()
        }
    }
}

private fun formatEpoch(epochMillis: Long): String {
    return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(epochMillis))
}

private fun formatEpochTime(epochMillis: Long): String {
    return SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(epochMillis))
}

private fun formatIsoDateTime(value: String?): String {
    if (value.isNullOrBlank()) return "--"
    return runCatching {
        java.time.Instant.parse(value).toEpochMilli()
    }.getOrNull()?.let { epoch ->
        SimpleDateFormat(NOTICE_TIMESTAMP_PATTERN, Locale.US).format(Date(epoch))
    } ?: value
}

private fun formatEpochInput(epochMillis: Long): String {
    return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(epochMillis))
}

private fun parseEpochInput(value: String): Long? {
    return runCatching {
        val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply { isLenient = false }
        format.parse(value.trim())?.time
    }.getOrNull()
}

private fun formatDurationFromMillis(durationMs: Long): String {
    val safe = durationMs.coerceAtLeast(0L)
    val totalSeconds = safe / 1000L
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}

private fun formatPoint(point: RegattaPoint): String {
    return String.format(Locale.US, "%.6f, %.6f", point.latitude, point.longitude)
}

private fun formatLocation(location: Location): String {
    return String.format(Locale.US, "%.6f, %.6f", location.latitude, location.longitude)
}

private fun normalizeGroupLabel(groupCode: String?): String {
    return groupCode?.ifBlank { "UNGROUPED" } ?: "UNGROUPED"
}

private data class RaceScoreRow(
    val boatId: String,
    val boatName: String,
    val skipperName: String,
    val groupCode: String,
    val status: String,
    val completedGates: Int,
    val realElapsedMs: Long?,
    val correctedElapsedMs: Long?,
    val penaltyPercent: Double,
    val racePoints: Double
)

private data class EventScoreRow(
    val boatId: String,
    val boatName: String,
    val skipperName: String,
    val groupCode: String,
    val totalPoints: Double,
    val racePoints: List<Double?>
)

private data class RaceGateTimelineRow(
    val boatId: String,
    val boatName: String,
    val groupCode: String,
    val gateTimes: List<Long?>,
    val correctedElapsedMs: Long?
)

private fun computeRaceScoreRows(
    live: RegattaLiveSnapshot,
    boats: List<RegattaBoatSummary>
): List<RaceScoreRow> {
    val boatMap = boats.associateBy { it.id }
    val t0 = live.countdownTargetEpochMs
    val scoringTargetGateId = live.scoringTargetGateId
    val dnfPoints = live.participants.size.coerceAtLeast(1).toDouble()
    data class Working(
        val participant: RegattaParticipantLive,
        val completedGates: Int,
        val penaltyPercent: Double,
        val correctedElapsedMs: Long?,
        val racePoints: Double,
        val targetCrossingEpochMs: Long?
    )
    val baseWorking = live.participants.map { participant ->
        val p = live.penalties.filter { it.boatId == participant.boatId }
        val penaltyPercent = p.sumOf {
            if (it.type.contains("percent", ignoreCase = true)) {
                it.value ?: 0.0
            } else {
                0.0
            }
        }
        val completedGates = live.crossings.count { it.boatId == participant.boatId }
        val targetCrossing = live.crossings
            .filter { it.boatId == participant.boatId && it.gateId == scoringTargetGateId }
            .minOfOrNull { it.crossingEpochMs }
        val realElapsed = if (targetCrossing != null && t0 != null) (targetCrossing - t0).coerceAtLeast(0L) else null
        val correctedElapsed = realElapsed?.let { base ->
            (base * (1.0 + penaltyPercent / 100.0)).toLong()
        }
        Working(
            participant = participant,
            completedGates = completedGates,
            penaltyPercent = penaltyPercent,
            correctedElapsedMs = correctedElapsed,
            racePoints = Double.NaN,
            targetCrossingEpochMs = targetCrossing
        )
    }
    val byGroup = baseWorking.groupBy { it.participant.groupCode?.ifBlank { "UNGROUPED" } ?: "UNGROUPED" }
    val ranked = byGroup.values.flatMap { groupRows ->
        val ordered = groupRows.sortedWith(
            compareBy<Working>(
                { it.correctedElapsedMs == null },
                { it.correctedElapsedMs ?: Long.MAX_VALUE },
                { it.participant.boatName.lowercase(Locale.US) }
            )
        )
        var completedRank = 0
        ordered.map { row ->
            val points = if (row.correctedElapsedMs != null) {
                completedRank += 1
                if (completedRank == 1) 0.75 else completedRank.toDouble()
            } else {
                dnfPoints
            }
            row.copy(racePoints = points)
        }
    }
    val ordered = ranked.sortedWith(
        compareBy<Working>(
            { it.racePoints },
            { it.correctedElapsedMs ?: Long.MAX_VALUE },
            { it.participant.groupCode.orEmpty() },
            { it.participant.boatName.lowercase(Locale.US) }
        )
    )
    return ordered.map { item ->
        val boat = boatMap[item.participant.boatId]
        val isDnf = item.targetCrossingEpochMs == null
        RaceScoreRow(
            boatId = item.participant.boatId,
            boatName = item.participant.boatName,
            skipperName = item.participant.skipperName.ifBlank { boat?.skipperName.orEmpty() },
            groupCode = item.participant.groupCode ?: boat?.groupCode.orEmpty(),
            status = if (isDnf) "DNF" else item.participant.status,
            completedGates = item.completedGates,
            realElapsedMs = item.targetCrossingEpochMs?.let { target ->
                t0?.let { (target - it).coerceAtLeast(0L) }
            },
            correctedElapsedMs = item.correctedElapsedMs,
            penaltyPercent = item.penaltyPercent,
            racePoints = item.racePoints
        )
    }
}

private fun computeRaceGateTimelineRows(
    live: RegattaLiveSnapshot,
    boats: List<RegattaBoatSummary>
): Pair<List<String>, List<RaceGateTimelineRow>> {
    val targetGateId = live.scoringTargetGateId
    val orderedGates = live.gates
        .filterNot { it.type.equals("start", ignoreCase = true) }
        .sortedBy { it.order }
    val headers = orderedGates.map { gate ->
        when {
            gate.type.equals("finish", ignoreCase = true) -> "Finish"
            gate.name.isNotBlank() -> gate.name
            else -> "Gate ${gate.order}"
        }
    }
    val boatMap = boats.associateBy { it.id }
    val rows = live.participants.map { participant ->
        val boat = boatMap[participant.boatId]
        val group = participant.groupCode?.ifBlank { "UNGROUPED" }
            ?: boat?.groupCode?.ifBlank { "UNGROUPED" }
            ?: "UNGROUPED"
        val relevantCrossings = live.crossings
            .filter { crossing ->
                crossing.boatId == participant.boatId &&
                    !crossing.status.equals("invalid", ignoreCase = true)
            }
        val gateTimes = orderedGates.map { gate ->
            relevantCrossings
                .filter { it.gateId == gate.id }
                .minOfOrNull { it.crossingEpochMs }
        }
        val penaltyPercent = live.penalties
            .filter { it.boatId == participant.boatId }
            .sumOf { penalty ->
                if (penalty.type.contains("percent", ignoreCase = true)) penalty.value ?: 0.0 else 0.0
            }
        val targetCrossing = if (targetGateId.isNullOrBlank()) {
            null
        } else {
            relevantCrossings
                .filter { it.gateId == targetGateId }
                .minOfOrNull { it.crossingEpochMs }
        }
        val realElapsed = if (targetCrossing != null && live.countdownTargetEpochMs != null) {
            (targetCrossing - live.countdownTargetEpochMs).coerceAtLeast(0L)
        } else {
            null
        }
        val correctedElapsed = realElapsed?.let { base ->
            (base * (1.0 + penaltyPercent / 100.0)).toLong()
        }
        RaceGateTimelineRow(
            boatId = participant.boatId,
            boatName = participant.boatName,
            groupCode = group,
            gateTimes = gateTimes,
            correctedElapsedMs = correctedElapsed
        )
    }.sortedWith(
        compareBy<RaceGateTimelineRow>(
            { it.groupCode },
            { it.correctedElapsedMs == null },
            { it.correctedElapsedMs ?: Long.MAX_VALUE },
            { it.boatName.lowercase(Locale.US) }
        )
    )
    return headers to rows
}

private fun computeEventScoreTable(
    event: RegattaEventSnapshot,
    raceSnapshots: List<RegattaLiveSnapshot>,
    raceIds: List<String>? = null
): List<EventScoreRow> {
    val selectedRaceIds = raceIds ?: event.races.map { it.id }
    val raceRowsByRaceId = raceSnapshots.associate { live ->
        live.raceId to computeRaceScoreRows(live, event.boats)
    }
    return event.boats.map { boat ->
        val perRace = selectedRaceIds.map { raceId ->
            raceRowsByRaceId[raceId]?.firstOrNull { it.boatId == boat.id }?.racePoints
        }
        EventScoreRow(
            boatId = boat.id,
            boatName = boat.boatName,
            skipperName = boat.skipperName,
            groupCode = boat.groupCode.orEmpty(),
            totalPoints = perRace.filterNotNull().sum(),
            racePoints = perRace
        )
    }.sortedBy { it.totalPoints }
}

private fun buildRaceScoringCsv(
    live: RegattaLiveSnapshot,
    rows: List<RaceScoreRow>
): String {
    val lines = mutableListOf<String>()
    lines += listOf(
        "rank",
        "boat_name",
        "skipper_name",
        "group_code",
        "status",
        "completed_gates",
        "real_time",
        "corrected_time",
        "penalty_percent",
        "race_points"
    ).joinToString(",")
    rows.forEachIndexed { index, row ->
        lines += listOf(
            (index + 1).toString(),
            csvCell(row.boatName),
            csvCell(row.skipperName),
            csvCell(row.groupCode),
            csvCell(row.status),
            row.completedGates.toString(),
            if (row.status.equals("DNF", ignoreCase = true)) "DNF" else row.realElapsedMs?.let(::formatDurationFromMillis).orEmpty(),
            if (row.status.equals("DNF", ignoreCase = true)) "DNF" else row.correctedElapsedMs?.let(::formatDurationFromMillis).orEmpty(),
            row.penaltyPercent.toString(),
            row.racePoints.toString()
        ).joinToString(",")
    }
    return lines.joinToString("\n")
}

private fun buildEventScoringCsv(
    event: RegattaEventSnapshot,
    rows: List<EventScoreRow>
): String {
    val headers = mutableListOf("rank", "boat_name", "skipper_name", "group_code")
    headers += event.races.map { sanitizeCsvHeader(it.name.ifBlank { "Race ${it.sequenceNumber}" }) }
    headers += "total_points"
    val lines = mutableListOf(headers.joinToString(","))
    rows.forEachIndexed { index, row ->
        val values = mutableListOf(
            (index + 1).toString(),
            csvCell(row.boatName),
            csvCell(row.skipperName),
            csvCell(row.groupCode)
        )
        values += row.racePoints.map { it?.toString().orEmpty() }
        values += row.totalPoints.toString()
        lines += values.joinToString(",")
    }
    return lines.joinToString("\n")
}

private fun buildCombinedResultsCsv(
    regattaName: String,
    regattaStartDate: String?,
    regattaEndDate: String?,
    raceName: String,
    raceDate: String?,
    raceStartTime: String?,
    raceEndTime: String?,
    raceRowsByGroup: Map<String, List<RaceScoreRow>>,
    regattaRowsByGroup: Map<String, List<EventScoreRow>>,
    selectedRaceIds: List<String>
): String {
    val lines = mutableListOf<String>()
    lines += listOf(
        csvCell("regatta_name"),
        csvCell(regattaName.ifBlank { "--" }),
        csvCell("from"),
        csvCell(regattaStartDate?.ifBlank { "--" } ?: "--"),
        csvCell("to"),
        csvCell(regattaEndDate?.ifBlank { "--" } ?: "--")
    ).joinToString(",")
    lines += listOf(
        csvCell("race_name"),
        csvCell(raceName.ifBlank { "--" }),
        csvCell("race_date"),
        csvCell(raceDate?.ifBlank { "--" } ?: "--"),
        csvCell("start"),
        csvCell(raceStartTime?.ifBlank { "--" } ?: "--"),
        csvCell("finish"),
        csvCell(raceEndTime?.ifBlank { "--" } ?: "--")
    ).joinToString(",")
    lines += ""
    lines += "RACE RESULTS"
    lines += "group,group_rank,boat_name,elapsed_time,penalty_percent,corrected_time,points"
    raceRowsByGroup.forEach { (groupName, rows) ->
        val ordered = rows.sortedWith(
            compareBy<RaceScoreRow>(
                { it.racePoints },
                { it.correctedElapsedMs ?: Long.MAX_VALUE },
                { it.boatName.lowercase(Locale.US) }
            )
        )
        ordered.forEachIndexed { index, row ->
            lines += listOf(
                csvCell(groupName),
                (index + 1).toString(),
                csvCell(row.boatName),
                csvCell(if (row.status.equals("DNF", ignoreCase = true)) "DNF" else row.realElapsedMs?.let(::formatDurationFromMillis).orEmpty()),
                String.format(Locale.US, "%.1f", row.penaltyPercent),
                csvCell(if (row.status.equals("DNF", ignoreCase = true)) "DNF" else row.correctedElapsedMs?.let(::formatDurationFromMillis).orEmpty()),
                String.format(Locale.US, "%.2f", row.racePoints)
            ).joinToString(",")
        }
    }
    lines += ""
    lines += "REGATTA STANDINGS"
    val raceHeaders = selectedRaceIds.indices.map { "R${it + 1}" }
    lines += (listOf("group", "group_rank", "boat_name") + raceHeaders + listOf("total_points")).joinToString(",")
    regattaRowsByGroup.forEach { (groupName, rows) ->
        val ordered = rows.sortedWith(compareBy<EventScoreRow>({ it.totalPoints }, { it.boatName.lowercase(Locale.US) }))
        ordered.forEachIndexed { index, row ->
            lines += (
                listOf(
                    csvCell(groupName),
                    (index + 1).toString(),
                    csvCell(row.boatName)
                ) + row.racePoints.map { it?.let { p -> String.format(Locale.US, "%.2f", p) }.orEmpty() } +
                    listOf(String.format(Locale.US, "%.2f", row.totalPoints))
                ).joinToString(",")
        }
    }
    return lines.joinToString("\n")
}

private fun buildCombinedResultsCsvFallback(
    regattaName: String,
    regattaStartDate: String?,
    regattaEndDate: String?,
    raceName: String,
    raceDate: String?,
    raceStartTime: String?,
    raceEndTime: String?,
    raceRowsByGroup: Map<String, List<RaceScoreRow>>,
    regattaRowsByGroup: Map<String, List<EventScoreRow>>,
    selectedRaceIds: List<String>
): String {
    val lines = mutableListOf<String>()
    lines += listOf(
        csvCell("regatta_name"),
        csvCell(regattaName.ifBlank { "--" }),
        csvCell("from"),
        csvCell(regattaStartDate?.ifBlank { "--" } ?: "--"),
        csvCell("to"),
        csvCell(regattaEndDate?.ifBlank { "--" } ?: "--")
    ).joinToString(",")
    lines += listOf(
        csvCell("race_name"),
        csvCell(raceName.ifBlank { "--" }),
        csvCell("race_date"),
        csvCell(raceDate?.ifBlank { "--" } ?: "--"),
        csvCell("start"),
        csvCell(raceStartTime?.ifBlank { "--" } ?: "--"),
        csvCell("finish"),
        csvCell(raceEndTime?.ifBlank { "--" } ?: "--")
    ).joinToString(",")
    lines += ""
    lines += "RACE RESULTS"
    lines += "group,group_rank,boat_name,elapsed_time,penalty_percent,corrected_time,points"
    raceRowsByGroup.forEach { (groupName, rows) ->
        val ordered = rows.sortedBy { it.boatName.lowercase(Locale.US) }
        ordered.forEachIndexed { index, row ->
            lines += listOf(
                csvCell(groupName),
                (index + 1).toString(),
                csvCell(row.boatName),
                "",
                "",
                "",
                ""
            ).joinToString(",")
        }
    }
    lines += ""
    lines += "REGATTA STANDINGS"
    val raceHeaders = selectedRaceIds.indices.map { "R${it + 1}" }
    lines += (listOf("group", "group_rank", "boat_name") + raceHeaders + listOf("total_points")).joinToString(",")
    regattaRowsByGroup.forEach { (groupName, rows) ->
        val ordered = rows.sortedBy { it.boatName.lowercase(Locale.US) }
        ordered.forEachIndexed { index, row ->
            lines += (
                listOf(
                    csvCell(groupName),
                    (index + 1).toString(),
                    csvCell(row.boatName)
                ) + selectedRaceIds.map { "" } +
                    listOf("")
                ).joinToString(",")
        }
    }
    return lines.joinToString("\n")
}

private fun writeCsvFile(
    context: Context,
    fileName: String,
    csvContent: String
): File {
    val dir = File(context.cacheDir, "regatta_exports").apply { mkdirs() }
    val file = File(dir, fileName)
    file.writeText(csvContent)
    return file
}

private fun shareCsvFile(context: Context, csvFile: File, title: String) {
    val authority = "${context.packageName}.fileprovider"
    runCatching {
        val uri = runCatching {
            FileProvider.getUriForFile(context, authority, csvFile)
        }.getOrElse {
            Uri.fromFile(csvFile)
        }
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, title))
    }
}

private fun csvCell(value: String): String {
    val escaped = value.replace("\"", "\"\"")
    return "\"$escaped\""
}

private fun sanitizeFileName(value: String): String {
    return value.lowercase(Locale.US).replace(Regex("[^a-z0-9]+"), "_").trim('_').ifBlank { "export" }
}

private fun sanitizeCsvHeader(value: String): String {
    return value.lowercase(Locale.US).replace(Regex("[^a-z0-9]+"), "_").trim('_')
}
