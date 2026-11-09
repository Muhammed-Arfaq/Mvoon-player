# Third-party notices

Mvoon is free software, licensed under the **GNU General Public License, version 3 or later**
(see [`LICENSE`](LICENSE)). It includes the following third-party software and fonts. Their full
licence texts are bundled in `app/src/main/assets/licenses/`, ship inside the app, and can be read in
**Settings → About**.

| Component | Version | Licence | Source |
|---|---|---|---|
| AndroidX Media3 (ExoPlayer, Session, HLS/DASH/RTSP/SmoothStreaming) | 1.9.4 | Apache-2.0 | https://github.com/androidx/media |
| Media3 FFmpeg decoder, Jellyfin build (`org.jellyfin.media3:media3-ffmpeg-decoder`) | 1.9.0+1 | GPL-3.0 (contains FFmpeg 6.0 libavcodec, LGPL-2.1+/GPL) | https://github.com/jellyfin/jellyfin-androidx-media, https://ffmpeg.org |
| libVLC for Android (`org.videolan.android:libvlc-all`) — the compatibility engine | 3.7.7 (VLC 3.0.25) | LGPL-2.1-or-later (bundles VLC modules and FFmpeg libavcodec/libavformat, LGPL-2.1+) | https://code.videolan.org/videolan/vlc-android |
| Jetpack Compose, Material 3, Activity, Lifecycle, Navigation, Room, DataStore, Core | see `gradle/libs.versions.toml` | Apache-2.0 | https://developer.android.com/jetpack |
| Kotlin, kotlinx.coroutines | 2.2.21 / 1.10.2 | Apache-2.0 | https://kotlinlang.org |
| Coil | 3.3.0 | Apache-2.0 | https://github.com/coil-kt/coil |
| Lucide icons for Compose (`com.composables:icons-lucide`) | 1.1.0 | MIT (wrapper); icon artwork ISC (Lucide), Feather-derived icons MIT | https://github.com/composablehorizons/composeicons, https://lucide.dev |
| Guava (via Media3) | — | Apache-2.0 | https://github.com/google/guava |
| Fraunces font | variable | SIL OFL 1.1 | https://github.com/undercasetype/Fraunces |
| Manrope font | variable | SIL OFL 1.1 | https://github.com/sharanda/manrope |
| Atkinson Hyperlegible font | 1.006 | SIL OFL 1.1 | https://brailleinstitute.org/freefont |

## Where to get the source code (GPL-3.0 / LGPL-2.1 "Corresponding Source")

Anyone who receives Mvoon can get the complete source code that builds it, free of charge:

1. **Mvoon itself:** this repository, https://github.com/mvoon/mvoon. Each release on Google Play
   has a matching Git tag (for example `v1.5.1`) and a `Mvoon-<version>-source.zip` attached to the
   GitHub release. The link is also in the app under **Settings → About → Source code**.
2. **FFmpeg decoder (GPL-3.0):** built by Jellyfin from
   https://github.com/jellyfin/jellyfin-androidx-media at tag **`v1.9.0+1`**, which pins
   - FFmpeg: https://git.ffmpeg.org/ffmpeg.git, `release/6.0` branch, commit
     **`37c90f55d8b6c3948c79449b04755527b4ae1351`**;
   - AndroidX Media: https://github.com/androidx/media, commit **`7cc1056f840ce226598d3b990d4a6f7cd17e2831`**.
3. **libVLC (LGPL-2.1+):** VideoLAN's https://code.videolan.org/videolan/vlc-android at tag
   **`libvlc-3.7.7`** (commit `7a7c8f14be67`), whose build script pins
   - libvlcjni: https://code.videolan.org/videolan/libvlcjni, branch `libvlcjni-3.x`, commit
     **`0b8dc65efb203c86a0476bc337ddd99ecf1c0ef6`** (includes the patches applied to VLC);
   - VLC: https://code.videolan.org/videolan/vlc, branch `3.0.x`, commit
     **`84177b2273abc5c4300234e6e18b55a4d2dd5a03`** (VLC 3.0.25), plus the contrib libraries listed
     in its `contrib/src` folder.

To keep these available for as long as the app is distributed, the maintainer downloads copies with
`python tools/fetch_third_party_sources.py` and attaches them to each GitHub release. If any link
stops working, open an issue at https://github.com/mvoon/mvoon/issues and a copy will be provided.

### libVLC (LGPL-2.1+)

libVLC is used unmodified as a separate shared library (`libvlc.so` and its Java wrapper), loaded at
runtime only when the compatibility engine is needed. You may replace the bundled library with a
compatible build of your own; the LGPL-2.1 text is in `app/src/main/assets/licenses/LGPL-2.1.txt`.
Because Mvoon also bundles GPL-3.0 code (the FFmpeg decoder build), the app as a whole is
distributed under GPL-3.0, which is compatible with LGPL-2.1+.

## Test media

Test media in `app/src/androidTest/assets/media` comes from the AndroidX Media3 test data
(Apache-2.0). The generated `mvoon_multiaudio.*` files are derived from those samples by
`tools/make_test_media.py`. The compatibility-engine samples (`welcome3.wmv`, `nokia_n90.wmv`,
`firstrun.rm`, `divx5.avi`) are public test files from https://samples.ffmpeg.org. They are used only
by the instrumented tests, are not part of the app, and are **not** stored in the public repository;
download them into `app/src/androidTest/assets/media/compat/` before running those tests (see
`README.md`).

## Trademarks

Dolby, Dolby Digital, Dolby Digital Plus, Dolby TrueHD and Dolby Atmos are trademarks of Dolby
Laboratories. DTS and DTS:X are trademarks of DTS, Inc. VLC and VideoLAN are trademarks of VideoLAN.
They are used only to describe format compatibility and the libraries used; Mvoon is not affiliated
with, endorsed or certified by these companies.
