package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.*
import com.example.ui.viewmodel.RasmViewModel
import com.example.util.ImageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeetingsScreen(viewModel: RasmViewModel) {
    val meetings by viewModel.meetings.collectAsState()
    val members by viewModel.members.collectAsState()
    val isAdmin by viewModel.isAdmin.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val context = LocalContext.current

    var showCreateDialog by remember { mutableStateOf(false) }
    var selectedMeeting by remember { mutableStateOf<Meeting?>(null) }

    Scaffold(
        floatingActionButton = {
            if (isAdmin) {
                FloatingActionButton(
                    onClick = { showCreateDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.testTag("add_meeting_fab")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "New Meeting")
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
                text = "Meeting Minutes & Assembly",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "${meetings.size} meetings recorded",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (meetings.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No meetings scheduled or recorded.", color = MaterialTheme.colorScheme.outline)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(meetings, key = { it.docId }) { meeting ->
                        MeetingCard(
                            meeting = meeting,
                            onClick = { selectedMeeting = meeting }
                        )
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateMeetingDialog(
            onDismiss = { showCreateDialog = false },
            onSave = { newMeeting ->
                viewModel.createMeeting(newMeeting) {
                    showCreateDialog = false
                }
            }
        )
    }

    selectedMeeting?.let { m ->
        MeetingDetailsDialog(
            meeting = m,
            members = members,
            isAdmin = isAdmin,
            associationName = settings.associationName,
            context = context,
            viewModel = viewModel,
            onDismiss = { selectedMeeting = null }
        )
    }
}

@Composable
fun MeetingCard(
    meeting: Meeting,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("meeting_card_${meeting.docId}"),
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
                Text(meeting.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Surface(
                    color = when (meeting.status) {
                        "Completed" -> Color(0xFFE8F5E9)
                        "In-Progress" -> Color(0xFFFFF3E0)
                        else -> Color(0xFFE3F2FD)
                    },
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = meeting.status,
                        color = when (meeting.status) {
                            "Completed" -> Color(0xFF2E7D32)
                            "In-Progress" -> Color(0xFFE65100)
                            else -> Color(0xFF1565C0)
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.outline)
                Spacer(modifier = Modifier.width(4.dp))
                Text("${meeting.date} at ${meeting.time}", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)

                Spacer(modifier = Modifier.width(16.dp))
                Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.outline)
                Spacer(modifier = Modifier.width(4.dp))
                Text(meeting.location, fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
            }

            if (meeting.agenda.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = meeting.agenda.joinToString(" • "),
                    maxLines = 2,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("${meeting.attendees.size} Attendees", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                Text("${meeting.actionItems.size} Action Items", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.secondary)
            }
        }
    }
}

@Composable
fun CreateMeetingDialog(
    onDismiss: () -> Unit,
    onSave: (Meeting) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())) }
    var time by remember { mutableStateOf("18:00") }
    var location by remember { mutableStateOf("Community Hall") }
    var agenda by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Schedule Meeting") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Meeting Title *") },
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
                    value = time,
                    onValueChange = { time = it },
                    label = { Text("Time (e.g. 18:00) *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text("Location") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = agenda,
                    onValueChange = { agenda = it },
                    label = { Text("Agenda Overview") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        onSave(
                            Meeting(
                                title = title.trim(),
                                date = date.trim(),
                                time = time.trim(),
                                location = location.trim(),
                                agenda = if (agenda.isNotBlank()) agenda.lines().map { it.trim() } else emptyList()
                            )
                        )
                    }
                }
            ) {
                Text("Schedule")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeetingDetailsDialog(
    meeting: Meeting,
    members: List<Member>,
    isAdmin: Boolean,
    associationName: String,
    context: Context,
    viewModel: RasmViewModel,
    onDismiss: () -> Unit
) {
    var subTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Minutes", "Attendees", "Actions", "Photos")

    // Local mutable copy for edits
    var currentMeeting by remember { mutableStateOf(meeting) }
    var minutesText by remember { mutableStateOf(meeting.minutes) }
    val coroutineScope = rememberCoroutineScope()
    val scannedPhotos by remember(meeting.docId) { viewModel.meetingPhotos(meeting.docId) }
        .collectAsState(initial = emptyList())
    var zoomPhoto by remember { mutableStateOf<String?>(null) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let {
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val url = ImageUtils.uploadPhotoToStorage(
                        uri = it,
                        context = context,
                        storagePath = ImageUtils.meetingPhotoPath(currentMeeting.docId),
                        maxDimension = 1000
                    )
                    withContext(Dispatchers.Main) {
                        viewModel.addMeetingPhoto(currentMeeting.docId, url)
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Couldn't upload photo: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(currentMeeting.title, fontWeight = FontWeight.Bold)
                Text("${currentMeeting.date} • ${currentMeeting.time} • ${currentMeeting.location}", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 440.dp)
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
                        // Minutes & Template
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (isAdmin) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Minutes Templates:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    TextButton(onClick = {
                                        // Email minutes to all members!
                                        val memberEmails = members.map { it.email.trim() }.filter { it.isNotBlank() }
                                        val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
                                            data = Uri.parse("mailto:")
                                            putExtra(Intent.EXTRA_BCC, memberEmails.toTypedArray())
                                            putExtra(Intent.EXTRA_SUBJECT, "[$associationName] Minutes: ${currentMeeting.title} (${currentMeeting.date})")
                                            putExtra(Intent.EXTRA_TEXT, "MEETING MINUTES\n\nTitle: ${currentMeeting.title}\nDate: ${currentMeeting.date}\nLocation: ${currentMeeting.location}\n\n$minutesText")
                                        }
                                        context.startActivity(Intent.createChooser(emailIntent, "Send Meeting Minutes via Email"))
                                    }) {
                                        Icon(Icons.Default.Email, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Email All (${members.count { it.email.isNotBlank() }})", fontSize = 11.sp)
                                    }
                                }

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Button(
                                        onClick = {
                                            minutesText = """
                                                ANNUAL GENERAL MEETING MINUTES
                                                1. Welcome Address by President
                                                2. Presentation of Annual Report by Secretary
                                                3. Presentation of Audited Accounts by Treasurer
                                                4. Discussion and Adoption of Reports
                                                5. Election / Approval of Office Bearers
                                                6. Any other business with permission of Chair
                                                7. Vote of Thanks
                                            """.trimIndent()
                                        },
                                        modifier = Modifier.weight(1f),
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                                    ) {
                                        Text("AGM", fontSize = 10.sp)
                                    }
                                    Button(
                                        onClick = {
                                            minutesText = """
                                                EXECUTIVE COMMITTEE MEETING MINUTES
                                                1. Confirmation of previous meeting minutes
                                                2. Monthly financial review & dues collection
                                                3. Maintenance & civic issues discussion
                                                4. Upcoming community events planning
                                                5. Decided resolutions & Action items
                                            """.trimIndent()
                                        },
                                        modifier = Modifier.weight(1f),
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                                    ) {
                                        Text("EC Meet", fontSize = 10.sp)
                                    }
                                }
                            }

                            OutlinedTextField(
                                value = minutesText,
                                onValueChange = {
                                    minutesText = it
                                    if (isAdmin) {
                                        val updated = currentMeeting.copy(minutes = it)
                                        currentMeeting = updated
                                        viewModel.updateMeeting(updated) {}
                                    }
                                },
                                label = { Text("Meeting Minutes / Discussions") },
                                minLines = 5,
                                maxLines = 10,
                                readOnly = !isAdmin,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                    1 -> {
                        // Attendees
                        var query by remember { mutableStateOf("") }
                        val matched = remember(query, members) {
                            if (query.isBlank()) emptyList()
                            else members.filter { it.name.contains(query, ignoreCase = true) || it.contact.contains(query) }
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (isAdmin) {
                                OutlinedTextField(
                                    value = query,
                                    onValueChange = { query = it },
                                    label = { Text("Search member to add attendee") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                if (matched.isNotEmpty()) {
                                    Card(shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                                        Column(modifier = Modifier.padding(6.dp)) {
                                            matched.take(3).forEach { m ->
                                                Text(
                                                    text = "+ ${m.name} (${m.contact})",
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clickable {
                                                            if (currentMeeting.attendees.none { it.name == m.name }) {
                                                                val updated = currentMeeting.copy(
                                                                    attendees = currentMeeting.attendees + MeetingAttendee(m.name, m.contact)
                                                                )
                                                                currentMeeting = updated
                                                                viewModel.updateMeeting(updated) {}
                                                            }
                                                            query = ""
                                                        }
                                                        .padding(6.dp),
                                                    fontWeight = FontWeight.SemiBold,
                                                    fontSize = 12.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Text("Attendees List (${currentMeeting.attendees.size}):", fontWeight = FontWeight.Bold)
                            LazyColumn(modifier = Modifier.heightIn(max = 220.dp)) {
                                items(currentMeeting.attendees) { att ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("${att.name} (${att.contact})", fontSize = 13.sp)
                                        if (isAdmin) {
                                            IconButton(
                                                onClick = {
                                                    val updated = currentMeeting.copy(
                                                        attendees = currentMeeting.attendees.filter { it.name != att.name }
                                                    )
                                                    currentMeeting = updated
                                                    viewModel.updateMeeting(updated) {}
                                                },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(Icons.Default.Close, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    2 -> {
                        // Action Items
                        var taskDesc by remember { mutableStateOf("") }
                        var assignedTo by remember { mutableStateOf("") }

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (isAdmin) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    OutlinedTextField(
                                        value = taskDesc,
                                        onValueChange = { taskDesc = it },
                                        label = { Text("Action item") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1.4f)
                                    )
                                    OutlinedTextField(
                                        value = assignedTo,
                                        onValueChange = { assignedTo = it },
                                        label = { Text("Assignee") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                Button(
                                    onClick = {
                                        if (taskDesc.isNotBlank()) {
                                            val newItem = MeetingActionItem(
                                                id = "act_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(4)}",
                                                task = taskDesc.trim(),
                                                assignedTo = assignedTo.trim()
                                            )
                                            val updated = currentMeeting.copy(actionItems = currentMeeting.actionItems + newItem)
                                            currentMeeting = updated
                                            viewModel.updateMeeting(updated) {}
                                            taskDesc = ""
                                            assignedTo = ""
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Add Action Item")
                                }
                            }

                            LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
                                items(currentMeeting.actionItems) { item ->
                                    Card(shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(10.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(item.task, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                if (item.assignedTo.isNotBlank()) {
                                                    Text("Assigned to: ${item.assignedTo}", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                                                }
                                            }
                                            Surface(
                                                color = if (item.status == "Done") Color(0xFFE8F5E9) else Color(0xFFFFF3E0),
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = item.status,
                                                    color = if (item.status == "Done") Color(0xFF2E7D32) else Color(0xFFE65100),
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    3 -> {
                        // Photos of Scanned minutes
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (isAdmin) {
                                Button(
                                    onClick = {
                                        photoPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.AddAPhoto, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Attach Scanned Minutes Photo")
                                }
                            }

                            if (scannedPhotos.isEmpty()) {
                                Text("No scanned minutes photos attached.", color = MaterialTheme.colorScheme.outline)
                            } else {
                                LazyColumn(modifier = Modifier.heightIn(max = 260.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(scannedPhotos, key = { it.docId }) { photo ->
                                        Box(modifier = Modifier.fillMaxWidth()) {
                                            AsyncImage(
                                                model = ImageUtils.toImageModel(photo.data),
                                                contentDescription = "Scanned Minutes",
                                                contentScale = ContentScale.Fit,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(220.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .clickable { zoomPhoto = photo.data }
                                            )
                                            if (isAdmin) {
                                                IconButton(
                                                    onClick = { viewModel.deleteMeetingPhoto(currentMeeting.docId, photo.docId) },
                                                    modifier = Modifier.align(Alignment.TopEnd)
                                                ) {
                                                    Icon(Icons.Default.Delete, contentDescription = "Remove photo", tint = MaterialTheme.colorScheme.error)
                                                }
                                            }
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

    zoomPhoto?.let { z ->
        com.example.ui.components.FullScreenPhotoViewer(photo = z, title = "Scanned minutes", onDismiss = { zoomPhoto = null })
    }
}
