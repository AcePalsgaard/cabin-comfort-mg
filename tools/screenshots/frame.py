#!/usr/bin/env python3
"""
Turns raw emulator screenshots into framed Play Store images (title, subtitle, phone frame).

    pip install pillow
    python frame.py <raw-dir> <out-dir>

<raw-dir> holds the raw adb screencaps named in SHOTS below (1080x2400 portrait,
2400x1080 landscape). Output goes to <out-dir>/1.png, 2.png, ... in SHOTS order;
copy them to app/src/main/play/listings/en-GB/graphics/phone-screenshots/.

Raw captures: log in to the mock server (tools/mock-server, --fail-rate 0 --slow 0
--outside 3), turn on the SystemUI demo status bar, 24-hour clock.
"""
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

NAVY = (19, 32, 59)
CREAM = (250, 246, 240)
ORANGE = (232, 89, 12)
SUB_ORANGE = (242, 107, 58)
WHITE = (255, 255, 255)
FRAME = (10, 12, 18)

# (raw file, title, subtitle, background "dark"/"light", cover raw pixels from this y down or None)
SHOTS = [
    ("p_running.png", "One tap to a warm car", "Heat, defrost, seats and wheel at once", "dark", None),
    ("p_light.png", "Picks the right profile", "Suggested from the outside temperature", "light", None),
    ("w_home.png", "Right on your home screen", "Widgets that start and stop in one tap", "dark", None),
    ("l_running.png", "Any way you hold it", "Made for portrait and landscape", "dark", None),
    # Settings: hide the debug-only test server section below the "Your car" card.
    ("p_settings.png", "Your car, your colour", "Drawn in its own colour – or use your photo", "light", 1530),
    ("p_edit.png", "Your comfort, your way", "Seats, steering wheel, windows, fan", "dark", None),
]


def font(bold: bool, size: int) -> ImageFont.FreeTypeFont:
    for name in (["segoeuib.ttf", "arialbd.ttf", "DejaVuSans-Bold.ttf"] if bold
                 else ["segoeui.ttf", "arial.ttf", "DejaVuSans.ttf"]):
        try:
            return ImageFont.truetype(name, size)
        except OSError:
            continue
    return ImageFont.load_default()


def rounded(img: Image.Image, radius: int) -> Image.Image:
    mask = Image.new("L", img.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, img.width - 1, img.height - 1], radius, fill=255)
    out = img.convert("RGBA")
    out.putalpha(mask)
    return out


def frame(raw: Image.Image, title: str, subtitle: str, theme: str, cover_from: int | None) -> Image.Image:
    raw = raw.convert("RGB")
    if cover_from is not None:
        bg_px = raw.getpixel((20, cover_from))
        ImageDraw.Draw(raw).rectangle([0, cover_from, raw.width, raw.height], fill=bg_px)

    landscape = raw.width > raw.height
    canvas_size = (1920, 1080) if landscape else (1080, 1920)
    bg = NAVY if theme == "dark" else CREAM
    fg = WHITE if theme == "dark" else NAVY
    canvas = Image.new("RGB", canvas_size, bg)
    d = ImageDraw.Draw(canvas)

    cx = canvas_size[0] // 2
    max_w = canvas_size[0] - 140

    def fitted(bold: bool, size: int, text: str) -> ImageFont.FreeTypeFont:
        f = font(bold, size)
        while size > 30 and d.textlength(text, font=f) > max_w:
            size -= 2
            f = font(bold, size)
        return f

    title_font = fitted(True, 84 if not landscape else 72, title)
    sub_font = fitted(False, 46 if not landscape else 40, subtitle)
    top = 110 if not landscape else 60
    d.text((cx, top), title, font=title_font, fill=fg, anchor="mt")
    d.text((cx, top + (110 if not landscape else 95)), subtitle, font=sub_font, fill=SUB_ORANGE, anchor="mt")

    # Phone: screen scaled to fit, inside a dark bezel.
    bezel = 14
    if landscape:
        screen_w = 1480
        screen_h = round(raw.height * screen_w / raw.width)
        y0 = 250
    else:
        screen_w = 700
        screen_h = round(raw.height * screen_w / raw.width)
        y0 = 310
    x0 = cx - screen_w // 2
    screen = rounded(raw.resize((screen_w, screen_h), Image.LANCZOS), 44)
    d.rounded_rectangle(
        [x0 - bezel, y0 - bezel, x0 + screen_w + bezel, y0 + screen_h + bezel],
        radius=44 + bezel, fill=FRAME,
    )
    canvas.paste(screen, (x0, y0), screen)
    return canvas


def main() -> None:
    raw_dir, out_dir = Path(sys.argv[1]), Path(sys.argv[2])
    out_dir.mkdir(parents=True, exist_ok=True)
    for i, (name, title, sub, theme, cover) in enumerate(SHOTS, start=1):
        img = frame(Image.open(raw_dir / name), title, sub, theme, cover)
        img.save(out_dir / f"{i}.png", optimize=True)
        print(f"{i}.png  {img.size[0]}x{img.size[1]}  {title}")


if __name__ == "__main__":
    main()
