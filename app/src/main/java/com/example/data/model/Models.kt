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
    val transactionType: String = "deposit", // deposit or withdrawal
    val amount: Long = 0L,
    val date: String = "",
    val transactionId: String = "",
    val remarks: String = ""
)

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
