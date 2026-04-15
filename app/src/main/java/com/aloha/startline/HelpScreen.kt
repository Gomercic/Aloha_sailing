package com.aloha.startline

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aloha.startline.nas.NasCallResult
import com.aloha.startline.regatta.AdminRegattaEventSummary
import com.aloha.startline.regatta.RegattaApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

/**
 * Scrollable in-app help (English). Styled for dark background (parent provides black).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HelpScreen(
    modifier: Modifier = Modifier,
    onOpenOrganizerFromSuperuser: (eventId: String, organizerToken: String) -> Unit = { _, _ -> }
) {
    val scroll = rememberScrollState()
    val bodyStyle = MaterialTheme.typography.bodyMedium.copy(
        color = Color.White,
        fontSize = 14.sp,
        lineHeight = 20.sp
    )
    val sectionTitleStyle = MaterialTheme.typography.titleMedium.copy(
        color = Color.White,
        fontWeight = FontWeight.Bold,
        fontSize = 17.sp
    )
    val subStyle = bodyStyle.copy(fontWeight = FontWeight.SemiBold)
    val scope = rememberCoroutineScope()
    val apiClient = remember { RegattaApiClient() }
    var showSuperuserLogin by remember { mutableStateOf(false) }
    var superuserUsername by remember { mutableStateOf("") }
    var superuserPassword by remember { mutableStateOf("") }
    var superuserToken by remember { mutableStateOf("") }
    var superuserLoggedInAs by remember { mutableStateOf("") }
    var superuserBusy by remember { mutableStateOf(false) }
    var superuserStatusMessage by remember { mutableStateOf<String?>(null) }
    var superuserErrorMessage by remember { mutableStateOf<String?>(null) }
    var adminEvents by remember { mutableStateOf<List<AdminRegattaEventSummary>>(emptyList()) }
    var pendingDeleteEvent by remember { mutableStateOf<AdminRegattaEventSummary?>(null) }
    var deleteConfirmVisible by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            text = "The app has three main screens:",
            style = sectionTitleStyle
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "• Start Line — pre-start sailing procedure\n" +
                "• Wind Shift — tracking wind-direction changes during a race from boat course\n" +
                "• Anchoring — anchoring alarm",
            style = bodyStyle
        )
        Spacer(Modifier.height(16.dp))

        Text(
            text = "Tapping the screen title switches between Start Line and Wind Shift. " +
                "To the left of the Menu button, boat speed over ground (SOG) is shown.",
            style = bodyStyle
        )
        Spacer(Modifier.height(20.dp))

        Text(text = "1. Start Line", style = sectionTitleStyle)
        Spacer(Modifier.height(6.dp))
        Text(
            text = "All choices on this screen use a double-tap.",
            style = subStyle
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "This screen helps with the pre-start sequence.",
            style = bodyStyle
        )
        Spacer(Modifier.height(12.dp))

        Text(text = "Top — countdown timer", style = subStyle)
        Spacer(Modifier.height(4.dp))
        Text(
            text = "• The start time for the pre-start countdown is set in Settings.\n" +
                "• Double-tap the timer digits to snap and round to the nearest full minute.\n" +
                "• Use + and − to change the value by one minute.\n" +
                "• Start begins the countdown.\n" +
                "• The timer plays audio signals every minute, every half minute, and every 10 seconds " +
                "in the last 30 seconds before the start.",
            style = bodyStyle
        )
        Spacer(Modifier.height(12.dp))

        Text(text = "Middle — buoys and approach", style = subStyle)
        Spacer(Modifier.height(4.dp))
        Text(
            text = "• Set L and Set R store the boat’s position at that moment as the left and right " +
                "mark of the start line. A mark can be cleared and set again. When both marks are set, " +
                "track recording begins.\n" +
                "• Between the buttons you see their distance (line length) and the time needed to sail " +
                "that distance at the speed shown above.\n" +
                "• The speed above the buttons is the estimated speed of approach perpendicular to the " +
                "start line (not SOG). Adjust it with + and −. During the pre-start, you can capture " +
                "the current approach speed with a double-tap on the speed digits.",
            style = bodyStyle
        )
        Spacer(Modifier.height(12.dp))

        Text(text = "Status box and map", style = subStyle)
        Spacer(Modifier.height(4.dp))
        Text(
            text = "• Below that, a panel shows perpendicular distance to the start line and time to " +
                "reach it at the speed set above. (In Settings you can enter the distance from the bow to the GPS antenna.) " +
                "A negative distance means the boat is on the wrong " +
                "side of the line. Negative time means you would reach the line too soon. In those cases " +
                "the panel turns red (invalid start). When values are positive it is green. If the boat " +
                "is outside the line’s perpendicular zone, distance and time refer to the nearest mark.\n" +
                "• The map shows both marks, the boat, and the track. Double-tap the upper-left quarter " +
                "to toggle North up / Start line up. Double-tap the lower-left quarter to switch between " +
                "the graphic map and OpenStreetMap. The right side is for zooming (double-tap).\n" +
                "• Reset / Stop REC resets the timer to the initial value and stops track recording. " +
                "While recording, REC is shown at the top.",
            style = bodyStyle
        )
        Spacer(Modifier.height(20.dp))

        Text(text = "2. Wind Shift", style = sectionTitleStyle)
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Selections use a single tap.",
            style = subStyle
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "This screen records wind-direction history from how the boat sails upwind or downwind. " +
                "The upper graph shows deviation from the average wind direction.",
            style = bodyStyle
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Modes:\n" +
                "• Single mode — boat sails only one tack toward the wind (including straight downwind).\n" +
                "• Dual mode — at least two upwind tacks (both tacks).\n\n" +
                "In single mode, before the first tack you can choose port or starboard tack from the " +
                "lower-left area of the graph.\n\n" +
                "In dual mode, after at least two tacks you can tap the upper-left of the graph to choose " +
                "Normal or Auto graph. They control how deviation is plotted. In Auto, positive deviation " +
                "is always in the wind direction (port tack vs starboard tack). The bar at the top of the " +
                "graph turns green if the boat sails more than 5° into the wind vs the average, red if " +
                "more than 5° off the wind, grey within ±5°.",
            style = bodyStyle
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "The lower map panel sets the averaging window in minutes (shown on the right). " +
                "The track uses a thicker line for the segment used for the average wind direction. " +
                "Tap the upper-left of the map area to toggle North up / wind-axis up. Tap the lower-left " +
                "to switch graphic map / OpenStreetMap.",
            style = bodyStyle
        )
        Spacer(Modifier.height(20.dp))

        Text(text = "3. Anchoring", style = sectionTitleStyle)
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Screen for setting an alarm when anchored. If the boat leaves the defined SAFE zone, " +
                "the alarm sounds.",
            style = bodyStyle
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Set anchor stores the anchor position. SAFE zone can be Circle or Cone; parameters " +
                "(radius, cone width, cone direction, apex offset) can be edited numerically or by " +
                "dragging up/down on the screen.",
            style = bodyStyle
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "The map shows anchor, boat, track, and SAFE zone. Boat position uses a ~10 s average " +
                "and updates about every 30 s. You can switch graphic map / OpenStreetMap and toggle " +
                "auto zoom.",
            style = bodyStyle
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Follow anchoring via internet lets one Android device send anchoring and boat data " +
                "to another over the internet. Tap the control to show fields. Enter the same ship code " +
                "on both devices. On the boat, use Sending mode and Start. On the device you carry ashore, " +
                "use Receiving mode and Start. In Receiving mode you cannot set the anchor location " +
                "(it comes from the network); other SAFE zone settings still apply on that device.",
            style = bodyStyle
        )
        Spacer(Modifier.height(20.dp))

        Text(text = "Track log", style = sectionTitleStyle)
        Spacer(Modifier.height(6.dp))
        Text(
            text = "List of recorded tracks. Long-press a track for Show on map, Delete, or Share. " +
                "Tracks are saved as GPX with start-line mark positions and boat position at race start.",
            style = bodyStyle
        )
        Spacer(Modifier.height(20.dp))

        Text(text = "Background and exit", style = sectionTitleStyle)
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Features keep running in the background until you exit with Exit from the menu or " +
                "turn each feature off inside the app (recording, anchoring alarm, timer, etc.).",
            style = bodyStyle
        )
        Spacer(Modifier.height(20.dp))

        Text(
            text = "Batman, say osam (8).",
            style = bodyStyle
        )
        Spacer(Modifier.height(12.dp))

        Text(
            text = "If you enjoy the app, buy us a beer when you meet us.\n" +
                "— Crew Aloha, BluSail24",
            style = bodyStyle.copy(fontStyle = FontStyle.Italic)
        )
        Spacer(Modifier.height(28.dp))

        Text(text = "Superuser", style = sectionTitleStyle)
        Spacer(Modifier.height(8.dp))
        Button(
            enabled = !superuserBusy,
            onClick = {
                showSuperuserLogin = true
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF424242))
        ) {
            Text("Login superuser")
        }

        superuserStatusMessage?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = Color(0xFFB8E0D0), style = bodyStyle)
        }
        superuserErrorMessage?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = Color(0xFFFF8A80), style = bodyStyle)
        }

        if (superuserToken.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Prijavljen: $superuserLoggedInAs", color = Color.White, fontSize = 13.sp)
                Spacer(Modifier.weight(1f))
                Button(
                    enabled = !superuserBusy,
                    onClick = {
                        scope.launch {
                            if (superuserToken.isBlank()) return@launch
                            superuserBusy = true
                            superuserErrorMessage = null
                            when (val result = withContext(Dispatchers.IO) {
                                apiClient.listAdminEvents(superuserToken)
                            }) {
                                is NasCallResult.Ok -> {
                                    adminEvents = result.value
                                    superuserStatusMessage = "Regatta list refreshed."
                                }
                                is NasCallResult.Err -> superuserErrorMessage = result.message
                            }
                            superuserBusy = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF424242))
                ) {
                    Text("Refresh")
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        superuserToken = ""
                        superuserLoggedInAs = ""
                        adminEvents = emptyList()
                        superuserStatusMessage = "Superuser logged out."
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8E2424))
                ) {
                    Text("Logout")
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = "All regattas (${adminEvents.size}) — long press to delete",
                style = subStyle
            )
            Spacer(Modifier.height(8.dp))
            if (adminEvents.isEmpty()) {
                Text("No regattas to display.", color = Color(0xFFCFD8DC), style = bodyStyle)
            } else {
                adminEvents.forEach { event ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = { },
                                onLongClick = {
                                    pendingDeleteEvent = event
                                    deleteConfirmVisible = true
                                }
                            )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp)
                        ) {
                            Text(event.name, color = Color.White, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Public: ${if (event.isPublic) "Yes" else "No"}",
                                    color = Color(0xFFCFD8DC),
                                    fontSize = 12.sp
                                )
                                Spacer(Modifier.weight(1f))
                                Text(
                                    text = "Status: ${event.status}",
                                    color = Color(0xFFCFD8DC),
                                    fontSize = 12.sp
                                )
                            }
                            Text(
                                text = "Dates: ${event.startDate ?: "--"} -> ${event.endDate ?: "--"}",
                                color = Color(0xFFCFD8DC),
                                fontSize = 12.sp
                            )
                            Text(
                                text = "Join: ${event.joinCode} · Org: ${event.organizerName.ifBlank { "--" }}",
                                color = Color(0xFFCFD8DC),
                                fontSize = 12.sp
                            )
                            Text(
                                text = "Organizer hash: ${event.organizerCodeHash.ifBlank { "--" }}",
                                color = Color(0xFFCFD8DC),
                                fontSize = 12.sp
                            )
                            Text(
                                text = "Races: ${event.racesCount}, Points: ${event.pointsCount}, Boats: ${event.boatsCount}",
                                color = Color(0xFFCFD8DC),
                                fontSize = 12.sp
                            )
                            Spacer(Modifier.height(6.dp))
                            Button(
                                enabled = !superuserBusy,
                                onClick = {
                                    scope.launch {
                                        if (superuserToken.isBlank()) {
                                            superuserErrorMessage = "No active superuser token."
                                            return@launch
                                        }
                                        superuserBusy = true
                                        superuserErrorMessage = null
                                        superuserStatusMessage = null
                                        when (val openResult = withContext(Dispatchers.IO) {
                                            apiClient.createAdminOrganizerSession(event.eventId, superuserToken)
                                        }) {
                                            is NasCallResult.Ok -> {
                                                superuserStatusMessage = "Opening organizer editor for '${event.name}'."
                                                onOpenOrganizerFromSuperuser(
                                                    openResult.value.eventId,
                                                    openResult.value.organizerToken
                                                )
                                            }
                                            is NasCallResult.Err -> superuserErrorMessage = openResult.message
                                        }
                                        superuserBusy = false
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0))
                            ) {
                                Text(if (superuserBusy) "Working..." else "Open as organizer")
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }
        }
        Spacer(Modifier.height(32.dp))
    }

    if (deleteConfirmVisible && pendingDeleteEvent != null) {
        val event = pendingDeleteEvent!!
        AlertDialog(
            onDismissRequest = {
                deleteConfirmVisible = false
                pendingDeleteEvent = null
            },
            title = { Text("Confirm deletion") },
            text = {
                Text(
                    "Delete regatta '${event.name}'?\n\n" +
                        "This will also delete all associated races, boats, and results."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (superuserToken.isBlank()) {
                            superuserErrorMessage = "Superuser token not active."
                            deleteConfirmVisible = false
                            pendingDeleteEvent = null
                            return@Button
                        }
                        val eventId = event.eventId
                        val eventName = event.name
                        deleteConfirmVisible = false
                        pendingDeleteEvent = null
                        scope.launch {
                            superuserBusy = true
                            superuserErrorMessage = null
                            superuserStatusMessage = null
                            when (val deleteResult = withContext(Dispatchers.IO) {
                                apiClient.deleteAdminEvent(eventId, superuserToken)
                            }) {
                                is NasCallResult.Ok -> {
                                    superuserStatusMessage = "Regatta '$eventName' deleted."
                                    when (val listResult = withContext(Dispatchers.IO) {
                                        apiClient.listAdminEvents(superuserToken)
                                    }) {
                                        is NasCallResult.Ok -> adminEvents = listResult.value
                                        is NasCallResult.Err -> superuserErrorMessage = listResult.message
                                    }
                                }
                                is NasCallResult.Err -> superuserErrorMessage = deleteResult.message
                            }
                            superuserBusy = false
                        }
                    }
                ) { Text("Yes") }
            },
            dismissButton = {
                Button(
                    onClick = {
                        deleteConfirmVisible = false
                        pendingDeleteEvent = null
                    }
                ) { Text("No") }
            }
        )
    }

    if (showSuperuserLogin) {
        AlertDialog(
            onDismissRequest = { showSuperuserLogin = false },
            title = { Text("Login superuser") },
            text = {
                Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = superuserUsername,
                        onValueChange = { superuserUsername = it },
                        label = { Text("Username") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = superuserPassword,
                        onValueChange = { superuserPassword = it },
                        label = { Text("Password") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = !superuserBusy,
                    onClick = {
                        scope.launch {
                            superuserBusy = true
                            superuserErrorMessage = null
                            superuserStatusMessage = null
                            when (val loginResult = withContext(Dispatchers.IO) {
                                apiClient.superuserLogin(superuserUsername, superuserPassword)
                            }) {
                                is NasCallResult.Ok -> {
                                    superuserToken = loginResult.value.superuserToken
                                    superuserLoggedInAs = loginResult.value.username
                                    superuserStatusMessage = "Prijavljen: ${loginResult.value.username}"
                                    when (val listResult = withContext(Dispatchers.IO) {
                                        apiClient.listAdminEvents(superuserToken)
                                    }) {
                                        is NasCallResult.Ok -> adminEvents = listResult.value
                                        is NasCallResult.Err -> superuserErrorMessage = listResult.message
                                    }
                                    showSuperuserLogin = false
                                }
                                is NasCallResult.Err -> superuserErrorMessage = loginResult.message
                            }
                            superuserBusy = false
                        }
                    }
                ) { Text(if (superuserBusy) "Working..." else "Login") }
            },
            dismissButton = {
                Button(onClick = { showSuperuserLogin = false }) { Text("Cancel") }
            }
        )
    }
}
