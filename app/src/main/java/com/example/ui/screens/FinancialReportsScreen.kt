package com.example.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.*
import com.example.ui.viewmodel.RasmViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinancialReportsScreen(viewModel: RasmViewModel) {
    val settings by viewModel.settings.collectAsState()
    val maintenanceCollections by viewModel.maintenanceCollections.collectAsState()
    val eventCollections by viewModel.eventCollections.collectAsState()
    val events by viewModel.events.collectAsState()
    val bankTransactions by viewModel.bankTransactions.collectAsState()
    val donations by viewModel.donations.collectAsState()
    val generalExpenses by viewModel.generalExpenses.collectAsState()
    val isAdmin by viewModel.isAdmin.collectAsState()

    // Financial calculations
    val totalMaintenance = maintenanceCollections.sumOf { it.amount }
    val totalEventCollections = eventCollections.sumOf { it.amount }
    val totalDonations = donations.sumOf { it.amount }
    val totalInflow = totalMaintenance + totalEventCollections + totalDonations

    val totalEventExpenses = events.sumOf { it.expenses.sumOf { exp -> exp.amount } }
    val totalGeneralExpenses = generalExpenses.sumOf { it.amount }
    val totalOutflow = totalEventExpenses + totalGeneralExpenses

    val totalDeposits = bankTransactions.filter { it.transactionType.equals("Deposit", ignoreCase = true) }.sumOf { it.amount }
    val totalWithdrawals = bankTransactions.filter { it.transactionType.equals("Withdrawal", ignoreCase = true) }.sumOf { it.amount }

    val currentBankBalance = settings.initialBankBalance + totalDeposits - totalWithdrawals
    val cashInHand = (totalInflow - totalOutflow) - totalDeposits + totalWithdrawals

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Overview", "Bank Log", "Donations", "Expenses")

    var showAddBankTx by remember { mutableStateOf(false) }
    var showAddDonation by remember { mutableStateOf(false) }
    var showAddExpense by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // High-level Balance Card
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
                            Text("Current Bank Balance", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                            Text("₹$currentBankBalance", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                        }
                        Surface(
                            color = Color.White.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                                Text("Cash in Hand", color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp)
                                Text("₹$cashInHand", color = Color(0xFFFFD54F), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Total Income: ₹$totalInflow", color = Color(0xFFB2DFDB), fontSize = 12.sp)
                        Text("Total Outflow: ₹$totalOutflow", color = Color(0xFFFFCDD2), fontSize = 12.sp)
                    }
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

        when (selectedTab) {
            0 -> OverviewTab(totalMaintenance, totalEventCollections, totalDonations, totalEventExpenses, totalGeneralExpenses, currentBankBalance, cashInHand)
            1 -> BankLogTab(bankTransactions, isAdmin, onAddClick = { showAddBankTx = true }, onDelete = { viewModel.deleteBankTransaction(it) })
            2 -> DonationsTab(donations, isAdmin, onAddClick = { showAddDonation = true }, onDelete = { viewModel.deleteDonation(it) })
            3 -> ExpensesTab(generalExpenses, isAdmin, onAddClick = { showAddExpense = true }, onDelete = { viewModel.deleteGeneralExpense(it) })
        }
    }

    if (showAddBankTx) {
        AddBankTxDialog(
            onDismiss = { showAddBankTx = false },
            onSave = { tx ->
                viewModel.recordBankTransaction(tx) {
                    showAddBankTx = false
                }
            }
        )
    }

    if (showAddDonation) {
        AddDonationDialog(
            onDismiss = { showAddDonation = false },
            onSave = { d ->
                viewModel.recordDonation(d) {
                    showAddDonation = false
                }
            }
        )
    }

    if (showAddExpense) {
        AddExpenseDialog(
            events = events,
            onDismiss = { showAddExpense = false },
            onSave = { exp ->
                viewModel.recordGeneralExpense(exp) {
                    showAddExpense = false
                }
            }
        )
    }
}

@Composable
fun OverviewTab(
    maintenance: Long,
    eventsCol: Long,
    donations: Long,
    eventExp: Long,
    generalExp: Long,
    bankBal: Long,
    cashInHand: Long
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Income Breakdown", fontWeight = FontWeight.Bold)
        FinancialRow("Maintenance Dues Collected", "₹$maintenance", Color(0xFF2E7D32))
        FinancialRow("Events Collections", "₹$eventsCol", Color(0xFF2E7D32))
        FinancialRow("Community Donations", "₹$donations", Color(0xFF2E7D32))
        FinancialRow("Gross Inflow", "₹${maintenance + eventsCol + donations}", Color(0xFF006A60), isTotal = true)

        Spacer(modifier = Modifier.height(10.dp))
        Text("Expenditure Breakdown", fontWeight = FontWeight.Bold)
        FinancialRow("Event Specific Expenses", "₹$eventExp", Color(0xFFC62828))
        FinancialRow("General Association Expenses", "₹$generalExp", Color(0xFFC62828))
        FinancialRow("Gross Outflow", "₹${eventExp + generalExp}", Color(0xFFC62828), isTotal = true)

        Spacer(modifier = Modifier.height(10.dp))
        Text("Net Association Position", fontWeight = FontWeight.Bold)
        val net = (maintenance + eventsCol + donations) - (eventExp + generalExp)
        FinancialRow("Net Reserves Surplus", "₹$net", if (net >= 0) Color(0xFF1565C0) else Color(0xFFC62828), isTotal = true)
        FinancialRow("Current Bank Account", "₹$bankBal", Color(0xFF1565C0))
        FinancialRow("Estimated Cash in Hand", "₹$cashInHand", Color(0xFF705D00))
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
    isAdmin: Boolean,
    onAddClick: () -> Unit,
    onDelete: (BankTransaction) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (isAdmin) {
            Button(
                onClick = onAddClick,
                modifier = Modifier.fillMaxWidth().testTag("add_bank_tx_button")
            ) {
                Icon(Icons.Default.AccountBalance, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Record Deposit / Withdrawal")
            }
        }

        if (transactions.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No bank transactions logged.", color = MaterialTheme.colorScheme.outline)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(transactions) { tx ->
                    val isDeposit = tx.transactionType.equals("Deposit", ignoreCase = true)
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
                                            text = tx.transactionType.uppercase(),
                                            color = if (isDeposit) Color(0xFF2E7D32) else Color(0xFFC62828),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(tx.date, fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                                }
                                if (tx.transactionId.isNotBlank()) {
                                    Text("Tx ID: ${tx.transactionId}", fontSize = 12.sp)
                                }
                                if (tx.remarks.isNotBlank()) {
                                    Text(tx.remarks, fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                                }
                            }
                            Text(
                                text = "${if (isDeposit) "+" else "-"}₹${tx.amount}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = if (isDeposit) Color(0xFF2E7D32) else Color(0xFFC62828)
                            )
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
    isAdmin: Boolean,
    onAddClick: () -> Unit,
    onDelete: (Donation) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (isAdmin) {
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
                            Column {
                                Text(d.donorName, fontWeight = FontWeight.Bold)
                                if (d.donorContact.isNotBlank()) Text(d.donorContact, fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                                Text("Date: ${d.date} • ${d.purpose.ifBlank { "General" }}", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                            }
                            Text("₹${d.amount}", fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32), fontSize = 16.sp)
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
    isAdmin: Boolean,
    onAddClick: () -> Unit,
    onDelete: (GeneralExpense) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (isAdmin) {
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
                            Column {
                                Text(exp.description, fontWeight = FontWeight.Bold)
                                Text("${exp.category} • ${exp.date}", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                            }
                            Text("₹${exp.amount}", fontWeight = FontWeight.Bold, color = Color(0xFFC62828), fontSize = 16.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AddBankTxDialog(
    onDismiss: () -> Unit,
    onSave: (BankTransaction) -> Unit
) {
    var type by remember { mutableStateOf("Deposit") }
    var amountStr by remember { mutableStateOf("") }
    var txId by remember { mutableStateOf("") }
    var remarks by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Bank Transaction") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Deposit", "Withdrawal").forEach { t ->
                        FilterChip(
                            selected = type == t,
                            onClick = { type = t },
                            label = { Text(t) }
                        )
                    }
                }
                OutlinedTextField(
                    value = amountStr,
                    onValueChange = { amountStr = it },
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
                    label = { Text("Remarks") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = date,
                    onValueChange = { date = it },
                    label = { Text("Date (yyyy-MM-dd)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amt = amountStr.toLongOrNull() ?: 0L
                    if (amt > 0) {
                        onSave(BankTransaction(transactionType = type, amount = amt, transactionId = txId.trim(), remarks = remarks.trim(), date = date.trim()))
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
    onSave: (Donation) -> Unit
) {
    var donorName by remember { mutableStateOf("") }
    var contact by remember { mutableStateOf("") }
    var amountStr by remember { mutableStateOf("") }
    var purpose by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Record Donation") },
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
                OutlinedTextField(
                    value = date,
                    onValueChange = { date = it },
                    label = { Text("Date (yyyy-MM-dd)") },
                    singleLine = true,
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
                Text("Record")
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
    onSave: (GeneralExpense) -> Unit
) {
    var desc by remember { mutableStateOf("") }
    var amountStr by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Maintenance") }
    var date by remember { mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Record General Expense") },
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
                OutlinedTextField(
                    value = date,
                    onValueChange = { date = it },
                    label = { Text("Date (yyyy-MM-dd)") },
                    singleLine = true,
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
