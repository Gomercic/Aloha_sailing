package com.example.startline.nas

import android.content.Context

/**
 * U Postavkama korisnik upisuje samo **ship code**.
 * Base URL i API ključ dolaze iz [NasDefaults] (ne prikazuju se u UI).
 */
class NasSyncPreferences(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    val baseUrl: String get() = NasDefaults.BASE_URL

    val apiKey: String get() = NasDefaults.API_KEY

    val shipCode: String
        get() = prefs.getString(KEY_SHIP_CODE, "")?.trim().orEmpty()

    fun saveShipCode(shipCode: String) {
        prefs.edit().putString(KEY_SHIP_CODE, shipCode.trim()).apply()
    }

    fun isConfigured(): Boolean = shipCode.isNotBlank()

    companion object {
        private const val PREFS_NAME = "nas_sync"
        private const val KEY_SHIP_CODE = "ship_code"
    }
}
