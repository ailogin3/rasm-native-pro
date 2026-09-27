package com.example.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** One data row of the import file after checking. [problem] is null when the row is fine. */
data class MemberImportRow(
    val line: Int,
    val name: String,
    val contact: String,
    val email: String,
    val memberType: String,
    val gender: String,
    val joinDate: String,
    /** e.g. "June 2026" - creates a "paid up to" entry for the new member, or null for none. */
    val paidUpto: String?,
    val problem: String?,
    val duplicate: Boolean
) {
    val isReady: Boolean get() = problem == null && !duplicate
}

data class MemberImportPreview(
    val rows: List<MemberImportRow>,
    val fileError: String?
) {
    val ready: List<MemberImportRow> get() = rows.filter { it.isReady }
    val duplicates: List<MemberImportRow> get() = rows.filter { it.problem == null && it.duplicate }
    val invalid: List<MemberImportRow> get() = rows.filter { it.problem != null }
}

/**
 * Reads a members CSV (header row required) and checks every row before anything is saved.
 *
 * Columns (any order, names are forgiving, e.g. "Mobile", "Phone Number" or "Contact" all work):
 *   name*, contact*, email, member type (OM / LM / HM), gender (Male / Female), join date, paid upto
 * A starred column is required.
 */
object MemberImport {

    const val MAX_ROWS = 1000

    const val SAMPLE_CSV =
        "name,contact,email,member type,gender,join date,paid upto\r\n" +
            "Priya Sharma,9876543210,priya@example.com,OM,Female,2024-04-01,June 2026\r\n" +
            "Rajesh Kumar,9123456780,rajesh@example.com,LM,Male,15/08/2023,\r\n"

    private val MONTHS = listOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December"
    )

    /** Lower-case ASCII letters only, so stray marks like a garbled byte-order mark never break a header. */
    private fun key(header: String) = header.lowercase().filter { it in 'a'..'z' }

    private val NAME_KEYS = setOf("name", "membername", "fullname")
    private val CONTACT_KEYS = setOf("contact", "phone", "mobile", "phonenumber", "mobilenumber", "contactnumber", "whatsapp")
    private val EMAIL_KEYS = setOf("email", "emailid", "emailaddress", "mail")
    private val TYPE_KEYS = setOf("type", "membertype", "membership", "category")
    private val GENDER_KEYS = setOf("gender", "sex")
    private val JOIN_KEYS = setOf("joindate", "joiningdate", "joined", "dateofjoining", "doj")
    private val PAID_KEYS = setOf("paidupto", "paidtill", "paiduptomonth", "paiduptill")

    /** Keeps the 10 digits of an Indian mobile number, dropping +91 / 91 / a leading 0. Null if it isn't 10 digits. */
    fun normalizeContact(raw: String): String? {
        val digits = raw.filter { it.isDigit() }
        val d = when {
            digits.length == 12 && digits.startsWith("91") -> digits.drop(2)
            digits.length == 11 && digits.startsWith("0") -> digits.drop(1)
            else -> digits
        }
        return if (d.length == 10) d else null
    }

    /** Last 10 digits, used to compare an existing member's contact with a file row. */
    fun contactKey(raw: String): String = raw.filter { it.isDigit() }.takeLast(10)

    fun normalizeType(raw: String): String? = when (key(raw)) {
        "", "om", "ordinary", "o" -> "OM"
        "lm", "life", "l" -> "LM"
        "hm", "honorary", "honourary", "h" -> "HM"
        else -> null
    }

    fun normalizeGender(raw: String): String? = when (key(raw)) {
        "", "male", "m", "man" -> "Male"
        "female", "f", "woman", "women", "womenswing", "ww" -> "Female"
        else -> null
    }

    /** Accepts 2024-05-31 or 31/05/2024 (also with dashes) and returns 2024-05-31; null if unreadable. */
    fun normalizeDate(raw: String): String? {
        val t = raw.trim()
        val iso = Regex("""^(\d{4})-(\d{1,2})-(\d{1,2})$""").matchEntire(t)
        val dmy = Regex("""^(\d{1,2})[/\-.](\d{1,2})[/\-.](\d{4})$""").matchEntire(t)
        val (y, m, d) = when {
            iso != null -> Triple(iso.groupValues[1].toInt(), iso.groupValues[2].toInt(), iso.groupValues[3].toInt())
            dmy != null -> Triple(dmy.groupValues[3].toInt(), dmy.groupValues[2].toInt(), dmy.groupValues[1].toInt())
            else -> return null
        }
        if (y !in 1900..2100 || m !in 1..12 || d !in 1..31) return null
        return String.format(Locale.US, "%04d-%02d-%02d", y, m, d)
    }

    /** "Jun 2026", "June-2026", "2026-06" or "06/2026" -> "June 2026". Null if unreadable. */
    fun normalizeMonth(raw: String): String? {
        val t = raw.trim()
        val ym = Regex("""^(\d{4})[\-/](\d{1,2})$""").matchEntire(t)
        val my = Regex("""^(\d{1,2})[\-/](\d{4})$""").matchEntire(t)
        val nameYear = Regex("""^([A-Za-z]+)[\s,.\-/]*(\d{2}|\d{4})$""").matchEntire(t)
        val (year, month) = when {
            ym != null -> Pair(ym.groupValues[1].toInt(), ym.groupValues[2].toInt())
            my != null -> Pair(my.groupValues[2].toInt(), my.groupValues[1].toInt())
            nameYear != null -> {
                val word = nameYear.groupValues[1].lowercase()
                // Full name or any prefix of at least 3 letters (Jun, June, Sep, Sept ...)
                val idx = MONTHS.indexOfFirst { month ->
                    val full = month.lowercase()
                    full == word || (word.length >= 3 && full.startsWith(word))
                }
                if (idx < 0) return null
                val yy = nameYear.groupValues[2].toInt().let { if (it < 100) 2000 + it else it }
                Pair(yy, idx + 1)
            }
            else -> return null
        }
        if (year !in 2000..2100 || month !in 1..12) return null
        return "${MONTHS[month - 1]} $year"
    }

    private fun validEmail(e: String) = Regex("""^[^\s@]+@[^\s@]+\.[^\s@]+$""").matches(e)

    /**
     * @param existingContacts contactKey() of every current member, so people already in the app are skipped.
     * @param today used for a blank join date.
     */
    fun parse(
        text: String,
        existingContacts: Set<String>,
        today: String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    ): MemberImportPreview {
        val table = CsvUtil.parse(text)
        if (table.isEmpty()) return MemberImportPreview(emptyList(), "The file is empty.")

        val headers = table.first().map { key(it) }
        fun col(keys: Set<String>) = headers.indexOfFirst { it in keys }
        val nameCol = col(NAME_KEYS)
        val contactCol = col(CONTACT_KEYS)
        if (nameCol < 0 || contactCol < 0) {
            return MemberImportPreview(
                emptyList(),
                "The first row must be a header with at least 'name' and 'contact' columns."
            )
        }
        val emailCol = col(EMAIL_KEYS)
        val typeCol = col(TYPE_KEYS)
        val genderCol = col(GENDER_KEYS)
        val joinCol = col(JOIN_KEYS)
        val paidCol = col(PAID_KEYS)

        val body = table.drop(1)
        if (body.isEmpty()) return MemberImportPreview(emptyList(), "The file has a header but no members.")
        if (body.size > MAX_ROWS) {
            return MemberImportPreview(emptyList(), "Please import at most $MAX_ROWS members at a time (this file has ${body.size}).")
        }

        fun cell(row: List<String>, index: Int) = if (index in row.indices) row[index].trim() else ""

        val seen = mutableMapOf<String, Int>()
        val rows = body.mapIndexed { i, r ->
            val line = i + 2 // header is line 1
            val name = cell(r, nameCol)
            val rawContact = cell(r, contactCol)
            val email = cell(r, emailCol).lowercase()
            var problem: String? = null

            val contact = normalizeContact(rawContact)
            val type = normalizeType(cell(r, typeCol))
            val gender = normalizeGender(cell(r, genderCol))
            val rawJoin = cell(r, joinCol)
            val join = if (rawJoin.isBlank()) today else normalizeDate(rawJoin)
            val rawPaid = cell(r, paidCol)
            val paid = if (rawPaid.isBlank()) null else normalizeMonth(rawPaid)

            when {
                name.isBlank() -> problem = "Name is missing"
                contact == null -> problem = "Phone must be 10 digits (found '$rawContact')"
                email.isNotBlank() && !validEmail(email) -> problem = "Email looks wrong ('$email')"
                type == null -> problem = "Unknown member type '${cell(r, typeCol)}' (use OM, LM or HM)"
                gender == null -> problem = "Unknown gender '${cell(r, genderCol)}' (use Male or Female)"
                join == null -> problem = "Join date must look like 2024-05-31 or 31/05/2024"
                rawPaid.isNotBlank() && paid == null -> problem = "Paid upto must look like June 2026"
            }

            var duplicate = false
            if (problem == null && contact != null) {
                if (contact in existingContacts) {
                    duplicate = true
                } else if (seen.containsKey(contact)) {
                    duplicate = true
                } else {
                    seen[contact] = line
                }
            }

            MemberImportRow(
                line = line,
                name = name,
                contact = contact ?: rawContact,
                email = email,
                memberType = type ?: "OM",
                gender = gender ?: "Male",
                joinDate = join ?: "",
                paidUpto = paid,
                problem = problem,
                duplicate = duplicate
            )
        }
        return MemberImportPreview(rows, null)
    }
}
