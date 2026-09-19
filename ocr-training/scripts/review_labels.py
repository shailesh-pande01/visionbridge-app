import os
import json
import urllib.parse
from http.server import HTTPServer, BaseHTTPRequestHandler

PORT = 8765
DATA_DIR = os.path.abspath("ocr-training/data")
REAL_VAL_PATH = os.path.join(DATA_DIR, "real_val.txt")

def load_items():
    if not os.path.exists(REAL_VAL_PATH):
        return []
    items = []
    with open(REAL_VAL_PATH, "r", encoding="utf-8") as f:
        for line in f:
            parts = line.strip("\r\n").split("\t")
            if len(parts) >= 2:
                items.append({"path": parts[0], "text": parts[1]})
            elif len(parts) == 1 and parts[0]:
                items.append({"path": parts[0], "text": ""})
    return items

def save_items(items):
    with open(REAL_VAL_PATH, "w", encoding="utf-8") as f:
        for item in items:
            f.write(f"{item['path']}\t{item['text']}\n")

current_index = 0

class ReviewHandler(BaseHTTPRequestHandler):
    def log_message(self, format, *args):
        pass

    def do_GET(self):
        global current_index
        parsed = urllib.parse.urlparse(self.path)

        if parsed.path == "/":
            items = load_items()
            total = len(items)
            if total == 0:
                html = "<html><body style='font-family:sans-serif;padding:40px;text-align:center;'><h2>No validation crops found or all reviewed/deleted!</h2></body></html>"
            else:
                current_index = max(0, min(current_index, total - 1))
                item = items[current_index]
                html = f"""<!DOCTYPE html>
<html>
<head>
    <meta charset="utf-8">
    <title>VisionBridge OCR Label Review</title>
    <style>
        body {{ font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif; background: #0f172a; color: #f8fafc; margin: 0; padding: 30px; display: flex; flex-direction: column; align-items: center; justify-content: center; min-height: 80vh; }}
        .card {{ background: #1e293b; border-radius: 12px; padding: 30px; max-width: 650px; width: 100%; box-shadow: 0 10px 25px -5px rgba(0,0,0,0.5); text-align: center; }}
        .progress {{ font-size: 16px; color: #94a3b8; font-weight: 600; margin-bottom: 20px; }}
        .img-container {{ background: #334155; border-radius: 8px; padding: 20px; margin-bottom: 25px; display: flex; align-items: center; justify-content: center; min-height: 120px; }}
        img {{ max-width: 100%; max-height: 180px; object-fit: contain; border-radius: 4px; box-shadow: 0 4px 6px rgba(0,0,0,0.3); }}
        input[type="text"] {{ width: 90%; font-size: 22px; padding: 12px 16px; border-radius: 8px; border: 2px solid #3b82f6; background: #0f172a; color: #fff; text-align: center; margin-bottom: 25px; }}
        input[type="text"]:focus {{ outline: none; border-color: #60a5fa; box-shadow: 0 0 0 3px rgba(96,165,250,0.3); }}
        .buttons {{ display: flex; gap: 12px; justify-content: center; }}
        button {{ font-size: 15px; font-weight: 600; padding: 10px 20px; border-radius: 6px; border: none; cursor: pointer; transition: background 0.15s; }}
        .btn-save {{ background: #10b981; color: white; }}
        .btn-save:hover {{ background: #059669; }}
        .btn-del {{ background: #ef4444; color: white; }}
        .btn-del:hover {{ background: #dc2626; }}
        .btn-back {{ background: #475569; color: white; }}
        .btn-back:hover {{ background: #334155; }}
        .guidance {{ margin-top: 20px; font-size: 13px; color: #94a3b8; line-height: 1.5; }}
    </style>
</head>
<body>
    <div class="card">
        <div class="progress">Reviewing Crop: {current_index + 1} / {total}</div>
        <div class="img-container">
            <img src="/image?path={urllib.parse.quote(item['path'])}" alt="Crop">
        </div>
        <form method="POST" action="/action">
            <input type="text" name="text" value="{item['text'].replace('\"', '&quot;')}" autofocus>
            <div class="buttons">
                <button type="submit" name="action" value="back" class="btn-back">&#8592; Back</button>
                <button type="submit" name="action" value="delete" class="btn-del">&#10006; Delete (unreadable)</button>
                <button type="submit" name="action" value="save" class="btn-save">&#10004; Save &amp; next</button>
            </div>
        </form>
        <div class="guidance">
            <strong>Instructions:</strong> Type exactly what is visible. If unreadable, click Delete. Never guess.
        </div>
    </div>
</body>
</html>"""
            self.send_response(200)
            self.send_header("Content-Type", "text/html; charset=utf-8")
            self.end_headers()
            self.wfile.write(html.encode("utf-8"))

        elif parsed.path == "/image":
            query = urllib.parse.parse_qs(parsed.query)
            rel_path = query.get("path", [""])[0]
            full_path = os.path.normpath(os.path.join(DATA_DIR, rel_path))
            if full_path.startswith(DATA_DIR) and os.path.exists(full_path):
                self.send_response(200)
                self.send_header("Content-Type", "image/jpeg")
                self.end_headers()
                with open(full_path, "rb") as f:
                    self.wfile.write(f.read())
            else:
                self.send_response(404)
                self.end_headers()
        else:
            self.send_response(404)
            self.end_headers()

    def do_POST(self):
        global current_index
        if self.path == "/action":
            content_length = int(self.headers.get("Content-Length", 0))
            post_body = self.rfile.read(content_length).decode("utf-8")
            form_data = urllib.parse.parse_qs(post_body)

            action = form_data.get("action", ["save"])[0]
            new_text = form_data.get("text", [""])[0].strip()

            items = load_items()
            if items:
                current_index = max(0, min(current_index, len(items) - 1))
                if action == "save":
                    items[current_index]["text"] = new_text
                    save_items(items)
                    current_index = min(len(items) - 1, current_index + 1)
                elif action == "delete":
                    del items[current_index]
                    save_items(items)
                    current_index = max(0, min(current_index, len(items) - 1))
                elif action == "back":
                    current_index = max(0, current_index - 1)

            self.send_response(303)
            self.send_header("Location", "/")
            self.end_headers()

def run():
    server = HTTPServer(("0.0.0.0", PORT), ReviewHandler)
    print(f"Review tool listening on http://localhost:{PORT}")
    server.serve_forever()

if __name__ == "__main__":
    run()
