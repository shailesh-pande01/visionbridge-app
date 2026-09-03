package com.example.visionbridge.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.visionbridge.R
import com.example.visionbridge.api.ApiResult
import com.example.visionbridge.api.AuthApi
import com.example.visionbridge.data.User
import com.example.visionbridge.ui.theme.Accent
import com.example.visionbridge.ui.theme.BgPrimary
import com.example.visionbridge.ui.theme.TextMuted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun SplashScreen(
    onSessionValid: (User) -> Unit,
    onSessionInvalid: (String?) -> Unit
) {
    val context = LocalContext.current
    val authApi = AuthApi(context)

    LaunchedEffect(Unit) {
        val result = withContext(Dispatchers.IO) {
            authApi.validateSession()
        }
        when (result) {
            is ApiResult.Success -> {
                onSessionValid(result.value)
            }
            is ApiResult.Failure -> {
                val msg = if (result.error.userMessage.contains("expired", ignoreCase = true)) {
                    result.error.userMessage
                } else null
                onSessionInvalid(msg)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgPrimary)
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineLarge,
                color = Accent,
                fontWeight = FontWeight.Bold,
                fontSize = 38.sp,
                modifier = Modifier.semantics { heading() }
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.tagline),
                style = MaterialTheme.typography.bodyLarge,
                color = TextMuted,
                fontSize = 16.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(48.dp))
            CircularProgressIndicator(
                color = Accent,
                strokeWidth = 4.dp,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.splash_loading),
                color = TextMuted,
                fontSize = 14.sp
            )
        }
    }
}
