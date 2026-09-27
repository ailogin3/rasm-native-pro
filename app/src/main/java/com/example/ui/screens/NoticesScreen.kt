package com.example.ui.screens

import android.content.Intent
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Notice
import com.example.ui.components.ConfirmDeleteDialog
import com.example.ui.components.EditDeleteActions
import com.example.ui.viewmodel.RasmViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/** True when the notice was posted in the last [days] days. */
internal fun isRecentNotice(notice: Notice, days: Int = 3): Boolean {
    if (notice.date.length < 10) return false
    return try {
        val posted = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(notice.date) ?: return false
        TimeUnit.MILLISECONDS.toDays(Date().time - posted.time) <= days
    } catch (e: Exception) {
        false
    }
}

@Composable
fun NoticesScreen(viewModel: RasmViewModel) {
    val notices by viewModel.notices.collectAsState()
    val isAdmin by viewModel.isAdmin.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val context = LocalContext.current

    var showAdd by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Notice?>(null) }
    var toDelete by remember { mutableStateOf<Notice?>(null) }

    Scaffold(
        floatingActionButton = {
            if (isAdmin) {
                FloatingActionButton(
                    onClick = { showAdd = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Post notice")
                }
            }
        }
    ) { innerPadding ->
        if (notices.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Campaign,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("No notices yet", color = MaterialTheme.colorScheme.outline)
                    if (isAdmin) {
                        Text("Tap + to post one", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(top = 12.dp, bottom = 88.dp)
            ) {
                items(notices, key = { it.docId }) { notice ->
                    NoticeCard(
                        notice = notice,
                        isAdmin = isAdmin,
                        onShare = {
                            val text = "*${notice.title}*\n\n${notice.body}\n\n- ${settings.associationName}"
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, text)
                            }
                            context.startActivity(Intent.createChooser(send, "Share notice"))
                        },
                        onEdit = { editing = notice },
                        onDelete = { toDelete = notice }
                    )
                }
            }
        }
    }

    if (showAdd) {
        NoticeDialog(
            initial = null,
            onDismiss = { showAdd = false },
            onSave = { title, body, pinned ->
                viewModel.postNotice(title, body, pinned) { showAdd = false }
            }
        )
    }

    editing?.let { current ->
        NoticeDialog(
            initial = current,
            onDismiss = { editing = null },
            onSave = { title, body, pinned ->
                viewModel.updateNotice(current.copy(title = title, body = body, pinned = pinned)) { editing = null }
            }
        )
    }

    toDelete?.let { n ->
        ConfirmDeleteDialog(
            title = "Delete notice?",
            message = "\"${n.title}\" will be removed for everyone. This cannot be undone.",
            onConfirm = {
                viewModel.deleteNotice(n)
                toDelete = null
            },
            onDismiss = { toDelete = null }
        )
    }
}

@Composable
private fun NoticeCard(
    notice: Notice,
    isAdmin: Boolean,
    onShare: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (notice.pinned) Color(0xFFFFF8E1) else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (notice.pinned) NoticeChip("PINNED", Color(0xFF8D6E00), Color(0xFFFFECB3))
                if (isRecentNotice(notice)) NoticeChip("NEW", Color(0xFF2E7D32), Color(0xFFE8F5E9))
            }
            Text(notice.title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(notice.body, fontSize = 14.sp)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = listOf(notice.date, notice.postedBy).filter { it.isNotBlank() }.joinToString(" - "),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onShare, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Default.Share,
                        contentDescription = "Share notice",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                if (isAdmin) {
                    EditDeleteActions(onEdit = onEdit, onDelete = onDelete)
                }
            }
        }
    }
}

@Composable
private fun NoticeChip(text: String, textColor: Color, background: Color) {
    Surface(color = background, shape = RoundedCornerShape(6.dp)) {
        Text(
            text = text,
            color = textColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun NoticeDialog(
    initial: Notice?,
    onDismiss: () -> Unit,
    onSave: (title: String, body: String, pinned: Boolean) -> Unit
) {
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var body by remember { mutableStateOf(initial?.body ?: "") }
    var pinned by remember { mutableStateOf(initial?.pinned ?: false) }
    val canSave = title.isNotBlank() && body.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Post Notice" else "Edit Notice") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it },
                    label = { Text("Message *") },
                    minLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = pinned, onCheckedChange = { pinned = it })
                    Text("Pin to the top", fontSize = 14.sp)
                }
                Text(
                    "Every member sees notices in the app. Use the share button on a notice to send it to your WhatsApp group.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        },
        confirmButton = {
            Button(
                enabled = canSave,
                onClick = { if (canSave) onSave(title.trim(), body.trim(), pinned) }
            ) {
                Text(if (initial == null) "Post" else "Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
