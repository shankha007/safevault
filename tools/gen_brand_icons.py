"""
Generates the bundled brand-logo catalogue (D-028).

    python tools/gen_brand_icons.py

Downloads the pinned Simple Icons release (CC0) from jsDelivr, keeps only the brands listed
below, checks every path is plain SVG path syntax, and writes:

  app/src/main/java/com/safevault/app/util/BrandCatalog.kt   (used by the app)
  desktop-preview/index.html, between the BRANDS markers     (used by the HTML preview)

The app itself never goes online: the logos are compiled into the APK.
To add a brand, add a row to BRANDS and run the script again.
"""
import json
import re
import sys
import urllib.request
from pathlib import Path

SIMPLE_ICONS_VERSION = "16.34.0"
CDN = f"https://cdn.jsdelivr.net/npm/simple-icons@{SIMPLE_ICONS_VERSION}"
ROOT = Path(__file__).resolve().parent.parent

# (slug in Simple Icons, or None for a colour-only brand), title, keywords, domains, hex for colour-only.
# Keywords match whole words of the item name ("HDFC debit card" -> "hdfc"); keywords of 5+
# letters also match inside run-together names ("mygmailaccount"). Domains match the website
# field, including subdomains.
BRANDS = [
    ("gmail", "Gmail", ["gmail"], ["gmail.com", "mail.google.com"], None),
    ("google", "Google", ["google"], ["google.com", "accounts.google.com"], None),
    ("googledrive", "Google Drive", ["google drive", "gdrive"], ["drive.google.com"], None),
    ("googlepay", "Google Pay", ["google pay", "gpay"], ["pay.google.com"], None),
    ("youtube", "YouTube", ["youtube"], ["youtube.com", "youtu.be"], None),
    ("youtubemusic", "YouTube Music", ["youtube music", "yt music"], ["music.youtube.com"], None),
    ("facebook", "Facebook", ["facebook", "fb"], ["facebook.com", "fb.com"], None),
    ("instagram", "Instagram", ["instagram", "insta"], ["instagram.com"], None),
    ("whatsapp", "WhatsApp", ["whatsapp"], ["whatsapp.com"], None),
    ("threads", "Threads", ["threads"], ["threads.net", "threads.com"], None),
    ("x", "X", ["x", "twitter"], ["x.com", "twitter.com"], None),
    ("snapchat", "Snapchat", ["snapchat"], ["snapchat.com"], None),
    ("telegram", "Telegram", ["telegram"], ["telegram.org", "t.me"], None),
    ("signal", "Signal", ["signal"], ["signal.org"], None),
    ("discord", "Discord", ["discord"], ["discord.com"], None),
    ("reddit", "Reddit", ["reddit"], ["reddit.com"], None),
    ("pinterest", "Pinterest", ["pinterest"], ["pinterest.com"], None),
    ("tiktok", "TikTok", ["tiktok"], ["tiktok.com"], None),
    ("quora", "Quora", ["quora"], ["quora.com"], None),
    ("medium", "Medium", ["medium"], ["medium.com"], None),
    ("netflix", "Netflix", ["netflix"], ["netflix.com"], None),
    ("spotify", "Spotify", ["spotify"], ["spotify.com"], None),
    ("twitch", "Twitch", ["twitch"], ["twitch.tv"], None),
    ("apple", "Apple", ["apple", "apple id", "appleid"], ["apple.com", "appleid.apple.com"], None),
    ("icloud", "iCloud", ["icloud"], ["icloud.com"], None),
    ("samsung", "Samsung", ["samsung"], ["samsung.com"], None),
    ("xiaomi", "Xiaomi", ["xiaomi", "mi account"], ["mi.com", "xiaomi.com"], None),
    ("oneplus", "OnePlus", ["oneplus"], ["oneplus.com", "oneplus.in"], None),
    ("github", "GitHub", ["github"], ["github.com"], None),
    ("gitlab", "GitLab", ["gitlab"], ["gitlab.com"], None),
    ("bitbucket", "Bitbucket", ["bitbucket"], ["bitbucket.org"], None),
    ("atlassian", "Atlassian", ["atlassian"], ["atlassian.com", "atlassian.net"], None),
    ("jira", "Jira", ["jira"], [], None),
    ("trello", "Trello", ["trello"], ["trello.com"], None),
    ("stackoverflow", "Stack Overflow", ["stack overflow", "stackoverflow"], ["stackoverflow.com"], None),
    ("cloudflare", "Cloudflare", ["cloudflare"], ["cloudflare.com"], None),
    ("figma", "Figma", ["figma"], ["figma.com"], None),
    ("notion", "Notion", ["notion"], ["notion.so", "notion.com"], None),
    ("dropbox", "Dropbox", ["dropbox"], ["dropbox.com"], None),
    ("zoom", "Zoom", ["zoom"], ["zoom.us", "zoom.com"], None),
    ("claude", "Claude", ["claude"], ["claude.ai"], None),
    ("protonmail", "Proton Mail", ["proton", "protonmail", "proton mail"], ["proton.me", "protonmail.com"], None),
    ("duolingo", "Duolingo", ["duolingo"], ["duolingo.com"], None),
    ("coursera", "Coursera", ["coursera"], ["coursera.org"], None),
    ("udemy", "Udemy", ["udemy"], ["udemy.com"], None),
    ("wordpress", "WordPress", ["wordpress"], ["wordpress.com", "wordpress.org"], None),
    ("shopify", "Shopify", ["shopify"], ["shopify.com"], None),
    ("ebay", "eBay", ["ebay"], ["ebay.com", "ebay.in"], None),
    ("aliexpress", "AliExpress", ["aliexpress"], ["aliexpress.com"], None),
    ("airbnb", "Airbnb", ["airbnb"], ["airbnb.com", "airbnb.co.in"], None),
    ("bookingdotcom", "Booking.com", ["booking", "booking.com"], ["booking.com"], None),
    ("uber", "Uber", ["uber"], ["uber.com"], None),
    ("steam", "Steam", ["steam"], ["steampowered.com", "steamcommunity.com"], None),
    ("epicgames", "Epic Games", ["epic games", "epicgames"], ["epicgames.com"], None),
    ("playstation", "PlayStation", ["playstation", "psn"], ["playstation.com"], None),
    ("paypal", "PayPal", ["paypal"], ["paypal.com"], None),
    ("razorpay", "Razorpay", ["razorpay"], ["razorpay.com"], None),
    # India: payments, food, shopping, telecom, banks, brokers
    ("paytm", "Paytm", ["paytm"], ["paytm.com"], None),
    ("phonepe", "PhonePe", ["phonepe", "phone pe"], ["phonepe.com"], None),
    ("swiggy", "Swiggy", ["swiggy"], ["swiggy.com"], None),
    ("zomato", "Zomato", ["zomato"], ["zomato.com"], None),
    ("bigbasket", "bigbasket", ["bigbasket", "big basket"], ["bigbasket.com"], None),
    ("airtel", "Airtel", ["airtel"], ["airtel.in", "airtel.com"], None),
    ("jio", "Jio", ["jio", "myjio"], ["jio.com"], None),
    ("vodafone", "Vodafone", ["vodafone", "vi"], ["vodafone.com", "myvi.in"], None),
    ("hdfcbank", "HDFC Bank", ["hdfc", "hdfc bank"], ["hdfcbank.com"], None),
    ("icicibank", "ICICI Bank", ["icici", "icici bank"], ["icicibank.com"], None),
    ("axisbank", "Axis Bank", ["axis", "axis bank"], ["axisbank.com"], None),
    ("zerodha", "Zerodha", ["zerodha", "kite"], ["zerodha.com", "kite.zerodha.com"], None),
    # Card networks: used for cards when the bank has no logo. Generic words like "visa"
    # are left out of the keywords so a login named "Visa application" keeps its letter.
    ("visa", "Visa", [], [], None),
    ("mastercard", "Mastercard", ["mastercard"], [], None),
    ("americanexpress", "American Express", ["american express", "amex"], ["americanexpress.com"], None),
    ("discover", "Discover", [], [], None),
    ("jcb", "JCB", [], [], None),
    ("dinersclub", "Diners Club", ["diners club", "diners"], [], None),
    # No free logo exists (the owners asked Simple Icons to remove them). Shown as the first
    # letter on the brand colour instead of an imitation of the logo.
    (None, "LinkedIn", ["linkedin"], ["linkedin.com"], "0A66C2"),
    (None, "Amazon", ["amazon", "amazon prime", "prime video"], ["amazon.com", "amazon.in", "primevideo.com"], "FF9900"),
    (None, "Microsoft", ["microsoft", "outlook", "hotmail", "xbox", "onedrive", "teams", "skype"],
     ["microsoft.com", "live.com", "outlook.com", "hotmail.com", "office.com", "xbox.com", "onedrive.com"], "0078D4"),
    (None, "Slack", ["slack"], ["slack.com"], "4A154B"),
    (None, "Yahoo", ["yahoo"], ["yahoo.com"], "6001D2"),
    (None, "Flipkart", ["flipkart"], ["flipkart.com"], "2874F0"),
    (None, "ChatGPT", ["chatgpt", "openai"], ["chatgpt.com", "openai.com"], "10A37F"),
    (None, "Canva", ["canva"], ["canva.com"], "00C4CC"),
]

PATH_OK = re.compile(r"^[MmZzLlHhVvCcSsQqTtAa0-9eE.,\-\s]+$")
HEX_OK = re.compile(r"^[0-9A-Fa-f]{6}$")


def fetch(url: str, tries: int = 4) -> bytes:
    for attempt in range(tries):
        try:
            with urllib.request.urlopen(url, timeout=60) as r:
                return r.read()
        except OSError:
            if attempt == tries - 1:
                raise
    raise AssertionError("unreachable")


def main() -> None:
    data = {i["slug"]: i for i in json.loads(fetch(f"{CDN}/data/simple-icons.json"))}
    rows = []
    for slug, title, keywords, domains, hex_ in BRANDS:
        path = None
        if slug:
            if slug not in data:
                sys.exit(f"{slug} is not in Simple Icons {SIMPLE_ICONS_VERSION}")
            hex_ = data[slug]["hex"]
            svg = fetch(f"{CDN}/icons/{slug}.svg").decode()
            m = re.search(r'<path d="([^"]+)"', svg)
            if not m or not PATH_OK.match(m.group(1)):
                sys.exit(f"{slug}: unexpected SVG content")
            path = m.group(1)
        if not HEX_OK.match(hex_):
            sys.exit(f"{title}: bad colour {hex_}")
        rows.append(dict(slug=slug or re.sub(r"\W", "", title.lower()), title=title, hex=hex_.upper(),
                         path=path, keywords=[k.lower() for k in keywords], domains=domains))
        print(("logo  " if path else "colour"), title)

    kt = [
        "// GENERATED by tools/gen_brand_icons.py from Simple Icons " + SIMPLE_ICONS_VERSION + " (CC0). Do not edit by hand.",
        "// Brand names and logos are trademarks of their owners; they are shown only to identify items.",
        "package com.safevault.app.util",
        "",
        "internal val brandCatalog: List<Brand> = listOf(",
    ]
    q = lambda s: json.dumps(s)  # JSON strings are valid Kotlin strings for this character set
    for r in rows:
        kt.append(
            f"    Brand({q(r['slug'])}, {q(r['title'])}, 0xFF{r['hex']}, "
            f"listOf({', '.join(map(q, r['keywords']))}), listOf({', '.join(map(q, r['domains']))}), "
            f"{q(r['path']) if r['path'] else 'null'}),"
        )
    kt.append(")")
    (ROOT / "app/src/main/java/com/safevault/app/util/BrandCatalog.kt").write_text("\n".join(kt) + "\n", encoding="utf-8")

    # Inlined so the preview stays a single file that opens anywhere.
    preview = ROOT / "desktop-preview/index.html"
    if preview.exists():
        html = preview.read_text(encoding="utf-8")
        start, end = "<!-- BRANDS:START", "<!-- BRANDS:END -->"
        i, j = html.index(start), html.index(end)
        block = (start + " (generated by tools/gen_brand_icons.py) -->\n<script>window.BRANDS = "
                 + json.dumps(rows, separators=(",", ":")) + ";</script>\n")
        preview.write_text(html[:i] + block + html[j:], encoding="utf-8")
    print(f"{len(rows)} brands written")


if __name__ == "__main__":
    main()
