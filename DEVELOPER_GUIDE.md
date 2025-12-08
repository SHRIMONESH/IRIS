# 🛠️ IRIS Developer Guide

> **Complete guide for developers to understand, run, modify, and debug the IRIS project**

---

## 📑 Table of Contents

1. [Quick Start Commands](#-quick-start-commands)
2. [Development Environment Setup](#-development-environment-setup)
3. [Building & Running the App](#-building--running-the-app)
4. [Understanding the App Flow](#-understanding-the-app-flow)
5. [How to Use the Running App](#-how-to-use-the-running-app)
6. [Code Architecture Deep Dive](#-code-architecture-deep-dive)
7. [Making Your First Changes](#-making-your-first-changes)
8. [Debugging & Troubleshooting](#-debugging--troubleshooting)
9. [Learning Path](#-learning-path)
10. [Common Tasks Reference](#-common-tasks-reference)

---

## 🚀 Quick Start Commands

### Essential Commands Cheat Sheet

Open **PowerShell** or **Terminal** and use these commands:

```powershell
# ══════════════════════════════════════════════════════════════
# SETUP (Run once to add ADB to your path for this session)
# ══════════════════════════════════════════════════════════════
$env:Path += ";$env:LOCALAPPDATA\Android\Sdk\platform-tools"

# ══════════════════════════════════════════════════════════════
# BUILD COMMANDS
# ══════════════════════════════════════════════════════════════

# Navigate to project folder
cd D:\IRIS

# Build debug APK (run after making code changes)
.\gradlew.bat assembleDebug

# Build release APK (for production)
.\gradlew.bat assembleRelease

# Clean build (if something is broken)
.\gradlew.bat clean assembleDebug

# ══════════════════════════════════════════════════════════════
# DEVICE COMMANDS
# ══════════════════════════════════════════════════════════════

# Check if phone is connected
adb devices

# Install app on phone
adb install -r "app\build\outputs\apk\debug\app-debug.apk"

# ══════════════════════════════════════════════════════════════
# APP CONTROL COMMANDS
# ══════════════════════════════════════════════════════════════

# Start/Launch the app
adb shell am start -n com.example.iris/.MainActivity

# STOP/FORCE CLOSE the app
adb shell am force-stop com.example.iris

# Restart app (stop + start)
adb shell am force-stop com.example.iris; adb shell am start -n com.example.iris/.MainActivity

# Uninstall app completely
adb uninstall com.example.iris

# ══════════════════════════════════════════════════════════════
# LOGGING/DEBUGGING COMMANDS
# ══════════════════════════════════════════════════════════════

# View ALL app logs (very verbose)
adb logcat | Select-String "IRIS"

# View specific module logs (recommended)
adb logcat -s IRIS_MAIN:V IRIS_YOLO:V IRIS_SEG:V IRIS_GROQ:V IRIS_VOICE:V

# Clear old logs and start fresh
adb logcat -c; adb logcat -s IRIS_MAIN:V IRIS_YOLO:V

# Save logs to file for later review
adb logcat -s IRIS_MAIN:V IRIS_YOLO:V > debug_log.txt

# ══════════════════════════════════════════════════════════════
# ONE-LINER: BUILD + INSTALL + RUN (Use this most often!)
# ══════════════════════════════════════════════════════════════
.\gradlew.bat assembleDebug; adb install -r "app\build\outputs\apk\debug\app-debug.apk"; adb shell am start -n com.example.iris/.MainActivity
```

### 📋 Copy-Paste Ready Commands

| What You Want | Command |
|---------------|---------|
| **Build the app** | `.\gradlew.bat assembleDebug` |
| **Install on phone** | `adb install -r "app\build\outputs\apk\debug\app-debug.apk"` |
| **Start the app** | `adb shell am start -n com.example.iris/.MainActivity` |
| **STOP the app** | `adb shell am force-stop com.example.iris` |
| **See if phone connected** | `adb devices` |
| **View logs** | `adb logcat -s IRIS_MAIN:V` |
| **Build + Install + Run** | `.\gradlew.bat assembleDebug; adb install -r "app\build\outputs\apk\debug\app-debug.apk"; adb shell am start -n com.example.iris/.MainActivity` |

---

## 💻 Development Environment Setup

### Prerequisites Checklist

| Requirement | How to Check | How to Install |
|-------------|--------------|----------------|
| **Java 17+** | `java -version` | [Download Oracle JDK](https://www.oracle.com/java/technologies/downloads/) |
| **Android Studio** | Open it | [Download Android Studio](https://developer.android.com/studio) |
| **Android SDK** | Check `$env:LOCALAPPDATA\Android\Sdk` exists | Installed with Android Studio |
| **Android Phone** | `adb devices` shows your device | Enable USB Debugging (see below) |

### Setting Up Your Android Phone

#### Step 1: Enable Developer Options
1. Open **Settings** on your phone
2. Go to **About Phone**
3. Find **Build Number**
4. **Tap it 7 times** rapidly
5. You'll see "You are now a developer!"

#### Step 2: Enable USB Debugging
1. Go to **Settings → Developer Options**
2. Enable **USB Debugging**
3. Connect phone to PC via USB cable
4. On phone, tap **"Allow"** when prompted for USB debugging

#### Step 3: Verify Connection
```powershell
$env:Path += ";$env:LOCALAPPDATA\Android\Sdk\platform-tools"
adb devices
```

You should see:
```
List of devices attached
XXXXXXXX    device
```

If it says `unauthorized`, check your phone for a permission popup.

### Setting Up Android Studio

1. **Open Android Studio**
2. Click **File → Open**
3. Navigate to `D:\IRIS` and click **OK**
4. Wait for **Gradle Sync** to complete (bottom progress bar)
5. If prompted to update Gradle, click **Update**

#### Recommended Android Studio Settings
- **File → Settings → Editor → Font Size**: 14-16 for readability
- **View → Tool Windows → Logcat**: Keep this open for debugging

---

## 🔨 Building & Running the App

### Method 1: Using Terminal (Recommended for Speed)

```powershell
# Step 1: Open PowerShell and navigate to project
cd D:\IRIS

# Step 2: Add ADB to path (do this once per terminal session)
$env:Path += ";$env:LOCALAPPDATA\Android\Sdk\platform-tools"

# Step 3: Build the APK
.\gradlew.bat assembleDebug

# Step 4: Install on phone
adb install -r "app\build\outputs\apk\debug\app-debug.apk"

# Step 5: Launch the app
adb shell am start -n com.example.iris/.MainActivity

# Step 6: To STOP the app
adb shell am force-stop com.example.iris
```

### Method 2: Using Android Studio

1. Connect your phone via USB
2. Select your device from the dropdown (top toolbar)
3. Click the **Green Play Button** ▶️ (or press `Shift+F10`)
4. App will build, install, and launch automatically

To stop: Click the **Red Stop Button** ⬛ in Android Studio

### Build Output Locations

| Build Type | APK Location |
|------------|--------------|
| Debug | `app\build\outputs\apk\debug\app-debug.apk` |
| Release | `app\build\outputs\apk\release\app-release.apk` |

---

## 🔄 Understanding the App Flow

### Application Lifecycle

```
┌─────────────────────────────────────────────────────────────┐
│                      APP LAUNCH                              │
└─────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│                    MainActivity.onCreate()                   │
│  • Build UI (camera preview, overlay, text display)          │
│  • Initialize AI engines (YOLO, PathSegmentor, GroqBrain)   │
│  • Setup voice manager (TTS + Speech Recognition)           │
│  • Request permissions (Camera, Microphone)                  │
└─────────────────────────────────────────────────────────────┘
                            │
                            ▼
                ┌───────────────────────┐
                │  Permissions Granted? │
                └───────────────────────┘
                    │              │
                   YES             NO
                    │              │
                    ▼              ▼
            ┌──────────────┐  ┌─────────────────┐
            │ Start Camera │  │ Show Error      │
            └──────────────┘  │ "Permissions    │
                    │         │  required"      │
                    ▼         └─────────────────┘
┌─────────────────────────────────────────────────────────────┐
│                    NAVIGATION MODE (Default)                 │
│  • Camera captures frames at 5 FPS                          │
│  • Each frame → YOLO detection → Path analysis              │
│  • Results displayed on overlay                              │
│  • Voice announces obstacles and directions                  │
└─────────────────────────────────────────────────────────────┘
                            │
                    [Volume Down Pressed]
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│                      VOICE MODE                              │
│  • Camera freezes (captures current frame)                   │
│  • Image sent to Groq AI for analysis                        │
│  • User can ask questions via voice                          │
│  • AI responds with scene description                        │
└─────────────────────────────────────────────────────────────┘
                            │
                    [Volume Down Pressed]
                            │
                            ▼
                   (Back to Navigation Mode)
```

### Frame Processing Pipeline

```
Camera Frame (every 200ms)
        │
        ▼
┌───────────────────┐
│  Rotate if needed │ (handle device orientation)
└───────────────────┘
        │
        ▼
┌───────────────────┐     ┌──────────────────────────────┐
│   YOLO Detector   │────▶│ Returns: List<DetectionResult>│
│  (Object Detection)│     │ • label (person, car, etc.)  │
└───────────────────┘     │ • confidence (0.0 - 1.0)     │
        │                  │ • position (centerX, centerY)│
        │                  │ • size (width, height)       │
        │                  │ • distance (meters)          │
        ▼                  │ • riskLevel (CRITICAL/etc)   │
┌───────────────────┐     └──────────────────────────────┘
│  Path Segmentor   │
│ (Walkable areas)  │
└───────────────────┘
        │
        ▼
┌───────────────────┐     ┌──────────────────────────────┐
│ Navigation Command│────▶│ Returns: NavigationCommand   │
│    Generator      │     │ • instruction ("Curve Left") │
└───────────────────┘     │ • priority (0-3)             │
        │                  │ • action (STOP/FORWARD/etc)  │
        ▼                  └──────────────────────────────┘
┌───────────────────┐
│  Announcement     │
│     System        │
└───────────────────┘
        │
        ├───────────────┬───────────────┐
        ▼               ▼               ▼
   ┌─────────┐    ┌──────────┐    ┌──────────┐
   │  Voice  │    │ Vibration│    │  Visual  │
   │  (TTS)  │    │ (Haptic) │    │ (Overlay)│
   └─────────┘    └──────────┘    └──────────┘
```

---

## 📱 How to Use the Running App

### When App First Launches

1. **Grant Permissions**: Tap "Allow" for Camera and Microphone
2. **Wait for Initialization**: You'll see "System Initializing..."
3. **Ready State**: "Navigation Active - Vol Down for Voice"

### Navigation Mode (Default)

This is the main mode - the app continuously analyzes what the camera sees.

| What You See | What It Means |
|--------------|---------------|
| **Green boxes** | Safe objects (far away) |
| **Yellow boxes** | Caution (medium distance) |
| **Orange boxes** | Warning (getting close) |
| **Red glowing boxes** | CRITICAL (very close/dangerous) |
| **Cyan dashed lines** | Safety tunnel boundaries |
| **Red dashed line** | Danger zone boundary |

| What You Hear | What To Do |
|---------------|------------|
| "Clear Straight Ahead" | Safe to walk forward |
| "Curve Left" | Path curves left, turn slightly |
| "Curve Right" | Path curves right, turn slightly |
| "Caution - [object] ahead" | Slow down, obstacle detected |
| "STOP - [object] in path" | Stop immediately! |
| "Warning - [object] X meters on your left/right" | Obstacle nearby but not in path |

| Vibration | Meaning |
|-----------|---------|
| **Strong vibration** | STOP! Critical danger |
| **Medium vibration** | Caution, proceed carefully |
| **Light vibration** | Alert, be aware |

### Voice Mode (Press Volume Down)

| Step | What Happens |
|------|--------------|
| 1. Press Volume Down | Screen freezes, short vibration |
| 2. Wait | "Voice mode activated. Analyzing what you're looking at." |
| 3. Wait | AI describes the scene |
| 4. Speak | Ask any question about what you see |
| 5. Wait | AI responds |
| 6. Repeat | Keep asking questions |
| 7. Press Volume Down | "Returning to navigation mode" |

#### Example Voice Mode Conversation
```
[Press Volume Down]
IRIS: "Voice mode activated. I can see a living room with a sofa on the 
       left and a coffee table in the center. There's a TV mounted on 
       the wall ahead."
You:  "Is there anything on the coffee table?"
IRIS: "Yes, I can see a remote control, some magazines, and a cup."
You:  "What color is the sofa?"
IRIS: "The sofa appears to be dark blue or navy colored."
[Press Volume Down]
IRIS: "Returning to navigation mode"
```

### Controls Summary

| Button | Mode | Action |
|--------|------|--------|
| **Volume Down** | Navigation | Enter Voice Mode |
| **Volume Down** | Voice | Exit to Navigation Mode |
| **Volume Up** | Any | (Reserved for item saving - future feature) |

### Stopping the App

**From Phone:**
- Swipe up from bottom to see recent apps
- Swipe IRIS away to close

**From Computer (Terminal):**
```powershell
adb shell am force-stop com.example.iris
```

---

## 🧠 Code Architecture Deep Dive

### File-by-File Explanation

```
app/src/main/java/com/example/iris/
├── MainActivity.kt           # The main controller
├── YoloDetector.kt           # Object detection AI
├── PathSegmentor.kt          # Path analysis AI  
├── GroqBrain.kt              # Cloud AI for conversations
├── VoiceManager.kt           # Speech-to-Text & Text-to-Speech
├── ConversationManager.kt    # Conversation history + offline fallback
├── OverlayView.kt            # Visual AR overlay
├── RiskLevel.kt              # Risk calculations
├── VelocityTracker.kt        # NEW: Object velocity tracking across frames
└── OfflineSceneDescriber.kt  # NEW: Offline scene descriptions from YOLO data
```

### Detailed File Descriptions

#### 1. `MainActivity.kt` (711 lines) - The Brain
**Purpose:** Main controller that coordinates everything

**Key Sections:**
```kotlin
// Lines 1-100: Imports and variable declarations
// Lines 100-200: onCreate() - App initialization
// Lines 200-320: Lifecycle methods (onPause, onDestroy)
// Lines 320-450: Voice mode control (enter/exit)
// Lines 450-570: Camera setup and frame processing
// Lines 570-680: Announcement system
// Lines 680-711: Utility functions (TTS, vibration)
```

**Important Functions:**
| Function | What It Does |
|----------|--------------|
| `onCreate()` | Initializes everything when app starts |
| `startCamera()` | Sets up camera and frame processing |
| `enterVoiceMode()` | Freezes camera, starts AI conversation |
| `exitVoiceMode()` | Returns to navigation |
| `processDynamicAnnouncements()` | Decides what to say and when |
| `speak()` | Makes the phone talk |
| `vibrate()` | Makes the phone vibrate |

---

#### 2. `YoloDetector.kt` (315 lines) - The Eyes
**Purpose:** Detects objects in camera frames using YOLO AI

**How It Works:**
```
Input Image (any size)
        │
        ▼
┌─────────────────────┐
│ Letterbox to 640x640│ (resize with padding)
└─────────────────────┘
        │
        ▼
┌─────────────────────┐
│ Convert to ByteBuffer│ (normalize RGB values)
└─────────────────────┘
        │
        ▼
┌─────────────────────┐
│   TFLite Inference  │ (run YOLO model)
└─────────────────────┘
        │
        ▼
┌─────────────────────┐
│   Parse Outputs     │ (extract boxes, classes)
└─────────────────────┘
        │
        ▼
┌─────────────────────┐
│   Apply NMS         │ (remove duplicate boxes)
└─────────────────────┘
        │
        ▼
List<DetectionResult>
```

**Key Data Structure:**
```kotlin
data class DetectionResult(
    val label: String,        // "person", "car", "pothole"
    val confidence: Float,    // 0.0 to 1.0 (how sure)
    val centerX: Float,       // 0.0 to 1.0 (left to right)
    val centerY: Float,       // 0.0 to 1.0 (top to bottom)
    val width: Float,         // 0.0 to 1.0 (box width)
    val height: Float,        // 0.0 to 1.0 (box height)
    val distance: Float,      // Estimated meters away
    val riskScore: Float,     // 0.0 to 1.0 (danger level)
    val riskLevel: RiskLevel, // CRITICAL/WARNING/CAUTION/INFO
    val announcementDelay: Int // Milliseconds between announcements
)
```

---

#### 3. `PathSegmentor.kt` (434 lines) - The Navigator
**Purpose:** Analyzes walkable paths and generates navigation commands

**How It Works:**
```
Image + YOLO Detections
        │
        ▼
┌─────────────────────────┐
│ Run path segmentation   │
│ model on image          │
└─────────────────────────┘
        │
        ▼
┌─────────────────────────┐
│ Divide screen into      │
│ LEFT | CENTER | RIGHT   │
└─────────────────────────┘
        │
        ▼
┌─────────────────────────┐
│ Score each region for   │
│ walkability             │
└─────────────────────────┘
        │
        ▼
┌─────────────────────────┐
│ Check YOLO objects for  │
│ danger zone violations  │
└─────────────────────────┘
        │
        ▼
NavigationCommand
```

**Navigation Commands:**
| Command | Priority | Meaning |
|---------|----------|---------|
| "STOP - [object] in path" | 0 (CRITICAL) | Immediate threat |
| "Caution - [object] ahead" | 1 (WARNING) | Approaching threat |
| "Curve Left" | 2 (CAUTION) | Path turns |
| "Curve Right" | 2 (CAUTION) | Path turns |
| "Clear Straight Ahead" | 3 (INFO) | Safe to proceed |

---

#### 4. `GroqBrain.kt` (348 lines) - The Cloud Intelligence
**Purpose:** Connects to Groq AI for scene understanding

**How It Works:**
```
Image + User Question
        │
        ▼
┌─────────────────────────┐
│ Resize image to <800px  │
│ Compress to JPEG        │
│ Encode as Base64        │
└─────────────────────────┘
        │
        ▼
┌─────────────────────────┐
│ Build JSON request      │
│ with image + prompt     │
└─────────────────────────┘
        │
        ▼
┌─────────────────────────┐
│ Send to Groq API        │
│ (requires internet)     │
└─────────────────────────┘
        │
        ▼
┌─────────────────────────┐
│ Parse AI response       │
│ Return text answer      │
└─────────────────────────┘
```

**API Configuration:**
```kotlin
API_URL = "https://api.groq.com/openai/v1/chat/completions"
PRIMARY_MODEL = "meta-llama/llama-4-scout-17b-16e-instruct"
FALLBACK_MODEL = "meta-llama/llama-4-maverick-17b-128e-instruct"
```

---

#### 5. `VoiceManager.kt` (281 lines) - The Voice
**Purpose:** Handles speaking and listening

**Two Components:**
1. **Text-to-Speech (TTS):** Phone speaks to user
2. **Speech-to-Text (STT):** Phone listens to user

**Key Functions:**
```kotlin
speak(text: String, autoListenAfter: Boolean)
// Makes the phone say something
// If autoListenAfter=true, starts listening when done

startListening()
// Starts speech recognition

stopListening()
// Stops speech recognition

stopSpeaking()
// Interrupts current speech
```

---

#### 6. `RiskLevel.kt` (120 lines) - The Risk Calculator
**Purpose:** Calculates danger levels for detected objects

**Risk Levels:**
```kotlin
enum class RiskLevel {
    CRITICAL,   // Red, 400ms delay, immediate threat
    WARNING,    // Orange, 800ms delay, approaching threat
    CAUTION,    // Yellow, 1200ms delay, nearby object
    INFO        // Green, 1500ms delay, safe/distant
}
```

**Risk Calculation:**
```
Risk Score = (Distance × 0.4) + (Motion × 0.3) + (Class × 0.3) + Position Bonus

Distance Factor:
  < 0.5m → 1.0 (very dangerous)
  < 2.0m → 0.9
  < 5.0m → 0.5
  > 5.0m → 0.1 (safe)

Class Factor:
  car/vehicle → 1.0 (most dangerous)
  motorcycle → 0.9
  pothole → 0.8
  person → 0.7
  pole → 0.6
  tree → 0.5 (least dangerous)

Position Bonus:
  In safety tunnel (center 40%) → +0.1
```

---

#### 7. `OverlayView.kt` (543 lines) - The Display
**Purpose:** Draws AR overlay on camera preview

**What It Draws:**
```
┌────────────────────────────────────────────┐
│  ┌──────────────────────────────────────┐  │ ← Path Direction HUD
│  │         CLEAR STRAIGHT AHEAD         │  │   (color-coded banner)
│  └──────────────────────────────────────┘  │
│                                            │
│  ┊          ┊                ┊          ┊  │ ← Safety Tunnel
│  ┊          ┊   ┌────────┐   ┊          ┊  │   (cyan dashed lines)
│  ┊          ┊   │ person │   ┊          ┊  │
│  ┊          ┊   │  1.2m  │   ┊          ┊  │ ← Detection Boxes
│  ┊          ┊   └────────┘   ┊          ┊  │   (color = risk level)
│  ┊┄┄┄┄┄┄┄┄┄┄┊┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┊┄┄┄┄┄┄┄┄┄┄┊  │ ← Danger Zone Line
│  ┊          ┊                ┊          ┊  │   (red dashed)
│  ┊          ┊   ┌────────┐   ┊          ┊  │
│  ┊          ┊   │  car   │   ┊          ┊  │
│  ┊          ┊   │ ⚠ 0.5m │   ┊          ┊  │ ← Critical (red glow)
│  ┊          ┊   └────────┘   ┊          ┊  │
│  ┌──────────────────────────────────────┐  │
│  │ Objects: 2 | Critical: 1 | Warn: 1   │  │ ← Stats Bar
│  └──────────────────────────────────────┘  │
└────────────────────────────────────────────┘
```

---

#### 8. `ConversationManager.kt` (140 lines) - The Memory
**Purpose:** Maintains conversation context for voice mode

**How It Works:**
```kotlin
// Stores conversation as list of messages
val conversationHistory = mutableListOf<Message>()

data class Message(
    val role: String,     // "user" or "assistant"
    val content: String,  // The actual text
    val timestamp: Long   // When it was said
)

// When you ask a question:
// 1. Adds your question to history
// 2. Builds prompt with previous context
// 3. Sends to GroqBrain (or falls back to OfflineSceneDescriber if offline)
// 4. Adds response to history
// 5. Returns response
```

---

#### 9. `VelocityTracker.kt` (290 lines) - The Motion Analyzer ✨ NEW
**Purpose:** Tracks objects across frames to calculate movement velocity

**How It Works:**
```
Frame N Detections          Frame N+1 Detections
       │                            │
       ▼                            ▼
┌─────────────────────────────────────────┐
│         IoU-Based Matching              │
│  (Match objects by bounding box overlap)│
└─────────────────────────────────────────┘
                    │
                    ▼
┌─────────────────────────────────────────┐
│         Velocity Calculation            │
│  velocity = position_delta / time_delta │
│  (with exponential smoothing)           │
└─────────────────────────────────────────┘
                    │
                    ▼
         TrackedDetection with:
         - speed (normalized units/sec)
         - isApproaching (moving towards camera)
         - isMovingIntoPath (entering safety tunnel)
```

**Key Data Structures:**
```kotlin
data class TrackedObject(
    val id: Int,              // Unique tracking ID
    val label: String,        // Object class
    var velocityX: Float,     // Horizontal movement
    var velocityY: Float,     // Vertical movement (down = approaching)
    var missingFrames: Int    // Frames since last seen
)

data class TrackedDetection(
    val detection: DetectionResult,
    val trackId: Int,
    val speed: Float,
    val isApproaching: Boolean,
    val isMovingIntoPath: Boolean
)
```

**Integration:** YoloDetector's `recalculateRiskWithVelocity()` uses this data to boost risk scores for approaching objects.

---

#### 10. `OfflineSceneDescriber.kt` (200 lines) - The Offline Brain ✨ NEW
**Purpose:** Generates scene descriptions from YOLO detections when offline

**How It Works:**
```
YOLO Detections
       │
       ▼
┌─────────────────────────────────────────┐
│         Categorize Objects              │
│  people, vehicles, obstacles, navigation│
└─────────────────────────────────────────┘
       │
       ▼
┌─────────────────────────────────────────┐
│      Generate Natural Language          │
│  "2 people ahead, 1 on left at 1.5m..." │
└─────────────────────────────────────────┘
       │
       ▼
        Scene Description String
```

**Supported Question Types:**
| Question Type | Example | Response |
|---------------|---------|----------|
| Count | "How many people?" | "I detect 3 people..." |
| Presence | "Is there a car?" | "Yes, there is a car at 2.3m..." |
| Location | "Where is the person?" | "Person is on your left..." |
| Safety | "Is it safe?" | "Caution: obstacles detected..." |
| Distance | "How far is the car?" | "The car is approximately 4.5m..." |
| Movement | "What's moving?" | "Approaching objects: person..." |

**Integration:** ConversationManager automatically falls back to this when network is unavailable.

---

## 🔧 Making Your First Changes

### Beginner Exercises

#### Exercise 1: Change Speech Rate
**File:** `MainActivity.kt`, Line ~626

Find:
```kotlin
tts.setSpeechRate(0.9f)
```

Change to:
```kotlin
tts.setSpeechRate(1.1f)  // Faster speech
```

**Rebuild and test:**
```powershell
.\gradlew.bat assembleDebug; adb install -r "app\build\outputs\apk\debug\app-debug.apk"; adb shell am start -n com.example.iris/.MainActivity
```

---

#### Exercise 2: Change Confidence Threshold
**File:** `YoloDetector.kt`, Line ~21

Find:
```kotlin
private val CONFIDENCE_THRESHOLD = 0.40f
```

Change to:
```kotlin
private val CONFIDENCE_THRESHOLD = 0.50f  // More strict, fewer detections
// OR
private val CONFIDENCE_THRESHOLD = 0.30f  // Less strict, more detections
```

---

#### Exercise 3: Change Safety Tunnel Width
**File:** `PathSegmentor.kt`, Line ~240

Find:
```kotlin
val inTunnel = detection.centerX in 0.30f..0.70f
```

Change to:
```kotlin
val inTunnel = detection.centerX in 0.25f..0.75f  // Wider tunnel
// OR
val inTunnel = detection.centerX in 0.35f..0.65f  // Narrower tunnel
```

---

#### Exercise 4: Add a New Voice Announcement
**File:** `MainActivity.kt`

Find the `generateSpatialAnnouncement()` function and modify:

```kotlin
private fun generateSpatialAnnouncement(detection: YoloDetector.DetectionResult): String {
    // ... existing code ...
    
    // Add your custom announcement for specific objects:
    if (detection.label == "pothole") {
        return "Watch out! Pothole $distanceText $position"
    }
    
    // ... rest of function ...
}
```

---

### Intermediate Exercises

#### Exercise 5: Add a New Detected Class Alert
Make the app specifically announce certain objects more urgently.

**File:** `RiskLevel.kt`

Add to `classDangerFactors`:
```kotlin
private val classDangerFactors = mapOf(
    "car" to 1.0f,
    // ... existing entries ...
    "dog" to 0.75f,  // Add this - dogs are moderately dangerous
    "stairs" to 0.85f  // Add this - stairs are quite dangerous
)
```

---

#### Exercise 6: Create a Custom Vibration Pattern
**File:** `MainActivity.kt`

Add a new function:
```kotlin
private fun vibratePattern(pattern: String) {
    when (pattern) {
        "sos" -> {
            // Short-short-short, long-long-long, short-short-short
            val timings = longArrayOf(0, 100, 100, 100, 100, 100, 200, 300, 100, 300, 100, 300, 200, 100, 100, 100, 100, 100)
            vibrator.vibrate(VibrationEffect.createWaveform(timings, -1))
        }
        "warning" -> {
            val timings = longArrayOf(0, 200, 100, 200)
            vibrator.vibrate(VibrationEffect.createWaveform(timings, -1))
        }
    }
}
```

---

## 🐛 Debugging & Troubleshooting

### Viewing Logs in Real-Time

```powershell
# All IRIS logs
adb logcat -s IRIS_MAIN:V IRIS_YOLO:V IRIS_SEG:V IRIS_GROQ:V IRIS_VOICE:V

# Just main activity
adb logcat -s IRIS_MAIN:V

# Just AI detection
adb logcat -s IRIS_YOLO:V IRIS_SEG:V

# Just voice/cloud
adb logcat -s IRIS_VOICE:V IRIS_GROQ:V
```

### Common Problems & Solutions

| Problem | Cause | Solution |
|---------|-------|----------|
| "Build failed" | Code syntax error | Check Android Studio for red underlines |
| "App crashes on launch" | Missing model files | Ensure `.tflite` files are in `assets/` |
| "Camera is black" | Permission denied | Uninstall app, reinstall, grant permissions |
| "No detections" | Model not loaded | Check logcat for IRIS_YOLO errors |
| "Voice mode not responding" | No internet | Check WiFi/data connection |
| "App not responding" | Heavy processing | Wait, or check for infinite loops |
| "Device not found" | USB debugging off | Re-enable USB debugging on phone |

### Debug Workflow

```
1. Something broke?
        │
        ▼
2. Check logcat:
   adb logcat -s IRIS_MAIN:V IRIS_YOLO:V 2>&1 | Select-Object -Last 50
        │
        ▼
3. Look for "Error" or "Exception"
        │
        ▼
4. Find the file and line number
        │
        ▼
5. Fix the issue
        │
        ▼
6. Rebuild: .\gradlew.bat assembleDebug
        │
        ▼
7. Reinstall: adb install -r "app\build\outputs\apk\debug\app-debug.apk"
        │
        ▼
8. Test again
```

### Useful ADB Debugging Commands

```powershell
# Check if app is running
adb shell pidof com.example.iris

# Get app memory usage
adb shell dumpsys meminfo com.example.iris

# Get crash logs
adb shell "logcat -b crash -d" | Select-String "iris"

# Record screen (useful for demos)
adb shell screenrecord /sdcard/demo.mp4
# Press Ctrl+C to stop, then:
adb pull /sdcard/demo.mp4 .

# Take screenshot
adb shell screencap /sdcard/screenshot.png
adb pull /sdcard/screenshot.png .
```

---

## 📚 Learning Path

### Week 1: Fundamentals

| Day | Topic | Resource | Time |
|-----|-------|----------|------|
| 1 | Kotlin Basics | [Kotlin Koans](https://play.kotlinlang.org/koans) | 2-3 hrs |
| 2 | Kotlin Classes & Data Classes | Same | 2 hrs |
| 3 | Kotlin Coroutines Intro | [Coroutines Guide](https://kotlinlang.org/docs/coroutines-overview.html) | 2 hrs |
| 4 | Android Activity Lifecycle | [Android Docs](https://developer.android.com/guide/components/activities/activity-lifecycle) | 2 hrs |
| 5 | Read `RiskLevel.kt` completely | This project | 1 hr |
| 6 | Read `VoiceManager.kt` completely | This project | 2 hrs |
| 7 | Do Exercises 1-3 above | This guide | 2 hrs |

### Week 2: Core Technologies

| Day | Topic | Resource | Time |
|-----|-------|----------|------|
| 1 | CameraX Basics | [CameraX Codelab](https://developer.android.com/codelabs/camerax-getting-started) | 3 hrs |
| 2 | Custom Views & Canvas | [Custom View Tutorial](https://developer.android.com/develop/ui/views/layout/custom-views/custom-components) | 2 hrs |
| 3 | Read `OverlayView.kt` | This project | 2 hrs |
| 4 | TensorFlow Lite Basics | [TFLite Android](https://www.tensorflow.org/lite/android/quickstart) | 3 hrs |
| 5 | Read `YoloDetector.kt` | This project | 3 hrs |
| 6 | Read `PathSegmentor.kt` | This project | 2 hrs |
| 7 | Do Exercises 4-6 above | This guide | 3 hrs |

### Week 3: Advanced Topics

| Day | Topic | Resource | Time |
|-----|-------|----------|------|
| 1 | HTTP & REST APIs | [OkHttp Guide](https://square.github.io/okhttp/) | 2 hrs |
| 2 | JSON Parsing | Practice with `GroqBrain.kt` | 2 hrs |
| 3 | Read `GroqBrain.kt` completely | This project | 2 hrs |
| 4 | Read `ConversationManager.kt` | This project | 1 hr |
| 5 | Read `MainActivity.kt` (Part 1) | Lines 1-350 | 3 hrs |
| 6 | Read `MainActivity.kt` (Part 2) | Lines 350-711 | 3 hrs |
| 7 | Plan your first feature! | Your choice | - |

---

## 📋 Common Tasks Reference

### Adding a New Feature Checklist

```
□ 1. Identify which file(s) to modify
□ 2. Write pseudocode first
□ 3. Implement changes
□ 4. Test with: .\gradlew.bat assembleDebug
□ 5. Check for errors in Android Studio
□ 6. Install: adb install -r "app\build\outputs\apk\debug\app-debug.apk"
□ 7. Test on device
□ 8. Check logs: adb logcat -s IRIS_MAIN:V
□ 9. Fix any issues
□ 10. Commit changes to git
```

### Git Commands

```powershell
# Check status
git status

# Add all changes
git add .

# Commit with message
git commit -m "Description of what you changed"

# Push to GitHub
git push origin master

# Pull latest changes
git pull origin master

# Create a new branch for features
git checkout -b feature/my-new-feature

# Switch back to master
git checkout master
```

### Quick Reference Card

| Task | Command |
|------|---------|
| Build | `.\gradlew.bat assembleDebug` |
| Install | `adb install -r "app\build\outputs\apk\debug\app-debug.apk"` |
| Run | `adb shell am start -n com.example.iris/.MainActivity` |
| **STOP** | `adb shell am force-stop com.example.iris` |
| Logs | `adb logcat -s IRIS_MAIN:V` |
| Clean | `.\gradlew.bat clean` |
| All-in-one | `.\gradlew.bat assembleDebug; adb install -r "app\build\outputs\apk\debug\app-debug.apk"; adb shell am start -n com.example.iris/.MainActivity` |

---

## 🎯 Next Steps

Now that you have this guide, here's what to do:

1. **Bookmark this file** - You'll reference it often
2. **Start with Week 1 learning path** - Build your foundation
3. **Try the exercises** - Hands-on learning is best
4. **Use the debug commands** - When things break, check logs first
5. **Ask questions** - When stuck, check logs and documentation

---

**Happy Coding! 🚀**

*Remember: Every expert was once a beginner. Take it one step at a time.*
