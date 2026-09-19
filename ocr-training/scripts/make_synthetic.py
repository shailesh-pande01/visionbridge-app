import os
import random
import string
import cv2
import numpy as np
from PIL import Image, ImageDraw, ImageFont

SEED = 42
random.seed(SEED)
np.random.seed(SEED)

DICT_PATH = "ocr-training/PaddleOCR/ppocr/utils/dict/ppocrv5_en_dict.txt"
OUTPUT_DIR = "ocr-training/data/synth"
LABEL_FILE = "ocr-training/data/synth_train.txt"
TOTAL_SAMPLES = 6000

os.makedirs(OUTPUT_DIR, exist_ok=True)
os.makedirs(os.path.dirname(LABEL_FILE), exist_ok=True)

with open(DICT_PATH, "r", encoding="utf-8") as f:
    valid_chars = set([line.strip("\r\n") for line in f if line.strip("\r\n")])
valid_chars.add(" ")

font_files = [
    "arial.ttf", "arialbd.ttf", "ariali.ttf", "ARIALN.TTF", "ARIALNB.TTF",
    "calibri.ttf", "calibrib.ttf", "calibrii.ttf",
    "times.ttf", "timesbd.ttf", "timesi.ttf",
    "cour.ttf", "courbd.ttf", "couri.ttf",
    "consola.ttf", "consolab.ttf",
    "georgia.ttf", "georgiab.ttf",
    "tahoma.ttf", "tahomabd.ttf",
    "verdana.ttf", "verdanab.ttf",
    "trebuc.ttf", "trebucbd.ttf",
    "comic.ttf", "impact.ttf", "segoeui.ttf", "segoeuib.ttf"
]
available_fonts = [os.path.join("C:/Windows/Fonts", f) for f in font_files if os.path.exists(os.path.join("C:/Windows/Fonts", f))]
assert len(available_fonts) >= 15, f"Expected >= 15 fonts, found {len(available_fonts)}"

# Templates
signs = ["EXIT", "ENTRANCE", "PUSH", "PULL", "RESTROOM", "CAUTION", "EMERGENCY EXIT", "NO SMOKING", "PLEASE WAIT", "STAFF ONLY", "WAY OUT", "KEEP CLEAR", "INQUIRY", "INFORMATION", "TICKETS", "PLATFORM", "LIFT", "STAIRS", "ENTRY", "DANGER"]
places = ["Room 101", "Room 204", "Gate 4B", "Gate 12", "Platform 1", "Platform 3", "Bus 42A", "Bus 108", "Terminal 2", "Seat 14C", "Coach S4", "Bed 12", "Floor 3", "Ward 4", "Cabin 7", "Bay 9"]
products = ["Whole Milk", "Salted Butter", "Organic Oats", "Brown Bread", "Tomato Sauce", "Green Tea", "Wheat Flour", "White Rice", "Sunflower Oil", "Dark Chocolate", "Almond Milk", "Apple Juice", "Hand Wash", "Dish Soap", "Bath Soap"]
receipts = ["Total Amount", "Subtotal", "Tax Included", "Thank You", "Payment Done", "Balance Due", "Cash Paid", "Credit Card", "UPI Payment", "Bill No: 9812", "Item Count: 4", "Discount", "Net Payable", "Order #4502"]
menus = ["Veg Burger", "Chicken Tikka", "Filter Coffee", "Masala Dosa", "Paneer Tikka", "Mango Lassi", "Fresh Lime Soda", "Cold Coffee", "Veg Sandwich", "Garlic Bread", "Tomato Soup", "French Fries", "Hot Chocolate", "Butter Naan", "Dal Makhani"]
medicines = ["PARACETAMOL", "Paracetamol 500mg", "Amoxicillin 250mg", "Ibuprofen 400mg", "Cetirizine 10mg", "Metformin 500mg", "Omeprazole 20mg", "Aspirin 75mg", "Azithromycin 500", "Cough Syrup 100ml", "Eye Drops 10ml", "Vitamin C 500mg", "Zinc 50mg", "Dolo 650 mg", "Crocin 500 mg"]
units = ["500 mg", "650 mg", "250 mg", "100 mg", "50 mg", "10 mg", "5 mg", "10 ml", "15 ml", "60 ml", "100 ml", "200 ml", "500 ml", "1 L", "2 L", "250 g", "500 g", "1 kg", "2 kg", "100 IU"]
dates = ["EXP 08/2027", "EXP 12/2026", "MFG 01/2025", "MFG 05/2024", "USE BY 10/26", "USE BY 04/27", "BB 15/09/2026", "EXP 03/2028", "MFG 11/2024", "EXP 11/2029", "24-11-2027", "15/08/2026"]
prices_rupee = ["\u20b945", "\u20b9120", "\u20b9250.00", "\u20b999", "\u20b9499", "\u20b9999", "\u20b91,200", "\u20b9150", "\u20b985.50", "\u20b930", "\u20b960", "\u20b9500", "\u20b9750", "\u20b910", "\u20b920"]
prices_usd = ["$4.99", "$12.50", "$1.99", "$25.00", "$9.99", "$15.75", "$3.50", "$0.99", "$8.00", "$50.00"]
phones = ["+91 98765 43210", "+91 91234 56789", "022 2548 9912", "1800 200 1234", "080 4123 9876", "1800 111 2222"]
emails = ["support@care.org", "help@vision.org", "info@pharmacy.in", "contact@store.com", "service@city.gov"]
words = ["OPEN", "CLOSED", "HOURS", "DAILY", "MONDAY", "SUNDAY", "DO NOT TOUCH", "PHARMACY", "CLINIC", "HOSPITAL", "LABORATORY", "DOCTOR", "PATIENT", "APPOINTMENT", "MEDICINE", "PRESCRIPTION", "KEEP REFRIGERATED", "SHAKE WELL"]

all_sources = [signs, places, products, receipts, menus, medicines, units, dates, prices_rupee, prices_usd, phones, emails, words]

def generate_text():
    while True:
        # Choose a template source
        source = random.choice(all_sources)
        text = random.choice(source)
        # Maybe add a price or unit or number
        if random.random() < 0.2 and len(text) < 15:
            text = f"{text} {random.choice(prices_rupee if random.random() < 0.7 else units)}"
        
        # Clean to 1-25 characters
        text = text.strip()
        if len(text) > 25:
            text = text[:25].strip()
        
        # Check all chars in dict
        if 1 <= len(text) <= 25 and all(c in valid_chars for c in text):
            return text

def augment_image(img_bgr):
    h, w = img_bgr.shape[:2]
    
    # 1. Perspective / shear / rotation (-7 to +7 deg)
    angle = random.uniform(-7, 7)
    M = cv2.getRotationMatrix2D((w / 2, h / 2), angle, 1.0)
    img_bgr = cv2.warpAffine(img_bgr, M, (w, h), borderMode=cv2.BORDER_REPLICATE)
    
    # 2. Blur: Gaussian or motion blur
    blur_type = random.random()
    if blur_type < 0.35:
        k = random.choice([3, 5])
        img_bgr = cv2.GaussianBlur(img_bgr, (k, k), 0)
    elif blur_type < 0.6:
        k = random.choice([3, 5])
        kernel = np.zeros((k, k))
        if random.random() < 0.5:
            kernel[int((k - 1) / 2), :] = np.ones(k)
        else:
            kernel[:, int((k - 1) / 2)] = np.ones(k)
        kernel = kernel / k
        img_bgr = cv2.filter2D(img_bgr, -1, kernel)
    
    # 3. Downscale-then-upscale
    if random.random() < 0.3:
        factor = random.uniform(0.5, 0.8)
        nh, nw = max(16, int(h * factor)), max(32, int(w * factor))
        img_bgr = cv2.resize(img_bgr, (nw, nh), interpolation=cv2.INTER_LINEAR)
        img_bgr = cv2.resize(img_bgr, (w, h), interpolation=cv2.INTER_LINEAR)

    # 4. Low light / brightness / contrast
    alpha = random.uniform(0.6, 1.3) # contrast
    beta = random.uniform(-40, 40)   # brightness
    img_bgr = np.clip(alpha * img_bgr + beta, 0, 255).astype(np.uint8)

    # 5. Gaussian noise
    if random.random() < 0.4:
        noise = np.random.normal(0, random.uniform(5, 20), img_bgr.shape).astype(np.float32)
        img_bgr = np.clip(img_bgr.astype(np.float32) + noise, 0, 255).astype(np.uint8)

    # 6. Glare spot or shadow
    if random.random() < 0.35:
        # glare or shadow circle
        cx, cy = random.randint(0, w), random.randint(0, h)
        radius = random.randint(min(h, w) // 2, max(h, w))
        is_glare = random.random() < 0.5
        mask = np.zeros((h, w), dtype=np.float32)
        cv2.circle(mask, (cx, cy), radius, 1.0, -1)
        mask = cv2.GaussianBlur(mask, (21, 21), 0)
        intensity = random.uniform(30, 80)
        if is_glare:
            img_bgr = np.clip(img_bgr.astype(np.float32) + mask[:, :, None] * intensity, 0, 255).astype(np.uint8)
        else:
            img_bgr = np.clip(img_bgr.astype(np.float32) - mask[:, :, None] * intensity, 0, 255).astype(np.uint8)

    # 7. JPEG compression quality 30-90
    jpeg_quality = random.randint(30, 90)
    encode_param = [int(cv2.IMWRITE_JPEG_QUALITY), jpeg_quality]
    _, encimg = cv2.imencode(".jpg", img_bgr, encode_param)
    img_bgr = cv2.imdecode(encimg, 1)

    return img_bgr

labels = []
print(f"Generating {TOTAL_SAMPLES} synthetic crops...")

for idx in range(1, TOTAL_SAMPLES + 1):
    text = generate_text()
    font_path = random.choice(available_fonts)
    font_size = random.randint(28, 44)
    font = ImageFont.truetype(font_path, font_size)
    
    # Calculate text bounding box
    dummy_img = Image.new("RGB", (1, 1))
    dummy_draw = ImageDraw.Draw(dummy_img)
    bbox = dummy_draw.textbbox((0, 0), text, font=font)
    tw, th = bbox[2] - bbox[0], bbox[3] - bbox[1]
    
    pad_x = random.randint(8, 20)
    pad_y = random.randint(6, 14)
    cw = max(48, tw + pad_x * 2)
    ch = max(36, th + pad_y * 2)
    
    # Background & text color
    dark_on_light = random.random() < 0.7
    if dark_on_light:
        bg_color = (random.randint(200, 255), random.randint(200, 255), random.randint(200, 255))
        fg_color = (random.randint(0, 70), random.randint(0, 70), random.randint(0, 70))
    else:
        bg_color = (random.randint(10, 60), random.randint(10, 60), random.randint(10, 60))
        fg_color = (random.randint(190, 255), random.randint(190, 255), random.randint(190, 255))
        
    pil_img = Image.new("RGB", (cw, ch), bg_color)
    draw = ImageDraw.Draw(pil_img)
    
    # Add slight background gradient or texture
    if random.random() < 0.3:
        for y_line in range(ch):
            shade = int(random.uniform(-15, 15))
            c = tuple(max(0, min(255, v + shade)) for v in bg_color)
            draw.line([(0, y_line), (cw, y_line)], fill=c)
            
    tx = pad_x - bbox[0]
    ty = pad_y - bbox[1]
    draw.text((tx, ty), text, font=font, fill=fg_color)
    
    # Convert PIL to cv2 BGR
    img_bgr = cv2.cvtColor(np.array(pil_img), cv2.COLOR_RGB2BGR)
    
    # Augment
    img_bgr = augment_image(img_bgr)
    
    # Save
    filename = f"{idx:06d}.jpg"
    rel_path = f"synth/{filename}"
    out_path = os.path.join(OUTPUT_DIR, filename)
    cv2.imwrite(out_path, img_bgr)
    
    labels.append(f"{rel_path}\t{text}")
    
    if idx % 1000 == 0:
        print(f"Rendered {idx}/{TOTAL_SAMPLES} crops...")

with open(LABEL_FILE, "w", encoding="utf-8") as f:
    f.write("\n".join(labels) + "\n")

print(f"Successfully wrote {len(labels)} synthetic crops to {LABEL_FILE}!")
