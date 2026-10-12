import os
from PIL import Image, ImageDraw, ImageFont

def render_terminal_screenshot(text_path, output_png_path):
    content = ""
    with open(text_path, "rb") as f:
        raw_bytes = f.read()
    for enc in ["utf-8-sig", "utf-8", "utf-16", "latin1"]:
        try:
            content = raw_bytes.decode(enc)
            break
        except Exception:
            continue
    lines = [line.rstrip() for line in content.splitlines()]

    font_size = 14
    line_spacing = 6
    try:
        # Try standard Windows monospace fonts
        font = ImageFont.truetype("consola.ttf", font_size)
    except Exception:
        try:
            font = ImageFont.truetype("arial.ttf", font_size)
        except Exception:
            font = ImageFont.load_default()

    margin_x = 24
    margin_top = 48
    margin_bottom = 24
    line_height = font_size + line_spacing

    max_line_len = max(len(l) for l in lines) if lines else 60
    img_width = max(860, max_line_len * 9 + margin_x * 2)
    img_height = margin_top + len(lines) * line_height + margin_bottom

    img = Image.new("RGB", (img_width, img_height), color=(30, 30, 30))
    draw = ImageDraw.Draw(img)

    # Title bar
    draw.rectangle([0, 0, img_width, 36], fill=(45, 45, 45))
    # Window control dots
    draw.ellipse([14, 12, 24, 22], fill=(255, 95, 86))   # Red
    draw.ellipse([32, 12, 42, 22], fill=(255, 189, 46))  # Yellow
    draw.ellipse([50, 12, 60, 22], fill=(39, 201, 63))   # Green

    # Title text
    title = "bash - Personal Finance Manager API Smoke Tests"
    draw.text((80, 10), title, fill=(200, 200, 200), font=font)

    y = margin_top
    for line in lines:
        color = (220, 220, 220)
        if "[PASS]" in line:
            color = (78, 201, 176) # Cyan/Green
        elif "ALL TESTS PASSED" in line or "100%" in line:
            color = (80, 250, 123) # Bright Green
        elif "FAIL" in line or "FAILED" in line:
            color = (255, 85, 85) # Red
        elif "===" in line or "Personal Finance Manager" in line:
            color = (86, 156, 214) # Cyan/Blue
        elif "Base URL:" in line:
            color = (220, 220, 170)

        draw.text((margin_x, y), line, fill=color, font=font)
        y += line_height

    os.makedirs(os.path.dirname(output_png_path), exist_ok=True)
    img.save(output_png_path, "PNG")
    print(f"Screenshot successfully generated at: {output_png_path}")

if __name__ == "__main__":
    render_terminal_screenshot("submission/test-report.txt", "submission/test-report.png")
