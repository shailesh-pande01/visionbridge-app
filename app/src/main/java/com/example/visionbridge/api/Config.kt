package com.example.visionbridge.api

import android.os.Build
import com.example.visionbridge.BuildConfig

/**
 * Where the existing VisionBridge MERN backend lives, from the phone's point of view.
 *
 * `localhost` on an Android device means *the device itself*, never the development
 * machine, so the host has to be chosen per environment:
 *
 *  · Emulator             10.0.2.2   — the emulator's alias for the host loopback
 *  · USB + `adb reverse`  127.0.0.1  — after `adb reverse tcp:5000 tcp:5000`
 *  · Same Wi-Fi           LAN IP     — from `visionbridge.lanHost` in gradle.properties
 *
 * Rather than guessing one of these, [BackendLocator] probes them against the
 * backend's own `GET /api/health` route and remembers whichever answers.
 */
object Config {

    const val HEALTH_PATH = "/api/health"
    const val READING_EXTRACT_PATH = "/api/reading/extract"

    private val port: Int get() = BuildConfig.VISIONBRIDGE_API_PORT
    private val lanHost: String get() = BuildConfig.VISIONBRIDGE_LAN_HOST

    private val prodUrl: String get() = BuildConfig.VISIONBRIDGE_PROD_URL

    /** Modern emulator images no longer report "generic" everywhere, so check the hardware too. */
    val isEmulator: Boolean
        get() = Build.FINGERPRINT.startsWith("generic") ||
                Build.FINGERPRINT.startsWith("unknown") ||
                Build.FINGERPRINT.contains("emulator", ignoreCase = true) ||
                Build.HARDWARE.equals("goldfish", ignoreCase = true) ||
                Build.HARDWARE.equals("ranchu", ignoreCase = true) ||
                Build.MODEL.contains("google_sdk", ignoreCase = true) ||
                Build.MODEL.contains("sdk_gphone", ignoreCase = true) ||
                Build.MODEL.contains("Emulator", ignoreCase = true) ||
                Build.MODEL.contains("Android SDK built for", ignoreCase = true) ||
                Build.MANUFACTURER.contains("Genymotion", ignoreCase = true) ||
                Build.PRODUCT.startsWith("sdk") ||
                Build.PRODUCT.contains("sdk_gphone", ignoreCase = true)

    /**
     * Base URLs to try, most likely first for the current device type.
     * Duplicates are removed so a LAN host of 127.0.0.1 does not get probed twice.
     */
    val candidateBaseUrls: List<String>
        get() {
            val emulatorLoopback = "http://10.0.2.2:$port"
            val adbReverseLoopback = "http://127.0.0.1:$port"
            val lan = "http://$lanHost:$port"
            
            val candidates = mutableListOf<String>()
            if (prodUrl.isNotBlank()) {
                candidates.add(prodUrl)
            }

            if (isEmulator) {
                candidates.addAll(listOf(emulatorLoopback, adbReverseLoopback, lan))
            } else {
                candidates.addAll(listOf(adbReverseLoopback, lan, emulatorLoopback))
            }
            return candidates.distinct()
        }

    /** Human-readable summary for error messages and logs. Contains no secrets. */
    val describeCandidates: String
        get() = candidateBaseUrls.joinToString(", ")
}
