package com.example.visionbridge.ui.screens.medication

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.visionbridge.R
import com.example.visionbridge.data.MedicationReminder
import com.example.visionbridge.data.SessionManager
import com.example.visionbridge.ui.theme.*
import com.example.visionbridge.utils.TextToSpeechManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayRemindersScreen(
    onBack: () -> Unit,
    viewModel: MedicationSafetyViewModel
) {
    val context = LocalContext.current
    val reminders by viewModel.reminders.collectAsStateWithLifecycle()
    val medications by viewModel.medications.collectAsStateWithLifecycle()

    val sessionManager = remember { SessionManager.getInstance(context) }
    val currentLanguage by sessionManager.language.collectAsStateWithLifecycle()

    val tts = remember { TextToSpeechManager(context) }
    val isSpeaking by tts.isSpeaking.collectAsStateWithLifecycle()

    var showAddDialog by remember { mutableStateOf(false) }
    var reminderToDelete by remember { mutableStateOf<MedicationReminder?>(null) }
    var testNotificationMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(currentLanguage) {
        tts.setLanguage(currentLanguage)
    }

    BackHandler {
        tts.stop()
        onBack()
    }

    // Categorize reminders by time of day
    val morningReminders = remember(reminders) {
        reminders.filter { r ->
            val hour = r.reminderTime.split(":").firstOrNull()?.toIntOrNull() ?: 0
            hour in 4..11
        }.sortedBy { it.reminderTime }
    }

    val afternoonReminders = remember(reminders) {
        reminders.filter { r ->
            val hour = r.reminderTime.split(":").firstOrNull()?.toIntOrNull() ?: 0
            hour in 12..16
        }.sortedBy { it.reminderTime }
    }

    val eveningReminders = remember(reminders) {
        reminders.filter { r ->
            val hour = r.reminderTime.split(":").firstOrNull()?.toIntOrNull() ?: 0
            hour in 17..20
        }.sortedBy { it.reminderTime }
    }

    val nightReminders = remember(reminders) {
        reminders.filter { r ->
            val hour = r.reminderTime.split(":").firstOrNull()?.toIntOrNull() ?: 0
            hour >= 21 || hour < 4
        }.sortedBy { it.reminderTime }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.medsafe_reminders_title),
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        tts.stop()
                        onBack()
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                            tint = TextPrimary
                        )
                    }
                },
                actions = {
                    if (isSpeaking) {
                        IconButton(onClick = { tts.stop() }) {
                            Icon(imageVector = Icons.Default.VolumeOff, contentDescription = "Stop speech", tint = Emergency)
                        }
                    }
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(imageVector = Icons.Default.AddAlarm, contentDescription = "Add New Reminder", tint = Accent)
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
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(10.dp))

            // Hear Schedule Quick Action Card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = AccentDim,
                border = androidx.compose.foundation.BorderStroke(1.dp, Accent)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Daily Schedule Readout",
                            color = Accent,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${reminders.count { it.enabled }} active reminder${if (reminders.count { it.enabled } != 1) "s" else ""}",
                            color = TextMuted,
                            fontSize = 13.sp
                        )
                    }
                    Button(
                        onClick = {
                            if (isSpeaking) {
                                tts.stop()
                            } else {
                                val scheduleSpeech = viewModel.buildTodayRemindersSpeech()
                                tts.speak(scheduleSpeech)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isSpeaking) Emergency else Accent
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = if (isSpeaking) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                            contentDescription = null,
                            tint = BgPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isSpeaking) "Stop" else stringResource(R.string.medsafe_hear_schedule),
                            color = BgPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            testNotificationMessage?.let { msg ->
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = SuccessDim,
                    border = androidx.compose.foundation.BorderStroke(1.dp, Success)
                ) {
                    Text(
                        text = "🔔 $msg",
                        color = Success,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(10.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (reminders.isEmpty()) {
                // Empty state
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(text = "⏰", fontSize = 48.sp)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.medsafe_no_reminders),
                            color = TextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Set daily alarms to receive high-priority alerts for your medication routine with sound and vibration.",
                            color = TextMuted,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 20.sp
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = { showAddDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Accent),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.height(50.dp)
                        ) {
                            Icon(imageVector = Icons.Default.AddAlarm, contentDescription = null, tint = BgPrimary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Set First Reminder", color = BgPrimary, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    if (morningReminders.isNotEmpty()) {
                        item {
                            TimeGroupHeader(title = "Morning", icon = "🌅", timeRange = "04:00 - 11:59")
                        }
                        items(morningReminders, key = { it.id }) { reminder ->
                            ReminderCard(
                                reminder = reminder,
                                onToggle = { enabled -> viewModel.toggleReminder(reminder.id, enabled) },
                                onTestAlarm = {
                                    viewModel.testTriggerReminder(reminder)
                                    testNotificationMessage = "Test notification sent for ${reminder.medicationName}"
                                },
                                onDelete = { reminderToDelete = reminder }
                            )
                        }
                    }

                    if (afternoonReminders.isNotEmpty()) {
                        item {
                            TimeGroupHeader(title = "Afternoon", icon = "☀️", timeRange = "12:00 - 16:59")
                        }
                        items(afternoonReminders, key = { it.id }) { reminder ->
                            ReminderCard(
                                reminder = reminder,
                                onToggle = { enabled -> viewModel.toggleReminder(reminder.id, enabled) },
                                onTestAlarm = {
                                    viewModel.testTriggerReminder(reminder)
                                    testNotificationMessage = "Test notification sent for ${reminder.medicationName}"
                                },
                                onDelete = { reminderToDelete = reminder }
                            )
                        }
                    }

                    if (eveningReminders.isNotEmpty()) {
                        item {
                            TimeGroupHeader(title = "Evening", icon = "🌇", timeRange = "17:00 - 20:59")
                        }
                        items(eveningReminders, key = { it.id }) { reminder ->
                            ReminderCard(
                                reminder = reminder,
                                onToggle = { enabled -> viewModel.toggleReminder(reminder.id, enabled) },
                                onTestAlarm = {
                                    viewModel.testTriggerReminder(reminder)
                                    testNotificationMessage = "Test notification sent for ${reminder.medicationName}"
                                },
                                onDelete = { reminderToDelete = reminder }
                            )
                        }
                    }

                    if (nightReminders.isNotEmpty()) {
                        item {
                            TimeGroupHeader(title = "Night", icon = "🌙", timeRange = "21:00 - 03:59")
                        }
                        items(nightReminders, key = { it.id }) { reminder ->
                            ReminderCard(
                                reminder = reminder,
                                onToggle = { enabled -> viewModel.toggleReminder(reminder.id, enabled) },
                                onTestAlarm = {
                                    viewModel.testTriggerReminder(reminder)
                                    testNotificationMessage = "Test notification sent for ${reminder.medicationName}"
                                },
                                onDelete = { reminderToDelete = reminder }
                            )
                        }
                    }
                }
            }
        }

        // Add Reminder Dialog
        if (showAddDialog) {
            CreateReminderDialog(
                savedMedications = medications.map { it.name },
                onDismiss = { showAddDialog = false },
                onConfirm = { name, time, dosage ->
                    viewModel.createReminder(
                        medicationName = name,
                        reminderTime = time,
                        dosageLabel = dosage
                    ) {
                        tts.speak("Reminder created for $name at $time.")
                    }
                    showAddDialog = false
                }
            )
        }

        // Delete Confirmation Dialog
        reminderToDelete?.let { rem ->
            AlertDialog(
                onDismissRequest = { reminderToDelete = null },
                containerColor = BgCard,
                title = { Text("Delete Reminder", color = TextPrimary, fontWeight = FontWeight.Bold) },
                text = {
                    Text(
                        "Are you sure you want to delete the reminder for ${rem.medicationName} at ${rem.formattedTime12Hr}?",
                        color = TextMuted,
                        fontSize = 15.sp
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.deleteReminder(rem.id)
                            reminderToDelete = null
                            tts.speak("Deleted reminder for ${rem.medicationName}.")
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Emergency)
                    ) {
                        Text("Delete", color = TextPrimary, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { reminderToDelete = null }) {
                        Text("Cancel", color = TextMuted)
                    }
                }
            )
        }
    }
}

@Composable
private fun TimeGroupHeader(
    title: String,
    icon: String,
    timeRange: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = icon, fontSize = 20.sp)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            color = TextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics { heading() }
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "($timeRange)",
            color = TextMuted,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun ReminderCard(
    reminder: MedicationReminder,
    onToggle: (Boolean) -> Unit,
    onTestAlarm: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = BgCard,
        border = androidx.compose.foundation.BorderStroke(1.dp, if (reminder.enabled) Border else Border.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Time & Name
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = reminder.formattedTime12Hr,
                        color = if (reminder.enabled) Accent else TextMuted,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = reminder.medicationName,
                        color = TextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (reminder.dosageLabel.isNotBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = reminder.dosageLabel,
                            color = TextMuted,
                            fontSize = 13.sp
                        )
                    }
                }

                // Toggle Switch
                Switch(
                    checked = reminder.enabled,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Accent,
                        checkedTrackColor = AccentDim,
                        uncheckedThumbColor = TextMuted,
                        uncheckedTrackColor = BgSecondary
                    )
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action row: Test Alarm Now & Delete
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onTestAlarm,
                    colors = ButtonDefaults.buttonColors(containerColor = BgSecondary),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.NotificationsActive,
                        contentDescription = null,
                        tint = Accent,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.medsafe_test_alarm),
                        color = Accent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete reminder",
                        tint = Emergency
                    )
                }
            }
        }
    }
}

@Composable
private fun CreateReminderDialog(
    savedMedications: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (name: String, time: String, dosage: String) -> Unit
) {
    var name by remember { mutableStateOf(savedMedications.firstOrNull() ?: "") }
    var selectedHour by remember { mutableIntStateOf(8) }
    var selectedMinute by remember { mutableIntStateOf(0) }
    var isPm by remember { mutableStateOf(false) }
    var dosage by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BgCard,
        title = {
            Text(
                text = "Add Medication Reminder",
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Medicine Name") },
                    modifier = Modifier.fillMaxWidth()
                )

                // Quick pick from saved meds
                if (savedMedications.isNotEmpty()) {
                    Text(
                        text = "Or pick saved: ${savedMedications.take(3).joinToString(", ")}",
                        color = TextMuted,
                        fontSize = 12.sp
                    )
                }

                // Time picker
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "Hour", color = TextMuted, fontSize = 12.sp)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { selectedHour = if (selectedHour <= 1) 12 else selectedHour - 1 }) {
                                Text("-", color = Accent, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            }
                            Text(
                                text = String.format("%02d", selectedHour),
                                color = TextPrimary,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(onClick = { selectedHour = if (selectedHour >= 12) 1 else selectedHour + 1 }) {
                                Text("+", color = Accent, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Text(":", color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "Minute", color = TextMuted, fontSize = 12.sp)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { selectedMinute = if (selectedMinute <= 0) 55 else selectedMinute - 5 }) {
                                Text("-", color = Accent, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            }
                            Text(
                                text = String.format("%02d", selectedMinute),
                                color = TextPrimary,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(onClick = { selectedMinute = if (selectedMinute >= 55) 0 else selectedMinute + 5 }) {
                                Text("+", color = Accent, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Button(
                        onClick = { isPm = !isPm },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentDim),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Accent)
                    ) {
                        Text(text = if (isPm) "PM" else "AM", color = Accent, fontWeight = FontWeight.Bold)
                    }
                }

                OutlinedTextField(
                    value = dosage,
                    onValueChange = { dosage = it },
                    label = { Text("Dosage instruction (e.g. 1 tablet)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        val militaryHour = when {
                            isPm && selectedHour < 12 -> selectedHour + 12
                            !isPm && selectedHour == 12 -> 0
                            else -> selectedHour
                        }
                        val timeString = String.format("%02d:%02d", militaryHour, selectedMinute)
                        onConfirm(name.trim(), timeString, dosage.trim())
                    }
                },
                enabled = name.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = Accent)
            ) {
                Text("Add Reminder", color = BgPrimary, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextMuted)
            }
        }
    )
}
