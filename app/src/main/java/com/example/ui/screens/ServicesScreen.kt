package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ServiceListing
import com.example.ui.viewmodel.RasmViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServicesScreen(viewModel: RasmViewModel) {
    val listings by viewModel.serviceListings.collectAsState()
    val isAdmin by viewModel.isAdmin.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()
    val currentMember by viewModel.currentMember.collectAsState()
    val context = LocalContext.current

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("ALL") }
    var showSubmitDialog by remember { mutableStateOf(false) }

    val categories = listOf(
        "ALL" to "All Trades",
        "Civil & Construction" to "Civil & Construction",
        "IT & Software" to "IT & Software",
        "Electrician & Plumbing" to "Electrical & Plumbing",
        "Healthcare & Nursing" to "Healthcare",
        "Catering & Events" to "Catering & Events",
        "Automobile & Repair" to "Automobile",
        "Tutoring & Education" to "Education",
        "Financial & Legal" to "Financial & Legal",
        "Other" to "Other"
    )

    val visibleListings = remember(listings, searchQuery, selectedCategory, isAdmin, currentUser) {
        listings.filter { item ->
            // Normal users see approved listings or their own submitted listings
            val canSee = isAdmin || item.status == "Approved" || (item.submittedByEmail.isNotBlank() && item.submittedByEmail.equals(currentUser?.email, ignoreCase = true))

            val matchesCat = selectedCategory == "ALL" || item.category == selectedCategory
            val matchesQuery = searchQuery.isBlank() ||
                    item.businessName.contains(searchQuery, ignoreCase = true) ||
                    item.contactPerson.contains(searchQuery, ignoreCase = true) ||
                    item.description.contains(searchQuery, ignoreCase = true) ||
                    item.contactNumber.contains(searchQuery)

            canSee && matchesCat && matchesQuery
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showSubmitDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("add_service_fab")
            ) {
                Icon(Icons.Default.AddBusiness, contentDescription = "List Your Business")
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
                text = "Member Business & Trade Directory",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Support your community members' businesses & trades",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Search Box
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search services, plumbers, tutors, IT...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("service_search_input"),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Category Carousel
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                items(categories) { (catKey, catLabel) ->
                    FilterChip(
                        selected = selectedCategory == catKey,
                        onClick = { selectedCategory = catKey },
                        label = { Text(catLabel, fontSize = 12.sp) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (visibleListings.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No services listed under this category yet.", color = MaterialTheme.colorScheme.outline)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(visibleListings, key = { it.docId }) { item ->
                        ServiceListingCard(
                            item = item,
                            isAdmin = isAdmin,
                            onApprove = { viewModel.updateServiceListingStatus(item.docId, item.businessName, true) },
                            onReject = { viewModel.updateServiceListingStatus(item.docId, item.businessName, false) },
                            onDelete = { viewModel.deleteServiceListing(item.docId, item.businessName) },
                            onCall = {
                                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${item.contactNumber}"))
                                context.startActivity(intent)
                            },
                            onWhatsApp = {
                                viewModel.sendWhatsAppMessage(
                                    item.contactNumber,
                                    "Hello ${item.contactPerson}, I found your service (${item.businessName}) on the Residents Association App.",
                                    context
                                )
                            }
                        )
                    }
                }
            }
        }
    }

    if (showSubmitDialog) {
        SubmitListingDialog(
            defaultName = currentMember?.name ?: "",
            defaultContact = currentMember?.contact ?: "",
            defaultEmail = currentUser?.email ?: "",
            onDismiss = { showSubmitDialog = false },
            onSubmit = { newListing ->
                viewModel.submitServiceListing(newListing) {
                    showSubmitDialog = false
                }
            }
        )
    }
}

@Composable
fun ServiceListingCard(
    item: ServiceListing,
    isAdmin: Boolean,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    onDelete: () -> Unit,
    onCall: () -> Unit,
    onWhatsApp: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth().testTag("service_card_${item.docId}")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(item.businessName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Surface(
                    color = when (item.status) {
                        "Approved" -> Color(0xFFE8F5E9)
                        "Rejected" -> Color(0xFFFFEBEE)
                        else -> Color(0xFFFFF3E0)
                    },
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = item.status,
                        color = when (item.status) {
                            "Approved" -> Color(0xFF2E7D32)
                            "Rejected" -> Color(0xFFC62828)
                            else -> Color(0xFFE65100)
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                Text(
                    text = item.category,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = item.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Contact: ${item.contactPerson} • ${item.contactNumber}",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onCall,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Call", fontSize = 12.sp)
                }
                Button(
                    onClick = onWhatsApp,
                    modifier = Modifier.weight(1.2f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366))
                ) {
                    Icon(Icons.Default.Chat, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("WhatsApp", fontSize = 12.sp)
                }
            }

            if (isAdmin) {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (item.status != "Approved") {
                        TextButton(onClick = onApprove) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Approve", color = Color(0xFF2E7D32))
                        }
                    }
                    if (item.status != "Rejected") {
                        TextButton(onClick = onReject) {
                            Icon(Icons.Default.Close, contentDescription = null, tint = Color(0xFFC62828), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Reject", color = Color(0xFFC62828))
                        }
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
fun SubmitListingDialog(
    defaultName: String,
    defaultContact: String,
    defaultEmail: String,
    onDismiss: () -> Unit,
    onSubmit: (ServiceListing) -> Unit
) {
    var bName by remember { mutableStateOf("") }
    var cPerson by remember { mutableStateOf(defaultName) }
    var cNumber by remember { mutableStateOf(defaultContact) }
    var category by remember { mutableStateOf("Civil & Construction") }
    var description by remember { mutableStateOf("") }

    val categories = listOf(
        "Civil & Construction",
        "IT & Software",
        "Electrician & Plumbing",
        "Healthcare & Nursing",
        "Catering & Events",
        "Automobile & Repair",
        "Tutoring & Education",
        "Financial & Legal",
        "Other"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("List Trade / Business") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = bName,
                    onValueChange = { bName = it },
                    label = { Text("Business / Service Name *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = cPerson,
                    onValueChange = { cPerson = it },
                    label = { Text("Contact Person *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = cNumber,
                    onValueChange = { cNumber = it },
                    label = { Text("Phone Number *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Brief Description of Services *") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (bName.isNotBlank() && cNumber.isNotBlank() && description.isNotBlank()) {
                        onSubmit(
                            ServiceListing(
                                businessName = bName.trim(),
                                contactPerson = cPerson.trim(),
                                contactNumber = cNumber.trim(),
                                category = category,
                                description = description.trim(),
                                submittedByEmail = defaultEmail
                            )
                        )
                    }
                }
            ) {
                Text("Submit for Review")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
