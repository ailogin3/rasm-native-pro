package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.viewmodel.RasmViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Admin-only card in Settings: export all association data to a JSON file and restore from one. */
@Composable
fun BackupRestoreCard(viewModel: RasmViewModel) {
    val busy by viewModel.backupBusy.collectAsState()
    var pendingRestore by remember { mutableStateOf<Uri?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) viewModel.exportBackup(uri)
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) pendingRestore = uri
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Backup & Restore", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Text(
                "Save all members, dues, events, finances, meetings and photos to a JSON file, or restore from one. Keep backup files safe: they contain member details.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.outline
            )
            Spacer(modifier = Modifier.height(10.dp))

            if (busy) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Working... please keep the app open.", fontSize = 13.sp)
                }
            } else {
                Button(
                    onClick = {
                        val stamp = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                        exportLauncher.launch("rasm-backup-$stamp.json")
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Backup, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Export backup")
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Restore, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Restore from backup")
                }
            }
        }
    }

    val uri = pendingRestore
    if (uri != null) {
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text("Restore from backup?") },
            text = {
                Text(
                    "Records in the file will overwrite existing records with the same ID. Records that are not in the file are not deleted. This cannot be undone.",
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.restoreBackup(uri)
                    pendingRestore = null
                }) { Text("Restore") }
            },
            dismissButton = { TextButton(onClick = { pendingRestore = null }) { Text("Cancel") } }
        )
    }
}
