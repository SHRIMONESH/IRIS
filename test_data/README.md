# Test Data Directory

This directory contains test images and annotations for evaluating the IRIS YOLO model.

## Directory Structure

```
test_data/
├── images/          # Test images (not tracked in git)
└── README.md        # This file
```

## Getting Test Images

### Option 1: Manual Download (Recommended)

1. Visit free image sites:
   - **Pexels:** https://www.pexels.com/
   - **Unsplash:** https://unsplash.com/
   - **Pixabay:** https://www.pixabay.com/

2. Search for:
   - "street scene"
   - "sidewalk pedestrians"
   - "urban walking"
   - "city street"

3. Download 10-30 images showing:
   - People walking
   - Vehicles (cars, bikes, motorcycles)
   - Obstacles (potholes, barriers, curbs)
   - Street furniture (poles, signs, benches)

4. Save to `test_data/images/`

### Option 2: Use Your Own Photos

1. Take photos with your phone camera
2. Capture typical navigation scenarios
3. Transfer to PC
4. Copy to `test_data/images/`

### Option 3: Use Helper Script

```bash
# Run informational script
python scripts/download_test_images.py
```

This will create the directory structure and provide guidance.

## Running Evaluation

Once you have images in `test_data/images/`, run:

```bash
# Basic evaluation (no ground truth needed)
python scripts/evaluate_model_standalone.py --images test_data/images

# With different confidence threshold
python scripts/evaluate_model_standalone.py --images test_data/images --conf-threshold 0.5

# With COCO annotations (advanced)
python scripts/evaluate_model_standalone.py --images test_data/images --coco-annotations annotations.json
```

## Results Location

Evaluation results are saved to `evaluation_results/`:
- `EVALUATION_SUMMARY.md` - Comprehensive analysis
- `THRESHOLD_COMPARISON.md` - Threshold comparison
- `evaluation_report.txt` - Raw statistics (not tracked)

## Git Tracking

- ✅ **Tracked:** This README
- ❌ **Not tracked:** 
  - `test_data/images/` (too large, user-specific)
  - `test_data/temp/` (temporary files)

See `.gitignore` for complete list.
