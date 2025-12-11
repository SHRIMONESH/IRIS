# 🚀 Quick Start: Running IRIS Model Evaluation (PC-Only)

> **✅ Runs entirely on your Windows PC - NO PHONE REQUIRED!**

## Prerequisites

- Python 3.7+ installed
- TensorFlow Lite (`pip install tensorflow`)
- ~100MB free disk space (for sample images)

## Three Ways to Evaluate

### 🎯 Option 1: Quick Test (Recommended - Start Here!)

**Uses whatever test images you have, no ground truth needed**

```powershell
# Navigate to project directory
cd D:\IRIS

# Install Python dependencies
pip install tensorflow pillow numpy

# Quick test with any images in test_data folder
python scripts/evaluate_model_standalone.py --quick-test
```

**What this does:**
- ✅ Uses images from partially downloaded COCO (if you canceled download)
- ✅ Or uses any images you put in `test_data/images/`
- ✅ Runs YOLO inference on PC
- ✅ Shows detection statistics, confidence distribution, class counts
- ✅ NO ground truth needed - just tests if model works

---

### 📸 Option 2: Use Your Own Images (Best for Real-World Testing)

**Test with sidewalk photos, street scenes, etc.**

```powershell
# 1. Create a folder with your test images
mkdir my_test_images

# 2. Add photos (from your phone camera, internet, etc.)
#    - Sidewalks with people
#    - Streets with cars
#    - Obstacles (poles, benches)
#    - Any real-world scenarios

# 3. Run evaluation
python scripts/evaluate_model_standalone.py --images ./my_test_images
```

**Why this is better:**
- ✅ Tests real IRIS use cases
- ✅ No large downloads
- ✅ Immediate insights
- ✅ See what model actually detects in practice

---

### 📊 Option 3: Full COCO Evaluation (Optional - For Benchmarking)

**Only if you want industry-standard metrics**

⚠️ **Skip this for now** - requires 1GB download. Start with Options 1 or 2 first!

### 📊 Option 3: Full COCO Evaluation (Optional - For Benchmarking)

**Only if you want industry-standard metrics**

⚠️ **Skip this for now** - requires 1GB download. Start with Options 1 or 2 first!

---

## 📈 Understanding the Output

### Example Output

```
═══════════════════════════════════════════════════════════
🚀 IRIS MODEL EVALUATION - Standalone PC Version
═══════════════════════════════════════════════════════════
Model: app/src/main/assets/yolov5n_int8.tflite
Confidence Threshold: 0.3
IoU Threshold: 0.45

📦 Loading YOLO model: app/src/main/assets/yolov5n_int8.tflite
✅ Model loaded successfully
   Input shape: [1, 640, 640, 3]
   Output shape: [1, 25200, 85]

✅ Found 15 images

═══════════════════════════════════════════════════════════
🔍 BASIC EVALUATION (No Ground Truth)
═══════════════════════════════════════════════════════════
Processing 15 images...
[15/15] image_015.jpg

═══════════════════════════════════════════════════════════
📊 EVALUATION RESULTS
═══════════════════════════════════════════════════════════

📷 Images Processed: 15
🎯 Total Detections: 87
📈 Avg Detections per Image: 5.8
⚡ Avg Processing Time: 245.3ms
💪 Avg Confidence: 0.678

📊 Confidence Distribution:
   Min: 0.312
   Max: 0.945
   Median: 0.689

   High (>0.7):   45 (51.7%)
   Medium (0.4-0.7): 35 (40.2%)
   Low (<0.4):    7 (8.0%)

🏷️  Detected Classes:
   Person          : 32
   Vehicle         : 28
   Obstacle        : 12
   Traffic_Light   : 8
   Bicycle         : 5
   Sign            : 2

═══════════════════════════════════════════════════════════

💾 Report saved to: ./evaluation_results/evaluation_report.txt
✅ Evaluation Complete!
```

---

## 🔍 What This Tells You

### Good Signs ✅
- **High average confidence (>0.6)**: Model is fairly certain about detections
- **Most detections are high confidence**: Few false alarms
- **Reasonable detections per image (3-10)**: Not hallucinating too many objects
- **Fast processing (<300ms)**: Real-time capable

### Red Flags ❌
- **Low average confidence (<0.4)**: Model is guessing, many false alarms
- **Too many detections (>20 per image)**: Probably hallucinating
- **Many low confidence detections**: Increase threshold to reduce noise
- **Slow processing (>500ms)**: May not work real-time on device

### What You Learn
1. **Which objects are detected most**: People? Cars? Obstacles?
2. **Confidence distribution**: Are detections reliable?
3. **Processing speed**: Can it run real-time?
4. **Potential issues**: Too many/few detections, wrong classes

---

## 🛠️ Next Steps Based on Results

### If Average Confidence is Low (<0.5)
**Problem:** Model making uncertain predictions

**Solution:**
```python
# In evaluate_model_standalone.py, increase threshold:
CONFIDENCE_THRESHOLD = 0.5  # Instead of 0.3
```

Re-run evaluation to see if quality improves.

---

### If Too Many Detections (>15 per image)
**Problem:** Model hallucinating objects (false positives)

**Solutions:**
1. Increase confidence threshold (0.5 or 0.6)
2. Adjust IoU threshold for stricter NMS
3. Review what's being falsely detected

---

### If Too Few Detections (<2 per image)
**Problem:** Model missing objects (false negatives)

**Solutions:**
1. Lower confidence threshold (0.25 or 0.2)
2. Check if test images match training data
3. Model may need retraining

---

### If Wrong Classes Detected
**Problem:** Detecting "Vehicle" when it should be "Person"

**Check:**
1. Is class mapping correct? (`IRIS_CLASS_NAMES` in script)
2. Does model match your YoloDetector.kt classes?
3. May need model retraining

---

## 📁 Output Files

After evaluation, check these files:

```
evaluation_results/
├── evaluation_report.txt    # Text summary
└── (more files with full COCO evaluation)
```

View report:
```powershell
cat evaluation_results/evaluation_report.txt
```

---

## 🐛 Troubleshooting

### "Model not found"
```
❌ Model not found: app/src/main/assets/yolov5n_int8.tflite
```

**Fix:** Check model file exists
```powershell
ls app/src/main/assets/*.tflite
```

---

### "No images found"
```
❌ No images found in: ./test_data/images
```

**Fix:** Add images to folder
```powershell
# Option 1: Use your own images
cp C:\Users\<you>\Pictures\*.jpg test_data\images\

# Option 2: Download sample images
# (Google "street scene images" or "sidewalk photos")
```

---

### "Missing dependencies"
```
❌ Missing dependencies!
Install with: pip install tensorflow pillow numpy
```

**Fix:** Install packages
```powershell
pip install tensorflow pillow numpy
```

---

### "Out of memory"
**Problem:** TensorFlow using too much RAM

**Fix:** Process fewer images at once
```powershell
# Only evaluate 5 images
python scripts/evaluate_model_standalone.py --images ./test_data/images --max-images 5
```

---

## 🎯 Recommended Workflow

**Day 1:** Quick sanity check
```powershell
# Just test if model runs
python scripts/evaluate_model_standalone.py --quick-test
```

**Day 2:** Real-world testing  
```powershell
# Add 10-20 photos of real scenarios
# Sidewalks, streets, obstacles
python scripts/evaluate_model_standalone.py --images ./my_test_images
```

**Day 3:** Adjust based on results
- If false alarms: increase threshold
- If missing objects: lower threshold  
- If wrong classes: check class mapping

**Day 4:** (Optional) Full COCO evaluation for benchmarking

---

## 💡 Pro Tips

1. **Start Small**: Test with 5-10 images first, then scale up
2. **Use Real Scenarios**: Photos from actual use cases > generic datasets
3. **Iterate Fast**: Run evaluation, adjust threshold, re-run
4. **Visual Inspection**: Look at actual detections, not just metrics
5. **Compare Thresholds**: Try 0.3, 0.5, 0.7 to see trade-offs

---

## 📚 See Also

- [EVALUATION_GUIDE.md](EVALUATION_GUIDE.md) - Complete framework and metrics explanation
- [DEVELOPER_GUIDE.md](DEVELOPER_GUIDE.md) - Understanding the codebase
- [README.md](README.md) - Project overview

---

**Last Updated:** December 11, 2025  
**Version:** 2.0 (PC Standalone)  
**Status:** Ready to Use ✅
