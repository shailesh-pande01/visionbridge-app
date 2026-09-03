# 👁️ VisionBridge Android

> **AI-first multimodal accessibility platform empowering low-vision and blind individuals to perceive the physical world, interact naturally through voice and gestures, and connect with human volunteers when AI is uncertain.**

[![Platform](https://img.shields.io/badge/Platform-Android-green.svg?logo=android)](https://www.android.com/)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-purple.svg?logo=kotlin)](https://kotlinlang.org/)
[![Compose](https://img.shields.io/badge/Jetpack_Compose-BOM_2024.09.00-blue.svg?logo=jetpackcompose)](https://developer.android.com/jetpack/compose)
[![Min SDK](https://img.shields.io/badge/Min_SDK-24-orange.svg)](https://developer.android.com/about/versions/nougat)
[![Target SDK](https://img.shields.io/badge/Target_SDK-36-red.svg)](https://developer.android.com/about/versions/16)
[![AI Engine](https://img.shields.io/badge/Google_Gemini-Multimodal_Live_API-4285F4.svg?logo=google)](https://deepmind.google/technologies/gemini/)
[![Backend](https://img.shields.io/badge/Backend-Supabase_Edge_Functions-3ECF8E.svg?logo=supabase)](https://supabase.com/)
[![WebRTC](https://img.shields.io/badge/Realtime-Stream_WebRTC-005FFF.svg)](https://getstream.io/webrtc/)
[![Accessibility](https://img.shields.io/badge/A11y-WCAG_AAA_Voice_First-9333EA.svg)]()

---

## 📖 Table of Contents

- [Overview](#-overview)
- [System Architecture](#-system-architecture)
- [Actual Feature Inventory](#-actual-feature-inventory)
- [Technology Stack](#-technology-stack)
- [Project Directory Structure](#-project-directory-structure)
- [Core Architecture & Deep-Dive Modules](#-core-architecture--deep-dive-modules)
  - [Gemini Multimodal Live API Pipeline](#1-gemini-multimodal-live-api-pipeline)
  - [Conversational Voice Layer & Context Memory](#2-conversational-voice-layer--context-memory)
  - [Peer-to-Peer WebRTC Volunteer Assistance](#3-peer-to-peer-webrtc-volunteer-assistance)
  - [Authentication & Backend Architecture](#4-authentication--backend-architecture)
  - [Accessibility, Gestures & TalkBack](#5-accessibility-gestures--talkback)
  - [Entertainment, Audiobooks & Gamification](#6-entertainment-audiobooks--gamification)
  - [Admin Telemetry Dashboard](#7-admin-telemetry-dashboard)
- [Android Permissions](#-android-permissions)
- [Developer Setup Guide](#-developer-setup-guide)
- [Testing & Quality Assurance](#-testing--quality-assurance)
- [⚠️ Known Limitations](#️-known-limitations)
- [Project Roadmap](#-project-roadmap)
- [Security Guidelines](#-security-guidelines)
- [Contributing](#-contributing)
- [License](#-license)

---

## 🌟 Overview

**VisionBridge** is a native Android accessibility companion tailored for blind and low-vision individuals. Recognizing that standard mobile interfaces with small tap targets and visual-first flows present severe barriers to accessibility, VisionBridge transforms the smartphone into a fully conversational, multimodal perception assistant.

### Key Tenets
1. **AI First, Human-in-the-Loop Always**: Low-vision users query Google Gemini multimodal models directly for instantaneous auditory scene understanding, OCR document reading, currency recognition, object finding, and transit information. Whenever confidence is below threshold ($< 0.85$) or the user requests personalized support, the application seamlessly triggers peer-to-peer WebRTC video and audio handoff to a verified human volunteer.
2. **True Voice & Gesture-First Interaction**: Every authenticated screen is controlled through the always-listening wake-word **"Vision"** (including Devanagari Hindi *"विजन"* and Marathi *"व्हिजन"*), two-finger double-tap triggers, and shake gestures with rich haptic feedback.
3. **Low Latency Multimodal Streaming**: Utilizes Google's bidirectional **Gemini Multimodal Live API** over WebSocket with native 16kHz audio capture, hardware acoustic echo cancellation, playback-aware voice activity detection (VAD), 24kHz audio playback, and instant speech barge-in.
4. **Resilient Serverless Backend**: Powered by **Supabase** (Auth, PostgreSQL with Row Level Security, Realtime WebSocket Broadcasts, and Edge Functions), alongside automatic runtime fallback to local or cloud Node.js/MERN endpoints via dynamic network health probing.

---

## 🏗️ System Architecture

```
                                  ┌─────────────────────────────────────────────────────────────┐
                                  │                    VisionBridge Android                     │
                                  │                  (Jetpack Compose UI)                       │
                                  └──────────────────────────────┬──────────────────────────────┘
                                                                 │
                                ┌────────────────────────────────┼────────────────────────────────┐
                                │                                │                                │
                                ▼                                ▼                                ▼
                     ┌───────────────────────┐        ┌───────────────────────┐        ┌───────────────────────┐
                     │   Voice & Gestures    │        │  Feature ViewModels   │        │     Device APIs       │
                     │  · WakeWordMatcher    │        │  · LiveViewModel      │        │  · CameraX (Rear/Fr.) │
                     │  · VoiceManager (TTS) │◄───────┤  · VolunteerViewModel │◄───────┤  · AudioRecord 16kHz  │
                     │  · GestureController  │        │  · SmartReadingVM     │        │  · AudioTrack 24kHz   │
                     │  · ContextMemoryMgr   │        │  · AdminViewModel     │        │  · FusedLocation GPS  │
                     └───────────────────────┘        └───────────┬───────────┘        └───────────────────────┘
                                                                  │
                                ┌─────────────────────────────────┼────────────────────────────────┐
                                │                                 │                                │
                                ▼                                 ▼                                ▼
                     ┌───────────────────────┐        ┌───────────────────────┐        ┌───────────────────────┐
                     │    Supabase Client    │        │   Gemini Live Engine  │        │   WebRTC Call Engine  │
                     │  · Supabase Auth      │        │  · GeminiLiveWebSocket│        │  · WebRtcCallManager  │
                     │  · PostgREST REST API │        │  · LiveAudioEngine    │        │  · SignalingClient    │
                     │  · Supabase Realtime  │        │  · LiveCameraStreamer │        │  · STUN/TURN Relays   │
                     └───────────┬───────────┘        └───────────┬───────────┘        └───────────┬───────────┘
                                 │                                │                                │
                                 ▼                                ▼                                ▼
                     ┌───────────────────────┐        ┌───────────────────────┐        ┌───────────────────────┐
                     │   Supabase Cloud /    │        │   Google Gemini API   │        │   Volunteer Device    │
                     │   PostgreSQL + RLS    │        │   Multimodal Live /   │        │   (Web / Android App) │
                     │   · Edge Functions    │        │   Flash 2.5 Server    │        │   · Peer Connection   │
                     │   · Realtime Channels │        │   · Low-Latency Voice │        │   · Asymmetric Video  │
                     └───────────────────────┘        └───────────────────────┘        └───────────────────────┘
```

---

## ✨ Actual Feature Inventory

The following table reflects the **actual current implementation state** within the Android codebase (`com.example.visionbridge`):

| Category | Feature | Status | Camera | Mic | GPS | Gemini | Supabase | WebRTC | Description |
| :--- | :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :--- |
| **Live AI** | **VisionBridge Live** | 🟢 Implemented | ✅ | ✅ | — | ✅ | ✅ | — | Real-time bidirectional streaming of camera frames & 16kHz audio to Gemini Live API; sub-second spoken replies with instant barge-in. |
| **Live AI** | **AI Voice Call** | 🟢 Implemented | — | ✅ | — | ✅ | ✅ | — | Audio-only hands-free conversational call powered by Gemini Live WebSocket. |
| **Voice** | **Wake-Word Engine** | 🟢 Implemented | — | ✅ | — | ✅ | ✅ | — | Continuous on-device wake-word detection for *"Vision"* / *"विजन"* / *"व्हिजन"* with single-utterance and 2-stage command parsing. |
| **Voice** | **Context Memory** | 🟢 Implemented | — | — | — | ✅ | ✅ | — | Multi-slot session memory (15-min TTL, 4-turn rolling history) retaining previous screen analysis across app navigation. |
| **Vision** | **Smart Reading** | 🟢 Implemented | ✅ | — | — | ✅ | ✅ | — | Instant camera capture, OCR text extraction via Gemini, and localized chunked TTS playback. |
| **Vision** | **Surroundings & Hazards** | 🟢 Implemented | ✅ | — | — | ✅ | ✅ | — | Comprehensive spatial description, obstacle identification, time of day, and environmental lighting assessment. |
| **Vision** | **Currency Reader** | 🟢 Implemented | ✅ | — | — | ✅ | ✅ | — | Banknote and coin recognition (INR ₹ and international), quantity counts, and automated total calculation. |
| **Vision** | **Smart Object Finder** | 🟢 Implemented | ✅ | — | — | ✅ | ✅ | — | Target item search with relative spatial guidance (*"to your left at 2 meters"*). |
| **Transit** | **Public Transport** | 🟢 Implemented | ✅ | — | — | ✅ | ✅ | — | Bus number extraction, railway platform sign detection, and transit route assistance. |
| **Location**| **"Where Am I?" Assistant** | 🟢 Implemented | — | — | ✅ | ✅ | ✅ | — | GPS coordinates, reverse geocoded address, and nearby prominent landmarks. |
| **Volunteer**| **Real-time Help Request** | 🟢 Implemented | — | — | ✅ | — | ✅ | — | Instant broadcast of assistance requests to volunteers with live status tracking (`PENDING`, `ACCEPTED`, `COMPLETED`). |
| **Volunteer**| **WebRTC Volunteer Call** | 🟢 Implemented | ✅ | ✅ | — | — | ✅ | ✅ | Asymmetric P2P video call (user camera `SEND_ONLY`, volunteer camera `OFF`), full-duplex audio, and Realtime signaling. |
| **Safety** | **Emergency SOS** | 🟢 Implemented | — | — | ✅ | — | ✅ | — | 5-second countdown with audible siren, automated WhatsApp distress dispatch with live location tracking links. |
| **Safety** | **Trusted Contacts** | 🟢 Implemented | — | — | — | — | ✅ | — | Add, view, edit, and delete emergency contacts synced to PostgreSQL via Supabase. |
| **Calling** | **Accessible Phone Dialer**| 🟢 Implemented | — | — | — | — | ✅ | — | Voice and large-button phone dialer, contacts management, favorites, and call logs. |
| **News** | **Daily News Assistant** | 🟢 Implemented | — | — | — | ✅ | ✅ | — | Category-based news briefings with localized audio readout (general, tech, sports, business). |
| **Audio** | **Live Radio & AI Radio** | 🟢 Implemented | — | — | — | ✅ | ✅ | — | Thousands of global and regional stations via Radio Browser API, ExoPlayer foreground playback, and AI topic segments. |
| **Audio** | **Stories & Audiobooks** | 🟢 Implemented | — | — | — | — | ✅ | — | Curated public domain classics (LibriVox / Gutenberg), chapter progress tracking, and chunked TTS narration. |
| **Audio** | **Voice-First Audio Games** | 🟢 Implemented | — | ✅ | — | ✅ | ✅ | — | Voice-driven Trivia, Riddles, Memory Challenges, and 20 Questions with AI evaluation. |
| **Gamify** | **Progress & Streaks** | 🟢 Implemented | — | — | — | — | ✅ | — | Gamified XP tracking, streaks, daily challenges, and unlockable achievements stored in PostgreSQL. |
| **Admin** | **Community Dashboard** | 🟢 Implemented | — | — | — | — | ✅ | — | Server-verified admin portal for user counts, active volunteers, call metrics, and active emergency SOS events. |
| **A11y** | **Gestures & Haptics** | 🟢 Implemented | — | — | — | — | — | — | 2-finger double-tap anywhere to invoke voice, shake detection to activate voice or SOS, and tactile vibration feedback. |
| **A11y** | **Trilingual Support** | 🟢 Implemented | — | — | — | — | — | — | Full native UI, voice recognition, and TTS localized in English (`en`), Hindi (`hi`), and Marathi (`mr`). |

*Legend: 🟢 Implemented & Tested | 🟡 Partially Implemented | 🟣 Experimental*

---

## 💻 Technology Stack

### Android & Core Libraries
- **Language**: Kotlin `2.0.21` (Java 11 target)
- **Target SDK**: `36` (Android 16 preview / Android 15 compatible) | **Min SDK**: `24` (Android 7.0 Nougat)
- **UI Framework**: Jetpack Compose with Compose BOM `2024.09.00`
- **Design System**: Material 3 (`androidx.compose.material3`), Material Icons Extended
- **Architecture Components**: ViewModel Compose, Lifecycle Runtime Compose, Navigation Compose `2.7.7`
- **Asynchronous**: Kotlin Coroutines `1.8+`, StateFlow, SharedFlow
- **Networking**: OkHttp `4.12.0` (HTTP/2, WebSocket client, timeout & interceptor management)
- **Camera**: AndroidX CameraX `1.3.4` (`camera-core`, `camera-camera2`, `camera-lifecycle`, `camera-view`)
- **Audio & Media**:
  - Android Low-Level Audio: `AudioRecord` (16kHz PCM mono), `AudioTrack` (`MODE_STREAM`, 24kHz PCM mono)
  - Hardware Audio Effects: `AcousticEchoCanceler`, `NoiseSuppressor`, `AutomaticGainControl`
  - Media3 Playback: AndroidX Media3 `1.3.1` (`media3-exoplayer`, `media3-session`, `media3-ui`)
- **Realtime WebRTC**: Stream WebRTC Android `1.1.1` (`io.getstream:stream-webrtc-android`)
- **Realtime Signaling**: Supabase Realtime WebSocket (Phoenix protocol) + legacy Socket.io Client `2.1.1`
- **Location**: Google Play Services Location `21.3.0` (`FusedLocationProviderClient`)
- **Persistence**: Preferences DataStore `1.1.1`, SharedPreferences
- **Testing**: JUnit 4, AndroidX Test Runner, JSON Unit `org.json:json:20240303`

### Backend & Cloud Infrastructure
- **Platform**: Supabase Cloud / Self-Hosted
- **Database**: PostgreSQL 15 with Row Level Security (RLS) policies and RPC stored procedures
- **Authentication**: Supabase GoTrue Auth (JWT with silent token refresh)
- **Edge Functions**: Deno / TypeScript serverless functions for AI orchestration
- **Realtime**: Supabase Realtime WebSockets (`realtime:request:$id` broadcast channels & `postgres_changes`)
- **Legacy Fallback**: Express.js / Node.js MERN server via dynamic `BackendLocator` probing

---

## 📁 Project Directory Structure

```text
visionbridge/
├── app/
│   ├── build.gradle.kts                     # Gradle configuration, compileSdk 36, dependencies
│   ├── proguard-rules.pro                   # R8 / ProGuard optimization rules
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml          # Permissions, foreground services, MainActivity
│       │   ├── java/com/example/visionbridge/
│       │   │   ├── MainActivity.kt          # Single-activity Compose NavHost & lifecycle hub
│       │   │   ├── api/                     # Backend API clients & network communication
│       │   │   │   ├── AdminApi.kt          # Zero-trust admin authorization & telemetry
│       │   │   │   ├── ApiClient.kt         # OkHttp client with retry & auth token injection
│       │   │   │   ├── ApiResult.kt         # Type-safe Success/Failure sealed interface
│       │   │   │   ├── AssistantApi.kt      # Gemini voice commands & follow-up questions
│       │   │   │   ├── AuthApi.kt           # Supabase Auth registration, login, session
│       │   │   │   ├── BackendLocator.kt    # Automatic discovery of active backend host
│       │   │   │   ├── CallingApi.kt        # Phone contacts and call log persistence
│       │   │   │   ├── Config.kt            # Network host candidates and health probe paths
│       │   │   │   ├── EmergencyApi.kt      # Emergency SOS triggers & trusted contacts
│       │   │   │   ├── EntertainmentApi.kt  # Radio, Stories, Audio Games & XP API
│       │   │   │   ├── FinderApi.kt         # Object finding visual analysis
│       │   │   │   ├── LocationApi.kt       # Geocoding and reverse landmark context
│       │   │   │   ├── NewsApi.kt           # Regional & topical news briefings
│       │   │   │   ├── SmartReadingApi.kt   # OCR document & menu text extraction
│       │   │   │   ├── TransportApi.kt      # Transit signage & bus number identification
│       │   │   │   └── VisionApi.kt         # Surroundings, hazards & currency inspection
│       │   │   ├── audio/                   # Background media services & playback engines
│       │   │   │   ├── EntertainmentAudioSession.kt # Exclusive audio session coordinator
│       │   │   │   ├── EntertainmentMediaService.kt # Media3 foreground playback service
│       │   │   │   └── StoryChunkedTtsPlayer.kt     # Sentence-chunked TTS player with pause/resume
│       │   │   ├── data/                    # Domain models, repositories & session managers
│       │   │   │   ├── AdminModels.kt       # Admin statistics, user items & call logs
│       │   │   │   ├── CallingModels.kt     # Phone contacts and call history models
│       │   │   │   ├── ContextMemoryManager.kt # Multi-slot 15-min TTL conversational memory
│       │   │   │   ├── EntertainmentModels.kt  # Radio stations, stories, trivia & XP progress
│       │   │   │   ├── Models.kt            # Core User, Scene, Currency, SOS & Action models
│       │   │   │   ├── NewsModels.kt        # News articles and briefings data structures
│       │   │   │   ├── SessionManager.kt    # SharedPreferences session & token cache
│       │   │   │   └── SmartReadingRepository.kt # Reading cache & repository
│       │   │   ├── gesture/                 # Accessibility gestures & physical sensors
│       │   │   │   ├── GestureController.kt # Centralized gesture coordinator & haptics
│       │   │   │   ├── ShakeDetector.kt     # Accelerometer shake gesture detector
│       │   │   │   └── TwoFingerDoubleTapModifier.kt # Compose modifier for 2-finger double-tap
│       │   │   ├── live/                    # Google Gemini Multimodal Live Engine
│       │   │   │   ├── GeminiLiveWebSocket.kt  # Bidirectional streaming WebSocket client
│       │   │   │   ├── LiveApi.kt              # Ephemeral token & session initiator
│       │   │   │   ├── LiveAudioEngine.kt      # Low-latency AudioRecord & AudioTrack PCM engine
│       │   │   │   ├── LiveCameraStreamer.kt   # Real-time CameraX JPEG frame streamer
│       │   │   │   ├── LiveModels.kt           # LiveSessionData, LiveState & diagnostics
│       │   │   │   └── LiveViewModel.kt        # Shared ViewModel for Live Vision & Voice Call
│       │   │   ├── supabase/                # Supabase SDK, Auth, PostgREST & Realtime
│       │   │   │   ├── SupabaseClient.kt       # PostgREST, RPC & Auth HTTP client
│       │   │   │   ├── SupabaseConfig.kt       # Endpoint routing & Edge Function names
│       │   │   │   └── SupabaseRealtimeClient.kt # Phoenix WebSocket for Broadcast & Postgres Changes
│       │   │   ├── ui/                      # Jetpack Compose UI layer
│       │   │   │   ├── components/          # Reusable accessible components
│       │   │   │   │   ├── ActionCard.kt        # High-contrast, large touch-target button
│       │   │   │   │   ├── AppTopBar.kt         # Accessible top navigation bar
│       │   │   │   │   ├── ConfirmLogoutDialog.kt # High-contrast logout confirmation dialog
│       │   │   │   │   ├── LanguageSelector.kt  # English / Hindi / Marathi picker
│       │   │   │   │   ├── SectionHeader.kt     # High-contrast category header
│       │   │   │   │   ├── VoiceAssistantArea.kt# Voice interaction status visualizer
│       │   │   │   │   └── VoiceStatusBar.kt    # Ambient bottom bar showing wake-word state
│       │   │   │   ├── screens/             # Feature screens & ViewModels
│       │   │   │   │   ├── admin/               # Admin dashboard & dedicated login screen
│       │   │   │   │   ├── auth/                # Supabase registration & login screen
│       │   │   │   │   ├── calling/             # Phone calling assistant screen
│       │   │   │   │   ├── currency/            # Currency reader screen
│       │   │   │   │   ├── emergency/           # Emergency SOS & trusted contacts screen
│       │   │   │   │   ├── entertainment/       # Radio, Stories, Audio Games & XP screens
│       │   │   │   │   ├── finder/              # Smart object finder screen
│       │   │   │   │   ├── live/                # VisionLiveScreen & VoiceCallScreen
│       │   │   │   │   ├── location/            # "Where Am I?" location assistant screen
│       │   │   │   │   ├── news/                # Daily news briefing assistant screen
│       │   │   │   │   ├── reading/             # Smart reading OCR screen & ViewModel
│       │   │   │   │   ├── surroundings/        # AI surroundings & hazard screen
│       │   │   │   │   ├── transport/           # Public transport assistant screen
│       │   │   │   │   └── volunteer/           # Volunteer help request & dashboard screens
│       │   │   │   │   ├── HomeScreen.kt        # Primary 6-tile accessible launch hub
│       │   │   │   │   └── SplashScreen.kt      # Startup splash & session validation gate
│       │   │   │   └── theme/               # High-contrast color palette, type & shapes
│       │   │   ├── utils/                   # Helper utilities
│       │   │   │   ├── ImageHelper.kt           # Bitmap rotation, compression & Base64 encoding
│       │   │   │   ├── LocaleHelper.kt          # Dynamic runtime locale switching (en, hi, mr)
│       │   │   │   ├── LocationHelper.kt        # Fused Location Provider wrappers
│       │   │   │   ├── SpeechRecognizerManager.kt # Android native SpeechRecognizer wrapper
│       │   │   │   ├── TextToSpeechManager.kt   # Android TextToSpeech engine with locale support
│       │   │   │   └── VoiceCommandParser.kt    # Offline heuristic fallback command parser
│       │   │   ├── voice/                   # Native voice assistant engine
│       │   │   │   ├── ScreenActionRegistry.kt  # Registry of screen-level custom actions
│       │   │   │   ├── VoiceActionRouter.kt     # Whitelist action validator & router
│       │   │   │   ├── VoiceActivationSource.kt # Wake word, tap, or gesture invocation source
│       │   │   │   ├── VoiceManager.kt          # Global lifecycle-aware voice assistant manager
│       │   │   │   └── WakeWordMatcher.kt       # Fast regex matcher for "Vision" / "विजन"
│       │   │   └── webrtc/                  # WebRTC peer connection & signaling
│       │   │       ├── WebRtcCallManager.kt     # PeerConnectionFactory, CameraCapturer, AudioDeviceModule
│       │   │       └── WebRtcSignalingClient.kt # Supabase Realtime broadcast signaling client
│       │   └── res/                         # Android resources
│       │       ├── values/strings.xml       # English strings & content descriptions
│       │       ├── values-hi/strings.xml    # Hindi (हिन्दी) localization strings
│       │       └── values-mr/strings.xml    # Marathi (मराठी) localization strings
│       └── test/                            # Comprehensive unit test suite (54 tests)
│           └── java/com/example/visionbridge/
│               ├── AdminDashboardTest.kt
│               ├── SupabaseAuthTest.kt
│               ├── VoiceCommandRoutingTest.kt
│               ├── gesture/GestureControllerAndVoiceActivationTest.kt
│               ├── voice/VoiceFollowUpContextTest.kt
│               └── voice/WakeWordMatcherAndRouterTest.kt
├── gradle/                                  # Gradle wrapper binaries & daemon configurations
├── sih-2026-deck/                           # Smart India Hackathon (SIH 2026) pitch presentation
├── supabase/                                # Supabase backend configuration & Edge Functions
│   ├── config.toml                          # Supabase project configuration
│   ├── functions/                           # Deno / TypeScript Edge Functions
│   │   ├── _shared/gemini.ts                # Gemini API client & prompt definitions
│   │   ├── assistant-command/               # Voice command intent parser
│   │   ├── assistant-ask/                   # Conversational follow-up question answerer
│   │   ├── emergency-sos/                   # SOS trigger & WhatsApp notification dispatcher
│   │   ├── live-session/                    # Gemini Live ephemeral token generation
│   │   ├── live-tool/                       # Live function execution
│   │   ├── object-finder/                   # Spatial object identification
│   │   ├── reading-extract/                 # Multimodal document text extraction
│   │   ├── transport-analyze/               # Transit sign & bus recognition
│   │   ├── vision-analyze/                  # Surroundings & hazard analysis
│   │   └── vision-currency/                 # Cash & coin denomination analyzer
│   └── migrations/                          # PostgreSQL database migrations
│       ├── 20260901000001_initial_schema.sql
│       ├── 20260901000002_auth_security_hardening.sql
│       ├── 20260901000003_volunteer_realtime_setup.sql
│       ├── 20260901000004_entertainment_schema.sql
│       ├── 20260901000005_calling_schema.sql
│       └── 20260902000006_admin_schema.sql
├── .gitignore                               # Clean Android and Supabase ignore rules
├── build.gradle.kts                         # Root Gradle build script
├── gradle.properties                        # Sanitized configuration template
└── settings.gradle.kts                      # Repository & plugin management
```

---

## 🔬 Core Architecture & Deep-Dive Modules

### 1. Gemini Multimodal Live API Pipeline

VisionBridge is engineered for sub-second conversational latency using Google's **Gemini Multimodal Live API** (`BidiGenerateContentConstrained` over WebSocket).

```
 ┌───────────────┐        ┌───────────────────────┐        ┌─────────────────────────┐
 │  Microphone   ├───────►│    LiveAudioEngine    ├───────►│  GeminiLiveWebSocket    │
 │ (16kHz Audio) │        │ Hardware AEC / NS /   │        │                         │
 └───────────────┘        │ Playback-Aware VAD    │        │ · Ephemeral Token Auth  │
                          └───────────────────────┘        │ · audio/pcm;rate=16000  │
 ┌───────────────┐        ┌───────────────────────┐        │ · image/jpeg frames     │
 │  Camera Feed  ├───────►│   LiveCameraStreamer  ├───────►│                         │
 │ (CameraX Jpeg)│        │ Frame throttler / OCR │        └────────────┬────────────┘
 └───────────────┘        └───────────────────────┘                     │
                                                                        ▼
 ┌───────────────┐        ┌───────────────────────┐        ┌─────────────────────────┐
 │ Speakerphone  │◄───────┤    LiveAudioEngine    │◄───────┤    Gemini Multimodal    │
 │ (24kHz Audio) │        │ Mode: MODE_STREAM     │        │        Live Model       │
 └───────────────┘        │ Instant Barge-in Cut  │        │  (Native Audio 24kHz)   │
                          └───────────────────────┘        └─────────────────────────┘
```

#### Low-Latency Audio Innovations
- **Acoustic Echo & Bleed Suppression**: When the AI speaks through the device speaker, `LiveAudioEngine` silences outgoing mic frames below the barge-in threshold so Gemini never hears or interrupts its own voice.
- **Instant Barge-In**: If the user starts speaking (RMS energy $> 0.055$), `AudioTrack` immediately flushes its buffer and pauses, giving the user conversational priority.
- **Ephemeral Token Security**: The client requests a short-lived ephemeral token from the Supabase `live-session` Edge Function. The permanent Google Gemini API key is never distributed to the Android client.

---

### 2. Conversational Voice Layer & Context Memory

The conversational assistant runs as an ambient listener mounted across the application:

```
[User speaks: "Vision, read this bill"]
               │
               ▼
      [WakeWordMatcher] ── Matches "Vision" / "विजन" / "व्हिजन"
               │
               ▼
      [Safety Fast Path Check]
       ├── "emergency" / "help" ──► Triggers SOS Screen immediately (Local)
       ├── "stop" / "silence"   ──► Mutes TTS immediately (Local)
       └── "capture" / "photo"  ──► Executes screen capture (Local)
               │
               ▼ (Everyday commands)
      [Supabase Edge Function: assistant-command]
       ├── Reads active feature memory slot (ContextMemoryManager)
       ├── Gemini evaluates intent against strict Action Whitelist
       └── Returns structured JSON (action, target, speech, question)
               │
               ▼
      [VoiceActionRouter] ──► Validates against Whitelist & Navigates
               │
               ▼
      [TextToSpeechManager] ──► Reads spoken response in active locale (en, hi, mr)
```

#### Multi-Slot Contextual Memory (`ContextMemoryManager`)
- **Non-Destructive Navigation**: When a user analyzes an item in Smart Reading or Surroundings and navigates back to Home, the analysis context remains cached.
- **Cross-Screen Follow-Ups**: From the Home Screen, the user can ask: *"Vision, how much was that bill?"* The assistant queries the `reading` slot and answers accurately without demanding a new photograph.
- **Freshness**: All context slots have a 15-minute TTL and cap summaries to prevent prompt bloat.

---

### 3. Peer-to-Peer WebRTC Volunteer Assistance

When an AI result has low confidence ($< 0.85$) or the user requests human support, VisionBridge engages its human-in-the-loop assistance pipeline:

```
 Low-Vision User                                                     Volunteer
(Android Client)                                                  (Web / Android)
       │                                                                 │
       ├──── 1. Creates help_requests row (Supabase PostgreSQL) ────────►│
       │                                                                 │
       │◄─── 2. Volunteer accepts via RPC accept_help_request ───────────┤
       │                                                                 │
       │════ 3. Supabase Realtime WebSocket Signaling (request:$id) ═════│
       │     · call:ping / call:pong                                     │
       │     · call:offer (SDP)                                          │
       │     · call:answer (SDP)                                         │
       │     · call:ice-candidate (Trickle ICE)                          │
       │                                                                 │
       │════ 4. Direct Peer-to-Peer WebRTC Media Session ════════════════│
       │                                                                 │
       │───── User Rear Camera (640x480 @ 30fps SEND_ONLY) ─────────────►│
       │                                                                 │
       │◄──── Full-Duplex Audio (Echo Cancelled / Speakerphone) ────────►│
       │                                                                 │
       │      Volunteer Camera is OFF (RECV_ONLY for bandwidth & privacy)│
```

---

### 4. Authentication & Backend Architecture

- **Supabase Auth & GoTrue**: Supports email and username-based signups (mapped to `@visionbridge.local`).
- **Silent Token Refresh**: Intercepts HTTP 401s thread-safely; uses the stored `refresh_token` to renew sessions transparently without logging out active users.
- **Row Level Security (RLS)**: Enforces strict data access rules across `profiles`, `help_requests`, `volunteer_call_logs`, `emergency_events`, and `phone_contacts`.
- **Zero-Trust Roles**: Enforces role gates (`lowVisionUser`, `volunteer`, `admin`). Admin actions require database verification via the `profiles` table before access is granted.

---

### 5. Accessibility, Gestures & TalkBack

VisionBridge conforms strictly to **WCAG AAA** guidelines:
- **Extreme Contrast**: Deep slate background (`#0F172A`), high-contrast surface (`#1E293B`), vivid cyan accents (`#00D4FF`), and warning amber (`#F59E0B`).
- **Touch Target Sizing**: Minimum interactive dimension is 56dp (ActionCards exceed 100dp).
- **Physical Gestures**:
  - **Two-Finger Double-Tap**: Instantly activates voice listening on any screen.
  - **Shake Detection**: Configurable accelerometer trigger to invoke voice or trigger SOS.
  - **Haptics**: Clear vibrational acknowledgments when voice starts, stops, or detects errors.
- **TalkBack Ready**: Every button, status chip, and camera preview contains comprehensive `contentDescription` attributes and semantic headers.

---

### 6. Entertainment, Audiobooks & Gamification

- **Global & Local Live Radio**: Integrated with the Radio Browser database. Plays streaming internet radio using an AndroidX Media3 foreground service (`EntertainmentMediaService`) that survives app backgrounding.
- **Audiobooks & Stories**: Built-in public domain classics (e.g., *Alice in Wonderland*, *Sherlock Holmes*). Maintains chapter-level resume bookmarks and integrates sentence-chunked TTS.
- **Audio Games**: Voice-first Trivia, Riddles, Memory Challenges, and 20 Questions evaluated in real-time by Gemini.
- **Gamification**: Users earn XP, maintain streaks, and complete daily accessibility challenges stored in PostgreSQL.

---

### 7. Admin Telemetry Dashboard

A dedicated, role-guarded portal for platform managers and community leaders:
- **Platform Overview**: Real-time counts of total users, registered volunteers, active volunteers, call metrics, and active SOS events.
- **User & Volunteer Management**: Searchable directory with contact information and request completion statistics.
- **Audit Logs**: Comprehensive call logs with duration, timestamp, and participant lookup.
- **Live SOS Monitoring**: Real-time tracking of active emergency events with resolution controls.

---

## 🔒 Android Permissions

| Permission | Reason for Use | Accessibility Relevance |
| :--- | :--- | :--- |
| `android.permission.RECORD_AUDIO` | Ambient wake-word detection, Gemini Live speech streaming, and WebRTC calls. | Essential for hands-free voice interaction. |
| `android.permission.CAMERA` | Scene inspection, Smart Reading, Currency detection, and WebRTC volunteer video. | Essential for environmental perception. |
| `android.permission.INTERNET` | Communication with Supabase, Gemini APIs, and WebRTC peer connections. | Essential for cloud AI and remote assistance. |
| `android.permission.ACCESS_FINE_LOCATION` | Determining location coordinates for Emergency SOS and "Where Am I?". | Critical for life safety and emergency response. |
| `android.permission.ACCESS_COARSE_LOCATION` | Approximate location detection for localized news and radio stations. | Enhances community entertainment. |
| `android.permission.MODIFY_AUDIO_SETTINGS` | Enabling speakerphone routing during WebRTC calls and Gemini Live. | Ensures loud, clear audio without holding phone to ear. |
| `android.permission.ACCESS_NETWORK_STATE` | Detecting offline state and switching between LAN, Wi-Fi, and cellular. | Prevents silent network failures. |
| `android.permission.FOREGROUND_SERVICE` | Keeping live audio radio streaming and emergency monitoring active. | Ensures uninterrupted background audio. |
| `android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Foreground media playback for Radio and Audiobooks (Android 14+). | Complies with Android 14 media service policies. |
| `android.permission.POST_NOTIFICATIONS` | Alerting users of incoming volunteer calls and emergency alerts. | Notifies low-vision users of status changes. |

---

## 🛠️ Developer Setup Guide

### Prerequisites
- **Android Studio**: Android Studio Ladybug (2024.2.1+) or Koala
- **Java Development Kit (JDK)**: JDK 17 or JDK 21
- **Android SDK**: Compile SDK 36, Platform Tools version 35+
- **Supabase Project**: Free or Pro Supabase project with Edge Functions enabled

### 1. Clone the Repository
```bash
git clone https://github.com/shailesh-pande01/visionbridge-app.git
cd visionbridge-app
```

### 2. Configure Credentials Safely

VisionBridge keeps secrets strictly isolated from Git version control. Copy the configuration template into `local.properties` (which is gitignored):

Open or create `local.properties` in the project root:
```properties
## Android SDK Path
sdk.dir=C\:\\Users\\<your-username>\\AppData\\Local\\Android\\Sdk

## VisionBridge Supabase Configuration
visionbridge.supabaseUrl=https://your-supabase-project-id.supabase.co
visionbridge.supabaseAnonKey=your_supabase_public_anon_key_here

## Optional: Local MERN Backend (For hybrid testing)
visionbridge.lanHost=10.0.2.2
visionbridge.apiPort=5000
visionbridge.prodUrl=https://your-production-backend.com
```

> [!IMPORTANT]
> Never commit private API keys or Supabase service-role keys to `gradle.properties` or source code. `app/build.gradle.kts` automatically reads credentials from `local.properties` first!

### 3. Deploy Supabase Backend (Optional for local backend development)
If you are deploying your own Supabase instance:
```bash
cd supabase
supabase link --project-ref your-project-id
supabase db push
supabase functions deploy
supabase secrets set GEMINI_API_KEY=your_gemini_api_key
```

### 4. Build and Run
1. Open the project in Android Studio.
2. Allow Gradle to sync dependencies.
3. Connect a physical Android device (recommended for CameraX and AudioRecord testing) or start an emulator with Camera & Microphone forwarding enabled.
4. Run the app (`Shift + F10`).

---

## 🧪 Testing & Quality Assurance

### Automated Unit Tests
The project includes 54 comprehensive unit tests verifying voice routing, wake-word matching, session management, and admin security:
```bash
./gradlew testDebugUnitTest
```

#### Test Coverage Summary
- `WakeWordMatcherAndRouterTest`: 17 tests verifying English, Hindi, and Marathi wake-words and fast-path commands.
- `GestureControllerAndVoiceActivationTest`: 13 tests verifying 2-finger double-taps, shake thresholds, and haptic feedback.
- `AdminDashboardTest`: 9 tests verifying zero-trust role verification, query builders, and statistic parsing.
- `VoiceFollowUpContextTest`: 6 tests verifying multi-slot contextual memory, slot TTL expiration, and cross-screen fallback.
- `SupabaseAuthTest`: 4 tests verifying login, silent refresh, and session expiration handling.
- `VoiceCommandRoutingTest`: 4 tests verifying command parsing and intent dispatching.

---

## ⚠️ Known Limitations

1. **Android Emulator Camera & Audio Latency**: The Gemini Live multimodal WebSocket requires real-time 16kHz audio input and high-quality frames. Running on the Android Studio Emulator may cause buffer underruns in `AudioRecord`; a physical Android device is strongly recommended.
2. **WebRTC Symmetric NATs**: On restrictive institutional or corporate Wi-Fi networks (Symmetric NAT), WebRTC peer connections require active TURN relays. OpenRelay credentials are included as fallbacks, but high-traffic deployments should configure a dedicated TURN server.
3. **Continuous Microphone Battery Consumption**: Continuous on-device wake-word detection consumes additional battery if left active indefinitely. VoiceManager automatically pauses during phone calls, WebRTC sessions, and background transitions to conserve battery.

---

## 🗺️ Project Roadmap

### ✅ Completed
- [x] Full Kotlin & Jetpack Compose migration.
- [x] Supabase Auth, PostgreSQL schema, RLS policies, and Edge Functions.
- [x] Bidirectional Google Gemini Multimodal Live streaming with sub-second voice responses.
- [x] Peer-to-peer WebRTC asymmetric video calling with volunteers.
- [x] Multi-slot contextual memory manager across app navigation.
- [x] Accessible Entertainment Suite: Live Radio, LibriVox Audiobooks, Audio Games & XP.
- [x] Accessible Phone Calling assistant and Daily News briefings.
- [x] Emergency SOS with automated WhatsApp dispatch and GPS tracking links.
- [x] Trilingual UI and voice support in English, Hindi, and Marathi.

### 🟡 In Progress
- [ ] Offline TensorFlow Lite / MediaPipe on-device fallbacks for basic obstacle detection without network.
- [ ] Bluetooth LE smart cane and smart glasses audio streaming integration.

### 📋 Planned
- [ ] BLE Beacon indoor navigation for transit hubs and universities.
- [ ] Ultra-wideband (UWB) precision micro-location for indoor object retrieval.

---

## 🛡️ Security Guidelines

- **Zero Hardcoded Secrets**: Client applications only hold the public Supabase Anon key. Master Gemini API keys and Supabase service-role keys are strictly maintained within Supabase Edge Function environment secrets.
- **Row Level Security (RLS)**: Users can only read and mutate their own profile data, emergency contacts, and call history.
- **Ephemeral Credentials**: Gemini Live sessions use short-lived ephemeral tokens generated server-side.
- **Client-Side Sanitation**: `.gitignore` strictly excludes local machine properties, debug logs (`adb*.txt`), and Supabase CLI session caches.

---

## 🤝 Contributing

Contributions are welcome from open-source developers and accessibility advocates!

1. Fork the repository (`https://github.com/shailesh-pande01/visionbridge-app/fork`).
2. Create your feature branch (`git checkout -b feature/amazing-feature`).
3. Commit your changes (`git commit -m 'feat: add amazing feature'`).
4. Run unit tests (`./gradlew testDebugUnitTest`).
5. Push to the branch (`git push origin feature/amazing-feature`).
6. Open a Pull Request.

---

## 📄 License

License information will be added separately. Please review repository terms before distributing.

---

<div align="center">
  <sub>Built with ❤️ for accessible technology and digital inclusion.</sub>
</div>
