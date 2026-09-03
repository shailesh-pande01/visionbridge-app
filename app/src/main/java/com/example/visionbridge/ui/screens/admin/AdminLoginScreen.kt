package com.example.visionbridge.ui.screens.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
import com.example.visionbridge.R
import com.example.visionbridge.api.AdminApi
import com.example.visionbridge.api.ApiResult
import com.example.visionbridge.data.User
import com.example.visionbridge.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminLoginScreen(
    onAdminLoginSuccess: (User) -> Unit,
    onBackToMainLogin: () -> Unit
) {
    val context = LocalContext.current
    val adminApi = remember { AdminApi(context) }
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val strBothFields = stringResource(R.string.auth_err_both_fields)
    val strNotAdmin = stringResource(R.string.admin_login_err_not_admin)

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

        // Security Badge & Title
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = AccentDim
        ) {
            Text(
                text = "🛡️ RESTRICTED AREA",
                color = Accent,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = stringResource(R.string.admin_login_title),
            style = MaterialTheme.typography.headlineMedium,
            color = Accent,
            fontWeight = FontWeight.Bold,
            fontSize = 26.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() }
        )

        Text(
            text = stringResource(R.string.admin_login_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = TextMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp, bottom = 24.dp)
        )

        // Error message banner
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
                    text = errorMessage.orEmpty(),
                    color = Emergency,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(14.dp),
                    textAlign = TextAlign.Center
                )
            }
        }

        // Username / Email input
        OutlinedTextField(
            value = username,
            onValueChange = { username = it; errorMessage = null },
            label = { Text(stringResource(R.string.common_username_or_email), color = TextMuted, fontSize = 15.sp) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next
            ),
            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 60.dp),
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

        // Password input
        OutlinedTextField(
            value = password,
            onValueChange = { password = it; errorMessage = null },
            label = { Text(stringResource(R.string.auth_password), color = TextMuted, fontSize = 15.sp) },
            singleLine = true,
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                TextButton(onClick = { passwordVisible = !passwordVisible }) {
                    Text(
                        text = if (passwordVisible) stringResource(R.string.common_hide) else stringResource(R.string.common_show),
                        color = Accent,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    focusManager.clearFocus()
                    if (username.isBlank() || password.isBlank()) {
                        errorMessage = strBothFields
                    } else {
                        isLoading = true
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                adminApi.adminLogin(username.trim(), password)
                            }
                            isLoading = false
                            when (result) {
                                is ApiResult.Success -> onAdminLoginSuccess(result.value)
                                is ApiResult.Failure -> errorMessage = result.error.userMessage
                            }
                        }
                    }
                }
            ),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 60.dp),
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

        Spacer(modifier = Modifier.height(24.dp))

        // Submit Button
        Button(
            onClick = {
                focusManager.clearFocus()
                if (username.isBlank() || password.isBlank()) {
                    errorMessage = strBothFields
                    return@Button
                }

                isLoading = true
                scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        adminApi.adminLogin(username.trim(), password)
                    }
                    isLoading = false
                    when (result) {
                        is ApiResult.Success -> onAdminLoginSuccess(result.value)
                        is ApiResult.Failure -> errorMessage = result.error.userMessage
                    }
                }
            },
            enabled = !isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 60.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Accent,
                disabledContainerColor = Accent.copy(alpha = 0.5f)
            )
        ) {
            if (isLoading) {
                CircularProgressIndicator(color = BgPrimary, modifier = Modifier.size(26.dp))
            } else {
                Text(
                    text = stringResource(R.string.admin_login_button),
                    fontSize = 18.sp,
                    color = BgPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Back to Main Login Link
        TextButton(
            onClick = onBackToMainLogin,
            modifier = Modifier.heightIn(min = 48.dp)
        ) {
            Text(
                text = stringResource(R.string.admin_back_to_main_login),
                color = TextMuted,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
