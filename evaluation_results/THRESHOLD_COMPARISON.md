# Confidence Threshold Comparison

## Overview
Tested IRIS YOLO model with two confidence thresholds to assess impact on detection quality and false alarm risk.

---

## Results Comparison

| Metric | Threshold 0.3 | Threshold 0.5 | Change |
|--------|---------------|---------------|---------|
| **Total Detections** | 196 | 126 | -35.7% |
| **Avg Detections/Image** | 6.5 | 4.2 | -35.4% |
| **Avg Confidence** | 0.613 | 0.741 | +20.9% ✅ |
| **Processing Time** | 365.7ms | 607.6ms | +66.1% ⚠️ |
| | | | |
| **High Confidence (>0.7)** | 80 (40.8%) | 80 (63.5%) | +22.7% ✅ |
| **Medium (0.4-0.7)** | 78 (39.8%) | 46 (36.5%) | -3.3% |
| **Low (<0.4)** | 38 (19.4%) | 0 (0.0%) | **-19.4% ✅** |

---

## Class Distribution Changes

| Class | Threshold 0.3 | Threshold 0.5 | Change |
|-------|---------------|---------------|---------|
| **Person** | 146 | 108 | -26.0% |
| **Vehicle** | 28 | 10 | -64.3% |
| **Obstacle** | 8 | 2 | -75.0% ⚠️ |
| **Traffic-sign** | 4 | 4 | 0% |
| **Pole** | 4 | 2 | -50.0% |
| **Train** | 2 | 0 | -100% |
| **Sidewalk** | 2 | 0 | -100% |
| **Animal** | 2 | 0 | -100% |

---

## Analysis

### ✅ Benefits of Higher Threshold (0.5)

1. **Eliminated Low Confidence Detections**
   - 0% detections below 0.4 confidence (was 19.4%)
   - Directly addresses false alarm concern

2. **Higher Average Confidence**
   - Improved from 0.613 to 0.741 (+20.9%)
   - Now approaching safety-critical target of >0.7

3. **More High-Confidence Detections**
   - 63.5% of detections have >0.7 confidence (was 40.8%)
   - Indicates more reliable detections

### ⚠️ Concerns with Higher Threshold

1. **Lost 35% of Detections**
   - 196 → 126 detections (-70 detections)
   - May miss some true obstacles

2. **Obstacle Detection Drop**
   - 8 → 2 obstacles detected (-75%)
   - **Critical safety issue:** Missing obstacles is dangerous

3. **Lost Rare Classes**
   - Train, Sidewalk, Animal: 100% reduction
   - May not matter for typical use cases

4. **Processing Time Increase**
   - 365.7ms → 607.6ms (+66%)
   - Possibly due to memory layout differences (needs investigation)

---

## Recommendations

### 🎯 Optimal Strategy: **Threshold 0.4**

Test intermediate threshold of **0.4** to balance:
- Reduce false alarms (eliminate very low confidence)
- Maintain obstacle detection (critical for safety)
- Keep reasonable detection coverage

### Class-Specific Thresholds (Advanced)

Consider different thresholds per class:
```
Person:       0.5  (most common, can afford to be strict)
Vehicle:      0.5  (clearly visible, high confidence expected)
Obstacle:     0.3  (safety-critical, cannot miss)
Traffic-sign: 0.4  (important but visible)
```

### Testing Priorities

1. **Test with threshold 0.4** - Balance point
2. **Collect obstacle-rich images** - Verify obstacle detection
3. **Real device testing** - Validate processing time
4. **Manual review** - Check missed detections at 0.5

---

## Conclusion

**Threshold 0.5 improves confidence but loses critical detections.**

The 75% drop in obstacle detection (8 → 2) is **unacceptable for blind navigation**. Missing potholes, barriers, or curbs could cause injury.

**Recommended Action:**
- Use **threshold 0.4** as compromise
- Test on Android device for realistic performance
- Consider class-specific thresholds
- Add more obstacle images to test dataset

**Safety First:** Better to have some false alarms than miss a dangerous obstacle.

---

Generated: December 11, 2025  
Test Dataset: 30 Pexels street scene images
