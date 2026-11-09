"""Package Mvoon's complete source code as dist/Mvoon-<version>-source.zip (for each GitHub release).

    python tools/make_source_zip.py

Leaves out build output, IDE caches, release APKs and everything secret: the signing key
(signing/), keystore.properties and local.properties are never included.
"""
import os
import re
import zipfile

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
SKIP_DIRS = {".git", ".gradle", ".kotlin", ".idea", "build", "dist", "signing", "captures", ".cxx", ".externalNativeBuild"}
SKIP_PATHS = {os.path.join(".github", "modernize"), os.path.join("app", "src", "androidTest", "assets", "media", "compat")}
SKIP_FILES = {"keystore.properties", "local.properties", ".DS_Store"}
SKIP_EXT = {".jks", ".keystore", ".apk", ".aab", ".iml"}


def version() -> str:
    gradle = open(os.path.join(ROOT, "app", "build.gradle.kts"), encoding="utf-8").read()
    return re.search(r'versionName = "([^"]+)"', gradle).group(1)


def main() -> None:
    name = f"Mvoon-{version()}-source"
    out = os.path.join(ROOT, "dist", f"{name}.zip")
    os.makedirs(os.path.dirname(out), exist_ok=True)
    count = 0
    with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as z:
        for dirpath, dirnames, filenames in os.walk(ROOT):
            rel_dir = os.path.relpath(dirpath, ROOT)
            dirnames[:] = sorted(
                d for d in dirnames
                if d not in SKIP_DIRS and os.path.normpath(os.path.join(rel_dir, d)) not in SKIP_PATHS
            )
            for f in sorted(filenames):
                if f in SKIP_FILES or os.path.splitext(f)[1].lower() in SKIP_EXT:
                    continue
                rel = os.path.normpath(os.path.join(rel_dir, f))
                z.write(os.path.join(ROOT, rel), os.path.join(name, rel))
                count += 1
    print(f"{out}: {count} files, {os.path.getsize(out) / 1e6:.1f} MB")


if __name__ == "__main__":
    main()
