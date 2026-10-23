# Mudal fork: new file in Mudal, a fork of PennyWise AI (AGPL-3.0-only), 2026-10-23 - see NOTICE.
"""Fetch Sri Lankan merchant icons and write 128x128 RGB PNGs.

Drawables land in app/src/main/res/drawable-nodpi/ic_brand_<slug>.png.
Existing upstream icons (Dialog, Keells, KFC, ...) are left untouched.
Banks get initials on a brand-coloured chip instead of a downloaded logo.

Usage (from the repo root):
    python scripts/fetch_sl_logos.py
    python scripts/fetch_sl_logos.py --force
"""

from __future__ import annotations

import argparse
import hashlib
import io
import re
import ssl
import sys
import urllib.error
import urllib.request
from collections import defaultdict
from concurrent.futures import ThreadPoolExecutor, as_completed
from dataclasses import dataclass
from html.parser import HTMLParser
from pathlib import Path
from urllib.parse import urljoin

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
OUT_DIR = ROOT / "app" / "src" / "main" / "res" / "drawable-nodpi"
UA = (
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
)
TIMEOUT = 12
CANVAS = 128
FIT = 104


@dataclass
class Brand:
    slug: str
    color: str
    keywords: list[str]
    domain: str | None = None
    # Drawable already shipped; only the keyword aliases are new.
    existing: bool = False
    # Draw initials instead of downloading a logo. Used for banks.
    monogram: str | None = None
    initials: str = ""

    def __post_init__(self) -> None:
        if not self.initials:
            parts = re.split(r"[\s&-]+", self.keywords[0])
            letters = [p[0] for p in parts if p and p[0].isalpha()]
            self.initials = ("".join(letters[:2]) or self.slug[:2]).upper()


# slug, colour, keywords, domain. existing=True keeps the current PNG.
BRANDS: list[Brand] = [
    # --- already shipped: aliases only ---
    Brand("dialog", "#D81818", ["dialog", "dialog axiata", "dialog tv", "dialog broadband"], existing=True),
    Brand("mobitel", "#4CA64C", ["mobitel"], existing=True),
    Brand("slt", "#004890", ["slt", "sri lanka telecom", "peotv", "peo tv"], existing=True),
    Brand("pickme", "#F0C000", ["pickme"], existing=True),
    Brand("glomark", "#002B5C", ["glomark"], existing=True),
    Brand("keells", "#6BA539", ["keells", "keels", "jaykay"], existing=True),
    Brand("kfc", "#F40027", ["kfc"], existing=True),
    Brand("mcdonalds", "#FFC600", ["mcdonalds", "mcdonald's"], existing=True),
    Brand("dominos", "#0066B3", ["dominos", "domino's"], existing=True),
    Brand("pizza_hut", "#EE3A43", ["pizza hut"], existing=True),
    Brand("burger_king", "#D62300", ["burger king"], existing=True),
    Brand("starbucks", "#00704A", ["starbucks"], existing=True),
    Brand("uber", "#000000", ["uber eats", "uber"], existing=True),
    Brand("spotify", "#1DB954", ["spotify"], existing=True),
    Brand("netflix", "#E50914", ["netflix"], existing=True),
    Brand("youtube", "#FF0000", ["youtube"], existing=True),
    Brand("airtel", "#ED1C24", ["airtel"], existing=True),
    Brand("bookmyshow", "#C4242B", ["bookmyshow"], existing=True),
    Brand("pvr", "#4A148C", ["pvr"], existing=True),
    # --- groceries ---
    Brand("cargills", "#E31C23", ["cargills", "food city"], "cargillsceylon.com"),
    Brand("arpico", "#E10600", ["arpico", "richard pieris"], "arpicosupercentre.com"),
    Brand("laugfs", "#E87722", ["laugfs super", "laugfs gas", "laugfs"], "laugfs.lk"),
    Brand("spar", "#00843D", ["spar"], "spar-international.com"),
    Brand("sathosa", "#0B6B3A", ["lanka sathosa", "sathosa"], "lankasathosa.lk"),
    Brand("finagle", "#C8102E", ["finagle"], "finagle.lk"),
    # --- food ---
    Brand("perera_sons", "#C0392B", ["perera & sons", "perera - sons", "perera and sons"], "pereraandsons.com"),
    # cbl.lk serves a different company's icon, so this stays a letter chip.
    Brand("fab", "#E4002B", ["the fab", "fab"], monogram="FAB"),
    Brand("barista", "#6F4E37", ["barista"], "barista.lk"),
    Brand("java_lounge", "#1B4F72", ["java lounge"], "javalounge.lk"),
    Brand("sensaal", "#F4A300", ["sen-saal", "sensaal"], "sensaal.lk"),
    Brand("sponge", "#F5C518", ["sponge"], "sponge.lk"),
    Brand("dinemore", "#C8102E", ["dinemore"], "dinemore.lk"),
    Brand("subway", "#008C15", ["subway"], "subway.com"),
    Brand("popeyes", "#F15A22", ["popeyes"], "popeyes.com"),
    Brand("breadtalk", "#F5A623", ["breadtalk", "bread talk"], "breadtalk.com"),
    # --- telco / utilities ---
    Brand("hutch", "#E6007E", ["hutchison", "hutch"], "hutch.lk"),
    Brand("lanka_bell", "#F58220", ["lanka bell"], "lankabell.com"),
    Brand("ceb", "#F9A825", ["ceylon electricity", "ceb"], "ceb.lk", initials="CEB"),
    Brand("leco", "#1565C0", ["lanka electricity", "leco"], "leco.lk", initials="LECO"),
    Brand("nwsdb", "#0277BD", ["national water", "water board", "nwsdb"], "waterboard.lk", initials="NW"),
    Brand("litro", "#0D47A1", ["litro"], "litrogas.com"),
    # --- transport / fuel ---
    Brand("kangaroo", "#F9A825", ["kangaroo cabs", "kangaroo"], "kangaroocabs.lk"),
    Brand("ceypetco", "#C62828", ["ceypetco", "ceylon petroleum"], "ceypetco.gov.lk", initials="CP"),
    Brand("lanka_ioc", "#F57C00", ["lanka ioc"], "iocl.com", initials="IOC"),
    Brand("sinopec", "#C8102E", ["sinopec"], "sinopec.com"),
    Brand("shell", "#DD1D21", ["shell"], "shell.com"),
    # --- shopping ---
    Brand("daraz", "#F85606", ["daraz"], "daraz.lk"),
    Brand("kapruka", "#E31C3D", ["kapruka"], "kapruka.com"),
    # The site icon is a hamburger menu, not the wordmark.
    Brand("odel", "#111111", ["odel"], monogram="OD"),
    Brand("nolimit", "#E10600", ["nolimit", "no limit"], "nolimit.lk"),
    Brand("cool_planet", "#00A3E0", ["cool planet"], "coolplanet.lk"),
    Brand("fashion_bug", "#C2185B", ["fashion bug"], "fashionbug.lk"),
    Brand("softlogic", "#E31C23", ["softlogic life", "softlogic"], "softlogic.lk"),
    Brand("abans", "#E10600", ["abans"], "abans.com"),
    Brand("singer", "#E30613", ["singer mega", "singer"], "singer.lk"),
    Brand("damro", "#8D6E63", ["damro"], "damro.lk"),
    Brand("singhagiri", "#6A1B9A", ["singhagiri"], "singhagiri.lk"),
    Brand("takas", "#FF6F00", ["takas"], "takas.lk"),
    Brand("wasi", "#00897B", ["wasi"], "wasi.lk"),
    Brand("simplytek", "#1565C0", ["simplytek"], "simplytek.lk"),
    Brand("idealz", "#6A1B9A", ["idealz"], "idealz.lk"),
    Brand("spa_ceylon", "#1B5E20", ["spa ceylon"], "spaceylon.com"),
    Brand("barefoot", "#5D4037", ["barefoot"], "barefootceylon.com"),
    Brand("laksala", "#F9A825", ["laksala"], "laksala.lk"),
    Brand("dsi", "#0D47A1", ["dsi"], "dsi.lk", initials="DSI"),
    Brand("bata", "#E30613", ["bata"], "bata.com"),
    Brand("sarasavi", "#1565C0", ["sarasavi"], "sarasavi.lk"),
    Brand("vijitha_yapa", "#6D4C41", ["vijitha yapa"], "vijithayapa.com"),
    Brand("mydeal", "#E65100", ["mydeal"], "mydeal.lk"),
    # --- healthcare ---
    Brand("healthguard", "#2E7D32", ["healthguard"], "healthguard.lk"),
    Brand("osusala", "#1565C0", ["osu sala", "osusala"], "spc.lk", initials="OS"),
    Brand("asiri", "#00897B", ["asiri"], "asirihealth.lk"),
    Brand("nawaloka", "#C62828", ["nawaloka"], "nawaloka.com"),
    Brand("durdans", "#1565C0", ["durdans"], "durdans.com"),
    Brand("hemas", "#0D47A1", ["hemas"], "hemas.com"),
    Brand("ninewells", "#00695C", ["ninewells"], "ninewellshospital.lk"),
    Brand("doc990", "#E53935", ["doc990"], "doc990.com", initials="D9"),
    Brand("echannelling", "#1565C0", ["echannelling"], "echannelling.com"),
    Brand("vision_care", "#0277BD", ["vision care"], "visioncare.lk"),
    # --- entertainment / digital ---
    Brand("scope", "#111111", ["scope cinemas", "scope entertainment"], "scopecinemas.com"),
    Brand("mytickets", "#6A1B9A", ["mytickets"], "mytickets.lk"),
    Brand("google", "#4285F4", ["google one"], "google.com"),
    Brand("microsoft", "#00A4EF", ["microsoft"], "microsoft.com"),
    # --- payments ---
    Brand("mintpay", "#6C2BD9", ["mintpay"], "mintpay.lk"),
    # The fetched mark was too blurred to read at 128px.
    Brand("koko", "#111111", ["paykoko", "koko"], monogram="KO"),
    Brand("payhere", "#0D47A1", ["payhere"], "payhere.lk"),
    Brand("webxpay", "#E65100", ["webxpay"], "webxpay.com"),
    Brand("frimi", "#6A1B9A", ["frimi"], "frimi.lk"),
    Brand("genie", "#E10600", ["genie"], "genie.lk"),
    Brand("ezcash", "#F9A825", ["ez cash", "ezcash"], "ezcash.lk"),
    # --- education ---
    Brand("sliit", "#0D47A1", ["sliit"], "sliit.lk", initials="SL"),
    Brand("nsbm", "#C62828", ["nsbm"], "nsbm.ac.lk", initials="NS"),
    Brand("nibm", "#1565C0", ["nibm"], "nibm.lk", initials="NI"),
    Brand("iit", "#1A237E", ["iit"], "iit.ac.lk", initials="IIT"),
    Brand("esoft", "#E65100", ["esoft"], "esoft.lk"),
    Brand("apiit", "#B71C1C", ["apiit"], "apiit.lk", initials="AP"),
    Brand("icbt", "#0D47A1", ["icbt"], "icbt.lk", initials="IC"),
    Brand("british_council", "#E10600", ["british council"], "britishcouncil.org"),
    # --- insurance (logos; banks stay monograms) ---
    Brand("ceylinco", "#0D47A1", ["ceylinco"], "ceylinco.com"),
    Brand("slic", "#0B3D91", ["sri lanka insurance"], "srilankainsurance.com", initials="SLI"),
    Brand("fairfirst", "#F9A825", ["fairfirst"], "fairfirst.lk"),
    Brand("janashakthi", "#6A1B9A", ["janashakthi"], "janashakthi.com"),
    # --- banks: initials on a colour chip, no downloaded mark ---
    Brand("sampath", "#E87722", ["sampath"], monogram="SA"),
    Brand("combank", "#004B8D", ["commercial bank", "combank"], monogram="CB"),
    Brand("hnb", "#C8102E", ["hatton national", "hnb"], monogram="HNB"),
    Brand("boc", "#C5A000", ["bank of ceylon", "boc"], monogram="BOC"),
    Brand("peoples", "#E31837", ["people's bank", "peoples bank"], monogram="PB"),
    Brand("seylan", "#E31C3D", ["seylan"], monogram="SE"),
    Brand("ntb", "#7A1F3D", ["nations trust"], monogram="NTB"),
    Brand("ndb_bank", "#00A651", ["ndb"], monogram="NDB"),
    Brand("dfcc", "#E4002B", ["dfcc"], monogram="DF"),
    Brand("nsb_bank", "#0054A6", ["nsb"], monogram="NSB"),
    Brand("pan_asia", "#F58220", ["pan asia"], monogram="PA"),
]


@dataclass
class Result:
    brand: Brand
    source: str
    path: Path | None = None
    digest: str = ""
    note: str = ""


class IconLinkParser(HTMLParser):
    def __init__(self) -> None:
        super().__init__()
        self.links: list[tuple[str, int]] = []

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        if tag.lower() != "link":
            return
        attr = {k.lower(): (v or "") for k, v in attrs}
        rel = attr.get("rel", "").lower()
        if "icon" not in rel:
            return
        href = attr.get("href", "").strip()
        if not href or href.lower().endswith(".svg"):
            return
        size = 0
        sizes = attr.get("sizes", "")
        match = re.search(r"(\d+)", sizes)
        if match:
            size = int(match.group(1))
        if "apple-touch-icon" in rel and size == 0:
            size = 180
        self.links.append((href, size))


def _ssl_context() -> ssl.SSLContext:
    return ssl.create_default_context()


def fetch_bytes(url: str) -> bytes | None:
    request = urllib.request.Request(url, headers={"User-Agent": UA, "Accept": "*/*"})
    try:
        with urllib.request.urlopen(request, timeout=TIMEOUT, context=_ssl_context()) as response:
            content_type = response.headers.get("Content-Type", "")
            if "text/html" in content_type:
                return None
            data = response.read(2_000_000)
            if data[:15].lstrip().lower().startswith(b"<!doctype") or data[:6].lower().startswith(b"<html"):
                return None
            return data
    except (urllib.error.URLError, TimeoutError, OSError, ValueError):
        return None


def fetch_text(url: str) -> str | None:
    request = urllib.request.Request(url, headers={"User-Agent": UA, "Accept": "text/html"})
    try:
        with urllib.request.urlopen(request, timeout=TIMEOUT, context=_ssl_context()) as response:
            raw = response.read(400_000)
            charset = "utf-8"
            content_type = response.headers.get("Content-Type", "")
            match = re.search(r"charset=([\w-]+)", content_type, re.I)
            if match:
                charset = match.group(1)
            return raw.decode(charset, errors="ignore")
    except (urllib.error.URLError, TimeoutError, OSError, ValueError, LookupError):
        return None


def hex_to_rgb(value: str) -> tuple[int, int, int]:
    value = value.lstrip("#")
    return int(value[0:2], 16), int(value[2:4], 16), int(value[4:6], 16)


def is_light(rgb: tuple[int, int, int]) -> bool:
    red, green, blue = rgb
    return (0.299 * red + 0.587 * green + 0.114 * blue) > 186


def load_image(data: bytes) -> Image.Image | None:
    try:
        image = Image.open(io.BytesIO(data))
        image.load()
        return image
    except Exception:
        return None


def mostly_light_artwork(image: Image.Image) -> bool:
    rgba = image.convert("RGBA")
    pixels = [pixel for pixel in rgba.getdata() if pixel[3] > 140]
    if len(pixels) < 20:
        return False
    light = sum(1 for red, green, blue, _ in pixels if red > 225 and green > 225 and blue > 225)
    return light / len(pixels) > 0.72


def render_icon(image: Image.Image, background: tuple[int, int, int]) -> Image.Image | None:
    rgba = image.convert("RGBA")
    alpha = rgba.getchannel("A")
    bbox = alpha.getbbox()
    if bbox is None:
        return None
    rgba = rgba.crop(bbox)
    width, height = rgba.size
    if width < 12 or height < 12:
        return None
    if max(width, height) / max(1, min(width, height)) > 3.2:
        return None
    scale = FIT / max(width, height)
    resized = rgba.resize((max(1, int(width * scale)), max(1, int(height * scale))), Image.Resampling.LANCZOS)
    canvas = Image.new("RGB", (CANVAS, CANVAS), background)
    plate = Image.new("RGBA", resized.size, background + (255,))
    plate.alpha_composite(resized)
    left = (CANVAS - plate.width) // 2
    top = (CANVAS - plate.height) // 2
    canvas.paste(plate.convert("RGB"), (left, top))
    return canvas


def font(size: int) -> ImageFont.ImageFont:
    for path in (
        r"C:\Windows\Fonts\arialbd.ttf",
        r"C:\Windows\Fonts\arial.ttf",
        "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
    ):
        if Path(path).exists():
            return ImageFont.truetype(path, size)
    return ImageFont.load_default()


def render_monogram(text: str, background: tuple[int, int, int]) -> Image.Image:
    canvas = Image.new("RGB", (CANVAS, CANVAS), background)
    draw = ImageDraw.Draw(canvas)
    label = text[:3]
    size = 64 if len(label) == 1 else 46 if len(label) == 2 else 34
    face = font(size)
    ink = (33, 37, 41) if is_light(background) else (255, 255, 255)
    box = draw.textbbox((0, 0), label, font=face)
    x = (CANVAS - (box[2] - box[0])) // 2 - box[0]
    y = (CANVAS - (box[3] - box[1])) // 2 - box[1]
    draw.text((x, y), label, fill=ink, font=face)
    return canvas


def candidate_urls(brand: Brand) -> list[tuple[str, str]]:
    if not brand.domain:
        return []
    urls: list[tuple[str, str]] = []
    for scheme in ("https", "http"):
        page = f"{scheme}://{brand.domain}/"
        html = fetch_text(page)
        if not html:
            continue
        parser = IconLinkParser()
        try:
            parser.feed(html)
        except Exception:
            continue
        for href, size in sorted(parser.links, key=lambda item: item[1], reverse=True)[:4]:
            urls.append((urljoin(page, href), "site-icon"))
        break
    urls.append((
        f"https://t2.gstatic.com/faviconV2?client=SOCIAL&type=FAVICON&fallback_opts=TYPE,SIZE,URL&url=https://{brand.domain}&size=128",
        "gstatic",
    ))
    urls.append((f"https://www.google.com/s2/favicons?domain={brand.domain}&sz=128", "google"))
    urls.append((f"https://icons.duckduckgo.com/ip3/{brand.domain}.ico", "duckduckgo"))
    return urls


def background_for(brand: Brand, artwork: Image.Image) -> tuple[int, int, int]:
    colour = hex_to_rgb(brand.color)
    if mostly_light_artwork(artwork) and not is_light(colour):
        return colour
    return (255, 255, 255)


def build(brand: Brand) -> Result:
    path = OUT_DIR / f"ic_brand_{brand.slug}.png"
    if brand.existing:
        if not path.exists():
            return Result(brand, "missing-existing", note="expected drawable was not on disk")
        return Result(brand, "existing", path)
    if brand.monogram:
        image = render_monogram(brand.monogram, hex_to_rgb(brand.color))
        image.save(path, "PNG")
        return Result(brand, "monogram", path, hashlib.sha256(path.read_bytes()).hexdigest()[:12])

    for url, source in candidate_urls(brand):
        data = fetch_bytes(url)
        if not data or len(data) < 80:
            continue
        loaded = load_image(data)
        if loaded is None:
            continue
        rendered = render_icon(loaded, background_for(brand, loaded))
        if rendered is None:
            continue
        rendered.save(path, "PNG")
        digest = hashlib.sha256(path.read_bytes()).hexdigest()[:12]
        return Result(brand, source, path, digest)
    image = render_monogram(brand.initials, hex_to_rgb(brand.color))
    image.save(path, "PNG")
    return Result(brand, "monogram-fallback", path, note="no usable logo")


def collapse_generic(results: list[Result]) -> None:
    """The same default globe served for several domains is not a logo."""
    groups: dict[str, list[Result]] = defaultdict(list)
    for result in results:
        if result.source in {"existing", "monogram", "monogram-fallback", "missing-existing"}:
            continue
        if result.path and result.path.exists():
            digest = hashlib.sha256(result.path.read_bytes()).hexdigest()
            result.digest = digest[:12]
            groups[digest].append(result)
    for group in groups.values():
        if len(group) < 3:
            continue
        for result in group:
            image = render_monogram(result.brand.initials, hex_to_rgb(result.brand.color))
            assert result.path is not None
            image.save(result.path, "PNG")
            result.source = "monogram-generic"
            result.note = "shared placeholder icon"


def emit_kotlin() -> None:
    """Print map entries that are not already in BrandIcons.kt."""
    icons = ROOT / "app" / "src" / "main" / "java" / "com" / "pennywiseai" / "tracker" / "ui" / "icons" / "BrandIcons.kt"
    existing = set(re.findall(r'"([^"]+)" to R\.drawable', icons.read_text(encoding="utf-8")))
    seen: set[str] = set()
    print("// mappings")
    for brand in BRANDS:
        for keyword in brand.keywords:
            if keyword in existing or keyword in seen:
                continue
            seen.add(keyword)
            print(f'        "{keyword}" to R.drawable.ic_brand_{brand.slug},')
    print("// colors")
    seen.clear()
    for brand in BRANDS:
        for keyword in brand.keywords:
            if keyword in seen:
                continue
            seen.add(keyword)
            print(f'        "{keyword}" to "{brand.color}",')


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--force", action="store_true", help="re-download even if the PNG exists")
    parser.add_argument("--emit-kotlin", action="store_true", help="print new BrandIcons entries and exit")
    args = parser.parse_args()
    if args.emit_kotlin:
        emit_kotlin()
        return 0
    OUT_DIR.mkdir(parents=True, exist_ok=True)

    pending = []
    skipped: list[Brand] = []
    for brand in BRANDS:
        path = OUT_DIR / f"ic_brand_{brand.slug}.png"
        if path.exists() and not args.force and not brand.existing and brand.monogram is None:
            skipped.append(brand)
        else:
            pending.append(brand)

    results: list[Result] = []
    with ThreadPoolExecutor(max_workers=8) as pool:
        futures = {pool.submit(build, brand): brand for brand in pending}
        for future in as_completed(futures):
            brand = futures[future]
            try:
                result = future.result()
            except Exception as error:  # keep the batch going
                result = Result(brand, "error", note=str(error))
            results.append(result)
            print(f"{result.source:20} {brand.slug}")

    collapse_generic(results)
    for brand in skipped:
        path = OUT_DIR / f"ic_brand_{brand.slug}.png"
        results.append(Result(brand, "kept", path))

    counts: dict[str, int] = defaultdict(int)
    for result in results:
        counts[result.source] += 1
    print("\n---")
    for source, count in sorted(counts.items()):
        print(f"{source}: {count}")
    failed = [result for result in results if result.source in {"monogram-fallback", "monogram-generic", "error", "missing-existing"}]
    if failed:
        print("\nFell back or failed:")
        for result in sorted(failed, key=lambda item: item.brand.slug):
            print(f"  {result.brand.slug}: {result.source} {result.note}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
