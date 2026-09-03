package com.example.visionbridge.ui.screens.admin

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.visionbridge.R
import com.example.visionbridge.data.*
import com.example.visionbridge.ui.components.ConfirmLogoutDialog
import com.example.visionbridge.ui.components.LanguageSelector
import com.example.visionbridge.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboardScreen(
    onLogout: () -> Unit,
    onNavigateBack: (() -> Unit)? = null,
    viewModel: AdminViewModel = viewModel()
) {
    val context = LocalContext.current
    val sessionManager = remember { SessionManager.getInstance(context) }
    val currentLanguage by sessionManager.language.collectAsStateWithLifecycle()

    val activeSection by viewModel.activeSection.collectAsStateWithLifecycle()
    val isRealtimeConnected by viewModel.isRealtimeConnected.collectAsStateWithLifecycle()
    val adminUser by viewModel.adminUser.collectAsStateWithLifecycle()

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var showLogoutDialog by remember { mutableStateOf(false) }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = BgSecondary,
                drawerContentColor = TextPrimary,
                modifier = Modifier.width(310.dp)
            ) {
                // Drawer Header
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(BgCard)
                        .padding(20.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "VisionBridge",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = Accent
                        )
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = AccentDim
                        ) {
                            Text(
                                text = stringResource(R.string.admin_dashboard_badge),
                                color = Accent,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = adminUser?.name?.takeIf { it.isNotBlank() } ?: "Administrator",
                        color = TextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "@${adminUser?.username ?: "admin"}",
                        color = TextMuted,
                        fontSize = 13.sp
                    )
                }

                HorizontalDivider(color = BorderSubtle)

                // Navigation Items
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 12.dp, horizontal = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    AdminDrawerItem(
                        icon = "📊",
                        label = stringResource(R.string.admin_nav_overview),
                        isSelected = activeSection == AdminSection.OVERVIEW,
                        onClick = {
                            viewModel.setSection(AdminSection.OVERVIEW)
                            scope.launch { drawerState.close() }
                        }
                    )
                    AdminDrawerItem(
                        icon = "🧑‍🦯",
                        label = stringResource(R.string.admin_nav_users),
                        isSelected = activeSection == AdminSection.USERS,
                        onClick = {
                            viewModel.setSection(AdminSection.USERS)
                            scope.launch { drawerState.close() }
                        }
                    )
                    AdminDrawerItem(
                        icon = "🤝",
                        label = stringResource(R.string.admin_nav_volunteers),
                        isSelected = activeSection == AdminSection.VOLUNTEERS,
                        onClick = {
                            viewModel.setSection(AdminSection.VOLUNTEERS)
                            scope.launch { drawerState.close() }
                        }
                    )
                    AdminDrawerItem(
                        icon = "📞",
                        label = stringResource(R.string.admin_nav_call_logs),
                        isSelected = activeSection == AdminSection.CALL_LOGS,
                        onClick = {
                            viewModel.setSection(AdminSection.CALL_LOGS)
                            scope.launch { drawerState.close() }
                        }
                    )
                    AdminDrawerItem(
                        icon = "🚨",
                        label = stringResource(R.string.admin_nav_emergency),
                        isSelected = activeSection == AdminSection.EMERGENCY,
                        onClick = {
                            viewModel.setSection(AdminSection.EMERGENCY)
                            scope.launch { drawerState.close() }
                        }
                    )
                    AdminDrawerItem(
                        icon = "👤",
                        label = stringResource(R.string.admin_nav_profile),
                        isSelected = activeSection == AdminSection.PROFILE,
                        onClick = {
                            viewModel.setSection(AdminSection.PROFILE)
                            scope.launch { drawerState.close() }
                        }
                    )
                }

                HorizontalDivider(color = BorderSubtle)

                // Logout Item in Drawer
                NavigationDrawerItem(
                    icon = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Logout,
                            contentDescription = null,
                            tint = Emergency,
                            modifier = Modifier.size(22.dp)
                        )
                    },
                    label = {
                        Text(
                            text = stringResource(R.string.auth_logout),
                            color = Emergency,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        showLogoutDialog = true
                    },
                    modifier = Modifier.padding(12.dp),
                    colors = NavigationDrawerItemDefaults.colors(
                        unselectedContainerColor = Color.Transparent
                    )
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = when (activeSection) {
                                        AdminSection.OVERVIEW -> stringResource(R.string.admin_nav_overview)
                                        AdminSection.USERS -> stringResource(R.string.admin_nav_users)
                                        AdminSection.VOLUNTEERS -> stringResource(R.string.admin_nav_volunteers)
                                        AdminSection.CALL_LOGS -> stringResource(R.string.admin_nav_call_logs)
                                        AdminSection.EMERGENCY -> stringResource(R.string.admin_nav_emergency)
                                        AdminSection.PROFILE -> stringResource(R.string.admin_nav_profile)
                                    },
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    color = TextPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                if (isRealtimeConnected) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(Success)
                                    )
                                }
                            }
                            Text(
                                text = when (activeSection) {
                                    AdminSection.OVERVIEW -> stringResource(R.string.admin_sub_overview)
                                    AdminSection.USERS -> stringResource(R.string.admin_sub_users)
                                    AdminSection.VOLUNTEERS -> stringResource(R.string.admin_sub_volunteers)
                                    AdminSection.CALL_LOGS -> stringResource(R.string.admin_sub_call_logs)
                                    AdminSection.EMERGENCY -> stringResource(R.string.admin_sub_emergency)
                                    AdminSection.PROFILE -> stringResource(R.string.admin_sub_profile)
                                },
                                fontSize = 11.sp,
                                color = TextMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = { scope.launch { drawerState.open() } },
                            modifier = Modifier.semantics {
                                contentDescription = "Open Admin Navigation Menu"
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = null,
                                tint = Accent
                            )
                        }
                    },
                    actions = {
                        LanguageSelector(
                            currentLanguageCode = currentLanguage,
                            onLanguageSelected = { langCode ->
                                sessionManager.setLanguage(langCode)
                            }
                        )
                        IconButton(
                            onClick = { viewModel.refreshCurrentSection() },
                            modifier = Modifier.semantics {
                                contentDescription = "Refresh Admin Data"
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                tint = TextMuted,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = BgPrimary,
                        titleContentColor = TextPrimary
                    )
                )
            },
            containerColor = BgPrimary
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(BgPrimary)
            ) {
                when (activeSection) {
                    AdminSection.OVERVIEW -> OverviewSectionView(viewModel = viewModel)
                    AdminSection.USERS -> UsersSectionView(viewModel = viewModel)
                    AdminSection.VOLUNTEERS -> VolunteersSectionView(viewModel = viewModel)
                    AdminSection.CALL_LOGS -> CallLogsSectionView(viewModel = viewModel)
                    AdminSection.EMERGENCY -> EmergencySosSectionView(viewModel = viewModel)
                    AdminSection.PROFILE -> ProfileSectionView(
                        user = adminUser,
                        onLogoutClick = { showLogoutDialog = true }
                    )
                }
            }
        }
    }

    if (showLogoutDialog) {
        ConfirmLogoutDialog(
            onConfirm = {
                showLogoutDialog = false
                sessionManager.clearUser()
                onLogout()
            },
            onDismiss = { showLogoutDialog = false }
        )
    }
}

// ─────────────────────────────────────────────────────────────────
// 1. OVERVIEW SECTION
// ─────────────────────────────────────────────────────────────────
@Composable
private fun OverviewSectionView(viewModel: AdminViewModel) {
    val stats by viewModel.overviewStats.collectAsStateWithLifecycle()
    val loading by viewModel.overviewLoading.collectAsStateWithLifecycle()
    val error by viewModel.overviewError.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()

    if (loading && stats == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Accent)
        }
        return
    }

    if (!error.isNullOrBlank() && stats == null) {
        ErrorState(message = error ?: stringResource(R.string.admin_err_fetch_failed), onRetry = { viewModel.loadOverview() })
        return
    }

    val s = stats ?: AdminOverviewStats()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Pulse banner
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = BgCard,
            border = androidx.compose.foundation.BorderStroke(1.dp, Border)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "⚡", fontSize = 18.sp, modifier = Modifier.padding(end = 8.dp))
                    Text(
                        text = stringResource(R.string.admin_realtime_badge),
                        color = Accent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = "${s.activeCalls} active calls",
                    color = if (s.activeCalls > 0) Warning else TextMuted,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // Stats 2-Column Grid
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            AdminStatCard(
                icon = "🧑‍🦯",
                value = s.totalUsers.toString(),
                label = stringResource(R.string.admin_stat_total_users),
                modifier = Modifier.weight(1f),
                onClick = { viewModel.setSection(AdminSection.USERS) }
            )
            AdminStatCard(
                icon = "🤝",
                value = s.totalVolunteers.toString(),
                label = stringResource(R.string.admin_stat_total_volunteers),
                modifier = Modifier.weight(1f),
                onClick = { viewModel.setSection(AdminSection.VOLUNTEERS) }
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            AdminStatCard(
                icon = "🟢",
                value = s.activeVolunteers.toString(),
                label = stringResource(R.string.admin_stat_active_volunteers),
                accentColor = Success,
                modifier = Modifier.weight(1f),
                onClick = { viewModel.setSection(AdminSection.VOLUNTEERS) }
            )
            AdminStatCard(
                icon = "📨",
                value = s.totalHelpRequests.toString(),
                label = stringResource(R.string.admin_stat_total_requests),
                modifier = Modifier.weight(1f),
                onClick = { viewModel.setSection(AdminSection.CALL_LOGS) }
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            AdminStatCard(
                icon = "📞",
                value = s.activeCalls.toString(),
                label = stringResource(R.string.admin_stat_active_calls),
                accentColor = if (s.activeCalls > 0) Warning else TextPrimary,
                modifier = Modifier.weight(1f),
                onClick = { viewModel.setSection(AdminSection.CALL_LOGS) }
            )
            AdminStatCard(
                icon = "✅",
                value = s.completedCalls.toString(),
                label = stringResource(R.string.admin_stat_completed_calls),
                accentColor = Success,
                modifier = Modifier.weight(1f),
                onClick = { viewModel.setSection(AdminSection.CALL_LOGS) }
            )
        }

        if (s.activeSos > 0) {
            AdminStatCard(
                icon = "🚨",
                value = s.activeSos.toString(),
                label = stringResource(R.string.admin_stat_active_sos),
                accentColor = Emergency,
                modifier = Modifier.fillMaxWidth(),
                onClick = { viewModel.setSection(AdminSection.EMERGENCY) }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Quick Action Shortcuts
        Text(
            text = stringResource(R.string.admin_quick_actions_title),
            color = TextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics { heading() }
        )

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = { viewModel.setSection(AdminSection.USERS) },
                modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BgCard)
            ) {
                Text("🧑‍🦯 ${stringResource(R.string.admin_nav_users)}", color = TextPrimary, fontSize = 14.sp)
            }
            Button(
                onClick = { viewModel.setSection(AdminSection.VOLUNTEERS) },
                modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BgCard)
            ) {
                Text("🤝 ${stringResource(R.string.admin_nav_volunteers)}", color = TextPrimary, fontSize = 14.sp)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = { viewModel.setSection(AdminSection.CALL_LOGS) },
                modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BgCard)
            ) {
                Text("📞 ${stringResource(R.string.admin_nav_call_logs)}", color = TextPrimary, fontSize = 14.sp)
            }
            Button(
                onClick = { viewModel.setSection(AdminSection.EMERGENCY) },
                modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BgCard)
            ) {
                Text("🚨 ${stringResource(R.string.admin_nav_emergency)}", color = TextPrimary, fontSize = 14.sp)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────
// 2. USERS MANAGEMENT SECTION
// ─────────────────────────────────────────────────────────────────
@Composable
private fun UsersSectionView(viewModel: AdminViewModel) {
    val users by viewModel.users.collectAsStateWithLifecycle()
    val search by viewModel.usersSearch.collectAsStateWithLifecycle()
    val page by viewModel.usersPage.collectAsStateWithLifecycle()
    val pagination by viewModel.usersPagination.collectAsStateWithLifecycle()
    val loading by viewModel.usersLoading.collectAsStateWithLifecycle()
    val error by viewModel.usersError.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 10.dp)) {
        // Search Input
        OutlinedTextField(
            value = search,
            onValueChange = { viewModel.setUsersSearch(it) },
            placeholder = { Text(stringResource(R.string.admin_users_search_placeholder), color = TextMuted, fontSize = 14.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Accent) },
            trailingIcon = {
                if (search.isNotBlank()) {
                    IconButton(onClick = { viewModel.setUsersSearch("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear", tint = TextMuted)
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = BgCard,
                unfocusedContainerColor = BgCard,
                focusedBorderColor = Accent,
                unfocusedBorderColor = BorderSubtle,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            ),
            modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (loading) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Accent)
            }
        } else if (!error.isNullOrBlank()) {
            ErrorState(message = error ?: "", onRetry = { viewModel.loadUsers() })
        } else if (users.isEmpty()) {
            EmptyState(message = stringResource(R.string.admin_no_results))
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(users, key = { it.id }) { user ->
                    UserCard(user = user)
                }
                item {
                    PaginationControls(
                        page = page,
                        totalPages = pagination.totalPages,
                        onPageChange = { viewModel.setUsersPage(it) }
                    )
                }
            }
        }
    }
}

@Composable
private fun UserCard(user: AdminUserItem) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = BgCard,
        border = androidx.compose.foundation.BorderStroke(1.dp, Border),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "🧑‍🦯", fontSize = 24.sp, modifier = Modifier.padding(end = 10.dp))
                    Column {
                        Text(text = user.name, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(text = "@${user.username}", color = Accent, fontSize = 13.sp)
                    }
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = AccentDim
                ) {
                    Text(
                        text = user.role,
                        color = Accent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = BorderSubtle)
            Spacer(modifier = Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(text = stringResource(R.string.admin_users_col_registered), color = TextMuted, fontSize = 12.sp)
                Text(text = formatDateDisplay(user.createdAt), color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }

            if (!user.emergencyWhatsappNumber.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text(text = stringResource(R.string.admin_users_col_emergency_contact), color = TextMuted, fontSize = 12.sp)
                    Text(text = user.emergencyWhatsappNumber, color = Success, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────
// 3. VOLUNTEERS MANAGEMENT SECTION
// ─────────────────────────────────────────────────────────────────
@Composable
private fun VolunteersSectionView(viewModel: AdminViewModel) {
    val volunteers by viewModel.volunteers.collectAsStateWithLifecycle()
    val search by viewModel.volunteersSearch.collectAsStateWithLifecycle()
    val availability by viewModel.volunteersAvailability.collectAsStateWithLifecycle()
    val page by viewModel.volunteersPage.collectAsStateWithLifecycle()
    val pagination by viewModel.volunteersPagination.collectAsStateWithLifecycle()
    val loading by viewModel.volunteersLoading.collectAsStateWithLifecycle()
    val error by viewModel.volunteersError.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 10.dp)) {
        // Search Input
        OutlinedTextField(
            value = search,
            onValueChange = { viewModel.setVolunteersSearch(it) },
            placeholder = { Text(stringResource(R.string.admin_volunteers_search_placeholder), color = TextMuted, fontSize = 14.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Accent) },
            trailingIcon = {
                if (search.isNotBlank()) {
                    IconButton(onClick = { viewModel.setVolunteersSearch("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear", tint = TextMuted)
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = BgCard,
                unfocusedContainerColor = BgCard,
                focusedBorderColor = Accent,
                unfocusedBorderColor = BorderSubtle,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            ),
            modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp)
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Availability Filter Chips
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            FilterChip(
                selected = availability == "all",
                onClick = { viewModel.setVolunteersAvailability("all") },
                label = { Text(stringResource(R.string.admin_volunteers_filter_all)) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Accent,
                    selectedLabelColor = BgPrimary,
                    containerColor = BgCard,
                    labelColor = TextMuted
                )
            )
            FilterChip(
                selected = availability == "available",
                onClick = { viewModel.setVolunteersAvailability("available") },
                label = { Text(stringResource(R.string.admin_volunteers_available)) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Success,
                    selectedLabelColor = BgPrimary,
                    containerColor = BgCard,
                    labelColor = TextMuted
                )
            )
            FilterChip(
                selected = availability == "unavailable",
                onClick = { viewModel.setVolunteersAvailability("unavailable") },
                label = { Text(stringResource(R.string.admin_volunteers_unavailable)) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Emergency,
                    selectedLabelColor = BgPrimary,
                    containerColor = BgCard,
                    labelColor = TextMuted
                )
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (loading) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Accent)
            }
        } else if (!error.isNullOrBlank()) {
            ErrorState(message = error ?: "", onRetry = { viewModel.loadVolunteers() })
        } else if (volunteers.isEmpty()) {
            EmptyState(message = stringResource(R.string.admin_no_results))
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(volunteers, key = { it.id }) { volunteer ->
                    VolunteerCard(volunteer = volunteer)
                }
                item {
                    PaginationControls(
                        page = page,
                        totalPages = pagination.totalPages,
                        onPageChange = { viewModel.setVolunteersPage(it) }
                    )
                }
            }
        }
    }
}

@Composable
private fun VolunteerCard(volunteer: AdminVolunteerItem) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = BgCard,
        border = androidx.compose.foundation.BorderStroke(1.dp, Border),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "🤝", fontSize = 24.sp, modifier = Modifier.padding(end = 10.dp))
                    Column {
                        Text(text = volunteer.name, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(text = "@${volunteer.username}", color = Accent, fontSize = 13.sp)
                    }
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (volunteer.availability) SuccessDim else EmergencyDim
                ) {
                    Text(
                        text = if (volunteer.availability) stringResource(R.string.admin_volunteers_available) else stringResource(R.string.admin_volunteers_unavailable),
                        color = if (volunteer.availability) Success else Emergency,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = BorderSubtle)
            Spacer(modifier = Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    Text(text = volunteer.acceptedRequests.toString(), color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text(text = stringResource(R.string.admin_volunteers_accepted_requests), color = TextMuted, fontSize = 11.sp)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    Text(text = volunteer.completedRequests.toString(), color = Success, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text(text = stringResource(R.string.admin_volunteers_completed_requests), color = TextMuted, fontSize = 11.sp)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    Text(text = formatDateDisplay(volunteer.createdAt), color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Text(text = stringResource(R.string.admin_users_col_registered), color = TextMuted, fontSize = 11.sp)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────
// 4. CALL LOGS SECTION
// ─────────────────────────────────────────────────────────────────
@Composable
private fun CallLogsSectionView(viewModel: AdminViewModel) {
    val callLogs by viewModel.callLogs.collectAsStateWithLifecycle()
    val search by viewModel.callLogsSearch.collectAsStateWithLifecycle()
    val status by viewModel.callLogsStatus.collectAsStateWithLifecycle()
    val sort by viewModel.callLogsSort.collectAsStateWithLifecycle()
    val page by viewModel.callLogsPage.collectAsStateWithLifecycle()
    val pagination by viewModel.callLogsPagination.collectAsStateWithLifecycle()
    val loading by viewModel.callLogsLoading.collectAsStateWithLifecycle()
    val error by viewModel.callLogsError.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 10.dp)) {
        // Search Input
        OutlinedTextField(
            value = search,
            onValueChange = { viewModel.setCallLogsSearch(it) },
            placeholder = { Text(stringResource(R.string.admin_call_logs_search_placeholder), color = TextMuted, fontSize = 14.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Accent) },
            trailingIcon = {
                if (search.isNotBlank()) {
                    IconButton(onClick = { viewModel.setCallLogsSearch("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear", tint = TextMuted)
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = BgCard,
                unfocusedContainerColor = BgCard,
                focusedBorderColor = Accent,
                unfocusedBorderColor = BorderSubtle,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            ),
            modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp)
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Filters row
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    selected = status == "all",
                    onClick = { viewModel.setCallLogsStatus("all") },
                    label = { Text(stringResource(R.string.admin_volunteers_filter_all), fontSize = 12.sp) }
                )
                FilterChip(
                    selected = status.equals("ACTIVE", ignoreCase = true),
                    onClick = { viewModel.setCallLogsStatus("ACTIVE") },
                    label = { Text(stringResource(R.string.admin_status_active), fontSize = 12.sp) }
                )
                FilterChip(
                    selected = status.equals("COMPLETED", ignoreCase = true),
                    onClick = { viewModel.setCallLogsStatus("COMPLETED") },
                    label = { Text(stringResource(R.string.admin_status_completed), fontSize = 12.sp) }
                )
            }

            TextButton(onClick = { viewModel.toggleCallLogsSort() }) {
                Text(
                    text = if (sort == "latest") "⬇️ ${stringResource(R.string.admin_call_logs_sort_latest)}" else "⬆️ ${stringResource(R.string.admin_call_logs_sort_oldest)}",
                    color = Accent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (loading) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Accent)
            }
        } else if (!error.isNullOrBlank()) {
            ErrorState(message = error ?: "", onRetry = { viewModel.loadCallLogs() })
        } else if (callLogs.isEmpty()) {
            EmptyState(message = stringResource(R.string.admin_no_results))
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(callLogs, key = { it.id }) { log ->
                    CallLogCard(log = log)
                }
                item {
                    PaginationControls(
                        page = page,
                        totalPages = pagination.totalPages,
                        onPageChange = { viewModel.setCallLogsPage(it) }
                    )
                }
            }
        }
    }
}

@Composable
private fun CallLogCard(log: AdminCallLogItem) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = BgCard,
        border = androidx.compose.foundation.BorderStroke(1.dp, Border),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Participants Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Text(text = "🧑‍🦯 ${log.userName}", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(text = " ➔ ", color = Accent, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(text = "🤝 ${log.volunteerName}", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                CallStatusBadge(status = log.status)
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = BorderSubtle)
            Spacer(modifier = Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(text = stringResource(R.string.admin_call_logs_duration), color = TextMuted, fontSize = 12.sp)
                Text(
                    text = formatDurationDisplay(log.durationSec),
                    color = Accent,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(text = stringResource(R.string.admin_call_logs_start), color = TextMuted, fontSize = 12.sp)
                Text(text = formatDateTimeDisplay(log.startedAt), color = TextPrimary, fontSize = 12.sp)
            }

            if (!log.endedAt.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text(text = stringResource(R.string.admin_call_logs_end), color = TextMuted, fontSize = 12.sp)
                    Text(text = formatDateTimeDisplay(log.endedAt), color = TextPrimary, fontSize = 12.sp)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────
// 5. EMERGENCY SOS SECTION
// ─────────────────────────────────────────────────────────────────
@Composable
private fun EmergencySosSectionView(viewModel: AdminViewModel) {
    val events by viewModel.emergencyEvents.collectAsStateWithLifecycle()
    val loading by viewModel.emergencyLoading.collectAsStateWithLifecycle()
    val error by viewModel.emergencyError.collectAsStateWithLifecycle()
    var eventToResolve by remember { mutableStateOf<AdminEmergencyItem?>(null) }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 10.dp)) {
        if (loading && events.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Emergency)
            }
            return
        }

        if (!error.isNullOrBlank() && events.isEmpty()) {
            ErrorState(message = error ?: "", onRetry = { viewModel.loadEmergencyEvents() })
            return
        }

        if (events.isEmpty()) {
            EmptyState(message = "No emergency SOS events on record.")
            return
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(events, key = { it.id }) { event ->
                EmergencyEventCard(
                    event = event,
                    onResolveClick = { eventToResolve = event }
                )
            }
        }
    }

    if (eventToResolve != null) {
        AlertDialog(
            onDismissRequest = { eventToResolve = null },
            title = { Text(stringResource(R.string.admin_sos_resolve_btn), fontWeight = FontWeight.Bold, color = TextPrimary) },
            text = { Text(stringResource(R.string.admin_sos_confirm_resolve), color = TextMuted) },
            confirmButton = {
                Button(
                    onClick = {
                        val id = eventToResolve?.id
                        eventToResolve = null
                        if (id != null) {
                            viewModel.resolveEmergency(id)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Success)
                ) {
                    Text(stringResource(R.string.common_confirm), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { eventToResolve = null }) {
                    Text(stringResource(R.string.common_cancel), color = TextMuted)
                }
            },
            containerColor = BgCard
        )
    }
}

@Composable
private fun EmergencyEventCard(
    event: AdminEmergencyItem,
    onResolveClick: () -> Unit
) {
    val isActive = event.status.equals("ACTIVE", ignoreCase = true)
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (isActive) EmergencyDim else BgCard,
        border = androidx.compose.foundation.BorderStroke(1.5.dp, if (isActive) Emergency else Border),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "🚨", fontSize = 22.sp, modifier = Modifier.padding(end = 8.dp))
                    Column {
                        Text(text = event.userName, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(text = "@${event.userUsername}", color = Accent, fontSize = 12.sp)
                    }
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isActive) Emergency else BgSecondary
                ) {
                    Text(
                        text = if (isActive) stringResource(R.string.admin_status_active) else stringResource(R.string.admin_status_ended),
                        color = if (isActive) BgPrimary else TextMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = BorderSubtle)
            Spacer(modifier = Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(text = stringResource(R.string.admin_sos_location), color = TextMuted, fontSize = 12.sp)
                Text(
                    text = "%.4f, %.4f".format(event.latitude, event.longitude),
                    color = Accent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(text = "WhatsApp Dispatch", color = TextMuted, fontSize = 12.sp)
                Text(
                    text = if (event.whatsappSent) stringResource(R.string.admin_sos_whatsapp_sent) else stringResource(R.string.admin_sos_whatsapp_failed),
                    color = if (event.whatsappSent) Success else Warning,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(text = "Alert Time", color = TextMuted, fontSize = 12.sp)
                Text(text = formatDateTimeDisplay(event.createdAt), color = TextPrimary, fontSize = 12.sp)
            }

            if (isActive) {
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onResolveClick,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Success)
                ) {
                    Text(stringResource(R.string.admin_sos_resolve_btn), fontWeight = FontWeight.Bold, color = BgPrimary)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────
// 6. PROFILE SECTION
// ─────────────────────────────────────────────────────────────────
@Composable
private fun ProfileSectionView(
    user: User?,
    onLogoutClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.height(10.dp))

        Box(
            modifier = Modifier
                .size(90.dp)
                .clip(CircleShape)
                .background(AccentDim)
                .border(2.dp, Accent, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "👤", fontSize = 44.sp)
        }

        Text(
            text = user?.name?.takeIf { it.isNotBlank() } ?: "Administrator",
            color = TextPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )

        Surface(
            shape = RoundedCornerShape(10.dp),
            color = AccentDim
        ) {
            Text(
                text = stringResource(R.string.admin_profile_role_title),
                color = Accent,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = BgCard,
            border = androidx.compose.foundation.BorderStroke(1.dp, Border),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ProfileDetailRow(label = stringResource(R.string.admin_users_col_username), value = "@${user?.username ?: "admin"}")
                ProfileDetailRow(label = stringResource(R.string.admin_users_col_role), value = user?.role ?: "admin")
                ProfileDetailRow(label = "Security Mode", value = "Strict Row-Level Security")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onLogoutClick,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 54.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Emergency)
        ) {
            Text(
                text = stringResource(R.string.admin_btn_sign_out),
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        }
    }
}

@Composable
private fun ProfileDetailRow(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
        Text(text = label, color = TextMuted, fontSize = 14.sp)
        Text(text = value, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
}

// ─────────────────────────────────────────────────────────────────
// Shared UI Components
// ─────────────────────────────────────────────────────────────────
@Composable
private fun AdminDrawerItem(
    icon: String,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) AccentDim else Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = icon, fontSize = 20.sp, modifier = Modifier.padding(end = 14.dp))
            Text(
                text = label,
                color = if (isSelected) Accent else TextPrimary,
                fontSize = 16.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}

@Composable
private fun AdminStatCard(
    icon: String,
    value: String,
    label: String,
    accentColor: Color = Accent,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = BgCard,
        border = androidx.compose.foundation.BorderStroke(1.dp, Border),
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = icon, fontSize = 20.sp)
                Text(text = value, color = accentColor, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = label,
                color = TextMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun CallStatusBadge(status: String) {
    val (color, dimColor) = when (status.uppercase()) {
        "ACTIVE" -> Pair(Warning, WarningDim)
        "COMPLETED" -> Pair(Success, SuccessDim)
        "CANCELLED" -> Pair(TextMuted, BgSecondary)
        "FAILED" -> Pair(Emergency, EmergencyDim)
        else -> Pair(Accent, AccentDim)
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = dimColor
    ) {
        Text(
            text = status.uppercase(),
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun PaginationControls(
    page: Int,
    totalPages: Int,
    onPageChange: (Int) -> Unit
) {
    if (totalPages <= 1) return

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Button(
            onClick = { onPageChange(page - 1) },
            enabled = page > 1,
            colors = ButtonDefaults.buttonColors(
                containerColor = BgCard,
                disabledContainerColor = BgCard.copy(alpha = 0.4f)
            )
        ) {
            Text(stringResource(R.string.admin_pagination_prev), color = if (page > 1) Accent else TextMuted, fontSize = 13.sp)
        }

        Text(
            text = stringResource(R.string.admin_pagination_page_of, page, totalPages),
            color = TextMuted,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )

        Button(
            onClick = { onPageChange(page + 1) },
            enabled = page < totalPages,
            colors = ButtonDefaults.buttonColors(
                containerColor = BgCard,
                disabledContainerColor = BgCard.copy(alpha = 0.4f)
            )
        ) {
            Text(stringResource(R.string.admin_pagination_next), color = if (page < totalPages) Accent else TextMuted, fontSize = 13.sp)
        }
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "⚠️", fontSize = 36.sp)
        Spacer(modifier = Modifier.height(10.dp))
        Text(text = message, color = Emergency, textAlign = TextAlign.Center, fontSize = 15.sp)
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(containerColor = Accent)
        ) {
            Text(stringResource(R.string.common_retry), color = BgPrimary, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun EmptyState(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = message, color = TextMuted, fontSize = 15.sp, textAlign = TextAlign.Center)
    }
}

private fun formatDateDisplay(dateStr: String?): String {
    if (dateStr.isNullOrBlank()) return "—"
    return try {
        val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
        val date = inputFormat.parse(dateStr.substringBefore('.'))
        val outputFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        outputFormat.format(date ?: Date())
    } catch (_: Exception) {
        dateStr.substringBefore('T')
    }
}

private fun formatDateTimeDisplay(dateStr: String?): String {
    if (dateStr.isNullOrBlank()) return "—"
    return try {
        val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
        val date = inputFormat.parse(dateStr.substringBefore('.'))
        val outputFormat = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
        outputFormat.format(date ?: Date())
    } catch (_: Exception) {
        dateStr
    }
}

private fun formatDurationDisplay(seconds: Int?): String {
    if (seconds == null || seconds < 0) return "—"
    val m = seconds / 60
    val s = seconds % 60
    return if (m > 0) "${m}m ${s}s" else "${s}s"
}
