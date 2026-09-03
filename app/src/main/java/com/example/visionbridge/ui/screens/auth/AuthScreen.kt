package com.example.visionbridge.ui.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
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
    sessionExpiredNotice: String? = null,
    onNavigateAdminLogin: (() -> Unit)? = null,
    viewModel: AuthViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current

    var isRegisterMode by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var identifier by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var selectedRole by remember { mutableStateOf("lowVisionUser") }
    var localError by remember { mutableStateOf<String?>(sessionExpiredNotice) }

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
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Branding Logo / Header
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineLarge,
            color = Accent,
            fontWeight = FontWeight.Bold,
            fontSize = 32.sp,
            modifier = Modifier.semantics { heading() }
        )
        Text(
            text = stringResource(R.string.tagline),
            style = MaterialTheme.typography.bodyLarge,
            color = TextMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp, bottom = 24.dp)
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
                    modifier = Modifier
                        .weight(1f)
                        .height(54.dp),
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
                    modifier = Modifier
                        .weight(1f)
                        .height(54.dp),
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

        Spacer(modifier = Modifier.height(20.dp))

        // Error message banner
        val errorMessage = localError ?: (uiState as? AuthUiState.Error)?.message
        if (!errorMessage.isNullOrBlank()) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = EmergencyDim,
                border = androidx.compose.foundation.BorderStroke(1.5.dp, Emergency.copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
                    .semantics {
                        liveRegion = LiveRegionMode.Assertive
                        contentDescription = "Error: $errorMessage"
                    }
            ) {
                Text(
                    text = errorMessage,
                    color = Emergency,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(16.dp),
                    textAlign = TextAlign.Center
                )
            }
        }

        if (isRegisterMode) {
            // Full Name
            OutlinedTextField(
                value = name,
                onValueChange = { name = it; localError = null },
                label = { Text(stringResource(R.string.auth_full_name), color = TextMuted, fontSize = 16.sp) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp),
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

        // Username / Identifier
        OutlinedTextField(
            value = identifier,
            onValueChange = { identifier = it; localError = null },
            label = {
                Text(
                    if (isRegisterMode) stringResource(R.string.auth_username) else stringResource(R.string.common_username_or_email),
                    color = TextMuted,
                    fontSize = 16.sp
                )
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (isRegisterMode) KeyboardType.Text else KeyboardType.Email,
                imeAction = ImeAction.Next
            ),
            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp),
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

            // Email (Optional)
            OutlinedTextField(
                value = email,
                onValueChange = { email = it; localError = null },
                label = { Text(stringResource(R.string.auth_email), color = TextMuted, fontSize = 16.sp) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp),
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
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Password
        OutlinedTextField(
            value = password,
            onValueChange = { password = it; localError = null },
            label = { Text(stringResource(R.string.auth_password), color = TextMuted, fontSize = 16.sp) },
            singleLine = true,
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                TextButton(onClick = { passwordVisible = !passwordVisible }) {
                    Text(
                        text = if (passwordVisible) stringResource(R.string.common_hide) else stringResource(R.string.common_show),
                        color = Accent,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = if (isRegisterMode) ImeAction.Next else ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onNext = { focusManager.moveFocus(FocusDirection.Down) },
                onDone = {
                    focusManager.clearFocus()
                    if (!isRegisterMode) {
                        viewModel.login(identifier, password)
                    }
                }
            ),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp),
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
                onValueChange = { confirmPassword = it; localError = null },
                label = { Text(stringResource(R.string.auth_confirm_password), color = TextMuted, fontSize = 16.sp) },
                singleLine = true,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp),
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

            // Role Selector Prompt
            Text(
                text = stringResource(R.string.auth_role_prompt),
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .semantics { heading() }
            )

            RoleCard(
                title = stringResource(R.string.auth_role_low_vision),
                description = stringResource(R.string.auth_role_low_vision_desc),
                icon = "👁️",
                isSelected = selectedRole == "lowVisionUser",
                onClick = { selectedRole = "lowVisionUser" }
            )

            Spacer(modifier = Modifier.height(12.dp))

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
                focusManager.clearFocus()
                localError = null
                if (isRegisterMode) {
                    viewModel.register(
                        name = name,
                        username = identifier,
                        password = password,
                        confirmPassword = confirmPassword,
                        role = selectedRole,
                        email = email
                    )
                } else {
                    viewModel.login(identifier, password)
                }
            },
            enabled = !isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 68.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Accent,
                disabledContainerColor = Accent.copy(alpha = 0.5f)
            )
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

        if (!isRegisterMode && onNavigateAdminLogin != null) {
            Spacer(modifier = Modifier.height(16.dp))
            TextButton(
                onClick = onNavigateAdminLogin,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text(
                    text = stringResource(R.string.admin_login_link),
                    color = TextMuted,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
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
    val selectedStateText = if (isSelected) stringResource(R.string.common_selected) else stringResource(R.string.common_not_selected)
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) BgSecondary else BgCard,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = "$title, $selectedStateText"
            },
        border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, Accent) else null
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = icon, fontSize = 32.sp, modifier = Modifier.padding(end = 16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = if (isSelected) Accent else TextPrimary,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    color = TextMuted,
                    fontSize = 14.sp,
                    lineHeight = 19.sp
                )
            }
        }
    }
}
