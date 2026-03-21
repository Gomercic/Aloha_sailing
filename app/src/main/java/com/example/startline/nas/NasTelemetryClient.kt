package com.example.startline.nas

import android.net.Uri
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

sealed class NasCallResult<out T> {
    data class Ok<T>(val value: T) : NasCallResult<T>()
    data class Err(val message: String) : NasCallResult<Nothing>()
}

class NasTelemetryClient(
    client: OkHttpClient? = null
) {

    private val http: OkHttpClient = client ?: DEFAULT_CLIENT

    fun putAnchoring(
        baseUrl: String,
        apiKey: String,
        shipCode: String,
        payload: NasAnchoringPayload
    ): NasCallResult<Unit> {
        val url = buildTelemetryUrl(baseUrl, shipCode) ?: return NasCallResult.Err("Neispravan URL ili ship code.")
        val bodyJson = JSONObject().apply { put("data", payload.toDataJson()) }
        val body = bodyJson.toString().toRequestBody(JSON_MEDIA)
        val request = Request.Builder()
            .url(url)
            .put(body)
            .header("X-API-Key", apiKey)
            .header("Accept", "application/json")
            .build()
        return execute(request) { Unit }
    }

    fun getAnchoring(
        baseUrl: String,
        apiKey: String,
        shipCode: String
    ): NasCallResult<NasAnchoringPayload> {
        val url = buildTelemetryUrl(baseUrl, shipCode) ?: return NasCallResult.Err("Neispravan URL ili ship code.")
        val request = Request.Builder()
            .url(url)
            .get()
            .header("X-API-Key", apiKey)
            .header("Accept", "application/json")
            .build()
        return execute(request) { body ->
            val root = JSONObject(body)
            NasAnchoringPayload.fromTelemetryJson(root)
                ?: error("Nema payload polja u odgovoru.")
        }
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
            NasCallResult.Err(e.message ?: "Mrežna greška")
        } catch (e: Exception) {
            NasCallResult.Err(e.message ?: "Greška pri obradi odgovora")
        }
    }

    companion object {
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

        private val DEFAULT_CLIENT: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        fun buildTelemetryUrl(baseUrlRaw: String, shipCodeRaw: String): String? {
            val base = baseUrlRaw.trim().trimEnd('/')
            val code = shipCodeRaw.trim()
            if (base.isEmpty() || code.isEmpty()) return null
            val encodedShip = Uri.encode(code)
            return "$base/v1/ships/$encodedShip/telemetry/latest"
        }
    }
}
