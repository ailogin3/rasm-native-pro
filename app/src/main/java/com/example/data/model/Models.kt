package com.example.data.model

data class Member(
    val docId: String = "",
    val name: String = "",
    val contact: String = "",
    val email: String = "",
    val memberType: String = "OM", // OM = Ordinary, LM = Life, HM = Honorary
    val gender: String = "Male",   // Male, Female (Women's Wing)
    val photo: String? = null,     // Base64 JPEG data URL or remote URL
    val joinDate: String = "",
    val family: List<FamilyMember> = emptyList(),
    val id: Long? = null
) {
    val isLifeMember: Boolean get() = memberType == "LM"
    val isOrdinaryMember: Boolean get() = memberType == "OM" || memberType.isBlank()
    val isHonoraryMember: Boolean get() = memberType == "HM"
    val isWomensWing: Boolean get() = gender.equals("Female", ignoreCase = true)
}

data class FamilyMember(
    val id: String = "",
    val name: String = "",
    val relation: String = "",
    val photo: String = ""
)

data class MaintenanceCollection(
    val docId: String = "",
    val memberId: String = "", // Firestore doc ID of member
    val contact: String = "",
    val amount: Long = 0L,
    val date: String = "",
    val period: Int = 1,
    val startMonth: String = "",
    val endMonth: String = "",
    val receiptNumber: String = "",
    val timestamp: String = ""
)

data class Event(
    val docId: String = "",
    val name: String = "",
    val date: String = "",
    val fee: Long = 0L,
    val expenses: List<EventExpense> = emptyList(),
    val attachments: List<AttachmentItem> = emptyList()
)

data class EventExpense(
    val id: String = "",
    val description: String = "",
    val amount: Long = 0L,
    val date: String = ""
)

data class AttachmentItem(
    val name: String = "",
    val type: String = "",
    val size: Long = 0L,
    val data: String = "",
    val uploadedAt: String = ""
)

data class EventCollection(
    val docId: String = "",
    val eventId: String = "",
    val memberId: String = "", // Firestore doc ID of member
    val contact: String = "",
    val amount: Long = 0L,
    val date: String = "",
    val receiptNumber: String = ""
)

data class BankTransaction(
    val docId: String = "",
    val transactionType: String = "deposit", // see BankTxType (matching ignores upper/lower case)
    val amount: Long = 0L,
    val date: String = "",
    val transactionId: String = "",
    val remarks: String = "",
    /** Name of the bank account this entry belongs to. Blank when the association has no bank accounts set up. */
    val bankAccount: String = "",
    /** Same value on both entries of a transfer between two accounts, so they are handled together. */
    val transferId: String = ""
)

/** One bank account of the association, with the balance it held before the first entry in this app. */
data class BankAccount(
    val docId: String = "",
    val name: String = "",
    val openingBalance: Long = 0L
)

/**
 * Kinds of bank-log entry and what each one does to the two balances.
 *
 *  Deposit         bank +, cash -   (cash taken from hand to the bank)
 *  Withdrawal      bank -, cash +   (cash taken from the bank to hand)
 *  Interest        bank +           (credited by the bank; counts as income, never was cash)
 *  Adjustment In   bank +           (correction only: no cash, no income)
 *  Adjustment Out  bank -           (bank charges / correction only: no cash, no expense)
 *  Transfer Out    bank -           (money moved to another bank account; not an expense)
 *  Transfer In     bank +           (money received from another bank account; not income)
 */
object BankTxType {
    const val DEPOSIT = "Deposit"
    const val WITHDRAWAL = "Withdrawal"
    const val INTEREST = "Interest"
    const val ADJUSTMENT_IN = "Adjustment In"
    const val ADJUSTMENT_OUT = "Adjustment Out"
    const val TRANSFER_IN = "Transfer In"
    const val TRANSFER_OUT = "Transfer Out"

    /** Only a choice in the entry form: saved as a Transfer Out on one account plus a Transfer In on the other. */
    const val TRANSFER = "Transfer"

    /** Every type that can be stored. */
    val ALL = listOf(DEPOSIT, WITHDRAWAL, INTEREST, ADJUSTMENT_IN, ADJUSTMENT_OUT, TRANSFER_IN, TRANSFER_OUT)

    /** What the entry form offers. */
    val ENTRY_CHOICES = listOf(DEPOSIT, WITHDRAWAL, INTEREST, ADJUSTMENT_IN, ADJUSTMENT_OUT, TRANSFER)

    fun isTransfer(type: String): Boolean {
        val c = canonical(type)
        return c == TRANSFER_IN || c == TRANSFER_OUT
    }

    /** Old records may be stored as "deposit" / "withdrawal"; this maps any casing to the canonical label. */
    fun canonical(raw: String): String =
        ALL.firstOrNull { it.equals(raw.trim(), ignoreCase = true) } ?: raw

    fun needsRemarks(type: String) = type == ADJUSTMENT_IN || type == ADJUSTMENT_OUT

    fun describe(type: String): String = when (type) {
        DEPOSIT -> "Cash moves from hand into the bank."
        WITHDRAWAL -> "Cash moves from the bank into hand."
        INTEREST -> "Interest credited by the bank. Adds to the bank balance and to income."
        ADJUSTMENT_IN -> "Correction that adds to the bank balance only (no cash, no income). Remarks required."
        ADJUSTMENT_OUT -> "Bank charges or a correction that reduces the bank balance only. Remarks required."
        TRANSFER -> "Moves money from one bank account to another. Not income or expense; the total bank balance stays the same."
        else -> ""
    }
}

data class Meeting(
    val docId: String = "",
    val title: String = "",
    val date: String = "",
    val time: String = "",
    val location: String = "",
    val attendees: List<MeetingAttendee> = emptyList(),
    val agenda: List<String> = emptyList(),
    val minutes: String = "",
    val minutesPhoto: String = "",
    val actionItems: List<MeetingActionItem> = emptyList(),
    val attachments: List<AttachmentItem> = emptyList(),
    val status: String = "scheduled", // scheduled, in-progress, completed, cancelled
    val createdAt: String = "",
    val updatedAt: String = ""
)

data class MeetingAttendee(
    val name: String = "",
    val contact: String = ""
)

data class MeetingActionItem(
    val id: String = "",
    val task: String = "",
    val assignedTo: String = "",
    val dueDate: String = "",
    val status: String = "Pending"
)

data class MeetingTemplate(
    val id: Long = 0L,
    val name: String = "",
    val content: String = "",
    val defaultAgenda: List<String> = emptyList()
)

data class Donation(
    val docId: String = "",
    val donorName: String = "",
    val donorContact: String = "",
    val memberId: String? = null,
    val amount: Long = 0L,
    val date: String = "",
    val eventId: String? = null,
    val purpose: String = "",
    val createdAt: String = ""
)

data class GeneralExpense(
    val docId: String = "",
    val description: String = "",
    val amount: Long = 0L,
    val date: String = "",
    val category: String = "",
    val eventId: String? = null,
    val createdAt: String = ""
)

data class ActivityLogEntry(
    val docId: String = "",
    val adminEmail: String = "",
    val action: String = "",
    val details: String = "",
    val timestamp: String = ""
)

data class ServiceListing(
    val docId: String = "",
    val memberId: String = "",
    val businessName: String = "",
    val category: String = "",
    val description: String = "",
    val location: String = "",
    val contactPerson: String = "",
    val contactNumber: String = "",
    val submittedByEmail: String = "",
    val status: String = "pending", // pending, approved
    val submittedAt: String = "",
    val approvedAt: String? = null,
    val approvedBy: String? = null
)

data class AssociationSettings(
    val associationName: String = "Residents Association",
    val associationRegNo: String = "",
    val associationLocation: String = "",
    val monthlyFee: Long = 500L,
    val initialBankBalance: Long = 0L
)

data class OfficeBearers(
    val presidentName: String = "",
    val presidentContact: String = "",
    val vicePresidentName: String = "",
    val vicePresidentContact: String = "",
    val secretaryName: String = "",
    val secretaryContact: String = "",
    val jointSecretaryName: String = "",
    val jointSecretaryContact: String = "",
    val treasurerName: String = "",
    val treasurerContact: String = "",
    val patronName: String = "",
    val patronContact: String = "",
    val committeeMembers: String = ""
)

// Scanned meeting-minutes photo, stored as its own document in meetings/{id}/photos
// (keeps the meeting document far below Firestore's 1 MiB document limit).
data class MeetingPhoto(
    val docId: String = "",
    val data: String = "",      // Base64 JPEG data URL
    val createdAt: Long = 0L
)

/** A notice posted by an admin for all members. [createdAt] is "yyyy-MM-dd'T'HH:mm:ss'Z'" and is used for ordering. */
data class Notice(
    val docId: String = "",
    val title: String = "",
    val body: String = "",
    val pinned: Boolean = false,
    val postedBy: String = "",
    val createdAt: String = ""
) {
    /** "2026-09-21" part of [createdAt]. */
    val date: String get() = createdAt.take(10)
}
