package com.example.visionbridge.ui.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.visionbridge.R
import com.example.visionbridge.data.User
import com.example.visionbridge.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(
    onAuthSuccess: (User) -> Unit,
    viewModel: AuthViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var isRegisterMode by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var selectedRole by remember { mutableStateOf("lowVisionUser") }
    var localError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(uiState) {
        if (uiState is AuthUiState.Success) {
            onAuthSuccess((uiState as AuthUiState.Success).user)
        }
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgPrimary)
            .verticalScroll(scrollState)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        // Branding Logo / Header
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineLarge,
            color = Accent,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics { heading() }
        )
        Text(
            text = stringResource(R.string.tagline),
            style = MaterialTheme.typography.bodyLarge,
            color = TextMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp, bottom = 28.dp)
        )

        // Mode Switcher (Login vs Register Tabs)
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = BgCard,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(6.dp)
            ) {
                Button(
                    onClick = {
                        isRegisterMode = false
                        localError = null
                        viewModel.resetError()
                    },
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (!isRegisterMode) Accent else Color.Transparent,
                        contentColor = if (!isRegisterMode) BgPrimary else TextMuted
                    )
                ) {
                    Text(
                        stringResource(R.string.auth_login_button),
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }

                Button(
                    onClick = {
                        isRegisterMode = true
                        localError = null
                        viewModel.resetError()
                    },
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isRegisterMode) Accent else Color.Transparent,
                        contentColor = if (isRegisterMode) BgPrimary else TextMuted
                    )
                ) {
                    Text(
                        stringResource(R.string.auth_register_button),
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Error message
        val errorMessage = localError ?: (uiState as? AuthUiState.Error)?.message
        if (!errorMessage.isNullOrBlank()) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = EmergencyDim,
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            ) {
                Text(
                    text = errorMessage,
                    color = Emergency,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(14.dp),
                    textAlign = TextAlign.Center
                )
            }
        }

        if (isRegisterMode) {
            // Full Name
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.auth_full_name), color = TextMuted) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedBorderColor = Accent,
                    unfocusedBorderColor = BorderSubtle,
                    focusedContainerColor = BgCard,
                    unfocusedContainerColor = BgCard
                ),
                shape = RoundedCornerShape(14.dp)
            )
            Spacer(modifier = Modifier.height(14.dp))
        }

        // Username
        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text(stringResource(R.string.auth_username), color = TextMuted) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                focusedBorderColor = Accent,
                unfocusedBorderColor = BorderSubtle,
                focusedContainerColor = BgCard,
                unfocusedContainerColor = BgCard
            ),
            shape = RoundedCornerShape(14.dp)
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Password
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text(stringResource(R.string.auth_password), color = TextMuted) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                focusedBorderColor = Accent,
                unfocusedBorderColor = BorderSubtle,
                focusedContainerColor = BgCard,
                unfocusedContainerColor = BgCard
            ),
            shape = RoundedCornerShape(14.dp)
        )

        if (isRegisterMode) {
            Spacer(modifier = Modifier.height(14.dp))

            // Confirm Password
            OutlinedTextField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it },
                label = { Text(stringResource(R.string.auth_confirm_password), color = TextMuted) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedBorderColor = Accent,
                    unfocusedBorderColor = BorderSubtle,
                    focusedContainerColor = BgCard,
                    unfocusedContainerColor = BgCard
                ),
                shape = RoundedCornerShape(14.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Role Selector Cards
            Text(
                text = stringResource(R.string.auth_role_prompt),
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            )

            RoleCard(
                title = stringResource(R.string.auth_role_low_vision),
                description = stringResource(R.string.auth_role_low_vision_desc),
                icon = "👁️",
                isSelected = selectedRole == "lowVisionUser",
                onClick = { selectedRole = "lowVisionUser" }
            )

            Spacer(modifier = Modifier.height(10.dp))

            RoleCard(
                title = stringResource(R.string.auth_role_volunteer),
                description = stringResource(R.string.auth_role_volunteer_desc),
                icon = "🤝",
                isSelected = selectedRole == "volunteer",
                onClick = { selectedRole = "volunteer" }
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Submit Button
        val isLoading = uiState is AuthUiState.Loading
        Button(
            onClick = {
                localError = null
                if (isRegisterMode) {
                    if (password != confirmPassword) {
                        localError = "Passwords do not match."
                        return@Button
                    }
                    viewModel.register(name, username, password, selectedRole)
                } else {
                    viewModel.login(username, password)
                }
            },
            enabled = !isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 68.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Accent)
        ) {
            if (isLoading) {
                CircularProgressIndicator(color = BgPrimary, modifier = Modifier.size(28.dp))
            } else {
                Text(
                    text = if (isRegisterMode) stringResource(R.string.auth_register_button) else stringResource(R.string.auth_login_button),
                    fontSize = 22.sp,
                    color = BgPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun RoleCard(
    title: String,
    description: String,
    icon: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) BgSecondary else BgCard,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = "$title, ${if (isSelected) "selected" else "not selected"}"
            },
        border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, Accent) else null
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = icon, fontSize = 32.sp, modifier = Modifier.padding(end = 16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = if (isSelected) Accent else TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    color = TextMuted,
                    fontSize = 14.sp,
                    lineHeight = 18.sp
                )
            }
        }
    }
}
