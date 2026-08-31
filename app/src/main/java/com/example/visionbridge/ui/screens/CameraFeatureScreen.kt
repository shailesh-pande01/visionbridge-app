package com.example.visionbridge.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.visionbridge.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraFeatureScreen(
    title: String,
    onBack: () -> Unit,
    onVolunteerHelp: () -> Unit
) {
    // Simulated states for UI design demonstration
    var hasResult by remember { mutableStateOf(false) }
    var isHazard by remember { mutableStateOf(false) }
    var lowConfidence by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("Back", color = TextPrimary, fontSize = 18.sp)
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
        ) {
            // Camera Preview Area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                if (!hasResult) {
                    Text(
                        text = "Camera Preview Active",
                        color = TextMuted,
                        style = MaterialTheme.typography.bodyLarge
                    )
                } else {
                    // Result Overlay
                    ResultOverlay(
                        isHazard = isHazard,
                        lowConfidence = lowConfidence,
                        onVolunteerHelp = onVolunteerHelp
                    )
                }
            }

            // Bottom Control Area
            Surface(
                color = BgCard,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (!hasResult) {
                        // Capture Button
                        Box(
                            modifier = Modifier
                                .size(96.dp)
                                .clip(CircleShape)
                                .background(Accent)
                                .clickable {
                                    hasResult = true
                                    // Randomly simulate different result states for demo
                                    isHazard = listOf(true, false).random()
                                    lowConfidence = listOf(true, false).random()
                                }
                                .semantics { contentDescription = "Capture Image" },
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(76.dp)
                                    .clip(CircleShape)
                                    .background(BgPrimary)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(68.dp)
                                        .clip(CircleShape)
                                        .background(Accent)
                                        .align(Alignment.Center)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Tap to Capture",
                            color = TextPrimary,
                            style = MaterialTheme.typography.titleMedium
                        )
                    } else {
                        // Action Buttons after result
                        Button(
                            onClick = {
                                hasResult = false
                                isHazard = false
                                lowConfidence = false
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(64.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Accent)
                        ) {
                            Text("Scan Again", fontSize = 20.sp, color = BgPrimary, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ResultOverlay(
    isHazard: Boolean,
    lowConfidence: Boolean,
    onVolunteerHelp: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Bottom
    ) {
        if (isHazard && !lowConfidence) {
            Surface(
                color = Emergency,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "CAUTION",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 24.sp,
                        modifier = Modifier.semantics { heading() }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Step down ahead. Please proceed carefully.",
                        color = Color.White,
                        fontSize = 20.sp
                    )
                }
            }
        } else if (lowConfidence) {
            Surface(
                color = BgSecondary.copy(alpha = 0.9f),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "I'm not confident enough to answer this accurately.",
                        color = TextPrimary,
                        fontSize = 20.sp,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    Button(
                        onClick = onVolunteerHelp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(60.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White)
                    ) {
                        Text("Get Help from a Volunteer", color = BgPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            Surface(
                color = BgSecondary.copy(alpha = 0.9f),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Result",
                        color = Accent,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "A wooden table with a white coffee mug on it.",
                        color = TextPrimary,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
