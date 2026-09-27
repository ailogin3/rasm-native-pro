package com.example.ui.screens

import android.app.Activity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.viewmodel.RasmViewModel

private enum class OtpStep { ENTER_PHONE, ENTER_CODE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(
    viewModel: RasmViewModel,
    onAuthSuccess: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity

    var step by remember { mutableStateOf(OtpStep.ENTER_PHONE) }
    var phone by remember { mutableStateOf("") }
    var otpCode by remember { mutableStateOf("") }
    var verificationId by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var showConfigDialog by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()

    fun toE164(raw: String): String {
        val trimmed = raw.trim()
        return if (trimmed.startsWith("+")) trimmed else "+91$trimmed"
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF004D40),
                        Color(0xFF00695C),
                        Color(0xFF1565C0)
                    )
                )
            )
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 440.dp)
                .verticalScroll(scrollState),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Icon
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (step == OtpStep.ENTER_CODE) Icons.Default.Sms else Icons.Default.Phone,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = if (step == OtpStep.ENTER_CODE) "Enter Code" else "Member Sign In",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = if (step == OtpStep.ENTER_CODE)
                        "Enter the 6-digit code sent to ${toE164(phone)}"
                    else
                        "Use the mobile number registered on your member record",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
                )

                // Error Message
                AnimatedVisibility(visible = errorMessage != null) {
                    errorMessage?.let { msg ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp),
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.errorContainer
                        ) {
                            Text(
                                text = msg,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }

                if (step == OtpStep.ENTER_PHONE) {
                    // Phone Input
                    OutlinedTextField(
                        value = phone,
                        onValueChange = {
                            phone = it
                            errorMessage = null
                        },
                        label = { Text("Mobile Number") },
                        placeholder = { Text("9876543210 or +919876543210") },
                        leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("auth_phone_input"),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // Send OTP Button
                    Button(
                        onClick = {
                            val digitsOnly = phone.filter { it.isDigit() }
                            if (digitsOnly.length < 10) {
                                errorMessage = "Enter a valid mobile number"
                                return@Button
                            }
                            val e164 = toE164(phone)
                            val act = activity
                            if (act == null) {
                                errorMessage = "Unable to start verification here"
                                return@Button
                            }
                            isLoading = true
                            // TEMP-SPARK-TESTING: real gate is `viewModel.checkPhoneIsMember`, which calls a
                            // Cloud Function and needs the Blaze plan. Bypassed here so the OTP flow can be
                            // exercised on Spark. REVERT to the block below once the project is on Blaze:
                            //
                            // viewModel.checkPhoneIsMember(e164) { isMember ->
                            //     if (!isMember) {
                            //         isLoading = false
                            //         errorMessage = "This number is not registered as a member. Please contact your association administrator to add you first."
                            //     } else {
                            //         viewModel.sendOtp(...)
                            //     }
                            // }
                            run {
                                viewModel.sendOtp(
                                    phoneNumber = e164,
                                    activity = act,
                                    onCodeSent = { vid ->
                                        isLoading = false
                                        verificationId = vid
                                        step = OtpStep.ENTER_CODE
                                    },
                                    onAutoVerified = {
                                        isLoading = false
                                        onAuthSuccess()
                                    },
                                    onError = { msg ->
                                        isLoading = false
                                        errorMessage = msg
                                    }
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("auth_send_otp_button"),
                        enabled = !isLoading,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                text = "Send OTP",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                } else {
                    // OTP Code Input
                    OutlinedTextField(
                        value = otpCode,
                        onValueChange = {
                            otpCode = it
                            errorMessage = null
                        },
                        label = { Text("6-Digit Code") },
                        leadingIcon = { Icon(Icons.Default.Sms, contentDescription = null) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("auth_otp_input"),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // Verify Button
                    Button(
                        onClick = {
                            val vid = verificationId
                            if (otpCode.isBlank() || vid == null) {
                                errorMessage = "Enter the code sent to your phone"
                                return@Button
                            }
                            isLoading = true
                            viewModel.verifyOtp(vid, otpCode.trim()) { success, err ->
                                isLoading = false
                                if (success) {
                                    onAuthSuccess()
                                } else {
                                    errorMessage = err ?: "Invalid OTP"
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("auth_verify_otp_button"),
                        enabled = !isLoading,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                text = "Verify & Sign In",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    TextButton(
                        onClick = {
                            step = OtpStep.ENTER_PHONE
                            otpCode = ""
                            verificationId = null
                            errorMessage = null
                        }
                    ) {
                        Text(
                            text = "Change number",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Firebase Config setup button (for initial setup)
                TextButton(
                    onClick = { showConfigDialog = true }
                ) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Configure Firebase Project",
                        color = MaterialTheme.colorScheme.outline,
                        style = MaterialTheme.typography.labelSmall
                    )
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
}

@Composable
fun FirebaseConfigDialog(
    viewModel: RasmViewModel,
    onDismiss: () -> Unit
) {
    var apiKey by remember { mutableStateOf("") }
    var appId by remember { mutableStateOf("") }
    var projectId by remember { mutableStateOf("") }
    var storageBucket by remember { mutableStateOf("") }
    var senderId by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Firebase Configuration") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "If google-services.json is not placed in the project, enter your Firebase project parameters:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = projectId,
                    onValueChange = { projectId = it },
                    label = { Text("Project ID") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API Key") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = appId,
                    onValueChange = { appId = it },
                    label = { Text("Application / Client ID") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = storageBucket,
                    onValueChange = { storageBucket = it },
                    label = { Text("Storage Bucket (Optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = senderId,
                    onValueChange = { senderId = it },
                    label = { Text("Messaging Sender ID (Optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (apiKey.isNotBlank() && appId.isNotBlank() && projectId.isNotBlank()) {
                        viewModel.saveFirebaseConfig(apiKey, appId, projectId, storageBucket, senderId) {
                            onDismiss()
                        }
                    }
                }
            ) {
                Text("Save & Connect")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
