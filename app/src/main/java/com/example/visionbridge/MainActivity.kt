package com.example.visionbridge

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.visionbridge.data.ContextMemoryManager
import com.example.visionbridge.data.SessionManager
import com.example.visionbridge.ui.components.VoiceStatusBar
import com.example.visionbridge.ui.screens.HomeScreen
import com.example.visionbridge.ui.screens.SmartReadingScreen
import com.example.visionbridge.ui.screens.auth.AuthScreen
import com.example.visionbridge.ui.screens.currency.CurrencyReaderScreen
import com.example.visionbridge.ui.screens.emergency.EmergencyContactsScreen
import com.example.visionbridge.ui.screens.emergency.EmergencySosScreen
import com.example.visionbridge.ui.screens.finder.ObjectFinderScreen
import com.example.visionbridge.ui.screens.hazard.HazardModeScreen
import com.example.visionbridge.ui.screens.location.LocationAssistantScreen
import com.example.visionbridge.ui.screens.surroundings.SurroundingsScreen
import com.example.visionbridge.ui.screens.transport.TransportAssistantScreen
import com.example.visionbridge.ui.screens.volunteer.VolunteerDashboardScreen
import com.example.visionbridge.ui.screens.volunteer.VolunteerHelpScreen
import com.example.visionbridge.ui.screens.entertainment.EntertainmentHomeScreen
import com.example.visionbridge.ui.screens.entertainment.GamesScreen
import com.example.visionbridge.ui.screens.entertainment.ProgressScreen
import com.example.visionbridge.ui.screens.entertainment.RadioScreen
import com.example.visionbridge.ui.screens.entertainment.StoryScreen
import com.example.visionbridge.ui.screens.live.VisionLiveScreen
import com.example.visionbridge.ui.screens.live.VoiceCallScreen
import com.example.visionbridge.ui.theme.BgPrimary
import com.example.visionbridge.ui.theme.VisionbridgeTheme
import com.example.visionbridge.voice.VoiceManager

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VisionbridgeTheme {
                VisionBridgeApp()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        VoiceManager.getInstance(this).onActivityPause()
    }

    override fun onResume() {
        super.onResume()
        VoiceManager.getInstance(this).onActivityResume()
    }
}

@Composable
fun VisionBridgeApp() {
    val context = LocalContext.current
    val navController = rememberNavController()
    val sessionManager = remember { SessionManager.getInstance(context) }
    val currentUser by sessionManager.currentUser.collectAsStateWithLifecycle()

    val voiceManager = remember { VoiceManager.getInstance(context) }
    val voiceState by voiceManager.voiceState.collectAsStateWithLifecycle()

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: ""

    // Connect voice navigation
    LaunchedEffect(navController) {
        voiceManager.setNavigationHandler { route ->
            try {
                if (route == "home") {
                    navController.navigate("home") {
                        popUpTo("home") { inclusive = false }
                    }
                } else {
                    navController.navigate(route)
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Voice navigation error for route: $route", e)
            }
        }
    }

    // Keep ContextMemoryManager active screen synced and coordinate voice manager
    LaunchedEffect(currentRoute) {
        val screenId = when {
            currentRoute.startsWith("live_vision") -> "liveVision"
            currentRoute.startsWith("voice_call") -> "voiceCall"
            currentRoute.startsWith("reading") -> "reading"
            currentRoute.startsWith("surroundings") -> "surroundings"
            currentRoute.startsWith("hazard") -> "hazard"
            currentRoute.startsWith("currency") -> "currency"
            currentRoute.startsWith("transport") -> "transport"
            currentRoute.startsWith("finder") -> "objectFinder"
            currentRoute.startsWith("location") -> "location"
            currentRoute.startsWith("volunteer") -> "volunteer"
            currentRoute.startsWith("sos") -> "emergency"
            currentRoute.startsWith("entertainment") -> "entertainment"
            currentRoute.startsWith("radio") -> "radio"
            currentRoute.startsWith("stories") -> "stories"
            currentRoute.startsWith("games") -> "games"
            currentRoute.startsWith("progress") -> "progress"
            else -> "home"
        }
        ContextMemoryManager.setActiveScreen(screenId)

        // Pause ambient speech recognition when inside Live sessions to prevent mic contention
        if (currentRoute == "live_vision" || currentRoute == "voice_call") {
            voiceManager.pauseForCall()
        } else if (currentRoute != "volunteer") {
            voiceManager.resumeAfterCall()
        }
    }

    val startDest = when {
        currentUser == null -> "auth"
        currentUser?.role == "volunteer" -> "volunteer_dashboard"
        else -> "home"
    }

    val showVoiceBar = currentUser != null &&
            currentUser?.role != "volunteer" &&
            currentRoute != "auth" &&
            currentRoute != "volunteer_dashboard" &&
            currentRoute != "live_vision" &&
            currentRoute != "voice_call"

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = BgPrimary,
        bottomBar = {
            if (showVoiceBar) {
                VoiceStatusBar(
                    voiceState = voiceState,
                    onActivate = { voiceManager.activateVoice() },
                    onStop = { voiceManager.stopVoice() }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(BgPrimary)
        ) {
            NavHost(
                navController = navController,
                startDestination = startDest,
                modifier = Modifier.fillMaxSize()
            ) {
                // Auth Screen
                composable("auth") {
                    AuthScreen(
                        onAuthSuccess = { user ->
                            if (user.role == "volunteer") {
                                navController.navigate("volunteer_dashboard") {
                                    popUpTo("auth") { inclusive = true }
                                }
                            } else {
                                navController.navigate("home") {
                                    popUpTo("auth") { inclusive = true }
                                }
                            }
                        }
                    )
                }

                // Low-Vision User Home Hub
                composable("home") {
                    HomeScreen(
                        onNavigate = { route -> navController.navigate(route) },
                        onLogout = {
                            navController.navigate("auth") {
                                popUpTo("home") { inclusive = true }
                            }
                        }
                    )
                }

                // 0a. VisionBridge Live (Real-time camera + voice)
                composable("live_vision") {
                    VisionLiveScreen(
                        onBack = { navController.popBackStack() },
                        onNavigateFallback = { route -> navController.navigate(route) }
                    )
                }

                // 0b. AI Voice Call (Real-time conversational voice assistant)
                composable("voice_call") {
                    VoiceCallScreen(
                        onBack = { navController.popBackStack() },
                        onSwitchToVisionLive = {
                            navController.navigate("live_vision") {
                                popUpTo("voice_call") { inclusive = true }
                            }
                        }
                    )
                }

                // 1. Smart Reading
                composable("reading") {
                    SmartReadingScreen(
                        title = "Read Text",
                        onBack = { navController.popBackStack() }
                    )
                }

                // 2. AI Surroundings
                composable("surroundings") {
                    SurroundingsScreen(
                        onBack = { navController.popBackStack() },
                        onNavigateHazard = { navController.navigate("hazard") },
                        onVolunteerHelp = { navController.navigate("volunteer") }
                    )
                }

                // 3. Hazard Mode
                composable("hazard") {
                    HazardModeScreen(
                        onBack = { navController.popBackStack() }
                    )
                }

                // 4. Currency Reader
                composable("currency") {
                    CurrencyReaderScreen(
                        onBack = { navController.popBackStack() },
                        onVolunteerHelp = { navController.navigate("volunteer") }
                    )
                }

                // 5. Public Transport & Signboard Assistant
                composable("transport") {
                    TransportAssistantScreen(
                        onBack = { navController.popBackStack() },
                        onVolunteerHelp = { navController.navigate("volunteer") }
                    )
                }

                // 6. Smart Object Finder
                composable(
                    route = "finder?target={target}",
                    arguments = listOf(
                        navArgument("target") {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        }
                    )
                ) { backStackEntry ->
                    val target = backStackEntry.arguments?.getString("target")
                    ObjectFinderScreen(
                        initialTarget = target,
                        onBack = { navController.popBackStack() },
                        onVolunteerHelp = { navController.navigate("volunteer") }
                    )
                }

                composable("finder") {
                    ObjectFinderScreen(
                        initialTarget = null,
                        onBack = { navController.popBackStack() },
                        onVolunteerHelp = { navController.navigate("volunteer") }
                    )
                }

                // 7. Where Am I? (Location)
                composable("location") {
                    LocationAssistantScreen(
                        onBack = { navController.popBackStack() }
                    )
                }

                // 8. Volunteer Help (Low-Vision user view)
                composable("volunteer") {
                    VolunteerHelpScreen(
                        onBack = {
                            navController.navigate("home") {
                                popUpTo("home") { inclusive = false }
                                launchSingleTop = true
                            }
                        }
                    )
                }

                // 9. Volunteer Dashboard (Volunteer role view)
                composable("volunteer_dashboard") {
                    VolunteerDashboardScreen(
                        onLogout = {
                            navController.navigate("auth") {
                                popUpTo("volunteer_dashboard") { inclusive = true }
                            }
                        }
                    )
                }

                // 10. Emergency SOS
                composable("sos") {
                    EmergencySosScreen(
                        onBack = { navController.popBackStack() },
                        onManageContacts = { navController.navigate("contacts") }
                    )
                }

                // 11. Emergency Trusted Contacts
                composable("contacts") {
                    EmergencyContactsScreen(
                        onBack = { navController.popBackStack() }
                    )
                }

                // 12. Entertainment Hub
                composable("entertainment") {
                    EntertainmentHomeScreen(
                        onNavigate = { route -> navController.navigate(route) },
                        onBack = { navController.popBackStack() }
                    )
                }

                // 13. Live Radio
                composable("radio") {
                    RadioScreen(
                        onBack = { navController.popBackStack() }
                    )
                }

                // 14. Stories & Audiobooks
                composable("stories") {
                    StoryScreen(
                        onBack = { navController.popBackStack() }
                    )
                }

                // 15. Audio Games & Daily Challenge
                composable("games") {
                    GamesScreen(
                        onBack = { navController.popBackStack() }
                    )
                }

                // 16. My Progress & Streak
                composable("progress") {
                    ProgressScreen(
                        onBack = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}
