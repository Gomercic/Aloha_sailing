package com.aloha.startline.regatta

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.location.Location
import android.net.Uri
import android.graphics.drawable.GradientDrawable
import android.view.MotionEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
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
import org.osmdroid.views.overlay.Polyline
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.Calendar

@Composable
fun RegattaScreen(
    apiClient: RegattaApiClient,
    prefs: RegattaPreferences,
    currentLocation: Location?,
    onSessionStateChanged: (Boolean) -> Unit = {},
    onOpenRaceStartLine: () -> Unit = {},
    onJoinModeChanged: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var mode by rememberSaveable { mutableStateOf("home") }
    var organizerName by rememberSaveable { mutableStateOf(prefs.organizerName) }
    var eventName by rememberSaveable { mutableStateOf("") }
    var joinCode by rememberSaveable { mutableStateOf(prefs.lastJoinCode) }
    var organizerCode by rememberSaveable { mutableStateOf(prefs.lastOrganizerAccessValue) }
    var regattaStartDate by rememberSaveable { mutableStateOf("") }
    var regattaEndDate by rememberSaveable { mutableStateOf("") }
    var regattaRaceEndTime by rememberSaveable { mutableStateOf("18:00") }
    var regattaLengthNmInput by rememberSaveable { mutableStateOf("") }
    var maxBoatsInput by rememberSaveable { mutableStateOf("50") }
    var quickAccessInput by rememberSaveable {
        mutableStateOf(
            prefs.lastOrganizerAccessValue.ifBlank { prefs.lastJoinCode }
        )
    }
    var quickAccessMode by rememberSaveable { mutableStateOf("join") }
    var isPublicEvent by rememberSaveable { mutableStateOf(false) }
    var revealOrganizerCode by rememberSaveable { mutableStateOf("") }
    var boatName by rememberSaveable { mutableStateOf("") }
    var skipperName by rememberSaveable { mutableStateOf("") }
    var clubName by rememberSaveable { mutableStateOf("") }
    var lengthValue by rememberSaveable { mutableStateOf("") }
    var lengthUnit by rememberSaveable { mutableStateOf("m") }
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
    var organizerLoginAskedCreateNew by remember { mutableStateOf(false) }
    var organizerRaceDateInput by rememberSaveable { mutableStateOf("") }
    var organizerRaceStartTimeInput by rememberSaveable { mutableStateOf("10:00") }
    var organizerRaceEndTimeInput by rememberSaveable { mutableStateOf("16:00") }
    var organizerRaceLengthNmInput by rememberSaveable { mutableStateOf("") }
    var organizerGateSelection by rememberSaveable { mutableStateOf("start") }
    var organizerSelectedMapPoint by remember { mutableStateOf<RegattaPoint?>(null) }
    var organizerGateMenuExpanded by remember { mutableStateOf(false) }
    var nextRaceName by rememberSaveable { mutableStateOf("") }
    var countdownMinutes by rememberSaveable { mutableStateOf("5") }
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
    val penaltyTypeInputs = remember { mutableStateMapOf<String, String>() }
    val penaltyValueInputs = remember { mutableStateMapOf<String, String>() }
    val penaltyReasonInputs = remember { mutableStateMapOf<String, String>() }
    val background = Color.Black
    val cardColor = Color(0xFF111111)
    val actionGreen = Color(0xFF2E7D32)
    val actionBlue = Color(0xFF1565C0)
    val actionOrange = Color(0xFFEF6C00)
    val muted = Color(0xFFBDBDBD)

    fun handleJoinRegattaNotFound() {
        errorMessage = "Regata ne postoji."
    }

    fun handleOrganizerRegattaNotFound() {
        errorMessage = "Regata ne postoji. Želiš li stvoriti novu regatu?"
        mode = "new"
    }

    fun snapshotPoint(): RegattaPoint? {
        val location = currentLocation ?: return null
        return RegattaPoint(location.latitude, location.longitude)
    }

    var organizerToken by rememberSaveable { mutableStateOf(prefs.organizerToken) }
    var organizerEventIds by remember { mutableStateOf(prefs.organizerEventIds) }
    var organizerEvents by remember { mutableStateOf<List<RegattaEventSnapshot>>(emptyList()) }
    var organizerEventsLoading by remember { mutableStateOf(false) }
    val isOrganizer = organizerToken.isNotBlank()

    LaunchedEffect(joinedEventId, joinedBoatId, organizerToken) {
        onSessionStateChanged(
            joinedEventId.isNotBlank() || joinedBoatId.isNotBlank() || organizerToken.isNotBlank()
        )
    }
    LaunchedEffect(joinedEventId, joinedBoatId) {
        onJoinModeChanged(joinedEventId.isNotBlank() && joinedBoatId.isNotBlank())
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
            if (!penaltyTypeInputs.containsKey(participant.boatId)) {
                penaltyTypeInputs[participant.boatId] = "percent"
            }
            if (!penaltyValueInputs.containsKey(participant.boatId)) {
                penaltyValueInputs[participant.boatId] = "0"
            }
            if (!penaltyReasonInputs.containsKey(participant.boatId)) {
                penaltyReasonInputs[participant.boatId] = ""
            }
        }
    }
    LaunchedEffect(mode, organizerEventIds) {
        if (mode != "organizer") return@LaunchedEffect
        if (organizerEventIds.isEmpty()) {
            organizerEvents = emptyList()
            organizerEventsLoading = false
            return@LaunchedEffect
        }
        organizerEventsLoading = true
        val snapshots = withContext(Dispatchers.IO) {
            organizerEventIds.mapNotNull { eventId ->
                when (val result = apiClient.getEventSnapshot(eventId)) {
                    is NasCallResult.Ok -> result.value
                    is NasCallResult.Err -> null
                }
            }
        }
        organizerEvents = snapshots.sortedBy { it.name.lowercase(Locale.US) }
        organizerEventsLoading = false
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Regatta",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Regata / race sync je odvojen od Start Line i Anchoring.",
                color = muted
            )
            Text(
                text = "GPS: ${currentLocation?.let(::formatLocation) ?: "No position yet"}",
                color = muted,
                fontSize = 12.sp
            )
            Button(
                onClick = { mode = "home" },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF616161))
            ) {
                Text("Cancel / Regatta Home")
            }

            statusMessage?.let { Text(text = it, color = Color(0xFFB8E0D0)) }
            errorMessage?.let { Text(text = it, color = Color(0xFFFF8A80)) }

            if (mode == "home") {
                RegattaSection(title = "Quick Access", cardColor = cardColor) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { quickAccessMode = "join" },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (quickAccessMode == "join") actionBlue else Color(0xFF424242)
                            )
                        ) { Text("Join Regata") }
                        Button(
                            onClick = { quickAccessMode = "organize" },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (quickAccessMode == "organize") actionOrange else Color(0xFF424242)
                            )
                        ) { Text("Organize Regata") }
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = quickAccessInput,
                        onValueChange = { quickAccessInput = it.uppercase(Locale.US) },
                        label = {
                            Text(
                                if (quickAccessMode == "join") {
                                    "Join code"
                                } else {
                                    "Organizer token / code"
                                }
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        enabled = !isBusy,
                        onClick = {
                            val input = quickAccessInput.trim()
                            if (input.isBlank()) {
                                errorMessage = "Unesi vrijednost."
                                return@Button
                            }
                            isBusy = true
                            errorMessage = null
                            statusMessage = null
                            scope.launch {
                                if (quickAccessMode == "join") {
                                    val normalizedJoinCode = input.uppercase(Locale.US)
                                    prefs.saveLastJoinCode(normalizedJoinCode)
                                    joinCode = normalizedJoinCode
                                    when (withContext(Dispatchers.IO) { apiClient.getEventByJoinCode(normalizedJoinCode) }) {
                                        is NasCallResult.Ok -> {
                                            mode = "join"
                                            statusMessage = "Regata pronađena. Dovrši join podatke."
                                        }
                                        is NasCallResult.Err -> handleJoinRegattaNotFound()
                                    }
                                } else {
                                    prefs.saveLastOrganizerAccessValue(input)
                                    organizerCode = input
                                    val mappedEventId = prefs.eventIdForOrganizerToken(input)
                                    if (!mappedEventId.isNullOrBlank()) {
                                        val snapshotResult = withContext(Dispatchers.IO) {
                                            apiClient.getEventSnapshot(mappedEventId)
                                        }
                                        joinedEventId = mappedEventId
                                        organizerToken = input
                                        if (snapshotResult is NasCallResult.Ok) {
                                            eventSnapshot = snapshotResult.value
                                            joinedRaceId = snapshotResult.value.activeRaceId.orEmpty()
                                        }
                                        prefs.saveJoinedEvent(joinedEventId, joinedRaceId, joinedBoatId)
                                        statusMessage = "Organizer access restored from saved token."
                                        mode = "dashboard"
                                    } else {
                                        val normalizedJoinCode = input.uppercase(Locale.US)
                                        when (withContext(Dispatchers.IO) { apiClient.getEventByJoinCode(normalizedJoinCode) }) {
                                            is NasCallResult.Ok -> {
                                                joinCode = normalizedJoinCode
                                                mode = "organizer"
                                                statusMessage = "Regata pronađena. Unesi organizer kod."
                                            }
                                            is NasCallResult.Err -> handleOrganizerRegattaNotFound()
                                        }
                                    }
                                }
                                isBusy = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (quickAccessMode == "join") actionBlue else actionOrange
                        )
                    ) {
                        Text(if (isBusy) "Working..." else "Continue")
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { mode = "join" },
                        colors = ButtonDefaults.buttonColors(containerColor = actionBlue)
                    ) { Text("Join Regata") }
                    Button(
                        onClick = { mode = "organizer" },
                        colors = ButtonDefaults.buttonColors(containerColor = actionOrange)
                    ) { Text("Organizer Login") }
                }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
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
                                    mode = "history"
                                }
                                is NasCallResult.Err -> errorMessage = result.message
                            }
                            publicHistoryLoading = false
                        }
                    },
                    enabled = !publicHistoryLoading,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF424242))
                ) {
                    Text(if (publicHistoryLoading) "Loading..." else "Regatta log ili history")
                }
            }

            if (mode == "history") {
                RegattaSection(title = "Public Regatta History", cardColor = cardColor) {
                    if (publicHistory.isEmpty()) {
                        Text("Nema public regata.", color = muted)
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
                                    Text("Race/plovova: ${regata.racesCount}", color = Color(0xFFCFD8DC), fontSize = 12.sp)
                                    Text(
                                        "Datumi regate: ${regata.startDate ?: "--"} -> ${regata.endDate ?: "--"}",
                                        color = Color(0xFFCFD8DC),
                                        fontSize = 12.sp
                                    )
                                    if (!regata.raceEndTime.isNullOrBlank()) {
                                        Text(
                                            "Kraj race: ${regata.raceEndTime}",
                                            color = Color(0xFFCFD8DC),
                                            fontSize = 12.sp
                                        )
                                    }
                                    Text(
                                        "Duljina regate: ${String.format(Locale.US, "%.2f", regata.regattaLengthNm)} NM",
                                        color = Color(0xFFCFD8DC),
                                        fontSize = 12.sp
                                    )
                                    Text("Bodova: ${regata.pointsCount}", color = Color(0xFFCFD8DC), fontSize = 12.sp)
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
                RegattaSection(title = "Race/plovovi regate", cardColor = cardColor) {
                    if (selectedEvent == null) {
                        Text("Regata nije učitana.", color = muted)
                    } else {
                        Text(selectedEvent.name, color = Color.White, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = { mode = "history" },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF424242))
                        ) { Text("Back to history") }
                        Spacer(Modifier.height(10.dp))
                        if (selectedEvent.races.isEmpty()) {
                            Text("Nema race/plovova.", color = muted)
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
                                            race.name.ifBlank { "Race/plov ${race.sequenceNumber}" },
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
                        Text("Results nisu dostupni.", color = muted)
                    } else {
                        val raceRows = computeRaceScoreRows(raceLive, selectedEvent.boats)
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
                            selectedRace.name.ifBlank { "Race/plov ${selectedRace.sequenceNumber}" },
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
                        Text("Tablica plova", color = Color.White, fontWeight = FontWeight.Bold)
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF263238))
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Brod", color = Color.White, fontSize = 11.sp, modifier = Modifier.weight(2f))
                                Text("Grupa", color = Color.White, fontSize = 11.sp, modifier = Modifier.weight(1f))
                                Text("Jedreno", color = Color.White, fontSize = 11.sp, modifier = Modifier.weight(1f))
                                Text("Penali", color = Color.White, fontSize = 11.sp, modifier = Modifier.weight(1f))
                                Text("Korig.", color = Color.White, fontSize = 11.sp, modifier = Modifier.weight(1f))
                                Text("Bodovi", color = Color.White, fontSize = 11.sp, modifier = Modifier.weight(1f))
                                Text("Mj.", color = Color.White, fontSize = 11.sp, modifier = Modifier.weight(0.7f))
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        if (raceRows.isEmpty()) {
                            Text("Nema plovova.", color = muted)
                        } else {
                            raceRowsByGroup.forEach { (groupName, rowsInGroup) ->
                                Text("Grupa: $groupName", color = Color(0xFFFFF59D), fontWeight = FontWeight.SemiBold)
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
                                                Text(row.realElapsedMs?.let(::formatDurationFromMillis) ?: "--", color = Color.White, fontSize = 11.sp, modifier = Modifier.weight(1f))
                                                Text(String.format(Locale.US, "%.1f%%", row.penaltyPercent), color = Color.White, fontSize = 11.sp, modifier = Modifier.weight(1f))
                                                Text(row.correctedElapsedMs?.let(::formatDurationFromMillis) ?: "--", color = Color.White, fontSize = 11.sp, modifier = Modifier.weight(1f))
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
                        Text("Tablica regate", color = Color.White, fontWeight = FontWeight.Bold)
                        val raceHeader = selectedRaceIds.indices.joinToString(" | ") { "R${it + 1}" }
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF263238))
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Brod", color = Color.White, fontSize = 11.sp, modifier = Modifier.weight(2f))
                                Text("Grupa", color = Color.White, fontSize = 11.sp, modifier = Modifier.weight(1f))
                                Text(raceHeader, color = Color.White, fontSize = 11.sp, modifier = Modifier.weight(2f))
                                Text("Zbroj", color = Color.White, fontSize = 11.sp, modifier = Modifier.weight(1f))
                                Text("Mj.", color = Color.White, fontSize = 11.sp, modifier = Modifier.weight(0.7f))
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        if (regattaRows.isEmpty()) {
                            Text("Nema podataka za tablicu regate.", color = muted)
                        } else {
                            regattaRowsByGroup.forEach { (groupName, rowsInGroup) ->
                                Text("Grupa: $groupName", color = Color(0xFFFFF59D), fontWeight = FontWeight.SemiBold)
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
                                val combinedCsv = buildCombinedResultsCsv(
                                    raceRowsByGroup = raceRowsByGroup,
                                    regattaRowsByGroup = regattaRowsByGroup,
                                    selectedRaceIds = selectedRaceIds
                                )
                                val file = writeCsvFile(
                                    context = context,
                                    fileName = "results_${sanitizeFileName(selectedEvent.name)}_${sanitizeFileName(selectedRace.name)}.csv",
                                    csvContent = combinedCsv
                                )
                                shareCsvFile(context, file, "Share results CSV")
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = actionGreen)
                        ) { Text("Share CSV (obje tablice)") }
                    }
                }
            }

            if (mode == "history_result") {
                val row = historySelectedBoatResult
                val selectedRace = historySelectedRace
                RegattaSection(title = "Result details", cardColor = cardColor) {
                    if (row == null) {
                        Text("Result nije odabran.", color = muted)
                    } else {
                        Text(row.boatName, color = Color.White, fontWeight = FontWeight.Bold)
                        Text("Race/plov: ${selectedRace?.name ?: "--"}", color = Color(0xFFCFD8DC), fontSize = 12.sp)
                        Spacer(Modifier.height(8.dp))
                        Text("Skipper: ${row.skipperName.ifBlank { "--" }}", color = Color.White, fontSize = 13.sp)
                        Text("Group: ${row.groupCode.ifBlank { "--" }}", color = Color.White, fontSize = 13.sp)
                        Text("Status: ${row.status}", color = Color.White, fontSize = 13.sp)
                        Text("Completed gates: ${row.completedGates}", color = Color.White, fontSize = 13.sp)
                        Text("Real time: ${row.realElapsedMs?.let(::formatDurationFromMillis) ?: "--"}", color = Color.White, fontSize = 13.sp)
                        Text(
                            "Corrected time: ${row.correctedElapsedMs?.let(::formatDurationFromMillis) ?: "--"}",
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
                LaunchedEffect(selectedRace?.id, selectedBoatId) {
                    if (selectedRace == null || selectedBoatId.isBlank()) {
                        historyMapBoatTrack = emptyList()
                        return@LaunchedEffect
                    }
                    when (val trackResult = withContext(Dispatchers.IO) {
                        apiClient.getBoatTrack(selectedRace.id, selectedBoatId)
                    }) {
                        is NasCallResult.Ok -> historyMapBoatTrack = trackResult.value
                        is NasCallResult.Err -> {
                            historyMapBoatTrack = emptyList()
                            errorMessage = trackResult.message
                        }
                    }
                }
                RegattaSection(title = "Map", cardColor = cardColor) {
                    if (selectedRace == null || raceLive == null) {
                        Text("Map podaci nisu dostupni.", color = muted)
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { mode = "history_race" },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF424242))
                            ) { Text("Back to results") }
                            Button(
                                onClick = { historyMapBoatMenuExpanded = true },
                                colors = ButtonDefaults.buttonColors(containerColor = actionBlue)
                            ) {
                                val label = participants.firstOrNull { it.boatId == selectedBoatId }?.boatName
                                    ?: "Odaberi brod"
                                Text(label)
                            }
                            DropdownMenu(
                                expanded = historyMapBoatMenuExpanded,
                                onDismissRequest = { historyMapBoatMenuExpanded = false }
                            ) {
                                participants.forEach { participant ->
                                    DropdownMenuItem(
                                        text = { Text(participant.boatName) },
                                        onClick = {
                                            historyMapSelectedBoatId = participant.boatId
                                            historyMapBoatMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        HistoryResultsMapView(
                            selectedRaceLive = raceLive,
                            selectedBoatId = selectedBoatId,
                            selectedBoatTrack = historyMapBoatTrack
                        )
                    }
                }
            }

            if (mode == "new") {
                RegattaSection(title = "Create Event", cardColor = cardColor) {
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
                        label = { Text("Event name") },
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
                        value = regattaRaceEndTime,
                        onValueChange = { regattaRaceEndTime = it },
                        label = { Text("Race end time (HH:MM)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = regattaLengthNmInput,
                        onValueChange = { regattaLengthNmInput = it },
                        label = { Text("Regatta length (NM)") },
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
                                                raceEndTime = regattaRaceEndTime,
                                                regattaLengthNm = regattaLengthNmInput.toDoubleOrNull() ?: 0.0,
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
                                        nextRaceName = "Race 2"
                                        organizerToken = result.value.organizerToken.orEmpty()
                                        prefs.saveOrganizerSession(joinedEventId, organizerToken)
                                        organizerEventIds = prefs.organizerEventIds
                                        revealOrganizerCode = result.value.organizerCode.orEmpty()
                                        prefs.saveJoinedEvent(joinedEventId, joinedRaceId, joinedBoatId)
                                        statusMessage = "Event created. Day 1 / Race 1 is ready."
                                        mode = "dashboard"
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

            if (mode == "organizer") {
                RegattaSection(title = "Organizer", cardColor = cardColor) {
                    val hasActiveOrganizerRegatta = organizerToken.isNotBlank() && joinedEventId.isNotBlank()
                    if (!hasActiveOrganizerRegatta) {
                        Text("Unesi hash regate pa Organizer login otvara regatu.", color = muted)
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = quickAccessInput,
                            onValueChange = { quickAccessInput = it.uppercase(Locale.US) },
                            label = { Text("Join code / hash regate") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(
                            enabled = !isBusy,
                            onClick = {
                                val input = quickAccessInput.trim().uppercase(Locale.US)
                                if (input.isBlank()) {
                                    organizerLoginAskedCreateNew = true
                                    return@Button
                                }
                                isBusy = true
                                errorMessage = null
                                statusMessage = null
                                scope.launch {
                                    when (val lookupResult = withContext(Dispatchers.IO) {
                                        apiClient.getEventByJoinCode(input)
                                    }) {
                                        is NasCallResult.Ok -> {
                                            joinCode = input
                                            mode = "organizer"
                                            when (val authResult = withContext(Dispatchers.IO) {
                                                apiClient.authenticateOrganizer(
                                                    joinCode = input,
                                                    organizerCode = input
                                                )
                                            }) {
                                                is NasCallResult.Ok -> {
                                                    joinedEventId = authResult.value.eventId
                                                    organizerToken = authResult.value.organizerToken
                                                    organizerCode = input
                                                    prefs.saveLastOrganizerAccessValue(input)
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
                                                            regattaRaceEndTime = snapshotResult.value.raceEndTime.orEmpty()
                                                            regattaLengthNmInput = if (snapshotResult.value.regattaLengthNm > 0.0) {
                                                                String.format(Locale.US, "%.2f", snapshotResult.value.regattaLengthNm)
                                                            } else ""
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
                                                            organizerGateSelection = "start"
                                                            statusMessage = "Organizer regata otvorena."
                                                        }
                                                        is NasCallResult.Err -> errorMessage = snapshotResult.message
                                                    }
                                                }
                                                is NasCallResult.Err -> errorMessage = authResult.message
                                            }
                                        }
                                        is NasCallResult.Err -> {
                                            organizerLoginAskedCreateNew = true
                                        }
                                    }
                                    isBusy = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = actionOrange)
                        ) {
                            Text(if (isBusy) "Logging in..." else "Organizer login")
                        }
                    } else {
                        val event = eventSnapshot
                        Text("Organizer / Regata", color = Color.White, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = eventName,
                            onValueChange = { eventName = it },
                            label = { Text("Ime regate") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = joinCode,
                                onValueChange = { joinCode = it.uppercase(Locale.US) },
                                label = { Text("Join kod") },
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = organizerCode,
                                onValueChange = { organizerCode = it.uppercase(Locale.US) },
                                label = { Text("Organizer hash") },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = regattaStartDate,
                                onValueChange = { regattaStartDate = it },
                                label = { Text("Datum od") },
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = regattaEndDate,
                                onValueChange = { regattaEndDate = it },
                                label = { Text("Datum do") },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = maxBoatsInput,
                                onValueChange = { maxBoatsInput = it.filter(Char::isDigit) },
                                label = { Text("Max brodova") },
                                modifier = Modifier.weight(1f),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                            )
                            OutlinedTextField(
                                value = regattaLengthNmInput,
                                onValueChange = { regattaLengthNmInput = it },
                                label = { Text("Dužina regate (NM)") },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = regattaRaceEndTime,
                            onValueChange = { regattaRaceEndTime = it },
                            label = { Text("Kraj race (HH:MM)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { isPublicEvent = false },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (!isPublicEvent) actionBlue else Color(0xFF424242)
                                )
                            ) { Text("Private") }
                            Button(
                                onClick = { isPublicEvent = true },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isPublicEvent) actionBlue else Color(0xFF424242)
                                )
                            ) { Text("Public") }
                        }
                        Spacer(Modifier.height(8.dp))
                        Button(
                            enabled = !isBusy,
                            onClick = {
                                isBusy = true
                                errorMessage = null
                                scope.launch {
                                    when (val updateResult = withContext(Dispatchers.IO) {
                                        apiClient.updateEvent(
                                            eventId = joinedEventId,
                                            organizerToken = organizerToken,
                                            draft = RegattaDraft(
                                                name = eventName,
                                                joinCode = joinCode,
                                                organizerName = organizerName,
                                                organizerCode = organizerCode,
                                                startDate = regattaStartDate,
                                                endDate = regattaEndDate,
                                                raceEndTime = regattaRaceEndTime,
                                                regattaLengthNm = regattaLengthNmInput.toDoubleOrNull() ?: 0.0,
                                                isPublic = isPublicEvent,
                                                maxBoats = maxBoatsInput.toIntOrNull()?.coerceAtLeast(1) ?: 1
                                            )
                                        )
                                    }) {
                                        is NasCallResult.Ok -> {
                                            statusMessage = "Podaci regate spremljeni."
                                            when (val snapshotResult = withContext(Dispatchers.IO) {
                                                apiClient.getEventSnapshot(joinedEventId)
                                            }) {
                                                is NasCallResult.Ok -> eventSnapshot = snapshotResult.value
                                                is NasCallResult.Err -> errorMessage = snapshotResult.message
                                            }
                                        }
                                        is NasCallResult.Err -> errorMessage = updateResult.message
                                    }
                                    isBusy = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = actionGreen)
                        ) {
                            Text(if (isBusy) "Saving..." else "Spremi regatu")
                        }
                        Spacer(Modifier.height(12.dp))
                        Text("Karakteristike race/plova", color = Color.White, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = organizerRaceDateInput,
                                onValueChange = { organizerRaceDateInput = it },
                                label = { Text("Datum (YYYY-MM-DD)") },
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = organizerRaceStartTimeInput,
                                onValueChange = { organizerRaceStartTimeInput = it },
                                label = { Text("Početak (HH:MM)") },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    val now = Calendar.getInstance()
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
                                        now.get(Calendar.YEAR),
                                        now.get(Calendar.MONTH),
                                        now.get(Calendar.DAY_OF_MONTH)
                                    ).show()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF424242)),
                                modifier = Modifier.weight(1f)
                            ) { Text("Kalendar") }
                            Button(
                                onClick = {
                                    val now = Calendar.getInstance()
                                    TimePickerDialog(
                                        context,
                                        { _, hour, minute ->
                                            organizerRaceStartTimeInput =
                                                String.format(Locale.US, "%02d:%02d", hour, minute)
                                        },
                                        now.get(Calendar.HOUR_OF_DAY),
                                        now.get(Calendar.MINUTE),
                                        true
                                    ).show()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF424242)),
                                modifier = Modifier.weight(1f)
                            ) { Text("Vrijeme start") }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = organizerRaceEndTimeInput,
                                onValueChange = { organizerRaceEndTimeInput = it },
                                label = { Text("Kraj (HH:MM)") },
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = organizerRaceLengthNmInput,
                                onValueChange = { organizerRaceLengthNmInput = it },
                                label = { Text("Duljina race (NM)") },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    val now = Calendar.getInstance()
                                    TimePickerDialog(
                                        context,
                                        { _, hour, minute ->
                                            organizerRaceEndTimeInput =
                                                String.format(Locale.US, "%02d:%02d", hour, minute)
                                        },
                                        now.get(Calendar.HOUR_OF_DAY),
                                        now.get(Calendar.MINUTE),
                                        true
                                    ).show()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF424242)),
                                modifier = Modifier.weight(1f)
                            ) { Text("Vrijeme kraj") }
                        }
                        Spacer(Modifier.height(8.dp))
                        Button(
                            enabled = !isBusy && joinedRaceId.isNotBlank(),
                            onClick = {
                                isBusy = true
                                errorMessage = null
                                scope.launch {
                                    when (val result = withContext(Dispatchers.IO) {
                                        apiClient.updateRaceDetails(
                                            raceId = joinedRaceId,
                                            organizerToken = organizerToken,
                                            raceDate = organizerRaceDateInput,
                                            startTime = organizerRaceStartTimeInput,
                                            endTime = organizerRaceEndTimeInput,
                                            raceLengthNm = organizerRaceLengthNmInput.toDoubleOrNull() ?: 0.0
                                        )
                                    }) {
                                        is NasCallResult.Ok -> {
                                            statusMessage = "Race karakteristike spremljene."
                                            when (val snapshotResult = withContext(Dispatchers.IO) {
                                                apiClient.getEventSnapshot(joinedEventId)
                                            }) {
                                                is NasCallResult.Ok -> eventSnapshot = snapshotResult.value
                                                is NasCallResult.Err -> errorMessage = snapshotResult.message
                                            }
                                        }
                                        is NasCallResult.Err -> errorMessage = result.message
                                    }
                                    isBusy = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = actionGreen)
                        ) { Text(if (isBusy) "Saving..." else "Spremi race karakteristike") }
                        Spacer(Modifier.height(10.dp))
                        Box {
                            OutlinedTextField(
                                value = organizerGateSelection,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Line/Gate odabir") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .pointerInput(organizerGateMenuExpanded) {
                                        detectTapGestures(onTap = { organizerGateMenuExpanded = true })
                                    }
                            )
                            DropdownMenu(
                                expanded = organizerGateMenuExpanded,
                                onDismissRequest = { organizerGateMenuExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("start") },
                                    onClick = {
                                        organizerGateSelection = "start"
                                        organizerGateMenuExpanded = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("finish") },
                                    onClick = {
                                        organizerGateSelection = "finish"
                                        organizerGateMenuExpanded = false
                                    }
                                )
                                helperGates.forEachIndexed { index, gate ->
                                    DropdownMenuItem(
                                        text = { Text(gate.name.ifBlank { "Gate ${index + 1}" }) },
                                        onClick = {
                                            organizerGateSelection = gate.id
                                            organizerGateMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    val p = snapshotPoint() ?: return@Button
                                    when {
                                        organizerGateSelection == "start" -> startA = p
                                        organizerGateSelection == "finish" -> finishA = p
                                        else -> {
                                            helperGates = helperGates.map {
                                                if (it.id == organizerGateSelection) it.copy(pointA = p) else it
                                            }
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB71C1C)),
                                modifier = Modifier.weight(1f)
                            ) { Text("L Get from GPS") }
                            Button(
                                onClick = {
                                    val p = organizerSelectedMapPoint ?: return@Button
                                    when {
                                        organizerGateSelection == "start" -> startA = p
                                        organizerGateSelection == "finish" -> finishA = p
                                        else -> {
                                            helperGates = helperGates.map {
                                                if (it.id == organizerGateSelection) it.copy(pointA = p) else it
                                            }
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB71C1C)),
                                modifier = Modifier.weight(1f)
                            ) { Text("L Get from Map") }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    val p = snapshotPoint() ?: return@Button
                                    when {
                                        organizerGateSelection == "start" -> startB = p
                                        organizerGateSelection == "finish" -> finishB = p
                                        else -> {
                                            helperGates = helperGates.map {
                                                if (it.id == organizerGateSelection) it.copy(pointB = p) else it
                                            }
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B5E20)),
                                modifier = Modifier.weight(1f)
                            ) { Text("R Get from GPS") }
                            Button(
                                onClick = {
                                    val p = organizerSelectedMapPoint ?: return@Button
                                    when {
                                        organizerGateSelection == "start" -> startB = p
                                        organizerGateSelection == "finish" -> finishB = p
                                        else -> {
                                            helperGates = helperGates.map {
                                                if (it.id == organizerGateSelection) it.copy(pointB = p) else it
                                            }
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B5E20)),
                                modifier = Modifier.weight(1f)
                            ) { Text("R Get from Map") }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    val newId = UUID.randomUUID().toString()
                                    helperGates = helperGates + PendingGateDraft(
                                        id = newId,
                                        name = "Gate ${helperGates.size + 1}",
                                        pointA = null,
                                        pointB = null
                                    )
                                    organizerGateSelection = newId
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = actionBlue)
                            ) { Text("+ Gate") }
                            Button(
                                onClick = {
                                    if (organizerGateSelection != "start" && organizerGateSelection != "finish") {
                                        helperGates = helperGates.filterNot { it.id == organizerGateSelection }
                                        organizerGateSelection = "start"
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8E2424))
                            ) { Text("x Gate") }
                        }
                        Spacer(Modifier.height(8.dp))
                        OrganizerGateMap(
                            startA = startA,
                            startB = startB,
                            finishA = finishA,
                            finishB = finishB,
                            helperGates = helperGates,
                            selectedMapPoint = organizerSelectedMapPoint,
                            onMapPointSelected = { organizerSelectedMapPoint = it }
                        )
                        Text(
                            "Map točka: ${organizerSelectedMapPoint?.let(::formatPoint) ?: "--"}",
                            color = muted,
                            fontSize = 12.sp
                        )
                        Spacer(Modifier.height(12.dp))
                        Text("Brodovi", color = Color.White, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(6.dp))
                        if (event == null || event.boats.isEmpty()) {
                            Text("Nema brodova.", color = muted)
                        } else {
                            event.boats.forEach { boat ->
                                Card(modifier = Modifier.fillMaxWidth()) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color(0xFF171717))
                                            .padding(10.dp)
                                    ) {
                                        Text(boat.boatName, color = Color.White, fontWeight = FontWeight.Bold)
                                        Text("Kormilar: ${boat.skipperName.ifBlank { "--" }}", color = Color(0xFFCFD8DC), fontSize = 12.sp)
                                        Text("Klub: ${boat.clubName.ifBlank { "--" }}", color = Color(0xFFCFD8DC), fontSize = 12.sp)
                                        val lengthMeters = when {
                                            boat.lengthValue == null -> "--"
                                            boat.lengthUnit.equals("ft", ignoreCase = true) ->
                                                String.format(Locale.US, "%.2f", boat.lengthValue * 0.3048)
                                            else -> String.format(Locale.US, "%.2f", boat.lengthValue)
                                        }
                                        Text("Duljina: $lengthMeters m", color = Color(0xFFCFD8DC), fontSize = 12.sp)
                                        Spacer(Modifier.height(6.dp))
                                        OutlinedTextField(
                                            value = groupInputs[boat.id] ?: boat.groupCode.orEmpty(),
                                            onValueChange = { groupInputs[boat.id] = it },
                                            label = { Text("Grupa") },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        Spacer(Modifier.height(6.dp))
                                        OutlinedTextField(
                                            value = penaltyValueInputs[boat.id].orEmpty(),
                                            onValueChange = { penaltyValueInputs[boat.id] = it },
                                            label = { Text("Penalty %") },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        Spacer(Modifier.height(6.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Button(
                                                onClick = {
                                                    scope.launch {
                                                        val liveRaceId = event.activeRaceId.orEmpty()
                                                        if (liveRaceId.isNotBlank()) {
                                                            when (val groupResult = withContext(Dispatchers.IO) {
                                                                apiClient.updateBoatGroup(
                                                                    eventId = joinedEventId,
                                                                    boatId = boat.id,
                                                                    organizerToken = organizerToken,
                                                                    groupCode = groupInputs[boat.id].orEmpty()
                                                                )
                                                            }) {
                                                                is NasCallResult.Ok -> statusMessage = "Grupa spremljena za ${boat.boatName}."
                                                                is NasCallResult.Err -> errorMessage = groupResult.message
                                                            }
                                                            val penaltyValue = penaltyValueInputs[boat.id].orEmpty().toDoubleOrNull()
                                                            if (penaltyValue != null && penaltyValue > 0.0) {
                                                                when (val penaltyResult = withContext(Dispatchers.IO) {
                                                                    apiClient.createPenalty(
                                                                        raceId = liveRaceId,
                                                                        boatId = boat.id,
                                                                        organizerToken = organizerToken,
                                                                        type = "percent",
                                                                        value = penaltyValue,
                                                                        reason = "Organizer manual"
                                                                    )
                                                                }) {
                                                                    is NasCallResult.Ok -> statusMessage = "Penalty spremljen za ${boat.boatName}."
                                                                    is NasCallResult.Err -> errorMessage = penaltyResult.message
                                                                }
                                                            }
                                                            when (val snapshotResult = withContext(Dispatchers.IO) {
                                                                apiClient.getEventSnapshot(joinedEventId)
                                                            }) {
                                                                is NasCallResult.Ok -> eventSnapshot = snapshotResult.value
                                                                is NasCallResult.Err -> errorMessage = snapshotResult.message
                                                            }
                                                        } else {
                                                            errorMessage = "Nema aktivnog race za upis penalty-a."
                                                        }
                                                    }
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = actionBlue)
                                            ) { Text("Spremi") }
                                            Button(
                                                onClick = {
                                                    pendingDeleteBoat = boat
                                                    showDeleteBoatConfirm = true
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8E2424))
                                            ) { Text("Obriši") }
                                        }
                                    }
                                }
                                Spacer(Modifier.height(6.dp))
                            }
                        }
                    }
                }
            }

            if (mode == "join") {
                RegattaSection(title = "Join Event", cardColor = cardColor) {
                    OutlinedTextField(
                        value = joinCode,
                        onValueChange = { joinCode = it.uppercase(Locale.US) },
                        label = { Text("Join code") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = boatName,
                        onValueChange = { boatName = it },
                        label = { Text("Boat name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = skipperName,
                        onValueChange = { skipperName = it },
                        label = { Text("Skipper name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = clubName,
                        onValueChange = { clubName = it },
                        label = { Text("Club name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = lengthValue,
                            onValueChange = { lengthValue = it },
                            label = { Text("Length") },
                            modifier = Modifier.weight(1f)
                        )
                        Box(modifier = Modifier.weight(1f)) {
                            OutlinedTextField(
                                value = lengthUnit,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Unit") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .pointerInput(lengthUnitMenuExpanded) {
                                        detectTapGestures(onTap = { lengthUnitMenuExpanded = true })
                                    }
                            )
                            DropdownMenu(
                                expanded = lengthUnitMenuExpanded,
                                onDismissRequest = { lengthUnitMenuExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("m") },
                                    onClick = {
                                        lengthUnit = "m"
                                        lengthUnitMenuExpanded = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("ft") },
                                    onClick = {
                                        lengthUnit = "ft"
                                        lengthUnitMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = groupCode,
                        onValueChange = { groupCode = it },
                        label = { Text("Group") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
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
                                                groupCode = groupCode
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
                                        statusMessage = "Podaci spremljeni. Otvaram Race Start Line."
                                        onOpenRaceStartLine()
                                    }
                                    is NasCallResult.Err -> {
                                        if (result.message.contains("REGATTA_FULL", ignoreCase = true)) {
                                            errorMessage = "Kvota je popunjena. Nije moguće prijaviti novi brod."
                                        } else if (result.message.contains("404")) {
                                            handleJoinRegattaNotFound()
                                        } else {
                                            errorMessage = result.message
                                        }
                                    }
                                }
                                isBusy = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = actionBlue)
                    ) {
                        Text(if (isBusy) "Saving..." else "Next")
                    }
                }
            }

            if (joinedEventId.isNotBlank()) {
                Button(
                    onClick = { mode = "dashboard" },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF424242))
                ) {
                    Text("Open Dashboard")
                }
            }

            if (mode == "dashboard" && joinedEventId.isNotBlank()) {
                RegattaSection(title = "Event Snapshot", cardColor = cardColor) {
                    Text("Event ID: $joinedEventId", color = Color.White)
                    Text("Race ID: ${joinedRaceId.ifBlank { "--" }}", color = Color.White)
                    Text("Boat ID: ${joinedBoatId.ifBlank { "--" }}", color = Color.White)
                    Text("Device ID: ${prefs.deviceId}", color = muted, fontSize = 12.sp)
                    Spacer(Modifier.height(8.dp))
                    val event = eventSnapshot
                    if (event == null) {
                        Text("Loading event snapshot...", color = muted)
                    } else {
                        if (nextRaceName.isBlank()) {
                            nextRaceName = "Race ${event.races.size + 1}"
                        }
                        Text("Name: ${event.name}", color = Color.White, fontWeight = FontWeight.SemiBold)
                        Text("Join code: ${event.joinCode}", color = Color.White)
                        Text("Organizer: ${event.organizerName}", color = Color.White)
                        if (!event.startDate.isNullOrBlank() || !event.endDate.isNullOrBlank()) {
                            Text(
                                "Regatta dates: ${event.startDate ?: "--"} -> ${event.endDate ?: "--"}",
                                color = Color.White
                            )
                        }
                        if (!event.raceEndTime.isNullOrBlank()) {
                            Text("Race end time: ${event.raceEndTime}", color = Color.White)
                        }
                        Text(
                            "Regatta length: ${String.format(Locale.US, "%.2f", event.regattaLengthNm)} NM",
                            color = Color.White
                        )
                        Text(
                            "Public results: ${if (event.isPublic) "Da" else "Ne"}",
                            color = Color.White
                        )
                        Text("Max boats: ${event.maxBoats}", color = Color.White)
                        Text("Status: ${event.status}", color = Color.White)
                        if (event.noticeBoard.isNotBlank()) {
                            Spacer(Modifier.height(6.dp))
                            Text("Notice board:", color = Color.White, fontWeight = FontWeight.SemiBold)
                            Text(event.noticeBoard, color = Color(0xFFCFD8DC))
                            event.noticeBoardUpdatedAt?.let { updatedAt ->
                                Text("Updated: $updatedAt", color = muted, fontSize = 12.sp)
                            }
                        }
                        Text("Boats: ${event.boats.size}", color = Color.White)
                        Text("Races: ${event.races.size}", color = Color.White)
                        Text("Role: ${if (isOrganizer) "Organizer" else "Participant"}", color = Color.White)
                        Spacer(Modifier.height(8.dp))
                        event.races.forEach { race ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Race ${race.sequenceNumber} (${race.state}) · gates ${race.gates.size}",
                                    color = if (race.id == joinedRaceId) Color(0xFFFFF59D) else Color(0xFFB8E0D0)
                                )
                                Button(
                                    onClick = {
                                        joinedRaceId = race.id
                                        prefs.saveJoinedEvent(joinedEventId, joinedRaceId, joinedBoatId)
                                        startTimeInput = race.countdownTargetEpochMs?.let(::formatEpochInput).orEmpty()
                                        scoringTargetGateId = race.scoringTargetGateId.orEmpty()
                                        organizerRaceDateInput = race.raceDate.orEmpty()
                                        organizerRaceStartTimeInput = race.startTime ?: organizerRaceStartTimeInput
                                        organizerRaceEndTimeInput = race.endTime ?: organizerRaceEndTimeInput
                                        organizerRaceLengthNmInput = race.raceLengthNm
                                            .takeIf { it > 0.0 }
                                            ?.let { String.format(Locale.US, "%.2f", it) }
                                            .orEmpty()
                                        startA = race.gates.firstOrNull { it.type == "start" }?.pointA
                                        startB = race.gates.firstOrNull { it.type == "start" }?.pointB
                                        finishA = race.gates.firstOrNull { it.type == "finish" }?.pointA
                                        finishB = race.gates.firstOrNull { it.type == "finish" }?.pointB
                                        helperGates = race.gates
                                            .filter { it.type == "gate" }
                                            .map {
                                                PendingGateDraft(
                                                    id = it.id.ifBlank { UUID.randomUUID().toString() },
                                                    name = it.name,
                                                    pointA = it.pointA,
                                                    pointB = it.pointB
                                                )
                                            }
                                        pendingHelperName = "Gate ${helperGates.size + 1}"
                                        pendingHelperA = null
                                        pendingHelperB = null
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF424242))
                                ) {
                                    Text("Select")
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                        }
                    }
                }

                if (isOrganizer && eventSnapshot != null) {
                    RegattaSection(title = "Organizer Controls", cardColor = cardColor) {
                        OutlinedTextField(
                            value = noticeBoardInput.ifBlank { eventSnapshot?.noticeBoard.orEmpty() },
                            onValueChange = { noticeBoardInput = it },
                            label = { Text("Notice board message") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(
                            enabled = !isBusy,
                            onClick = {
                                isBusy = true
                                errorMessage = null
                                scope.launch {
                                    when (
                                        val result = withContext(Dispatchers.IO) {
                                            apiClient.updateNoticeBoard(
                                                eventId = joinedEventId,
                                                organizerToken = organizerToken,
                                                noticeText = noticeBoardInput.ifBlank { eventSnapshot?.noticeBoard.orEmpty() }
                                            )
                                        }
                                    ) {
                                        is NasCallResult.Ok -> statusMessage = "Notice board updated."
                                        is NasCallResult.Err -> errorMessage = result.message
                                    }
                                    isBusy = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = actionBlue)
                        ) {
                            Text(if (isBusy) "Saving..." else "Save Notice")
                        }
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = nextRaceName,
                            onValueChange = { nextRaceName = it },
                            label = { Text("Next race name") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(
                            enabled = !isBusy,
                            onClick = {
                                isBusy = true
                                errorMessage = null
                                scope.launch {
                                    when (
                                        val result = withContext(Dispatchers.IO) {
                                            apiClient.createRace(
                                                eventId = joinedEventId,
                                                organizerToken = organizerToken,
                                                name = nextRaceName
                                            )
                                        }
                                    ) {
                                        is NasCallResult.Ok -> {
                                            joinedRaceId = result.value
                                            prefs.saveJoinedEvent(joinedEventId, joinedRaceId, joinedBoatId)
                                            statusMessage = "Created and selected ${nextRaceName.ifBlank { "new race" }}."
                                        }
                                        is NasCallResult.Err -> errorMessage = result.message
                                    }
                                    isBusy = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = actionGreen)
                        ) {
                            Text(if (isBusy) "Working..." else "Create Next Race")
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = countdownMinutes,
                                onValueChange = { countdownMinutes = it },
                                label = { Text("Countdown min") },
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                enabled = joinedRaceId.isNotBlank() && !isBusy,
                                onClick = {
                                    val minutes = countdownMinutes.toLongOrNull()?.coerceAtLeast(1L) ?: 5L
                                    isBusy = true
                                    errorMessage = null
                                    scope.launch {
                                        when (
                                            val result = withContext(Dispatchers.IO) {
                                                apiClient.updateCountdown(
                                                    raceId = joinedRaceId,
                                                    organizerToken = organizerToken,
                                                    countdownTargetEpochMs = System.currentTimeMillis() + minutes * 60_000L
                                                )
                                            }
                                        ) {
                                            is NasCallResult.Ok -> statusMessage = "Countdown synced for $minutes min."
                                            is NasCallResult.Err -> errorMessage = result.message
                                        }
                                        isBusy = false
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = actionOrange)
                            ) {
                                Text("Sync")
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = startTimeInput,
                            onValueChange = { startTimeInput = it },
                            label = { Text("Start time (yyyy-MM-dd HH:mm:ss)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                enabled = joinedRaceId.isNotBlank() && !isBusy,
                                onClick = {
                                    val parsedEpoch = parseEpochInput(startTimeInput)
                                    if (parsedEpoch == null) {
                                        errorMessage = "Invalid start time format."
                                        return@Button
                                    }
                                    isBusy = true
                                    errorMessage = null
                                    scope.launch {
                                        when (
                                            val result = withContext(Dispatchers.IO) {
                                                apiClient.updateCountdown(
                                                    raceId = joinedRaceId,
                                                    organizerToken = organizerToken,
                                                    countdownTargetEpochMs = parsedEpoch
                                                )
                                            }
                                        ) {
                                            is NasCallResult.Ok -> statusMessage = "Start time updated."
                                            is NasCallResult.Err -> errorMessage = result.message
                                        }
                                        isBusy = false
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = actionOrange)
                            ) {
                                Text("Set Start Time")
                            }
                            Button(
                                enabled = joinedRaceId.isNotBlank() && !isBusy,
                                onClick = {
                                    val base = parseEpochInput(startTimeInput)
                                        ?: eventSnapshot?.races?.firstOrNull { it.id == joinedRaceId }?.countdownTargetEpochMs
                                    if (base == null) {
                                        errorMessage = "No base start time to adjust."
                                        return@Button
                                    }
                                    val adjusted = base - 15_000L
                                    startTimeInput = formatEpochInput(adjusted)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF424242))
                            ) { Text("-15s") }
                            Button(
                                enabled = joinedRaceId.isNotBlank() && !isBusy,
                                onClick = {
                                    val base = parseEpochInput(startTimeInput)
                                        ?: eventSnapshot?.races?.firstOrNull { it.id == joinedRaceId }?.countdownTargetEpochMs
                                    if (base == null) {
                                        errorMessage = "No base start time to adjust."
                                        return@Button
                                    }
                                    val adjusted = base + 15_000L
                                    startTimeInput = formatEpochInput(adjusted)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF424242))
                            ) { Text("+15s") }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Rules: initial start >= +5 min from now; corrections allowed until T-4 min, max ±15s.",
                            color = muted,
                            fontSize = 11.sp
                        )
                        Spacer(Modifier.height(10.dp))
                        val selectedRace = eventSnapshot?.races?.firstOrNull { it.id == joinedRaceId }
                        val targetCandidates = selectedRace?.gates
                            ?.filterNot { it.type.equals("start", ignoreCase = true) }
                            .orEmpty()
                        OutlinedTextField(
                            value = scoringTargetGateId,
                            onValueChange = { scoringTargetGateId = it },
                            label = { Text("Scoring target gate ID") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (targetCandidates.isNotEmpty()) {
                            Spacer(Modifier.height(6.dp))
                            targetCandidates.forEach { gate ->
                                Text(
                                    text = "${gate.name} (${gate.type}) · ${gate.id}",
                                    color = if (gate.id == scoringTargetGateId) Color(0xFFFFF59D) else muted,
                                    fontSize = 12.sp
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Button(
                            enabled = joinedRaceId.isNotBlank() && scoringTargetGateId.isNotBlank() && !isBusy,
                            onClick = {
                                isBusy = true
                                errorMessage = null
                                scope.launch {
                                    when (
                                        val result = withContext(Dispatchers.IO) {
                                            apiClient.updateScoringTargetGate(
                                                raceId = joinedRaceId,
                                                organizerToken = organizerToken,
                                                gateId = scoringTargetGateId
                                            )
                                        }
                                    ) {
                                        is NasCallResult.Ok -> statusMessage = "Scoring target gate synced."
                                        is NasCallResult.Err -> errorMessage = result.message
                                    }
                                    isBusy = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = actionBlue)
                        ) {
                            Text("Set Scoring Target")
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            RaceStateButton("Lobby", actionBlue) {
                                isBusy = true
                                errorMessage = null
                                scope.launch {
                                    when (
                                        val result = withContext(Dispatchers.IO) {
                                            apiClient.updateRaceState(
                                                raceId = joinedRaceId,
                                                organizerToken = organizerToken,
                                                state = "lobby"
                                            )
                                        }
                                    ) {
                                        is NasCallResult.Ok -> statusMessage = "Race state changed to LOBBY."
                                        is NasCallResult.Err -> errorMessage = result.message
                                    }
                                    isBusy = false
                                }
                            }
                            RaceStateButton("Armed", actionOrange) {
                                isBusy = true
                                errorMessage = null
                                scope.launch {
                                    when (
                                        val result = withContext(Dispatchers.IO) {
                                            apiClient.updateRaceState(
                                                raceId = joinedRaceId,
                                                organizerToken = organizerToken,
                                                state = "armed"
                                            )
                                        }
                                    ) {
                                        is NasCallResult.Ok -> statusMessage = "Race state changed to ARMED."
                                        is NasCallResult.Err -> errorMessage = result.message
                                    }
                                    isBusy = false
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            RaceStateButton("Started", actionGreen) {
                                isBusy = true
                                errorMessage = null
                                scope.launch {
                                    when (
                                        val result = withContext(Dispatchers.IO) {
                                            apiClient.updateRaceState(
                                                raceId = joinedRaceId,
                                                organizerToken = organizerToken,
                                                state = "started"
                                            )
                                        }
                                    ) {
                                        is NasCallResult.Ok -> statusMessage = "Race state changed to STARTED."
                                        is NasCallResult.Err -> errorMessage = result.message
                                    }
                                    isBusy = false
                                }
                            }
                            RaceStateButton("Finished", Color(0xFF6D4C41)) {
                                isBusy = true
                                errorMessage = null
                                scope.launch {
                                    when (
                                        val result = withContext(Dispatchers.IO) {
                                            apiClient.updateRaceState(
                                                raceId = joinedRaceId,
                                                organizerToken = organizerToken,
                                                state = "finished"
                                            )
                                        }
                                    ) {
                                        is NasCallResult.Ok -> statusMessage = "Race state changed to FINISHED."
                                        is NasCallResult.Err -> errorMessage = result.message
                                    }
                                    isBusy = false
                                }
                            }
                        }
                    }

                    RegattaSection(title = "Course Setup From GPS", cardColor = cardColor) {
                        Text("Current GPS point can be snapped into start / finish / multiple helper gates.", color = muted)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CaptureButton("Start A") { startA = snapshotPoint() }
                            CaptureButton("Start B") { startB = snapshotPoint() }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CaptureButton("Finish A") { finishA = snapshotPoint() }
                            CaptureButton("Finish B") { finishB = snapshotPoint() }
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = pendingHelperName,
                            onValueChange = { pendingHelperName = it },
                            label = { Text("New helper gate name") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CaptureButton("Gate A") { pendingHelperA = snapshotPoint() }
                            CaptureButton("Gate B") { pendingHelperB = snapshotPoint() }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("Start A: ${startA?.let(::formatPoint) ?: "--"}", color = Color.White, fontSize = 12.sp)
                        Text("Start B: ${startB?.let(::formatPoint) ?: "--"}", color = Color.White, fontSize = 12.sp)
                        Text("Finish A: ${finishA?.let(::formatPoint) ?: "--"}", color = Color.White, fontSize = 12.sp)
                        Text("Finish B: ${finishB?.let(::formatPoint) ?: "--"}", color = Color.White, fontSize = 12.sp)
                        Text("Pending gate A: ${pendingHelperA?.let(::formatPoint) ?: "--"}", color = Color.White, fontSize = 12.sp)
                        Text("Pending gate B: ${pendingHelperB?.let(::formatPoint) ?: "--"}", color = Color.White, fontSize = 12.sp)
                        Spacer(Modifier.height(10.dp))
                        Button(
                            enabled = pendingHelperA != null && pendingHelperB != null,
                            onClick = {
                                helperGates = helperGates + PendingGateDraft(
                                    id = UUID.randomUUID().toString(),
                                    name = pendingHelperName.ifBlank { "Gate ${helperGates.size + 1}" },
                                    pointA = pendingHelperA,
                                    pointB = pendingHelperB
                                )
                                pendingHelperName = "Gate ${helperGates.size + 1}"
                                pendingHelperA = null
                                pendingHelperB = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = actionBlue)
                        ) {
                            Text("Add Helper Gate")
                        }
                        Spacer(Modifier.height(8.dp))
                        if (helperGates.isEmpty()) {
                            Text("No helper gates added yet.", color = muted)
                        } else {
                            helperGates.forEachIndexed { index, gate ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "${index + 1}. ${gate.name} · " +
                                            "${gate.pointA?.let(::formatPoint) ?: "--"} / " +
                                            "${gate.pointB?.let(::formatPoint) ?: "--"}",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Spacer(Modifier.height(0.dp))
                                    Button(
                                        onClick = {
                                            helperGates = helperGates.filterNot { it.id == gate.id }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8E2424))
                                    ) {
                                        Text("Remove")
                                    }
                                }
                                Spacer(Modifier.height(6.dp))
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Button(
                            enabled = !isBusy && joinedRaceId.isNotBlank() &&
                                startA != null && startB != null && finishA != null && finishB != null,
                            onClick = {
                                val gates = mutableListOf<GateDraft>()
                                gates += GateDraft(0, "start", "Start", startA!!, startB!!)
                                helperGates.forEachIndexed { index, gate ->
                                    if (gate.pointA != null && gate.pointB != null) {
                                        gates += GateDraft(
                                            order = index + 1,
                                            type = "gate",
                                            name = gate.name,
                                            pointA = gate.pointA,
                                            pointB = gate.pointB
                                        )
                                    }
                                }
                                gates += GateDraft(
                                    order = helperGates.size + 1,
                                    type = "finish",
                                    name = "Finish",
                                    pointA = finishA!!,
                                    pointB = finishB!!
                                )
                                isBusy = true
                                errorMessage = null
                                scope.launch {
                                    when (
                                        val result = withContext(Dispatchers.IO) {
                                            apiClient.updateCourse(
                                                raceId = joinedRaceId,
                                                gates = gates,
                                                organizerToken = organizerToken
                                            )
                                        }
                                    ) {
                                        is NasCallResult.Ok -> statusMessage = "Course synced to server."
                                        is NasCallResult.Err -> errorMessage = result.message
                                    }
                                    isBusy = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = actionGreen)
                        ) {
                            Text(if (isBusy) "Saving..." else "Save Course")
                        }
                    }
                }

                RegattaSection(title = "Live Race", cardColor = cardColor) {
                    val live = liveSnapshot
                    if (live == null) {
                        Text("Waiting for live race snapshot...", color = muted)
                    } else {
                        Text("Race: ${live.raceName}", color = Color.White, fontWeight = FontWeight.SemiBold)
                        Text("State: ${live.state}", color = Color.White)
                        Text(
                            "Countdown target: ${live.countdownTargetEpochMs?.let(::formatEpoch) ?: "--"}",
                            color = Color.White
                        )
                        Text("Participants: ${live.participants.size}", color = Color.White)
                        Text("Crossings: ${live.crossings.size}", color = Color.White)
                        Text("Penalties: ${live.penalties.size}", color = Color.White)
                        Text(
                            "Scoring target gate: ${live.scoringTargetGateId?.ifBlank { "--" } ?: "--"}",
                            color = Color.White
                        )
                        Spacer(Modifier.height(8.dp))
                        RegattaCourseMap(
                            gates = live.gates,
                            participants = live.participants
                        )
                        Spacer(Modifier.height(8.dp))
                        live.gates.forEach { gate ->
                            Text(
                                text = "${gate.order}. ${gate.name} (${gate.type})",
                                color = Color(0xFFFFF59D)
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        live.participants.take(12).forEach { participant ->
                            Text(
                                text = "${participant.boatName} · ${participant.status} · next gate ${participant.nextGateOrder}" +
                                    " · last ${participant.lastSignalEpochMs?.let(::formatEpoch) ?: "--"}",
                                color = Color(0xFFB8E0D0),
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                RegattaSection(title = "Scoring Export", cardColor = cardColor) {
                    Text("Share CSV scoring for active race or whole regatta event.", color = muted)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            enabled = liveSnapshot != null && eventSnapshot != null,
                            onClick = {
                                val event = eventSnapshot ?: return@Button
                                val live = liveSnapshot ?: return@Button
                                val rows = computeRaceScoreRows(live, event.boats)
                                val csv = buildRaceScoringCsv(live, rows)
                                val file = writeCsvFile(
                                    context = context,
                                    fileName = "race_scoring_${sanitizeFileName(live.raceName)}.csv",
                                    csvContent = csv
                                )
                                shareCsvFile(context, file, "Share race scoring")
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = actionBlue)
                        ) {
                            Text("Share Race CSV")
                        }
                        Button(
                            enabled = eventSnapshot != null,
                            onClick = {
                                val event = eventSnapshot ?: return@Button
                                scope.launch {
                                    errorMessage = null
                                    val raceSnapshots = mutableListOf<RegattaLiveSnapshot>()
                                    for (race in event.races) {
                                        when (val result = withContext(Dispatchers.IO) { apiClient.getLiveSnapshot(race.id) }) {
                                            is NasCallResult.Ok -> raceSnapshots += result.value
                                            is NasCallResult.Err -> {
                                                errorMessage = result.message
                                                return@launch
                                            }
                                        }
                                    }
                                    val table = computeEventScoreTable(event, raceSnapshots)
                                    val csv = buildEventScoringCsv(event, table)
                                    val file = writeCsvFile(
                                        context = context,
                                        fileName = "regatta_scoring_${sanitizeFileName(event.name)}.csv",
                                        csvContent = csv
                                    )
                                    shareCsvFile(context, file, "Share regatta scoring")
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = actionGreen)
                        ) {
                            Text("Share Regatta CSV")
                        }
                    }
                }

                RegattaSection(title = "Boat Live View", cardColor = cardColor) {
                    val live = liveSnapshot
                    if (live == null || live.participants.isEmpty()) {
                        Text("No participant data yet.", color = muted)
                    } else {
                        live.participants.forEach { participant ->
                            val participantCrossings = live.crossings
                                .filter { it.boatId == participant.boatId }
                                .sortedBy { it.crossingEpochMs }
                            val participantPenalties = live.penalties
                                .filter { it.boatId == participant.boatId }
                            Card(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFF171717))
                                        .padding(10.dp)
                                ) {
                                    Text(
                                        text = participant.boatName,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                    Text(
                                        text = "Skipper: ${participant.skipperName.ifBlank { "--" }}",
                                        color = Color(0xFFCFD8DC),
                                        fontSize = 12.sp
                                    )
                                    Text(
                                        text = "Status: ${participant.status.uppercase(Locale.US)}",
                                        color = Color(0xFFFFF59D),
                                        fontSize = 12.sp
                                    )
                                    Text(
                                        text = "Group: ${participant.groupCode ?: "--"}  ·  Next gate: ${participant.nextGateOrder}",
                                        color = Color(0xFFCFD8DC),
                                        fontSize = 12.sp
                                    )
                                    Text(
                                        text = "Start snapshot: ${participant.startSnapshotEpochMs?.let(::formatEpoch) ?: "--"}",
                                        color = Color(0xFFCFD8DC),
                                        fontSize = 12.sp
                                    )
                                    Text(
                                        text = "Start position: ${
                                            if (participant.startSnapshotLatitude != null && participant.startSnapshotLongitude != null) {
                                                String.format(
                                                    Locale.US,
                                                    "%.6f, %.6f",
                                                    participant.startSnapshotLatitude,
                                                    participant.startSnapshotLongitude
                                                )
                                            } else {
                                                "--"
                                            }
                                        }",
                                        color = Color(0xFFCFD8DC),
                                        fontSize = 12.sp
                                    )
                                    Text(
                                        text = "Last signal: ${participant.lastSignalEpochMs?.let(::formatEpoch) ?: "--"}",
                                        color = Color(0xFFCFD8DC),
                                        fontSize = 12.sp
                                    )
                                    Text(
                                        text = "Speed: ${
                                            participant.lastSpeedKnots?.let {
                                                String.format(Locale.US, "%.1f kn", it)
                                            } ?: "--"
                                        }",
                                        color = Color(0xFFCFD8DC),
                                        fontSize = 12.sp
                                    )
                                    Text(
                                        text = "Last position: ${
                                            if (participant.lastLatitude != null && participant.lastLongitude != null) {
                                                String.format(
                                                    Locale.US,
                                                    "%.6f, %.6f",
                                                    participant.lastLatitude,
                                                    participant.lastLongitude
                                                )
                                            } else {
                                                "--"
                                            }
                                        }",
                                        color = Color(0xFFCFD8DC),
                                        fontSize = 12.sp
                                    )
                                    Text(
                                        text = "Finished: ${participant.finishedAtEpochMs?.let(::formatEpoch) ?: "--"}",
                                        color = Color(0xFFCFD8DC),
                                        fontSize = 12.sp
                                    )
                                    Spacer(Modifier.height(8.dp))

                                    Text(
                                        text = "Gate Crossings",
                                        color = Color.White,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp
                                    )
                                    if (participantCrossings.isEmpty()) {
                                        Text("No crossings yet.", color = muted, fontSize = 12.sp)
                                    } else {
                                        participantCrossings.forEach { crossing ->
                                            Text(
                                                text = "${crossing.gateOrder}. ${crossing.gateName} " +
                                                    "(${crossing.gateType}) · ${formatEpoch(crossing.crossingEpochMs)} " +
                                                    "· ${crossing.source}",
                                                color = Color(0xFFB8E0D0),
                                                fontSize = 12.sp
                                            )
                                        }
                                    }

                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        text = "Penalties",
                                        color = Color.White,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp
                                    )
                                    if (participantPenalties.isEmpty()) {
                                        Text("No penalties.", color = muted, fontSize = 12.sp)
                                    } else {
                                        participantPenalties.forEach { penalty ->
                                            Text(
                                                text = "${penalty.value?.let { String.format(Locale.US, "%.1f%%", it) } ?: "--"} · ${penalty.reason.ifBlank { "--" }} " +
                                                    "· ${penalty.createdAt}",
                                                color = Color(0xFFFFCC80),
                                                fontSize = 12.sp
                                            )
                                        }
                                    }
                                    if (isOrganizer) {
                                        Spacer(Modifier.height(8.dp))
                                        Text(
                                            text = "Organizer Tools",
                                            color = Color.White,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 13.sp
                                        )
                                        Spacer(Modifier.height(6.dp))
                                        OutlinedTextField(
                                            value = groupInputs[participant.boatId].orEmpty(),
                                            onValueChange = { groupInputs[participant.boatId] = it },
                                            label = { Text("Group") },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        Spacer(Modifier.height(6.dp))
                                        Button(
                                            onClick = {
                                                scope.launch {
                                                    when (
                                                        val result = withContext(Dispatchers.IO) {
                                                            apiClient.updateBoatGroup(
                                                                eventId = joinedEventId,
                                                                boatId = participant.boatId,
                                                                organizerToken = organizerToken,
                                                                groupCode = groupInputs[participant.boatId].orEmpty()
                                                            )
                                                        }
                                                    ) {
                                                        is NasCallResult.Ok -> statusMessage =
                                                            "Updated group for ${participant.boatName}."
                                                        is NasCallResult.Err -> errorMessage = result.message
                                                    }
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = actionBlue)
                                        ) {
                                            Text("Save Group")
                                        }
                                        Spacer(Modifier.height(8.dp))
                                        OutlinedTextField(
                                            value = penaltyValueInputs[participant.boatId].orEmpty(),
                                            onValueChange = { penaltyValueInputs[participant.boatId] = it },
                                            label = { Text("Penalty (%)") },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        Spacer(Modifier.height(6.dp))
                                        OutlinedTextField(
                                            value = penaltyReasonInputs[participant.boatId].orEmpty(),
                                            onValueChange = { penaltyReasonInputs[participant.boatId] = it },
                                            label = { Text("Reason") },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        Spacer(Modifier.height(6.dp))
                                        Button(
                                            enabled = joinedRaceId.isNotBlank(),
                                            onClick = {
                                                scope.launch {
                                                    when (
                                                        val result = withContext(Dispatchers.IO) {
                                                            apiClient.createPenalty(
                                                                raceId = joinedRaceId,
                                                                boatId = participant.boatId,
                                                                organizerToken = organizerToken,
                                                                type = "percent",
                                                                value = penaltyValueInputs[participant.boatId].orEmpty()
                                                                    .toDoubleOrNull(),
                                                                reason = penaltyReasonInputs[participant.boatId].orEmpty()
                                                            )
                                                        }
                                                    ) {
                                                        is NasCallResult.Ok -> {
                                                            statusMessage = "Penalty added for ${participant.boatName}."
                                                            penaltyReasonInputs[participant.boatId] = ""
                                                        }
                                                        is NasCallResult.Err -> errorMessage = result.message
                                                    }
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8E2424))
                                        ) {
                                            Text("Add Penalty")
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }
            }
        }
    }
    if (organizerLoginAskedCreateNew) {
        AlertDialog(
            onDismissRequest = { organizerLoginAskedCreateNew = false },
            title = { Text("Nova regata?") },
            text = { Text("Nije unesen hash regate. Želiš li napraviti novu regatu?") },
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
                        regattaRaceEndTime = "18:00"
                        regattaLengthNmInput = ""
                        maxBoatsInput = "50"
                        isPublicEvent = false
                        mode = "new"
                    }
                ) { Text("Da") }
            },
            dismissButton = {
                TextButton(onClick = { organizerLoginAskedCreateNew = false }) { Text("Ne") }
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
            title = { Text("Brisanje broda") },
            text = { Text("Jesi siguran da želiš obrisati brod ${boat.boatName} i sve njegove rezultate iz regate?") },
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
                                    statusMessage = "Brod ${boat.boatName} obrisan."
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
                ) { Text("Da") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDeleteBoatConfirm = false
                        pendingDeleteBoat = null
                    }
                ) { Text("Ne") }
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
    onMapPointSelected: (RegattaPoint) -> Unit
) {
    AndroidView(
        modifier = Modifier
            .fillMaxWidth()
            .height(260.dp),
        factory = { ctx ->
            org.osmdroid.config.Configuration.getInstance().userAgentValue = ctx.packageName
            MapView(ctx).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                setBuiltInZoomControls(false)
                isTilesScaledToDpi = true
                controller.setZoom(15.0)
                overlays.add(
                    MapEventsOverlay(
                        object : MapEventsReceiver {
                            override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                                onMapPointSelected(RegattaPoint(p.latitude, p.longitude))
                                return true
                            }

                            override fun longPressHelper(p: GeoPoint): Boolean = false
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
            fun addLine(name: String, a: RegattaPoint?, b: RegattaPoint?, color: Int) {
                if (a == null || b == null) return
                val pa = GeoPoint(a.latitude, a.longitude)
                val pb = GeoPoint(b.latitude, b.longitude)
                bounds += pa
                bounds += pb
                mapView.overlays.add(
                    Polyline(mapView).apply {
                        outlinePaint.color = color
                        outlinePaint.strokeWidth = 7f
                        setPoints(listOf(pa, pb))
                        title = name
                    }
                )
            }
            addLine("Start", startA, startB, android.graphics.Color.YELLOW)
            helperGates.forEach { gate ->
                addLine(gate.name, gate.pointA, gate.pointB, android.graphics.Color.rgb(66, 165, 245))
            }
            addLine("Finish", finishA, finishB, android.graphics.Color.RED)
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
            when {
                bounds.size >= 2 -> mapView.zoomToBoundingBox(BoundingBox.fromGeoPointsSafe(bounds), true, 48)
                bounds.size == 1 -> {
                    mapView.controller.setCenter(bounds.first())
                    mapView.controller.setZoom(16.0)
                }
            }
            mapView.invalidate()
        }
    )
}

@Composable
private fun HistoryResultsMapView(
    selectedRaceLive: RegattaLiveSnapshot,
    selectedBoatId: String,
    selectedBoatTrack: List<RegattaBoatTrackPoint>
) {
    val raceGates = selectedRaceLive.gates
    val t0Points = selectedRaceLive.participants.filter {
        it.startSnapshotLatitude != null && it.startSnapshotLongitude != null
    }
    AndroidView(
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp),
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

            t0Points.forEach { participant ->
                val lat = participant.startSnapshotLatitude ?: return@forEach
                val lon = participant.startSnapshotLongitude ?: return@forEach
                val point = GeoPoint(lat, lon)
                boundsPoints += point
                mapView.overlays.add(
                    Marker(mapView).apply {
                        position = point
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        title = "${participant.boatName} T0"
                    }
                )
            }

            val selectedTrackGeo = selectedBoatTrack.map { GeoPoint(it.latitude, it.longitude) }
            if (selectedTrackGeo.size >= 2) {
                boundsPoints += selectedTrackGeo
                mapView.overlays.add(
                    Polyline(mapView).apply {
                        outlinePaint.color = android.graphics.Color.CYAN
                        outlinePaint.strokeWidth = 6f
                        setPoints(selectedTrackGeo)
                        title = "Ruta odabranog broda"
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
                            title = participant.boatName
                        }
                    )
                }
            }

            when {
                boundsPoints.size >= 2 -> mapView.zoomToBoundingBox(BoundingBox.fromGeoPointsSafe(boundsPoints), true, 48)
                boundsPoints.size == 1 -> {
                    mapView.controller.setCenter(boundsPoints.first())
                    mapView.controller.setZoom(16.0)
                }
            }
            mapView.invalidate()
        }
    )
}

private fun createGateBuoyDrawable(color: Int): GradientDrawable {
    return GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color)
        setStroke(2, android.graphics.Color.BLACK)
        setSize(22, 22)
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
private fun RegattaSection(
    title: String,
    cardColor: Color,
    content: @Composable () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(cardColor)
                .padding(12.dp)
        ) {
            Text(
                text = title,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

private fun formatEpoch(epochMillis: Long): String {
    return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(epochMillis))
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

private fun computeRaceScoreRows(
    live: RegattaLiveSnapshot,
    boats: List<RegattaBoatSummary>
): List<RaceScoreRow> {
    val boatMap = boats.associateBy { it.id }
    val t0 = live.countdownTargetEpochMs
    val scoringTargetGateId = live.scoringTargetGateId
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
                (ordered.count { it.correctedElapsedMs != null } + 1).toDouble()
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
        RaceScoreRow(
            boatId = item.participant.boatId,
            boatName = item.participant.boatName,
            skipperName = item.participant.skipperName.ifBlank { boat?.skipperName.orEmpty() },
            groupCode = item.participant.groupCode ?: boat?.groupCode.orEmpty(),
            status = item.participant.status,
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
            row.realElapsedMs?.let(::formatDurationFromMillis).orEmpty(),
            row.correctedElapsedMs?.let(::formatDurationFromMillis).orEmpty(),
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
    raceRowsByGroup: Map<String, List<RaceScoreRow>>,
    regattaRowsByGroup: Map<String, List<EventScoreRow>>,
    selectedRaceIds: List<String>
): String {
    val lines = mutableListOf<String>()
    lines += "TABLICA PLOVA"
    lines += "group,group_rank,boat_name,jedreno_time,penali_percent,korigirano_time,bodovi"
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
                csvCell(row.realElapsedMs?.let(::formatDurationFromMillis).orEmpty()),
                String.format(Locale.US, "%.1f", row.penaltyPercent),
                csvCell(row.correctedElapsedMs?.let(::formatDurationFromMillis).orEmpty()),
                String.format(Locale.US, "%.2f", row.racePoints)
            ).joinToString(",")
        }
    }
    lines += ""
    lines += "TABLICA REGATE"
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
