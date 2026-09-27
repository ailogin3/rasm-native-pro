package com.example.ui.screens

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import com.example.data.model.Member
import com.example.ui.viewmodel.RasmViewModel
import com.example.util.CsvUtil
import com.example.util.DuesInfo
import com.example.util.ImageUtils
import com.example.util.MemberImport
import com.example.util.MemberImportPreview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Admin-only "Import / Export" menu for the Members screen:
 * import members from a CSV file, import photos in bulk, export members to CSV, save a sample file.
 */
@Composable
fun MemberBulkTools(
    viewModel: RasmViewModel,
    members: List<Member>,
    duesByMember: Map<String, DuesInfo>
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var menuOpen by remember { mutableStateOf(false) }
    var showImportHelp by remember { mutableStateOf(false) }
    var showPhotoHelp by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<MemberImportPreview?>(null) }
    var importProgress by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var importResult by remember { mutableStateOf<String?>(null) }
    var photoStatus by remember { mutableStateOf<String?>(null) }
    var photoResult by remember { mutableStateOf<String?>(null) }
    var pendingCsv by remember { mutableStateOf<String?>(null) }

    val saveCsvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? ->
        val text = pendingCsv
        if (uri != null && text != null) {
            val result = CsvUtil.writeToUri(context, uri, text)
            Toast.makeText(
                context,
                if (result.isSuccess) "CSV saved" else "Couldn't save the CSV: ${result.exceptionOrNull()?.message}",
                Toast.LENGTH_LONG
            ).show()
        }
        pendingCsv = null
    }

    val csvPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val read = CsvUtil.readFromUri(context, uri)
            read.onSuccess { text ->
                val existing = members.map { MemberImport.contactKey(it.contact) }.toSet()
                preview = MemberImport.parse(text, existing)
            }.onFailure {
                Toast.makeText(context, "Couldn't read the file: ${it.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            scope.launch {
                var matched = 0
                val unmatched = mutableListOf<String>()
                val failed = mutableListOf<String>()
                uris.forEachIndexed { index, uri ->
                    photoStatus = "Photo ${index + 1} of ${uris.size}..."
                    val fileName = displayNameOf(context, uri)
                    val member = matchMemberForPhoto(fileName, members)
                    if (member == null) {
                        unmatched.add(fileName)
                    } else {
                        val uploaded = withContext(Dispatchers.IO) {
                            runCatching {
                                ImageUtils.uploadPhotoToStorage(uri, context, ImageUtils.memberPhotoPath(member.docId))
                            }.getOrNull()
                        }
                        if (uploaded != null && viewModel.setMemberPhoto(member, uploaded)) matched++ else failed.add(fileName)
                    }
                }
                photoStatus = null
                photoResult = buildString {
                    append("$matched of ${uris.size} photos were added to members.")
                    if (unmatched.isNotEmpty()) {
                        append("\n\nNo single matching member for:\n")
                        append(unmatched.take(10).joinToString("\n") { "- $it" })
                        if (unmatched.size > 10) append("\n...and ${unmatched.size - 10} more")
                    }
                    if (failed.isNotEmpty()) {
                        append("\n\nCould not save:\n")
                        append(failed.take(10).joinToString("\n") { "- $it" })
                    }
                }
            }
        }
    }

    val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    Box {
        OutlinedButton(onClick = { menuOpen = true }) {
            Icon(Icons.Default.TableChart, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Import / Export")
            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text("Import members (CSV)") },
                leadingIcon = { Icon(Icons.Default.FileUpload, contentDescription = null) },
                onClick = {
                    menuOpen = false
                    showImportHelp = true
                }
            )
            DropdownMenuItem(
                text = { Text("Import photos") },
                leadingIcon = { Icon(Icons.Default.PhotoLibrary, contentDescription = null) },
                onClick = {
                    menuOpen = false
                    showPhotoHelp = true
                }
            )
            DropdownMenuItem(
                text = { Text("Export members (CSV)") },
                leadingIcon = { Icon(Icons.Default.FileDownload, contentDescription = null) },
                onClick = {
                    menuOpen = false
                    pendingCsv = buildMembersCsv(members, duesByMember)
                    saveCsvLauncher.launch("members_$today.csv")
                }
            )
            DropdownMenuItem(
                text = { Text("Save sample import file") },
                leadingIcon = { Icon(Icons.Default.Description, contentDescription = null) },
                onClick = {
                    menuOpen = false
                    pendingCsv = MemberImport.SAMPLE_CSV
                    saveCsvLauncher.launch("members_import_sample.csv")
                }
            )
        }
    }

    if (showImportHelp) {
        AlertDialog(
            onDismissRequest = { showImportHelp = false },
            title = { Text("Import members") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Choose a CSV file (Excel: File > Save as > CSV). The first row must be a header.", fontSize = 13.sp)
                    Text("Columns: name*, contact*, email, member type (OM / LM / HM), gender (Male / Female), join date, paid upto.", fontSize = 13.sp)
                    Text("'Paid upto' (for example June 2026) sets the member as paid up to that month, like Set Paid-Upto.", fontSize = 13.sp)
                    Text("Members whose phone number already exists are skipped, so nothing is overwritten. Nothing is saved until you confirm the preview.", fontSize = 13.sp)
                    Text("Tip: use 'Save sample import file' in this menu to get a ready template.", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                }
            },
            confirmButton = {
                Button(onClick = {
                    showImportHelp = false
                    csvPicker.launch(arrayOf("*/*"))
                }) { Text("Choose file") }
            },
            dismissButton = { TextButton(onClick = { showImportHelp = false }) { Text("Cancel") } }
        )
    }

    if (showPhotoHelp) {
        AlertDialog(
            onDismissRequest = { showPhotoHelp = false },
            title = { Text("Import photos") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Name each photo file with the member's 10-digit phone number (9876543210.jpg) or full name (Priya Sharma.jpg).", fontSize = 13.sp)
                    Text("Photos are matched to existing members and replace their current photo. If two members share a phone number, use the name instead.", fontSize = 13.sp)
                }
            },
            confirmButton = {
                Button(onClick = {
                    showPhotoHelp = false
                    photoPicker.launch(arrayOf("image/*"))
                }) { Text("Choose photos") }
            },
            dismissButton = { TextButton(onClick = { showPhotoHelp = false }) { Text("Cancel") } }
        )
    }

    preview?.let { p ->
        val fileError = p.fileError
        if (fileError != null) {
            AlertDialog(
                onDismissRequest = { preview = null },
                title = { Text("Can't import this file") },
                text = { Text(fileError) },
                confirmButton = { TextButton(onClick = { preview = null }) { Text("OK") } }
            )
        } else {
            val ready = p.ready
            val duplicates = p.duplicates
            val invalid = p.invalid
            val busy = importProgress != null
            AlertDialog(
                onDismissRequest = { if (!busy) preview = null },
                title = { Text("Import members") },
                text = {
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("${p.rows.size} rows read from the file", fontSize = 13.sp)
                        Text(
                            "${ready.size} ready to add",
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2E7D32)
                        )
                        if (ready.any { it.paidUpto != null }) {
                            Text(
                                "${ready.count { it.paidUpto != null }} of them also get a paid-upto month.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                        if (duplicates.isNotEmpty()) {
                            Text(
                                "${duplicates.size} skipped: phone number is already a member, or repeated in the file",
                                fontSize = 13.sp,
                                color = Color(0xFF8D6E00)
                            )
                        }
                        if (invalid.isNotEmpty()) {
                            Text(
                                "${invalid.size} skipped because of problems:",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFFC62828)
                            )
                            invalid.take(8).forEach {
                                Text("Line ${it.line}: ${it.problem}", fontSize = 12.sp)
                            }
                            if (invalid.size > 8) Text("...and ${invalid.size - 8} more", fontSize = 12.sp)
                        }
                        importProgress?.let { (done, total) ->
                            Spacer(modifier = Modifier.height(4.dp))
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            Text("Adding $done of $total...", fontSize = 12.sp)
                        }
                    }
                },
                confirmButton = {
                    Button(
                        enabled = ready.isNotEmpty() && !busy,
                        onClick = {
                            importProgress = Pair(0, ready.size)
                            viewModel.importMembers(
                                rows = ready,
                                onProgress = { done, total -> importProgress = Pair(done, total) },
                                onDone = { added, failures ->
                                    importProgress = null
                                    preview = null
                                    importResult = buildString {
                                        append("$added members added.")
                                        if (failures.isNotEmpty()) {
                                            append("\n\nProblems:\n")
                                            append(failures.take(8).joinToString("\n") { "- $it" })
                                            if (failures.size > 8) append("\n...and ${failures.size - 8} more")
                                        }
                                    }
                                }
                            )
                        }
                    ) { Text("Import ${ready.size}") }
                },
                dismissButton = {
                    TextButton(enabled = !busy, onClick = { preview = null }) { Text("Cancel") }
                }
            )
        }
    }

    importResult?.let { msg ->
        AlertDialog(
            onDismissRequest = { importResult = null },
            title = { Text("Import finished") },
            text = { Text(msg, fontSize = 13.sp) },
            confirmButton = { TextButton(onClick = { importResult = null }) { Text("OK") } }
        )
    }

    photoStatus?.let { status ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Importing photos") },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Text(status)
                }
            },
            confirmButton = {}
        )
    }

    photoResult?.let { msg ->
        AlertDialog(
            onDismissRequest = { photoResult = null },
            title = { Text("Photo import finished") },
            text = { Text(msg, fontSize = 13.sp) },
            confirmButton = { TextButton(onClick = { photoResult = null }) { Text("OK") } }
        )
    }
}

private fun buildMembersCsv(members: List<Member>, dues: Map<String, DuesInfo>): String {
    val header = listOf(
        "Name", "Contact", "Email", "Member Type", "Gender", "Join Date",
        "Family Members", "Paid Upto", "Months Due", "Amount Due (Rs)"
    )
    val rows = members.sortedBy { it.name.lowercase() }.map { m ->
        val d = dues[m.docId]
        listOf<Any?>(
            m.name,
            m.contact,
            m.email,
            m.memberType,
            m.gender,
            m.joinDate,
            m.family.joinToString("; ") { f -> if (f.relation.isBlank()) f.name else "${f.name} (${f.relation})" },
            d?.paidUpto ?: "",
            d?.monthsDue,
            d?.amountDue
        )
    }
    return CsvUtil.build(header, rows)
}

private fun displayNameOf(context: Context, uri: Uri): String {
    var name: String? = null
    try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) name = cursor.getString(0)
        }
    } catch (e: Exception) {
        // fall back to the last path segment below
    }
    return name ?: (uri.lastPathSegment ?: "photo")
}

/**
 * Finds the one member a photo file belongs to: by 10-digit phone number in the file name, else by full name.
 * Returns null when nothing matches or when more than one member would match (so a wrong photo is never saved).
 */
internal fun matchMemberForPhoto(fileName: String, members: List<Member>): Member? {
    val base = fileName.substringBeforeLast('.', fileName)
    val digits = base.filter { it.isDigit() }
    if (digits.length >= 10) {
        val key = digits.takeLast(10)
        val byPhone = members.filter { MemberImport.contactKey(it.contact) == key }
        if (byPhone.size == 1) return byPhone.first()
        if (byPhone.size > 1) return null
    }
    val normalized = base.lowercase().filter { it.isLetterOrDigit() }
    if (normalized.isBlank()) return null
    return members
        .filter { m -> m.name.lowercase().filter { it.isLetterOrDigit() } == normalized }
        .singleOrNull()
}
