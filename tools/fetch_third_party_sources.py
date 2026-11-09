"""Download the exact third-party source code that Mvoon's GPL/LGPL libraries were built from.

Attach the files this creates to each GitHub release, next to Mvoon's own source zip, so the
"Corresponding Source" stays available even if an upstream link disappears (see THIRD_PARTY_NOTICES.md).

    python tools/fetch_third_party_sources.py [output-folder]     (default: dist/sources)

Keep these revisions in sync with gradle/libs.versions.toml when you update ffmpegDecoder or libvlc.
"""
import os
import sys
import urllib.request

SOURCES = [
    # FFmpeg decoder (GPL-3.0): Jellyfin's build of org.jellyfin.media3:media3-ffmpeg-decoder 1.9.0+1.
    ("jellyfin-androidx-media-v1.9.0+1.tar.gz",
     "https://github.com/jellyfin/jellyfin-androidx-media/archive/refs/tags/v1.9.0%2B1.tar.gz"),
    ("ffmpeg-37c90f55d8b6.tar.gz",
     "https://github.com/FFmpeg/FFmpeg/archive/37c90f55d8b6c3948c79449b04755527b4ae1351.tar.gz"),
    ("androidx-media-7cc1056f840c.tar.gz",
     "https://github.com/androidx/media/archive/7cc1056f840ce226598d3b990d4a6f7cd17e2831.tar.gz"),
    # libVLC (LGPL-2.1+): org.videolan.android:libvlc-all 3.7.7.
    ("vlc-android-libvlc-3.7.7.tar.gz",
     "https://code.videolan.org/videolan/vlc-android/-/archive/libvlc-3.7.7/vlc-android-libvlc-3.7.7.tar.gz"),
    ("libvlcjni-0b8dc65efb20.tar.gz",
     "https://code.videolan.org/videolan/libvlcjni/-/archive/0b8dc65efb203c86a0476bc337ddd99ecf1c0ef6/libvlcjni-0b8dc65efb203c86a0476bc337ddd99ecf1c0ef6.tar.gz"),
    ("vlc-84177b2273ab.tar.gz",
     "https://code.videolan.org/videolan/vlc/-/archive/84177b2273abc5c4300234e6e18b55a4d2dd5a03/vlc-84177b2273abc5c4300234e6e18b55a4d2dd5a03.tar.gz"),
]


def main() -> None:
    out = sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(__file__), "..", "dist", "sources")
    os.makedirs(out, exist_ok=True)
    for name, url in SOURCES:
        target = os.path.join(out, name)
        if os.path.exists(target) and os.path.getsize(target) > 0:
            print(f"already there: {name}")
            continue
        print(f"downloading {name} ...", flush=True)
        request = urllib.request.Request(url, headers={"User-Agent": "mvoon-source-fetch"})
        with urllib.request.urlopen(request, timeout=120) as response, open(target + ".part", "wb") as f:
            while chunk := response.read(1 << 20):
                f.write(chunk)
        os.replace(target + ".part", target)
        print(f"  {os.path.getsize(target) / 1e6:.1f} MB")
    print(f"done: {os.path.abspath(out)}")


if __name__ == "__main__":
    main()
