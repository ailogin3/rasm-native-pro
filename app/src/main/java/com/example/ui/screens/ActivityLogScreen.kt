package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.viewmodel.RasmViewModel

@Composable
fun ActivityLogScreen(viewModel: RasmViewModel) {
    val activityLog by viewModel.activityLog.collectAsState()
    val isAdmin by viewModel.isAdmin.collectAsState()

    var searchQuery by remember { mutableStateOf("") }

    val filteredLog = remember(activityLog, searchQuery) {
        if (searchQuery.isBlank()) activityLog
        else activityLog.filter {
            it.action.contains(searchQuery, ignoreCase = true) ||
                    it.adminEmail.contains(searchQuery, ignoreCase = true) ||
                    it.details.contains(searchQuery, ignoreCase = true)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Activity & Audit Log",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "90-day administrative accountability log",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            Surface(
                color = if (isAdmin) Color(0xFFE8F5E9) else Color(0xFFFFEBEE),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    text = if (isAdmin) "ADMIN ACCESS" else "RESTRICTED",
                    color = if (isAdmin) Color(0xFF2E7D32) else Color(0xFFC62828),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (!isAdmin) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Admin privileges are required to view the activity log.", color = MaterialTheme.colorScheme.error)
                }
            }
            return
        }

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Filter audit records...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("activity_search_input"),
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (filteredLog.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No activity records found.", color = MaterialTheme.colorScheme.outline)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filteredLog, key = { it.docId.ifBlank { it.timestamp } }) { entry ->
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(entry.action, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                Text(entry.timestamp, fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                            }
                            if (entry.adminEmail.isNotBlank()) {
                                Text("By: ${entry.adminEmail}", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                            }
                            if (entry.details.isNotBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(entry.details, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}
