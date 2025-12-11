<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android-3DDC84?style=for-the-badge&logo=android&logoColor=white" alt="Android"/>
  <img src="https://img.shields.io/badge/Kotlin-1.9+-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white" alt="Kotlin"/>
  <img src="https://img.shields.io/badge/TensorFlow%20Lite-FF6F00?style=for-the-badge&logo=tensorflow&logoColor=white" alt="TFLite"/>
  <img src="https://img.shields.io/badge/Groq%20AI-00D4AA?style=for-the-badge&logo=openai&logoColor=white" alt="Groq"/>
  <img src="https://img.shields.io/badge/Min%20SDK-26-blue?style=for-the-badge" alt="Min SDK"/>
</p>

<h1 align="center">👁️ IRIS</h1>
<h3 align="center">Intelligent Real-time Insight System</h3>
<p align="center"><em>AI-Powered Navigation Assistant for the Visually Impaired</em></p>

<p align="center">
  <img src="https://img.shields.io/badge/Version-1.0-green?style=flat-square" alt="Version"/>
  <img src="https://img.shields.io/badge/License-MIT-blue?style=flat-square" alt="License"/>
  <img src="https://img.shields.io/badge/Status-Active%20Development-yellow?style=flat-square" alt="Status"/>
</p>

---

## 📖 Table of Contents

- [Overview](#-overview)
- [Key Features](#-key-features)
- [System Architecture](#-system-architecture)
- [Technology Stack](#-technology-stack)
- [Installation](#-installation)
- [User Manual](#-user-manual)
- [Technical Specifications](#-technical-specifications)
- [API Reference](#-api-reference)
- [Project Structure](#-project-structure)
- [Known Issues & Limitations](#-known-issues--limitations)
- [Roadmap](#-roadmap)
- [Contributing](#-contributing)
- [License](#-license)

> 📚 **For Developers:** See [DEVELOPER_GUIDE.md](DEVELOPER_GUIDE.md) for complete setup instructions, code architecture, debugging tips, and learning resources.

---

## 🌟 Overview

**IRIS** (Intelligent Real-time Insight System) is an Android application designed to serve as a navigation assistant for visually impaired users. Using advanced AI and computer vision, IRIS transforms a smartphone into "smart glasses" that can:

- **See** the world through the device camera
- **Detect** obstacles and hazards in real-time
- **Analyze** walkable paths and safe routes
- **Speak** navigation instructions to guide users safely
- **Understand** scenes through AI-powered conversations

### The Vision

IRIS aims to provide independence and safety to visually impaired individuals by combining:
- **On-device AI** for instant obstacle detection (no internet required for basic navigation)
- **Cloud AI** for intelligent scene understanding and conversations
- **Intuitive voice interface** for hands-free operation

---

## 🚀 Key Features

### 🎯 Real-Time Object Detection
| Feature | Description |
|---------|-------------|
| **YOLO Detection** | Identifies 28+ object classes including people, vehicles, potholes, poles, stairs |
| **Distance Estimation** | Calculates real-world distance to detected objects |
| **Risk Assessment** | Prioritizes threats based on distance, speed, and danger level |
| **5 FPS Processing** | Optimized for battery life while maintaining safety |

### 🛤️ Path Navigation
| Feature | Description |
|---------|-------------|
| **Walkable Path Analysis** | Identifies safe walking areas using segmentation AI |
| **Directional Guidance** | Provides "Straight", "Curve Left", "Curve Right" instructions |
| **Safety Tunnel** | Defines a virtual safe corridor for walking |
| **Danger Zone Detection** | Alerts when obstacles enter the collision path |

### 🎤 Voice Interaction
| Feature | Description |
|---------|-------------|
| **Text-to-Speech** | Clear voice announcements for navigation |
| **Speech Recognition** | Voice commands for hands-free operation |
| **AI Conversations** | Ask questions about your surroundings |
| **Multi-turn Dialogue** | Context-aware follow-up questions |

### 📳 Haptic Feedback
| Vibration | Meaning |
|-----------|---------|
| **Strong (200ms)** | Critical danger - stop immediately |
| **Medium (100ms)** | Obstacle detected - proceed with caution |
| **Light (50ms)** | General alert |

---

## 🏗️ System Architecture

IRIS employs a **Dual-Brain Architecture** for optimal performance:

```
┌─────────────────────────────────────────────────────────────────┐
│                         IRIS SYSTEM                              │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│   ┌─────────────────────┐     ┌─────────────────────┐          │
│   │    REFLEX BRAIN     │     │   COGNITIVE BRAIN   │          │
│   │    (On-Device)      │     │      (Cloud)        │          │
│   ├─────────────────────┤     ├─────────────────────┤          │
│   │ • YOLO Detection    │     │ • Scene Description │          │
│   │ • Path Segmentation │     │ • Q&A Conversations │          │
│   │ • Distance Calc     │     │ • Object Queries    │          │
│   │ • Risk Scoring      │     │ • Context Memory    │          │
│   ├─────────────────────┤     ├─────────────────────┤          │
│   │ Latency: <200ms     │     │ Latency: 1-3s       │          │
│   │ Internet: ❌ None   │     │ Internet: ✅ Required│          │
│   └─────────────────────┘     └─────────────────────┘          │
│              │                          │                       │
│              └──────────┬───────────────┘                       │
│                         ▼                                       │
│              ┌─────────────────────┐                           │
│              │    OUTPUT LAYER     │                           │
│              ├─────────────────────┤                           │
│              │ • Voice (TTS)       │                           │
│              │ • Haptic Feedback   │                           │
│              │ • Visual Overlay    │                           │
│              └─────────────────────┘                           │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

### Data Flow

```
Camera Frame → YOLO Detector → Risk Scorer → Announcement Queue
                    ↓
              Path Segmentor → Navigation Command → TTS Engine
                    ↓
              Overlay View → AR Bounding Boxes → Display
```

---

## 💻 Technology Stack

### Core Technologies

| Component | Technology | Version |
|-----------|------------|---------|
| **Language** | Kotlin | 1.9+ |
| **Platform** | Android | SDK 26-35 |
| **Camera** | CameraX | 1.4.0 |
| **ML Runtime** | Google LiteRT (TensorFlow Lite) | 1.0.1 |
| **Cloud AI** | Groq API (Llama 4 Vision) | Latest |
| **Networking** | OkHttp | 4.12.0 |
| **JSON** | Gson | 2.10.1 |

### AI Models

| Model | Purpose | Input Size | Format |
|-------|---------|------------|--------|
| `best_float32.tflite` | Object Detection (YOLO) | 640×640 | TensorFlow Lite |
| `path_seg.tflite` | Path Segmentation | 640×640 | TensorFlow Lite |
| Llama 4 Scout | Scene Understanding | Variable | Cloud API |

### Detected Object Classes

```
Person      Vehicle     Pothole     Pole        Tree
Animal      Crosswalk   Obstacle    Over-bridge Railway
Road-barrier Sidewalk   Stairs      Traffic-light
Traffic-sign Train      (and more...)
```

---

## 📲 Installation

### Prerequisites

- Android device with Android 8.0 (API 26) or higher
- Camera permission
- Microphone permission (for voice mode)
- Internet connection (for voice conversations only)

### Build from Source

```bash
# Clone the repository
git clone https://github.com/SHRIMONESH/IRIS.git
cd IRIS

# Open in Android Studio
# OR build via command line:

# Windows
.\gradlew.bat assembleDebug

# macOS/Linux
./gradlew assembleDebug

# Install on connected device
adb install app/build/outputs/apk/debug/app-debug.apk
```

### Configuration

1. **API Key Setup** (Required for Voice Mode):
   - Obtain a Groq API key from [console.groq.com](https://console.groq.com)
   - Update the key in `GroqBrain.kt` (line 21)
   - ⚠️ For production, move to secure storage

2. **Permissions**:
   - Camera: Required for all features
   - Microphone: Required for voice commands
   - Vibration: Recommended for haptic feedback

---

## 📖 User Manual

### 🎯 The Golden Rule

> **Always start at the Hub.**

| Term | Definition |
|------|------------|
| **Hub Location** | A fixed spot (e.g., house entrance rug) |
| **Hub Direction** | A fixed reference (e.g., facing the door handle) |

---

### 🔄 Operating Modes

IRIS has two primary modes:

| Mode | Trigger | Purpose |
|------|---------|---------|
| **Navigation Mode** | Default on launch | Real-time obstacle detection & path guidance |
| **Voice Mode** | Press Volume Down | AI-powered scene Q&A |

---

### 📍 Scenario A: Saving an Item Location

Use this to save the location of important items (keys, wallet, etc.)

| Step | Action | Response |
|------|--------|----------|
| 1 | Stand at the **Hub** (facing reference) | - |
| 2 | Press **Volume Up** | *"Listening..."* |
| 3 | Say: **"Save Key Office"** | *"Stand at Hub. Walk to Key Office. Press Vol Up when you arrive."* |
| 4 | Turn and walk naturally to the item location | - |
| 5 | Stop exactly where you want to save | - |
| 6 | Press **Volume Up** | *"Location Saved. Returning to idle."* |

---

### 🔍 Scenario B: Finding a Saved Item

Use this to navigate back to a previously saved location.

| Step | Action | Response |
|------|--------|----------|
| 1 | Stand at the **Hub** (facing reference) | - |
| 2 | Press **Volume Up** | *"Listening..."* |
| 3 | Say: **"Find Key Office"** | *"Go to Hub. Face the reference. Press Vol Up to start."* |
| 4 | Ensure you are aligned correctly | - |
| 5 | Press **Volume Up** (confirmation) | *"Turn Left... Walk Forward... Bear Right..."* |
| 6 | Follow instructions | *"ARRIVED"* |

---

### 🗑️ Scenario C: Deleting a Saved Location

Remove a location that's no longer needed or was saved incorrectly.

| Step | Action | Response |
|------|--------|----------|
| 1 | Press **Volume Up** | *"Listening..."* |
| 2 | Say: **"Delete Key Office"** | *"Deleted location for Key Office."* |

---

### 📊 Scenario D: Checking Status

Find out how many items you have saved.

| Step | Action | Response |
|------|--------|----------|
| 1 | Press **Volume Up** | *"Listening..."* |
| 2 | Say: **"Stats"** | *"I have 3 items saved."* |

---

### 🚶 Navigation Mode Controls

| Control | Action |
|---------|--------|
| **Launch App** | Automatically starts camera and navigation |
| **Volume Down** | Toggle Voice Mode on/off |
| **Walk normally** | App announces obstacles and directions |

### Navigation Announcements

| Announcement | Meaning | Action Required |
|--------------|---------|-----------------|
| *"Clear Straight Ahead"* | Path is clear | Continue walking |
| *"Curve Left"* | Path turns left | Turn slightly left |
| *"Curve Right"* | Path turns right | Turn slightly right |
| *"Caution - [object] ahead"* | Object approaching path | Slow down |
| *"STOP - [object] in path"* | Obstacle in collision course | Stop immediately |
| *"STOP - No Clear Path"* | Cannot determine safe path | Stop and reassess |

### 🎙️ Voice Mode Controls

| Control | Action |
|---------|--------|
| **Volume Down** | Enter Voice Mode (freezes camera) |
| **Speak** | Ask questions about what you see |
| **Volume Down** | Exit Voice Mode (resumes navigation) |

### Voice Mode Example Questions

```
"What's in front of me?"
"Is there a door nearby?"
"What color is this object?"
"Is it safe to cross the street?"
"Read the sign for me"
"How many people are there?"
```

---

## 🔧 Technical Specifications

### Risk Level System

IRIS uses a 4-tier risk assessment system:

| Level | Score Range | Announcement Delay | Visual | Vibration |
|-------|-------------|-------------------|--------|-----------|
| 🔴 **CRITICAL** | > 0.75 | 400ms | Red glow, thick box | Strong (200ms) |
| 🟠 **WARNING** | > 0.50 | 800ms | Orange box | Medium (100ms) |
| 🟡 **CAUTION** | > 0.30 | 1200ms | Yellow box | Light (50ms) |
| 🟢 **INFO** | < 0.30 | 1500ms | Green box | None |

### Risk Calculation Formula

```
Risk Score = (Distance Factor × 0.4) + (Motion Factor × 0.3) + (Class Factor × 0.3) + Position Bonus

Where:
- Distance Factor: 1.0 (< 0.5m) → 0.1 (> 5m)
- Motion Factor: 1.0 (fast moving) → 0.2 (static)
- Class Factor: Vehicle (1.0) → Tree (0.5)
- Position Bonus: +0.1 if in safety tunnel
```

### Distance Estimation

```kotlin
Distance (meters) = w₀ + (w₁ × Width_pixels) + (w₂ × Height_pixels)

// Class-specific coefficients:
Person:     w₀=0.15, w₁=0.0082, w₂=0.0085
Car:        w₀=0.25, w₁=0.0095, w₂=0.0092
Pothole:    w₀=0.10, w₁=0.0075, w₂=0.0078
Pole:       w₀=0.08, w₁=0.0065, w₂=0.0070
```

### Safety Zones

```
Screen Layout:
┌────────────────────────────────────┐
│             TOP ZONE               │  ← Far objects
│           (Upper 40%)              │
├──────┬─────────────────────┬───────┤
│      │                     │       │
│ LEFT │   SAFETY TUNNEL     │ RIGHT │  ← Danger Zone
│ ZONE │     (30%-70%)       │ ZONE  │     (Bottom 60%)
│      │                     │       │
│      │ ═══ COLLISION ═══   │       │  ← Immediate threat
└──────┴─────────────────────┴───────┘
```

### Performance Targets

| Metric | Target | Purpose |
|--------|--------|---------|
| YOLO Inference | < 20ms | Real-time detection |
| Path Segmentation | < 15ms | Real-time analysis |
| Frame Rate | 5 FPS | Battery efficiency |
| Voice Response | < 3s | Conversation fluidity |

---

## 🔌 API Reference

### Groq Cloud AI Integration

**Endpoint:** `https://api.groq.com/openai/v1/chat/completions`

**Models Used:**
- Primary: `meta-llama/llama-4-scout-17b-16e-instruct`
- Fallback: `meta-llama/llama-4-maverick-17b-128e-instruct`

**Request Format:**
```json
{
  "model": "meta-llama/llama-4-scout-17b-16e-instruct",
  "messages": [
    {
      "role": "user",
      "content": [
        {"type": "text", "text": "Describe this scene"},
        {"type": "image_url", "image_url": {"url": "data:image/jpeg;base64,..."}}
      ]
    }
  ],
  "max_tokens": 512,
  "temperature": 0.7
}
```

---

## 📁 Project Structure

```
IRIS/
├── 📄 README.md                    # This file
├── 📄 build.gradle.kts             # Root build configuration
├── 📄 settings.gradle.kts          # Project settings
├── 📄 gradle.properties            # Gradle properties
├── 📁 gradle/
│   ├── 📄 libs.versions.toml       # Version catalog
│   └── 📁 wrapper/
│       └── 📄 gradle-wrapper.properties
├── 📁 app/
│   ├── 📄 build.gradle.kts         # App build configuration
│   ├── 📄 proguard-rules.pro       # ProGuard rules
│   └── 📁 src/
│       └── 📁 main/
│           ├── 📄 AndroidManifest.xml
│           ├── 📁 assets/
│           │   ├── 📄 best_float32.tflite   # YOLO model
│           │   ├── 📄 path_seg.tflite       # Segmentation model
│           │   └── 📄 labels.txt            # Class labels
│           ├── 📁 java/com/example/iris/
│           │   ├── 📄 MainActivity.kt       # Main app controller
│           │   ├── 📄 YoloDetector.kt       # Object detection
│           │   ├── 📄 PathSegmentor.kt      # Path analysis
│           │   ├── 📄 GroqBrain.kt          # Cloud AI integration
│           │   ├── 📄 VoiceManager.kt       # TTS & STT
│           │   ├── 📄 ConversationManager.kt# Dialogue context
│           │   ├── 📄 OverlayView.kt        # AR visualization
│           │   └── 📄 RiskLevel.kt          # Risk calculations
│           └── 📁 res/
│               ├── 📁 drawable/             # Icons
│               ├── 📁 layout/               # XML layouts
│               ├── 📁 values/               # Strings, colors
│               └── 📁 xml/                  # Backup rules
```

### Module Descriptions

| Module | Responsibility |
|--------|----------------|
| `MainActivity` | App lifecycle, camera binding, mode switching, announcement logic |
| `YoloDetector` | Loads YOLO model, runs inference, applies NMS, calculates distance/risk |
| `PathSegmentor` | Loads segmentation model, analyzes walkable areas, generates nav commands |
| `GroqBrain` | Handles Groq API calls, image encoding, error handling, model fallback |
| `VoiceManager` | Manages TTS engine, speech recognition, listening states |
| `ConversationManager` | Maintains conversation history, builds context-aware prompts |
| `OverlayView` | Draws AR overlays, bounding boxes, safety tunnel, HUD |
| `RiskLevel` | Defines risk enums, distance calculator, risk scorer algorithms |

---

## ⚠️ Known Issues & Limitations

### Current Limitations

| Issue | Impact | Status |
|-------|--------|--------|
| ✅ ~~No velocity tracking~~ | ~~Risk calculation uses static assumption~~ | **FIXED** - VelocityTracker now tracks objects across frames |
| API key in source | Security risk | Move to secure storage before release |
| ✅ ~~No offline voice mode~~ | ~~Requires internet for scene Q&A~~ | **FIXED** - OfflineSceneDescriber provides fallback |
| Single-device calibration | Distance estimates may vary | Re-calibrate for different phones |
| Model confidence tuning | Some low-confidence detections (false alarms) | **IN PROGRESS** - Evaluation complete, threshold tuning needed |

### Device Compatibility

| Status | Devices |
|--------|---------|
| ✅ Tested | Pixel 6-9 series, Samsung S21+ |
| ⚠️ Limited | Low-RAM devices (< 4GB) |
| ❌ Not Supported | Android < 8.0 (API 26) |

---

## 📊 Model Evaluation

IRIS includes comprehensive evaluation tools to measure model quality and identify false alarm patterns.

### Evaluation Results (December 2025)

**Test Dataset:** 30 street scene images from Pexels

| Configuration | Detections | Avg Confidence | High Conf (>0.7) | Low Conf (<0.4) |
|--------------|------------|----------------|------------------|-----------------|
| **Threshold 0.3** | 196 | 0.613 | 40.8% | 19.4% ⚠️ |
| **Threshold 0.5** | 126 | 0.741 ✅ | 63.5% | 0% ✅ |

**Key Findings:**
- ✅ Strong person detection (74.5% of detections)
- ⚠️ Moderate confidence (0.613) with 0.3 threshold
- ✅ Higher threshold (0.5) eliminates low-confidence false alarms
- 🚨 Higher threshold reduces obstacle detection by 75% (safety concern)

**Recommendation:** Use threshold 0.4 as balance point between false alarms and safety.

### Run Your Own Evaluation

```bash
# Quick evaluation with sample images
python scripts/evaluate_model_standalone.py --images test_data/images

# Test different confidence thresholds
python scripts/evaluate_model_standalone.py --images test_data/images --conf-threshold 0.4

# Full evaluation with ground truth
python scripts/evaluate_model_standalone.py --images ./test_images --coco-annotations ./annotations.json
```

See [EVALUATION_GUIDE.md](EVALUATION_GUIDE.md) for complete instructions.

---

## 🗺️ Roadmap

### Version 1.1 (In Progress)
- [x] Velocity tracking for moving objects ✅
- [x] Offline scene description fallback ✅
- [x] Model evaluation framework ✅
- [ ] Confidence threshold tuning
- [ ] Secure API key storage
- [ ] Multi-device calibration system
- [ ] Multi-language support

### Version 1.2 (Planned)
- [ ] Indoor navigation with saved locations
- [ ] Object tracking across frames
- [ ] Custom voice profiles
- [ ] Wear OS companion app

### Version 2.0 (Future)
- [ ] AR glasses integration
- [ ] Real-time translation of signs
- [ ] Community-shared navigation routes
- [ ] Emergency contact alerts

---

## 🤝 Contributing

Contributions are welcome! Please follow these steps:

1. **Fork** the repository
2. **Create** a feature branch (`git checkout -b feature/amazing-feature`)
3. **Commit** your changes (`git commit -m 'Add amazing feature'`)
4. **Push** to the branch (`git push origin feature/amazing-feature`)
5. **Open** a Pull Request

### Development Guidelines

- Follow Kotlin coding conventions
- Add unit tests for new features
- Update documentation as needed
- Test on multiple device sizes

---

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

---

## 👥 Credits

**Developed by:** SHRIMONESH

**Technologies Used:**
- TensorFlow Lite by Google
- CameraX by Android Jetpack
- Groq AI for cloud inference
- Llama 4 by Meta

---

## 📞 Support

For support, please:
- Open an [issue](https://github.com/SHRIMONESH/IRIS/issues)
- Contact the maintainer

---

<p align="center">
  <strong>IRIS - Empowering Independence Through AI Vision</strong>
</p>

<p align="center">
  Made with ❤️ for accessibility
</p>
