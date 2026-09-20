package com.example.ui.screens

import android.content.Context
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.MaintenanceCollection
import com.example.data.model.Member
import com.example.ui.viewmodel.RasmViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaintenanceScreen(viewModel: RasmViewModel) {
    val members by viewModel.members.collectAsState()
    val collections by viewModel.maintenanceCollections.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val isAdmin by viewModel.isAdmin.collectAsState()
    val context = LocalContext.current

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabTitles = listOf("Collect Dues", "Check Dues", "Pending Report", "Reminders")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Tab row
        PrimaryTabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary
        ) {
            tabTitles.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title, maxLines = 1, fontSize = 13.sp) }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        when (selectedTab) {
            0 -> CollectDuesTab(viewModel, members, settings.monthlyFee, isAdmin, context)
            1 -> CheckDuesTab(members, collections)
            2 -> PendingReportTab(members, collections)
            3 -> RemindersTab(viewModel, members, collections, settings.associationName, context)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectDuesTab(
    viewModel: RasmViewModel,
    members: List<Member>,
    monthlyFee: Long,
    isAdmin: Boolean,
    context: Context
) {
    var contactQuery by remember { mutableStateOf("") }
    var selectedPeriod by remember { mutableIntStateOf(1) }
    var startMonth by remember { mutableStateOf(getCurrentMonthYear()) }

    val matchedMember = remember(contactQuery, members) {
        if (contactQuery.isBlank()) null
        else members.find { it.contact.contains(contactQuery) || it.name.contains(contactQuery, ignoreCase = true) }
    }

    val totalAmount = selectedPeriod * monthlyFee
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (!isAdmin) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Only association admins can collect maintenance fees.",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        // Contact input with auto-fill
        OutlinedTextField(
            value = contactQuery,
            onValueChange = { contactQuery = it },
            label = { Text("Member Contact or Name") },
            leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("maintenance_contact_input"),
            enabled = isAdmin,
            shape = RoundedCornerShape(12.dp)
        )

        matchedMember?.let { m ->
            Surface(
                color = Color(0xFFE8F5E9),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF2E7D32))
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Selected: ${m.name}",
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2E7D32)
                        )
                        Text(
                            text = "${m.contact} • ${m.memberType}",
                            fontSize = 12.sp,
                            color = Color(0xFF1B5E20)
                        )
                    }
                }
            }
        }

        // Month Picker
        val monthOptions = remember { generateMonthList() }
        var expandedMonthDropdown by remember { mutableStateOf(false) }

        ExposedDropdownMenuBox(
            expanded = expandedMonthDropdown,
            onExpandedChange = { if (isAdmin) expandedMonthDropdown = !expandedMonthDropdown }
        ) {
            OutlinedTextField(
                value = startMonth,
                onValueChange = {},
                readOnly = true,
                label = { Text("Start Month") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedMonthDropdown) },
                modifier = Modifier.fillMaxWidth().menuAnchor(),
                enabled = isAdmin,
                shape = RoundedCornerShape(12.dp)
            )
            ExposedDropdownMenu(
                expanded = expandedMonthDropdown,
                onDismissRequest = { expandedMonthDropdown = false }
            ) {
                monthOptions.forEach { m ->
                    DropdownMenuItem(
                        text = { Text(m) },
                        onClick = {
                            startMonth = m
                            expandedMonthDropdown = false
                        }
                    )
                }
            }
        }

        // Period (Months)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Period (Months):", fontWeight = FontWeight.SemiBold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { if (selectedPeriod > 1) selectedPeriod-- },
                    enabled = isAdmin && selectedPeriod > 1
                ) {
                    Icon(Icons.Default.RemoveCircleOutline, contentDescription = "Minus")
                }
                Text(
                    text = "$selectedPeriod",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                IconButton(
                    onClick = { if (selectedPeriod < 36) selectedPeriod++ },
                    enabled = isAdmin && selectedPeriod < 36
                ) {
                    Icon(Icons.Default.AddCircleOutline, contentDescription = "Plus")
                }
            }
        }

        // Calculated Amount Display
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(18.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Total Amount to Collect",
                    fontSize = 13.sp,
                    color = Color(0xFF2E7D32)
                )
                Text(
                    text = "₹$totalAmount",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF1B5E20)
                )
                val endMonth = calculateEndMonth(startMonth, selectedPeriod)
                Text(
                    text = "Covering: $startMonth to $endMonth",
                    fontSize = 12.sp,
                    color = Color(0xFF2E7D32)
                )
            }
        }

        Button(
            onClick = {
                matchedMember?.let { m ->
                    val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                    val endMonth = calculateEndMonth(startMonth, selectedPeriod)
                    viewModel.recordMaintenance(
                        member = m,
                        amount = totalAmount,
                        period = selectedPeriod,
                        startMonth = startMonth,
                        endMonth = endMonth,
                        date = today,
                        context = context
                    ) {
                        contactQuery = ""
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("collect_maintenance_button"),
            enabled = isAdmin && matchedMember != null,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Receipt, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Collect & Send WhatsApp Receipt", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun CheckDuesTab(
    members: List<Member>,
    collections: List<MaintenanceCollection>
) {
    var query by remember { mutableStateOf("") }
    val member = remember(query, members) {
        if (query.isBlank()) null
        else members.find { it.contact.contains(query) || it.name.contains(query, ignoreCase = true) }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Search by Member Contact or Name") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("check_dues_search_input"),
            shape = RoundedCornerShape(12.dp)
        )

        member?.let { m ->
            val memberPayments = collections.filter { it.memberId == m.docId || it.contact == m.contact }
            val totalPaid = memberPayments.sumOf { it.amount }

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = m.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(text = "Contact: ${m.contact}", style = MaterialTheme.typography.bodySmall)
                    Text(text = "Member Type: ${m.memberType}", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "Total Maintenance Paid: ₹$totalPaid", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text(text = "Payment Records: ${memberPayments.size}", style = MaterialTheme.typography.bodySmall)
                }
            }

            Text("Payment History", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)

            if (memberPayments.isEmpty()) {
                Text("No payment records found.", color = MaterialTheme.colorScheme.outline)
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(memberPayments) { p ->
                        Card(
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("${p.startMonth} to ${p.endMonth}", fontWeight = FontWeight.SemiBold)
                                    Text("Date: ${p.date} • ${p.period} month(s)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                                }
                                Text("₹${p.amount}", fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                            }
                        }
                    }
                }
            }
        } ?: run {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Type a contact number or member name above to check dues.", color = MaterialTheme.colorScheme.outline)
            }
        }
    }
}

@Composable
fun PendingReportTab(
    members: List<Member>,
    collections: List<MaintenanceCollection>
) {
    var selectedMonth by remember { mutableStateOf(getCurrentMonthYear()) }
    val monthList = remember { generateMonthList() }
    var expandedMonth by remember { mutableStateOf(false) }

    // Find pending members
    val pendingMembers = remember(selectedMonth, members, collections) {
        members.filter { member ->
            val hasPaid = collections.any { c ->
                (c.memberId == member.docId || c.contact == member.contact) &&
                        isMonthBetween(selectedMonth, c.startMonth, c.endMonth)
            }
            !hasPaid
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Pending for Month:", fontWeight = FontWeight.SemiBold)
            Box {
                Button(onClick = { expandedMonth = true }) {
                    Text(selectedMonth)
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                }
                DropdownMenu(expanded = expandedMonth, onDismissRequest = { expandedMonth = false }) {
                    monthList.forEach { m ->
                        DropdownMenuItem(
                            text = { Text(m) },
                            onClick = {
                                selectedMonth = m
                                expandedMonth = false
                            }
                        )
                    }
                }
            }
        }

        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (pendingMembers.isEmpty()) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (pendingMembers.isEmpty()) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (pendingMembers.isEmpty()) Color(0xFF2E7D32) else Color(0xFFC62828)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = if (pendingMembers.isEmpty())
                        "All members have paid for $selectedMonth! 🎉"
                    else
                        "${pendingMembers.size} members have not paid for $selectedMonth",
                    color = if (pendingMembers.isEmpty()) Color(0xFF2E7D32) else Color(0xFFC62828),
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(pendingMembers) { m ->
                Card(
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(m.name, fontWeight = FontWeight.Bold)
                            Text(m.contact, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        }
                        Surface(
                            color = Color(0xFFFFEBEE),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                "PENDING",
                                color = Color(0xFFC62828),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RemindersTab(
    viewModel: RasmViewModel,
    members: List<Member>,
    collections: List<MaintenanceCollection>,
    associationName: String,
    context: Context
) {
    var selectedMonth by remember { mutableStateOf(getCurrentMonthYear()) }
    var reminderType by remember { mutableStateOf("Gentle Reminder") }
    var customMessage by remember { mutableStateOf("") }

    val pendingMembers = remember(selectedMonth, members, collections) {
        members.filter { member ->
            val hasPaid = collections.any { c ->
                (c.memberId == member.docId || c.contact == member.contact) &&
                        isMonthBetween(selectedMonth, c.startMonth, c.endMonth)
            }
            !hasPaid
        }
    }

    val previewText = remember(reminderType, customMessage, selectedMonth, associationName) {
        if (customMessage.isNotBlank()) customMessage
        else when (reminderType) {
            "Gentle Reminder" -> "Dear Member,\n\nThis is a gentle reminder that your maintenance fee for $selectedMonth is pending. Please make the payment at your earliest convenience.\n\nThank you,\n$associationName"
            "Urgent Reminder" -> "URGENT NOTICE\n\nDear Member,\n\nYour maintenance fee for $selectedMonth is still pending. Kindly clear the payment immediately to avoid inconvenience.\n\n$associationName"
            else -> "FINAL NOTICE\n\nDear Member,\n\nThis is the final notice for pending maintenance dues for $selectedMonth. Please clear within 24 hours.\n\n$associationName"
        }
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Payment Reminder System", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

        // Type selection
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Gentle Reminder", "Urgent Reminder", "Final Notice").forEach { type ->
                FilterChip(
                    selected = reminderType == type,
                    onClick = { reminderType = type },
                    label = { Text(type, fontSize = 11.sp) }
                )
            }
        }

        // Preview box
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("Message Preview:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text(previewText, style = MaterialTheme.typography.bodySmall)
            }
        }

        Text("Pending Members (${pendingMembers.size})", fontWeight = FontWeight.Bold)

        pendingMembers.forEach { m ->
            Card(
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(m.name, fontWeight = FontWeight.Bold)
                        Text(m.contact, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    }
                    Button(
                        onClick = {
                            val personalized = previewText.replace("Dear Member", "Dear ${m.name}")
                            viewModel.sendWhatsAppMessage(m.contact, personalized, context)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366))
                    ) {
                        Icon(Icons.Default.Chat, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Send WhatsApp")
                    }
                }
            }
        }
    }
}

fun getCurrentMonthYear(): String {
    val cal = Calendar.getInstance()
    val monthNames = arrayOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
    return "${monthNames[cal.get(Calendar.MONTH)]} ${cal.get(Calendar.YEAR)}"
}

fun generateMonthList(): List<String> {
    val list = mutableListOf<String>()
    val monthNames = arrayOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
    val cal = Calendar.getInstance()
    val currentYear = cal.get(Calendar.YEAR)
    for (year in 2024..2030) {
        for (m in monthNames) {
            list.add("$m $year")
        }
    }
    return list
}

fun calculateEndMonth(startMonth: String, period: Int): String {
    val monthNames = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
    val parts = startMonth.split(" ")
    if (parts.size != 2) return startMonth
    val mIdx = monthNames.indexOf(parts[0])
    val y = parts[1].toIntOrNull() ?: 2026
    val totalM = mIdx + (period - 1)
    val endMIdx = totalM % 12
    val endY = y + (totalM / 12)
    return "${monthNames[endMIdx]} $endY"
}

fun isMonthBetween(checkMonth: String, startMonth: String, endMonth: String): Boolean {
    val monthNames = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
    fun monthYearToVal(my: String): Int {
        val parts = my.split(" ")
        if (parts.size != 2) return 0
        val m = monthNames.indexOf(parts[0])
        val y = parts[1].toIntOrNull() ?: 0
        return y * 12 + m
    }
    val checkVal = monthYearToVal(checkMonth)
    val startVal = monthYearToVal(startMonth)
    val endVal = monthYearToVal(endMonth)
    return checkVal in startVal..endVal
}
