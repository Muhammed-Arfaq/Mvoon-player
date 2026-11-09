package app.mvoon.vlc

/** Friendly names for the FourCC codec ids libVLC reports (lower-cased, space-padded to 4 chars). */
internal object VlcCodecNames {

    private val names = mapOf(
        "h264" to "H.264 (AVC)", "avc1" to "H.264 (AVC)", "hevc" to "H.265 (HEVC)", "av01" to "AV1",
        "vp90" to "VP9", "vp80" to "VP8", "mp4v" to "MPEG-4 Part 2", "dx50" to "DivX 5", "divx" to "DivX",
        "div3" to "DivX 3 (MS MPEG-4 v3)", "xvid" to "Xvid", "mp43" to "MS MPEG-4 v3", "mp42" to "MS MPEG-4 v2",
        "mp41" to "MS MPEG-4 v1", "wmv1" to "WMV 7", "wmv2" to "WMV 8", "wmv3" to "WMV 9", "wvc1" to "VC-1",
        "wmva" to "VC-1 Advanced", "mpgv" to "MPEG-1/2", "mp2v" to "MPEG-2", "mp1v" to "MPEG-1", "h263" to "H.263",
        "flv1" to "Sorenson Spark", "rv10" to "RealVideo 1", "rv20" to "RealVideo 2", "rv30" to "RealVideo 3",
        "rv40" to "RealVideo 4", "theo" to "Theora", "mjpg" to "Motion JPEG", "vc1 " to "VC-1",
        "mp4a" to "AAC", "mpga" to "MPEG audio", "mp3 " to "MP3", "a52 " to "AC-3 (Dolby Digital)",
        "eac3" to "E-AC-3 (Dolby Digital Plus)", "dts " to "DTS", "mlp " to "MLP", "trhd" to "TrueHD",
        "flac" to "FLAC", "opus" to "Opus", "vorb" to "Vorbis", "alac" to "ALAC", "wma1" to "WMA 1",
        "wma2" to "WMA 2", "wmap" to "WMA Pro", "wmal" to "WMA Lossless", "cook" to "RealAudio Cook",
        "atrc" to "ATRAC3", "sipr" to "RealAudio Sipro", "28_8" to "RealAudio 28.8", "14_4" to "RealAudio 14.4",
        "araw" to "PCM", "s16l" to "PCM", "samr" to "AMR-NB", "sawb" to "AMR-WB",
        "subt" to "Text", "ssa " to "ASS / SSA", "webv" to "WebVTT", "tx3g" to "TX3G", "pgs " to "PGS (image)",
        "dvbs" to "DVB (image)", "spu " to "VobSub (image)", "cc1 " to "Closed captions",
    )

    private val imageSubtitles = setOf("pgs ", "dvbs", "spu ")

    /** libVLC packs the FourCC little-endian into an int ('h','2','6','4' -> 0x34363268). */
    private fun fourcc(value: Int): String =
        String(CharArray(4) { i -> ((value ushr (8 * i)) and 0xFF).toChar() }).lowercase()

    /** [description] is libVLC's own codec description, used when the FourCC isn't in the table. */
    fun name(fourcc: Int, description: String?): String? {
        if (fourcc == 0) return description?.ifBlank { null }
        val key = fourcc(fourcc)
        return names[key] ?: description?.ifBlank { null } ?: key.trim().uppercase()
    }

    fun isImageSubtitle(fourcc: Int): Boolean = fourcc != 0 && fourcc(fourcc) in imageSubtitles
}
