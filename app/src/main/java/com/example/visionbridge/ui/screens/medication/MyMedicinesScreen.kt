package com.example.visionbridge.ui.screens.medication

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.visionbridge.R
import com.example.visionbridge.data.Medication
import com.example.visionbridge.data.SessionManager
import com.example.visionbridge.ui.theme.*
import com.example.visionbridge.utils.TextToSpeechManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyMedicinesScreen(
    onBack: () -> Unit,
    onNavigateToScan: () -> Unit,
    viewModel: MedicationSafetyViewModel
) {
    val context = LocalContext.current
    val medications by viewModel.medications.collectAsStateWithLifecycle()
    val reminders by viewModel.reminders.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()

    val sessionManager = remember { SessionManager.getInstance(context) }
    val currentLanguage by sessionManager.language.collectAsStateWithLifecycle()

    val tts = remember { TextToSpeechManager(context) }
    val isSpeaking by tts.isSpeaking.collectAsStateWithLifecycle()

    var medicationToEdit by remember { mutableStateOf<Medication?>(null) }
    var medicationToDelete by remember { mutableStateOf<Medication?>(null) }
    var medicationForReminder by remember { mutableStateOf<Medication?>(null) }

    LaunchedEffect(currentLanguage) {
        tts.setLanguage(currentLanguage)
    }

    BackHandler {
        tts.stop()
        onBack()
    }

    val filteredMedications = remember(medications, searchQuery) {
        if (searchQuery.isBlank()) {
            medications
        } else {
            val query = searchQuery.trim().lowercase()
            medications.filter { med ->
                med.name.lowercase().contains(query) ||
                        med.activeIngredients.any { it.lowercase().contains(query) } ||
                        med.strength.lowercase().contains(query) ||
                        med.form.lowercase().contains(query)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.medsafe_cabinet_title),
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
                    IconButton(onClick = onNavigateToScan) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = "Scan New Medicine", tint = Accent)
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
            // Search field
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                placeholder = { Text(stringResource(R.string.medsafe_search_placeholder), color = TextMuted) },
                leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = TextMuted) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setSearchQuery("") }) {
                            Icon(imageVector = Icons.Default.Clear, contentDescription = "Clear search", tint = TextMuted)
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedBorderColor = Accent,
                    unfocusedBorderColor = Border,
                    focusedContainerColor = BgCard,
                    unfocusedContainerColor = BgCard
                )
            )

            if (filteredMedications.isEmpty()) {
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
                        Text(text = "💊", fontSize = 48.sp)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (searchQuery.isNotBlank()) "No medicines match your search" else stringResource(R.string.medsafe_no_meds),
                            color = TextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Save medicines after scanning to review packaging details, active ingredients, and set reminders anytime.",
                            color = TextMuted,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 20.sp
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = onNavigateToScan,
                            colors = ButtonDefaults.buttonColors(containerColor = Accent),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.height(50.dp)
                        ) {
                            Icon(imageVector = Icons.Default.CameraAlt, contentDescription = null, tint = BgPrimary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Scan New Medicine", color = BgPrimary, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(filteredMedications, key = { it.id }) { medication ->
                        val matchingReminders = reminders.filter {
                            it.medicationId == medication.id || it.medicationName.equals(medication.name, ignoreCase = true)
                        }

                        MedicationCard(
                            medication = medication,
                            remindersCount = matchingReminders.size,
                            onReadAloud = {
                                val speech = buildString {
                                    append("${medication.name}. ")
                                    if (medication.strength.isNotBlank()) append("Strength ${medication.strength}. ")
                                    if (medication.form.isNotBlank()) append("Form ${medication.form}. ")
                                    if (medication.activeIngredients.isNotEmpty()) {
                                        append("Active ingredients: ${medication.activeIngredients.joinToString(", ")}. ")
                                    }
                                    if (medication.isExpired) {
                                        append("Warning: This medicine is past its expiry date of ${medication.expiryDate}. ")
                                    } else if (medication.expiryDate.isNotBlank()) {
                                        append("Expiry date is ${medication.expiryDate}. ")
                                    }
                                    if (medication.printedDirections.isNotBlank()) {
                                        append("Packaging directions: ${medication.printedDirections}. ")
                                    }
                                    if (medication.storageInformation.isNotBlank()) {
                                        append("Storage: ${medication.storageInformation}. ")
                                    }
                                    if (matchingReminders.isNotEmpty()) {
                                        append("${matchingReminders.size} reminders configured.")
                                    }
                                }
                                tts.speak(speech)
                            },
                            onAddReminder = { medicationForReminder = medication },
                            onEdit = { medicationToEdit = medication },
                            onDelete = { medicationToDelete = medication }
                        )
                    }
                }
            }
        }

        // Edit Dialog
        medicationToEdit?.let { med ->
            EditMedicationDialog(
                medication = med,
                onDismiss = { medicationToEdit = null },
                onSave = { updated ->
                    viewModel.updateMedication(updated)
                    medicationToEdit = null
                    tts.speak("Updated ${updated.name}.")
                }
            )
        }

        // Delete Confirmation Dialog
        medicationToDelete?.let { med ->
            AlertDialog(
                onDismissRequest = { medicationToDelete = null },
                containerColor = BgCard,
                title = { Text("Delete Medication", color = TextPrimary, fontWeight = FontWeight.Bold) },
                text = {
                    Text(
                        "Are you sure you want to delete ${med.name} from your medicines?",
                        color = TextMuted,
                        fontSize = 15.sp
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.deleteMedication(med.id)
                            medicationToDelete = null
                            tts.speak("Deleted ${med.name}.")
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Emergency)
                    ) {
                        Text("Delete", color = TextPrimary, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { medicationToDelete = null }) {
                        Text("Cancel", color = TextMuted)
                    }
                }
            )
        }

        // Add Reminder Dialog
        medicationForReminder?.let { med ->
            AddReminderQuickDialog(
                medicineName = med.name,
                defaultDosage = med.strength,
                onDismiss = { medicationForReminder = null },
                onConfirm = { time, dosage ->
                    viewModel.createReminder(
                        medicationName = med.name,
                        reminderTime = time,
                        dosageLabel = dosage,
                        medicationId = med.id
                    ) {
                        tts.speak("Reminder created for ${med.name} at $time.")
                    }
                    medicationForReminder = null
                }
            )
        }
    }
}

@Composable
private fun MedicationCard(
    medication: Medication,
    remindersCount: Int,
    onReadAloud: () -> Unit,
    onAddReminder: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = BgCard,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (medication.isExpired) Emergency else Border
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Name & Strength
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = medication.name,
                        color = TextPrimary,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold
                    )
                    val details = listOfNotNull(
                        medication.strength.takeIf { it.isNotBlank() },
                        medication.form.takeIf { it.isNotBlank() }
                    ).joinToString(" • ")
                    if (details.isNotBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(text = details, color = Accent, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    }
                }

                // Badges
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (medication.isExpired) {
                        Surface(shape = RoundedCornerShape(6.dp), color = EmergencyDim) {
                            Text(
                                text = "EXPIRED",
                                color = Emergency,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    } else if (medication.expiryDate.isNotBlank()) {
                        Surface(shape = RoundedCornerShape(6.dp), color = BgSecondary) {
                            Text(
                                text = "Exp: ${medication.expiryDate}",
                                color = TextMuted,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }

            // Active ingredients
            if (medication.activeIngredients.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Active: ${medication.activeIngredients.joinToString(", ")}",
                    color = TextMuted,
                    fontSize = 13.sp
                )
            }

            // Reminders count pill
            if (remindersCount > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = AccentDim
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.NotificationsActive,
                            contentDescription = null,
                            tint = Accent,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "$remindersCount daily reminder${if (remindersCount > 1) "s" else ""}",
                            color = Accent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onReadAloud,
                        colors = ButtonDefaults.buttonColors(containerColor = AccentDim),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(imageVector = Icons.Default.VolumeUp, contentDescription = null, tint = Accent, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Read", color = Accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = onAddReminder,
                        colors = ButtonDefaults.buttonColors(containerColor = BgSecondary),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(imageVector = Icons.Default.AlarmAdd, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Remind", color = TextPrimary, fontSize = 13.sp)
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = onEdit) {
                        Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit ${medication.name}", tint = TextMuted)
                    }
                    IconButton(onClick = onDelete) {
                        Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete ${medication.name}", tint = Emergency)
                    }
                }
            }
        }
    }
}

@Composable
private fun EditMedicationDialog(
    medication: Medication,
    onDismiss: () -> Unit,
    onSave: (Medication) -> Unit
) {
    var name by remember { mutableStateOf(medication.name) }
    var strength by remember { mutableStateOf(medication.strength) }
    var form by remember { mutableStateOf(medication.form) }
    var printedDirections by remember { mutableStateOf(medication.printedDirections) }
    var expiryDate by remember { mutableStateOf(medication.expiryDate) }
    var storage by remember { mutableStateOf(medication.storageInformation) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BgCard,
        title = { Text("Edit Medication Details", color = TextPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Medicine Name") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = strength,
                    onValueChange = { strength = it },
                    label = { Text("Strength (e.g. 500mg)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = form,
                    onValueChange = { form = it },
                    label = { Text("Form (e.g. Tablet, Syrup)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = expiryDate,
                    onValueChange = { expiryDate = it },
                    label = { Text("Expiry Date (e.g. 12/2026)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = printedDirections,
                    onValueChange = { printedDirections = it },
                    label = { Text("Printed Directions") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = storage,
                    onValueChange = { storage = it },
                    label = { Text("Storage Instructions") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        medication.copy(
                            name = name.trim(),
                            strength = strength.trim(),
                            form = form.trim(),
                            printedDirections = printedDirections.trim(),
                            expiryDate = expiryDate.trim(),
                            storageInformation = storage.trim()
                        )
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = Accent)
            ) {
                Text("Save Changes", color = BgPrimary, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextMuted)
            }
        }
    )
}
