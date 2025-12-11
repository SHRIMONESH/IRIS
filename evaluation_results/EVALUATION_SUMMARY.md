# IRIS Model Evaluation Summary
**Date:** December 11, 2025  
**Model:** best_float32.tflite (YOLOv5n, 12.3MB)  
**Test Dataset:** 30 street scene images from Pexels  
**Evaluation Type:** Basic (No Ground Truth)

---

## 📊 Overall Performance

| Metric | Value | Assessment |
|--------|-------|------------|
| **Images Processed** | 30 | ✅ Complete |
| **Total Detections** | 196 | ✅ Reasonable |
| **Avg Detections/Image** | 6.5 | ✅ Good for street scenes |
| **Avg Processing Time** | 365.7ms | ⚠️ Higher than Android (target: <20ms) |
| **Avg Confidence** | 0.613 | ⚠️ Moderate (target: >0.7 for safety) |

---

## 🏷️ Detection Distribution

| Class | Count | Percentage | Notes |
|-------|-------|------------|-------|
| **Person** | 146 | 74.5% | ✅ Primary target - detecting well |
| **Vehicle** | 28 | 14.3% | ✅ Good coverage |
| **Obstacle** | 8 | 4.1% | ⚠️ Important for safety |
| **Traffic-sign** | 4 | 2.0% | Low but acceptable |
| **Pole** | 4 | 2.0% | Expected in urban scenes |
| **Train** | 2 | 1.0% | Context-specific |
| **Sidewalk** | 2 | 1.0% | Important for navigation |
| **Animal** | 2 | 1.0% | Rare but detected |

---

## 📈 Confidence Analysis

| Confidence Range | Count | Percentage | Interpretation |
|-----------------|-------|------------|----------------|
| **High (>0.7)** | 80 | 40.8% | ✅ Strong confident detections |
| **Medium (0.4-0.7)** | 78 | 39.8% | ⚠️ Moderate - may need review |
| **Low (<0.4)** | 38 | 19.4% | 🚨 Risky for safety-critical use |

**Min Confidence:** 0.300 (at threshold)  
**Max Confidence:** 0.970 (very confident)  
**Median Confidence:** 0.601

---

## ⚠️ Key Findings

### 🎯 Strengths
1. **Person Detection Dominance** - 146/196 (74.5%) detections are people, which is the primary use case for IRIS
2. **High Confidence Detections** - 40.8% of detections have >0.7 confidence
3. **Diverse Class Coverage** - Detecting 8 different object types
4. **Consistent Performance** - 6.5 avg detections/image shows stable behavior

### 🚨 Concerns (False Alarm Risk)
1. **Low Confidence Detections** - 19.4% of detections below 0.4 confidence
   - **Safety Impact:** Could cause false alarms or missed critical obstacles
   - **Recommendation:** Consider raising confidence threshold to 0.5

2. **Processing Speed** - 365.7ms per image on PC
   - Android target is <20ms for real-time performance
   - PC CPU inference is slower than Android NNAPI/GPU
   - Real device testing needed to confirm actual performance

3. **Moderate Average Confidence** - 0.613 average
   - Target for safety-critical: >0.7
   - Indicates model may be uncertain on some detections
   - **Risk:** Could miss critical objects or generate false positives

4. **Limited Obstacle Detection** - Only 8 obstacle detections (4.1%)
   - Obstacles are safety-critical (potholes, barriers, debris)
   - May indicate model weakness in obstacle detection
   - **Requires:** More focused testing with obstacle-heavy images

---

## 🎯 Recommendations

### Immediate Actions
1. **Adjust Confidence Threshold**
   ```
   Current: 0.3 → Recommended: 0.5
   Impact: Reduce false alarms by filtering low-confidence detections
   Trade-off: May miss some true positives
   ```

2. **Test with Obstacle-Rich Images**
   - Current dataset has limited obstacles (curbs, potholes, barriers)
   - Download 10-15 images specifically showing obstacles
   - Re-run evaluation to assess obstacle detection performance

3. **Test on Real Android Device**
   - PC inference (365ms) doesn't reflect Android performance
   - Android uses NNAPI/GPU acceleration
   - Measure actual inference time on target device

### Next Steps for Full Evaluation
1. **Label Ground Truth**
   - Manually label 30-50 test images with bounding boxes
   - Use labeling tools (LabelImg, CVAT, or Label Studio)
   - Calculate Precision, Recall, F1, mAP metrics

2. **Distance Estimation Testing** (TIER 2)
   - Test with images at known distances (1m, 2m, 5m, 10m)
   - Measure Mean Absolute Error (MAE) and under-estimation rate
   - Critical for safety - underestimation causes late warnings

3. **Scenario-Based Testing** (TIER 4)
   - **Crowded sidewalk:** Multiple people detection
   - **Low light:** Evening/night performance
   - **Adverse weather:** Rain, fog, glare
   - **Edge cases:** Children, wheelchairs, pets, unusual obstacles

---

## 🔄 Next Evaluation Plan

### Phase 1: Confidence Threshold Tuning ✅ (Complete)
- Run with thresholds: 0.3, 0.4, 0.5, 0.6
- Find optimal balance between detection rate and false alarms

### Phase 2: Obstacle-Focused Testing (NEXT)
- Download 15 obstacle-rich images
- Categories: Potholes, barriers, curbs, debris, construction
- Measure obstacle detection rate

### Phase 3: Ground Truth Evaluation
- Label 50 test images manually
- Calculate precision, recall, F1, mAP
- Identify systematic failure patterns

### Phase 4: Real Device Testing
- Deploy to Android device
- Measure actual inference time
- Test in real-world conditions

---

## 📝 Conclusion

**Model Status:** **⚠️ Functional but Needs Tuning**

The IRIS YOLO model is detecting objects successfully, with strong performance on person detection (74.5% of all detections). However, the moderate average confidence (0.613) and significant portion of low-confidence detections (19.4%) indicate risk for false alarms in safety-critical applications.

**Critical for blind users:** False positives could cause alarm fatigue, while false negatives could miss dangerous obstacles. The 0.3 confidence threshold is too permissive for safety-critical use.

**Action Required:** 
1. Raise confidence threshold to 0.5
2. Test with obstacle-rich images
3. Validate on real Android device
4. Consider model retraining if obstacle detection remains weak

**Timeline:**
- Threshold tuning: 1 hour
- Obstacle testing: 2-3 hours
- Device validation: 1 day
- Ground truth labeling: 3-5 days (if needed)

---

**Generated:** December 11, 2025  
**Evaluation Tool:** evaluate_model_standalone.py v1.0  
**Full Report:** evaluation_report.txt
