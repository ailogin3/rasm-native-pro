package com.example.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.FamilyMember
import com.example.data.model.Member
import com.example.ui.components.FullScreenPhotoViewer
import com.example.ui.viewmodel.RasmViewModel
import com.example.util.ImageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FamilyScreen(viewModel: RasmViewModel) {
    val context = LocalContext.current
    val members by viewModel.members.collectAsState()
    val isAdmin by viewModel.isAdmin.collectAsState()
    val me by viewModel.currentMember.collectAsState()

    var query by remember { mutableStateOf("") }
    var zoom by remember { mutableStateOf<Pair<String?, String>?>(null) }

    // Editor state: which member's family is being edited, and which entry (null = adding a new one)
    var editorOwner by remember { mutableStateOf<Member?>(null) }
    var editorEntry by remember { mutableStateOf<FamilyMember?>(null) }
    var deleteOwner by remember { mutableStateOf<Member?>(null) }
    var deleteEntry by remember { mutableStateOf<FamilyMember?>(null) }

    fun saveFamily(owner: Member, newList: List<FamilyMember>) {
        // Photos now live in Cloud Storage — the Firestore doc only holds short download
        // URLs, so the old "too many/large base64 photos" size guard no longer applies.
        viewModel.updateMember(owner.copy(family = newList)) {}
    }

    val others = remember(members, me, query) {
        members
            .filter { it.docId != me?.docId }
            .filter {
                query.isBlank() ||
                        it.name.contains(query, ignoreCase = true) ||
                        it.contact.contains(query) ||
                        it.family.any { f -> f.name.contains(query, ignoreCase = true) }
            }
            .sortedBy { it.name.lowercase() }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            val totalPeople = members.size + members.sumOf { it.family.size }
            Text(
                "${members.size} members  •  ${members.sumOf { it.family.size }} family members  •  $totalPeople people",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.outline
            )
        }

        if (me != null) {
            item {
                Text("My Family", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            }
            item {
                FamilyOwnerCard(
                    owner = me!!,
                    canEdit = true,
                    onAdd = { editorOwner = me; editorEntry = null },
                    onEdit = { f -> editorOwner = me; editorEntry = f },
                    onDelete = { f -> deleteOwner = me; deleteEntry = f },
                    onZoom = { photo, name -> zoom = Pair(photo, name) }
                )
            }
        } else if (!isAdmin) {
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                ) {
                    Text(
                        "Your login email is not linked to a member record yet. Ask an admin to add your email to your member profile, then your family will appear here.",
                        modifier = Modifier.padding(14.dp),
                        fontSize = 14.sp
                    )
                }
            }
        }

        if (isAdmin) {
            item {
                Text("All Families", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            }
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search member or family member") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true
                )
            }
            items(others, key = { it.docId }) { owner ->
                FamilyOwnerCard(
                    owner = owner,
                    canEdit = true,
                    onAdd = { editorOwner = owner; editorEntry = null },
                    onEdit = { f -> editorOwner = owner; editorEntry = f },
                    onDelete = { f -> deleteOwner = owner; deleteEntry = f },
                    onZoom = { photo, name -> zoom = Pair(photo, name) }
                )
            }
        }
        item { Spacer(modifier = Modifier.height(24.dp)) }
    }

    // Add / edit dialog
    val owner = editorOwner
    if (owner != null) {
        val existing = editorEntry
        FamilyMemberEditorDialog(
            ownerName = owner.name,
            ownerKey = owner.docId.ifBlank { owner.contact },
            existing = existing,
            onDismiss = { editorOwner = null; editorEntry = null },
            onSave = { saved ->
                val newList = if (existing == null) {
                    owner.family + saved
                } else {
                    owner.family.map { if (it.id == existing.id) saved else it }
                }
                saveFamily(owner, newList)
                editorOwner = null
                editorEntry = null
            }
        )
    }

    // Delete confirmation
    val dOwner = deleteOwner
    val dEntry = deleteEntry
    if (dOwner != null && dEntry != null) {
        AlertDialog(
            onDismissRequest = { deleteOwner = null; deleteEntry = null },
            title = { Text("Remove family member?") },
            text = { Text("${dEntry.name} (${dEntry.relation}) will be removed from ${dOwner.name}'s family.") },
            confirmButton = {
                TextButton(onClick = {
                    saveFamily(dOwner, dOwner.family.filter { it.id != dEntry.id })
                    deleteOwner = null
                    deleteEntry = null
                }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleteOwner = null; deleteEntry = null }) { Text("Cancel") }
            }
        )
    }

    zoom?.let { z ->
        FullScreenPhotoViewer(photo = z.first, title = z.second, onDismiss = { zoom = null })
    }
}

@Composable
private fun FamilyOwnerCard(
    owner: Member,
    canEdit: Boolean,
    onAdd: () -> Unit,
    onEdit: (FamilyMember) -> Unit,
    onDelete: (FamilyMember) -> Unit,
    onZoom: (String?, String) -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PhotoAvatar(photo = owner.photo, name = owner.name, size = 46, onClick = onZoom)
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(owner.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(
                        "${owner.family.size} family member${if (owner.family.size == 1) "" else "s"}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                if (canEdit) {
                    FilledTonalButton(onClick = onAdd) {
                        Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add")
                    }
                }
            }

            if (owner.family.isNotEmpty()) {
                HorizontalDivider()
            }
            owner.family.forEach { f ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PhotoAvatar(photo = f.photo.ifBlank { null }, name = f.name, size = 40, onClick = onZoom)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(f.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text(f.relation, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (canEdit) {
                        IconButton(onClick = { onEdit(f) }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(onClick = { onDelete(f) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PhotoAvatar(
    photo: String?,
    name: String,
    size: Int,
    onClick: (String?, String) -> Unit
) {
    if (!photo.isNullOrBlank()) {
        AsyncImage(
            model = ImageUtils.toImageModel(photo),
            contentDescription = name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(size.dp)
                .clip(CircleShape)
                .clickable { onClick(photo, name) }
        )
    } else {
        Box(
            modifier = Modifier
                .size(size.dp)
                .clip(CircleShape)
                .background(Color(0xFFE0F2F1)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = name.take(1).uppercase(),
                fontWeight = FontWeight.Bold,
                fontSize = (size / 2.4).sp,
                color = Color(0xFF006A60)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FamilyMemberEditorDialog(
    ownerName: String,
    ownerKey: String,
    existing: FamilyMember?,
    onDismiss: () -> Unit,
    onSave: (FamilyMember) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf(existing?.name ?: "") }
    var relation by remember { mutableStateOf(existing?.relation ?: "") }
    var photo by remember { mutableStateOf(existing?.photo ?: "") }
    var processing by remember { mutableStateOf(false) }
    val familyMemberId = remember { existing?.id?.ifBlank { null } ?: UUID.randomUUID().toString() }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) {
            processing = true
            scope.launch(Dispatchers.IO) {
                try {
                    val url = ImageUtils.uploadPhotoToStorage(
                        uri = uri,
                        context = context,
                        storagePath = ImageUtils.familyPhotoPath(ownerKey, familyMemberId),
                        maxDimension = 320
                    )
                    val oldPhoto = photo
                    withContext(Dispatchers.Main) {
                        processing = false
                        photo = url
                    }
                    if (oldPhoto.isNotBlank() && oldPhoto != url) {
                        ImageUtils.deleteFromStorage(oldPhoto)
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        processing = false
                        Toast.makeText(context, "Couldn't upload photo: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Add family member" else "Edit family member") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("For $ownerName", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable {
                                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (photo.isNotBlank()) {
                            AsyncImage(
                                model = ImageUtils.toImageModel(photo),
                                contentDescription = "Photo",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(Icons.Default.AddAPhoto, contentDescription = "Add photo")
                        }
                        if (processing) CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("Tap the circle to add a photo", fontSize = 12.sp)
                        if (photo.isNotBlank()) {
                            TextButton(onClick = { photo = "" }) { Text("Remove photo") }
                        }
                    }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = relation,
                    onValueChange = { relation = it },
                    label = { Text("Relation") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("Spouse", "Son", "Daughter", "Father", "Mother", "Other").forEach { r ->
                        AssistChip(onClick = { relation = r }, label = { Text(r, fontSize = 12.sp) })
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = name.isNotBlank() && relation.isNotBlank() && !processing,
                onClick = {
                    onSave(
                        FamilyMember(
                            id = familyMemberId,
                            name = name.trim(),
                            relation = relation.trim(),
                            photo = photo
                        )
                    )
                }
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
