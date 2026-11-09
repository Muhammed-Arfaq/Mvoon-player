package app.mvoon.engine

import androidx.media3.common.Format
import androidx.media3.common.MimeTypes

/** Human-friendly codec names. Brand names (Dolby, DTS) are used descriptively only. */
object CodecNames {

    private val names = mapOf(
        MimeTypes.VIDEO_H264 to "H.264 (AVC)",
        MimeTypes.VIDEO_H265 to "H.265 (HEVC)",
        MimeTypes.VIDEO_AV1 to "AV1",
        MimeTypes.VIDEO_VP9 to "VP9",
        MimeTypes.VIDEO_VP8 to "VP8",
        MimeTypes.VIDEO_MP4V to "MPEG-4 Part 2",
        MimeTypes.VIDEO_DIVX to "DivX / Xvid",
        MimeTypes.VIDEO_MPEG2 to "MPEG-2",
        MimeTypes.VIDEO_MPEG to "MPEG-1",
        MimeTypes.VIDEO_H263 to "H.263",
        MimeTypes.VIDEO_VC1 to "VC-1",
        MimeTypes.VIDEO_DOLBY_VISION to "Dolby Vision",
        MimeTypes.VIDEO_MJPEG to "Motion JPEG",
        MimeTypes.VIDEO_APV to "APV",
        "video/mp43" to "MS MPEG-4 v3",
        "video/wmv" to "WMV",
        MimeTypes.AUDIO_AAC to "AAC",
        MimeTypes.AUDIO_MPEG to "MP3",
        MimeTypes.AUDIO_MPEG_L1 to "MP1",
        MimeTypes.AUDIO_MPEG_L2 to "MP2",
        MimeTypes.AUDIO_AC3 to "AC-3 (Dolby Digital)",
        MimeTypes.AUDIO_E_AC3 to "E-AC-3 (Dolby Digital Plus)",
        MimeTypes.AUDIO_E_AC3_JOC to "E-AC-3 JOC (Atmos bed)",
        MimeTypes.AUDIO_AC4 to "AC-4",
        MimeTypes.AUDIO_TRUEHD to "TrueHD",
        MimeTypes.AUDIO_DTS to "DTS",
        MimeTypes.AUDIO_DTS_HD to "DTS-HD",
        MimeTypes.AUDIO_DTS_EXPRESS to "DTS Express",
        MimeTypes.AUDIO_DTS_X to "DTS:X",
        MimeTypes.AUDIO_FLAC to "FLAC",
        MimeTypes.AUDIO_ALAC to "ALAC",
        MimeTypes.AUDIO_OPUS to "Opus",
        MimeTypes.AUDIO_VORBIS to "Vorbis",
        MimeTypes.AUDIO_RAW to "PCM",
        MimeTypes.AUDIO_AMR_NB to "AMR-NB",
        MimeTypes.AUDIO_AMR_WB to "AMR-WB",
        MimeTypes.AUDIO_MLAW to "G.711 µ-law",
        MimeTypes.AUDIO_ALAW to "G.711 A-law",
        MimeTypes.AUDIO_WAV to "WAV",
        MimeTypes.APPLICATION_SUBRIP to "SRT",
        MimeTypes.TEXT_SSA to "ASS / SSA",
        MimeTypes.TEXT_VTT to "WebVTT",
        MimeTypes.APPLICATION_MP4VTT to "WebVTT",
        MimeTypes.APPLICATION_TTML to "TTML",
        MimeTypes.APPLICATION_TX3G to "TX3G",
        MimeTypes.APPLICATION_CEA608 to "CEA-608",
        MimeTypes.APPLICATION_CEA708 to "CEA-708",
        MimeTypes.APPLICATION_PGS to "PGS (image)",
        MimeTypes.APPLICATION_VOBSUB to "VobSub (image)",
        MimeTypes.APPLICATION_DVBSUBS to "DVB (image)",
    )

    private val bitmapSubtitleMimes = setOf(
        MimeTypes.APPLICATION_PGS,
        MimeTypes.APPLICATION_VOBSUB,
        MimeTypes.APPLICATION_DVBSUBS,
    )

    fun nameFor(mime: String?): String? = mime?.let { names[it] ?: it.substringAfter('/').uppercase() }

    /**
     * Since Media3 1.4 subtitles are parsed during extraction: the track's sample MIME type becomes
     * `application/x-media3-cues` and the original format moves to [Format.codecs].
     */
    fun originalMime(format: Format): String? =
        if (format.sampleMimeType == MimeTypes.APPLICATION_MEDIA3_CUES) format.codecs ?: format.sampleMimeType
        else format.sampleMimeType

    fun isBitmapSubtitle(format: Format): Boolean = originalMime(format) in bitmapSubtitleMimes

    fun describe(format: Format): String = nameFor(originalMime(format)) ?: "Unknown"
}
