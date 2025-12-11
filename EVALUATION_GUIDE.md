# 📊 IRIS Model Evaluation Guide

> **Comprehensive framework for evaluating YOLO detection and distance estimation performance**

---

## 📑 Table of Contents

1. [Overview](#-overview)
2. [Why Evaluation Matters](#-why-evaluation-matters)
3. [Evaluation Framework](#-evaluation-framework)
4. [Test Data & Ground Truth](#-test-data--ground-truth)
5. [How to Run Evaluation](#-how-to-run-evaluation)
6. [Understanding the Metrics](#-understanding-the-metrics)
7. [Interpreting Results](#-interpreting-results)
8. [Improving Performance](#-improving-performance)

---

## 🌟 Overview

The IRIS Model Evaluation system tests the accuracy and reliability of:
1. **YOLO Object Detection** - How well we detect objects
2. **Distance Estimation** - How accurate our distance calculations are
3. **False Alarm Analysis** - Understanding when and why we make mistakes

**Critical for Safety:** In a vision assistance system, false alarms can be dangerous:
- **False Positives** (phantom detections) → User loses trust, ignores real warnings
- **False Negatives** (missed detections) → User doesn't know about real danger
- **Distance Errors** → Wrong urgency assessment, poor decision making

---

## ⚠️ Why Evaluation Matters

### The Problem
As you noted: *"model is working good but the outputs are imperfect sometimes false alarms are dangerous"*

### Real-World Impact

| Issue | Example | Danger Level |
|-------|---------|--------------|
| **False Positive** | Detects "car" when it's a shadow | 🟡 User fatigue, ignored warnings |
| **False Negative** | Misses pothole in path | 🔴 User falls, gets injured |
| **Distance Under-estimate** | Says car is 5m away when it's 2m | 🔴 User thinks they have more time |
| **Distance Over-estimate** | Says person is 10m away when it's 5m | 🟡 Confusing, less urgent than needed |
| **Wrong Classification** | Calls a motorcycle a "bicycle" | 🟠 Wrong risk assessment |

### What We Need to Know
1. **Detection Accuracy**: How often are we right vs wrong?
2. **Class Performance**: Which objects are detected well? Which are missed?
3. **Confidence Calibration**: Are high-confidence predictions actually more accurate?
4. **Distance Accuracy**: How far off are our distance estimates?
5. **Critical Failure Modes**: What specific scenarios cause failures?

---

## 🏗️ Evaluation Framework

### TIER 1: Detection Performance (Model Quality)

**Goal:** Measure YOLO's accuracy in detecting objects

#### Metrics

| Metric | What It Measures | Formula | Good Value |
|--------|------------------|---------|------------|
| **Precision** | Of all detections, how many are real? | TP / (TP + FP) | >0.85 |
| **Recall** | Of all real objects, how many detected? | TP / (TP + FN) | >0.90 |
| **F1 Score** | Balance of precision & recall | 2 × (P × R) / (P + R) | >0.85 |
| **mAP@0.5** | Mean Average Precision at IoU=0.5 | Industry standard | >0.60 |
| **False Positive Rate** | How often we hallucinate objects | FP / (FP + TN) | <0.10 |
| **False Negative Rate** | How often we miss objects | FN / (FN + TP) | <0.10 |

**Definitions:**
- **TP (True Positive)**: Correctly detected an object
- **FP (False Positive)**: Detected object that doesn't exist (phantom)
- **FN (False Negative)**: Missed a real object (most dangerous!)
- **TN (True Negative)**: Correctly didn't detect (background)

#### Per-Class Analysis

Critical object classes for IRIS:
```
High Priority (Safety Critical):
- Person (pedestrians)
- Car, Bus, Truck (vehicles)
- Pothole (ground hazards)
- Pole, Tree (static obstacles)

Medium Priority:
- Motorcycle, Bicycle
- Traffic Light, Stop Sign
- Bench, Chair

Low Priority:
- Bird, Cat, Dog
- Background objects
```

---

### TIER 2: Distance Estimation Performance

**Goal:** Measure accuracy of distance calculations

#### Metrics

| Metric | What It Measures | Formula | Acceptable Error |
|--------|------------------|---------|------------------|
| **MAE** (Mean Absolute Error) | Average distance error | Σ\|pred - actual\| / N | <0.5m for close objects |
| **RMSE** (Root Mean Square Error) | Penalizes large errors | √(Σ(pred - actual)²/N) | <0.8m overall |
| **Relative Error** | Error as % of actual distance | \|pred - actual\| / actual | <20% |
| **Under-estimation Rate** | How often we say "farther than reality" | Count(pred > actual) / N | <10% (CRITICAL!) |
| **Over-estimation Rate** | How often we say "closer than reality" | Count(pred < actual) / N | <30% (acceptable) |

**Why Under-estimation is Critical:**
- Saying "car is 5m away" when it's 2m → User thinks they have time, gets hit
- Saying "pothole is 3m away" when it's 1m → User doesn't react in time

**Distance Bands Accuracy:**
```
CRITICAL ZONE (0-2m):   Error must be <0.3m
DANGER ZONE (2-5m):     Error must be <0.5m  
CAUTION ZONE (5-10m):   Error can be <1.0m
INFO ZONE (>10m):       Error can be <2.0m
```

---

## 📁 Test Data & Ground Truth

### Three Evaluation Modes

#### Mode 1: Basic Statistics (No Ground Truth)
**Best for: Quick testing, initial insights**

```powershell
python scripts/evaluate_model_standalone.py --images ./any_images_folder
```

**What you get:**
- Detection counts per image
- Confidence distribution
- Class distribution
- Processing speed
- NO precision/recall (needs ground truth)

**Use this when:**
- You have your own test images
- Want quick sanity check
- Don't need benchmark metrics

---

#### Mode 2: Your Own Labeled Data
**Best for: Real-world scenarios specific to IRIS**

Create simple JSON annotations for your test images:
```json
{
  "images": [
    {
      "file_name": "sidewalk1.jpg",
      "objects": [
        {"class": "person", "bbox": [0.3, 0.5, 0.2, 0.4]},
        {"class": "pole", "bbox": [0.7, 0.3, 0.1, 0.6]}
      ]
    }
  ]
}
```

Then run:
```powershell
python scripts/evaluate_model_standalone.py --images ./my_images --annotations ./my_annotations.json
```

---

#### Mode 3: COCO Dataset (Full Benchmark)
**Best for: Industry-standard comparison**

### Dataset: COCO 2017 (Common Objects in Context)

**Why COCO?**
- ✅ 80 object classes (includes all our critical objects)
- ✅ 5,000 validation images with bounding box annotations
- ✅ Industry standard for object detection evaluation
- ✅ Publicly available, well-documented
- ✅ Ground truth annotations in JSON format

**COCO Classes Relevant to IRIS:**
```json
{
  "1": "person",
  "2": "bicycle", 
  "3": "car",
  "4": "motorcycle",
  "6": "bus",
  "7": "train",
  "8": "truck",
  "10": "traffic light",
  "11": "fire hydrant",
  "13": "stop sign",
  "14": "parking meter",
  "15": "bench"
}
```

**How to use:**
1. Download COCO validation images (optional - large download)
2. Annotations already downloaded at `test_data/temp/annotations/instances_val2017.json`
3. Run evaluation with annotations

### Distance Ground Truth Challenge

**Problem:** COCO doesn't provide depth/distance information

**Solution - Heuristic Estimation:**
The evaluation script estimates distance using:
```python
# Known real-world object sizes
person_height = 1.7m
car_length = 4.5m
bus_length = 12.0m

# Use bounding box height to estimate distance
# Larger box in image = closer object
# Smaller box in image = farther object
estimated_distance = (real_height × focal_length) / box_height
```

**Limitations:** This is approximate, not perfect ground truth. Real-world validation with measured distances would be more accurate.

---

## 🚀 How to Run Evaluation

### Overview

**✅ Runs entirely on your PC - NO ANDROID DEVICE NEEDED!**

The evaluation uses:
- Python script on your PC
- TensorFlow Lite model (`.tflite` file)
- Test images from disk
- Optional COCO ground truth annotations

### Prerequisites

```powershell
# Ensure you have Python 3.7+
python --version

# Install required packages
pip install tensorflow pillow numpy
```

### Quick Start (Recommended)

```powershell
# Navigate to project
cd D:\IRIS

# Option 1: Quick test with any available images
python scripts/evaluate_model_standalone.py --quick-test

# Option 2: Test with your own images
python scripts/evaluate_model_standalone.py --images ./my_test_images

# Option 3: Full COCO evaluation (requires downloaded dataset)
python scripts/evaluate_model_standalone.py --images ./test_data/images --coco-annotations ./test_data/annotations.json
```

### What Happens

1. **Model Loading**: Script loads your `.tflite` model from `app/src/main/assets/`
2. **Image Processing**: Preprocesses images (resize, normalize) same as Android app
3. **Inference**: Runs YOLO detection on each image
4. **Analysis**: Calculates metrics, generates reports
5. **Output**: Prints results to console and saves to files

**No phone. No ADB. No Android build. Just Python on PC.**

---

## 📈 Understanding the Metrics

### Detection Metrics Explained

#### Precision (Positive Predictive Value)
```
Precision = True Positives / (True Positives + False Positives)

Example:
- YOLO detects 100 objects
- 85 are real, 15 are phantoms
- Precision = 85 / 100 = 0.85 (85%)

Interpretation:
- High precision (>0.9) = Few false alarms, trustworthy
- Low precision (<0.7) = Many false alarms, user fatigue
```

#### Recall (Sensitivity, True Positive Rate)
```
Recall = True Positives / (True Positives + False Negatives)

Example:
- Ground truth: 100 real objects
- YOLO detects 90 of them
- Recall = 90 / 100 = 0.90 (90%)

Interpretation:
- High recall (>0.9) = Catches most objects, safe
- Low recall (<0.8) = Misses many objects, DANGEROUS
```

#### F1 Score (Harmonic Mean)
```
F1 = 2 × (Precision × Recall) / (Precision + Recall)

Example:
- Precision = 0.85, Recall = 0.90
- F1 = 2 × (0.85 × 0.90) / (0.85 + 0.90) = 0.874

Interpretation:
- Balances precision and recall
- Good: >0.85, Acceptable: >0.75, Poor: <0.65
```

#### Confusion Matrix Example

```
                Predicted
               Pos    Neg
Actual  Pos  │ 85  │ 15 │  ← 15 missed (False Negatives) - DANGEROUS!
        Neg  │ 10  │ 890│  ← 10 phantom (False Positives) - Annoying
        
- 85 True Positives
- 10 False Positives (said "car" but nothing there)
- 15 False Negatives (missed 15 real cars) ⚠️
- 890 True Negatives (correctly ignored background)

Precision = 85 / (85 + 10) = 0.89
Recall = 85 / (85 + 15) = 0.85
```

---

### Distance Metrics Explained

#### Mean Absolute Error (MAE)
```
MAE = (Σ |predicted_distance - actual_distance|) / N

Example:
Image 1: Predicted 3.0m, Actual 2.5m → Error = 0.5m
Image 2: Predicted 5.2m, Actual 6.0m → Error = 0.8m
Image 3: Predicted 1.8m, Actual 2.0m → Error = 0.2m

MAE = (0.5 + 0.8 + 0.2) / 3 = 0.5m

Interpretation:
- <0.5m: Excellent for safety
- 0.5-1.0m: Acceptable
- >1.0m: Needs improvement
```

#### Critical: Under-estimation Analysis
```
Object at 2.0m, we say 5.0m → User thinks "not urgent"
Object at 1.5m, we say 3.0m → User doesn't react in time

Under-estimation Rate = Count(predicted > actual) / Total
Target: <10% for critical zone
```

---

## 🔍 Interpreting Results

### Good Performance Example

```
TIER 1 - Detection Performance:
✅ Overall Precision: 0.88 (88%)
✅ Overall Recall: 0.92 (92%)  
✅ F1 Score: 0.90
✅ False Positive Rate: 0.08 (8%)
✅ False Negative Rate: 0.08 (8%)

Per-Class Performance:
✅ Person:     Precision=0.91, Recall=0.94  (EXCELLENT)
✅ Car:        Precision=0.89, Recall=0.90  (GOOD)
⚠️  Motorcycle: Precision=0.78, Recall=0.82  (Needs improvement)
❌ Pothole:    Precision=0.65, Recall=0.70  (POOR - not in COCO, expected)

TIER 2 - Distance Estimation:
✅ Mean Absolute Error: 0.48m
✅ RMSE: 0.72m
✅ Under-estimation Rate: 8% (SAFE)
⚠️  Over-estimation Rate: 35% (Acceptable)

Critical Zone (0-2m):
✅ MAE: 0.31m (Excellent)
✅ Under-estimation: 5% (SAFE)
```

**Interpretation:** Model is performing well for most critical objects. Potholes need custom training.

---

### Poor Performance Example

```
TIER 1 - Detection Performance:
⚠️  Overall Precision: 0.72 (72%)
❌ Overall Recall: 0.65 (65%)  
❌ F1 Score: 0.68
❌ False Positive Rate: 0.22 (22% phantom detections!)
❌ False Negative Rate: 0.35 (35% missed objects!)

Per-Class Performance:
✅ Car:     Precision=0.85, Recall=0.88  (Good)
❌ Person:  Precision=0.68, Recall=0.55  (DANGEROUS - missing half the people!)
❌ Bicycle: Precision=0.60, Recall=0.48  (Poor)

TIER 2 - Distance Estimation:
❌ Mean Absolute Error: 1.2m (Too high)
❌ RMSE: 1.8m
❌ Under-estimation Rate: 28% (DANGEROUS!)

Critical Zone (0-2m):
❌ MAE: 0.85m (Unacceptable)
❌ Under-estimation: 30% (USER SAFETY RISK!)
```

**Interpretation:** Model has serious issues:
1. Missing 35% of objects (especially people) - CRITICAL SAFETY ISSUE
2. 22% false alarms - user will stop trusting system
3. Distance under-estimation is dangerous - user thinks they have more time

**Action Required:** Model needs improvement before real-world use.

---

## 🛠️ Improving Performance

### If Detection Precision is Low (<0.80)

**Problem:** Too many false positives (phantom detections)

**Solutions:**
1. **Increase Confidence Threshold**
   ```kotlin
   // In YoloDetector.kt
   private val CONFIDENCE_THRESHOLD = 0.5f  // Try 0.6f or 0.7f
   ```

2. **Improve NMS (Non-Maximum Suppression)**
   ```kotlin
   private val NMS_THRESHOLD = 0.4f  // Try 0.3f for stricter filtering
   ```

3. **Add Class-Specific Thresholds**
   ```kotlin
   val classConfidenceThresholds = mapOf(
       "person" to 0.5f,
       "car" to 0.6f,
       "pothole" to 0.7f  // Require higher confidence for uncertain classes
   )
   ```

4. **Train with More Negative Examples**
   - Include images of common false positive scenarios
   - Shadows, reflections, patterns that look like objects

---

### If Detection Recall is Low (<0.85)

**Problem:** Missing too many real objects (false negatives)

**Solutions:**
1. **Lower Confidence Threshold**
   ```kotlin
   private val CONFIDENCE_THRESHOLD = 0.4f  // More lenient
   ```

2. **Multi-Scale Detection**
   - Ensure YOLO can detect small and large objects
   - Check if your model has multiple detection heads

3. **Improve Image Quality**
   - Better preprocessing
   - Handle low-light conditions
   - Reduce motion blur

4. **Retrain Model**
   - Include more examples of hard-to-detect objects
   - Small objects, partial occlusions, edge cases

---

### If Distance Estimation is Inaccurate

**Problem:** Wrong distance calculations

**Solutions:**
1. **Re-calibrate Camera Parameters**
   ```kotlin
   // In YoloDetector.kt or distance calculation
   private val CAMERA_FOV_VERTICAL = 50f  // Adjust based on your device
   private val CAMERA_HEIGHT_M = 1.5f     // Adjust based on how user holds phone
   ```

2. **Per-Class Size Calibration**
   ```kotlin
   // Measure real-world sizes more accurately
   val realWorldSizes = mapOf(
       "person" to 1.7f,  // Average adult height
       "car" to 4.5f      // Average car length
   )
   ```

3. **Collect Real-World Measurements**
   - Take photos of objects at known distances
   - Measure actual distances with tape measure
   - Compare predictions vs reality
   - Adjust calibration factors

4. **Context-Aware Estimation**
   - Use multiple cues (object size, ground plane, perspective)
   - Consider object position in frame

---

### If Under-estimation Rate is High (>15%)

**Problem:** DANGEROUS - saying objects are farther than they are

**Solutions:**
1. **Add Safety Margin**
   ```kotlin
   // Multiply distance by 0.8 to be conservative
   val adjustedDistance = rawDistance * 0.8f  
   ```

2. **Separate Confidence Levels**
   ```kotlin
   if (distanceUncertainty > 0.3f) {
       // Assume closer distance when uncertain
       return estimatedDistance * 0.7f  
   }
   ```

3. **Better Calibration for Close Objects**
   - Focus on accuracy in 0-5m range
   - Use different formula for near vs far objects

---

## 🎯 Target Performance Benchmarks

### Minimum Acceptable Performance

For IRIS to be safe for real-world use:

| Metric | Target | Critical? |
|--------|--------|-----------|
| **Detection Recall** | >0.90 | ✅ YES - Must catch 90%+ of objects |
| **Detection Precision** | >0.80 | ⚠️ Important - Limit false alarms |
| **F1 Score** | >0.85 | ⚠️ Important - Overall quality |
| **Person Recall** | >0.95 | ✅ CRITICAL - Can't miss people |
| **Vehicle Recall** | >0.90 | ✅ CRITICAL - Can't miss cars |
| **Distance MAE (0-2m)** | <0.4m | ✅ CRITICAL - Critical zone |
| **Distance MAE (2-5m)** | <0.7m | ⚠️ Important |
| **Under-estimation Rate** | <10% | ✅ CRITICAL - Safety issue |
| **False Positive Rate** | <15% | ⚠️ Important - User trust |

### Excellent Performance

What we should aim for:

| Metric | Target |
|--------|--------|
| Detection Recall | >0.95 |
| Detection Precision | >0.90 |
| Person Recall | >0.98 |
| Distance MAE (Critical Zone) | <0.3m |
| Under-estimation Rate | <5% |
| False Positive Rate | <8% |

---

## 📝 Evaluation Checklist

Before declaring model "production ready":

- [ ] Run evaluation on 100+ COCO images
- [ ] Overall F1 Score >0.85
- [ ] Person detection recall >0.95
- [ ] Vehicle detection recall >0.90
- [ ] False negative rate <10%
- [ ] Distance MAE in critical zone <0.4m
- [ ] Under-estimation rate <10%
- [ ] Reviewed all false negatives (understand why we missed objects)
- [ ] Reviewed all false positives (understand phantom detection patterns)
- [ ] Tested on edge cases (low light, occlusions, motion blur)
- [ ] Documented limitations and unsafe scenarios

---

## 🔄 Iterative Improvement Process

1. **Run Evaluation** → Get metrics
2. **Analyze Failures** → Study false positives/negatives
3. **Identify Patterns** → What causes errors?
4. **Make Improvements** → Adjust thresholds, retrain, etc.
5. **Re-evaluate** → Did performance improve?
6. **Repeat** until targets met

---

## 📚 References

- COCO Dataset: https://cocodataset.org/
- YOLO Papers: YOLOv3, YOLOv5, YOLOv8
- Object Detection Metrics: https://github.com/rafaelpadilla/Object-Detection-Metrics
- Model Evaluation Best Practices: https://developers.google.com/machine-learning/guides/model-evaluation

---

## 🤝 Contributing to Evaluation

To improve this evaluation framework:

1. Add more test datasets (custom pothole/obstacle data)
2. Implement real-world distance measurement validation
3. Add video-based evaluation (temporal consistency)
4. Create visual analysis tools (overlay predictions on images)
5. Add TIER 3 & 4 evaluations (system-level, scenario-based)

---

**Last Updated:** December 11, 2025  
**Version:** 1.0  
**Status:** Active Development
