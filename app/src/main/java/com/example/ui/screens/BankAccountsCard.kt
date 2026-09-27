package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.BankAccount
import com.example.ui.components.ConfirmDeleteDialog
import com.example.ui.components.EditDeleteActions
import com.example.ui.viewmodel.RasmViewModel

/**
 * Settings card (admin only) to keep the association's bank accounts.
 * Each account has a name and the balance it held before the first entry in the app.
 * The name is fixed once created; an account with bank entries can't be deleted.
 */
@Composable
fun BankAccountsCard(viewModel: RasmViewModel) {
    val accounts by viewModel.bankAccounts.collectAsState()
    var showAdd by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<BankAccount?>(null) }
    var toDelete by remember { mutableStateOf<BankAccount?>(null) }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Bank Accounts", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { showAdd = true }) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Account")
                }
            }
            Text(
                "Add each bank account with the balance it held before the first entry in this app. " +
                    "Bank entries then choose an account, and Finance shows a balance for each. " +
                    "With no accounts added, the single Opening Bank Balance above is used.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.outline
            )
            if (accounts.isEmpty()) {
                Text("No bank accounts added yet.", fontSize = 13.sp, color = MaterialTheme.colorScheme.outline)
            }
            accounts.forEach { account ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(account.name, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Opening balance \u20B9${account.openingBalance}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    EditDeleteActions(onEdit = { editing = account }, onDelete = { toDelete = account })
                }
            }
        }
    }

    if (showAdd) {
        AccountDialog(
            initial = null,
            onDismiss = { showAdd = false },
            onSave = { name, opening ->
                viewModel.addBankAccount(name, opening) { showAdd = false }
            }
        )
    }

    editing?.let { current ->
        AccountDialog(
            initial = current,
            onDismiss = { editing = null },
            onSave = { _, opening ->
                viewModel.updateBankAccountOpening(current, opening) { editing = null }
            }
        )
    }

    toDelete?.let { account ->
        ConfirmDeleteDialog(
            title = "Delete bank account?",
            message = "\"${account.name}\" will be removed. An account that already has bank entries can't be deleted.",
            onConfirm = {
                viewModel.deleteBankAccount(account)
                toDelete = null
            },
            onDismiss = { toDelete = null }
        )
    }
}

@Composable
private fun AccountDialog(
    initial: BankAccount?,
    onDismiss: () -> Unit,
    onSave: (name: String, openingBalance: Long) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var openingStr by remember { mutableStateOf(initial?.openingBalance?.toString() ?: "0") }
    val opening = openingStr.toLongOrNull()
    val canSave = name.isNotBlank() && opening != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add Bank Account" else "Edit Opening Balance") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (initial == null) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Account name * (e.g. SBI Savings)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Text(initial.name, fontWeight = FontWeight.SemiBold)
                }
                OutlinedTextField(
                    value = openingStr,
                    onValueChange = { openingStr = it.filter { ch -> ch.isDigit() || ch == '-' } },
                    label = { Text("Opening balance (\u20B9)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "The balance this account held before the first entry in this app.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        },
        confirmButton = {
            Button(
                enabled = canSave,
                onClick = { if (opening != null && canSave) onSave(name.trim(), opening) }
            ) {
                Text(if (initial == null) "Add" else "Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
