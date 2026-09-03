package com.example.visionbridge.ui.screens.calling

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.visionbridge.R
import com.example.visionbridge.data.PhoneContact
import com.example.visionbridge.ui.components.AppTopBar
import com.example.visionbridge.ui.theme.*
import com.example.visionbridge.utils.TextToSpeechManager
import com.example.visionbridge.voice.ScreenActionRegistry

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallingAssistantScreen(
    onBack: () -> Unit,
    viewModel: CallingViewModel = viewModel()
) {
    val context = LocalContext.current
    val contacts by viewModel.contacts.collectAsStateWithLifecycle()
    val recentCalls by viewModel.recentCalls.collectAsStateWithLifecycle()
    val spokenPrompt by viewModel.spokenPrompt.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableStateOf(0) } // 0: Contacts, 1: Recent Calls
    var showAddDialog by remember { mutableStateOf(false) }

    val tts = remember { TextToSpeechManager(context) }

    LaunchedEffect(spokenPrompt) {
        if (spokenPrompt.isNotBlank()) {
            tts.speak(spokenPrompt)
        }
    }

    DisposableEffect(Unit) {
        ScreenActionRegistry.registerScreen(
            screenId = "calling",
            onCancel = { onBack() }
        )
        onDispose {
            ScreenActionRegistry.unregisterScreen("calling")
            tts.shutdown()
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(R.string.feature_calling_title),
                subtitle = stringResource(R.string.calling_subtitle),
                onBack = {
                    tts.stop()
                    onBack()
                },
                backContentDescription = stringResource(R.string.common_back),
                actions = {
                    TextButton(
                        onClick = { showAddDialog = true },
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text("➕ " + stringResource(R.string.common_add), color = Accent, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            )
        },
        containerColor = BgPrimary
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            // Voice Command Hint
            Surface(
                color = BgCard,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "🎙️", fontSize = 24.sp, modifier = Modifier.padding(end = 12.dp))
                    Text(
                        text = stringResource(R.string.calling_voice_hint),
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Tab Row
            PrimaryTabRow(
                selectedTabIndex = selectedTab,
                containerColor = BgPrimary,
                contentColor = Accent,
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Text(
                            text = stringResource(R.string.calling_tab_contacts, contacts.size),
                            fontSize = 16.sp,
                            fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal,
                            color = if (selectedTab == 0) Accent else TextMuted
                        )
                    }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Text(
                            text = stringResource(R.string.calling_tab_recent),
                            fontSize = 16.sp,
                            fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal,
                            color = if (selectedTab == 1) Accent else TextMuted
                        )
                    }
                )
            }

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Accent)
                }
            } else if (selectedTab == 0) {
                // Contacts List
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(contacts, key = { it.id }) { contact ->
                        ContactItemCard(
                            contact = contact,
                            onCall = {
                                viewModel.triggerDial(contact.phoneNumber, contact, context)
                            },
                            onDelete = {
                                viewModel.deleteContact(contact.id)
                            }
                        )
                    }
                }
            } else {
                // Recent Calls List
                if (recentCalls.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "No recent calls.",
                            color = TextMuted,
                            fontSize = 18.sp
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        items(recentCalls, key = { it.id }) { call ->
                            Surface(
                                color = BgCard,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.triggerDial(call.phoneNumber, null, context)
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text(
                                            text = if (call.name.isNotBlank()) call.name else call.phoneNumber,
                                            color = TextPrimary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 18.sp
                                        )
                                        Text(
                                            text = "${call.direction.replaceFirstChar { it.uppercase() }} • ${call.phoneNumber}",
                                            color = TextMuted,
                                            fontSize = 14.sp
                                        )
                                    }
                                    Text(
                                        text = stringResource(R.string.calling_btn_call),
                                        color = Accent,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Add Contact Dialog
    if (showAddDialog) {
        var name by remember { mutableStateOf("") }
        var phone by remember { mutableStateOf("") }
        var relationship by remember { mutableStateOf("Friend") }

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text(stringResource(R.string.calling_add_contact), fontWeight = FontWeight.Bold, color = TextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(stringResource(R.string.calling_field_name)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text(stringResource(R.string.calling_field_phone)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = relationship,
                        onValueChange = { relationship = it },
                        label = { Text(stringResource(R.string.calling_field_relation)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (name.isNotBlank() && phone.isNotBlank()) {
                            viewModel.addContact(name, phone, relationship)
                            showAddDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Accent)
                ) {
                    Text(stringResource(R.string.common_add), color = BgPrimary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text(stringResource(R.string.common_cancel), color = TextMuted)
                }
            },
            containerColor = BgCard
        )
    }
}

@Composable
private fun ContactItemCard(
    contact: PhoneContact,
    onCall: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        color = BgCard,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = contact.name,
                        color = TextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (contact.isFavorite) {
                        Text(text = " ⭐", fontSize = 16.sp)
                    }
                }
                Text(
                    text = "${contact.relationship} • ${contact.phoneNumber}",
                    color = TextMuted,
                    fontSize = 15.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onCall,
                    colors = ButtonDefaults.buttonColors(containerColor = Accent),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text(stringResource(R.string.calling_btn_call), color = BgPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }

                IconButton(onClick = onDelete) {
                    Text("🗑️", fontSize = 18.sp)
                }
            }
        }
    }
}
