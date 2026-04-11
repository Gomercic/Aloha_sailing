package com.aloha.startline.nas

import org.json.JSONArray
import org.json.JSONObject

data class NasTrackPoint(
    val latitude: Double,
    val longitude: Double,
    val epochMillis: Long
)

/**
 * JSON koji ide u [data] polje PUT /v1/ships/{code}/telemetry/latest.
 */
data class NasAnchoringPayload(
    val anchorLat: Double?,
    val anchorLon: Double?,
    val areaMode: String,
    val radiusMeters: Double,
    val segmentCenterDeg: Double,
    val segmentWidthDeg: Double,
    val coneApexOffsetMeters: Double,
    val alarmEnabled: Boolean,
    val trackPoints: List<NasTrackPoint>
) {

    fun toDataJson(): JSONObject {
        val data = JSONObject()
        if (anchorLat != null) data.put("anchor_lat", anchorLat)
        if (anchorLon != null) data.put("anchor_lon", anchorLon)
        data.put("area_mode", areaMode)
        data.put("radius_meters", radiusMeters)
        data.put("segment_center_deg", segmentCenterDeg)
        data.put("segment_width_deg", segmentWidthDeg)
        data.put("cone_apex_offset_meters", coneApexOffsetMeters)
        data.put("alarm_enabled", alarmEnabled)
        if (trackPoints.isNotEmpty()) {
            val arr = JSONArray()
            for (p in trackPoints) {
                arr.put(
                    JSONObject().apply {
                        put("latitude", p.latitude)
                        put("longitude", p.longitude)
                        put("epoch_millis", p.epochMillis)
                    }
                )
            }
            data.put("track_points", arr)
        }
        return data
    }

    companion object {
        fun fromTelemetryJson(root: JSONObject): NasAnchoringPayload? {
            val data = root.optJSONObject("payload") ?: return null
            val lat = if (data.has("anchor_lat") && !data.isNull("anchor_lat")) {
                data.getDouble("anchor_lat")
            } else null
            val lon = if (data.has("anchor_lon") && !data.isNull("anchor_lon")) {
                data.getDouble("anchor_lon")
            } else null
            val mode = data.optString("area_mode", "circle").lowercase()
            val radius = data.optDouble("radius_meters", 35.0)
            val segC = data.optDouble("segment_center_deg", 0.0)
            val segW = data.optDouble("segment_width_deg", 120.0)
            val apex = data.optDouble("cone_apex_offset_meters", -5.0)
            val alarm = data.optBoolean("alarm_enabled", true)
            val track = mutableListOf<NasTrackPoint>()
            val arr = data.optJSONArray("track_points")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    track.add(
                        NasTrackPoint(
                            latitude = o.optDouble("latitude", Double.NaN).takeIf { !it.isNaN() }
                                ?: continue,
                            longitude = o.optDouble("longitude", Double.NaN).takeIf { !it.isNaN() }
                                ?: continue,
                            epochMillis = o.optLong("epoch_millis", 0L)
                        )
                    )
                }
            }
            return NasAnchoringPayload(
                anchorLat = lat,
                anchorLon = lon,
                areaMode = mode,
                radiusMeters = radius,
                segmentCenterDeg = segC,
                segmentWidthDeg = segW,
                coneApexOffsetMeters = apex,
                alarmEnabled = alarm,
                trackPoints = track
            )
        }
    }
}
