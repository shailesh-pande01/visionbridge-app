import os
import glob
import random
import json
import cv2
import numpy as np
import onnxruntime as ort

SEED = 42
random.seed(SEED)
np.random.seed(SEED)

PHOTOS_DIR = "ocr-training/data/real_photos"
DATA_DIR = "ocr-training/data"
TRAIN_CROPS_DIR = os.path.join(DATA_DIR, "real_train")
VAL_CROPS_DIR = os.path.join(DATA_DIR, "real_val")
os.makedirs(TRAIN_CROPS_DIR, exist_ok=True)
os.makedirs(VAL_CROPS_DIR, exist_ok=True)

DICT_PATH = "ocr-training/PaddleOCR/ppocr/utils/dict/ppocrv5_en_dict.txt"
with open(DICT_PATH, "r", encoding="utf-8") as f:
    valid_chars = set([line.strip("\r\n") for line in f if line.strip("\r\n")])
valid_chars.add(" ")

with open("app/src/main/assets/ocr/en_rec_dict.json", "r", encoding="utf-8") as f:
    rec_dict = json.load(f)
rec_labels = ["blank"] + rec_dict + [" "]

det_sess = ort.InferenceSession("ocr-training/models/det_onnx/inference.onnx")
rec_sess = ort.InferenceSession("ocr-training/models/rec_onnx/inference.onnx")

photos = sorted(glob.glob(os.path.join(PHOTOS_DIR, "*.jpg")))
random.shuffle(photos)

# 70 / 30 split
num_train = int(len(photos) * 0.70)
train_photos = photos[:num_train]
val_photos = photos[num_train:]

print(f"Total photos: {len(photos)}. Train photos: {len(train_photos)}, Val photos: {len(val_photos)}")

def detect_and_recognize(img_path):
    img = cv2.imread(img_path)
    if img is None:
        return []
    orig_h, orig_w = img.shape[:2]

    # Detector prep
    max_side = max(orig_h, orig_w)
    ratio = 960.0 / max_side if max_side > 960 else 1.0
    tw = max(32, int(round(orig_w * ratio / 32.0) * 32))
    th = max(32, int(round(orig_h * ratio / 32.0) * 32))
    resized = cv2.resize(img, (tw, th))

    mean = np.array([0.485, 0.456, 0.406], dtype=np.float32)
    std = np.array([0.229, 0.224, 0.225], dtype=np.float32)
    blob = ((resized[:, :, ::-1] / 255.0 - mean) / std).transpose(2, 0, 1).astype(np.float32)[None, ...]

    det_out = det_sess.run(None, {"x": blob})[0][0, 0]
    mask = (det_out > 0.3).astype(np.uint8)
    contours, _ = cv2.findContours(mask, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)

    crops_with_text = []
    scale_x = orig_w / float(tw)
    scale_y = orig_h / float(th)

    for c in contours:
        rect = cv2.minAreaRect(c)
        rw, rh = rect[1]
        if min(rw, rh) < 3.0:
            continue

        box = cv2.boxPoints(rect)
        # Unclip expansion
        area = rw * rh
        perimeter = 2.0 * (rw + rh)
        if perimeter < 1e-4:
            continue
        dist = (area * 2.0) / perimeter

        center = np.mean(box, axis=0)
        unclip_box = []
        for p in box:
            v = p - center
            v_len = np.linalg.norm(v)
            unclip_box.append(p if v_len < 1e-4 else p + (v / v_len) * dist)
        unclip_box = np.array(unclip_box, dtype=np.float32)

        # Scale back to original
        unclip_box[:, 0] = np.clip(unclip_box[:, 0] * scale_x, 0, orig_w - 1)
        unclip_box[:, 1] = np.clip(unclip_box[:, 1] * scale_y, 0, orig_h - 1)

        # Order points: tl, tr, br, bl
        pts = sorted(unclip_box, key=lambda p: p[1])
        top = sorted(pts[:2], key=lambda p: p[0])
        bottom = sorted(pts[2:], key=lambda p: p[0])
        tl, tr = top
        bl, br = bottom

        cw = int(round(max(np.linalg.norm(tr - tl), np.linalg.norm(br - bl))))
        ch = int(round(max(np.linalg.norm(bl - tl), np.linalg.norm(br - tr))))
        if cw < 4 or ch < 4:
            continue

        src_pts = np.array([tl, tr, br, bl], dtype=np.float32)
        dst_pts = np.array([[0, 0], [cw, 0], [cw, ch], [0, ch]], dtype=np.float32)
        M = cv2.getPerspectiveTransform(src_pts, dst_pts)
        crop_img = cv2.warpPerspective(img, M, (cw, ch))

        if ch >= 1.5 * cw:
            crop_img = cv2.rotate(crop_img, cv2.ROTATE_90_COUNTERCLOCKWISE)
            ch, cw = crop_img.shape[:2]

        # Recognizer prep
        rec_h = 48
        rec_w = max(32, int(round(cw * (48.0 / ch))))
        rec_resized = cv2.resize(crop_img, (rec_w, rec_h))
        rec_blob = ((rec_resized[:, :, ::-1] / 255.0 - 0.5) / 0.5).transpose(2, 0, 1).astype(np.float32)[None, ...]

        rec_out = rec_sess.run(None, {"x": rec_blob})[0]
        time_steps = rec_out.shape[1]
        probs = rec_out[0]

        argmaxes = np.argmax(probs, axis=-1)
        max_vals = np.max(probs, axis=-1)

        chars = []
        scores = []
        last_idx = -1
        for t in range(time_steps):
            idx = argmaxes[t]
            val = max_vals[t]
            if idx != 0 and idx != last_idx:
                chars.append(rec_labels[idx])
                scores.append(val)
            last_idx = idx

        text = "".join(chars).strip()
        score = float(np.mean(scores)) if scores else 0.0

        if 1 <= len(text) <= 25 and all(ch in valid_chars for ch in text):
            crops_with_text.append((crop_img, text, score))

    return crops_with_text

# 1. Process Training Photos
train_lines = []
train_count = 0
print("Extracting training pseudo-labels (score >= 0.95)...")
for p in train_photos:
    crops = detect_and_recognize(p)
    for crop_img, text, score in crops:
        if score >= 0.95:
            train_count += 1
            crop_name = f"crop_train_{train_count:05d}.jpg"
            crop_path = os.path.join(TRAIN_CROPS_DIR, crop_name)
            cv2.imwrite(crop_path, crop_img)
            rel_path = f"real_train/{crop_name}"
            train_lines.append(f"{rel_path}\t{text}")

print(f"Generated {len(train_lines)} training pseudo-labels (>= 0.95 confidence)")
with open(os.path.join(DATA_DIR, "real_train.txt"), "w", encoding="utf-8") as f:
    f.write("\n".join(train_lines) + "\n")

# real_train_x10.txt repeating lines 10 times
train_lines_x10 = train_lines * 10
with open(os.path.join(DATA_DIR, "real_train_x10.txt"), "w", encoding="utf-8") as f:
    f.write("\n".join(train_lines_x10) + "\n")

# Combined training file: synthetic + real_train_x10
with open(os.path.join(DATA_DIR, "synth_train.txt"), "r", encoding="utf-8") as f:
    synth_lines = [l.strip() for l in f if l.strip()]

all_train_lines = synth_lines + train_lines_x10
random.shuffle(all_train_lines)
with open(os.path.join(DATA_DIR, "train_all.txt"), "w", encoding="utf-8") as f:
    f.write("\n".join(all_train_lines) + "\n")
print(f"train_all.txt has {len(all_train_lines)} lines ({len(synth_lines)} synth + {len(train_lines_x10)} real_x10)")

# 2. Process Validation Photos (up to 50 crops for review)
val_lines = []
val_count = 0
print("Extracting validation crops for review...")
for p in val_photos:
    crops = detect_and_recognize(p)
    for crop_img, text, score in crops:
        val_count += 1
        crop_name = f"crop_val_{val_count:05d}.jpg"
        crop_path = os.path.join(VAL_CROPS_DIR, crop_name)
        cv2.imwrite(crop_path, crop_img)
        rel_path = f"real_val/{crop_name}"
        val_lines.append(f"{rel_path}\t{text}")
        if val_count >= 50:
            break
    if val_count >= 50:
        break

print(f"Extracted {len(val_lines)} validation crops for review")
with open(os.path.join(DATA_DIR, "real_val.txt"), "w", encoding="utf-8") as f:
    f.write("\n".join(val_lines) + "\n")
with open(os.path.join(DATA_DIR, "real_val_initial.txt"), "w", encoding="utf-8") as f:
    f.write("\n".join(val_lines) + "\n")
