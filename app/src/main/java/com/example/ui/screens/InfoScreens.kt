package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.viewmodel.RasmViewModel

// ------------------------------------------------------------------------------------
// Help & FAQ
// ------------------------------------------------------------------------------------

private data class FaqItem(val question: String, val answer: String)

private val FAQ_ITEMS = listOf(
    FaqItem(
        "How do I sign in?",
        "Use the email and password you registered with. If you are new, choose Create Account on the sign-in screen. If your email is not yet on your member profile, ask an office bearer to add it so your dues and family details appear in the app."
    ),
    FaqItem(
        "What is the difference between an Admin and a Member?",
        "Admins (office bearers granted access in Settings) can add or edit members, record dues, donations, expenses and bank entries, manage meetings and approve service listings. Members can view association information and manage their own family details."
    ),
    FaqItem(
        "How do I check my maintenance dues?",
        "Open the Dues tab and use Check Dues. You can also ask the AI assistant \"What is my dues status?\" once you have added your own API key."
    ),
    FaqItem(
        "How do payments and receipts work?",
        "Payments are recorded by an admin after you pay (cash, UPI or bank transfer). A numbered receipt is generated and can be shared with you on WhatsApp."
    ),
    FaqItem(
        "How do I add or change my family members and photos?",
        "Open the menu (three dots) and choose My Family. Tap Add to add someone, or the pencil icon to edit. Tap the round photo to add a picture. Tap any photo in the app to view it full screen and pinch to zoom."
    ),
    FaqItem(
        "Where can I see meeting minutes?",
        "Open Meetings & Minutes from the menu. Admins can attach photos of scanned minutes in the Photos tab. Tap a photo to zoom in and read it."
    ),
    FaqItem(
        "How do I list my business or skill in the Services Directory?",
        "Open Services Directory and use the List Your Business button. It appears to everyone once an admin approves it."
    ),
    FaqItem(
        "What is Ask AI and what does it cost?",
        "Ask AI answers questions about dues, finances and events. Every user adds their own free Google AI Studio API key (aistudio.google.com/apikey), which is stored encrypted on that phone. The assistant is read-only and can make mistakes, so verify important figures."
    ),
    FaqItem(
        "Can an admin back up the data?",
        "Yes. In Settings, admins can export a JSON backup of all association data and restore it later. Keep backup files safe because they contain member details and photos."
    ),
    FaqItem(
        "I forgot my password.",
        "On the sign-in screen enter your email and tap Forgot password?. A reset link is sent to your registered email."
    ),
    FaqItem(
        "Photos are not uploading.",
        "Check your internet connection and try a smaller photo. If it still fails, note the message shown at the bottom of the screen and share it with an admin."
    )
)

@Composable
fun HelpScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Frequently asked questions", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        FAQ_ITEMS.forEach { item -> FaqCard(item) }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun FaqCard(item: FaqItem) {
    var expanded by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .animateContentSize(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    item.question,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null
                )
            }
            if (expanded) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(item.answer, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ------------------------------------------------------------------------------------
// Contact Us
// ------------------------------------------------------------------------------------

@Composable
fun ContactScreen(viewModel: RasmViewModel) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsState()
    val bearers by viewModel.officeBearers.collectAsState()

    val people = listOf(
        Triple("President", bearers.presidentName, bearers.presidentContact),
        Triple("Vice President", bearers.vicePresidentName, bearers.vicePresidentContact),
        Triple("Secretary", bearers.secretaryName, bearers.secretaryContact),
        Triple("Joint Secretary", bearers.jointSecretaryName, bearers.jointSecretaryContact),
        Triple("Treasurer", bearers.treasurerName, bearers.treasurerContact),
        Triple("Patron", bearers.patronName, bearers.patronContact)
    ).filter { it.second.isNotBlank() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(settings.associationName, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                if (settings.associationLocation.isNotBlank()) {
                    Text(settings.associationLocation, fontSize = 14.sp)
                }
                if (settings.associationRegNo.isNotBlank()) {
                    Text("Reg. No: ${settings.associationRegNo}", fontSize = 13.sp)
                }
            }
        }

        Text("Office bearers", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)

        if (people.isEmpty()) {
            Text(
                "No office bearer details have been added yet. An admin can add them in Settings.",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.outline
            )
        }

        people.forEach { (role, name, contact) ->
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(role, fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                        Text(name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        if (contact.isNotBlank()) Text(contact, fontSize = 13.sp)
                    }
                    if (contact.isNotBlank()) {
                        IconButton(onClick = {
                            try {
                                context.startActivity(
                                    Intent(Intent.ACTION_DIAL, Uri.parse("tel:${contact.trim()}"))
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            } catch (_: Exception) {
                            }
                        }) {
                            Icon(Icons.Default.Call, contentDescription = "Call", tint = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(onClick = { viewModel.sendWhatsAppMessage(contact, "", context) }) {
                            Icon(Icons.Default.Chat, contentDescription = "WhatsApp", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }

        if (bearers.committeeMembers.isNotBlank()) {
            Text("Committee members", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Text(bearers.committeeMembers, fontSize = 14.sp)
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

// ------------------------------------------------------------------------------------
// Privacy Policy
// ------------------------------------------------------------------------------------

@Composable
fun PrivacyPolicyScreen(viewModel: RasmViewModel) {
    val settings by viewModel.settings.collectAsState()
    val org = settings.associationName.ifBlank { "the association" }

    val sections = listOf(
        "Who runs this app" to
                "This app is operated by $org for its members. \"We\" below means $org and its office bearers.",
        "What information we keep" to
                "Member records: name, phone number, email, gender, membership type, joining date and profile photo. Family details: names, relations and photos that a member or admin adds. Payments: maintenance dues, event fees, donations and receipt numbers. Association records: events, expenses, bank entries, meeting minutes and photos of scanned minutes, service listings, and an activity log of admin actions. Account data: your sign-in email.",
        "Why we keep it" to
                "To run the membership: recording dues and payments, sending receipts and reminders, organising events and meetings, keeping the member directory and family list, and keeping accounts transparent to the members.",
        "Where it is stored" to
                "Data is stored in a Firebase project (Google Cloud) that belongs to the association. Sign-in uses Firebase Authentication. Photos are stored inside the records they belong to, not on a public web page.",
        "Who can see it" to
                "Admins (office bearers granted access) can see and manage all association records. Members can see the information the app shows them, such as the directory, events and association finances, and can manage their own family details. We do not sell your information and we do not show advertisements.",
        "WhatsApp and calls" to
                "Receipts and reminders are shared through WhatsApp only when an admin or you choose to send them. That sharing happens outside this app and is covered by WhatsApp's own terms.",
        "Ask AI feature (optional)" to
                "If you add your own Google AI Studio API key, your question and a summary of association data are sent to Google's Gemini service to produce an answer. For normal members that summary contains only their own record and association totals. For admins it also includes member names, phone numbers and dues. Your key is encrypted on your phone and is never stored by the association. You can remove it at any time in the Ask AI screen. Google's terms and privacy policy apply to data sent to its service.",
        "Backups" to
                "Admins can export a JSON backup that contains member details and photos. Whoever holds that file is responsible for keeping it safe and deleting it when no longer needed.",
        "How long we keep it" to
                "We keep records for as long as you are a member and as long as the association needs them for its accounts. You can ask an office bearer to correct or remove your personal details, subject to any records the association must retain.",
        "Your choices" to
                "You can ask to see, correct or delete your details, and you can remove family photos or your own photo at any time. Contact an office bearer using the Contact Us screen.",
        "Children" to
                "The app is meant for adult members. Family entries may include children's names and photos, added by a parent or guardian who is a member.",
        "Changes to this policy" to
                "We may update this policy when the app changes. The latest version is always available in the app under Privacy Policy.",
        "Contact" to
                "For any privacy question, use the Contact Us screen to reach the office bearers of $org."
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Privacy Policy", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineSmall)
        Text("Last updated: September 2026", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
        sections.forEach { (title, body) ->
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Text(body, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}
