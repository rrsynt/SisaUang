package com.example.data.sync

import android.content.Context
import com.example.domain.model.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.math.BigDecimal

class GoogleSheetsSyncClient(
    private val context: Context,
    private val authManager: GoogleAuthManager,
    private val okHttpClient: OkHttpClient
) {
    private val mediaTypeJson = "application/json; charset=utf-8".toMediaType()

    private suspend fun getAuthHeader(): String {
        val token = authManager.getAccessToken() ?: throw IOException("Gagal mendapatkan token akses Google. Harap masuk kembali.")
        return "Bearer $token"
    }

    private suspend fun executeRequestWithRetry(request: Request): Response = withContext(Dispatchers.IO) {
        var attempt = 0
        var delayMs = 1000L
        while (true) {
            val authenticatedRequest = request.newBuilder()
                .header("Authorization", getAuthHeader())
                .build()
            
            val call = okHttpClient.newCall(authenticatedRequest)
            var response: Response? = null
            var exception: IOException? = null
            try {
                response = call.execute()
            } catch (e: IOException) {
                exception = e
            }

            if (response != null) {
                val code = response.code
                if (code == 429 || code >= 500) {
                    response.close()
                    if (attempt >= 3) {
                        throw IOException("Google API Error: HTTP $code. Limit rate terlampaui.")
                    }
                } else {
                    return@withContext response
                }
            } else {
                if (attempt >= 3 && exception != null) {
                    throw exception
                }
            }
            attempt++
            kotlinx.coroutines.delay(delayMs)
            delayMs *= 2
        }
        throw IOException("Koneksi gagal setelah beberapa kali percobaan.")
    }

    // Membuat spreadsheet baru jika kosong
    suspend fun createSpreadsheet(title: String): String = withContext(Dispatchers.IO) {
        val url = "https://sheets.googleapis.com/v4/spreadsheets"
        val bodyJson = JSONObject().apply {
            put("properties", JSONObject().apply {
                put("title", title)
            })
        }
        val request = Request.Builder()
            .url(url)
            .post(bodyJson.toString().toRequestBody(mediaTypeJson))
            .build()

        executeRequestWithRetry(request).use { response ->
            if (!response.isSuccessful) {
                throw IOException("Gagal membuat spreadsheet baru: HTTP ${response.code} ${response.message}")
            }
            val resBody = response.body?.string() ?: throw IOException("Respon kosong dari Google Sheets API")
            val json = JSONObject(resBody)
            json.getString("spreadsheetId")
        }
    }

    // Mendapatkan properti lembar kerja (tab) yang ada
    suspend fun getSpreadsheetSheets(spreadsheetId: String): List<Pair<Int, String>> = withContext(Dispatchers.IO) {
        val url = "https://sheets.googleapis.com/v4/spreadsheets/$spreadsheetId?includeGridData=false"
        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        executeRequestWithRetry(request).use { response ->
            if (!response.isSuccessful) {
                throw IOException("Gagal memuat info spreadsheet: HTTP ${response.code}")
            }
            val resBody = response.body?.string() ?: return@withContext emptyList()
            val json = JSONObject(resBody)
            val sheetsArray = json.optJSONArray("sheets") ?: return@withContext emptyList()
            val result = mutableListOf<Pair<Int, String>>()
            for (i in 0 until sheetsArray.length()) {
                val sheetObj = sheetsArray.getJSONObject(i)
                val props = sheetObj.getJSONObject("properties")
                val id = props.getInt("sheetId")
                val title = props.getString("title")
                result.add(Pair(id, title))
            }
            result
        }
    }

    // Batch update untuk menambah tab baru, memformat header, dll.
    suspend fun batchUpdate(spreadsheetId: String, requests: JSONArray) = withContext(Dispatchers.IO) {
        val url = "https://sheets.googleapis.com/v4/spreadsheets/$spreadsheetId:batchUpdate"
        val bodyJson = JSONObject().apply {
            put("requests", requests)
        }
        val request = Request.Builder()
            .url(url)
            .post(bodyJson.toString().toRequestBody(mediaTypeJson))
            .build()

        executeRequestWithRetry(request).use { response ->
            if (!response.isSuccessful) {
                throw IOException("Gagal melakukan batch update: HTTP ${response.code} ${response.body?.string()}")
            }
        }
    }

    // Membaca data baris dari tab tertentu
    suspend fun readValues(spreadsheetId: String, range: String): List<List<String>> = withContext(Dispatchers.IO) {
        val url = "https://sheets.googleapis.com/v4/spreadsheets/$spreadsheetId/values/$range"
        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        executeRequestWithRetry(request).use { response ->
            if (response.code == 404) return@withContext emptyList()
            if (!response.isSuccessful) {
                throw IOException("Gagal membaca nilai: HTTP ${response.code}")
            }
            val resBody = response.body?.string() ?: return@withContext emptyList()
            val json = JSONObject(resBody)
            val valuesArray = json.optJSONArray("values") ?: return@withContext emptyList()
            val result = mutableListOf<List<String>>()
            for (i in 0 until valuesArray.length()) {
                val rowArray = valuesArray.getJSONArray(i)
                val row = mutableListOf<String>()
                for (j in 0 until rowArray.length()) {
                    row.add(rowArray.optString(j, ""))
                }
                result.add(row)
            }
            result
        }
    }

    // Menulis data baris ke range tertentu
    suspend fun writeValues(spreadsheetId: String, range: String, values: List<List<String>>) = withContext(Dispatchers.IO) {
        val url = "https://sheets.googleapis.com/v4/spreadsheets/$spreadsheetId/values/$range?valueInputOption=USER_ENTERED"
        val bodyJson = JSONObject().apply {
            put("values", JSONArray(values.map { JSONArray(it) }))
        }
        val request = Request.Builder()
            .url(url)
            .put(bodyJson.toString().toRequestBody(mediaTypeJson))
            .build()

        executeRequestWithRetry(request).use { response ->
            if (!response.isSuccessful) {
                throw IOException("Gagal menulis nilai: HTTP ${response.code} ${response.body?.string()}")
            }
        }
    }
}
