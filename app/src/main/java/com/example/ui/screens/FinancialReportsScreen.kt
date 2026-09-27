package com.example.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.*
import com.example.ui.components.ConfirmDeleteDialog
import com.example.ui.components.EditDeleteActions
import com.example.util.CsvUtil
import com.example.ui.viewmodel.RasmViewModel
import com.example.util.PdfBankRow
import com.example.util.PdfLedgerRow
import com.example.util.PdfReportGenerator
import com.example.util.PdfSummaryRow
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

// ---------------------------------------------------------------------------------------------
// Date helpers. All dates in this app are stored as "yyyy-MM-dd" strings, which sort correctly
// as plain strings, so period filtering is a simple string comparison.
// ---------------------------------------------------------------------------------------------

// ---------------------------------------------------------------------------------------------
// Bank-transaction effects. Mirrors the table in BankTxType's doc comment:
//   Deposit         bank +, cash -   Withdrawal      bank -, cash +
//   Interest        bank +           Adjustment In   bank +
//   Adjustment Out  bank -           Transfer In     bank +
//   Transfer Out    bank -
// Cash is only ever touched by a Deposit or a Withdrawal; everything else is bank-only.
// ---------------------------------------------------------------------------------------------

private fun BankTransaction.bankDelta(): Long = when (BankTxType.canonical(transactionType)) {
    BankTxType.WITHDRAWAL, BankTxType.ADJUSTMENT_OUT, BankTxType.TRANSFER_OUT -> -amount
    else -> amount
}

private fun BankTransaction.cashDelta(): Long = when (BankTxType.canonical(transactionType)) {
    BankTxType.DEPOSIT -> -amount
    BankTxType.WITHDRAWAL -> amount
    else -> 0L
}

private val DATE_RE = Regex("""\d{4}-\d{2}-\d{2}""")

private fun utcFormat() = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }

private fun dateToMillis(s: String): Long? =
    if (DATE_RE.matches(s)) runCatching { utcFormat().parse(s)?.time }.getOrNull() else null

private fun prettyDate(s: String): String =
    if (DATE_RE.matches(s)) "${s.substring(8, 10)}/${s.substring(5, 7)}/${s.substring(0, 4)}" else s

private fun inr(v: Long): String = PdfReportGenerator.formatAmount(v).replace("Rs. ", "\u20B9")

private class Slice<T>(val before: List<T>, val inPeriod: List<T>, val undated: Int)

/**
 * Splits [items] into "before the period" (used for opening balances) and "inside the period".
 * Items after the period end are dropped. Items without a valid date only count when no range is set.
 */
private fun <T> sliceByDate(items: List<T>, from: String, to: String, dateOf: (T) -> String): Slice<T> {
    val before = mutableListOf<T>()
    val inside = mutableListOf<T>()
    var undated = 0
    val unbounded = from.isBlank() && to.isBlank()
    for (item in items) {
        val d = dateOf(item).trim().take(10).takeIf { DATE_RE.matches(it) }
        when {
            d == null -> if (unbounded) inside.add(item) else undated++
            from.isNotBlank() && d < from -> before.add(item)
            to.isNotBlank() && d > to -> Unit
            else -> inside.add(item)
        }
    }
    return Slice(before, inside, undated)
}

private fun BankTransaction.isType(type: String) = BankTxType.canonical(transactionType) == type

private data class DatePreset(val label: String, val from: String, val to: String)

private fun buildPresets(): List<DatePreset> {
    val cal = Calendar.getInstance()
    val year = cal.get(Calendar.YEAR)
    val month = cal.get(Calendar.MONTH) // 0-based
    val lastDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val fyStart = if (month >= Calendar.APRIL) year else year - 1 // Indian financial year: Apr-Mar
    fun fy(startYear: Int) = Pair(
        String.format(Locale.US, "%04d-04-01", startYear),
        String.format(Locale.US, "%04d-03-31", startYear + 1)
    )
    val thisFy = fy(fyStart)
    val lastFy = fy(fyStart - 1)
    return listOf(
        DatePreset("All time", "", ""),
        DatePreset(
            "This month",
            String.format(Locale.US, "%04d-%02d-01", year, month + 1),
            String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, lastDay)
        ),
        DatePreset("This FY", thisFy.first, thisFy.second),
        DatePreset("Last FY", lastFy.first, lastFy.second)
    )
}

/** Read-only field that opens a Material date picker. Value is a "yyyy-MM-dd" string ("" = not set). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    onClear: (() -> Unit)? = null
) {
    var show by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            placeholder = { Text("Any") },
            trailingIcon = { Icon(Icons.Default.DateRange, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        // Transparent overlay: a read-only text field swallows taps, so catch them here.
        Box(modifier = Modifier.matchParentSize().clickable { show = true })
    }

    if (show) {
        val state = rememberDatePickerState(initialSelectedDateMillis = dateToMillis(value))
        DatePickerDialog(
            onDismissRequest = { show = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onValueChange(utcFormat().format(Date(it))) }
                    show = false
                }) { Text("OK") }
            },
            dismissButton = {
                Row {
                    if (onClear != null) {
                        TextButton(onClick = { onClear(); show = false }) { Text("Clear") }
                    }
                    TextButton(onClick = { show = false }) { Text("Cancel") }
                }
            }
        ) {
            DatePicker(state = state)
        }
    }
}

/** Balance of one bank account for the chosen period. */
private class AccountFigure(val name: String, val opening: Long, val closing: Long)

private class FinanceFigures(
    val openingBank: Long,
    val openingCash: Long,
    val maintenance: Long,
    val eventCollections: Long,
    val donations: Long,
    val interest: Long,
    val eventExpenses: Long,
    val generalExpenses: Long,
    val deposits: Long,
    val withdrawals: Long,
    val adjustmentsIn: Long,
    val adjustmentsOut: Long,
    val closingBank: Long,
    val closingCash: Long
) {
    /** Income for the period: collections + donations + bank interest. (Only interest never passes through cash.) */
    val inflow get() = maintenance + eventCollections + donations + interest
    val outflow get() = eventExpenses + generalExpenses
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinancialReportsScreen(viewModel: RasmViewModel) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsState()
    val members by viewModel.members.collectAsState()
    val maintenanceCollections by viewModel.maintenanceCollections.collectAsState()
    val eventCollections by viewModel.eventCollections.collectAsState()
    val events by viewModel.events.collectAsState()
    val bankTransactions by viewModel.bankTransactions.collectAsState()
    val donations by viewModel.donations.collectAsState()
    val generalExpenses by viewModel.generalExpenses.collectAsState()
    val canManageMoney by viewModel.canManageMoney.collectAsState()
    val bankAccounts by viewModel.bankAccounts.collectAsState()

    // Report period ("" = open ended). Default is all time, same as before.
    var fromDate by rememberSaveable { mutableStateOf("") }
    var toDate by rememberSaveable { mutableStateOf("") }
    val presets = remember { buildPresets() }
    val rangeInvalid = fromDate.isNotBlank() && toDate.isNotBlank() && fromDate > toDate

    // Event collections/expenses fall back to the event's own date when they have none.
    val eventCollectionsDated = eventCollections.map { c ->
        if (c.date.isBlank()) c.copy(date = events.firstOrNull { it.docId == c.eventId }?.date.orEmpty()) else c
    }
    val eventExpensesDated = events.flatMap { ev ->
        ev.expenses.map { exp ->
            exp.copy(
                description = if (ev.name.isBlank()) exp.description else "${ev.name}: ${exp.description}",
                date = exp.date.ifBlank { ev.date }
            )
        }
    }

    val maintS = sliceByDate(maintenanceCollections, fromDate, toDate) { it.date }
    val evColS = sliceByDate(eventCollectionsDated, fromDate, toDate) { it.date }
    val donS = sliceByDate(donations, fromDate, toDate) { it.date }
    val evExpS = sliceByDate(eventExpensesDated, fromDate, toDate) { it.date }
    val genExpS = sliceByDate(generalExpenses, fromDate, toDate) { it.date }
    val bankS = sliceByDate(bankTransactions, fromDate, toDate) { it.date }

    val undatedCount = maintS.undated + evColS.undated + donS.undated + evExpS.undated + genExpS.undated + bankS.undated

    // Balances carried in from before the period start.
    // Opening cash / bank = the opening figure from Settings + everything recorded before the period.
    val cashInflowBefore = maintS.before.sumOf { it.amount } + evColS.before.sumOf { it.amount } + donS.before.sumOf { it.amount }
    val outflowBefore = evExpS.before.sumOf { it.amount } + genExpS.before.sumOf { it.amount }
    // With bank accounts set up, the opening bank balance is the sum of the accounts' opening balances;
    // with none, the single Opening Bank Balance from Settings is used, exactly as before.
    val openingBankBase = if (bankAccounts.isEmpty()) settings.initialBankBalance else bankAccounts.sumOf { it.openingBalance }
    val openingBank = openingBankBase + bankS.before.sumOf { it.bankDelta() }
    val openingCash = settings.initialCashBalance + (cashInflowBefore - outflowBefore) + bankS.before.sumOf { it.cashDelta() }

    // Movement inside the period
    val deposits = bankS.inPeriod.filter { it.isType(BankTxType.DEPOSIT) }.sumOf { it.amount }
    val withdrawals = bankS.inPeriod.filter { it.isType(BankTxType.WITHDRAWAL) }.sumOf { it.amount }
    val interest = bankS.inPeriod.filter { it.isType(BankTxType.INTEREST) }.sumOf { it.amount }
    val adjustmentsIn = bankS.inPeriod.filter { it.isType(BankTxType.ADJUSTMENT_IN) }.sumOf { it.amount }
    val adjustmentsOut = bankS.inPeriod.filter { it.isType(BankTxType.ADJUSTMENT_OUT) }.sumOf { it.amount }
    val maintenance = maintS.inPeriod.sumOf { it.amount }
    val eventCols = evColS.inPeriod.sumOf { it.amount }
    val donationTotal = donS.inPeriod.sumOf { it.amount }
    val eventExp = evExpS.inPeriod.sumOf { it.amount }
    val generalExp = genExpS.inPeriod.sumOf { it.amount }
    val cashInflow = maintenance + eventCols + donationTotal
    val outflow = eventExp + generalExp

    // Balance of each bank account for the period (entries without a known account are grouped as "Unassigned").
    val accountFigures: List<AccountFigure> = if (bankAccounts.isEmpty()) emptyList() else {
        val named = bankAccounts.map { a ->
            val opening = a.openingBalance + bankS.before.filter { it.bankAccount == a.name }.sumOf { it.bankDelta() }
            val closing = opening + bankS.inPeriod.filter { it.bankAccount == a.name }.sumOf { it.bankDelta() }
            AccountFigure(a.name, opening, closing)
        }
        val known = bankAccounts.map { it.name }.toSet()
        val unassignedBefore = bankS.before.filter { it.bankAccount !in known }.sumOf { it.bankDelta() }
        val unassignedIn = bankS.inPeriod.filter { it.bankAccount !in known }.sumOf { it.bankDelta() }
        if (unassignedBefore != 0L || unassignedIn != 0L) {
            named + AccountFigure("Unassigned", unassignedBefore, unassignedBefore + unassignedIn)
        } else {
            named
        }
    }

    val figures = FinanceFigures(
        openingBank = openingBank,
        openingCash = openingCash,
        maintenance = maintenance,
        eventCollections = eventCols,
        donations = donationTotal,
        interest = interest,
        eventExpenses = eventExp,
        generalExpenses = generalExp,
        deposits = deposits,
        withdrawals = withdrawals,
        adjustmentsIn = adjustmentsIn,
        adjustmentsOut = adjustmentsOut,
        closingBank = openingBank + bankS.inPeriod.sumOf { it.bankDelta() },
        closingCash = openingCash + (cashInflow - outflow) + bankS.inPeriod.sumOf { it.cashDelta() }
    )

    val periodLabel = when {
        fromDate.isBlank() && toDate.isBlank() -> "All time"
        toDate.isBlank() -> "From ${prettyDate(fromDate)}"
        fromDate.isBlank() -> "Up to ${prettyDate(toDate)}"
        else -> "${prettyDate(fromDate)} to ${prettyDate(toDate)}"
    }

    var pendingPdf by remember { mutableStateOf<ByteArray?>(null) }
    val pdfLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri: Uri? ->
        val bytes = pendingPdf
        if (uri != null && bytes != null) {
            val result = runCatching {
                context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                    ?: error("Could not open the file")
            }
            Toast.makeText(
                context,
                if (result.isSuccess) "PDF saved" else "Couldn't save the PDF: ${result.exceptionOrNull()?.message}",
                Toast.LENGTH_LONG
            ).show()
        }
        pendingPdf = null
    }

    // Ledger as CSV (opens in Excel): one row per entry in the chosen period.
    var pendingCsv by remember { mutableStateOf<String?>(null) }
    val csvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? ->
        val text = pendingCsv
        if (uri != null && text != null) {
            val result = CsvUtil.writeToUri(context, uri, text)
            Toast.makeText(
                context,
                if (result.isSuccess) "CSV saved" else "Couldn't save the CSV: ${result.exceptionOrNull()?.message}",
                Toast.LENGTH_LONG
            ).show()
        }
        pendingCsv = null
    }

    fun exportCsv() {
        fun memberName(id: String) = members.firstOrNull { it.docId == id }?.name.orEmpty()
        val ledger = mutableListOf<Pair<String, List<Any?>>>()
        maintS.inPeriod.filter { it.amount != 0L }.forEach {
            ledger.add(it.date to listOf<Any?>(it.date, "Maintenance", memberName(it.memberId), it.receiptNumber, it.amount, "", ""))
        }
        evColS.inPeriod.forEach {
            val evName = events.firstOrNull { e -> e.docId == it.eventId }?.name.orEmpty()
            ledger.add(it.date to listOf<Any?>(it.date, "Event collection", listOf(evName, memberName(it.memberId)).filter { s -> s.isNotBlank() }.joinToString(" - "), it.receiptNumber, it.amount, "", ""))
        }
        donS.inPeriod.forEach {
            ledger.add(it.date to listOf<Any?>(it.date, "Donation", listOf(it.donorName, it.purpose).filter { s -> s.isNotBlank() }.joinToString(" - "), "", it.amount, "", ""))
        }
        evExpS.inPeriod.forEach {
            ledger.add(it.date to listOf<Any?>(it.date, "Event expense", it.description, "", "", it.amount, ""))
        }
        genExpS.inPeriod.forEach {
            ledger.add(it.date to listOf<Any?>(it.date, "General expense", listOf(it.category, it.description).filter { s -> s.isNotBlank() }.joinToString(" - "), "", "", it.amount, ""))
        }
        bankS.inPeriod.forEach {
            ledger.add(it.date to listOf<Any?>(it.date, "Bank " + BankTxType.canonical(it.transactionType), it.remarks, it.transactionId, "", "", it.bankDelta(), it.bankAccount))
        }
        val header = listOf("Date", "Type", "Particulars", "Reference", "Income (Rs)", "Expense (Rs)", "Bank change (Rs)", "Account")
        pendingCsv = CsvUtil.build(header, ledger.sortedBy { it.first }.map { row -> if (row.second.size < 8) row.second + "" else row.second })
        csvLauncher.launch("financial_ledger_${fromDate.ifBlank { "start" }}_to_${toDate.ifBlank { "today" }}.csv")
    }

    fun exportPdf() {
        fun memberName(id: String) = members.firstOrNull { it.docId == id }?.name.orEmpty()
        fun withName(prefix: String, id: String) =
            memberName(id).let { if (it.isBlank()) prefix else "$prefix - $it" }

        val ledger = buildList<PdfLedgerRow> {
            maintS.inPeriod.filter { it.amount != 0L }.forEach {
                add(PdfLedgerRow(it.date, withName("Maintenance", it.memberId) + if (it.receiptNumber.isBlank()) "" else " (${it.receiptNumber})", it.amount, 0L))
            }
            evColS.inPeriod.forEach {
                val evName = events.firstOrNull { e -> e.docId == it.eventId }?.name.orEmpty()
                add(PdfLedgerRow(it.date, withName(if (evName.isBlank()) "Event collection" else "Event collection: $evName", it.memberId), it.amount, 0L))
            }
            donS.inPeriod.forEach {
                add(PdfLedgerRow(it.date, "Donation - ${it.donorName}" + if (it.purpose.isBlank()) "" else " (${it.purpose})", it.amount, 0L))
            }
            evExpS.inPeriod.forEach {
                add(PdfLedgerRow(it.date, "Event expense - ${it.description}", 0L, it.amount))
            }
            genExpS.inPeriod.forEach {
                add(PdfLedgerRow(it.date, (if (it.category.isBlank()) "" else "${it.category} - ") + it.description, 0L, it.amount))
            }
        }.sortedBy { it.date }

        val bank = bankS.inPeriod.sortedBy { it.date }.map {
            PdfBankRow(
                it.date,
                when (val label = BankTxType.canonical(it.transactionType)) {
                    BankTxType.ADJUSTMENT_IN -> "Adj. In"
                    BankTxType.ADJUSTMENT_OUT -> "Adj. Out"
                    else -> label
                },
                listOf(it.bankAccount, it.transactionId.ifBlank { it.remarks }).filter { s -> s.isNotBlank() }.joinToString(" - "),
                it.amount
            )
        }

        val summary = listOf(
            PdfSummaryRow("Opening Bank Balance", PdfReportGenerator.formatAmount(figures.openingBank)),
            PdfSummaryRow("Opening Cash in Hand", PdfReportGenerator.formatAmount(figures.openingCash)),
            PdfSummaryRow("Maintenance Dues Collected", PdfReportGenerator.formatAmount(figures.maintenance)),
            PdfSummaryRow("Event Collections", PdfReportGenerator.formatAmount(figures.eventCollections)),
            PdfSummaryRow("Donations", PdfReportGenerator.formatAmount(figures.donations)),
            PdfSummaryRow("Bank Interest", PdfReportGenerator.formatAmount(figures.interest)),
            PdfSummaryRow("Total Income", PdfReportGenerator.formatAmount(figures.inflow), bold = true),
            PdfSummaryRow("Event Expenses", PdfReportGenerator.formatAmount(figures.eventExpenses)),
            PdfSummaryRow("General Expenses", PdfReportGenerator.formatAmount(figures.generalExpenses)),
            PdfSummaryRow("Total Expenses", PdfReportGenerator.formatAmount(figures.outflow), bold = true),
            PdfSummaryRow("Net Surplus / (Deficit)", PdfReportGenerator.formatAmount(figures.inflow - figures.outflow), bold = true),
            PdfSummaryRow("Deposited to Bank", PdfReportGenerator.formatAmount(figures.deposits)),
            PdfSummaryRow("Withdrawn from Bank", PdfReportGenerator.formatAmount(figures.withdrawals)),
            PdfSummaryRow("Bank Adjustments (net)", PdfReportGenerator.formatAmount(figures.adjustmentsIn - figures.adjustmentsOut)),
            PdfSummaryRow("Closing Bank Balance", PdfReportGenerator.formatAmount(figures.closingBank), bold = true),
            PdfSummaryRow("Closing Cash in Hand", PdfReportGenerator.formatAmount(figures.closingCash), bold = true)
        ) + accountFigures.map { PdfSummaryRow("Bank - ${it.name}", PdfReportGenerator.formatAmount(it.closing)) }

        val details = listOf(settings.associationRegNo.takeIf { it.isNotBlank() }?.let { "Reg. No: $it" }, settings.associationLocation.takeIf { it.isNotBlank() })
            .filterNotNull().joinToString("  |  ")

        pendingPdf = PdfReportGenerator.generate(
            orgName = settings.associationName,
            orgDetails = details,
            periodLabel = periodLabel,
            summary = summary,
            ledger = ledger,
            bank = bank
        )
        val safeFrom = fromDate.ifBlank { "start" }
        val safeTo = toDate.ifBlank { "today" }
        pdfLauncher.launch("financial_statement_${safeFrom}_to_${safeTo}.pdf")
    }

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Overview", "Bank Log", "Donations", "Expenses")

    var showAddBankTx by remember { mutableStateOf(false) }
    var showAddDonation by remember { mutableStateOf(false) }
    var showAddExpense by remember { mutableStateOf(false) }

    // Admin corrections: which entry is being edited / is waiting for delete confirmation
    var editingBank by remember { mutableStateOf<BankTransaction?>(null) }
    var editingDonation by remember { mutableStateOf<Donation?>(null) }
    var editingExpense by remember { mutableStateOf<GeneralExpense?>(null) }
    var bankToDelete by remember { mutableStateOf<BankTransaction?>(null) }
    var donationToDelete by remember { mutableStateOf<Donation?>(null) }
    var expenseToDelete by remember { mutableStateOf<GeneralExpense?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Period picker
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Report period", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Row {
                        TextButton(
                            onClick = { exportCsv() },
                            enabled = !rangeInvalid,
                            modifier = Modifier.testTag("export_csv_button")
                        ) {
                            Icon(Icons.Default.TableChart, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("CSV")
                        }
                        TextButton(
                            onClick = { exportPdf() },
                            enabled = !rangeInvalid,
                            modifier = Modifier.testTag("export_pdf_button")
                        ) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("PDF")
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DatePickerField(
                        label = "From",
                        value = fromDate,
                        onValueChange = { fromDate = it },
                        onClear = { fromDate = "" },
                        modifier = Modifier.weight(1f)
                    )
                    DatePickerField(
                        label = "To",
                        value = toDate,
                        onValueChange = { toDate = it },
                        onClear = { toDate = "" },
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    presets.forEach { p ->
                        FilterChip(
                            selected = fromDate == p.from && toDate == p.to,
                            onClick = { fromDate = p.from; toDate = p.to },
                            label = { Text(p.label, fontSize = 12.sp) }
                        )
                    }
                }
                if (rangeInvalid) {
                    Text("'From' date must be on or before the 'To' date.", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
            }
        }

        // High-level Balance Card (closing balances at the end of the selected period)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(Color(0xFF1565C0), Color(0xFF0D47A1))
                        )
                    )
                    .padding(20.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                if (toDate.isBlank()) "Current Bank Balance" else "Bank Balance on ${prettyDate(toDate)}",
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 12.sp
                            )
                            Text(inr(figures.closingBank), color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                        }
                        Surface(
                            color = Color.White.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                                Text("Cash in Hand", color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp)
                                Text(inr(figures.closingCash), color = Color(0xFFFFD54F), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Income: ${inr(figures.inflow)}", color = Color(0xFFB2DFDB), fontSize = 12.sp)
                        Text("Outflow: ${inr(figures.outflow)}", color = Color(0xFFFFCDD2), fontSize = 12.sp)
                    }
                    Text(periodLabel, color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
                }
            }
        }

        PrimaryTabRow(selectedTabIndex = selectedTab) {
            tabs.forEachIndexed { idx, title ->
                Tab(
                    selected = selectedTab == idx,
                    onClick = { selectedTab = idx },
                    text = { Text(title, fontSize = 12.sp) }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (rangeInvalid) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Fix the date range to see the report.", color = MaterialTheme.colorScheme.outline)
            }
        } else when (selectedTab) {
            0 -> OverviewTab(figures, accountFigures, undatedCount)
            1 -> BankLogTab(bankS.inPeriod, bankAccounts, canManageMoney, onAddClick = { showAddBankTx = true }, onEdit = { editingBank = it }, onDelete = { bankToDelete = it })
            2 -> DonationsTab(donS.inPeriod, canManageMoney, onAddClick = { showAddDonation = true }, onEdit = { editingDonation = it }, onDelete = { donationToDelete = it })
            3 -> ExpensesTab(genExpS.inPeriod, canManageMoney, onAddClick = { showAddExpense = true }, onEdit = { editingExpense = it }, onDelete = { expenseToDelete = it })
            else -> Unit
        }
    }

    if (showAddBankTx || editingBank != null) {
        AddBankTxDialog(
            initial = editingBank,
            accounts = bankAccounts,
            onTransfer = { from, to, amount, date, txnId, remarks ->
                viewModel.recordBankTransfer(from, to, amount, date, txnId, remarks) {
                    showAddBankTx = false
                }
            },
            onDismiss = { showAddBankTx = false; editingBank = null },
            onSave = { tx ->
                val editing = editingBank
                if (editing == null) {
                    viewModel.recordBankTransaction(tx) {
                        showAddBankTx = false
                    }
                } else {
                    viewModel.updateBankTransaction(tx.copy(docId = editing.docId)) {
                        editingBank = null
                    }
                }
            }
        )
    }

    if (showAddDonation || editingDonation != null) {
        AddDonationDialog(
            initial = editingDonation,
            onDismiss = { showAddDonation = false; editingDonation = null },
            onSave = { d ->
                val editing = editingDonation
                if (editing == null) {
                    viewModel.recordDonation(d) {
                        showAddDonation = false
                    }
                } else {
                    viewModel.updateDonation(d.copy(docId = editing.docId)) {
                        editingDonation = null
                    }
                }
            }
        )
    }

    if (showAddExpense || editingExpense != null) {
        AddExpenseDialog(
            initial = editingExpense,
            events = events,
            onDismiss = { showAddExpense = false; editingExpense = null },
            onSave = { exp ->
                val editing = editingExpense
                if (editing == null) {
                    viewModel.recordGeneralExpense(exp) {
                        showAddExpense = false
                    }
                } else {
                    viewModel.updateGeneralExpense(exp.copy(docId = editing.docId, eventId = editing.eventId, createdAt = editing.createdAt)) {
                        editingExpense = null
                    }
                }
            }
        )
    }

    bankToDelete?.let { tx ->
        val isTransfer = BankTxType.isTransfer(tx.transactionType)
        val legs = if (isTransfer && tx.transferId.isNotBlank()) bankTransactions.filter { it.transferId == tx.transferId } else listOf(tx)
        ConfirmDeleteDialog(
            title = if (isTransfer) "Delete transfer?" else "Delete bank entry?",
            message = if (isTransfer) {
                "Both sides of this \u20B9${tx.amount} transfer (out of one account and into the other) will be removed. This cannot be undone."
            } else {
                "${tx.transactionType} of \u20B9${tx.amount} on ${prettyDate(tx.date)} will be removed and the bank balance recalculated. This cannot be undone."
            },
            onConfirm = {
                if (isTransfer) viewModel.deleteBankTransfer(legs) else viewModel.deleteBankTransaction(tx)
                bankToDelete = null
            },
            onDismiss = { bankToDelete = null }
        )
    }

    donationToDelete?.let { d ->
        ConfirmDeleteDialog(
            title = "Delete donation?",
            message = "Donation of \u20B9${d.amount} from ${d.donorName} on ${prettyDate(d.date)} will be removed from the reports. This cannot be undone.",
            onConfirm = { viewModel.deleteDonation(d); donationToDelete = null },
            onDismiss = { donationToDelete = null }
        )
    }

    expenseToDelete?.let { exp ->
        ConfirmDeleteDialog(
            title = "Delete expense?",
            message = "\"${exp.description}\" (\u20B9${exp.amount}) on ${prettyDate(exp.date)} will be removed from the reports. This cannot be undone.",
            onConfirm = { viewModel.deleteGeneralExpense(exp); expenseToDelete = null },
            onDismiss = { expenseToDelete = null }
        )
    }
}

@Composable
private fun OverviewTab(f: FinanceFigures, accounts: List<AccountFigure>, undatedCount: Int) {
    val scrollState = rememberScrollState()
    val net = f.inflow - f.outflow
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Opening Balances", fontWeight = FontWeight.Bold)
        FinancialRow("Opening Bank Balance", inr(f.openingBank), Color(0xFF1565C0))
        FinancialRow("Opening Cash in Hand", inr(f.openingCash), Color(0xFF705D00))

        Spacer(modifier = Modifier.height(6.dp))
        Text("Income Breakdown", fontWeight = FontWeight.Bold)
        FinancialRow("Maintenance Dues Collected", inr(f.maintenance), Color(0xFF2E7D32))
        FinancialRow("Events Collections", inr(f.eventCollections), Color(0xFF2E7D32))
        FinancialRow("Community Donations", inr(f.donations), Color(0xFF2E7D32))
        FinancialRow("Bank Interest", inr(f.interest), Color(0xFF2E7D32))
        FinancialRow("Gross Inflow", inr(f.inflow), Color(0xFF006A60), isTotal = true)

        Spacer(modifier = Modifier.height(6.dp))
        Text("Expenditure Breakdown", fontWeight = FontWeight.Bold)
        FinancialRow("Event Specific Expenses", inr(f.eventExpenses), Color(0xFFC62828))
        FinancialRow("General Association Expenses", inr(f.generalExpenses), Color(0xFFC62828))
        FinancialRow("Gross Outflow", inr(f.outflow), Color(0xFFC62828), isTotal = true)

        Spacer(modifier = Modifier.height(6.dp))
        Text("Net Position for Period", fontWeight = FontWeight.Bold)
        FinancialRow("Surplus / (Deficit)", inr(net), if (net >= 0) Color(0xFF1565C0) else Color(0xFFC62828), isTotal = true)
        FinancialRow("Deposited to Bank", inr(f.deposits), Color(0xFF2E7D32))
        FinancialRow("Withdrawn from Bank", inr(f.withdrawals), Color(0xFFC62828))
        if (f.adjustmentsIn != 0L || f.adjustmentsOut != 0L) {
            FinancialRow("Bank Adjustments In", inr(f.adjustmentsIn), Color(0xFF2E7D32))
            FinancialRow("Bank Adjustments Out", inr(f.adjustmentsOut), Color(0xFFC62828))
        }

        Spacer(modifier = Modifier.height(6.dp))
        Text("Closing Balances", fontWeight = FontWeight.Bold)
        FinancialRow("Closing Bank Balance", inr(f.closingBank), Color(0xFF1565C0), isTotal = true)
        FinancialRow("Closing Cash in Hand", inr(f.closingCash), Color(0xFF705D00), isTotal = true)

        if (accounts.isNotEmpty()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text("Bank Accounts (closing balance)", fontWeight = FontWeight.Bold)
            accounts.forEach { a ->
                FinancialRow(a.name, inr(a.closing), Color(0xFF1565C0))
            }
        }

        if (undatedCount > 0) {
            Text(
                "$undatedCount entr${if (undatedCount == 1) "y has" else "ies have"} no valid date and " +
                    "${if (undatedCount == 1) "is" else "are"} left out of this period. Choose 'All time' to include everything.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.outline
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun FinancialRow(label: String, amount: String, color: Color, isTotal: Boolean = false) {
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = if (isTotal) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, fontWeight = if (isTotal) FontWeight.Bold else FontWeight.Normal, fontSize = 13.sp)
            Text(amount, fontWeight = FontWeight.Bold, color = color, fontSize = if (isTotal) 15.sp else 13.sp)
        }
    }
}

@Composable
fun BankLogTab(
    transactions: List<BankTransaction>,
    accounts: List<BankAccount>,
    canEdit: Boolean,
    onAddClick: () -> Unit,
    onEdit: (BankTransaction) -> Unit,
    onDelete: (BankTransaction) -> Unit
) {
    var accountFilter by remember { mutableStateOf<String?>(null) } // null = all accounts
    val shown = if (accountFilter == null) transactions else transactions.filter { it.bankAccount == accountFilter }

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (canEdit) {
            Button(
                onClick = onAddClick,
                modifier = Modifier.fillMaxWidth().testTag("add_bank_tx_button")
            ) {
                Icon(Icons.Default.AccountBalance, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Record Bank Entry")
            }
        }

        if (accounts.isNotEmpty()) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = accountFilter == null,
                    onClick = { accountFilter = null },
                    label = { Text("All accounts") }
                )
                accounts.forEach { a ->
                    FilterChip(
                        selected = accountFilter == a.name,
                        onClick = { accountFilter = a.name },
                        label = { Text(a.name) }
                    )
                }
            }
        }

        if (shown.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No bank transactions logged.", color = MaterialTheme.colorScheme.outline)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(shown) { tx ->
                    val isDeposit = tx.bankDelta() >= 0L // green "+" when the entry raises the bank balance
                    val isTransfer = BankTxType.isTransfer(tx.transactionType)
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        color = if (isDeposit) Color(0xFFE8F5E9) else Color(0xFFFFEBEE),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = BankTxType.canonical(tx.transactionType).uppercase(),
                                            color = if (isDeposit) Color(0xFF2E7D32) else Color(0xFFC62828),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        listOf(tx.date, tx.bankAccount).filter { it.isNotBlank() }.joinToString(" - "),
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                                if (tx.transactionId.isNotBlank()) {
                                    Text("Tx ID: ${tx.transactionId}", fontSize = 12.sp)
                                }
                                if (tx.remarks.isNotBlank()) {
                                    Text(tx.remarks, fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                                }
                            }
                            Text(
                                text = "${if (isDeposit) "+" else "-"}\u20B9${tx.amount}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = if (isDeposit) Color(0xFF2E7D32) else Color(0xFFC62828)
                            )
                            if (canEdit) {
                                // A transfer is two linked entries: it can only be deleted (both sides together), not edited.
                                EditDeleteActions(
                                    onEdit = if (isTransfer) null else ({ onEdit(tx) }),
                                    onDelete = { onDelete(tx) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DonationsTab(
    donations: List<Donation>,
    canEdit: Boolean,
    onAddClick: () -> Unit,
    onEdit: (Donation) -> Unit,
    onDelete: (Donation) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (canEdit) {
            Button(onClick = onAddClick, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.VolunteerActivism, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Record Donation")
            }
        }

        if (donations.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No donations recorded.", color = MaterialTheme.colorScheme.outline)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(donations) { d ->
                    Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(d.donorName, fontWeight = FontWeight.Bold)
                                if (d.donorContact.isNotBlank()) Text(d.donorContact, fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                                Text("Date: ${d.date} • ${d.purpose.ifBlank { "General" }}", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                            }
                            Text("₹${d.amount}", fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32), fontSize = 16.sp)
                            if (canEdit) {
                                EditDeleteActions(onEdit = { onEdit(d) }, onDelete = { onDelete(d) })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ExpensesTab(
    expenses: List<GeneralExpense>,
    canEdit: Boolean,
    onAddClick: () -> Unit,
    onEdit: (GeneralExpense) -> Unit,
    onDelete: (GeneralExpense) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (canEdit) {
            Button(onClick = onAddClick, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.AddShoppingCart, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Record General Expense")
            }
        }

        if (expenses.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No general expenses recorded.", color = MaterialTheme.colorScheme.outline)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(expenses) { exp ->
                    Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(exp.description, fontWeight = FontWeight.Bold)
                                Text("${exp.category} • ${exp.date}", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                            }
                            Text("₹${exp.amount}", fontWeight = FontWeight.Bold, color = Color(0xFFC62828), fontSize = 16.sp)
                            if (canEdit) {
                                EditDeleteActions(onEdit = { onEdit(exp) }, onDelete = { onDelete(exp) })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountChips(accounts: List<BankAccount>, selected: String, onSelect: (String) -> Unit) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        accounts.forEach { a ->
            FilterChip(
                selected = selected == a.name,
                onClick = { onSelect(a.name) },
                label = { Text(a.name) }
            )
        }
    }
}

@Composable
fun AddBankTxDialog(
    onDismiss: () -> Unit,
    onSave: (BankTransaction) -> Unit,
    onTransfer: (from: String, to: String, amount: Long, date: String, transactionId: String, remarks: String) -> Unit,
    accounts: List<BankAccount>,
    initial: BankTransaction? = null
) {
    var type by remember {
        mutableStateOf(
            initial?.let { BankTxType.canonical(it.transactionType) }?.takeIf { it in BankTxType.ENTRY_CHOICES } ?: BankTxType.DEPOSIT
        )
    }
    var amountStr by remember { mutableStateOf(initial?.amount?.toString() ?: "") }
    var txId by remember { mutableStateOf(initial?.transactionId ?: "") }
    var remarks by remember { mutableStateOf(initial?.remarks ?: "") }
    var date by remember { mutableStateOf(initial?.date?.ifBlank { null } ?: SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())) }
    var account by remember {
        mutableStateOf(initial?.bankAccount?.takeIf { n -> accounts.any { it.name == n } } ?: accounts.firstOrNull()?.name ?: "")
    }
    var fromAccount by remember { mutableStateOf(accounts.getOrNull(0)?.name ?: "") }
    var toAccount by remember { mutableStateOf(accounts.getOrNull(1)?.name ?: "") }

    val amt = amountStr.toLongOrNull() ?: 0L
    val isTransfer = type == BankTxType.TRANSFER
    val remarksMissing = BankTxType.needsRemarks(type) && remarks.isBlank()
    val transferOk = accounts.size >= 2 && fromAccount.isNotBlank() && toAccount.isNotBlank() && fromAccount != toAccount
    val accountOk = if (isTransfer) transferOk else (accounts.isEmpty() || account.isNotBlank())
    val canSave = amt > 0 && !remarksMissing && accountOk

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Bank Entry" else "Edit Bank Entry") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    BankTxType.ENTRY_CHOICES.forEach { t ->
                        if (initial == null || t != BankTxType.TRANSFER) {
                            FilterChip(
                                selected = type == t,
                                onClick = { type = t },
                                label = { Text(t) }
                            )
                        }
                    }
                }
                Text(
                    BankTxType.describe(type),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.outline
                )

                if (isTransfer) {
                    if (accounts.size < 2) {
                        Text(
                            "Add at least two bank accounts in Settings to record a transfer.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.error
                        )
                    } else {
                        Text("From account", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                        AccountChips(accounts, fromAccount) { fromAccount = it }
                        Text("To account", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                        AccountChips(accounts, toAccount) { toAccount = it }
                        if (fromAccount == toAccount) {
                            Text("Choose two different accounts.", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                        }
                    }
                } else if (accounts.isNotEmpty()) {
                    Text("Bank account", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                    AccountChips(accounts, account) { account = it }
                }

                OutlinedTextField(
                    value = amountStr,
                    onValueChange = { amountStr = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Amount (₹) *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = txId,
                    onValueChange = { txId = it },
                    label = { Text("UTR / Cheque / Transaction ID") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = remarks,
                    onValueChange = { remarks = it },
                    label = { Text(if (BankTxType.needsRemarks(type)) "Remarks *" else "Remarks") },
                    singleLine = true,
                    isError = remarksMissing,
                    modifier = Modifier.fillMaxWidth()
                )
                DatePickerField(
                    label = "Date",
                    value = date,
                    onValueChange = { date = it },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                enabled = canSave,
                onClick = {
                    if (canSave) {
                        if (isTransfer) {
                            onTransfer(fromAccount, toAccount, amt, date.trim(), txId.trim(), remarks.trim())
                        } else {
                            onSave(
                                BankTransaction(
                                    transactionType = type,
                                    amount = amt,
                                    transactionId = txId.trim(),
                                    remarks = remarks.trim(),
                                    date = date.trim(),
                                    bankAccount = if (accounts.isNotEmpty()) account else ""
                                )
                            )
                        }
                    }
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun AddDonationDialog(
    onDismiss: () -> Unit,
    onSave: (Donation) -> Unit,
    initial: Donation? = null
) {
    var donorName by remember { mutableStateOf(initial?.donorName ?: "") }
    var contact by remember { mutableStateOf(initial?.donorContact ?: "") }
    var amountStr by remember { mutableStateOf(initial?.amount?.toString() ?: "") }
    var purpose by remember { mutableStateOf(initial?.purpose ?: "") }
    var date by remember { mutableStateOf(initial?.date?.ifBlank { null } ?: SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Record Donation" else "Edit Donation") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = donorName,
                    onValueChange = { donorName = it },
                    label = { Text("Donor Name *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = contact,
                    onValueChange = { contact = it },
                    label = { Text("Contact Number") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = amountStr,
                    onValueChange = { amountStr = it },
                    label = { Text("Donation Amount (₹) *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = purpose,
                    onValueChange = { purpose = it },
                    label = { Text("Purpose (e.g. Festival, Welfare)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                DatePickerField(
                    label = "Date",
                    value = date,
                    onValueChange = { date = it },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amt = amountStr.toLongOrNull() ?: 0L
                    if (donorName.isNotBlank() && amt > 0) {
                        onSave(Donation(donorName = donorName.trim(), donorContact = contact.trim(), amount = amt, purpose = purpose.trim(), date = date.trim()))
                    }
                }
            ) {
                Text(if (initial == null) "Record" else "Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun AddExpenseDialog(
    events: List<Event>,
    onDismiss: () -> Unit,
    onSave: (GeneralExpense) -> Unit,
    initial: GeneralExpense? = null
) {
    var desc by remember { mutableStateOf(initial?.description ?: "") }
    var amountStr by remember { mutableStateOf(initial?.amount?.toString() ?: "") }
    var category by remember { mutableStateOf(initial?.category ?: "Maintenance") }
    var date by remember { mutableStateOf(initial?.date?.ifBlank { null } ?: SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Record General Expense" else "Edit General Expense") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = desc,
                    onValueChange = { desc = it },
                    label = { Text("Description *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = amountStr,
                    onValueChange = { amountStr = it },
                    label = { Text("Amount (₹) *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("Category (Electricity, Cleaning, Office...)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                DatePickerField(
                    label = "Date",
                    value = date,
                    onValueChange = { date = it },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amt = amountStr.toLongOrNull() ?: 0L
                    if (desc.isNotBlank() && amt > 0) {
                        onSave(GeneralExpense(description = desc.trim(), amount = amt, category = category.trim(), date = date.trim()))
                    }
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
