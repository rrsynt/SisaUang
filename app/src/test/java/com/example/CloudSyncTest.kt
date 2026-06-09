package com.example

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

class CloudSyncTest {

    // Pemetaan sinonim sederhana untuk pengujian
    private val synonyms = mapOf(
        "uuid" to "id",
        "nominal" to "amount",
        "jumlah" to "amount",
        "jumlahuang" to "amount",
        "jenis" to "type",
        "kategori" to "categoryId",
        "akun" to "accountId",
        "tanggal" to "dateTime",
        "catatan" to "note"
    )

    private fun normalizeHeader(header: String): String {
        val clean = header.lowercase().replace("[\\s_-]".toRegex(), "")
        return synonyms[clean] ?: clean
    }

    @Test
    fun testColumnSynonymMapping() {
        // Pengujian kecocokan case-insensitive, spasi, underscore, dan sinonim Bahasa Indonesia / Inggris
        assertEquals("id", normalizeHeader("UUID"))
        assertEquals("id", normalizeHeader("Id"))
        assertEquals("amount", normalizeHeader("Nominal"))
        assertEquals("amount", normalizeHeader("Jumlah Uang"))
        assertEquals("amount", normalizeHeader("amount"))
        assertEquals("type", normalizeHeader("Jenis"))
        assertEquals("categoryId", normalizeHeader("Kategori"))
        assertEquals("accountId", normalizeHeader("Akun"))
        assertEquals("dateTime", normalizeHeader("Tanggal"))
        assertEquals("note", normalizeHeader("Catatan"))
    }

    @Test
    fun testConflictResolutionLWW() {
        // Resolusi konflik menggunakan Last-Write-Wins (LWW) berdasarkan timestamp updatedAt
        val localUpdatedAt = 1717150000000L // 31 Mei 2026, 12:30:00
        val sheetUpdatedAt = 1717150060000L // 31 Mei 2026, 12:31:00 (Lebih baru)

        val localData = mapOf("id" to "tx-1", "amount" to "10000", "updatedAt" to localUpdatedAt.toString())
        val sheetData = mapOf("id" to "tx-1", "amount" to "25000", "updatedAt" to sheetUpdatedAt.toString())

        // Memilih pemenang berdasarkan updatedAt
        val winner = if ((localData["updatedAt"]?.toLong() ?: 0L) > (sheetData["updatedAt"]?.toLong() ?: 0L)) {
            localData
        } else {
            sheetData
        }

        assertEquals("25000", winner["amount"])
        assertEquals(sheetUpdatedAt.toString(), winner["updatedAt"])
    }

    @Test
    fun testConflictResolutionLWWLocalWins() {
        val localUpdatedAt = 1717150090000L // Lebih baru
        val sheetUpdatedAt = 1717150060000L

        val localData = mapOf("id" to "tx-1", "amount" to "50000", "updatedAt" to localUpdatedAt.toString())
        val sheetData = mapOf("id" to "tx-1", "amount" to "25000", "updatedAt" to sheetUpdatedAt.toString())

        val winner = if ((localData["updatedAt"]?.toLong() ?: 0L) > (sheetData["updatedAt"]?.toLong() ?: 0L)) {
            localData
        } else {
            sheetData
        }

        assertEquals("50000", winner["amount"])
        assertEquals(localUpdatedAt.toString(), winner["updatedAt"])
    }

    @Test
    fun testSoftDeleteConsistency() {
        // Soft delete diidentifikasi melalui kolom 'deleted' = true/1
        val record = mapOf("id" to "tx-2", "deleted" to "1", "updatedAt" to System.currentTimeMillis().toString())
        val isDeleted = record["deleted"] == "1" || record["deleted"]?.lowercase() == "true"
        assertEquals(true, isDeleted)
    }
}
