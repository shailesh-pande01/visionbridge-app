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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.visionbridge.api.AuthApi
import com.example.visionbridge.data.AuthState
import com.example.visionbridge.data.ContextMemoryManager
import com.example.visionbridge.data.SessionManager
import com.example.visionbridge.ui.components.VoiceStatusBar
import com.example.visionbridge.ui.screens.HomeScreen
import com.example.visionbridge.ui.screens.SmartReadingScreen
import com.example.visionbridge.ui.screens.SplashScreen
import com.example.visionbridge.ui.screens.auth.AuthScreen
import com.example.visionbridge.ui.screens.calling.CallingAssistantScreen
import com.example.visionbridge.ui.screens.currency.CurrencyReaderScreen
import com.example.visionbridge.ui.screens.emergency.EmergencyContactsScreen
import com.example.visionbridge.ui.screens.emergency.EmergencySosScreen
import com.example.visionbridge.ui.screens.entertainment.EntertainmentHomeScreen
import com.example.visionbridge.ui.screens.entertainment.GamesScreen
import com.example.visionbridge.ui.screens.entertainment.ProgressScreen
import com.example.visionbridge.ui.screens.entertainment.RadioScreen
import com.example.visionbridge.ui.screens.entertainment.StoryScreen
import com.example.visionbridge.ui.screens.finder.ObjectFinderScreen
import com.example.visionbridge.ui.screens.live.VisionLiveScreen
import com.example.visionbridge.ui.screens.live.VoiceCallScreen
import com.example.visionbridge.ui.screens.location.LocationAssistantScreen
import com.example.visionbridge.ui.screens.news.NewsAssistantScreen
import com.example.visionbridge.ui.screens.surroundings.SurroundingsScreen
import com.example.visionbridge.ui.screens.transport.TransportAssistantScreen
import com.example.visionbridge.ui.screens.volunteer.VolunteerDashboardScreen
import com.example.visionbridge.ui.screens.volunteer.VolunteerHelpScreen
import com.example.visionbridge.gesture.GestureController
import com.example.visionbridge.gesture.twoFingerDoubleTapGesture
import com.example.visionbridge.ui.screens.admin.AdminDashboardScreen
import com.example.visionbridge.ui.screens.admin.AdminLoginScreen
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
        GestureController.getInstance(this).onPause()
    }

    override fun onResume() {
        super.onResume()
        VoiceManager.getInstance(this).onActivityResume()
        GestureController.getInstance(this).onResume()
    }
}

@Composable
fun VisionBridgeApp() {
    val context = LocalContext.current
    val navController = rememberNavController()
    val sessionManager = remember { SessionManager.getInstance(context) }
    val authApi = remember { AuthApi(context) }
    val currentUser by sessionManager.currentUser.collectAsStateWithLifecycle()
    val authState by sessionManager.authState.collectAsStateWithLifecycle()

    val voiceManager = remember { VoiceManager.getInstance(context) }
    val voiceState by voiceManager.voiceState.collectAsStateWithLifecycle()

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: ""

    var sessionExpiredMessage by remember { mutableStateOf<String?>(null) }

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

    // Global session expiry watcher
    LaunchedEffect(authState) {
        if (authState is AuthState.SessionExpired) {
            sessionExpiredMessage = (authState as AuthState.SessionExpired).message
            voiceManager.stopVoice()
            ContextMemoryManager.reset()
            navController.navigate("auth") {
                popUpTo(0) { inclusive = true }
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
            currentRoute.startsWith("calling") -> "calling"
            currentRoute.startsWith("news") -> "news"
            currentRoute.startsWith("admin") -> "admin"
            else -> "home"
        }
        ContextMemoryManager.setActiveScreen(screenId)

        // Pause ambient speech recognition when inside Live sessions to prevent mic contention
        if (currentRoute == "live_vision" || currentRoute == "voice_call") {
            voiceManager.pauseForCall()
        } else if (currentRoute != "volunteer" && currentRoute != "auth" && currentRoute != "splash" && !currentRoute.startsWith("admin")) {
            voiceManager.resumeAfterCall()
        }
    }

    val showVoiceBar = currentUser != null &&
            currentUser?.role != "volunteer" &&
            currentUser?.role != "admin" &&
            currentRoute != "splash" &&
            currentRoute != "auth" &&
            currentRoute != "admin_login" &&
            currentRoute != "admin_dashboard" &&
            currentRoute != "volunteer_dashboard" &&
            currentRoute != "live_vision" &&
            currentRoute != "voice_call"

    val currentLanguage by sessionManager.language.collectAsStateWithLifecycle()
    val locale = remember(currentLanguage) { com.example.visionbridge.utils.LocaleHelper.getLocale(currentLanguage) }
    val configuration = remember(currentLanguage, locale) {
        android.content.res.Configuration(context.resources.configuration).apply {
            com.example.visionbridge.utils.LocaleHelper.applyLocaleToConfiguration(this, locale)
        }
    }
    val localizedContext = remember(context, currentLanguage, locale) {
        com.example.visionbridge.utils.LocaleHelper.wrapContext(context, currentLanguage)
    }

    val activity = context as? androidx.activity.ComponentActivity

    val gestureController = remember { GestureController.getInstance(context) }

    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.ui.platform.LocalConfiguration provides configuration,
        androidx.compose.ui.platform.LocalContext provides localizedContext,
        *buildList {
            if (activity != null) {
                add(androidx.activity.compose.LocalActivityResultRegistryOwner provides activity)
                add(androidx.activity.compose.LocalOnBackPressedDispatcherOwner provides activity)
            }
        }.toTypedArray()
    ) {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .twoFingerDoubleTapGesture {
                    gestureController.onTwoFingerDoubleTap()
                },
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
                    startDestination = "splash",
                    modifier = Modifier.fillMaxSize()
                ) {
                // 0. Startup Splash & Session Check Gate
                composable("splash") {
                    SplashScreen(
                        onSessionValid = { user ->
                            if (user.role == "admin") {
                                navController.navigate("admin_dashboard") {
                                    popUpTo("splash") { inclusive = true }
                                }
                            } else if (user.role == "volunteer") {
                                navController.navigate("volunteer_dashboard") {
                                    popUpTo("splash") { inclusive = true }
                                }
                            } else {
                                navController.navigate("home") {
                                    popUpTo("splash") { inclusive = true }
                                }
                            }
                        },
                        onSessionInvalid = { errorNotice ->
                            sessionExpiredMessage = errorNotice
                            navController.navigate("auth") {
                                popUpTo("splash") { inclusive = true }
                            }
                        }
                    )
                }

                // Auth Screen
                composable("auth") {
                    AuthScreen(
                        sessionExpiredNotice = sessionExpiredMessage,
                        onNavigateAdminLogin = {
                            navController.navigate("admin_login")
                        },
                        onAuthSuccess = { user ->
                            sessionExpiredMessage = null
                            if (user.role == "admin") {
                                navController.navigate("admin_dashboard") {
                                    popUpTo("auth") { inclusive = true }
                                }
                            } else if (user.role == "volunteer") {
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
                            voiceManager.stopVoice()
                            authApi.logout()
                            ContextMemoryManager.reset()
                            navController.navigate("auth") {
                                popUpTo(0) { inclusive = true }
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
                        onVolunteerHelp = { navController.navigate("volunteer") }
                    )
                }

                // 3. Currency Reader
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
                            voiceManager.stopVoice()
                            authApi.logout()
                            ContextMemoryManager.reset()
                            navController.navigate("auth") {
                                popUpTo(0) { inclusive = true }
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

                // 17. Accessible Phone Calling Assistant
                composable("calling") {
                    CallingAssistantScreen(
                        onBack = { navController.popBackStack() }
                    )
                }

                // 18. Daily News Assistant
                composable("news") {
                    NewsAssistantScreen(
                        onBack = { navController.popBackStack() }
                    )
                }

                // 19. Dedicated Admin Login Gate
                composable("admin_login") {
                    AdminLoginScreen(
                        onAdminLoginSuccess = { user ->
                            sessionExpiredMessage = null
                            navController.navigate("admin_dashboard") {
                                popUpTo("admin_login") { inclusive = true }
                            }
                        },
                        onBackToMainLogin = {
                            navController.popBackStack()
                        }
                    )
                }

                // 20. Admin Dashboard (Strict Role-Guarded Destination)
                composable("admin_dashboard") {
                    if (currentUser?.role != "admin") {
                        LaunchedEffect(Unit) {
                            if (currentUser?.role == "volunteer") {
                                navController.navigate("volunteer_dashboard") {
                                    popUpTo("admin_dashboard") { inclusive = true }
                                }
                            } else {
                                navController.navigate("home") {
                                    popUpTo("admin_dashboard") { inclusive = true }
                                }
                            }
                        }
                    } else {
                        AdminDashboardScreen(
                            onLogout = {
                                voiceManager.stopVoice()
                                authApi.logout()
                                ContextMemoryManager.reset()
                                navController.navigate("auth") {
                                    popUpTo(0) { inclusive = true }
                                }
                            },
                            onNavigateBack = {
                                navController.navigate("home")
                            }
                        )
                    }
                }
            }
        }
    }
}
}
