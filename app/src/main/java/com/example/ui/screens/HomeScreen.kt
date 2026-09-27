package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.Member
import com.example.ui.viewmodel.RasmViewModel
import com.example.util.ImageUtils

@Composable
fun HomeScreen(
    viewModel: RasmViewModel,
    onNavigateToMembers: (filterType: String?) -> Unit,
    onViewMemberProfile: (Member) -> Unit,
    onOpenNotices: () -> Unit = {}
) {
    val notices by viewModel.notices.collectAsState()
    val members by viewModel.members.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val officeBearers by viewModel.officeBearers.collectAsState()
    val currentMember by viewModel.currentMember.collectAsState()

    // Compute stats
    val totalMembers = members.size
    val totalFamilies = members.sumOf { it.family.size }
    val lifeMembers = members.count { it.isLifeMember }
    val ordinaryMembers = members.count { it.isOrdinaryMember }
    val honoraryMembers = members.count { it.isHonoraryMember }
    val womensWing = members.count { it.isWomensWing }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Association Banner
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(Color(0xFF004D40), Color(0xFF006A60), Color(0xFF0D5C53))
                        )
                    )
                    .padding(20.dp)
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFFD99B26),
                            modifier = Modifier.size(48.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Apartment,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = settings.associationName.ifBlank { "Residents Association" },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFFFFD54F)
                            )
                            Text(
                                text = "Welfare & Community Association",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.9f)
                            )
                        }
                    }

                    if (settings.associationRegNo.isNotBlank() || settings.associationLocation.isNotBlank()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        val pillText = buildString {
                            if (settings.associationRegNo.isNotBlank()) append("Reg. No: ${settings.associationRegNo}")
                            if (settings.associationRegNo.isNotBlank() && settings.associationLocation.isNotBlank()) append(" • ")
                            if (settings.associationLocation.isNotBlank()) append(settings.associationLocation)
                        }
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color.White.copy(alpha = 0.2f),
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            Text(
                                text = pillText,
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }
        }

        // Latest notice (pinned ones come first)
        notices.firstOrNull()?.let { notice ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
                    .clickable { onOpenNotices() },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8E1))
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Default.Campaign, contentDescription = null, tint = Color(0xFF8D6E00))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (notice.pinned) "Pinned notice" else "Latest notice",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF8D6E00)
                        )
                        Text(notice.title, fontWeight = FontWeight.Bold)
                        Text(notice.body, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(
                            "See all notices",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }

        // Logged-in member strip
        currentMember?.let { member ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    if (!member.photo.isNullOrBlank()) {
                        AsyncImage(
                            model = ImageUtils.toImageModel(member.photo),
                            contentDescription = member.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFB2DFDB)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = member.name.take(1).uppercase(),
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp,
                                color = Color(0xFF00695C)
                            )
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = member.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Active Member",
                                tint = Color(0xFF00695C),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        val typeLabel = when (member.memberType) {
                            "LM" -> "Life Member"
                            "HM" -> "Honorary Member"
                            else -> "Ordinary Member"
                        }
                        Text(
                            text = "${member.contact} • $typeLabel",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFE8F5E9)
                    ) {
                        Text(
                            text = "ACTIVE",
                            color = Color(0xFF2E7D32),
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }

        // Overview Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Overview Dashboard",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Real-time Stats",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // 2-Column Grid of Stat Cards
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatCard(
                    modifier = Modifier.weight(1f),
                    title = "Total Members",
                    count = totalMembers,
                    icon = Icons.Default.Groups,
                    bgColor = Color(0xFFE0F2F1),
                    accentColor = Color(0xFF006A60),
                    onClick = { onNavigateToMembers(null) }
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    title = "Families",
                    count = totalFamilies,
                    icon = Icons.Default.Home,
                    bgColor = Color(0xFFFFF8E1),
                    accentColor = Color(0xFFB88200),
                    onClick = { onNavigateToMembers(null) }
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatCard(
                    modifier = Modifier.weight(1f),
                    title = "Life Members",
                    count = lifeMembers,
                    icon = Icons.Default.MilitaryTech,
                    bgColor = Color(0xFFE8EAF6),
                    accentColor = Color(0xFF283593),
                    onClick = { onNavigateToMembers("LM") }
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    title = "Ordinary Members",
                    count = ordinaryMembers,
                    icon = Icons.Default.Person,
                    bgColor = Color(0xFFF3E5F5),
                    accentColor = Color(0xFF6A1B9A),
                    onClick = { onNavigateToMembers("OM") }
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatCard(
                    modifier = Modifier.weight(1f),
                    title = "Honorary Members",
                    count = honoraryMembers,
                    icon = Icons.Default.Star,
                    bgColor = Color(0xFFFFEBEE),
                    accentColor = Color(0xFFC62828),
                    onClick = { onNavigateToMembers("HM") }
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    title = "Women's Wing",
                    count = womensWing,
                    icon = Icons.Default.Female,
                    bgColor = Color(0xFFFCE4EC),
                    accentColor = Color(0xFFAD1457),
                    onClick = { onNavigateToMembers("WW") }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Executive Committee Section
        val officials = listOfNotNull(
            if (officeBearers.presidentName.isNotBlank()) "President" to officeBearers.presidentName else null,
            if (officeBearers.vicePresidentName.isNotBlank()) "Vice President" to officeBearers.vicePresidentName else null,
            if (officeBearers.secretaryName.isNotBlank()) "Secretary" to officeBearers.secretaryName else null,
            if (officeBearers.jointSecretaryName.isNotBlank()) "Joint Secretary" to officeBearers.jointSecretaryName else null,
            if (officeBearers.treasurerName.isNotBlank()) "Treasurer" to officeBearers.treasurerName else null,
            if (officeBearers.patronName.isNotBlank()) "Patron" to officeBearers.patronName else null
        )

        if (officials.isNotEmpty()) {
            Text(
                text = "Executive Committee",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(officials) { (role, name) ->
                    Card(
                        modifier = Modifier.width(110.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF1565C0)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = name.take(1).uppercase(),
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = name,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Text(
                                text = role,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        // Recent Members
        if (members.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Members Directory",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                TextButton(onClick = { onNavigateToMembers(null) }) {
                    Text("View All (${members.size})")
                }
            }

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(members.take(12)) { member ->
                    Card(
                        modifier = Modifier
                            .width(96.dp)
                            .clickable { onViewMemberProfile(member) },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            if (!member.photo.isNullOrBlank()) {
                                AsyncImage(
                                    model = ImageUtils.toImageModel(member.photo),
                                    contentDescription = member.name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFB2DFDB)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = member.name.take(1).uppercase(),
                                        color = Color(0xFF00695C),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = member.name,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Text(
                                text = member.contact,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatCard(
    title: String,
    count: Int,
    icon: ImageVector,
    bgColor: Color,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .testTag("stat_card_${title.lowercase().replace(" ", "_")}"),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Text(
                    text = count.toString(),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = accentColor
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = accentColor
            )
        }
    }
}
