package com.example.data.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.example.DompetKuApplication
import com.example.domain.model.DraftTransaction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.security.MessageDigest
import java.util.UUID

class DompetKuNotificationListenerService : NotificationListenerService() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val app = application as? DompetKuApplication ?: return
        val container = app.container

        serviceScope.launch {
            // Cek apakah listener aktif
            if (!container.preferenceRepository.notificationListenerEnabledFlow.value) return@launch

            val packageName = sbn.packageName
            // Cek apakah aplikasi ini diaktifkan oleh pengguna untuk parsing
            if (!container.preferenceRepository.isNotificationAppEnabled(packageName)) return@launch

            val extras = sbn.notification.extras
            val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
            val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
            val fullText = "$title $text"

            // Ambil semua rule yang cocok dengan package name
            val rules = container.parsingRuleRepository.getAllParsingRules()
                .filter { it.appPackage.equals(packageName, ignoreCase = true) }

            for (rule in rules) {
                try {
                    val regex = Regex(rule.regexPattern, RegexOption.IGNORE_CASE)
                    val matchResult = regex.find(fullText)
                    if (matchResult != null) {
                        // 1. Ekstrak nominal
                        val amountGroup = matchResult.groups[rule.amountGroupIndex]?.value
                        val amountStr = amountGroup?.replace(Regex("[^0-9]"), "") ?: ""
                        if (amountStr.isEmpty()) continue

                        val amount = BigDecimal(amountStr)

                        // 2. Ekstrak catatan
                        var note = ""
                        if (rule.noteGroupIndex != null && rule.noteGroupIndex!! < matchResult.groups.size) {
                            note = matchResult.groups[rule.noteGroupIndex!!]?.value ?: ""
                        }
                        if (note.isEmpty()) {
                            note = "Transaksi dari notifikasi $title"
                        }

                        // 3. Deduplikasi: hitung hash berbasis postTime notifikasi + nominal + catatan
                        val hashInput = "$packageName|$amount|$note|${sbn.postTime}"
                        val hash = MessageDigest.getInstance("MD5")
                            .digest(hashInput.toByteArray())
                            .joinToString("") { "%02x".format(it) }

                        // Cek jika draft dengan hash ini sudah ada
                        val existingDraft = container.draftTransactionRepository.getDraftByHash(hash)
                        if (existingDraft == null) {
                            // Cek jika ada koreksi dari history (learning mappings)
                            val corrections = container.parserCorrectionRepository.getAllCorrections()
                            val categories = container.categoryRepository.getCategories()

                            var finalCategoryId = rule.categoryId
                            var finalAccountId = rule.accountId

                            // Terapkan fuzzy match untuk kategori atau koreksi
                            if (finalCategoryId == null) {
                                val matchedCat = categories.find { note.contains(it.name, ignoreCase = true) }
                                finalCategoryId = matchedCat?.id
                            }

                            for (corr in corrections) {
                                if (fullText.contains(corr.keyword, ignoreCase = true)) {
                                    if (corr.mappedCategoryId != null) {
                                        finalCategoryId = corr.mappedCategoryId
                                    }
                                    if (corr.mappedAccountId != null) {
                                        finalAccountId = corr.mappedAccountId
                                    }
                                }
                            }

                            val draft = DraftTransaction(
                                id = UUID.randomUUID().toString(),
                                amount = amount,
                                type = rule.type,
                                categoryId = finalCategoryId,
                                accountId = finalAccountId,
                                toAccountId = null,
                                adminFee = null,
                                note = note,
                                dateTime = System.currentTimeMillis(),
                                tags = listOf("NOTIFIKASI", packageName.split(".").last().uppercase()),
                                sourceInput = "NOTIF",
                                transactionHash = hash,
                                isConfirmed = false
                            )
                            container.draftTransactionRepository.insertDraft(draft)
                        }
                        break // Berhenti di aturan pertama yang cocok
                    }
                } catch (e: Exception) {
                    // Abaikan error pada pencocokan rule tertentu
                }
            }
        }
    }
}
