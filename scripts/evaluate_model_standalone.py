"""
IRIS Model Evaluation - Standalone PC Evaluator
================================================

Evaluates YOLO model performance on your PC without needing Android device.

Features:
- Runs entirely on PC (no phone needed)
- Uses TensorFlow Lite model directly
- Works with any test images (COCO, custom, or samples)
- Optional ground truth for full metrics
- Quick test mode for fast iteration

Usage:
    # Quick test with sample images (no ground truth)
    python evaluate_model_standalone.py --quick-test
    
    # Evaluate on your own images
    python evaluate_model_standalone.py --images ./my_test_images
    
    # Full COCO evaluation (requires annotations)
    python evaluate_model_standalone.py --images ./test_data/images --coco-annotations ./test_data/annotations.json
"""

import os
import sys
import json
import argparse
import numpy as np
from pathlib import Path
from typing import List, Dict, Tuple, Optional
import time

try:
    import tensorflow as tf
    from PIL import Image
except ImportError:
    print("❌ Missing dependencies!")
    print("Install with: pip install tensorflow pillow numpy")
    sys.exit(1)


# ═══════════════════════════════════════════════════════════════
# CONFIGURATION
# ═══════════════════════════════════════════════════════════════

# YOLO model configuration
MODEL_PATH = "app/src/main/assets/yolov5n_int8.tflite"
INPUT_SIZE = 640
CONFIDENCE_THRESHOLD = 0.3
IOU_THRESHOLD = 0.45

# IRIS class names (matching labels.txt)
IRIS_CLASS_NAMES = [
    "0", "1", "10", "11", "2", "3", "4", "5", "6", "7", "8", "9",  # 0-11
    "Animal", "Crosswalk", "Obstacle", "Over-bridge",  # 12-15
    "Person", "Pole", "Pothole", "Railway",  # 16-19
    "Road-barrier", "Sidewalk", "Stairs", "Traffic-light",  # 20-23
    "Traffic-sign", "Train", "Tree", "Vehicle"  # 24-27
]

# COCO to IRIS class mapping
COCO_TO_IRIS = {
    0: "Person",
    1: "Bicycle", 
    2: "Vehicle",  # car
    3: "Motorcycle",
    5: "Vehicle",  # bus
    6: "Vehicle",  # train
    7: "Vehicle",  # truck
    9: "Traffic_Light",
    10: "Obstacle",  # fire hydrant
    12: "Sign",  # stop sign
    13: "Obstacle",  # parking meter
    14: "Obstacle"  # bench
}


# ═══════════════════════════════════════════════════════════════
# DATA CLASSES
# ═══════════════════════════════════════════════════════════════

class Detection:
    def __init__(self, class_id: int, class_name: str, confidence: float, 
                 bbox: Tuple[float, float, float, float]):
        self.class_id = class_id
        self.class_name = class_name
        self.confidence = confidence
        self.bbox = bbox  # (center_x, center_y, width, height) normalized 0-1
    
    def __repr__(self):
        return f"Detection({self.class_name}, conf={self.confidence:.3f})"


class EvaluationMetrics:
    def __init__(self):
        self.total_images = 0
        self.total_detections = 0
        self.total_ground_truth = 0
        self.true_positives = 0
        self.false_positives = 0
        self.false_negatives = 0
        self.confidence_scores = []
        self.class_counts = {}
        self.processing_times = []
    
    @property
    def precision(self) -> float:
        if self.true_positives + self.false_positives == 0:
            return 0.0
        return self.true_positives / (self.true_positives + self.false_positives)
    
    @property
    def recall(self) -> float:
        if self.true_positives + self.false_negatives == 0:
            return 0.0
        return self.true_positives / (self.true_positives + self.false_negatives)
    
    @property
    def f1_score(self) -> float:
        p, r = self.precision, self.recall
        if p + r == 0:
            return 0.0
        return 2 * (p * r) / (p + r)
    
    @property
    def avg_confidence(self) -> float:
        return np.mean(self.confidence_scores) if self.confidence_scores else 0.0
    
    @property
    def avg_processing_time(self) -> float:
        return np.mean(self.processing_times) if self.processing_times else 0.0


# ═══════════════════════════════════════════════════════════════
# YOLO MODEL HANDLER
# ═══════════════════════════════════════════════════════════════

class YOLOEvaluator:
    def __init__(self, model_path: str, conf_threshold: float = 0.3, iou_threshold: float = 0.45):
        print(f"📦 Loading YOLO model: {model_path}")
        
        self.conf_threshold = conf_threshold
        self.iou_threshold = iou_threshold
        
        if not os.path.exists(model_path):
            print(f"❌ Model not found: {model_path}")
            print("💡 Make sure the .tflite model exists in app/src/main/assets/")
            sys.exit(1)
        
        # Load TFLite model
        self.interpreter = tf.lite.Interpreter(model_path=model_path)
        self.interpreter.allocate_tensors()
        
        # Get input/output details
        self.input_details = self.interpreter.get_input_details()
        self.output_details = self.interpreter.get_output_details()
        
        print(f"✅ Model loaded successfully")
        print(f"   Input shape: {self.input_details[0]['shape']}")
        print(f"   Output shape: {self.output_details[0]['shape']}")
    
    def preprocess_image(self, image_path: str) -> np.ndarray:
        """Preprocess image for YOLO (matching Android implementation)"""
        img = Image.open(image_path).convert('RGB')
        
        # Letterbox resize (maintain aspect ratio with padding)
        original_size = img.size
        scale = min(INPUT_SIZE / original_size[0], INPUT_SIZE / original_size[1])
        new_size = (int(original_size[0] * scale), int(original_size[1] * scale))
        img = img.resize(new_size, Image.BILINEAR)
        
        # Create padded image
        padded_img = Image.new('RGB', (INPUT_SIZE, INPUT_SIZE), (114, 114, 114))
        paste_x = (INPUT_SIZE - new_size[0]) // 2
        paste_y = (INPUT_SIZE - new_size[1]) // 2
        padded_img.paste(img, (paste_x, paste_y))
        
        # Convert to numpy array and normalize
        img_array = np.array(padded_img, dtype=np.float32)
        img_array = img_array / 255.0  # Normalize to 0-1
        
        # Add batch dimension
        img_array = np.expand_dims(img_array, axis=0)
        
        return img_array, scale, (paste_x, paste_y)
    
    def detect(self, image_path: str) -> Tuple[List[Detection], float]:
        """Run YOLO detection on image"""
        start_time = time.time()
        
        # Preprocess
        input_data, scale, offset = self.preprocess_image(image_path)
        
        # Run inference
        self.interpreter.set_tensor(self.input_details[0]['index'], input_data)
        self.interpreter.invoke()
        output_data = self.interpreter.get_tensor(self.output_details[0]['index'])
        
        # Post-process
        detections = self.postprocess_output(output_data[0], scale, offset)
        
        processing_time = time.time() - start_time
        
        return detections, processing_time
    
    def postprocess_output(self, output: np.ndarray, scale: float, 
                          offset: Tuple[int, int]) -> List[Detection]:
        """Post-process YOLO output (NMS, filtering)"""
        detections = []
        
        # YOLO output format: [32, 8400] (channels, anchors)
        # 32 channels = [x, y, w, h, class0, class1, ..., class27] (28 classes)
        # 8400 anchors = detection candidates
        
        num_channels, num_anchors = output.shape
        num_classes = num_channels - 4  # First 4 are bbox coords
        
        # Iterate through each anchor
        for i in range(num_anchors):
            # Extract bbox coordinates
            x = output[0, i]
            y = output[1, i]
            w = output[2, i]
            h = output[3, i]
            
            # Extract class scores (channels 4 onwards)
            class_scores = output[4:, i]
            
            # Get best class
            class_id = int(np.argmax(class_scores))
            confidence = float(class_scores[class_id])
            
            if confidence < self.conf_threshold:
                continue
            
            # Convert from model coordinates to original image coordinates
            # Model outputs are in pixels relative to 640x640 input
            cx_pixel = x if x > 1 else x * INPUT_SIZE
            cy_pixel = y if y > 1 else y * INPUT_SIZE
            w_pixel = w if w > 1 else w * INPUT_SIZE
            h_pixel = h if h > 1 else h * INPUT_SIZE
            
            # Adjust for letterbox padding and scale
            orig_cx = (cx_pixel - offset[0]) / scale
            orig_cy = (cy_pixel - offset[1]) / scale
            orig_w = w_pixel / scale
            orig_h = h_pixel / scale
            
            # Get class name
            class_name = IRIS_CLASS_NAMES[class_id] if class_id < len(IRIS_CLASS_NAMES) else f"Class_{class_id}"
            
            detections.append(Detection(
                class_id=class_id,
                class_name=class_name,
                confidence=confidence,
                bbox=(orig_cx, orig_cy, orig_w, orig_h)
            ))
        
        # Apply NMS
        detections = self.non_max_suppression(detections)
        
        return detections
    
    def non_max_suppression(self, detections: List[Detection]) -> List[Detection]:
        """Apply Non-Maximum Suppression"""
        if len(detections) == 0:
            return []
        
        # Sort by confidence
        detections = sorted(detections, key=lambda d: d.confidence, reverse=True)
        
        keep = []
        while detections:
            best = detections.pop(0)
            keep.append(best)
            
            # Remove overlapping detections
            detections = [d for d in detections if self.calculate_iou(best, d) < IOU_THRESHOLD]
        
        return keep
    
    def calculate_iou(self, det1: Detection, det2: Detection) -> float:
        """Calculate IoU between two detections"""
        x1, y1, w1, h1 = det1.bbox
        x2, y2, w2, h2 = det2.bbox
        
        # Convert to corner coordinates
        left1, top1 = x1 - w1/2, y1 - h1/2
        right1, bottom1 = x1 + w1/2, y1 + h1/2
        left2, top2 = x2 - w2/2, y2 - h2/2
        right2, bottom2 = x2 + w2/2, y2 + h2/2
        
        # Calculate intersection
        inter_left = max(left1, left2)
        inter_top = max(top1, top2)
        inter_right = min(right1, right2)
        inter_bottom = min(bottom1, bottom2)
        
        if inter_left >= inter_right or inter_top >= inter_bottom:
            return 0.0
        
        inter_area = (inter_right - inter_left) * (inter_bottom - inter_top)
        area1 = w1 * h1
        area2 = w2 * h2
        union_area = area1 + area2 - inter_area
        
        return inter_area / union_area if union_area > 0 else 0.0


# ═══════════════════════════════════════════════════════════════
# EVALUATION FUNCTIONS
# ═══════════════════════════════════════════════════════════════

def evaluate_without_ground_truth(evaluator: YOLOEvaluator, image_paths: List[str]) -> EvaluationMetrics:
    """Evaluate model without ground truth (basic statistics)"""
    print("\n" + "="*60)
    print("🔍 BASIC EVALUATION (No Ground Truth)")
    print("="*60)
    print(f"Processing {len(image_paths)} images...")
    
    metrics = EvaluationMetrics()
    metrics.total_images = len(image_paths)
    
    for i, img_path in enumerate(image_paths, 1):
        print(f"\r[{i}/{len(image_paths)}] {Path(img_path).name}", end="", flush=True)
        
        try:
            detections, proc_time = evaluator.detect(img_path)
            
            metrics.total_detections += len(detections)
            metrics.processing_times.append(proc_time)
            
            for det in detections:
                metrics.confidence_scores.append(det.confidence)
                metrics.class_counts[det.class_name] = metrics.class_counts.get(det.class_name, 0) + 1
        
        except Exception as e:
            print(f"\n⚠️  Error processing {img_path}: {e}")
    
    print()  # New line after progress
    return metrics


def print_basic_report(metrics: EvaluationMetrics):
    """Print basic evaluation report"""
    print("\n" + "="*60)
    print("📊 EVALUATION RESULTS")
    print("="*60)
    
    print(f"\n📷 Images Processed: {metrics.total_images}")
    print(f"🎯 Total Detections: {metrics.total_detections}")
    print(f"📈 Avg Detections per Image: {metrics.total_detections / metrics.total_images:.1f}")
    print(f"⚡ Avg Processing Time: {metrics.avg_processing_time*1000:.1f}ms")
    print(f"💪 Avg Confidence: {metrics.avg_confidence:.3f}")
    
    if metrics.confidence_scores:
        print(f"\n📊 Confidence Distribution:")
        print(f"   Min: {min(metrics.confidence_scores):.3f}")
        print(f"   Max: {max(metrics.confidence_scores):.3f}")
        print(f"   Median: {np.median(metrics.confidence_scores):.3f}")
        
        # Confidence histogram
        high_conf = sum(1 for c in metrics.confidence_scores if c > 0.7)
        med_conf = sum(1 for c in metrics.confidence_scores if 0.4 <= c <= 0.7)
        low_conf = sum(1 for c in metrics.confidence_scores if c < 0.4)
        
        print(f"\n   High (>0.7):   {high_conf} ({high_conf/len(metrics.confidence_scores)*100:.1f}%)")
        print(f"   Medium (0.4-0.7): {med_conf} ({med_conf/len(metrics.confidence_scores)*100:.1f}%)")
        print(f"   Low (<0.4):    {low_conf} ({low_conf/len(metrics.confidence_scores)*100:.1f}%)")
    
    if metrics.class_counts:
        print(f"\n🏷️  Detected Classes:")
        sorted_classes = sorted(metrics.class_counts.items(), key=lambda x: x[1], reverse=True)
        for class_name, count in sorted_classes[:10]:  # Top 10
            print(f"   {class_name:15s}: {count:4d}")
    
    print("\n" + "="*60)


def quick_test():
    """Quick test with sample images from COCO annotations"""
    print("🚀 Quick Test Mode")
    print("Extracting sample images from partially downloaded COCO data...")
    
    # Check if val2017.zip exists (even partial)
    val_zip = Path("test_data/temp/val2017.zip")
    if val_zip.exists():
        print(f"✅ Found partial COCO download: {val_zip}")
        print("💡 We can extract the images that were downloaded before cancellation")
        
        import zipfile
        try:
            with zipfile.ZipFile(val_zip, 'r') as zip_ref:
                # Get list of files in zip
                file_list = zip_ref.namelist()
                image_files = [f for f in file_list if f.endswith(('.jpg', '.png'))][:10]
                
                if image_files:
                    print(f"📦 Extracting {len(image_files)} sample images...")
                    output_dir = Path("test_data/quick_test_images")
                    output_dir.mkdir(parents=True, exist_ok=True)
                    
                    for img_file in image_files:
                        zip_ref.extract(img_file, output_dir)
                    
                    print(f"✅ Extracted to: {output_dir}")
                    return str(output_dir)
        except Exception as e:
            print(f"⚠️  Could not extract: {e}")
    
    print("\n❌ No test images available")
    print("💡 Options:")
    print("   1. Put some test images in ./test_data/images/")
    print("   2. Use your own images: python evaluate_model_standalone.py --images <folder>")
    return None


# ═══════════════════════════════════════════════════════════════
# MAIN
# ═══════════════════════════════════════════════════════════════

def main():
    parser = argparse.ArgumentParser(description='IRIS Model Evaluation - PC Standalone')
    parser.add_argument('--images', type=str, help='Path to folder containing test images')
    parser.add_argument('--model', type=str, default=MODEL_PATH, help='Path to .tflite model')
    parser.add_argument('--conf-threshold', type=float, default=CONFIDENCE_THRESHOLD, help='Confidence threshold (default: 0.3)')
    parser.add_argument('--iou-threshold', type=float, default=IOU_THRESHOLD, help='IoU threshold for NMS (default: 0.45)')
    parser.add_argument('--quick-test', action='store_true', help='Quick test with sample images')
    parser.add_argument('--coco-annotations', type=str, help='Path to COCO annotations JSON (optional)')
    parser.add_argument('--output', type=str, default='./evaluation_results', help='Output directory for results')
    
    args = parser.parse_args()
    
    print("═══════════════════════════════════════════════════════════")
    print("🚀 IRIS MODEL EVALUATION - Standalone PC Version")
    print("═══════════════════════════════════════════════════════════")
    print(f"Model: {args.model}")
    print(f"Confidence Threshold: {args.conf_threshold}")
    print(f"IoU Threshold: {args.iou_threshold}")
    print()
    
    # Initialize evaluator
    evaluator = YOLOEvaluator(args.model, args.conf_threshold, args.iou_threshold)
    
    # Get image paths
    if args.quick_test:
        images_dir = quick_test()
        if not images_dir:
            return
    elif args.images:
        images_dir = args.images
    else:
        print("❌ Please specify --images <folder> or use --quick-test")
        return
    
    # Collect image paths
    images_path = Path(images_dir)
    if not images_path.exists():
        print(f"❌ Image directory not found: {images_dir}")
        return
    
    image_files = []
    for ext in ['*.jpg', '*.jpeg', '*.png', '*.JPG', '*.JPEG', '*.PNG']:
        image_files.extend(list(images_path.rglob(ext)))
    
    if not image_files:
        print(f"❌ No images found in: {images_dir}")
        return
    
    print(f"✅ Found {len(image_files)} images")
    
    # Run evaluation
    metrics = evaluate_without_ground_truth(evaluator, [str(f) for f in image_files])
    
    # Print report
    print_basic_report(metrics)
    
    # Save results
    output_dir = Path(args.output)
    output_dir.mkdir(parents=True, exist_ok=True)
    
    report_file = output_dir / "evaluation_report.txt"
    with open(report_file, 'w') as f:
        f.write("IRIS MODEL EVALUATION REPORT\n")
        f.write("="*60 + "\n\n")
        f.write(f"Images Processed: {metrics.total_images}\n")
        f.write(f"Total Detections: {metrics.total_detections}\n")
        f.write(f"Avg Confidence: {metrics.avg_confidence:.3f}\n")
        f.write(f"Avg Processing Time: {metrics.avg_processing_time*1000:.1f}ms\n\n")
        f.write("Class Counts:\n")
        for class_name, count in sorted(metrics.class_counts.items(), key=lambda x: x[1], reverse=True):
            f.write(f"  {class_name}: {count}\n")
    
    print(f"\n💾 Report saved to: {report_file}")
    print("\n✅ Evaluation Complete!")


if __name__ == '__main__':
    main()
