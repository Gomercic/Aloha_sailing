package com.aloha.startline.regatta

import com.aloha.startline.nas.NasCallResult
import com.aloha.startline.nas.NasDefaults
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

data class RegattaCreateResult(
    val eventId: String,
    val raceId: String?,
    val organizerCode: String?,
    val organizerToken: String?
)

data class RegattaJoinResult(
    val eventId: String,
    val raceId: String?,
    val boatId: String?
)

data class RegattaOrganizerAuthResult(
    val eventId: String,
    val organizerToken: String
)

data class RegattaEventLookupResult(
    val eventId: String,
    val name: String,
    val joinCode: String,
    val status: String,
    val startDate: String?,
    val endDate: String?
)

data class PublicRegattaEventSummary(
    val eventId: String,
    val name: String,
    val joinCode: String,
    val organizerName: String,
    val status: String,
    val startDate: String?,
    val endDate: String?,
    val raceEndTime: String?,
    val regattaLengthNm: Double,
    val updatedAt: String,
    val boatsCount: Int,
    val racesCount: Int,
    val pointsCount: Int
)

data class AdminRegattaEventSummary(
    val eventId: String,
    val name: String,
    val joinCode: String,
    val organizerCodeHash: String,
    val organizerName: String,
    val status: String,
    val isPublic: Boolean,
    val startDate: String?,
    val endDate: String?,
    val raceEndTime: String?,
    val regattaLengthNm: Double,
    val updatedAt: String,
    val boatsCount: Int,
    val racesCount: Int,
    val pointsCount: Int
)

data class RegattaSignalPoint(
    val latitude: Double,
    val longitude: Double,
    val epochMillis: Long
)

data class RegattaClientCrossingPayload(
    val gateId: String,
    val crossingEpochMillis: Long,
    val source: String = "client_auto"
)

data class RegattaTrackPointPayload(
    val latitude: Double,
    val longitude: Double,
    val epochMillis: Long,
    val speedKnots: Double?,
    val headingDeg: Double?
)

data class RegattaBoatTrackPoint(
    val latitude: Double,
    val longitude: Double,
    val epochMillis: Long,
    val speedKnots: Double?,
    val headingDeg: Double?
)

data class SuperuserLoginResult(
    val username: String,
    val superuserToken: String
)

data class AdminOrganizerSessionResult(
    val eventId: String,
    val organizerToken: String
)

class RegattaApiClient(
    client: OkHttpClient? = null
) {

    private val http: OkHttpClient = client ?: DEFAULT_CLIENT

    fun createEvent(
        draft: RegattaDraft
    ): NasCallResult<RegattaCreateResult> {
        val request = Request.Builder()
            .url("${NasDefaults.BASE_URL}/v1/regattas/events")
            .post(
                JSONObject().apply {
                    put("name", draft.name.trim())
                    put("join_code", draft.joinCode.trim())
                    put("organizer_name", draft.organizerName.trim())
                    put("organizer_code", draft.organizerCode.trim())
                    put("start_date", draft.startDate.trim())
                    put("end_date", draft.endDate.trim())
                    put("race_end_time", draft.raceEndTime.trim())
                    put("regatta_length_nm", draft.regattaLengthNm)
                    put("is_public", draft.isPublic)
                    put("max_boats", draft.maxBoats)
                }.toString().toRequestBody(JSON_MEDIA)
            )
            .header("X-API-Key", NasDefaults.API_KEY)
            .header("Accept", "application/json")
            .build()
        return execute(request) { body ->
            val json = JSONObject(body)
            RegattaCreateResult(
                eventId = json.getString("event_id"),
                raceId = json.optString("race_id").takeIf { it.isNotBlank() },
                organizerCode = json.optString("organizer_code").takeIf { it.isNotBlank() },
                organizerToken = json.optString("organizer_token").takeIf { it.isNotBlank() }
            )
        }
    }

    fun authenticateOrganizer(
        joinCode: String,
        organizerCode: String
    ): NasCallResult<RegattaOrganizerAuthResult> {
        val request = Request.Builder()
            .url("${NasDefaults.BASE_URL}/v1/regattas/events/organizer-auth")
            .post(
                JSONObject().apply {
                    put("join_code", joinCode.trim())
                    put("organizer_code", organizerCode.trim())
                }.toString().toRequestBody(JSON_MEDIA)
            )
            .header("X-API-Key", NasDefaults.API_KEY)
            .header("Accept", "application/json")
            .build()
        return execute(request) { body ->
            val json = JSONObject(body)
            RegattaOrganizerAuthResult(
                eventId = json.getString("event_id"),
                organizerToken = json.getString("organizer_token")
            )
        }
    }

    fun joinEvent(
        joinCode: String,
        draft: BoatRegistrationDraft,
        deviceId: String
    ): NasCallResult<RegattaJoinResult> {
        val request = Request.Builder()
            .url("${NasDefaults.BASE_URL}/v1/regattas/events/join")
            .post(
                JSONObject().apply {
                    put("join_code", joinCode.trim())
                    put("device_id", deviceId)
                    put("boat_name", draft.boatName.trim())
                    put("skipper_name", draft.skipperName.trim())
                    put("club_name", draft.clubName.trim())
                    put("group_code", draft.groupCode.trim())
                    draft.lengthValue.toDoubleOrNull()?.let { put("length_value", it) }
                    if (draft.lengthUnit.isNotBlank()) put("length_unit", draft.lengthUnit.trim())
                }.toString().toRequestBody(JSON_MEDIA)
            )
            .header("X-API-Key", NasDefaults.API_KEY)
            .header("Accept", "application/json")
            .build()
        return execute(request) { body ->
            val json = JSONObject(body)
            RegattaJoinResult(
                eventId = json.getString("event_id"),
                raceId = json.optString("race_id").takeIf { it.isNotBlank() },
                boatId = json.optString("boat_id").takeIf { it.isNotBlank() }
            )
        }
    }

    fun getEventByJoinCode(joinCode: String): NasCallResult<RegattaEventLookupResult> {
        val request = Request.Builder()
            .url("${NasDefaults.BASE_URL}/v1/regattas/events/by-join-code/${joinCode.trim()}")
            .get()
            .header("X-API-Key", NasDefaults.API_KEY)
            .header("Accept", "application/json")
            .build()
        return execute(request) { body ->
            val json = JSONObject(body)
            RegattaEventLookupResult(
                eventId = json.getString("event_id"),
                name = json.optString("name"),
                joinCode = json.optString("join_code"),
                status = json.optString("status"),
                startDate = json.optString("start_date").takeIf { it.isNotBlank() },
                endDate = json.optString("end_date").takeIf { it.isNotBlank() }
            )
        }
    }

    fun getEventSnapshot(eventId: String): NasCallResult<RegattaEventSnapshot> {
        val request = Request.Builder()
            .url("${NasDefaults.BASE_URL}/v1/regattas/events/${eventId.trim()}/snapshot")
            .get()
            .header("X-API-Key", NasDefaults.API_KEY)
            .header("Accept", "application/json")
            .build()
        return execute(request) { body ->
            parseEventSnapshot(JSONObject(body))
        }
    }

    fun listPublicEvents(): NasCallResult<List<PublicRegattaEventSummary>> {
        val request = Request.Builder()
            .url("${NasDefaults.BASE_URL}/v1/regattas/events/public")
            .get()
            .header("X-API-Key", NasDefaults.API_KEY)
            .header("Accept", "application/json")
            .build()
        return execute(request) { body ->
            val json = JSONObject(body)
            val events = json.optJSONArray("events") ?: JSONArray()
            buildList {
                for (i in 0 until events.length()) {
                    val item = events.optJSONObject(i) ?: continue
                    add(
                        PublicRegattaEventSummary(
                            eventId = item.optString("event_id"),
                            name = item.optString("name"),
                            joinCode = item.optString("join_code"),
                            organizerName = item.optString("organizer_name"),
                            status = item.optString("status"),
                            startDate = item.optString("start_date").takeIf { it.isNotBlank() },
                            endDate = item.optString("end_date").takeIf { it.isNotBlank() },
                            raceEndTime = item.optString("race_end_time").takeIf { it.isNotBlank() },
                            regattaLengthNm = item.optDouble("regatta_length_nm").takeIf { !it.isNaN() } ?: 0.0,
                            updatedAt = item.optString("updated_at"),
                            boatsCount = item.optInt("boats_count", 0),
                            racesCount = item.optInt("races_count", 0),
                            pointsCount = item.optInt("points_count", 0)
                        )
                    )
                }
            }
        }
    }

    fun superuserLogin(username: String, password: String): NasCallResult<SuperuserLoginResult> {
        val request = Request.Builder()
            .url("${NasDefaults.BASE_URL}/v1/admin/login")
            .post(
                JSONObject().apply {
                    put("username", username.trim())
                    put("password", password)
                }.toString().toRequestBody(JSON_MEDIA)
            )
            .header("X-API-Key", NasDefaults.API_KEY)
            .header("Accept", "application/json")
            .build()
        return execute(request) { body ->
            val json = JSONObject(body)
            SuperuserLoginResult(
                username = json.optString("username"),
                superuserToken = json.optString("superuser_token")
            )
        }
    }

    fun listAdminEvents(superuserToken: String): NasCallResult<List<AdminRegattaEventSummary>> {
        val request = Request.Builder()
            .url("${NasDefaults.BASE_URL}/v1/admin/regattas/events?superuser_token=${superuserToken.trim()}")
            .get()
            .header("X-API-Key", NasDefaults.API_KEY)
            .header("Accept", "application/json")
            .build()
        return execute(request) { body ->
            val json = JSONObject(body)
            val events = json.optJSONArray("events") ?: JSONArray()
            buildList {
                for (i in 0 until events.length()) {
                    val item = events.optJSONObject(i) ?: continue
                    add(
                        AdminRegattaEventSummary(
                            eventId = item.optString("event_id"),
                            name = item.optString("name"),
                            joinCode = item.optString("join_code"),
                            organizerCodeHash = item.optString("organizer_code_hash"),
                            organizerName = item.optString("organizer_name"),
                            status = item.optString("status"),
                            isPublic = item.optBoolean("is_public", false),
                            startDate = item.optString("start_date").takeIf { it.isNotBlank() },
                            endDate = item.optString("end_date").takeIf { it.isNotBlank() },
                            raceEndTime = item.optString("race_end_time").takeIf { it.isNotBlank() },
                            regattaLengthNm = item.optDouble("regatta_length_nm").takeIf { !it.isNaN() } ?: 0.0,
                            updatedAt = item.optString("updated_at"),
                            boatsCount = item.optInt("boats_count", 0),
                            racesCount = item.optInt("races_count", 0),
                            pointsCount = item.optInt("points_count", 0)
                        )
                    )
                }
            }
        }
    }

    fun deleteAdminEvent(eventId: String, superuserToken: String): NasCallResult<Unit> {
        val request = Request.Builder()
            .url("${NasDefaults.BASE_URL}/v1/admin/regattas/events/${eventId.trim()}?superuser_token=${superuserToken.trim()}")
            .delete()
            .header("X-API-Key", NasDefaults.API_KEY)
            .header("Accept", "application/json")
            .build()
        return execute(request) { Unit }
    }

    fun createAdminOrganizerSession(
        eventId: String,
        superuserToken: String
    ): NasCallResult<AdminOrganizerSessionResult> {
        val request = Request.Builder()
            .url("${NasDefaults.BASE_URL}/v1/admin/regattas/events/${eventId.trim()}/organizer-session?superuser_token=${superuserToken.trim()}")
            .post("{}".toRequestBody(JSON_MEDIA))
            .header("X-API-Key", NasDefaults.API_KEY)
            .header("Accept", "application/json")
            .build()
        return execute(request) { body ->
            val json = JSONObject(body)
            AdminOrganizerSessionResult(
                eventId = json.optString("event_id"),
                organizerToken = json.optString("organizer_token")
            )
        }
    }

    fun createRace(
        eventId: String,
        organizerToken: String,
        name: String,
        dayNumber: Int = 1
    ): NasCallResult<String> {
        val request = Request.Builder()
            .url("${NasDefaults.BASE_URL}/v1/regattas/events/${eventId.trim()}/races")
            .post(
                JSONObject().apply {
                    put("organizer_token", organizerToken)
                    put("name", name.trim())
                    put("day_number", dayNumber)
                }.toString().toRequestBody(JSON_MEDIA)
            )
            .header("X-API-Key", NasDefaults.API_KEY)
            .header("Accept", "application/json")
            .build()
        return execute(request) { body ->
            JSONObject(body).getString("race_id")
        }
    }

    fun updateBoatGroup(
        eventId: String,
        boatId: String,
        organizerToken: String,
        groupCode: String
    ): NasCallResult<Unit> {
        val request = Request.Builder()
            .url("${NasDefaults.BASE_URL}/v1/regattas/events/${eventId.trim()}/boats/${boatId.trim()}/group")
            .put(
                JSONObject().apply {
                    put("organizer_token", organizerToken)
                    put("group_code", groupCode.trim())
                }.toString().toRequestBody(JSON_MEDIA)
            )
            .header("X-API-Key", NasDefaults.API_KEY)
            .header("Accept", "application/json")
            .build()
        return execute(request) { Unit }
    }

    fun updateNoticeBoard(
        eventId: String,
        organizerToken: String,
        noticeText: String
    ): NasCallResult<Unit> {
        val request = Request.Builder()
            .url("${NasDefaults.BASE_URL}/v1/regattas/events/${eventId.trim()}/notice-board")
            .put(
                JSONObject().apply {
                    put("organizer_token", organizerToken)
                    put("notice_text", noticeText)
                }.toString().toRequestBody(JSON_MEDIA)
            )
            .header("X-API-Key", NasDefaults.API_KEY)
            .header("Accept", "application/json")
            .build()
        return execute(request) { Unit }
    }

    fun updateEvent(
        eventId: String,
        organizerToken: String,
        draft: RegattaDraft
    ): NasCallResult<Unit> {
        val request = Request.Builder()
            .url("${NasDefaults.BASE_URL}/v1/regattas/events/${eventId.trim()}")
            .put(
                JSONObject().apply {
                    put("organizer_token", organizerToken)
                    put("name", draft.name.trim())
                    put("join_code", draft.joinCode.trim())
                    put("organizer_name", draft.organizerName.trim())
                    put("organizer_code", draft.organizerCode.trim())
                    put("start_date", draft.startDate.trim())
                    put("end_date", draft.endDate.trim())
                    put("race_end_time", draft.raceEndTime.trim())
                    put("regatta_length_nm", draft.regattaLengthNm)
                    put("max_boats", draft.maxBoats)
                    put("is_public", draft.isPublic)
                }.toString().toRequestBody(JSON_MEDIA)
            )
            .header("X-API-Key", NasDefaults.API_KEY)
            .header("Accept", "application/json")
            .build()
        return execute(request) { Unit }
    }

    fun deleteBoat(
        eventId: String,
        boatId: String,
        organizerToken: String
    ): NasCallResult<Unit> {
        val request = Request.Builder()
            .url("${NasDefaults.BASE_URL}/v1/regattas/events/${eventId.trim()}/boats/${boatId.trim()}?organizer_token=${organizerToken.trim()}")
            .delete()
            .header("X-API-Key", NasDefaults.API_KEY)
            .header("Accept", "application/json")
            .build()
        return execute(request) { Unit }
    }

    fun updateCourse(
        raceId: String,
        gates: List<GateDraft>,
        organizerToken: String
    ): NasCallResult<Unit> {
        val request = Request.Builder()
            .url("${NasDefaults.BASE_URL}/v1/regattas/races/${raceId.trim()}/course")
            .put(
                JSONObject().apply {
                    put("organizer_token", organizerToken)
                    put(
                        "gates",
                        JSONArray().apply {
                            gates.forEach { gate ->
                                put(
                                    JSONObject().apply {
                                        put("order", gate.order)
                                        put("type", gate.type)
                                        put("name", gate.name)
                                        put("point_a", JSONObject().apply {
                                            put("latitude", gate.pointA.latitude)
                                            put("longitude", gate.pointA.longitude)
                                        })
                                        put("point_b", JSONObject().apply {
                                            put("latitude", gate.pointB.latitude)
                                            put("longitude", gate.pointB.longitude)
                                        })
                                    }
                                )
                            }
                        }
                    )
                }.toString().toRequestBody(JSON_MEDIA)
            )
            .header("X-API-Key", NasDefaults.API_KEY)
            .header("Accept", "application/json")
            .build()
        return execute(request) { Unit }
    }

    fun updateCountdown(
        raceId: String,
        organizerToken: String,
        countdownTargetEpochMs: Long
    ): NasCallResult<Unit> {
        val request = Request.Builder()
            .url("${NasDefaults.BASE_URL}/v1/regattas/races/${raceId.trim()}/countdown")
            .put(
                JSONObject().apply {
                    put("organizer_token", organizerToken)
                    put("countdown_target_epoch_ms", countdownTargetEpochMs)
                }.toString().toRequestBody(JSON_MEDIA)
            )
            .header("X-API-Key", NasDefaults.API_KEY)
            .header("Accept", "application/json")
            .build()
        return execute(request) { Unit }
    }

    fun updateRaceDetails(
        raceId: String,
        organizerToken: String,
        raceDate: String,
        startTime: String,
        endTime: String,
        raceLengthNm: Double
    ): NasCallResult<Unit> {
        val request = Request.Builder()
            .url("${NasDefaults.BASE_URL}/v1/regattas/races/${raceId.trim()}/details")
            .put(
                JSONObject().apply {
                    put("organizer_token", organizerToken)
                    put("race_date", raceDate.trim())
                    put("start_time", startTime.trim())
                    put("end_time", endTime.trim())
                    put("race_length_nm", raceLengthNm)
                }.toString().toRequestBody(JSON_MEDIA)
            )
            .header("X-API-Key", NasDefaults.API_KEY)
            .header("Accept", "application/json")
            .build()
        return execute(request) { Unit }
    }

    fun updateRaceState(
        raceId: String,
        organizerToken: String,
        state: String
    ): NasCallResult<Unit> {
        val request = Request.Builder()
            .url("${NasDefaults.BASE_URL}/v1/regattas/races/${raceId.trim()}/state")
            .put(
                JSONObject().apply {
                    put("organizer_token", organizerToken)
                    put("state", state)
                }.toString().toRequestBody(JSON_MEDIA)
            )
            .header("X-API-Key", NasDefaults.API_KEY)
            .header("Accept", "application/json")
            .build()
        return execute(request) { Unit }
    }

    fun updateScoringTargetGate(
        raceId: String,
        organizerToken: String,
        gateId: String
    ): NasCallResult<Unit> {
        val request = Request.Builder()
            .url("${NasDefaults.BASE_URL}/v1/regattas/races/${raceId.trim()}/scoring-target")
            .put(
                JSONObject().apply {
                    put("organizer_token", organizerToken)
                    put("gate_id", gateId.trim())
                }.toString().toRequestBody(JSON_MEDIA)
            )
            .header("X-API-Key", NasDefaults.API_KEY)
            .header("Accept", "application/json")
            .build()
        return execute(request) { Unit }
    }

    fun getLiveSnapshot(raceId: String): NasCallResult<RegattaLiveSnapshot> {
        val request = Request.Builder()
            .url("${NasDefaults.BASE_URL}/v1/regattas/races/${raceId.trim()}/live")
            .get()
            .header("X-API-Key", NasDefaults.API_KEY)
            .header("Accept", "application/json")
            .build()
        return execute(request) { body ->
            parseLiveSnapshot(JSONObject(body))
        }
    }

    fun createPenalty(
        raceId: String,
        boatId: String,
        organizerToken: String,
        type: String,
        value: Double?,
        reason: String
    ): NasCallResult<String> {
        val request = Request.Builder()
            .url("${NasDefaults.BASE_URL}/v1/regattas/races/${raceId.trim()}/penalties")
            .post(
                JSONObject().apply {
                    put("organizer_token", organizerToken)
                    put("boat_id", boatId)
                    put("type", type.trim())
                    if (value != null) put("value", value)
                    put("reason", reason.trim())
                }.toString().toRequestBody(JSON_MEDIA)
            )
            .header("X-API-Key", NasDefaults.API_KEY)
            .header("Accept", "application/json")
            .build()
        return execute(request) { body ->
            JSONObject(body).optString("penalty_id")
        }
    }

    fun overrideCrossingStatus(
        raceId: String,
        crossingId: String,
        organizerToken: String,
        status: String
    ): NasCallResult<Unit> {
        val request = Request.Builder()
            .url("${NasDefaults.BASE_URL}/v1/regattas/races/${raceId.trim()}/crossings/${crossingId.trim()}/override")
            .post(
                JSONObject().apply {
                    put("organizer_token", organizerToken.trim())
                    put("status", status.trim())
                }.toString().toRequestBody(JSON_MEDIA)
            )
            .header("X-API-Key", NasDefaults.API_KEY)
            .header("Accept", "application/json")
            .build()
        return execute(request) { Unit }
    }

    fun sendTrackBatch(
        raceId: String,
        boatId: String,
        points: List<RegattaTrackPointPayload>
    ): NasCallResult<Unit> {
        val request = Request.Builder()
            .url("${NasDefaults.BASE_URL}/v1/regattas/races/${raceId.trim()}/boats/${boatId.trim()}/track-batch")
            .post(
                JSONObject().apply {
                    put(
                        "points",
                        JSONArray().apply {
                            points.forEach { point ->
                                put(
                                    JSONObject().apply {
                                        put("latitude", point.latitude)
                                        put("longitude", point.longitude)
                                        put("epoch_millis", point.epochMillis)
                                        point.speedKnots?.let { put("speed_knots", it) }
                                        point.headingDeg?.let { put("heading_deg", it) }
                                    }
                                )
                            }
                        }
                    )
                }.toString().toRequestBody(JSON_MEDIA)
            )
            .header("X-API-Key", NasDefaults.API_KEY)
            .header("Accept", "application/json")
            .build()
        return execute(request) { Unit }
    }

    fun getBoatTrack(
        raceId: String,
        boatId: String
    ): NasCallResult<List<RegattaBoatTrackPoint>> {
        val request = Request.Builder()
            .url("${NasDefaults.BASE_URL}/v1/regattas/races/${raceId.trim()}/boats/${boatId.trim()}/track")
            .get()
            .header("X-API-Key", NasDefaults.API_KEY)
            .header("Accept", "application/json")
            .build()
        return execute(request) { body ->
            val json = JSONObject(body)
            val points = json.optJSONArray("points") ?: JSONArray()
            buildList {
                for (i in 0 until points.length()) {
                    val item = points.optJSONObject(i) ?: continue
                    add(
                        RegattaBoatTrackPoint(
                            latitude = item.optDouble("latitude"),
                            longitude = item.optDouble("longitude"),
                            epochMillis = item.optLong("epoch_millis"),
                            speedKnots = item.optDouble("speed_knots").takeIf { !it.isNaN() },
                            headingDeg = item.optDouble("heading_deg").takeIf { !it.isNaN() }
                        )
                    )
                }
            }
        }
    }

    fun sendSignalBatch(
        raceId: String,
        boatId: String,
        points: List<RegattaSignalPoint>
    ): NasCallResult<Unit> {
        val request = Request.Builder()
            .url("${NasDefaults.BASE_URL}/v1/regattas/races/${raceId.trim()}/boats/${boatId.trim()}/signal-batch")
            .post(
                JSONObject().apply {
                    put(
                        "points",
                        JSONArray().apply {
                            points.forEach { point ->
                                put(
                                    JSONObject().apply {
                                        put("latitude", point.latitude)
                                        put("longitude", point.longitude)
                                        put("epoch_millis", point.epochMillis)
                                    }
                                )
                            }
                        }
                    )
                }.toString().toRequestBody(JSON_MEDIA)
            )
            .header("X-API-Key", NasDefaults.API_KEY)
            .header("Accept", "application/json")
            .build()
        return execute(request) { Unit }
    }

    fun sendClientCrossing(
        raceId: String,
        boatId: String,
        crossing: RegattaClientCrossingPayload
    ): NasCallResult<Unit> {
        val request = Request.Builder()
            .url("${NasDefaults.BASE_URL}/v1/regattas/races/${raceId.trim()}/boats/${boatId.trim()}/crossings/client")
            .post(
                JSONObject().apply {
                    put("gate_id", crossing.gateId.trim())
                    put("crossing_epoch_ms", crossing.crossingEpochMillis)
                    put("source", crossing.source)
                }.toString().toRequestBody(JSON_MEDIA)
            )
            .header("X-API-Key", NasDefaults.API_KEY)
            .header("Accept", "application/json")
            .build()
        return execute(request) { Unit }
    }

    fun sendStartSnapshot(
        raceId: String,
        boatId: String,
        latitude: Double,
        longitude: Double,
        epochMillis: Long
    ): NasCallResult<Unit> {
        val request = Request.Builder()
            .url("${NasDefaults.BASE_URL}/v1/regattas/races/${raceId.trim()}/boats/${boatId.trim()}/start-snapshot")
            .post(
                JSONObject().apply {
                    put("latitude", latitude)
                    put("longitude", longitude)
                    put("epoch_millis", epochMillis)
                }.toString().toRequestBody(JSON_MEDIA)
            )
            .header("X-API-Key", NasDefaults.API_KEY)
            .header("Accept", "application/json")
            .build()
        return execute(request) { Unit }
    }

    private inline fun <T> execute(request: Request, parse: (String) -> T): NasCallResult<T> {
        return try {
            http.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val hint = if (body.isNotBlank()) body.take(200) else response.message
                    return NasCallResult.Err("HTTP ${response.code}: $hint")
                }
                NasCallResult.Ok(parse(body))
            }
        } catch (e: IOException) {
            NasCallResult.Err(e.message ?: "Network error")
        } catch (e: Exception) {
            NasCallResult.Err(e.message ?: "Response parsing error")
        }
    }

    private fun parseEventSnapshot(json: JSONObject): RegattaEventSnapshot {
        return RegattaEventSnapshot(
            eventId = json.getString("event_id"),
            name = json.optString("name"),
            joinCode = json.optString("join_code"),
            organizerName = json.optString("organizer_name"),
            startDate = json.optString("start_date").takeIf { it.isNotBlank() },
            endDate = json.optString("end_date").takeIf { it.isNotBlank() },
            raceEndTime = json.optString("race_end_time").takeIf { it.isNotBlank() },
            regattaLengthNm = json.optDouble("regatta_length_nm").takeIf { !it.isNaN() } ?: 0.0,
            isPublic = json.optBoolean("is_public", false),
            maxBoats = json.optInt("max_boats", 50),
            noticeBoard = json.optString("notice_board"),
            noticeBoardUpdatedAt = json.optString("notice_board_updated_at").takeIf { it.isNotBlank() },
            noticePosts = parseNoticePosts(json.optJSONArray("notice_posts")),
            status = json.optString("status", "draft"),
            boats = parseBoats(json.optJSONArray("boats")),
            races = parseRaces(json.optJSONArray("races")),
            activeRaceId = json.optString("active_race_id").takeIf { it.isNotBlank() }
        )
    }

    private fun parseLiveSnapshot(json: JSONObject): RegattaLiveSnapshot {
        return RegattaLiveSnapshot(
            eventId = json.getString("event_id"),
            raceId = json.getString("race_id"),
            raceName = json.optString("race_name"),
            state = json.optString("state", "draft"),
            countdownTargetEpochMs = json.optLong("countdown_target_epoch_ms").takeIf { it > 0L },
            scoringTargetGateId = json.optString("scoring_target_gate_id").takeIf { it.isNotBlank() },
            gates = parseGates(json.optJSONArray("gates")),
            participants = parseParticipants(json.optJSONArray("participants")),
            crossings = parseCrossings(json.optJSONArray("crossings")),
            penalties = parsePenalties(json.optJSONArray("penalties"))
        )
    }

    private fun parseBoats(array: JSONArray?): List<RegattaBoatSummary> {
        if (array == null) return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                add(
                    RegattaBoatSummary(
                        id = item.optString("id"),
                        deviceId = item.optString("device_id").takeIf { it.isNotBlank() },
                        boatName = item.optString("boat_name"),
                        skipperName = item.optString("skipper_name"),
                        clubName = item.optString("club_name"),
                        lengthValue = item.optDouble("length_value").takeIf { !it.isNaN() },
                        lengthUnit = item.optString("length_unit").takeIf { it.isNotBlank() },
                        groupCode = item.optString("group_code").takeIf { it.isNotBlank() }
                    )
                )
            }
        }
    }

    private fun parseRaces(array: JSONArray?): List<RegattaRaceSummary> {
        if (array == null) return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                add(
                    RegattaRaceSummary(
                        id = item.optString("id"),
                        name = item.optString("name"),
                        dayNumber = item.optInt("day_number", 1),
                        sequenceNumber = item.optInt("sequence_number", i + 1),
                        state = item.optString("state", "draft"),
                        countdownTargetEpochMs = item.optLong("countdown_target_epoch_ms")
                            .takeIf { it > 0L },
                        scoringTargetGateId = item.optString("scoring_target_gate_id").takeIf { it.isNotBlank() },
                        raceDate = item.optString("race_date").takeIf { it.isNotBlank() },
                        startTime = item.optString("start_time").takeIf { it.isNotBlank() },
                        endTime = item.optString("end_time").takeIf { it.isNotBlank() },
                        raceLengthNm = item.optDouble("race_length_nm").takeIf { !it.isNaN() } ?: 0.0,
                        gates = parseGates(item.optJSONArray("gates"))
                    )
                )
            }
        }
    }

    private fun parseGates(array: JSONArray?): List<RegattaGate> {
        if (array == null) return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val pointA = item.optJSONObject("point_a") ?: continue
                val pointB = item.optJSONObject("point_b") ?: continue
                add(
                    RegattaGate(
                        id = item.optString("id"),
                        order = item.optInt("order", i),
                        type = item.optString("type"),
                        name = item.optString("name"),
                        pointA = RegattaPoint(
                            latitude = pointA.optDouble("latitude"),
                            longitude = pointA.optDouble("longitude")
                        ),
                        pointB = RegattaPoint(
                            latitude = pointB.optDouble("latitude"),
                            longitude = pointB.optDouble("longitude")
                        )
                    )
                )
            }
        }
    }

    private fun parseParticipants(array: JSONArray?): List<RegattaParticipantLive> {
        if (array == null) return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                add(
                    RegattaParticipantLive(
                        boatId = item.optString("boat_id"),
                        boatName = item.optString("boat_name"),
                        skipperName = item.optString("skipper_name"),
                        groupCode = item.optString("group_code").takeIf { it.isNotBlank() },
                        status = item.optString("status", "joined"),
                        startSnapshotEpochMs = item.optLong("start_snapshot_epoch_ms").takeIf { it > 0L },
                        startSnapshotLatitude = item.optDouble("start_snapshot_latitude").takeIf { !it.isNaN() },
                        startSnapshotLongitude = item.optDouble("start_snapshot_longitude").takeIf { !it.isNaN() },
                        lastSignalEpochMs = item.optLong("last_signal_epoch_ms").takeIf { it > 0L },
                        lastLatitude = item.optDouble("last_latitude").takeIf { !it.isNaN() },
                        lastLongitude = item.optDouble("last_longitude").takeIf { !it.isNaN() },
                        lastSpeedKnots = item.optDouble("last_speed_knots").takeIf { !it.isNaN() },
                        nextGateOrder = item.optInt("next_gate_order", 0),
                        finishedAtEpochMs = item.optLong("finished_at_epoch_ms").takeIf { it > 0L }
                    )
                )
            }
        }
    }

    private fun parseCrossings(array: JSONArray?): List<RegattaCrossingSummary> {
        if (array == null) return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                add(
                    RegattaCrossingSummary(
                        id = item.optString("id"),
                        boatId = item.optString("boat_id"),
                        boatName = item.optString("boat_name"),
                        gateId = item.optString("gate_id"),
                        gateName = item.optString("gate_name"),
                        gateOrder = item.optInt("gate_order", 0),
                        gateType = item.optString("gate_type"),
                        crossingEpochMs = item.optLong("crossing_epoch_ms"),
                        source = item.optString("source", "auto"),
                        status = item.optString("status", "recorded")
                    )
                )
            }
        }
    }

    private fun parsePenalties(array: JSONArray?): List<RegattaPenaltySummary> {
        if (array == null) return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                add(
                    RegattaPenaltySummary(
                        id = item.optString("id"),
                        boatId = item.optString("boat_id"),
                        boatName = item.optString("boat_name"),
                        type = item.optString("type"),
                        value = item.optDouble("value").takeIf { !it.isNaN() },
                        reason = item.optString("reason"),
                        createdAt = item.optString("created_at")
                    )
                )
            }
        }
    }

    private fun parseNoticePosts(array: JSONArray?): List<RegattaNoticePost> {
        if (array == null) return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                add(
                    RegattaNoticePost(
                        id = item.optString("id"),
                        noticeText = item.optString("notice_text"),
                        publishedAt = item.optString("published_at").takeIf { it.isNotBlank() }
                    )
                )
            }
        }
    }

    companion object {
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

        private val DEFAULT_CLIENT: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}
