package com.example.visionbridge.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.visionbridge.ui.theme.BgPrimary
import com.example.visionbridge.ui.theme.Emergency
import com.example.visionbridge.ui.theme.EmergencyDim
import com.example.visionbridge.ui.theme.TextPrimary
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SOSScreen(
    onBack: () -> Unit
) {
    var countdown by remember { mutableStateOf(5) }
    var isEmergencyActive by remember { mutableStateOf(false) }

    LaunchedEffect(countdown, isEmergencyActive) {
        if (!isEmergencyActive && countdown > 0) {
            delay(1000)
            countdown--
            if (countdown == 0) {
                isEmergencyActive = true
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Emergency SOS", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("Cancel", color = TextPrimary, fontSize = 18.sp)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BgPrimary,
                    titleContentColor = TextPrimary,
                    navigationIconContentColor = TextPrimary
                )
            )
        },
        containerColor = BgPrimary
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (!isEmergencyActive) {
                Text(
                    text = "Sending SOS in",
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextPrimary,
                    modifier = Modifier.semantics { heading() }
                )
                
                Spacer(modifier = Modifier.height(32.dp))
                
                Surface(
                    shape = RoundedCornerShape(32.dp),
                    color = EmergencyDim,
                    modifier = Modifier.size(200.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = countdown.toString(),
                            fontSize = 96.sp,
                            fontWeight = FontWeight.Bold,
                            color = Emergency
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(48.dp))
                
                Button(
                    onClick = onBack,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = TextPrimary)
                ) {
                    Text("CANCEL SOS", fontSize = 24.sp, color = BgPrimary, fontWeight = FontWeight.Bold)
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Button(
                    onClick = { isEmergencyActive = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Emergency)
                ) {
                    Text("SEND NOW", fontSize = 24.sp, color = TextPrimary, fontWeight = FontWeight.Bold)
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(32.dp),
                    color = Emergency,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Text(
                            text = "🚨",
                            fontSize = 72.sp,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                        Text(
                            text = "SOS SENT",
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            modifier = Modifier.semantics { heading() }
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Emergency contacts and local services have been notified of your location.",
                            fontSize = 20.sp,
                            color = TextPrimary,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                Button(
                    onClick = onBack,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = TextPrimary)
                ) {
                    Text("End Emergency Mode", fontSize = 20.sp, color = BgPrimary, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
