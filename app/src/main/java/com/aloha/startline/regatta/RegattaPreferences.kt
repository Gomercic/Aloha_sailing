package com.aloha.startline.regatta

import android.content.Context
import java.util.UUID
import org.json.JSONObject

class RegattaPreferences(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    val deviceId: String
        get() {
            val existing = prefs.getString(KEY_DEVICE_ID, null)
            if (!existing.isNullOrBlank()) return existing
            val created = UUID.randomUUID().toString()
            prefs.edit().putString(KEY_DEVICE_ID, created).apply()
            return created
        }

    val organizerName: String
        get() = prefs.getString(KEY_ORGANIZER_NAME, "")?.trim().orEmpty()

    val joinedEventId: String
        get() = prefs.getString(KEY_JOINED_EVENT_ID, "")?.trim().orEmpty()

    val joinedRaceId: String
        get() = prefs.getString(KEY_JOINED_RACE_ID, "")?.trim().orEmpty()

    val joinedBoatId: String
        get() = prefs.getString(KEY_JOINED_BOAT_ID, "")?.trim().orEmpty()

    val organizerToken: String
        get() = prefs.getString(KEY_ORGANIZER_TOKEN, "")?.trim().orEmpty()

    val organizerEventIds: List<String>
        get() = readOrganizerSessions().keys.toList().sorted()

    val lastJoinCode: String
        get() = prefs.getString(KEY_LAST_JOIN_CODE, "")?.trim().orEmpty()

    val lastOrganizerAccessValue: String
        get() = prefs.getString(KEY_LAST_ORGANIZER_ACCESS_VALUE, "")?.trim().orEmpty()

    val lastBoatName: String
        get() = prefs.getString(KEY_LAST_BOAT_NAME, "")?.trim().orEmpty()

    val lastSkipperName: String
        get() = prefs.getString(KEY_LAST_SKIPPER_NAME, "")?.trim().orEmpty()

    val lastClubName: String
        get() = prefs.getString(KEY_LAST_CLUB_NAME, "")?.trim().orEmpty()

    val lastBoatLengthValue: String
        get() = prefs.getString(KEY_LAST_BOAT_LENGTH_VALUE, "")?.trim().orEmpty()

    val lastBoatLengthUnit: String
        get() = prefs.getString(KEY_LAST_BOAT_LENGTH_UNIT, "m")?.trim().orEmpty().ifBlank { "m" }

    fun saveOrganizerName(name: String) {
        prefs.edit().putString(KEY_ORGANIZER_NAME, name.trim()).apply()
    }

    fun saveJoinedEvent(eventId: String, raceId: String?, boatId: String?) {
        prefs.edit()
            .putString(KEY_JOINED_EVENT_ID, eventId.trim())
            .putString(KEY_JOINED_RACE_ID, raceId?.trim().orEmpty())
            .putString(KEY_JOINED_BOAT_ID, boatId?.trim().orEmpty())
            .apply()
    }

    fun saveOrganizerToken(token: String) {
        prefs.edit().putString(KEY_ORGANIZER_TOKEN, token.trim()).apply()
    }

    fun saveLastJoinCode(value: String) {
        prefs.edit().putString(KEY_LAST_JOIN_CODE, value.trim().uppercase()).apply()
    }

    fun saveLastOrganizerAccessValue(value: String) {
        prefs.edit().putString(KEY_LAST_ORGANIZER_ACCESS_VALUE, value.trim()).apply()
    }

    fun saveLastBoatDetails(
        boatName: String,
        skipperName: String,
        clubName: String,
        lengthValue: String,
        lengthUnit: String
    ) {
        prefs.edit()
            .putString(KEY_LAST_BOAT_NAME, boatName.trim())
            .putString(KEY_LAST_SKIPPER_NAME, skipperName.trim())
            .putString(KEY_LAST_CLUB_NAME, clubName.trim())
            .putString(KEY_LAST_BOAT_LENGTH_VALUE, lengthValue.trim())
            .putString(KEY_LAST_BOAT_LENGTH_UNIT, lengthUnit.trim().ifBlank { "m" })
            .apply()
    }

    fun saveOrganizerSession(eventId: String, token: String) {
        val normalizedEventId = eventId.trim()
        val normalizedToken = token.trim()
        if (normalizedEventId.isBlank() || normalizedToken.isBlank()) return
        val sessions = readOrganizerSessions()
        sessions[normalizedEventId] = normalizedToken
        prefs.edit()
            .putString(KEY_ORGANIZER_TOKEN, normalizedToken)
            .putString(KEY_ORGANIZER_SESSIONS, JSONObject(sessions as Map<*, *>).toString())
            .apply()
    }

    fun organizerTokenForEvent(eventId: String): String {
        return readOrganizerSessions()[eventId.trim()].orEmpty()
    }

    fun eventIdForOrganizerToken(token: String): String? {
        val normalized = token.trim()
        if (normalized.isBlank()) return null
        return readOrganizerSessions()
            .entries
            .firstOrNull { it.value.equals(normalized, ignoreCase = true) }
            ?.key
    }

    fun clearJoinedEvent() {
        prefs.edit()
            .remove(KEY_JOINED_EVENT_ID)
            .remove(KEY_JOINED_RACE_ID)
            .remove(KEY_JOINED_BOAT_ID)
            .remove(KEY_ORGANIZER_TOKEN)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "regatta_sync"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_ORGANIZER_NAME = "organizer_name"
        private const val KEY_JOINED_EVENT_ID = "joined_event_id"
        private const val KEY_JOINED_RACE_ID = "joined_race_id"
        private const val KEY_JOINED_BOAT_ID = "joined_boat_id"
        private const val KEY_ORGANIZER_TOKEN = "organizer_token"
        private const val KEY_ORGANIZER_SESSIONS = "organizer_sessions"
        private const val KEY_LAST_JOIN_CODE = "last_join_code"
        private const val KEY_LAST_ORGANIZER_ACCESS_VALUE = "last_organizer_access_value"
        private const val KEY_LAST_BOAT_NAME = "last_boat_name"
        private const val KEY_LAST_SKIPPER_NAME = "last_skipper_name"
        private const val KEY_LAST_CLUB_NAME = "last_club_name"
        private const val KEY_LAST_BOAT_LENGTH_VALUE = "last_boat_length_value"
        private const val KEY_LAST_BOAT_LENGTH_UNIT = "last_boat_length_unit"
    }

    private fun readOrganizerSessions(): MutableMap<String, String> {
        val raw = prefs.getString(KEY_ORGANIZER_SESSIONS, "")?.trim().orEmpty()
        if (raw.isBlank()) return mutableMapOf()
        return runCatching {
            val json = JSONObject(raw)
            buildMap {
                val keys = json.keys()
                while (keys.hasNext()) {
                    val key = keys.next().trim()
                    val value = json.optString(key).trim()
                    if (key.isNotBlank() && value.isNotBlank()) {
                        put(key, value)
                    }
                }
            }.toMutableMap()
        }.getOrElse { mutableMapOf() }
    }
}
