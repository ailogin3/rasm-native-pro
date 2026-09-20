package com.example.ui.screens

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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.viewmodel.RasmViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(
    viewModel: RasmViewModel,
    onAuthSuccess: () -> Unit
) {
    var isSignUp by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var showConfigDialog by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()

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
                        imageVector = if (isSignUp) Icons.Default.PersonAdd else Icons.Default.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = if (isSignUp) "Create Account" else "Member Login",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = if (isSignUp)
                        "Use the email registered on your member record"
                    else
                        "Sign in with your registered email to continue",
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

                // Email Input
                OutlinedTextField(
                    value = email,
                    onValueChange = {
                        email = it
                        errorMessage = null
                    },
                    label = { Text("Email Address") },
                    leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("auth_email_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Password Input
                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        errorMessage = null
                    },
                    label = { Text("Password") },
                    leadingIcon = { Icon(Icons.Default.VpnKey, contentDescription = null) },
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (passwordVisible) "Hide password" else "Show password"
                            )
                        }
                    },
                    singleLine = true,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("auth_password_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                // Confirm Password (Signup only)
                if (isSignUp) {
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = {
                            confirmPassword = it
                            errorMessage = null
                        },
                        label = { Text("Confirm Password") },
                        leadingIcon = { Icon(Icons.Default.VpnKey, contentDescription = null) },
                        singleLine = true,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("auth_confirm_password_input"),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Submit Button
                Button(
                    onClick = {
                        if (email.isBlank() || password.isBlank()) {
                            errorMessage = "Please fill in all fields"
                            return@Button
                        }
                        if (isSignUp) {
                            if (password != confirmPassword) {
                                errorMessage = "Passwords do not match"
                                return@Button
                            }
                            if (password.length < 6) {
                                errorMessage = "Password must be at least 6 characters"
                                return@Button
                            }
                            isLoading = true
                            viewModel.signup(email, password) { success, err ->
                                isLoading = false
                                if (success) {
                                    onAuthSuccess()
                                } else {
                                    errorMessage = err ?: "Signup failed"
                                }
                            }
                        } else {
                            isLoading = true
                            viewModel.login(email, password) { success, err ->
                                isLoading = false
                                if (success) {
                                    onAuthSuccess()
                                } else {
                                    errorMessage = err ?: "Login failed. Check your email and password."
                                }
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("auth_submit_button"),
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
                            text = if (isSignUp) "Create Account" else "Sign In",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Toggle between Login and Signup
                TextButton(
                    onClick = {
                        isSignUp = !isSignUp
                        errorMessage = null
                    }
                ) {
                    Text(
                        text = if (isSignUp)
                            "Already have an account? Sign In"
                        else
                            "First time? Create your account",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Forgot Password link
                if (!isSignUp) {
                    TextButton(
                        onClick = {
                            if (email.isBlank()) {
                                errorMessage = "Enter your email address above, then tap Forgot Password"
                            } else {
                                viewModel.forgotPassword(email) { ok, msg ->
                                    errorMessage = msg
                                }
                            }
                        }
                    ) {
                        Text(
                            text = "Forgot password?",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
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
