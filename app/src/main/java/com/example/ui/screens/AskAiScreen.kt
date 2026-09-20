package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ai.AiContextBuilder
import com.example.ai.AskAiState
import com.example.ai.AskAiViewModel
import com.example.ai.SaveKeyResult
import com.example.ui.viewmodel.RasmViewModel

/**
 * Read-only "Ask AI" assistant. Every user enters their OWN Google AI Studio API key
 * (stored encrypted on this device only).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AskAiScreen(
    rasm: RasmViewModel,
    aiViewModel: AskAiViewModel = viewModel()
) {
    val context = LocalContext.current
    val state by aiViewModel.state.collectAsState()

    // Collect so the flows stay active and hold fresh data when the user taps "Ask"
    val settings by rasm.settings.collectAsState()
    val officeBearers by rasm.officeBearers.collectAsState()
    val members by rasm.members.collectAsState()
    val maintenance by rasm.maintenanceCollections.collectAsState()
    val eventCollections by rasm.eventCollections.collectAsState()
    val events by rasm.events.collectAsState()
    val bankTransactions by rasm.bankTransactions.collectAsState()
    val donations by rasm.donations.collectAsState()
    val expenses by rasm.generalExpenses.collectAsState()
    val meetings by rasm.meetings.collectAsState()
    val isAdmin by rasm.isAdmin.collectAsState()
    val me by rasm.currentMember.collectAsState()

    var question by remember { mutableStateOf("") }
    var showKeyEditor by remember { mutableStateOf(false) }

    fun ask(q: String) {
        val data = AiContextBuilder.build(
            settings = settings,
            officeBearers = officeBearers,
            members = members,
            maintenance = maintenance,
            eventCollections = eventCollections,
            events = events,
            bankTransactions = bankTransactions,
            donations = donations,
            expenses = expenses,
            meetings = meetings,
            isAdmin = isAdmin,
            me = me
        )
        aiViewModel.ask(q, data)
    }

    val s = state

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Ask AI", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
                Text(
                    if (isAdmin)
                        "Ask about dues, finances, events and members. As an admin, member names, phone numbers and dues are sent to Google's Gemini service to answer you."
                    else
                        "Ask about your own dues, events and association finances. Only your own record and association totals are sent to Google's Gemini service.",
                    fontSize = 13.sp
                )
                Text(
                    "The assistant only reads data; it cannot change anything. AI can make mistakes, so double-check important figures in the app.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                )
            }
        }

        if (s is AskAiState.KeySetupRequired || showKeyEditor) {
            KeySetupCard(
                isReplacing = s !is AskAiState.KeySetupRequired,
                onSave = { input ->
                    when (val r = aiViewModel.saveKey(input)) {
                        is SaveKeyResult.Success -> {
                            showKeyEditor = false
                            null
                        }
                        is SaveKeyResult.Error -> r.message
                    }
                },
                onCancel = if (s is AskAiState.KeySetupRequired) null else ({ showKeyEditor = false }),
                onOpenAiStudio = {
                    try {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/apikey"))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    } catch (_: Exception) {
                    }
                }
            )
        }

        if (s !is AskAiState.KeySetupRequired) {
            OutlinedTextField(
                value = question,
                onValueChange = { question = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Your question") },
                minLines = 2,
                maxLines = 5
            )

            val suggestions = if (isAdmin) {
                listOf(
                    "Who has pending dues?",
                    "Summarize our finances",
                    "Which events are coming up?",
                    "How many members do we have?"
                )
            } else {
                listOf(
                    "What is my dues status?",
                    "Summarize the association finances",
                    "Which events are coming up?"
                )
            }
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                suggestions.forEach { text ->
                    AssistChip(
                        onClick = {
                            question = text
                            ask(text)
                        },
                        label = { Text(text, fontSize = 12.sp) }
                    )
                }
            }

            Button(
                onClick = { ask(question) },
                enabled = question.isNotBlank() && s !is AskAiState.Processing,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Send, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Ask")
            }

            when (s) {
                is AskAiState.Processing -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(s.message)
                    }
                }
                is AskAiState.Answer -> {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(s.question, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = MaterialTheme.colorScheme.outline)
                            SelectionContainer {
                                Text(cleanAiText(s.text), fontSize = 15.sp)
                            }
                            Text(
                                "Model: ${s.model}" + if (s.geminiFallback) " (Gemini fallback)" else "",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
                is AskAiState.Error -> {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Text(
                            s.message,
                            modifier = Modifier.padding(14.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontSize = 14.sp
                        )
                    }
                }
                else -> Unit
            }

            // Key management
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Your API key", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text(
                        aiViewModel.keyManager.getMaskedApiKey() + "  (stored encrypted on this device only)",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { showKeyEditor = true }) { Text("Replace key") }
                        TextButton(onClick = { aiViewModel.removeKey() }) {
                            Text("Remove key", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }

    if (s is AskAiState.NeedsApproval) {
        AlertDialog(
            onDismissRequest = { aiViewModel.reset() },
            title = { Text("Use a Gemini model?") },
            text = { Text(s.message, fontSize = 14.sp) },
            confirmButton = {
                Button(onClick = { aiViewModel.approveGeminiFallback() }) { Text("Use Gemini once") }
            },
            dismissButton = {
                TextButton(onClick = { aiViewModel.reset() }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun KeySetupCard(
    isReplacing: Boolean,
    onSave: (String) -> String?,
    onCancel: (() -> Unit)?,
    onOpenAiStudio: () -> Unit
) {
    var keyInput by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                if (isReplacing) "Replace your API key" else "Add your own Google AI Studio key",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
            Text(
                "Every member uses their own free key, so nobody shares limits or costs. Create one at aistudio.google.com/apikey, then paste it here. The key is encrypted on this phone and is only sent to Google.",
                fontSize = 13.sp
            )
            TextButton(onClick = onOpenAiStudio) { Text("Open Google AI Studio") }
            OutlinedTextField(
                value = keyInput,
                onValueChange = {
                    keyInput = it
                    error = null
                },
                label = { Text("API key") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                isError = error != null,
                supportingText = { error?.let { Text(it) } },
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { error = onSave(keyInput) }, enabled = keyInput.isNotBlank()) {
                    Text("Save key")
                }
                if (onCancel != null) {
                    TextButton(onClick = onCancel) { Text("Cancel") }
                }
            }
        }
    }
}

/** The model often answers with markdown; show it as clean plain text. */
private fun cleanAiText(raw: String): String =
    raw.replace("**", "")
        .replace(Regex("(?m)^\\s*[*-]\\s+"), "• ")
        .replace(Regex("(?m)^#{1,6}\\s*"), "")
        .trim()
