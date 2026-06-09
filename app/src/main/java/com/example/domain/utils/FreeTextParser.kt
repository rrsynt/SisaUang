package com.example.domain.utils

import com.example.domain.model.Account
import com.example.domain.model.Category
import com.example.domain.model.ParserCorrection
import com.example.domain.model.TransactionType
import java.math.BigDecimal
import java.util.Calendar
import java.util.regex.Pattern

data class ParsedResult(
    val amount: BigDecimal,
    val type: TransactionType,
    val category: Category?,
    val account: Account?,
    val toAccount: Account?,
    val note: String,
    val dateTime: Long
)

object FreeTextParser {
    private val indonesianOnes = mapOf(
        "nol" to BigDecimal("0"),
        "satu" to BigDecimal("1"),
        "dua" to BigDecimal("2"),
        "tiga" to BigDecimal("3"),
        "empat" to BigDecimal("4"),
        "lima" to BigDecimal("5"),
        "enam" to BigDecimal("6"),
        "tujuh" to BigDecimal("7"),
        "delapan" to BigDecimal("8"),
        "sembilan" to BigDecimal("9"),
        "sepuluh" to BigDecimal("10"),
        "sebelas" to BigDecimal("11"),
        "se" to BigDecimal("1")
    )

    private fun parseIndonesianOnes(words: List<String>): BigDecimal {
        if (words.isEmpty()) return BigDecimal.ZERO
        return indonesianOnes[words[0]] ?: BigDecimal.ZERO
    }

    private fun parseIndonesianSubHundred(words: List<String>): BigDecimal {
        if (words.isEmpty()) return BigDecimal.ZERO
        val puluhIndex = words.indexOf("puluh")
        if (puluhIndex != -1) {
            val left = words.subList(0, puluhIndex)
            val right = words.subList(puluhIndex + 1, words.size)
            val leftVal = if (left.isEmpty() || left[0] == "se") BigDecimal.ONE else parseIndonesianOnes(left)
            val rightVal = parseIndonesianOnes(right)
            return leftVal.multiply(BigDecimal("10")).add(rightVal)
        }
        val belasIndex = words.indexOf("belas")
        if (belasIndex != -1) {
            val left = words.subList(0, belasIndex)
            val leftVal = if (left.isEmpty() || left[0] == "se") BigDecimal.ONE else parseIndonesianOnes(left)
            return leftVal.add(BigDecimal("10"))
        }
        return parseIndonesianOnes(words)
    }

    private fun parseIndonesianSubThousand(words: List<String>): BigDecimal {
        if (words.isEmpty()) return BigDecimal.ZERO
        val ratusIndex = words.indexOf("ratus")
        if (ratusIndex != -1) {
            val left = words.subList(0, ratusIndex)
            val right = words.subList(ratusIndex + 1, words.size)
            val leftVal = if (left.isEmpty() || left[0] == "se") BigDecimal.ONE else parseIndonesianOnes(left)
            val rightVal = parseIndonesianSubHundred(right)
            return leftVal.multiply(BigDecimal("100")).add(rightVal)
        }
        return parseIndonesianSubHundred(words)
    }

    private fun parseIndonesianWords(words: List<String>): BigDecimal {
        if (words.isEmpty()) return BigDecimal.ZERO
        
        val miliarIndex = words.indexOf("miliar")
        if (miliarIndex != -1) {
            val left = words.subList(0, miliarIndex)
            val right = words.subList(miliarIndex + 1, words.size)
            val leftVal = parseIndonesianSubThousand(left)
            return leftVal.multiply(BigDecimal("1000000000")).add(parseIndonesianWords(right))
        }

        val jutaIndex = words.indexOf("juta")
        if (jutaIndex != -1) {
            val left = words.subList(0, jutaIndex)
            val right = words.subList(jutaIndex + 1, words.size)
            val leftVal = parseIndonesianSubThousand(left)
            return leftVal.multiply(BigDecimal("1000000")).add(parseIndonesianWords(right))
        }

        val ribuIndex = words.indexOf("ribu")
        if (ribuIndex != -1) {
            val left = words.subList(0, ribuIndex)
            val right = words.subList(ribuIndex + 1, words.size)
            val leftVal = parseIndonesianSubThousand(left)
            return leftVal.multiply(BigDecimal("1000")).add(parseIndonesianWords(right))
        }

        return parseIndonesianSubThousand(words)
    }

    private val englishOnes = mapOf(
        "zero" to BigDecimal("0"), "one" to BigDecimal("1"), "two" to BigDecimal("2"),
        "three" to BigDecimal("3"), "four" to BigDecimal("4"), "five" to BigDecimal("5"),
        "six" to BigDecimal("6"), "seven" to BigDecimal("7"), "eight" to BigDecimal("8"),
        "nine" to BigDecimal("9"), "ten" to BigDecimal("10"), "eleven" to BigDecimal("11"),
        "twelve" to BigDecimal("12"), "thirteen" to BigDecimal("13"), "fourteen" to BigDecimal("14"),
        "fifteen" to BigDecimal("15"), "sixteen" to BigDecimal("16"), "seventeen" to BigDecimal("17"),
        "eighteen" to BigDecimal("18"), "nineteen" to BigDecimal("19")
    )
    private val englishTens = mapOf(
        "twenty" to BigDecimal("20"), "thirty" to BigDecimal("30"), "forty" to BigDecimal("40"),
        "fifty" to BigDecimal("50"), "sixty" to BigDecimal("60"), "seventy" to BigDecimal("70"),
        "eighty" to BigDecimal("80"), "ninety" to BigDecimal("90")
    )

    private fun parseEnglishOnes(words: List<String>): BigDecimal {
        if (words.isEmpty()) return BigDecimal.ZERO
        return englishOnes[words[0]] ?: BigDecimal.ZERO
    }

    private fun parseEnglishSubHundred(words: List<String>): BigDecimal {
        if (words.isEmpty()) return BigDecimal.ZERO
        val first = words[0]
        if (englishTens.containsKey(first)) {
            val tenVal = englishTens[first]!!
            val rest = words.subList(1, words.size)
            val restVal = parseEnglishOnes(rest)
            return tenVal.add(restVal)
        }
        return parseEnglishOnes(words)
    }

    private fun parseEnglishSubThousand(words: List<String>): BigDecimal {
        if (words.isEmpty()) return BigDecimal.ZERO
        val hundredIndex = words.indexOf("hundred")
        if (hundredIndex != -1) {
            val left = words.subList(0, hundredIndex)
            val right = words.subList(hundredIndex + 1, words.size)
            val leftVal = parseEnglishOnes(left)
            val rightVal = parseEnglishSubHundred(right)
            return leftVal.multiply(BigDecimal("100")).add(rightVal)
        }
        return parseEnglishSubHundred(words)
    }

    private fun parseEnglishWords(words: List<String>): BigDecimal {
        if (words.isEmpty()) return BigDecimal.ZERO
        
        val billionIndex = words.indexOf("billion")
        if (billionIndex != -1) {
            val left = words.subList(0, billionIndex)
            val right = words.subList(billionIndex + 1, words.size)
            val leftVal = parseEnglishSubThousand(left)
            return leftVal.multiply(BigDecimal("1000000000")).add(parseEnglishWords(right))
        }

        val millionIndex = words.indexOf("million")
        if (millionIndex != -1) {
            val left = words.subList(0, millionIndex)
            val right = words.subList(millionIndex + 1, words.size)
            val leftVal = parseEnglishSubThousand(left)
            return leftVal.multiply(BigDecimal("1000000")).add(parseEnglishWords(right))
        }

        val thousandIndex = words.indexOf("thousand")
        if (thousandIndex != -1) {
            val left = words.subList(0, thousandIndex)
            val right = words.subList(thousandIndex + 1, words.size)
            val leftVal = parseEnglishSubThousand(left)
            return leftVal.multiply(BigDecimal("1000")).add(parseEnglishWords(right))
        }

        return parseEnglishSubThousand(words)
    }

    fun replaceSpokenNumbersWithDigits(text: String): String {
        val normalized = text.lowercase()
            .replace("seratus", "se ratus")
            .replace("seribu", "se ribu")
            .replace("sepuluh", "sepuluh")
            .replace("sebelas", "sebelas")
        
        val tokens = normalized.split(Regex("\\s+")).filter { it.isNotEmpty() }
        
        val idVocab = setOf(
            "nol", "satu", "dua", "tiga", "empat", "lima", "enam", "tujuh", "delapan", "sembilan", 
            "sepuluh", "sebelas", "se", "belas", "puluh", "ratus", "ribu", "juta", "miliar", "rupiah"
        )
        
        val enVocab = setOf(
            "zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", 
            "ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", 
            "eighteen", "nineteen", "twenty", "thirty", "forty", "fifty", "sixty", "seventy", 
            "eighty", "ninety", "hundred", "thousand", "million", "billion", "dollars", "dollar", "bucks"
        )

        val resultTokens = mutableListOf<String>()
        var i = 0
        while (i < tokens.size) {
            val token = tokens[i]
            
            if (idVocab.contains(token)) {
                val segment = mutableListOf<String>()
                var j = i
                while (j < tokens.size && idVocab.contains(tokens[j])) {
                    segment.add(tokens[j])
                    j++
                }
                val cleanSegment = segment.filter { it != "rupiah" }
                val value = parseIndonesianWords(cleanSegment)
                if (value > BigDecimal.ZERO) {
                    resultTokens.add(value.toPlainString())
                    i = j
                    continue
                }
            } else if (enVocab.contains(token)) {
                val segment = mutableListOf<String>()
                var j = i
                while (j < tokens.size && enVocab.contains(tokens[j])) {
                    segment.add(tokens[j])
                    j++
                }
                val cleanSegment = segment.filter { it != "dollars" && it != "dollar" && it != "bucks" }
                val value = parseEnglishWords(cleanSegment)
                if (value > BigDecimal.ZERO) {
                    resultTokens.add(value.toPlainString())
                    i = j
                    continue
                }
            }
            
            resultTokens.add(tokens[i])
            i++
        }
        
        return resultTokens.joinToString(" ")
    }

    fun cleanNumberString(raw: String): BigDecimal? {
        var s = raw.replace(Regex("[^0-9.,]"), "").trim { it == '.' || it == ',' }
        if (s.isEmpty()) return null
        
        val hasDot = s.contains(".")
        val hasComma = s.contains(",")
        
        if (hasDot && hasComma) {
            val lastDot = s.lastIndexOf(".")
            val lastComma = s.lastIndexOf(",")
            if (lastComma > lastDot) {
                val integerPart = s.substring(0, lastComma).replace(".", "")
                val decimalPart = s.substring(lastComma + 1)
                s = "$integerPart.$decimalPart"
            } else {
                s = s.replace(",", "")
            }
        } else if (hasDot) {
            val parts = s.split(".")
            if (parts.size == 2) {
                val decimalPart = parts[1]
                if (decimalPart.length == 2) {
                    // Decimal
                } else {
                    s = s.replace(".", "")
                }
            } else {
                s = s.replace(".", "")
            }
        } else if (hasComma) {
            val parts = s.split(",")
            if (parts.size == 2) {
                val decimalPart = parts[1]
                if (decimalPart.length == 2) {
                    s = parts[0] + "." + decimalPart
                } else {
                    s = s.replace(",", "")
                }
            } else {
                s = s.replace(",", "")
            }
        }
        return s.toBigDecimalOrNull()
    }

    fun parse(
        text: String,
        accounts: List<Account>,
        categories: List<Category>,
        corrections: List<ParserCorrection>
    ): ParsedResult {
        val preprocessedText = replaceSpokenNumbersWithDigits(text).replace(Regex("(\\d)\\s*([.,])\\s*(\\d)"), "$1$2$3")
        val normalized = preprocessedText.lowercase().trim()

        // 1. Parsing tanggal relatif
        var dateTime = System.currentTimeMillis()
        val cal = Calendar.getInstance()
        if (normalized.contains("kemarin")) {
            cal.add(Calendar.DAY_OF_YEAR, -1)
            dateTime = cal.timeInMillis
        } else if (normalized.contains("lusa")) {
            cal.add(Calendar.DAY_OF_YEAR, -2)
            dateTime = cal.timeInMillis
        }

        // 2. Parsing nominal (mendukung k, rb, ribu, jt, juta, m, miliar)
        var amount = BigDecimal.ZERO
        val amountRegex = Pattern.compile("\\b(\\d+(?:[.,]\\d+)*)\\s*(k|rb|ribu|jt|juta|m|miliar)?\\b", Pattern.CASE_INSENSITIVE)
        val matcher = amountRegex.matcher(normalized)
        var parsedAmountStr = ""
        if (matcher.find()) {
            val numStr = matcher.group(1) ?: "0"
            val unit = matcher.group(2)?.lowercase() ?: ""
            val multiplier = when (unit) {
                "k", "rb", "ribu" -> BigDecimal("1000")
                "jt", "juta" -> BigDecimal("1000000")
                "m", "miliar" -> BigDecimal("1000000000")
                else -> BigDecimal.ONE
            }
            try {
                cleanNumberString(numStr)?.let {
                    amount = it.multiply(multiplier)
                }
                parsedAmountStr = matcher.group(0) ?: ""
            } catch (e: Exception) {
                // Abaikan error parsing
            }
        }

        // 3. Tentukan tipe transaksi
        var type = TransactionType.PENGELUARAN
        if (normalized.contains("transfer") || normalized.contains("kirim") || normalized.contains("pindah") || normalized.contains("ke")) {
            if (normalized.contains("transfer") || normalized.contains("ke")) {
                type = TransactionType.TRANSFER
            }
        }
        if (normalized.contains("gaji") || normalized.contains("terima") || normalized.contains("pemasukan") || normalized.contains("bonus") || normalized.contains("dapat") || normalized.contains("untung")) {
            type = TransactionType.PEMASUKAN
        }

        // 4. Koreksi pengguna yang dipelajari (Learning Mappings)
        var categoryIdFromCorrection: String? = null
        var accountIdFromCorrection: String? = null
        for (correction in corrections) {
            if (normalized.contains(correction.keyword.lowercase())) {
                if (correction.mappedCategoryId != null) {
                    categoryIdFromCorrection = correction.mappedCategoryId
                }
                if (correction.mappedAccountId != null) {
                    accountIdFromCorrection = correction.mappedAccountId
                }
            }
        }

        // 5. Pencocokan Akun
        var account: Account? = accounts.find { it.id == accountIdFromCorrection }
        var toAccount: Account? = null

        if (type == TransactionType.TRANSFER) {
            val sortedAccounts = accounts.sortedByDescending { it.name.length }
            val keIndex = normalized.indexOf(" ke ")
            if (keIndex != -1) {
                val beforeKe = normalized.substring(0, keIndex)
                val afterKe = normalized.substring(keIndex + 4)
                toAccount = sortedAccounts.find { afterKe.contains(it.name.lowercase()) }
                account = sortedAccounts.find { beforeKe.contains(it.name.lowercase()) }
            }
            if (account == null) {
                account = sortedAccounts.find { normalized.contains(it.name.lowercase()) }
            }
            if (toAccount == null) {
                toAccount = sortedAccounts.find { it.id != account?.id && normalized.contains(it.name.lowercase()) }
            }
        } else {
            if (account == null) {
                val sortedAccounts = accounts.sortedByDescending { it.name.length }
                account = sortedAccounts.find { normalized.contains(it.name.lowercase()) }
            }
        }

        // 6. Pencocokan Kategori
        var category: Category? = categories.find { it.id == categoryIdFromCorrection }
        if (category == null && type != TransactionType.TRANSFER) {
            val sortedCategories = categories.sortedByDescending { it.name.length }
            category = sortedCategories.find { normalized.contains(it.name.lowercase()) }
        }

        // 7. Ekstraksi Catatan
        var note = preprocessedText
        if (parsedAmountStr.isNotEmpty()) {
            note = note.replace(parsedAmountStr, "", ignoreCase = true)
        }
        if (account != null) {
            note = note.replace(account.name, "", ignoreCase = true)
        }
        if (toAccount != null) {
            note = note.replace(toAccount.name, "", ignoreCase = true)
        }

        val removeKeywords = listOf("transfer", "kirim", "pindah", "kemarin", "tadi", "hari ini", " ke ", " dari ", "untuk", "beli", "bayar")
        for (kw in removeKeywords) {
            note = note.replace(kw, "", ignoreCase = true)
        }

        note = note.replace(Regex("\\s+"), " ").trim()
        if (note.isEmpty()) {
            note = when (type) {
                TransactionType.PEMASUKAN -> "Pemasukan"
                TransactionType.TRANSFER -> "Transfer"
                else -> "Pengeluaran"
            }
        }

        return ParsedResult(
            amount = amount,
            type = type,
            category = category,
            account = account,
            toAccount = toAccount,
            note = note,
            dateTime = dateTime
        )
    }
}
