plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.example.iris"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.iris"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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

    kotlinOptions {
        jvmTarget = "11"
    }

    // 1. NDK Version: Required for the C++ engines inside LiteRT (runs YOLO)
    ndkVersion = "25.2.9519653"

    // 2. Crucial: Prevents YOLO model (.tflite) from being corrupted
    aaptOptions {
        noCompress("tflite", "onnx", "json", "bin", "task")
    }

    // 3. CRITICAL FIX: Prevents crash on Pixel 9 / Android 15
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

dependencies {
    // --- 1. ANDROID UI ---
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // --- 2. CAMERA (CameraX) ---
    // The eyes of the application
    val camerax_version = "1.4.0"
    implementation("androidx.camera:camera-core:${camerax_version}")
    implementation("androidx.camera:camera-camera2:${camerax_version}")
    implementation("androidx.camera:camera-lifecycle:${camerax_version}")
    implementation("androidx.camera:camera-view:${camerax_version}")

    // --- 3. REFLEX BRAIN (Layer 1: YOLO Detector + Segmentation) ---
    // Runs 'best_float32.tflite' offline using Google LiteRT (formerly TensorFlow Lite)
    implementation("com.google.ai.edge.litert:litert:1.0.1")
    implementation("com.google.ai.edge.litert:litert-support:1.0.1")

    // --- 4. COGNITIVE BRAIN (Layer 2: Groq LPU API) ---
    // The "Smart Brain" for Medicine, Recipes, and Brands.
    // We use OkHttp to talk to the ultra-fast Groq API.
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // --- 5. UTILS ---
    // JSON parsing for Groq API responses
    implementation("com.google.code.gson:gson:2.10.1")

    // --- REMOVED ---
    // implementation("com.google.ai.client.generativeai:generativeai:0.9.0") // Removed Gemini
}