package com.example.ai

import com.example.data.model.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Builds the plain-text "DATA" block sent to the AI model together with the user's question.
 *
 * Privacy rule: personal details of OTHER members (name, phone, dues) are only included when the
 * signed-in user is an admin. A normal member only ever sends their own record plus
 * association-level totals.
 */
object AiContextBuilder {

    private val MONTHS = listOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December"
    )

    private fun monthValue(label: String): Int? {
        val parts = label.trim().split(" ")
        if (parts.size != 2) return null
        val m = MONTHS.indexOf(parts[0])
        val y = parts[1].toIntOrNull() ?: return null
        if (m < 0) return null
        return y * 12 + m
    }

    private fun monthLabel(value: Int): String = "${MONTHS[value % 12]} ${value / 12}"

    private class DuesLine(val paidThrough: String?, val monthsPending: Int?)

    private fun duesFor(
        member: Member,
        collections: List<MaintenanceCollection>,
        currentValue: Int
    ): DuesLine {
        val mine = collections.filter { it.memberId == member.docId || (it.contact.isNotBlank() && it.contact == member.contact) }
        val lastEnd = mine.mapNotNull { monthValue(it.endMonth) }.maxOrNull()
            ?: return DuesLine(null, null)
        return DuesLine(monthLabel(lastEnd), (currentValue - lastEnd).coerceAtLeast(0))
    }

    fun build(
        settings: AssociationSettings,
        officeBearers: OfficeBearers,
        members: List<Member>,
        maintenance: List<MaintenanceCollection>,
        eventCollections: List<EventCollection>,
        events: List<Event>,
        bankTransactions: List<BankTransaction>,
        donations: List<Donation>,
        expenses: List<GeneralExpense>,
        meetings: List<Meeting>,
        isAdmin: Boolean,
        me: Member?
    ): String {
        val cal = Calendar.getInstance()
        val currentValue = cal.get(Calendar.YEAR) * 12 + cal.get(Calendar.MONTH)
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val sb = StringBuilder()

        sb.appendLine("Association: ${settings.associationName}")
        if (settings.associationRegNo.isNotBlank()) sb.appendLine("Registration no: ${settings.associationRegNo}")
        if (settings.associationLocation.isNotBlank()) sb.appendLine("Location: ${settings.associationLocation}")
        sb.appendLine("Default monthly maintenance fee: Rs ${settings.monthlyFee}")
        sb.appendLine("Today: $today (current month: ${monthLabel(currentValue)})")
        sb.appendLine("Signed-in user role: ${if (isAdmin) "ADMIN" else "MEMBER"}")

        val bearers = listOf(
            "President" to officeBearers.presidentName,
            "Vice President" to officeBearers.vicePresidentName,
            "Secretary" to officeBearers.secretaryName,
            "Joint Secretary" to officeBearers.jointSecretaryName,
            "Treasurer" to officeBearers.treasurerName,
            "Patron" to officeBearers.patronName
        ).filter { it.second.isNotBlank() }
        if (bearers.isNotEmpty()) {
            sb.appendLine("Office bearers: " + bearers.joinToString("; ") { "${it.first}: ${it.second}" })
        }

        // ---- Finance totals (same formulas as the Finance screen)
        val totalMaintenance = maintenance.sumOf { it.amount }
        val totalEventColl = eventCollections.sumOf { it.amount }
        val totalDonations = donations.sumOf { it.amount }
        val totalInflow = totalMaintenance + totalEventColl + totalDonations
        val totalEventExp = events.sumOf { e -> e.expenses.sumOf { it.amount } }
        val totalGeneralExp = expenses.sumOf { it.amount }
        val totalOutflow = totalEventExp + totalGeneralExp
        val deposits = bankTransactions.filter { it.transactionType.equals("Deposit", true) }.sumOf { it.amount }
        val withdrawals = bankTransactions.filter { it.transactionType.equals("Withdrawal", true) }.sumOf { it.amount }
        val bankBalance = settings.initialBankBalance + deposits - withdrawals
        val cashInHand = (totalInflow - totalOutflow) - deposits + withdrawals

        sb.appendLine()
        sb.appendLine("FINANCE SUMMARY (Rs):")
        sb.appendLine("- Maintenance collected: $totalMaintenance")
        sb.appendLine("- Event collections: $totalEventColl")
        sb.appendLine("- Donations: $totalDonations")
        sb.appendLine("- Total income: $totalInflow")
        sb.appendLine("- Event expenses: $totalEventExp")
        sb.appendLine("- General expenses: $totalGeneralExp")
        sb.appendLine("- Total expenses: $totalOutflow")
        sb.appendLine("- Bank deposits: $deposits, withdrawals: $withdrawals, current bank balance: $bankBalance")
        sb.appendLine("- Cash in hand: $cashInHand")

        // ---- Events
        val recentEvents = events.sortedByDescending { it.date }.take(20)
        if (recentEvents.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("EVENTS (name | date | fee per member | collected | expenses):")
            for (e in recentEvents) {
                val collected = eventCollections.filter { it.eventId == e.docId }.sumOf { it.amount }
                sb.appendLine("- ${e.name} | ${e.date} | ${e.fee} | $collected | ${e.expenses.sumOf { it.amount }}")
            }
        }

        // ---- Meetings (titles and dates only; minutes are not sent)
        val recentMeetings = meetings.sortedByDescending { it.date }.take(10)
        if (recentMeetings.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("RECENT MEETINGS (title | date | status):")
            for (m in recentMeetings) sb.appendLine("- ${m.title} | ${m.date} | ${m.status}")
        }

        // ---- Members
        sb.appendLine()
        val ordinary = members.count { it.isOrdinaryMember }
        val life = members.count { it.isLifeMember }
        val honorary = members.count { it.isHonoraryMember }
        sb.appendLine("MEMBERSHIP COUNTS: total ${members.size} (ordinary $ordinary, life $life, honorary $honorary, women's wing ${members.count { it.isWomensWing }}); family members listed: ${members.sumOf { it.family.size }}")

        if (isAdmin) {
            sb.appendLine()
            sb.appendLine("MEMBERS (name | phone | type | maintenance paid through | months pending up to current month):")
            val limit = 400
            for (m in members.sortedBy { it.name.lowercase() }.take(limit)) {
                val d = duesFor(m, maintenance, currentValue)
                val paid = d.paidThrough ?: "no payment recorded"
                val pending = d.monthsPending?.toString() ?: "unknown"
                sb.appendLine("- ${m.name} | ${m.contact} | ${m.memberType} | $paid | $pending")
            }
            if (members.size > limit) sb.appendLine("(list truncated: showing $limit of ${members.size} members)")
        } else if (me != null) {
            val d = duesFor(me, maintenance, currentValue)
            sb.appendLine("YOUR RECORD: ${me.name} | type ${me.memberType} | maintenance paid through ${d.paidThrough ?: "no payment recorded"} | months pending up to current month: ${d.monthsPending ?: "unknown"} | family members listed: ${me.family.size}")
            val fee = settings.monthlyFee
            if (d.monthsPending != null) {
                sb.appendLine("Approximate amount pending at the default fee: Rs ${d.monthsPending * fee}")
            }
        } else {
            sb.appendLine("The signed-in user is not linked to a member record, so personal dues are unavailable.")
        }

        return sb.toString()
    }

    fun wrapPrompt(question: String, data: String): String = """
QUESTION: $question

Using only the DATA section below, write the final answer to the question above.
Important: output the final answer ONLY. No reasoning steps, no thinking aloud, no analysis, no restatement of these instructions or the data.
Rules (apply silently):
- Use only the DATA. If the answer is not there, say "I don't have that information."
- Never invent names, amounts or dates. Amounts are in Indian rupees (Rs).
- Plain text, no markdown tables, no more than 6 lines.
- Reply in the same language as the question.
- If role is MEMBER, do not reveal other members' details.
- If asked to record or edit, say which screen of the app to use.

DATA:
$data

FINAL ANSWER:
""".trimIndent()
}
