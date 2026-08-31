package com.example.visionbridge.ui.screens

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
import com.example.visionbridge.ui.theme.Accent
import com.example.visionbridge.ui.theme.BgCard
import com.example.visionbridge.ui.theme.BgPrimary
import com.example.visionbridge.ui.theme.TextMuted
import com.example.visionbridge.ui.theme.TextPrimary
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VolunteerScreen(
    onBack: () -> Unit
) {
    var requestStatus by remember { mutableStateOf("IDLE") } // IDLE, SEARCHING, CONNECTED

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Volunteer Help", fontWeight = FontWeight.Bold) },
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
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            when (requestStatus) {
                "IDLE" -> {
                    Text(
                        text = "Need human assistance?",
                        style = MaterialTheme.typography.headlineMedium,
                        color = TextPrimary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.semantics { heading() }
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Connect with a sighted volunteer through a live video call.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextMuted,
                        textAlign = TextAlign.Center
                    )
                    
                    Spacer(modifier = Modifier.height(48.dp))
                    
                    Button(
                        onClick = { requestStatus = "SEARCHING" },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Accent)
                    ) {
                        Text("Call a Volunteer", fontSize = 24.sp, color = BgPrimary, fontWeight = FontWeight.Bold)
                    }
                }
                
                "SEARCHING" -> {
                    CircularProgressIndicator(
                        color = Accent,
                        modifier = Modifier.size(80.dp),
                        strokeWidth = 8.dp
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                    Text(
                        text = "Finding an available volunteer...",
                        style = MaterialTheme.typography.headlineMedium,
                        color = TextPrimary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.semantics { heading() }
                    )
                    
                    // Simulate finding someone
                    LaunchedEffect(Unit) {
                        delay(3000)
                        requestStatus = "CONNECTED"
                    }
                    
                    Spacer(modifier = Modifier.height(48.dp))
                    
                    Button(
                        onClick = { requestStatus = "IDLE" },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(72.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BgCard)
                    ) {
                        Text("Cancel Request", fontSize = 20.sp, color = TextPrimary)
                    }
                }
                
                "CONNECTED" -> {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = BgCard,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(32.dp)
                        ) {
                            Text(
                                text = "👤",
                                fontSize = 80.sp,
                                modifier = Modifier.padding(bottom = 16.dp)
                            )
                            Text(
                                text = "Connected to Sarah",
                                style = MaterialTheme.typography.headlineMedium,
                                color = TextPrimary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.semantics { heading() }
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Sarah can see your camera and hear you.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = Accent,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(48.dp))
                    
                    Button(
                        onClick = { requestStatus = "IDLE" },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = TextPrimary)
                    ) {
                        Text("End Call", fontSize = 24.sp, color = BgPrimary, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
