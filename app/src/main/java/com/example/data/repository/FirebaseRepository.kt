package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.data.model.*
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.storage.FirebaseStorage
import com.example.util.ImageUtils
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.google.firebase.Timestamp
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter
import kotlinx.coroutines.tasks.await

class FirebaseRepository(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("rasm_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val TAG = "FirebaseRepository"
        const val PREF_API_KEY = "fb_api_key"
        const val PREF_APP_ID = "fb_app_id"
        const val PREF_PROJECT_ID = "fb_project_id"
        const val PREF_STORAGE_BUCKET = "fb_storage_bucket"
        const val PREF_MESSAGING_SENDER_ID = "fb_messaging_sender_id"
        const val PREF_AUTH_DOMAIN = "fb_auth_domain"
    }

    init {
        ensureFirebaseInitialized()
    }

    fun ensureFirebaseInitialized(): Boolean {
        return try {
            if (FirebaseApp.getApps(context).isNotEmpty()) {
                true
            } else {
                // Check if custom config exists in SharedPreferences
                val apiKey = prefs.getString(PREF_API_KEY, null)
                val appId = prefs.getString(PREF_APP_ID, null)
                val projectId = prefs.getString(PREF_PROJECT_ID, null)

                if (!apiKey.isNullOrBlank() && !appId.isNullOrBlank() && !projectId.isNullOrBlank()) {
                    val builder = FirebaseOptions.Builder()
                        .setApiKey(apiKey)
                        .setApplicationId(appId)
                        .setProjectId(projectId)
                    prefs.getString(PREF_STORAGE_BUCKET, null)?.let { if (it.isNotBlank()) builder.setStorageBucket(it) }
                    prefs.getString(PREF_MESSAGING_SENDER_ID, null)?.let { if (it.isNotBlank()) builder.setGcmSenderId(it) }

                    FirebaseApp.initializeApp(context, builder.build())
                    Log.i(TAG, "Initialized Firebase with stored credentials")
                    true
                } else {
                    Log.w(TAG, "No default google-services.json or stored credentials found")
                    false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing Firebase", e)
            false
        }
    }

    fun saveFirebaseConfig(
        apiKey: String,
        appId: String,
        projectId: String,
        storageBucket: String? = null,
        messagingSenderId: String? = null
    ): Boolean {
        prefs.edit().apply {
            putString(PREF_API_KEY, apiKey)
            putString(PREF_APP_ID, appId)
            putString(PREF_PROJECT_ID, projectId)
            putString(PREF_STORAGE_BUCKET, storageBucket ?: "")
            putString(PREF_MESSAGING_SENDER_ID, messagingSenderId ?: "")
            apply()
        }
        return try {
            val builder = FirebaseOptions.Builder()
                .setApiKey(apiKey)
                .setApplicationId(appId)
                .setProjectId(projectId)
            if (!storageBucket.isNullOrBlank()) builder.setStorageBucket(storageBucket)
            if (!messagingSenderId.isNullOrBlank()) builder.setGcmSenderId(messagingSenderId)

            if (FirebaseApp.getApps(context).isNotEmpty()) {
                val app = FirebaseApp.getInstance()
                app.delete()
            }
            FirebaseApp.initializeApp(context, builder.build())
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply Firebase configuration", e)
            false
        }
    }

    private fun getFirestore(): FirebaseFirestore? {
        return try {
            if (ensureFirebaseInitialized()) FirebaseFirestore.getInstance() else null
        } catch (e: Exception) {
            Log.e(TAG, "Firestore unavailable", e)
            null
        }
    }

    private fun getAuth(): FirebaseAuth? {
        return try {
            if (ensureFirebaseInitialized()) FirebaseAuth.getInstance() else null
        } catch (e: Exception) {
            Log.e(TAG, "FirebaseAuth unavailable", e)
            null
        }
    }

    private fun getStorage(): FirebaseStorage? {
        return try {
            if (ensureFirebaseInitialized()) FirebaseStorage.getInstance() else null
        } catch (e: Exception) {
            Log.e(TAG, "Firebase Storage unavailable", e)
            null
        }
    }

    /**
     * PRO-PLAN ONLY. Requires the project to be on the Blaze plan with Cloud Functions
     * deployed (see /functions in the repo root). There is no direct-Firestore fallback
     * for the operations that go through this — checkIsAdmin, addAdmin, removeAdmin,
     * and emailBelongsToMember all call deployed callable functions exclusively.
     */
    private fun getFunctions(): FirebaseFunctions? {
        return try {
            if (ensureFirebaseInitialized()) FirebaseFunctions.getInstance() else null
        } catch (e: Exception) {
            Log.e(TAG, "Firebase Functions unavailable", e)
            null
        }
    }

    val currentUser: FirebaseUser?
        get() = getAuth()?.currentUser

    // ----------------------------------------------------
    // AUTHENTICATION & ADMIN GATING
    // ----------------------------------------------------

    suspend fun signIn(email: String, pass: String): Result<FirebaseUser> {
        val auth = getAuth() ?: return Result.failure(Exception("Firebase is not initialized"))
        return try {
            val authResult = auth.signInWithEmailAndPassword(email.trim(), pass).await()
            val user = authResult.user ?: throw Exception("Authentication returned null user")
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun signUp(email: String, pass: String): Result<FirebaseUser> {
        val auth = getAuth() ?: return Result.failure(Exception("Firebase is not initialized"))
        val targetEmail = email.trim().lowercase()

        // Verify that email belongs to an existing member record
        val isMember = emailBelongsToMember(targetEmail)
        if (!isMember) {
            return Result.failure(
                Exception("This email is not registered as a member. Please contact your association administrator to add you first.")
            )
        }

        return try {
            val authResult = auth.createUserWithEmailAndPassword(targetEmail, pass).await()
            val user = authResult.user ?: throw Exception("Account creation returned null user")
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * PRO-PLAN ONLY: routed entirely through the "phoneBelongsToMember" Cloud Function,
     * mirroring emailBelongsToMember. Call this BEFORE sending an OTP — phone sign-in
     * auto-creates a Firebase user on first successful verification, so this is the
     * only gate keeping non-members from ever getting an account.
     */
    suspend fun phoneBelongsToMember(phone: String): Boolean {
        val functions = getFunctions() ?: return false
        return try {
            val result = functions.getHttpsCallable("phoneBelongsToMember")
                .call(mapOf("phone" to phone.trim()))
                .await()
            (result.data as? Map<*, *>)?.get("belongsToMember") as? Boolean ?: false
        } catch (e: Exception) {
            Log.e(TAG, "Error checking member phone via Cloud Function", e)
            false
        }
    }

    /**
     * Starts phone-number verification. phoneNumber must be E.164 (e.g. "+919876543210").
     * Results arrive via the callbacks: onVerificationCompleted (instant auto-retrieval,
     * mainly Play-services devices), onVerificationFailed, or onCodeSent (manual entry).
     */
    fun sendOtp(
        phoneNumber: String,
        activity: android.app.Activity,
        callbacks: com.google.firebase.auth.PhoneAuthProvider.OnVerificationStateChangedCallbacks
    ) {
        val auth = getAuth()
        if (auth == null) {
            callbacks.onVerificationFailed(com.google.firebase.FirebaseException("Firebase is not initialized"))
            return
        }
        val options = com.google.firebase.auth.PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(phoneNumber.trim())
            .setTimeout(60L, java.util.concurrent.TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(callbacks)
            .build()
        com.google.firebase.auth.PhoneAuthProvider.verifyPhoneNumber(options)
    }

    fun buildOtpCredential(verificationId: String, code: String): com.google.firebase.auth.PhoneAuthCredential =
        com.google.firebase.auth.PhoneAuthProvider.getCredential(verificationId, code)

    /**
     * Signs in (or, on first use, silently creates) the Firebase user for this phone
     * credential. There is no separate "createUser" call for phone auth — Firebase
     * does both in one step, which is why phoneBelongsToMember must run first.
     */
    suspend fun signInWithPhoneCredential(
        credential: com.google.firebase.auth.PhoneAuthCredential
    ): Result<FirebaseUser> {
        val auth = getAuth() ?: return Result.failure(Exception("Firebase is not initialized"))
        return try {
            val authResult = auth.signInWithCredential(credential).await()
            val user = authResult.user ?: throw Exception("Authentication returned null user")
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun signOut() {
        getAuth()?.signOut()
    }

    suspend fun sendPasswordReset(email: String): Result<Unit> {
        val auth = getAuth() ?: return Result.failure(Exception("Firebase is not initialized"))
        return try {
            auth.sendPasswordResetEmail(email.trim()).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * PRO-PLAN ONLY: routed entirely through the "emailBelongsToMember" Cloud Function.
     * No direct Firestore read of the members collection happens on-device for this check
     * anymore — the function runs with the Admin SDK server-side, so an unauthenticated
     * caller (e.g. during sign-up) never needs read access to the members collection at all.
     */
    suspend fun emailBelongsToMember(email: String): Boolean {
        val functions = getFunctions() ?: return false
        return try {
            val result = functions.getHttpsCallable("emailBelongsToMember")
                .call(mapOf("email" to email.trim().lowercase()))
                .await()
            (result.data as? Map<*, *>)?.get("belongsToMember") as? Boolean ?: false
        } catch (e: Exception) {
            Log.e(TAG, "Error checking member email via Cloud Function", e)
            false
        }
    }

    /**
     * PRO-PLAN ONLY: routed entirely through the "checkIsAdmin" Cloud Function.
     * The client never reads the admins collection directly, so Firestore Rules can
     * (and should) lock that collection down to server-only access.
     */
    suspend fun checkIsAdmin(phone: String?): Boolean {
        if (phone.isNullOrBlank()) return false
        val functions = getFunctions() ?: return false
        return try {
            val result = functions.getHttpsCallable("checkIsAdmin")
                .call(mapOf("phone" to phone.trim()))
                .await()
            (result.data as? Map<*, *>)?.get("isAdmin") as? Boolean ?: false
        } catch (e: Exception) {
            Log.w(TAG, "Could not resolve admin status via Cloud Function", e)
            false
        }
    }

    fun getAdminsFlow(): Flow<List<String>> = callbackFlow {
        val firestore = getFirestore()
        if (firestore == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val listener: ListenerRegistration = firestore.collection("admins")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Admins snapshot error", error)
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.filter {
                    it.getBoolean("isAdmin") == true
                }?.map { it.id } ?: emptyList()
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    /**
     * PRO-PLAN ONLY: routed entirely through the "addAdmin" Cloud Function, which itself
     * checks (server-side, via the Admin SDK) that the CALLER is already an admin before
     * granting admin status to anyone else. The client no longer has direct write access
     * to the admins collection — see Firestore Rules.
     */
    suspend fun addAdmin(phone: String): Result<Unit> {
        val functions = getFunctions() ?: return Result.failure(Exception("Firebase Functions unavailable"))
        return try {
            functions.getHttpsCallable("addAdmin")
                .call(mapOf("phone" to phone.trim()))
                .await()
            logActivity("Added admin", phone.trim())
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** PRO-PLAN ONLY: routed entirely through the "removeAdmin" Cloud Function. See addAdmin. */
    suspend fun removeAdmin(phone: String): Result<Unit> {
        val functions = getFunctions() ?: return Result.failure(Exception("Firebase Functions unavailable"))
        return try {
            functions.getHttpsCallable("removeAdmin")
                .call(mapOf("phone" to phone.trim()))
                .await()
            logActivity("Removed admin", phone.trim())
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ----------------------------------------------------
    // REAL-TIME SNAPSHOT LISTENERS
    // ----------------------------------------------------

    fun getMembersFlow(): Flow<List<Member>> = callbackFlow {
        val firestore = getFirestore()
        if (firestore == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val listener = firestore.collection("members")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Members listener error", error)
                    return@addSnapshotListener
                }
                val members = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        val familyRaw = doc.get("family") as? List<Map<String, Any>> ?: emptyList()
                        val familyList = familyRaw.map { famMap ->
                            FamilyMember(
                                id = famMap["id"] as? String ?: "",
                                name = famMap["name"] as? String ?: "",
                                relation = famMap["relation"] as? String ?: "",
                                photo = famMap["photo"] as? String ?: ""
                            )
                        }
                        Member(
                            docId = doc.id,
                            name = doc.getString("name") ?: "",
                            contact = doc.getString("contact") ?: "",
                            email = doc.getString("email") ?: "",
                            memberType = doc.getString("memberType") ?: "OM",
                            gender = doc.getString("gender") ?: "Male",
                            photo = doc.getString("photo"),
                            joinDate = doc.getString("joinDate") ?: "",
                            family = familyList,
                            id = doc.getLong("id")
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed parsing member ${doc.id}", e)
                        null
                    }
                }?.sortedBy { it.name.lowercase() } ?: emptyList()
                trySend(members)
            }
        awaitClose { listener.remove() }
    }

    fun getMaintenanceCollectionsFlow(): Flow<List<MaintenanceCollection>> = callbackFlow {
        val firestore = getFirestore()
        if (firestore == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val listener = firestore.collection("maintenanceCollections")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Maintenance listener error", error)
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        // Handle memberId stored as String or Number from older web app
                        val rawMemberId = doc.get("memberId")
                        val memberIdStr = when (rawMemberId) {
                            is String -> rawMemberId
                            is Number -> rawMemberId.toString()
                            else -> ""
                        }
                        MaintenanceCollection(
                            docId = doc.id,
                            memberId = memberIdStr,
                            contact = doc.getString("contact") ?: "",
                            amount = doc.getLong("amount") ?: 0L,
                            date = doc.getString("date") ?: "",
                            period = doc.getLong("period")?.toInt() ?: 1,
                            startMonth = doc.getString("startMonth") ?: "",
                            endMonth = doc.getString("endMonth") ?: "",
                            receiptNumber = doc.getString("receiptNumber") ?: "",
                            timestamp = doc.getString("timestamp") ?: ""
                        )
                    } catch (e: Exception) {
                        null
                    }
                }?.sortedByDescending { it.date } ?: emptyList()
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    fun getEventsFlow(): Flow<List<Event>> = callbackFlow {
        val firestore = getFirestore()
        if (firestore == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val listener = firestore.collection("events")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Events listener error", error)
                    return@addSnapshotListener
                }
                val events = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        val expensesRaw = doc.get("expenses") as? List<Map<String, Any>> ?: emptyList()
                        val expenseList = expensesRaw.map { exp ->
                            EventExpense(
                                description = exp["description"] as? String ?: "",
                                amount = (exp["amount"] as? Number)?.toLong() ?: 0L,
                                date = exp["date"] as? String ?: ""
                            )
                        }
                        val attachmentsRaw = doc.get("attachments") as? List<Map<String, Any>> ?: emptyList()
                        val attachmentList = attachmentsRaw.map { att ->
                            AttachmentItem(
                                name = att["name"] as? String ?: "",
                                type = att["type"] as? String ?: "",
                                size = (att["size"] as? Number)?.toLong() ?: 0L,
                                data = att["data"] as? String ?: "",
                                uploadedAt = att["uploadedAt"] as? String ?: ""
                            )
                        }
                        Event(
                            docId = doc.id,
                            name = doc.getString("name") ?: "",
                            date = doc.getString("date") ?: "",
                            fee = doc.getLong("fee") ?: 0L,
                            expenses = expenseList,
                            attachments = attachmentList
                        )
                    } catch (e: Exception) {
                        null
                    }
                }?.sortedByDescending { it.date } ?: emptyList()
                trySend(events)
            }
        awaitClose { listener.remove() }
    }

    fun getEventCollectionsFlow(): Flow<List<EventCollection>> = callbackFlow {
        val firestore = getFirestore()
        if (firestore == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val listener = firestore.collection("eventCollections")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "EventCollections listener error", error)
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        val rawEventId = doc.get("eventId")
                        val eventIdStr = when (rawEventId) {
                            is String -> rawEventId
                            is Number -> rawEventId.toString()
                            else -> ""
                        }
                        val rawMemberId = doc.get("memberId")
                        val memberIdStr = when (rawMemberId) {
                            is String -> rawMemberId
                            is Number -> rawMemberId.toString()
                            else -> ""
                        }
                        EventCollection(
                            docId = doc.id,
                            eventId = eventIdStr,
                            memberId = memberIdStr,
                            contact = doc.getString("contact") ?: "",
                            amount = doc.getLong("amount") ?: 0L,
                            date = doc.getString("date") ?: "",
                            receiptNumber = doc.getString("receiptNumber") ?: ""
                        )
                    } catch (e: Exception) {
                        null
                    }
                }?.sortedByDescending { it.date } ?: emptyList()
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    fun getBankTransactionsFlow(): Flow<List<BankTransaction>> = callbackFlow {
        val firestore = getFirestore()
        if (firestore == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val listener = firestore.collection("bankTransactions")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "BankTransactions listener error", error)
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        BankTransaction(
                            docId = doc.id,
                            transactionType = doc.getString("transactionType") ?: "deposit",
                            amount = doc.getLong("amount") ?: 0L,
                            date = doc.getString("date") ?: "",
                            transactionId = doc.getString("transactionId") ?: "",
                            remarks = doc.getString("remarks") ?: ""
                        )
                    } catch (e: Exception) {
                        null
                    }
                }?.sortedByDescending { it.date } ?: emptyList()
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    fun getMeetingsFlow(): Flow<List<Meeting>> = callbackFlow {
        val firestore = getFirestore()
        if (firestore == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val listener = firestore.collection("meetings")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Meetings listener error", error)
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        val attendeesRaw = doc.get("attendees") as? List<Map<String, Any>> ?: emptyList()
                        val attendees = attendeesRaw.map { a ->
                            MeetingAttendee(
                                name = a["name"] as? String ?: "",
                                contact = a["contact"] as? String ?: ""
                            )
                        }
                        val agendaRaw = doc.get("agenda") as? List<String> ?: emptyList()
                        val actionItemsRaw = doc.get("actionItems") as? List<Map<String, Any>> ?: emptyList()
                        val actionItems = actionItemsRaw.map { act ->
                            MeetingActionItem(
                                task = act["task"] as? String ?: "",
                                assignedTo = act["assignedTo"] as? String ?: "",
                                dueDate = act["dueDate"] as? String ?: "",
                                status = act["status"] as? String ?: "Pending"
                            )
                        }
                        val attachmentsRaw = doc.get("attachments") as? List<Map<String, Any>> ?: emptyList()
                        val attachments = attachmentsRaw.map { att ->
                            AttachmentItem(
                                name = att["name"] as? String ?: "",
                                type = att["type"] as? String ?: "",
                                size = (att["size"] as? Number)?.toLong() ?: 0L,
                                data = att["data"] as? String ?: "",
                                uploadedAt = att["uploadedAt"] as? String ?: ""
                            )
                        }
                        Meeting(
                            docId = doc.id,
                            title = doc.getString("title") ?: "",
                            date = doc.getString("date") ?: "",
                            time = doc.getString("time") ?: "",
                            location = doc.getString("location") ?: "",
                            attendees = attendees,
                            agenda = agendaRaw,
                            minutes = doc.getString("minutes") ?: "",
                            minutesPhoto = doc.getString("minutesPhoto") ?: "",
                            actionItems = actionItems,
                            attachments = attachments,
                            status = doc.getString("status") ?: "scheduled",
                            createdAt = doc.getString("createdAt") ?: "",
                            updatedAt = doc.getString("updatedAt") ?: ""
                        )
                    } catch (e: Exception) {
                        null
                    }
                }?.sortedByDescending { it.date } ?: emptyList()
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    fun getDonationsFlow(): Flow<List<Donation>> = callbackFlow {
        val firestore = getFirestore()
        if (firestore == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val listener = firestore.collection("donations")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Donations listener error", error)
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        Donation(
                            docId = doc.id,
                            donorName = doc.getString("donorName") ?: "",
                            donorContact = doc.getString("donorContact") ?: "",
                            memberId = doc.getString("memberId"),
                            amount = doc.getLong("amount") ?: 0L,
                            date = doc.getString("date") ?: "",
                            eventId = doc.getString("eventId"),
                            purpose = doc.getString("purpose") ?: "",
                            createdAt = doc.getString("createdAt") ?: ""
                        )
                    } catch (e: Exception) {
                        null
                    }
                }?.sortedByDescending { it.date } ?: emptyList()
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    fun getGeneralExpensesFlow(): Flow<List<GeneralExpense>> = callbackFlow {
        val firestore = getFirestore()
        if (firestore == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val listener = firestore.collection("generalExpenses")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "GeneralExpenses listener error", error)
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        GeneralExpense(
                            docId = doc.id,
                            description = doc.getString("description") ?: "",
                            amount = doc.getLong("amount") ?: 0L,
                            date = doc.getString("date") ?: "",
                            category = doc.getString("category") ?: "",
                            eventId = doc.getString("eventId"),
                            createdAt = doc.getString("createdAt") ?: ""
                        )
                    } catch (e: Exception) {
                        null
                    }
                }?.sortedByDescending { it.date } ?: emptyList()
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    fun getActivityLogFlow(): Flow<List<ActivityLogEntry>> = callbackFlow {
        val firestore = getFirestore()
        if (firestore == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val listener = firestore.collection("activityLog")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "ActivityLog listener error", error)
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        ActivityLogEntry(
                            docId = doc.id,
                            adminEmail = doc.getString("adminEmail") ?: "",
                            action = doc.getString("action") ?: "",
                            details = doc.getString("details") ?: "",
                            timestamp = doc.getString("timestamp") ?: ""
                        )
                    } catch (e: Exception) {
                        null
                    }
                }?.sortedByDescending { it.timestamp } ?: emptyList()
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    fun getServiceListingsFlow(): Flow<List<ServiceListing>> = callbackFlow {
        val firestore = getFirestore()
        if (firestore == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val listener = firestore.collection("serviceListings")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "ServiceListings listener error", error)
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        ServiceListing(
                            docId = doc.id,
                            memberId = doc.getString("memberId") ?: "",
                            businessName = doc.getString("businessName") ?: "",
                            category = doc.getString("category") ?: "",
                            description = doc.getString("description") ?: "",
                            location = doc.getString("location") ?: "",
                            status = doc.getString("status") ?: "pending",
                            submittedAt = doc.getString("submittedAt") ?: "",
                            approvedAt = doc.getString("approvedAt"),
                            approvedBy = doc.getString("approvedBy")
                        )
                    } catch (e: Exception) {
                        null
                    }
                }?.sortedByDescending { it.submittedAt } ?: emptyList()
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    fun getSettingsFlow(): Flow<AssociationSettings> = callbackFlow {
        val firestore = getFirestore()
        if (firestore == null) {
            trySend(AssociationSettings())
            close()
            return@callbackFlow
        }
        val listener = firestore.collection("settings")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Settings listener error", error)
                    return@addSnapshotListener
                }
                var name = "Residents Association"
                var regNo = ""
                var location = ""
                var fee = 500L
                var initialBal = 0L

                snapshot?.documents?.forEach { doc ->
                    val key = doc.getString("key") ?: doc.id.removePrefix("setting_")
                    when (key) {
                        "associationName" -> name = doc.getString("value") ?: (doc.get("value") as? String) ?: name
                        "associationRegNo" -> regNo = doc.getString("value") ?: (doc.get("value") as? String) ?: regNo
                        "associationLocation" -> location = doc.getString("value") ?: (doc.get("value") as? String) ?: location
                        "monthlyFee" -> fee = (doc.get("value") as? Number)?.toLong() ?: fee
                        "initialBankBalance" -> initialBal = (doc.get("value") as? Number)?.toLong() ?: initialBal
                    }
                }
                trySend(AssociationSettings(name, regNo, location, fee, initialBal))
            }
        awaitClose { listener.remove() }
    }

    fun getOfficeBearersFlow(): Flow<OfficeBearers> = callbackFlow {
        val firestore = getFirestore()
        if (firestore == null) {
            trySend(OfficeBearers())
            close()
            return@callbackFlow
        }
        val listener = firestore.collection("officeBearers").document("association_officials")
            .addSnapshotListener { doc, error ->
                if (error != null || doc == null || !doc.exists()) {
                    trySend(OfficeBearers())
                    return@addSnapshotListener
                }
                fun getPerson(field: String): Pair<String, String> {
                    val map = doc.get(field) as? Map<String, Any>
                    val n = map?.get("name") as? String ?: ""
                    val c = map?.get("contact") as? String ?: ""
                    return Pair(n, c)
                }
                val pres = getPerson("president")
                val vp = getPerson("vicePresident")
                val sec = getPerson("secretary")
                val jsec = getPerson("jointSecretary")
                val tr = getPerson("treasurer")
                val pat = getPerson("patron")
                val comm = doc.getString("committeeMembers") ?: ""

                trySend(
                    OfficeBearers(
                        presidentName = pres.first,
                        presidentContact = pres.second,
                        vicePresidentName = vp.first,
                        vicePresidentContact = vp.second,
                        secretaryName = sec.first,
                        secretaryContact = sec.second,
                        jointSecretaryName = jsec.first,
                        jointSecretaryContact = jsec.second,
                        treasurerName = tr.first,
                        treasurerContact = tr.second,
                        patronName = pat.first,
                        patronContact = pat.second,
                        committeeMembers = comm
                    )
                )
            }
        awaitClose { listener.remove() }
    }

    // ----------------------------------------------------
    // MUTATIONS & WRITES
    // ----------------------------------------------------

    suspend fun addMember(member: Member): Result<String> {
        val firestore = getFirestore() ?: return Result.failure(Exception("Firestore unavailable"))
        return try {
            val docId = if (member.contact.isNotBlank()) "member_${member.contact.trim()}" else firestore.collection("members").document().id
            val map = mapOf(
                "name" to member.name.trim(),
                "contact" to member.contact.trim(),
                "email" to member.email.trim(),
                "memberType" to member.memberType,
                "gender" to member.gender,
                "photo" to member.photo,
                "joinDate" to member.joinDate,
                "family" to member.family.map {
                    mapOf("id" to it.id, "name" to it.name, "relation" to it.relation, "photo" to it.photo)
                }
            )
            firestore.collection("members").document(docId).set(map, SetOptions.merge()).await()
            Result.success(docId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateMember(member: Member): Result<Unit> {
        val firestore = getFirestore() ?: return Result.failure(Exception("Firestore unavailable"))
        return try {
            // Best-effort: if the photo changed, delete the now-orphaned old Storage file.
            try {
                val prev = firestore.collection("members").document(member.docId).get().await()
                val prevPhoto = prev.getString("photo")
                if (!prevPhoto.isNullOrBlank() && prevPhoto != member.photo) {
                    ImageUtils.deleteFromStorage(prevPhoto)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not check previous photo for member ${member.docId}", e)
            }
            val map = mapOf(
                "name" to member.name.trim(),
                "contact" to member.contact.trim(),
                "email" to member.email.trim(),
                "memberType" to member.memberType,
                "gender" to member.gender,
                "photo" to member.photo,
                "joinDate" to member.joinDate,
                "family" to member.family.map {
                    mapOf("id" to it.id, "name" to it.name, "relation" to it.relation, "photo" to it.photo)
                }
            )
            firestore.collection("members").document(member.docId).set(map, SetOptions.merge()).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteMember(docId: String): Result<Unit> {
        val firestore = getFirestore() ?: return Result.failure(Exception("Firestore unavailable"))
        return try {
            // Best-effort Storage cleanup: fetch the doc first so we know which
            // Cloud Storage files (profile + family photos) belong to this member.
            try {
                val snap = firestore.collection("members").document(docId).get().await()
                ImageUtils.deleteFromStorage(snap.getString("photo"))
                @Suppress("UNCHECKED_CAST")
                val family = snap.get("family") as? List<Map<String, Any?>>
                family?.forEach { fam -> ImageUtils.deleteFromStorage(fam["photo"] as? String) }
            } catch (e: Exception) {
                Log.w(TAG, "Could not clean up Storage photos for member $docId", e)
            }
            firestore.collection("members").document(docId).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun recordMaintenancePayment(collection: MaintenanceCollection): Result<String> {
        val firestore = getFirestore() ?: return Result.failure(Exception("Firestore unavailable"))
        return try {
            val docRef = firestore.collection("maintenanceCollections").document()
            val map = mapOf(
                "memberId" to collection.memberId, // Firestore doc ID of member!
                "contact" to collection.contact,
                "amount" to collection.amount,
                "date" to collection.date,
                "period" to collection.period,
                "startMonth" to collection.startMonth,
                "endMonth" to collection.endMonth,
                "receiptNumber" to collection.receiptNumber,
                "timestamp" to collection.timestamp
            )
            docRef.set(map).await()
            Result.success(docRef.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteMaintenancePayment(docId: String): Result<Unit> {
        val firestore = getFirestore() ?: return Result.failure(Exception("Firestore unavailable"))
        return try {
            firestore.collection("maintenanceCollections").document(docId).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createEvent(event: Event): Result<String> {
        val firestore = getFirestore() ?: return Result.failure(Exception("Firestore unavailable"))
        return try {
            val docRef = firestore.collection("events").document()
            val map = mapOf(
                "name" to event.name.trim(),
                "date" to event.date,
                "fee" to event.fee,
                "expenses" to event.expenses.map {
                    mapOf("description" to it.description, "amount" to it.amount, "date" to it.date)
                },
                "attachments" to event.attachments.map {
                    mapOf("name" to it.name, "type" to it.type, "size" to it.size, "data" to it.data, "uploadedAt" to it.uploadedAt)
                }
            )
            docRef.set(map).await()
            Result.success(docRef.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateEvent(event: Event): Result<Unit> {
        val firestore = getFirestore() ?: return Result.failure(Exception("Firestore unavailable"))
        return try {
            val map = mapOf(
                "name" to event.name.trim(),
                "date" to event.date,
                "fee" to event.fee,
                "expenses" to event.expenses.map {
                    mapOf("description" to it.description, "amount" to it.amount, "date" to it.date)
                },
                "attachments" to event.attachments.map {
                    mapOf("name" to it.name, "type" to it.type, "size" to it.size, "data" to it.data, "uploadedAt" to it.uploadedAt)
                }
            )
            firestore.collection("events").document(event.docId).set(map, SetOptions.merge()).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteEvent(docId: String): Result<Unit> {
        val firestore = getFirestore() ?: return Result.failure(Exception("Firestore unavailable"))
        return try {
            firestore.collection("events").document(docId).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun recordEventPayment(ec: EventCollection): Result<String> {
        val firestore = getFirestore() ?: return Result.failure(Exception("Firestore unavailable"))
        return try {
            val docRef = firestore.collection("eventCollections").document()
            val map = mapOf(
                "eventId" to ec.eventId,
                "memberId" to ec.memberId, // Firestore doc ID of member!
                "contact" to ec.contact,
                "amount" to ec.amount,
                "date" to ec.date,
                "receiptNumber" to ec.receiptNumber
            )
            docRef.set(map).await()
            Result.success(docRef.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteEventPayment(docId: String): Result<Unit> {
        val firestore = getFirestore() ?: return Result.failure(Exception("Firestore unavailable"))
        return try {
            firestore.collection("eventCollections").document(docId).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun recordBankTransaction(bt: BankTransaction): Result<String> {
        val firestore = getFirestore() ?: return Result.failure(Exception("Firestore unavailable"))
        return try {
            val docRef = firestore.collection("bankTransactions").document()
            val map = mapOf(
                "transactionType" to bt.transactionType,
                "amount" to bt.amount,
                "date" to bt.date,
                "transactionId" to bt.transactionId,
                "remarks" to bt.remarks
            )
            docRef.set(map).await()
            logActivity("Recorded bank ${bt.transactionType}", "₹${bt.amount}${if (bt.remarks.isNotBlank()) " - ${bt.remarks}" else ""}")
            Result.success(docRef.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteBankTransaction(docId: String, type: String, amount: Long): Result<Unit> {
        val firestore = getFirestore() ?: return Result.failure(Exception("Firestore unavailable"))
        return try {
            firestore.collection("bankTransactions").document(docId).delete().await()
            logActivity("Deleted bank $type", "₹$amount")
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun recordDonation(donation: Donation): Result<String> {
        val firestore = getFirestore() ?: return Result.failure(Exception("Firestore unavailable"))
        return try {
            val docRef = firestore.collection("donations").document()
            val map = mapOf(
                "donorName" to donation.donorName.trim(),
                "donorContact" to donation.donorContact.trim(),
                "memberId" to donation.memberId,
                "amount" to donation.amount,
                "date" to donation.date,
                "eventId" to donation.eventId,
                "purpose" to donation.purpose.trim(),
                "createdAt" to donation.createdAt
            )
            docRef.set(map).await()
            logActivity("Recorded donation", "${donation.donorName} - ₹${donation.amount}")
            Result.success(docRef.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteDonation(donation: Donation): Result<Unit> {
        val firestore = getFirestore() ?: return Result.failure(Exception("Firestore unavailable"))
        return try {
            firestore.collection("donations").document(donation.docId).delete().await()
            logActivity("Deleted donation", "${donation.donorName} - ₹${donation.amount}")
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun recordGeneralExpense(expense: GeneralExpense): Result<String> {
        val firestore = getFirestore() ?: return Result.failure(Exception("Firestore unavailable"))
        return try {
            val docRef = firestore.collection("generalExpenses").document()
            val map = mapOf(
                "description" to expense.description.trim(),
                "amount" to expense.amount,
                "date" to expense.date,
                "category" to expense.category,
                "eventId" to expense.eventId,
                "createdAt" to expense.createdAt
            )
            docRef.set(map).await()
            logActivity("Recorded general expense", "${expense.description} - ₹${expense.amount}")
            Result.success(docRef.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteGeneralExpense(expense: GeneralExpense): Result<Unit> {
        val firestore = getFirestore() ?: return Result.failure(Exception("Firestore unavailable"))
        return try {
            firestore.collection("generalExpenses").document(expense.docId).delete().await()
            logActivity("Deleted general expense", "${expense.description} - ₹${expense.amount}")
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createMeeting(meeting: Meeting): Result<String> {
        val firestore = getFirestore() ?: return Result.failure(Exception("Firestore unavailable"))
        return try {
            val docRef = firestore.collection("meetings").document()
            val map = mapOf(
                "title" to meeting.title.trim(),
                "date" to meeting.date,
                "time" to meeting.time,
                "location" to meeting.location,
                "attendees" to meeting.attendees.map { mapOf("name" to it.name, "contact" to it.contact) },
                "agenda" to meeting.agenda,
                "minutes" to meeting.minutes,
                "minutesPhoto" to meeting.minutesPhoto,
                "actionItems" to meeting.actionItems.map {
                    mapOf("task" to it.task, "assignedTo" to it.assignedTo, "dueDate" to it.dueDate, "status" to it.status)
                },
                "attachments" to meeting.attachments.map {
                    mapOf("name" to it.name, "type" to it.type, "size" to it.size, "data" to it.data, "uploadedAt" to it.uploadedAt)
                },
                "status" to meeting.status,
                "createdAt" to meeting.createdAt,
                "updatedAt" to meeting.updatedAt
            )
            docRef.set(map).await()
            Result.success(docRef.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateMeeting(meeting: Meeting): Result<Unit> {
        val firestore = getFirestore() ?: return Result.failure(Exception("Firestore unavailable"))
        return try {
            val map = mapOf(
                "title" to meeting.title.trim(),
                "date" to meeting.date,
                "time" to meeting.time,
                "location" to meeting.location,
                "attendees" to meeting.attendees.map { mapOf("name" to it.name, "contact" to it.contact) },
                "agenda" to meeting.agenda,
                "minutes" to meeting.minutes,
                "minutesPhoto" to meeting.minutesPhoto,
                "actionItems" to meeting.actionItems.map {
                    mapOf("task" to it.task, "assignedTo" to it.assignedTo, "dueDate" to it.dueDate, "status" to it.status)
                },
                "attachments" to meeting.attachments.map {
                    mapOf("name" to it.name, "type" to it.type, "size" to it.size, "data" to it.data, "uploadedAt" to it.uploadedAt)
                },
                "status" to meeting.status,
                "updatedAt" to meeting.updatedAt
            )
            firestore.collection("meetings").document(meeting.docId).set(map, SetOptions.merge()).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteMeeting(docId: String): Result<Unit> {
        val firestore = getFirestore() ?: return Result.failure(Exception("Firestore unavailable"))
        return try {
            // Firestore does not delete subcollections automatically, and Storage
            // files are not deleted just because the Firestore doc pointing to them is.
            val photos = firestore.collection("meetings").document(docId)
                .collection("photos").get().await()
            for (p in photos.documents) {
                ImageUtils.deleteFromStorage(p.getString("data"))
                p.reference.delete().await()
            }
            firestore.collection("meetings").document(docId).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ----------------------------------------------------
    // MEETING SCANNED-MINUTES PHOTOS (subcollection meetings/{id}/photos)
    // ----------------------------------------------------

    fun getMeetingPhotosFlow(meetingId: String): Flow<List<MeetingPhoto>> = callbackFlow {
        val firestore = getFirestore()
        if (firestore == null || meetingId.isBlank()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val listener = firestore.collection("meetings").document(meetingId)
            .collection("photos")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Meeting photos listener error", error)
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { doc ->
                    val data = doc.getString("data")
                    if (data.isNullOrBlank()) null
                    else MeetingPhoto(
                        docId = doc.id,
                        data = data,
                        createdAt = doc.getLong("createdAt") ?: 0L
                    )
                }?.sortedBy { it.createdAt } ?: emptyList()
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    suspend fun addMeetingPhoto(meetingId: String, dataUrl: String): Result<Unit> {
        val firestore = getFirestore() ?: return Result.failure(Exception("Firestore unavailable"))
        return try {
            val map = mapOf(
                "data" to dataUrl,
                "createdAt" to System.currentTimeMillis()
            )
            firestore.collection("meetings").document(meetingId)
                .collection("photos").add(map).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteMeetingPhoto(meetingId: String, photoId: String): Result<Unit> {
        val firestore = getFirestore() ?: return Result.failure(Exception("Firestore unavailable"))
        return try {
            val docRef = firestore.collection("meetings").document(meetingId)
                .collection("photos").document(photoId)
            try {
                val snap = docRef.get().await()
                ImageUtils.deleteFromStorage(snap.getString("data"))
            } catch (e: Exception) {
                Log.w(TAG, "Could not clean up Storage file for meeting photo $photoId", e)
            }
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ----------------------------------------------------
    // BACKUP / RESTORE (JSON)
    // ----------------------------------------------------

    // The "admins" collection is deliberately NOT part of backups (a restore must never be able to grant admin rights).
    private val backupRootCollections = listOf(
        "members", "maintenanceCollections", "events", "eventCollections", "bankTransactions",
        "donations", "generalExpenses", "meetings", "serviceListings", "settings",
        "officeBearers", "activityLog"
    )

    private fun writeBackupValue(w: android.util.JsonWriter, v: Any?) {
        when (v) {
            null -> w.nullValue()
            is String -> w.value(v)
            is Boolean -> w.value(v)
            is Number -> w.value(v)
            is Timestamp -> {
                w.beginObject()
                w.name("__ts"); w.value(v.seconds)
                w.name("__ns"); w.value(v.nanoseconds.toLong())
                w.endObject()
            }
            is Map<*, *> -> {
                w.beginObject()
                for ((k, x) in v) {
                    w.name(k.toString())
                    writeBackupValue(w, x)
                }
                w.endObject()
            }
            is List<*> -> {
                w.beginArray()
                for (x in v) writeBackupValue(w, x)
                w.endArray()
            }
            else -> w.value(v.toString())
        }
    }

    private fun writeBackupDoc(w: android.util.JsonWriter, path: String, data: Map<String, Any?>?) {
        w.beginObject()
        w.name("path"); w.value(path)
        w.name("data"); writeBackupValue(w, data ?: emptyMap<String, Any?>())
        w.endObject()
    }

    private fun readBackupValue(r: android.util.JsonReader): Any? {
        return when (r.peek()) {
            android.util.JsonToken.NULL -> { r.nextNull(); null }
            android.util.JsonToken.BOOLEAN -> r.nextBoolean()
            android.util.JsonToken.STRING -> r.nextString()
            android.util.JsonToken.NUMBER -> {
                val text = r.nextString()
                text.toLongOrNull() ?: text.toDouble()
            }
            android.util.JsonToken.BEGIN_ARRAY -> {
                val list = mutableListOf<Any?>()
                r.beginArray()
                while (r.hasNext()) list.add(readBackupValue(r))
                r.endArray()
                list
            }
            android.util.JsonToken.BEGIN_OBJECT -> {
                val map = linkedMapOf<String, Any?>()
                r.beginObject()
                while (r.hasNext()) {
                    val key = r.nextName()
                    map[key] = readBackupValue(r)
                }
                r.endObject()
                val secs = map["__ts"]
                val nanos = map["__ns"]
                if (map.size == 2 && secs is Number && nanos is Number) {
                    Timestamp(secs.toLong(), nanos.toInt())
                } else {
                    map
                }
            }
            else -> { r.skipValue(); null }
        }
    }

    /** Writes all association data as JSON to [out]. Returns the number of records written. */
    suspend fun exportBackup(out: OutputStream): Result<Int> = withContext(Dispatchers.IO) {
        val firestore = getFirestore() ?: return@withContext Result.failure<Int>(Exception("Firestore unavailable"))
        try {
            var count = 0
            val writer = android.util.JsonWriter(OutputStreamWriter(out, Charsets.UTF_8))
            writer.beginObject()
            writer.name("app"); writer.value("rasm-native")
            writer.name("version"); writer.value(1L)
            writer.name("exportedAt"); writer.value(System.currentTimeMillis())
            writer.name("docs")
            writer.beginArray()
            for (col in backupRootCollections) {
                val snap = firestore.collection(col).get().await()
                for (doc in snap.documents) {
                    writeBackupDoc(writer, doc.reference.path, doc.data)
                    count++
                    if (col == "meetings") {
                        val photos = doc.reference.collection("photos").get().await()
                        for (p in photos.documents) {
                            writeBackupDoc(writer, p.reference.path, p.data)
                            count++
                        }
                    }
                }
            }
            writer.endArray()
            writer.endObject()
            writer.flush()
            Result.success(count)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Restores records from a backup file. Existing records with the same path are overwritten;
     * records that are not in the backup are left untouched. Only known association collections
     * are restored. Returns the number of records written.
     */
    suspend fun importBackup(input: InputStream): Result<Int> = withContext(Dispatchers.IO) {
        val firestore = getFirestore() ?: return@withContext Result.failure<Int>(Exception("Firestore unavailable"))
        try {
            val reader = android.util.JsonReader(InputStreamReader(input, Charsets.UTF_8))
            var count = 0
            var appOk = false
            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "app" -> appOk = reader.nextString() == "rasm-native"
                    "docs" -> {
                        if (!appOk) throw IllegalArgumentException("This file is not a RASM backup")
                        reader.beginArray()
                        while (reader.hasNext()) {
                            val item = readBackupValue(reader) as? Map<*, *>
                            val path = item?.get("path") as? String
                            @Suppress("UNCHECKED_CAST")
                            val data = item?.get("data") as? Map<String, Any?>
                            if (path == null || data == null) continue
                            val segs = path.split("/")
                            val allowed = when (segs.size) {
                                2 -> segs[0] in backupRootCollections
                                4 -> segs[0] == "meetings" && segs[2] == "photos"
                                else -> false
                            }
                            if (!allowed) continue
                            firestore.document(path).set(data).await()
                            count++
                        }
                        reader.endArray()
                    }
                    else -> reader.skipValue()
                }
            }
            reader.endObject()
            Result.success(count)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun submitServiceListing(listing: ServiceListing): Result<String> {
        val firestore = getFirestore() ?: return Result.failure(Exception("Firestore unavailable"))
        return try {
            val docRef = firestore.collection("serviceListings").document()
            val map = mapOf(
                "memberId" to listing.memberId,
                "businessName" to listing.businessName.trim(),
                "category" to listing.category,
                "description" to listing.description.trim(),
                "location" to listing.location.trim(),
                "status" to listing.status,
                "submittedAt" to listing.submittedAt,
                "approvedAt" to listing.approvedAt,
                "approvedBy" to listing.approvedBy
            )
            docRef.set(map).await()
            Result.success(docRef.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateServiceListingStatus(docId: String, businessName: String, approve: Boolean, adminEmail: String): Result<Unit> {
        val firestore = getFirestore() ?: return Result.failure(Exception("Firestore unavailable"))
        return try {
            if (approve) {
                val map = mapOf(
                    "status" to "approved",
                    "approvedAt" to java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).format(java.util.Date()),
                    "approvedBy" to adminEmail
                )
                firestore.collection("serviceListings").document(docId).set(map, SetOptions.merge()).await()
                logActivity("Approved service listing", businessName)
            } else {
                firestore.collection("serviceListings").document(docId).delete().await()
                logActivity("Rejected service listing", businessName)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteServiceListing(docId: String, businessName: String): Result<Unit> {
        val firestore = getFirestore() ?: return Result.failure(Exception("Firestore unavailable"))
        return try {
            firestore.collection("serviceListings").document(docId).delete().await()
            logActivity("Deleted service listing", businessName)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateAssociationSettings(settings: AssociationSettings): Result<Unit> {
        val firestore = getFirestore() ?: return Result.failure(Exception("Firestore unavailable"))
        return try {
            val batch = firestore.batch()
            batch.set(firestore.collection("settings").document("setting_associationName"), mapOf("key" to "associationName", "value" to settings.associationName), SetOptions.merge())
            batch.set(firestore.collection("settings").document("setting_associationRegNo"), mapOf("key" to "associationRegNo", "value" to settings.associationRegNo), SetOptions.merge())
            batch.set(firestore.collection("settings").document("setting_associationLocation"), mapOf("key" to "associationLocation", "value" to settings.associationLocation), SetOptions.merge())
            batch.set(firestore.collection("settings").document("setting_monthlyFee"), mapOf("key" to "monthlyFee", "value" to settings.monthlyFee), SetOptions.merge())
            batch.set(firestore.collection("settings").document("setting_initialBankBalance"), mapOf("key" to "initialBankBalance", "value" to settings.initialBankBalance), SetOptions.merge())
            batch.commit().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateOfficeBearers(ob: OfficeBearers): Result<Unit> {
        val firestore = getFirestore() ?: return Result.failure(Exception("Firestore unavailable"))
        return try {
            val map = mapOf(
                "president" to mapOf("name" to ob.presidentName, "contact" to ob.presidentContact),
                "vicePresident" to mapOf("name" to ob.vicePresidentName, "contact" to ob.vicePresidentContact),
                "secretary" to mapOf("name" to ob.secretaryName, "contact" to ob.secretaryContact),
                "jointSecretary" to mapOf("name" to ob.jointSecretaryName, "contact" to ob.jointSecretaryContact),
                "treasurer" to mapOf("name" to ob.treasurerName, "contact" to ob.treasurerContact),
                "patron" to mapOf("name" to ob.patronName, "contact" to ob.patronContact),
                "committeeMembers" to ob.committeeMembers
            )
            firestore.collection("officeBearers").document("association_officials")
                .set(map, SetOptions.merge()).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun logActivity(action: String, details: String = "") {
        val firestore = getFirestore() ?: return
        val email = currentUser?.email ?: "admin"
        try {
            val timestamp = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).format(java.util.Date())
            val entry = mapOf(
                "adminEmail" to email,
                "action" to action,
                "details" to details,
                "timestamp" to timestamp
            )
            firestore.collection("activityLog").add(entry).await()
        } catch (e: Exception) {
            Log.e(TAG, "Failed logging admin activity", e)
        }
    }
}
