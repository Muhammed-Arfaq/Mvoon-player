package app.mvoon.engine

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.os.Build
import androidx.media3.common.MimeTypes
import androidx.media3.decoder.ffmpeg.FfmpegLibrary

/**
 * Inspects what THIS device can actually decode — the platform MediaCodec decoders plus the bundled
 * FFmpeg audio library — instead of assuming. Powers Settings → Format support.
 */
object CodecDiagnostics {

    enum class Route { HARDWARE, PLATFORM_SOFTWARE, FFMPEG, NONE }

    data class FormatSupport(
        val mimeType: String,
        val name: String,
        val isVideo: Boolean,
        val route: Route,
        val decoderNames: List<String>,
        /** Largest resolution a video decoder advertises, e.g. "3840×2160". */
        val maxResolution: String? = null,
        val note: String? = null,
    )

    data class Report(
        val ffmpegAvailable: Boolean,
        val ffmpegVersion: String?,
        val formats: List<FormatSupport>,
        val deviceDecoders: List<String>,
    )

    private val videoFormats = listOf(
        MimeTypes.VIDEO_H264, MimeTypes.VIDEO_H265, MimeTypes.VIDEO_AV1, MimeTypes.VIDEO_VP9, MimeTypes.VIDEO_VP8,
        MimeTypes.VIDEO_DOLBY_VISION, MimeTypes.VIDEO_MP4V, MimeTypes.VIDEO_H263, MimeTypes.VIDEO_MPEG2, MimeTypes.VIDEO_VC1,
    )
    private val audioFormats = listOf(
        MimeTypes.AUDIO_AAC, MimeTypes.AUDIO_MPEG, MimeTypes.AUDIO_FLAC, MimeTypes.AUDIO_OPUS, MimeTypes.AUDIO_VORBIS,
        MimeTypes.AUDIO_AC3, MimeTypes.AUDIO_E_AC3, MimeTypes.AUDIO_E_AC3_JOC, MimeTypes.AUDIO_AC4,
        MimeTypes.AUDIO_DTS, MimeTypes.AUDIO_DTS_HD, MimeTypes.AUDIO_DTS_X, MimeTypes.AUDIO_TRUEHD, MimeTypes.AUDIO_ALAC,
        MimeTypes.AUDIO_RAW, MimeTypes.AUDIO_AMR_NB, MimeTypes.AUDIO_AMR_WB, MimeTypes.AUDIO_MPEG_L2,
    )

    fun ffmpegAvailable(): Boolean = runCatching { FfmpegLibrary.isAvailable() }.getOrDefault(false)

    fun ffmpegSupports(mime: String): Boolean = runCatching { FfmpegLibrary.supportsFormat(mime) }.getOrDefault(false)

    fun report(): Report {
        // ALL_CODECS is what ExoPlayer's MediaCodecUtil consults, so the report matches real playback.
        val codecs = MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos
            .filterNot { it.isEncoder || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && it.isAlias) }
            .distinctBy { it.name }
        val ffmpeg = ffmpegAvailable()
        val formats = (videoFormats.map { it to true } + audioFormats.map { it to false }).map { (mime, isVideo) ->
            val decoders = codecs.filter { info -> info.supportedTypes.any { it.equals(mime, ignoreCase = true) } }
            val hardware = decoders.filter(::isHardware)
            val software = decoders - hardware.toSet()
            val ffmpegOk = !isVideo && ffmpeg && ffmpegSupports(mime)
            val route = when {
                hardware.isNotEmpty() -> Route.HARDWARE
                software.isNotEmpty() -> Route.PLATFORM_SOFTWARE
                ffmpegOk -> Route.FFMPEG
                else -> Route.NONE
            }
            val note = when {
                route == Route.NONE && mime == MimeTypes.AUDIO_E_AC3_JOC && ffmpeg && ffmpegSupports(MimeTypes.AUDIO_E_AC3) ->
                    "Plays as E-AC-3 5.1/7.1 via FFmpeg (Atmos height objects are not rendered)"
                route == Route.NONE && mime == MimeTypes.AUDIO_DTS_X && ffmpeg && ffmpegSupports(MimeTypes.AUDIO_DTS_HD) ->
                    "DTS:X core plays via FFmpeg (object audio is not rendered)"
                mime == MimeTypes.AUDIO_AC4 && route == Route.NONE -> "AC-4 needs a licensed device decoder"
                isVideo && route == Route.NONE -> "No decoder on this device — video won't play (audio still will)"
                route == Route.FFMPEG -> "Software decoding via bundled FFmpeg"
                hardware.isNotEmpty() && software.isNotEmpty() -> "Software fallback available"
                else -> null
            }
            FormatSupport(
                mimeType = mime,
                name = CodecNames.nameFor(mime) ?: mime,
                isVideo = isVideo,
                route = if (route == Route.NONE && note?.contains("via FFmpeg") == true) Route.FFMPEG else route,
                decoderNames = decoders.map { it.name } + if (ffmpegOk) listOf("FFmpeg") else emptyList(),
                maxResolution = if (isVideo) maxResolution(decoders, mime) else null,
                note = note,
            )
        }
        return Report(
            ffmpegAvailable = ffmpeg,
            ffmpegVersion = runCatching { FfmpegLibrary.getVersion() }.getOrNull(),
            formats = formats,
            deviceDecoders = codecs.map { info ->
                val kind = if (isHardware(info)) "HW" else "SW"
                "${info.name} [$kind] — ${info.supportedTypes.joinToString()}"
            },
        )
    }

    private fun isHardware(info: MediaCodecInfo): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            info.isHardwareAccelerated
        } else {
            val name = info.name.lowercase()
            !(name.startsWith("omx.google.") || name.startsWith("c2.android.") || name.contains(".sw."))
        }

    private fun maxResolution(decoders: List<MediaCodecInfo>, mime: String): String? {
        var best: Pair<Int, Int>? = null
        for (info in decoders) {
            val caps = runCatching { info.getCapabilitiesForType(mime).videoCapabilities }.getOrNull() ?: continue
            val w = caps.supportedWidths.upper
            val h = caps.supportedHeights.upper
            if (best == null || w.toLong() * h > best.first.toLong() * best.second) best = w to h
        }
        return best?.let { "${it.first}×${it.second}" }
    }
}
