package com.example.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.remote.AppVersionInfo

/**
 * Dismissible "a new version is available" banner, shown above the
 * screen content regardless of which tab is currently selected.
 * Dismissal is per-session only (see RasmViewModel.dismissUpdateBanner) --
 * it reappears on the next cold start until the member actually updates.
 */
@Composable
fun UpdateBanner(
    info: AppVersionInfo,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.SystemUpdate,
                contentDescription = null,
                tint = Color(0xFFE65100)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Update available (v${info.latestVersionName})",
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFE65100)
                )
                if (!info.updateMessage.isNullOrBlank()) {
                    Text(
                        text = info.updateMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF6D4C00)
                    )
                }
            }
            if (!info.updateUrl.isNullOrBlank()) {
                TextButton(onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(info.updateUrl)))
                }) {
                    Text("Update")
                }
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Filled.Close, contentDescription = "Dismiss")
            }
        }
    }
}
