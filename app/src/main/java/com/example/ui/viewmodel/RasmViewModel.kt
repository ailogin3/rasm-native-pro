package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.*
import com.example.data.repository.FirebaseRepository
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.example.data.remote.AppVersionInfo
import com.example.data.remote.FirebaseAppVersionApi

/** Marks a zero-amount maintenance record created by bulk member import to record a pre-import "paid upto" balance. */
const val MIGRATED_RECEIPT = "MIGRATED"
const val MIGRATED_START_MONTH = "January 2000"

class RasmViewModel(application: Application) : AndroidViewModel(application) {

    val repository = FirebaseRepository(application)

    private val _currentUser = MutableStateFlow<FirebaseUser?>(repository.currentUser)
    val currentUser: StateFlow<FirebaseUser?> = _currentUser.asStateFlow()

    private val _isAdmin = MutableStateFlow(false)
    val isAdmin: StateFlow<Boolean> = _isAdmin.asStateFlow()

    private val _userMessage = MutableSharedFlow<String>()
    val userMessage: SharedFlow<String> = _userMessage.asSharedFlow()

    // Real-time collections
    val members: StateFlow<List<Member>> = repository.getMembersFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val maintenanceCollections: StateFlow<List<MaintenanceCollection>> = repository.getMaintenanceCollectionsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val events: StateFlow<List<Event>> = repository.getEventsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val eventCollections: StateFlow<List<EventCollection>> = repository.getEventCollectionsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val bankTransactions: StateFlow<List<BankTransaction>> = repository.getBankTransactionsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val meetings: StateFlow<List<Meeting>> = repository.getMeetingsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val donations: StateFlow<List<Donation>> = repository.getDonationsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val generalExpenses: StateFlow<List<GeneralExpense>> = repository.getGeneralExpensesFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activityLog: StateFlow<List<ActivityLogEntry>> = repository.getActivityLogFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val serviceListings: StateFlow<List<ServiceListing>> = repository.getServiceListingsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val settings: StateFlow<AssociationSettings> = repository.getSettingsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AssociationSettings())

    val officeBearers: StateFlow<OfficeBearers> = repository.getOfficeBearersFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), OfficeBearers())

    val admins: StateFlow<List<String>> = repository.getAdminsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val notices: StateFlow<List<Notice>> = repository.getNoticesFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val bankAccounts: StateFlow<List<BankAccount>> = repository.getBankAccountsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** This app has no separate treasurer role, so "can handle money entries" is just admin. */
    val canManageMoney: StateFlow<Boolean> get() = isAdmin

    // Currently logged-in member record (matched by phone number, last 10 digits,
    // so it doesn't matter whether +91 or spacing differs between the two sides)
    private fun normalizePhone(raw: String?): String = raw?.filter { it.isDigit() }?.takeLast(10) ?: ""

    val currentMember: StateFlow<Member?> = combine(currentUser, members) { user, list ->
        val phone = normalizePhone(user?.phoneNumber)
        if (phone.isBlank()) null
        else list.find { normalizePhone(it.contact) == phone }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Update banner (see FirebaseAppVersionApi) -- _appVersionInfo is null
    // until the first fetch resolves (or fails, silently). Dismissing is
    // per-session only (not persisted), so the banner reappears on the
    // next cold start until the member actually updates.
    private val _appVersionInfo = MutableStateFlow<AppVersionInfo?>(null)
    val appVersionInfo: StateFlow<AppVersionInfo?> = _appVersionInfo.asStateFlow()

    private val _updateBannerDismissed = MutableStateFlow(false)
    val showUpdateBanner: StateFlow<Boolean> = combine(
        _appVersionInfo, _updateBannerDismissed
    ) { info, dismissed ->
        !dismissed && info != null && info.latestVersionCode > com.example.BuildConfig.VERSION_CODE
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun dismissUpdateBanner() {
        _updateBannerDismissed.value = true
    }

    /**
     * Re-checks for a new version. Called from MainApp on every app
     * foreground (not just cold start via init{}), so a member who keeps
     * the app running in the background still sees a banner you publish
     * while their session is alive, without needing to force-close it.
     */
    fun refreshUpdateCheck() {
        viewModelScope.launch {
            _appVersionInfo.value = FirebaseAppVersionApi.fetchLatestVersion()
        }
    }

    init {
        checkAdminStatus()
        viewModelScope.launch {
            _appVersionInfo.value = FirebaseAppVersionApi.fetchLatestVersion()
        }
    }

    fun checkAdminStatus() {
        viewModelScope.launch {
            val phone = repository.currentUser?.phoneNumber
            val adminStatus = repository.checkIsAdmin(phone)
            _isAdmin.value = adminStatus
            _currentUser.value = repository.currentUser
        }
    }

    fun showMessage(message: String) {
        viewModelScope.launch {
            _userMessage.emit(message)
        }
    }

    // ----------------------------------------------------
    // AUTHENTICATION
    // ----------------------------------------------------

    fun login(email: String, pass: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            val res = repository.signIn(email, pass)
            res.onSuccess {
                _currentUser.value = it
                checkAdminStatus()
                onResult(true, null)
            }.onFailure {
                onResult(false, it.message)
            }
        }
    }

    fun signup(email: String, pass: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            val res = repository.signUp(email, pass)
            res.onSuccess {
                _currentUser.value = it
                checkAdminStatus()
                onResult(true, null)
            }.onFailure {
                onResult(false, it.message)
            }
        }
    }

    /** Checks the phone against member records before an OTP is ever sent. */
    fun checkPhoneIsMember(phone: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            onResult(repository.phoneBelongsToMember(phone))
        }
    }

    /**
     * Sends the OTP. onCodeSent gives the verificationId to hold onto for verifyOtp;
     * onAutoVerified fires instead, skipping manual code entry, when Play Services
     * retrieves the code automatically.
     */
    fun sendOtp(
        phoneNumber: String,
        activity: android.app.Activity,
        onCodeSent: (String) -> Unit,
        onAutoVerified: () -> Unit,
        onError: (String) -> Unit
    ) {
        val callbacks = object : com.google.firebase.auth.PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(credential: com.google.firebase.auth.PhoneAuthCredential) {
                viewModelScope.launch {
                    repository.signInWithPhoneCredential(credential)
                        .onSuccess {
                            _currentUser.value = it
                            checkAdminStatus()
                            onAutoVerified()
                        }
                        .onFailure { onError(it.message ?: "Verification failed") }
                }
            }

            override fun onVerificationFailed(e: com.google.firebase.FirebaseException) {
                onError(e.message ?: "Could not send OTP. Check the number and try again.")
            }

            override fun onCodeSent(
                verificationId: String,
                token: com.google.firebase.auth.PhoneAuthProvider.ForceResendingToken
            ) {
                onCodeSent(verificationId)
            }
        }
        repository.sendOtp(phoneNumber, activity, callbacks)
    }

    fun verifyOtp(verificationId: String, code: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            val credential = repository.buildOtpCredential(verificationId, code)
            val res = repository.signInWithPhoneCredential(credential)
            res.onSuccess {
                _currentUser.value = it
                checkAdminStatus()
                onResult(true, null)
            }.onFailure {
                onResult(false, it.message ?: "Invalid OTP. Please try again.")
            }
        }
    }

    fun logout() {
        repository.signOut()
        _currentUser.value = null
        _isAdmin.value = false
    }

    fun forgotPassword(email: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            val res = repository.sendPasswordReset(email)
            res.onSuccess {
                onResult(true, "Password reset email sent to $email. Check your inbox.")
            }.onFailure {
                onResult(false, it.message)
            }
        }
    }

    // ----------------------------------------------------
    // MEMBERS
    // ----------------------------------------------------

    fun addMember(member: Member, onSuccess: () -> Unit) {
        viewModelScope.launch {
            val res = repository.addMember(member)
            res.onSuccess {
                showMessage("Member added successfully!")
                onSuccess()
            }.onFailure {
                showMessage("Failed to add member: ${it.message}")
            }
        }
    }

    fun updateMember(member: Member, onSuccess: () -> Unit) {
        viewModelScope.launch {
            val res = repository.updateMember(member)
            res.onSuccess {
                showMessage("Member updated successfully!")
                onSuccess()
            }.onFailure {
                showMessage("Failed to update member: ${it.message}")
            }
        }
    }

    fun deleteMember(docId: String) {
        viewModelScope.launch {
            val res = repository.deleteMember(docId)
            res.onSuccess {
                showMessage("Member deleted")
            }.onFailure {
                showMessage("Failed to delete member: ${it.message}")
            }
        }
    }

    fun updateOwnFamily(familyMembers: List<FamilyMember>, onSuccess: () -> Unit) {
        val member = currentMember.value ?: return
        val updated = member.copy(family = familyMembers)
        updateMember(updated, onSuccess)
    }

    // ----------------------------------------------------
    // DUES & MAINTENANCE
    // ----------------------------------------------------

    fun recordMaintenance(
        member: Member,
        amount: Long,
        period: Int,
        startMonth: String,
        endMonth: String,
        date: String,
        context: Context,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            val receiptNo = generateReceiptNumber(System.currentTimeMillis())
            val coll = MaintenanceCollection(
                memberId = member.docId, // Native fix: link via Firestore doc ID!
                contact = member.contact,
                amount = amount,
                date = date,
                period = period,
                startMonth = startMonth,
                endMonth = endMonth,
                receiptNumber = receiptNo,
                timestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date())
            )
            val res = repository.recordMaintenancePayment(coll)
            res.onSuccess {
                showMessage("Payment recorded! Receipt: $receiptNo")
                shareReceiptViaWhatsApp(member, coll, context)
                onSuccess()
            }.onFailure {
                showMessage("Failed to record payment: ${it.message}")
            }
        }
    }

    fun deleteMaintenance(docId: String) {
        viewModelScope.launch {
            repository.deleteMaintenancePayment(docId).onSuccess {
                showMessage("Payment record deleted")
            }.onFailure {
                showMessage("Failed deleting payment: ${it.message}")
            }
        }
    }

    fun shareReceiptViaWhatsApp(member: Member, coll: MaintenanceCollection, context: Context) {
        val assocName = settings.value.associationName
        val msg = """
            *${assocName.uppercase()} RECEIPT*
            
            Receipt No: ${coll.receiptNumber}
            Date: ${coll.date}
            Member: ${member.name}
            Contact: ${member.contact}
            Period: ${coll.startMonth} to ${coll.endMonth} (${coll.period} mo)
            Amount Paid: ₹${coll.amount}
            Status: ✅ PAID
            
            Thank you for your payment!
        """.trimIndent()
        sendWhatsAppMessage(member.contact, msg, context)
    }

    fun sendWhatsAppMessage(contact: String, message: String, context: Context) {
        try {
            val cleanNumber = contact.replace(Regex("[^0-9]"), "")
            val internationalNumber = if (cleanNumber.length == 10) "91$cleanNumber" else cleanNumber
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("https://wa.me/$internationalNumber?text=${Uri.encode(message)}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            showMessage("Could not open WhatsApp: ${e.message}")
        }
    }

    // ----------------------------------------------------
    // EVENTS
    // ----------------------------------------------------

    fun createEvent(event: Event, onSuccess: () -> Unit) {
        viewModelScope.launch {
            repository.createEvent(event).onSuccess {
                showMessage("Event created successfully!")
                onSuccess()
            }.onFailure {
                showMessage("Error creating event: ${it.message}")
            }
        }
    }

    fun updateEvent(event: Event, onSuccess: () -> Unit) {
        viewModelScope.launch {
            repository.updateEvent(event).onSuccess {
                showMessage("Event updated successfully!")
                onSuccess()
            }.onFailure {
                showMessage("Error updating event: ${it.message}")
            }
        }
    }

    fun deleteEvent(docId: String) {
        viewModelScope.launch {
            repository.deleteEvent(docId).onSuccess {
                showMessage("Event deleted")
            }.onFailure {
                showMessage("Error deleting event: ${it.message}")
            }
        }
    }

    fun recordEventPayment(member: Member, event: Event, amount: Long, date: String, context: Context, onSuccess: () -> Unit) {
        viewModelScope.launch {
            val receiptNo = "EV/${SimpleDateFormat("yyyy/MM", Locale.US).format(Date())}/${(1000..9999).random()}"
            val ec = EventCollection(
                eventId = event.docId,
                memberId = member.docId, // Native fix: Firestore document ID!
                contact = member.contact,
                amount = amount,
                date = date,
                receiptNumber = receiptNo
            )
            repository.recordEventPayment(ec).onSuccess {
                showMessage("Event payment recorded! Receipt: $receiptNo")
                val msg = """
                    *${settings.value.associationName.uppercase()} EVENT RECEIPT*
                    
                    Receipt No: $receiptNo
                    Event: ${event.name}
                    Member: ${member.name}
                    Amount: ₹$amount
                    Date: $date
                    Status: ✅ PAID
                """.trimIndent()
                sendWhatsAppMessage(member.contact, msg, context)
                onSuccess()
            }.onFailure {
                showMessage("Error recording event payment: ${it.message}")
            }
        }
    }

    fun deleteEventPayment(docId: String) {
        viewModelScope.launch {
            repository.deleteEventPayment(docId).onSuccess {
                showMessage("Event payment record deleted")
            }.onFailure {
                showMessage("Error deleting: ${it.message}")
            }
        }
    }

    // ----------------------------------------------------
    // BANK & FINANCIALS
    // ----------------------------------------------------

    fun recordBankTransaction(tx: BankTransaction, onSuccess: () -> Unit) {
        viewModelScope.launch {
            repository.recordBankTransaction(tx).onSuccess {
                showMessage("Bank transaction recorded!")
                onSuccess()
            }.onFailure {
                showMessage("Error recording bank transaction: ${it.message}")
            }
        }
    }

    fun deleteBankTransaction(tx: BankTransaction) {
        viewModelScope.launch {
            repository.deleteBankTransaction(tx.docId, tx.transactionType, tx.amount).onSuccess {
                showMessage("Bank transaction deleted")
            }.onFailure {
                showMessage("Error deleting: ${it.message}")
            }
        }
    }

    fun updateBankTransaction(tx: BankTransaction, onSuccess: () -> Unit) {
        viewModelScope.launch {
            repository.updateBankTransaction(tx).onSuccess {
                showMessage("Bank transaction updated")
                onSuccess()
            }.onFailure {
                showMessage("Error updating bank transaction: ${it.message}")
            }
        }
    }

    fun recordBankTransfer(
        from: String,
        to: String,
        amount: Long,
        date: String,
        transactionId: String,
        remarks: String,
        onSuccess: () -> Unit
    ) {
        if (from == to) {
            showMessage("Choose two different accounts")
            return
        }
        viewModelScope.launch {
            repository.recordBankTransfer(from, to, amount, date, transactionId, remarks).onSuccess {
                showMessage("Transfer recorded")
                onSuccess()
            }.onFailure {
                showMessage("Failed to record transfer: ${it.message}")
            }
        }
    }

    /** Deletes a transfer: both of its entries go together so the accounts never get out of step. */
    fun deleteBankTransfer(legs: List<BankTransaction>) {
        if (legs.isEmpty()) return
        viewModelScope.launch {
            repository.deleteBankTransferLegs(legs.map { it.docId }, legs.first().amount).onSuccess {
                showMessage("Transfer deleted")
            }.onFailure {
                showMessage("Failed to delete transfer: ${it.message}")
            }
        }
    }

    fun recordDonation(donation: Donation, onSuccess: () -> Unit) {
        viewModelScope.launch {
            repository.recordDonation(donation).onSuccess {
                showMessage("Donation recorded! ❤️")
                onSuccess()
            }.onFailure {
                showMessage("Error recording donation: ${it.message}")
            }
        }
    }

    fun updateDonation(donation: Donation, onSuccess: () -> Unit) {
        viewModelScope.launch {
            repository.updateDonation(donation).onSuccess {
                showMessage("Donation updated")
                onSuccess()
            }.onFailure {
                showMessage("Error updating donation: ${it.message}")
            }
        }
    }

    fun deleteDonation(donation: Donation) {
        viewModelScope.launch {
            repository.deleteDonation(donation).onSuccess {
                showMessage("Donation record deleted")
            }.onFailure {
                showMessage("Error deleting donation: ${it.message}")
            }
        }
    }

    fun recordGeneralExpense(expense: GeneralExpense, onSuccess: () -> Unit) {
        viewModelScope.launch {
            repository.recordGeneralExpense(expense).onSuccess {
                showMessage("General expense recorded!")
                onSuccess()
            }.onFailure {
                showMessage("Error recording expense: ${it.message}")
            }
        }
    }

    fun deleteGeneralExpense(expense: GeneralExpense) {
        viewModelScope.launch {
            repository.deleteGeneralExpense(expense).onSuccess {
                showMessage("Expense record deleted")
            }.onFailure {
                showMessage("Error deleting expense: ${it.message}")
            }
        }
    }

    fun updateGeneralExpense(expense: GeneralExpense, onSuccess: () -> Unit) {
        viewModelScope.launch {
            repository.updateGeneralExpense(expense).onSuccess {
                showMessage("Expense updated")
                onSuccess()
            }.onFailure {
                showMessage("Error updating expense: ${it.message}")
            }
        }
    }

    // ----------------------------------------------------
    // NOTICES (admin only to post / edit / delete)
    // ----------------------------------------------------

    fun postNotice(title: String, body: String, pinned: Boolean, onSuccess: () -> Unit) {
        if (!_isAdmin.value) return
        viewModelScope.launch {
            val notice = Notice(
                title = title,
                body = body,
                pinned = pinned,
                postedBy = currentMember.value?.name?.takeIf { it.isNotBlank() } ?: (repository.currentUser?.phoneNumber ?: "Admin"),
                createdAt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date())
            )
            repository.addNotice(notice).onSuccess {
                showMessage("Notice posted")
                onSuccess()
            }.onFailure {
                showMessage("Failed to post notice: ${it.message}")
            }
        }
    }

    fun updateNotice(notice: Notice, onSuccess: () -> Unit) {
        if (!_isAdmin.value) return
        viewModelScope.launch {
            repository.updateNotice(notice).onSuccess {
                showMessage("Notice updated")
                onSuccess()
            }.onFailure {
                showMessage("Failed to update notice: ${it.message}")
            }
        }
    }

    fun deleteNotice(notice: Notice) {
        if (!_isAdmin.value) return
        viewModelScope.launch {
            repository.deleteNotice(notice).onSuccess {
                showMessage("Notice deleted")
            }.onFailure {
                showMessage("Failed to delete notice: ${it.message}")
            }
        }
    }

    // ----------------------------------------------------
    // BULK MEMBER IMPORT (admin only)
    // ----------------------------------------------------

    /**
     * Adds the given, already-checked rows one by one. A row with a "paid upto" month also gets the same
     * zero-amount "migrated" entry that Set Paid-Upto creates. Calls [onProgress] after every row.
     */
    fun importMembers(
        rows: List<com.example.util.MemberImportRow>,
        onProgress: (done: Int, total: Int) -> Unit,
        onDone: (added: Int, failures: List<String>) -> Unit
    ) {
        if (!_isAdmin.value) return
        viewModelScope.launch {
            var added = 0
            val failures = mutableListOf<String>()
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            val stamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date())
            rows.forEachIndexed { index, row ->
                val member = Member(
                    name = row.name,
                    contact = row.contact,
                    email = row.email,
                    memberType = row.memberType,
                    gender = row.gender,
                    joinDate = row.joinDate
                )
                val result = repository.addMember(member)
                if (result.isSuccess) {
                    added++
                    val upto = row.paidUpto
                    if (upto != null) {
                        val record = MaintenanceCollection(
                            memberId = result.getOrNull().orEmpty(),
                            contact = row.contact,
                            amount = 0L,
                            date = today,
                            period = 0,
                            startMonth = MIGRATED_START_MONTH,
                            endMonth = upto,
                            receiptNumber = MIGRATED_RECEIPT,
                            timestamp = stamp
                        )
                        val paid = repository.recordMaintenancePayment(record)
                        if (paid.isFailure) {
                            failures.add("Line ${row.line} (${row.name}): added, but paid-upto failed - ${paid.exceptionOrNull()?.message}")
                        }
                    }
                } else {
                    failures.add("Line ${row.line} (${row.name}): ${result.exceptionOrNull()?.message}")
                }
                onProgress(index + 1, rows.size)
            }
            repository.logActivity("Bulk imported members", "$added of ${rows.size} members added")
            onDone(added, failures)
        }
    }

    /** Saves an already-compressed photo (data URL) on a member. Used by the bulk photo import. */
    suspend fun setMemberPhoto(member: Member, photo: String): Boolean {
        if (!_isAdmin.value) return false
        return repository.updateMember(member.copy(photo = photo)).isSuccess
    }

    // ----------------------------------------------------
    // BANK ACCOUNTS
    // ----------------------------------------------------

    fun addBankAccount(name: String, openingBalance: Long, onSuccess: () -> Unit) {
        if (!_isAdmin.value) return
        val clean = name.trim()
        if (clean.isBlank()) {
            showMessage("Enter a name for the bank account")
            return
        }
        if (bankAccounts.value.any { it.name.equals(clean, ignoreCase = true) }) {
            showMessage("A bank account named \"$clean\" already exists")
            return
        }
        viewModelScope.launch {
            repository.addBankAccount(clean, openingBalance).onSuccess {
                showMessage("Bank account added")
                onSuccess()
            }.onFailure {
                showMessage("Failed to add bank account: ${it.message}")
            }
        }
    }

    fun updateBankAccountOpening(account: BankAccount, openingBalance: Long, onSuccess: () -> Unit) {
        if (!_isAdmin.value) return
        viewModelScope.launch {
            repository.updateBankAccountOpening(account, openingBalance).onSuccess {
                showMessage("Opening balance updated")
                onSuccess()
            }.onFailure {
                showMessage("Failed to update: ${it.message}")
            }
        }
    }

    fun deleteBankAccount(account: BankAccount) {
        if (!_isAdmin.value) return
        if (bankTransactions.value.any { it.bankAccount == account.name }) {
            showMessage("\"${account.name}\" already has bank entries, so it can't be deleted")
            return
        }
        viewModelScope.launch {
            repository.deleteBankAccount(account).onSuccess {
                showMessage("Bank account deleted")
            }.onFailure {
                showMessage("Failed to delete: ${it.message}")
            }
        }
    }

    // ----------------------------------------------------
    // MEETINGS
    // ----------------------------------------------------

    fun createMeeting(meeting: Meeting, onSuccess: () -> Unit) {
        viewModelScope.launch {
            repository.createMeeting(meeting).onSuccess {
                showMessage("Meeting created!")
                onSuccess()
            }.onFailure {
                showMessage("Error creating meeting: ${it.message}")
            }
        }
    }

    fun updateMeeting(meeting: Meeting, onSuccess: () -> Unit) {
        viewModelScope.launch {
            repository.updateMeeting(meeting).onSuccess {
                showMessage("Meeting updated!")
                onSuccess()
            }.onFailure {
                showMessage("Error updating meeting: ${it.message}")
            }
        }
    }

    // ----------------------------------------------------
    // BACKUP / RESTORE
    // ----------------------------------------------------

    private val _backupBusy = MutableStateFlow(false)
    val backupBusy: StateFlow<Boolean> = _backupBusy.asStateFlow()

    fun exportBackup(uri: Uri) {
        if (!_isAdmin.value) return
        viewModelScope.launch {
            _backupBusy.value = true
            val resolver = getApplication<Application>().contentResolver
            val res: Result<Int> = try {
                val out = resolver.openOutputStream(uri, "wt")
                    ?: throw IllegalStateException("Could not open the selected file")
                out.use { repository.exportBackup(it) }
            } catch (e: Exception) {
                Result.failure(e)
            }
            _backupBusy.value = false
            res.onSuccess { showMessage("Backup saved ($it records)") }
                .onFailure { showMessage("Backup failed: ${it.message}") }
        }
    }

    fun restoreBackup(uri: Uri) {
        if (!_isAdmin.value) return
        viewModelScope.launch {
            _backupBusy.value = true
            val resolver = getApplication<Application>().contentResolver
            val res: Result<Int> = try {
                val input = resolver.openInputStream(uri)
                    ?: throw IllegalStateException("Could not open the selected file")
                input.use { repository.importBackup(it) }
            } catch (e: Exception) {
                Result.failure(e)
            }
            _backupBusy.value = false
            res.onSuccess { showMessage("Restore finished ($it records)") }
                .onFailure { showMessage("Restore failed: ${it.message}") }
        }
    }

    fun meetingPhotos(meetingId: String): Flow<List<MeetingPhoto>> =
        repository.getMeetingPhotosFlow(meetingId)

    fun addMeetingPhoto(meetingId: String, dataUrl: String) {
        viewModelScope.launch {
            repository.addMeetingPhoto(meetingId, dataUrl).onSuccess {
                showMessage("Photo saved")
            }.onFailure {
                showMessage("Failed to save photo: ${it.message}")
            }
        }
    }

    fun deleteMeetingPhoto(meetingId: String, photoId: String) {
        viewModelScope.launch {
            repository.deleteMeetingPhoto(meetingId, photoId).onSuccess {
                showMessage("Photo removed")
            }.onFailure {
                showMessage("Failed to remove photo: ${it.message}")
            }
        }
    }

    fun deleteMeeting(docId: String) {
        viewModelScope.launch {
            repository.deleteMeeting(docId).onSuccess {
                showMessage("Meeting deleted")
            }.onFailure {
                showMessage("Error deleting meeting: ${it.message}")
            }
        }
    }

    // ----------------------------------------------------
    // COMMUNITY SERVICE LISTINGS
    // ----------------------------------------------------

    fun submitServiceListing(listing: ServiceListing, onSuccess: () -> Unit) {
        viewModelScope.launch {
            repository.submitServiceListing(listing).onSuccess {
                showMessage("Listing submitted for review!")
                onSuccess()
            }.onFailure {
                showMessage("Error submitting listing: ${it.message}")
            }
        }
    }

    fun updateServiceListingStatus(docId: String, businessName: String, approve: Boolean) {
        viewModelScope.launch {
            val adminEmail = currentUser.value?.email ?: "admin"
            repository.updateServiceListingStatus(docId, businessName, approve, adminEmail).onSuccess {
                showMessage(if (approve) "Listing approved!" else "Listing rejected")
            }.onFailure {
                showMessage("Error: ${it.message}")
            }
        }
    }

    fun deleteServiceListing(docId: String, businessName: String) {
        viewModelScope.launch {
            repository.deleteServiceListing(docId, businessName).onSuccess {
                showMessage("Listing deleted")
            }.onFailure {
                showMessage("Error: ${it.message}")
            }
        }
    }

    // ----------------------------------------------------
    // SETTINGS & ADMINS
    // ----------------------------------------------------

    fun updateSettings(newSettings: AssociationSettings, onSuccess: () -> Unit) {
        viewModelScope.launch {
            repository.updateAssociationSettings(newSettings).onSuccess {
                showMessage("Settings updated successfully!")
                onSuccess()
            }.onFailure {
                showMessage("Failed to update settings: ${it.message}")
            }
        }
    }

    fun updateOfficeBearers(ob: OfficeBearers, onSuccess: () -> Unit) {
        viewModelScope.launch {
            repository.updateOfficeBearers(ob).onSuccess {
                showMessage("Officials updated successfully!")
                onSuccess()
            }.onFailure {
                showMessage("Failed to update officials: ${it.message}")
            }
        }
    }

    fun addAdmin(phone: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            repository.addAdmin(phone).onSuccess {
                showMessage("$phone is now an admin!")
                onSuccess()
            }.onFailure {
                showMessage("Failed to add admin: ${it.message}")
            }
        }
    }

    fun removeAdmin(phone: String) {
        viewModelScope.launch {
            repository.removeAdmin(phone).onSuccess {
                showMessage("Admin status removed for $phone")
            }.onFailure {
                showMessage("Failed to remove admin: ${it.message}")
            }
        }
    }

    fun saveFirebaseConfig(apiKey: String, appId: String, projectId: String, bucket: String?, senderId: String?, onSuccess: () -> Unit) {
        val ok = repository.saveFirebaseConfig(apiKey, appId, projectId, bucket, senderId)
        if (ok) {
            showMessage("Firebase config saved and connected!")
            checkAdminStatus()
            onSuccess()
        } else {
            showMessage("Failed to apply Firebase configuration")
        }
    }

    private fun generateReceiptNumber(timestamp: Long): String {
        val now = Date(timestamp)
        val y = SimpleDateFormat("yyyy", Locale.US).format(now)
        val m = SimpleDateFormat("MM", Locale.US).format(now)
        val rand = (1000..9999).random()
        return "RA/$y/$m/$rand"
    }
}
