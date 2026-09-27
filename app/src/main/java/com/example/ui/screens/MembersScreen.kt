package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.FamilyMember
import com.example.data.model.Member
import com.example.ui.viewmodel.RasmViewModel
import com.example.util.DuesCalculator
import com.example.util.DuesInfo
import com.example.util.ImageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MembersScreen(
    viewModel: RasmViewModel,
    initialFilter: String? = null
) {
    val members by viewModel.members.collectAsState()
    val maintenanceCollections by viewModel.maintenanceCollections.collectAsState()
    val eventCollections by viewModel.eventCollections.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val isAdmin by viewModel.isAdmin.collectAsState()
    val canManageMoney by viewModel.canManageMoney.collectAsState()
    val currentMember by viewModel.currentMember.collectAsState()
    val context = LocalContext.current

    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(initialFilter ?: "ALL") }

    var showAddDialog by remember { mutableStateOf(false) }
    var editingMember by remember { mutableStateOf<Member?>(null) }
    var viewingMember by remember { mutableStateOf<Member?>(null) }
    var memberToDelete by remember { mutableStateOf<Member?>(null) }
    var managingFamilyMember by remember { mutableStateOf<Member?>(null) }

    val filteredMembers = remember(members, searchQuery, selectedFilter) {
        members.filter { member ->
            val matchesQuery = searchQuery.isBlank() ||
                    member.name.contains(searchQuery, ignoreCase = true) ||
                    member.contact.contains(searchQuery) ||
                    member.email.contains(searchQuery, ignoreCase = true)

            val matchesFilter = when (selectedFilter) {
                "LM" -> member.isLifeMember
                "OM" -> member.isOrdinaryMember
                "HM" -> member.isHonoraryMember
                "WW" -> member.isWomensWing
                else -> true
            }

            matchesQuery && matchesFilter
        }
    }

    // Months / amount due per member, recalculated only when members, payments, fee or the month change.
    val currentMonthValue = DuesCalculator.currentMonthValue()
    val duesByMember = remember(members, maintenanceCollections, settings.monthlyFee, currentMonthValue, canManageMoney) {
        if (!canManageMoney) emptyMap<String, DuesInfo>()
        else members.associate { m ->
            m.docId to DuesCalculator.forMember(m, maintenanceCollections, settings.monthlyFee, currentMonthValue)
        }
    }

    Scaffold(
        floatingActionButton = {
            if (isAdmin) {
                FloatingActionButton(
                    onClick = { showAddDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.testTag("add_member_fab")
                ) {
                    Icon(Icons.Default.PersonAdd, contentDescription = "Add Member")
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
            // Search Box
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("member_search_input"),
                placeholder = { Text("Search by name, contact, email...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Filter Chips
            val filters = listOf(
                "ALL" to "All",
                "OM" to "Ordinary",
                "LM" to "Life",
                "HM" to "Honorary",
                "WW" to "Women's Wing"
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(filters) { (code, label) ->
                    FilterChip(
                        selected = selectedFilter == code,
                        onClick = { selectedFilter = code },
                        label = { Text(label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }

            if (isAdmin) {
                Spacer(modifier = Modifier.height(4.dp))
                MemberBulkTools(
                    viewModel = viewModel,
                    members = members,
                    duesByMember = duesByMember
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Count header
            Text(
                text = "${filteredMembers.size} members found",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Members List
            if (filteredMembers.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.SearchOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No members match your criteria",
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredMembers, key = { it.docId.ifBlank { it.contact } }) { member ->
                        val mCount = maintenanceCollections.count { it.memberId == member.docId }
                        val eCount = eventCollections.count { it.memberId == member.docId }

                        MemberCard(
                            member = member,
                            paymentCount = mCount + eCount,
                            isAdmin = isAdmin,
                            dues = duesByMember[member.docId],
                            onView = { viewingMember = member },
                            onEdit = { editingMember = member },
                            onDelete = { memberToDelete = member },
                            onCall = {
                                try {
                                    context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${member.contact.trim()}")))
                                } catch (_: Exception) {
                                }
                            },
                            onWhatsApp = {
                                viewModel.sendWhatsAppMessage(member.contact, "Hello ${member.name},", context)
                            }
                        )
                    }
                }
            }
        }
    }

    // Add Member Dialog
    if (showAddDialog) {
        MemberEditDialog(
            title = "Add New Member",
            member = null,
            context = context,
            onDismiss = { showAddDialog = false },
            onSave = { newMember ->
                viewModel.addMember(newMember) {
                    showAddDialog = false
                }
            }
        )
    }

    // Edit Member Dialog
    editingMember?.let { member ->
        MemberEditDialog(
            title = "Edit Member",
            member = member,
            context = context,
            onDismiss = { editingMember = null },
            onSave = { updated ->
                viewModel.updateMember(updated) {
                    editingMember = null
                }
            }
        )
    }

    // Delete Confirmation Dialog
    memberToDelete?.let { member ->
        AlertDialog(
            onDismissRequest = { memberToDelete = null },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Delete Member") },
            text = { Text("Are you sure you want to delete ${member.name}? This will remove the member record.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteMember(member.docId)
                        memberToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { memberToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // View Member Profile Dialog
    viewingMember?.let { member ->
        MemberProfileDialog(
            member = member,
            canEditFamily = isAdmin || (currentMember?.docId == member.docId),
            onManageFamily = {
                managingFamilyMember = member
            },
            onDismiss = { viewingMember = null },
            onCall = {
                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${member.contact}"))
                context.startActivity(intent)
            },
            onWhatsApp = {
                viewModel.sendWhatsAppMessage(member.contact, "Hello ${member.name},", context)
            }
        )
    }

    // Family Member Manager Dialog
    managingFamilyMember?.let { member ->
        FamilyManagerDialog(
            member = member,
            context = context,
            onDismiss = { managingFamilyMember = null },
            onSave = { updatedFamily ->
                val updated = member.copy(family = updatedFamily)
                viewModel.updateMember(updated) {
                    managingFamilyMember = null
                    if (viewingMember?.docId == member.docId) {
                        viewingMember = updated
                    }
                }
            }
        )
    }
}

@Composable
fun MemberCard(
    member: Member,
    paymentCount: Int,
    isAdmin: Boolean,
    onView: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onCall: () -> Unit = {},
    onWhatsApp: () -> Unit = {},
    dues: DuesInfo? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onView() }
            .testTag("member_card_${member.contact}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar / Photo
            if (!member.photo.isNullOrBlank()) {
                AsyncImage(
                    model = ImageUtils.toImageModel(member.photo),
                    contentDescription = member.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFE0F2F1)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = member.name.take(1).uppercase(),
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        color = Color(0xFF006A60)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Member Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = member.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = member.contact,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (member.email.isNotBlank()) {
                    Text(
                        text = member.email,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val badgeText = when (member.memberType) {
                        "LM" -> "Life Member"
                        "HM" -> "Honorary"
                        else -> "Ordinary"
                    }
                    val badgeColor = when (member.memberType) {
                        "LM" -> Color(0xFF283593)
                        "HM" -> Color(0xFFC62828)
                        else -> Color(0xFF006A60)
                    }
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = badgeColor.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = badgeText,
                            color = badgeColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    if (member.isWomensWing) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFFAD1457).copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = "Women's Wing",
                                color = Color(0xFFAD1457),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                // Dues status (admins only): "3 months due • ₹1,500"
                if (dues != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = DuesCalculator.dueText(dues),
                        color = when {
                            !dues.hasRecord -> Color(0xFF757575)
                            dues.monthsDue == 0 -> Color(0xFF2E7D32)
                            else -> Color(0xFFC62828)
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Quick actions: Call / WhatsApp for everyone, Edit / Delete for admins
            Column(horizontalAlignment = Alignment.End) {
                if (member.contact.isNotBlank()) {
                    Row {
                        IconButton(onClick = onCall, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.Default.Call, contentDescription = "Call ${member.name}", tint = Color(0xFF1565C0))
                        }
                        IconButton(onClick = onWhatsApp, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.Default.Chat, contentDescription = "WhatsApp ${member.name}", tint = Color(0xFF2E7D32))
                        }
                    }
                }
                if (isAdmin) {
                    Row {
                        IconButton(onClick = onEdit, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(onClick = onDelete, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemberEditDialog(
    title: String,
    member: Member?,
    context: Context,
    onDismiss: () -> Unit,
    onSave: (Member) -> Unit
) {
    var name by remember { mutableStateOf(member?.name ?: "") }
    var contact by remember { mutableStateOf(member?.contact ?: "") }
    var email by remember { mutableStateOf(member?.email ?: "") }
    var memberType by remember { mutableStateOf(member?.memberType ?: "OM") }
    var gender by remember { mutableStateOf(member?.gender ?: "Male") }
    var photoUrl by remember { mutableStateOf(member?.photo) }
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var isProcessingPhoto by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let {
            selectedUri = it
            isProcessingPhoto = true
            coroutineScope.launch(Dispatchers.IO) {
                val memberKey = (member?.docId ?: contact).ifBlank { "new_${System.currentTimeMillis()}" }
                try {
                    val url = ImageUtils.uploadPhotoToStorage(
                        uri = it,
                        context = context,
                        storagePath = ImageUtils.memberPhotoPath(memberKey)
                    )
                    withContext(Dispatchers.Main) {
                        photoUrl = url
                        isProcessingPhoto = false
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Couldn't upload photo: ${e.message}", Toast.LENGTH_LONG).show()
                        isProcessingPhoto = false
                    }
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Photo selector
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFE0F2F1))
                        .clickable {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    val previewModel = selectedUri ?: ImageUtils.toImageModel(photoUrl)
                    if (previewModel != null) {
                        AsyncImage(
                            model = previewModel,
                            contentDescription = "Selected Photo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            Icons.Default.AddAPhoto,
                            contentDescription = "Add Photo",
                            tint = Color(0xFF006A60),
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    if (isProcessingPhoto) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.4f)),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        }
                    }
                }
                Text("Tap to change photo", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Full Name *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = contact,
                    onValueChange = { contact = it },
                    label = { Text("Contact Number *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email Address") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Member Type selector
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("OM" to "Ordinary", "LM" to "Life", "HM" to "Honorary").forEach { (code, label) ->
                        FilterChip(
                            selected = memberType == code,
                            onClick = { memberType = code },
                            label = { Text(label, fontSize = 12.sp) }
                        )
                    }
                }

                // Gender selector
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Male" to "Male", "Female" to "Female (WW)").forEach { (code, label) ->
                        FilterChip(
                            selected = gender == code,
                            onClick = { gender = code },
                            label = { Text(label, fontSize = 12.sp) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank() && contact.isNotBlank()) {
                        val updated = (member ?: Member(
                            joinDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                        )).copy(
                            name = name.trim(),
                            contact = contact.trim(),
                            email = email.trim(),
                            memberType = memberType,
                            gender = gender,
                            photo = photoUrl
                        )
                        onSave(updated)
                    }
                }
            ) {
                Text("Save Member")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun MemberProfileDialog(
    member: Member,
    canEditFamily: Boolean,
    onManageFamily: () -> Unit,
    onDismiss: () -> Unit,
    onCall: () -> Unit,
    onWhatsApp: () -> Unit
) {
    var zoom by remember { mutableStateOf<Pair<String?, String>?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Badge, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Member Profile")
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!member.photo.isNullOrBlank()) {
                        AsyncImage(
                            model = ImageUtils.toImageModel(member.photo),
                            contentDescription = member.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .clickable { zoom = Pair(member.photo, member.name) }
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFE0F2F1)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = member.name.take(1).uppercase(),
                                fontWeight = FontWeight.Bold,
                                fontSize = 28.sp,
                                color = Color(0xFF006A60)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(text = member.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(text = member.contact, style = MaterialTheme.typography.bodyMedium)
                        if (member.email.isNotBlank()) {
                            Text(text = member.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onCall,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Call")
                    }
                    Button(
                        onClick = onWhatsApp,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366))
                    ) {
                        Icon(Icons.Default.Chat, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("WhatsApp")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Family Members (${member.family.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    if (canEditFamily) {
                        TextButton(onClick = onManageFamily) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Manage")
                        }
                    }
                }

                if (member.family.isEmpty()) {
                    Text(
                        text = "No family members listed yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        member.family.forEach { fam ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (fam.photo.isNotBlank()) {
                                    AsyncImage(
                                        model = ImageUtils.toImageModel(fam.photo),
                                        contentDescription = fam.name,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .clickable { zoom = Pair(fam.photo, fam.name) }
                                    )
                                } else {
                                    Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(fam.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                    Text(fam.relation, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )

    zoom?.let { z ->
        com.example.ui.components.FullScreenPhotoViewer(photo = z.first, title = z.second, onDismiss = { zoom = null })
    }
}

@Composable
fun FamilyManagerDialog(
    member: Member,
    context: Context,
    onDismiss: () -> Unit,
    onSave: (List<FamilyMember>) -> Unit
) {
    val familyList = remember { mutableStateListOf<FamilyMember>().apply { addAll(member.family) } }
    var newName by remember { mutableStateOf("") }
    var newRelation by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Family Members Editor") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Text("Add or remove family members for ${member.name}:", style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(10.dp))

                // New family member inputs
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = newRelation,
                    onValueChange = { newRelation = it },
                    label = { Text("Relation (Spouse, Child, Parent...)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        if (newName.isNotBlank() && newRelation.isNotBlank()) {
                            familyList.add(
                                FamilyMember(
                                    id = "fam_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(5)}",
                                    name = newName.trim(),
                                    relation = newRelation.trim()
                                )
                            )
                            newName = ""
                            newRelation = ""
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add to Family")
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))

                // Current items list
                LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
                    items(familyList) { fam ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(fam.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(fam.relation, fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                            }
                            IconButton(onClick = { familyList.remove(fam) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(familyList.toList()) }) {
                Text("Save Changes")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}


