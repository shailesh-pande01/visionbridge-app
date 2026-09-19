import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.visionbridge"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.example.visionbridge"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // ── VisionBridge backend location & Supabase configuration ──
        // Prioritizes local.properties (gitignored) over gradle.properties (committed)
        val localProperties = Properties()
        val localPropertiesFile = rootProject.file("local.properties")
        if (localPropertiesFile.exists()) {
            FileInputStream(localPropertiesFile).use { localProperties.load(it) }
        }
        fun resolveProp(key: String, fallback: String): String {
            val localVal = localProperties.getProperty(key)?.trim()
            if (!localVal.isNullOrBlank() && !localVal.startsWith("your_") && !localVal.contains("your-supabase")) {
                return localVal
            }
            val projVal = (project.findProperty(key) as? String)?.trim()
            if (!projVal.isNullOrBlank() && !projVal.startsWith("your_") && !projVal.contains("your-supabase")) {
                return projVal
            }
            return fallback
        }

        buildConfigField(
            "String",
            "VISIONBRIDGE_LAN_HOST",
            "\"${resolveProp("visionbridge.lanHost", "10.0.2.2")}\""
        )
        buildConfigField(
            "int",
            "VISIONBRIDGE_API_PORT",
            resolveProp("visionbridge.apiPort", "5000")
        )
        buildConfigField(
            "String",
            "VISIONBRIDGE_PROD_URL",
            "\"${resolveProp("visionbridge.prodUrl", "")}\""
        )
        buildConfigField(
            "String",
            "VISIONBRIDGE_SUPABASE_URL",
            "\"${resolveProp("visionbridge.supabaseUrl", "https://your-supabase-project.supabase.co")}\""
        )
        buildConfigField(
            "String",
            "VISIONBRIDGE_SUPABASE_ANON_KEY",
            "\"${resolveProp("visionbridge.supabaseAnonKey", "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.dummy_anon_key")}\""
        )
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.exifinterface)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation("androidx.navigation:navigation-compose:2.7.7")
    testImplementation(libs.junit)
    testImplementation("org.json:json:20240303")
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // CameraX
    val camerax_version = "1.3.4"
    implementation("androidx.camera:camera-core:${camerax_version}")
    implementation("androidx.camera:camera-camera2:${camerax_version}")
    implementation("androidx.camera:camera-lifecycle:${camerax_version}")
    implementation("androidx.camera:camera-view:${camerax_version}")
    
    // OkHttp
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    
    // WebRTC for Volunteer Assistance Call
    implementation("io.getstream:stream-webrtc-android:1.1.1")
    
    // Socket.io for Realtime updates & WebRTC signaling
    implementation("io.socket:socket.io-client:2.1.1") {
        exclude(group = "org.json", module = "json")
    }

    // Preferences DataStore
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Location
    implementation("com.google.android.gms:play-services-location:21.3.0")

    // Material Icons Extended
    implementation("androidx.compose.material:material-icons-extended")

    // AndroidX Media3 (ExoPlayer & MediaSession)
    val media3_version = "1.3.1"
    implementation("androidx.media3:media3-exoplayer:${media3_version}")
    implementation("androidx.media3:media3-session:${media3_version}")
    implementation("androidx.media3:media3-ui:${media3_version}")
    implementation("androidx.media3:media3-common:${media3_version}")

    // ONNX Runtime for Offline OCR
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.30.0")
}