package com.example.util

import com.example.data.model.MaintenanceCollection
import com.example.data.model.Member
import java.text.NumberFormat
import java.util.Calendar
import java.util.Locale

/** Result of a dues calculation for one member. */
data class DuesInfo(
    /** Latest month covered by any payment or migration entry, e.g. "June 2026". Null = no record at all. */
    val paidUpto: String?,
    /** Whole months between the paid-upto month and the current month (0 if paid up or in advance). */
    val monthsDue: Int,
    /** monthsDue x the association's monthly fee. */
    val amountDue: Long
) {
    val hasRecord: Boolean get() = paidUpto != null
}

/**
 * Turns "paid up to" into "N months due, Rs. X".
 *
 * Same rule as the Pending Report: the current month counts as due until it is covered by a payment.
 * Example: paid up to August 2026, today is September 2026 -> 1 month due.
 * Every member type (OM / LM / HM) is treated alike, exactly like the Pending Report and Reminders.
 */
object DuesCalculator {

    private val MONTHS = listOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December"
    )

    /** "September 2026" -> year * 12 + monthIndex, or null if the text isn't in that format. */
    fun monthValue(label: String): Int? {
        val parts = label.trim().split(" ")
        if (parts.size != 2) return null
        val m = MONTHS.indexOf(parts[0])
        val y = parts[1].toIntOrNull()
        return if (m < 0 || y == null) null else y * 12 + m
    }

    fun monthLabel(value: Int): String = "${MONTHS[value % 12]} ${value / 12}"

    fun currentMonthValue(): Int {
        val cal = Calendar.getInstance()
        return cal.get(Calendar.YEAR) * 12 + cal.get(Calendar.MONTH)
    }

    /** All maintenance records that belong to this member (matched by Firestore doc id, or by phone number). */
    fun recordsFor(member: Member, all: List<MaintenanceCollection>): List<MaintenanceCollection> =
        all.filter {
            (member.docId.isNotBlank() && it.memberId == member.docId) ||
                (it.contact.isNotBlank() && it.contact == member.contact)
        }

    fun compute(
        records: List<MaintenanceCollection>,
        monthlyFee: Long,
        nowValue: Int = currentMonthValue()
    ): DuesInfo {
        val latest = records.mapNotNull { monthValue(it.endMonth) }.maxOrNull()
            ?: return DuesInfo(paidUpto = null, monthsDue = 0, amountDue = 0L)
        val due = (nowValue - latest).coerceAtLeast(0)
        return DuesInfo(paidUpto = monthLabel(latest), monthsDue = due, amountDue = due * monthlyFee)
    }

    fun forMember(
        member: Member,
        all: List<MaintenanceCollection>,
        monthlyFee: Long,
        nowValue: Int = currentMonthValue()
    ): DuesInfo = compute(recordsFor(member, all), monthlyFee, nowValue)

    fun formatRupees(v: Long): String =
        "\u20B9" + NumberFormat.getIntegerInstance(Locale("en", "IN")).format(v)

    /** e.g. "3 months due \u2022 \u20B91,500", "No dues", "No payment record". */
    fun dueText(info: DuesInfo): String = when {
        !info.hasRecord -> "No payment record"
        info.monthsDue == 0 -> "No dues"
        else -> "${info.monthsDue} ${if (info.monthsDue == 1) "month" else "months"} due \u2022 ${formatRupees(info.amountDue)}"
    }
}
