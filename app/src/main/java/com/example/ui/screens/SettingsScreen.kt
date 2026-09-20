package com.example.ui.screens

import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AssociationSettings
import com.example.data.model.OfficeBearers
import com.example.ui.viewmodel.RasmViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: RasmViewModel,
    onLogout: () -> Unit
) {
    val settings by viewModel.settings.collectAsState()
    val officeBearers by viewModel.officeBearers.collectAsState()
    val admins by viewModel.admins.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()
    val isAdmin by viewModel.isAdmin.collectAsState()
    val currentMember by viewModel.currentMember.collectAsState()

    var showConfigDialog by remember { mutableStateOf(false) }
    var showOfficialsDialog by remember { mutableStateOf(false) }
    var showAddAdminDialog by remember { mutableStateOf(false) }

    // Editable settings state
    var assocName by remember(settings) { mutableStateOf(settings.associationName) }
    var regNo by remember(settings) { mutableStateOf(settings.associationRegNo) }
    var location by remember(settings) { mutableStateOf(settings.associationLocation) }
    var feeStr by remember(settings) { mutableStateOf(settings.monthlyFee.toString()) }
    var initBankStr by remember(settings) { mutableStateOf(settings.initialBankBalance.toString()) }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Logged-in User Account Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(currentMember?.name ?: "Member Account", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(currentUser?.email ?: "No email", fontSize = 13.sp, color = MaterialTheme.colorScheme.outline)
                    }
                    Surface(
                        color = if (isAdmin) Color(0xFFE8F5E9) else Color(0xFFE0F2F1),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = if (isAdmin) "ADMIN" else "MEMBER",
                            color = if (isAdmin) Color(0xFF2E7D32) else Color(0xFF006A60),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                Button(
                    onClick = {
                        viewModel.logout()
                        onLogout()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth().testTag("logout_button")
                ) {
                    Icon(Icons.Default.ExitToApp, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Sign Out")
                }
            }
        }

        // Association Details Card (Admin Only)
        if (isAdmin) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Association Configuration", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)

                    OutlinedTextField(
                        value = assocName,
                        onValueChange = { assocName = it },
                        label = { Text("Association Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = regNo,
                        onValueChange = { regNo = it },
                        label = { Text("Registration Number") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = location,
                        onValueChange = { location = it },
                        label = { Text("Location / District") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = feeStr,
                        onValueChange = { feeStr = it },
                        label = { Text("Default Monthly Maintenance Fee (₹)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = initBankStr,
                        onValueChange = { initBankStr = it },
                        label = { Text("Opening Bank Balance (₹)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        onClick = {
                            val fee = feeStr.toLongOrNull() ?: settings.monthlyFee
                            val initBank = initBankStr.toLongOrNull() ?: settings.initialBankBalance
                            viewModel.updateSettings(
                                settings.copy(
                                    associationName = assocName.trim(),
                                    associationRegNo = regNo.trim(),
                                    associationLocation = location.trim(),
                                    monthlyFee = fee,
                                    initialBankBalance = initBank
                                )
                            ) {}
                        },
                        modifier = Modifier.fillMaxWidth().testTag("save_settings_button")
                    ) {
                        Text("Save Association Settings")
                    }
                }
            }

            // Executive Committee Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Executive Committee", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        TextButton(onClick = { showOfficialsDialog = true }) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Edit Officials")
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    OfficialRow("President", officeBearers.presidentName)
                    OfficialRow("Vice President", officeBearers.vicePresidentName)
                    OfficialRow("Secretary", officeBearers.secretaryName)
                    OfficialRow("Joint Secretary", officeBearers.jointSecretaryName)
                    OfficialRow("Treasurer", officeBearers.treasurerName)
                    OfficialRow("Patron", officeBearers.patronName)
                }
            }

            // Manage Admins Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Admin Privileges", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        TextButton(onClick = { showAddAdminDialog = true }) {
                            Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Grant Admin")
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    admins.forEach { adminEmail ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(adminEmail, fontSize = 13.sp)
                            if (adminEmail != currentUser?.email) {
                                IconButton(onClick = { viewModel.removeAdmin(adminEmail) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Revoke Admin", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }

        if (isAdmin) {
            BackupRestoreCard(viewModel)
        }

        // Firebase Connection Configuration
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Firebase Infrastructure", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text("Manage runtime Firebase project credentials without modifying source code.", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)

                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = { showConfigDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.CloudQueue, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Configure Firebase Connection")
                }
            }
        }
    }

    if (showConfigDialog) {
        FirebaseConfigDialog(
            viewModel = viewModel,
            onDismiss = { showConfigDialog = false }
        )
    }

    if (showOfficialsDialog) {
        OfficialsEditDialog(
            current = officeBearers,
            onDismiss = { showOfficialsDialog = false },
            onSave = { updated ->
                viewModel.updateOfficeBearers(updated) {
                    showOfficialsDialog = false
                }
            }
        )
    }

    if (showAddAdminDialog) {
        var emailInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddAdminDialog = false },
            title = { Text("Grant Admin Access") },
            text = {
                OutlinedTextField(
                    value = emailInput,
                    onValueChange = { emailInput = it },
                    label = { Text("Member Email Address") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (emailInput.isNotBlank()) {
                            viewModel.addAdmin(emailInput.trim()) {
                                showAddAdminDialog = false
                            }
                        }
                    }
                ) {
                    Text("Grant")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddAdminDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun OfficialRow(role: String, name: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(role, fontSize = 13.sp, color = MaterialTheme.colorScheme.outline)
        Text(name.ifBlank { "—" }, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun OfficialsEditDialog(
    current: OfficeBearers,
    onDismiss: () -> Unit,
    onSave: (OfficeBearers) -> Unit
) {
    var pres by remember { mutableStateOf(current.presidentName) }
    var vp by remember { mutableStateOf(current.vicePresidentName) }
    var sec by remember { mutableStateOf(current.secretaryName) }
    var jtSec by remember { mutableStateOf(current.jointSecretaryName) }
    var treas by remember { mutableStateOf(current.treasurerName) }
    var patron by remember { mutableStateOf(current.patronName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Officials") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(value = pres, onValueChange = { pres = it }, label = { Text("President") }, singleLine = true)
                OutlinedTextField(value = vp, onValueChange = { vp = it }, label = { Text("Vice President") }, singleLine = true)
                OutlinedTextField(value = sec, onValueChange = { sec = it }, label = { Text("Secretary") }, singleLine = true)
                OutlinedTextField(value = jtSec, onValueChange = { jtSec = it }, label = { Text("Joint Secretary") }, singleLine = true)
                OutlinedTextField(value = treas, onValueChange = { treas = it }, label = { Text("Treasurer") }, singleLine = true)
                OutlinedTextField(value = patron, onValueChange = { patron = it }, label = { Text("Patron") }, singleLine = true)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        current.copy(
                            presidentName = pres.trim(),
                            vicePresidentName = vp.trim(),
                            secretaryName = sec.trim(),
                            jointSecretaryName = jtSec.trim(),
                            treasurerName = treas.trim(),
                            patronName = patron.trim()
                        )
                    )
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
