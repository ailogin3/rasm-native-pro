package com.example.ui.screens

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.*
import com.example.ui.viewmodel.RasmViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventsScreen(viewModel: RasmViewModel) {
    val events by viewModel.events.collectAsState()
    val eventCollections by viewModel.eventCollections.collectAsState()
    val members by viewModel.members.collectAsState()
    val isAdmin by viewModel.isAdmin.collectAsState()
    val context = LocalContext.current

    var showCreateDialog by remember { mutableStateOf(false) }
    var selectedEvent by remember { mutableStateOf<Event?>(null) }

    Scaffold(
        floatingActionButton = {
            if (isAdmin) {
                FloatingActionButton(
                    onClick = { showCreateDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.testTag("add_event_fab")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "New Event")
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = "Community Events",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "${events.size} organized events",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (events.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No events scheduled yet.", color = MaterialTheme.colorScheme.outline)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(events, key = { it.docId }) { event ->
                        val collections = eventCollections.filter { it.eventId == event.docId }
                        val totalCollected = collections.sumOf { it.amount }
                        val totalExpenses = event.expenses.sumOf { it.amount }
                        val netBalance = totalCollected - totalExpenses

                        EventCard(
                            event = event,
                            totalCollected = totalCollected,
                            totalExpenses = totalExpenses,
                            netBalance = netBalance,
                            participantCount = collections.size,
                            onClick = { selectedEvent = event }
                        )
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateEventDialog(
            onDismiss = { showCreateDialog = false },
            onSave = { newEvent ->
                viewModel.createEvent(newEvent) {
                    showCreateDialog = false
                }
            }
        )
    }

    selectedEvent?.let { event ->
        EventDetailsDialog(
            event = event,
            eventCollections = eventCollections.filter { it.eventId == event.docId },
            members = members,
            isAdmin = isAdmin,
            context = context,
            viewModel = viewModel,
            onDismiss = { selectedEvent = null }
        )
    }
}

@Composable
fun EventCard(
    event: Event,
    totalCollected: Long,
    totalExpenses: Long,
    netBalance: Long,
    participantCount: Int,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("event_card_${event.docId}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(event.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Surface(
                    color = Color(0xFFE0F2F1),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "Fee: ₹${event.fee}",
                        color = Color(0xFF006A60),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Date: ${event.date}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Collected", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                    Text("₹$totalCollected", fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                }
                Column {
                    Text("Expenses", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                    Text("₹$totalExpenses", fontWeight = FontWeight.Bold, color = Color(0xFFC62828))
                }
                Column {
                    Text("Net Balance", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                    Text("₹$netBalance", fontWeight = FontWeight.Bold, color = if (netBalance >= 0) Color(0xFF1565C0) else Color(0xFFC62828))
                }
                Column {
                    Text("Paid", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                    Text("$participantCount members", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun CreateEventDialog(
    onDismiss: () -> Unit,
    onSave: (Event) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())) }
    var feeStr by remember { mutableStateOf("100") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Event") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Event Name *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = date,
                    onValueChange = { date = it },
                    label = { Text("Date (yyyy-MM-dd) *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = feeStr,
                    onValueChange = { feeStr = it },
                    label = { Text("Fee per Member (₹)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        val fee = feeStr.toLongOrNull() ?: 0L
                        onSave(Event(name = name.trim(), date = date.trim(), fee = fee))
                    }
                }
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventDetailsDialog(
    event: Event,
    eventCollections: List<EventCollection>,
    members: List<Member>,
    isAdmin: Boolean,
    context: Context,
    viewModel: RasmViewModel,
    onDismiss: () -> Unit
) {
    var subTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Overview", "Collect", "Expenses", "Files")

    // Sub-dialogs
    var showExpenseDialog by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(event.name, fontWeight = FontWeight.Bold)
                Text("Date: ${event.date} • Fee: ₹${event.fee}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
            ) {
                PrimaryTabRow(selectedTabIndex = subTab) {
                    tabs.forEachIndexed { idx, label ->
                        Tab(
                            selected = subTab == idx,
                            onClick = { subTab = idx },
                            text = { Text(label, fontSize = 11.sp) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                when (subTab) {
                    0 -> {
                        // Overview
                        val totalCol = eventCollections.sumOf { it.amount }
                        val totalExp = event.expenses.sumOf { it.amount }
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Total Collections: ₹$totalCol", fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                            Text("Total Expenses: ₹$totalExp", fontWeight = FontWeight.Bold, color = Color(0xFFC62828))
                            Text("Net Surplus: ₹${totalCol - totalExp}", fontWeight = FontWeight.Bold, color = Color(0xFF1565C0))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Paid Members (${eventCollections.size}):", fontWeight = FontWeight.SemiBold)
                            LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
                                items(eventCollections) { col ->
                                    val member = members.find { it.docId == col.memberId || it.contact == col.contact }
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(member?.name ?: col.contact, fontSize = 13.sp)
                                        Text("₹${col.amount}", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF2E7D32))
                                    }
                                }
                            }
                        }
                    }
                    1 -> {
                        // Collect payment
                        var query by remember { mutableStateOf("") }
                        val member = remember(query, members) {
                            if (query.isBlank()) null
                            else members.find { it.contact.contains(query) || it.name.contains(query, ignoreCase = true) }
                        }
                        var amountStr by remember { mutableStateOf(event.fee.toString()) }

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = query,
                                onValueChange = { query = it },
                                label = { Text("Member Contact or Name") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            member?.let {
                                Text("Selected: ${it.name} (${it.contact})", color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
                            }
                            OutlinedTextField(
                                value = amountStr,
                                onValueChange = { amountStr = it },
                                label = { Text("Amount (₹)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Button(
                                onClick = {
                                    member?.let { m ->
                                        val amt = amountStr.toLongOrNull() ?: event.fee
                                        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                                        viewModel.recordEventPayment(m, event, amt, today, context) {
                                            query = ""
                                        }
                                    }
                                },
                                enabled = isAdmin && member != null,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Record Payment & WhatsApp")
                            }
                        }
                    }
                    2 -> {
                        // Expenses
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (isAdmin) {
                                Button(onClick = { showExpenseDialog = true }, modifier = Modifier.fillMaxWidth()) {
                                    Icon(Icons.Default.Add, contentDescription = null)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Add Expense")
                                }
                            }
                            LazyColumn(modifier = Modifier.heightIn(max = 240.dp)) {
                                items(event.expenses) { exp ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column {
                                            Text(exp.description, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                            Text(exp.date, fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                                        }
                                        Text("₹${exp.amount}", fontWeight = FontWeight.Bold, color = Color(0xFFC62828))
                                    }
                                }
                            }
                        }
                    }
                    3 -> {
                        // Files / Attachments
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Event Attachments (${event.attachments.size})", fontWeight = FontWeight.Bold)
                            if (event.attachments.isEmpty()) {
                                Text("No attachments uploaded.", color = MaterialTheme.colorScheme.outline)
                            } else {
                                event.attachments.forEach { att ->
                                    Card(shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.AttachFile, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(att.name, modifier = Modifier.weight(1f), fontSize = 13.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )

    if (showExpenseDialog) {
        var desc by remember { mutableStateOf("") }
        var amtStr by remember { mutableStateOf("") }
        var date by remember { mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())) }

        AlertDialog(
            onDismissRequest = { showExpenseDialog = false },
            title = { Text("Add Event Expense") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = desc,
                        onValueChange = { desc = it },
                        label = { Text("Expense Description *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = amtStr,
                        onValueChange = { amtStr = it },
                        label = { Text("Amount (₹) *") },
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
                        val amt = amtStr.toLongOrNull() ?: 0L
                        if (desc.isNotBlank() && amt > 0) {
                            val newExp = EventExpense(
                                id = "exp_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(4)}",
                                description = desc.trim(),
                                amount = amt,
                                date = date.trim()
                            )
                            val updated = event.copy(expenses = event.expenses + newExp)
                            viewModel.updateEvent(updated) {
                                showExpenseDialog = false
                            }
                        }
                    }
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { showExpenseDialog = false }) { Text("Cancel") }
            }
        )
    }
}
