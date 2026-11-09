package app.mvoon.engine

import android.graphics.Bitmap
import android.graphics.Typeface
import android.net.Uri
import android.text.Layout
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.text.Cue
import androidx.media3.exoplayer.ExoPlaybackException
import androidx.media3.exoplayer.mediacodec.MediaCodecRenderer
import app.mvoon.core.media.MediaKind
import app.mvoon.core.media.PlayableItem
import app.mvoon.core.media.StreamType
import app.mvoon.core.player.CueTextAlignment
import app.mvoon.core.player.ErrorKind
import app.mvoon.core.player.PlatformImage
import app.mvoon.core.player.PlaybackError
import app.mvoon.core.player.StyledText
import app.mvoon.core.player.SubtitleCue
import app.mvoon.core.player.TextSpanStyle
import app.mvoon.core.util.resolutionLabel

/** Android implementation of the neutral [PlatformImage] handle. */
class AndroidImage(val bitmap: Bitmap) : PlatformImage {
    override val width: Int get() = bitmap.width
    override val height: Int get() = bitmap.height
}

internal object CueMapper {

    fun map(cues: List<Cue>): List<SubtitleCue> = cues.mapNotNull(::mapCue)

    private fun mapCue(cue: Cue): SubtitleCue? {
        cue.bitmap?.let { bitmap ->
            val width = if (cue.size == Cue.DIMEN_UNSET) 0.5f else cue.size
            val height = if (cue.bitmapHeight == Cue.DIMEN_UNSET) Float.NaN else cue.bitmapHeight
            val position = if (cue.position == Cue.DIMEN_UNSET) 0.5f else cue.position
            val left = when (cue.positionAnchor) {
                Cue.ANCHOR_TYPE_MIDDLE -> position - width / 2f
                Cue.ANCHOR_TYPE_END -> position - width
                else -> position
            }
            val line = if (cue.line == Cue.DIMEN_UNSET || cue.lineType != Cue.LINE_TYPE_FRACTION) 0.85f else cue.line
            val h = if (height.isNaN()) width * bitmap.height / bitmap.width.coerceAtLeast(1) else height
            val top = when (cue.lineAnchor) {
                Cue.ANCHOR_TYPE_MIDDLE -> line - h / 2f
                Cue.ANCHOR_TYPE_END -> line - h
                else -> line
            }
            return SubtitleCue.Image(AndroidImage(bitmap), left, top, width, height)
        }
        val text = cue.text ?: return null
        if (text.isBlank()) return null
        val anchoredTop = cue.lineType == Cue.LINE_TYPE_FRACTION && cue.line != Cue.DIMEN_UNSET &&
            cue.line < 0.4f && cue.lineAnchor != Cue.ANCHOR_TYPE_END
        val alignment = when (cue.textAlignment) {
            Layout.Alignment.ALIGN_NORMAL -> CueTextAlignment.START
            Layout.Alignment.ALIGN_OPPOSITE -> CueTextAlignment.END
            else -> CueTextAlignment.CENTER
        }
        return SubtitleCue.Text(styled(text), alignment, anchoredTop)
    }

    private fun styled(text: CharSequence): StyledText {
        val plain = text.toString()
        if (text !is Spanned) return StyledText(plain)
        val spans = text.getSpans(0, text.length, Any::class.java).mapNotNull { span ->
            val start = text.getSpanStart(span)
            val end = text.getSpanEnd(span)
            if (start < 0 || end <= start) return@mapNotNull null
            when (span) {
                is StyleSpan -> TextSpanStyle(
                    start, end,
                    bold = span.style == Typeface.BOLD || span.style == Typeface.BOLD_ITALIC,
                    italic = span.style == Typeface.ITALIC || span.style == Typeface.BOLD_ITALIC,
                )
                is UnderlineSpan -> TextSpanStyle(start, end, underline = true)
                is StrikethroughSpan -> TextSpanStyle(start, end, strikethrough = true)
                is ForegroundColorSpan -> TextSpanStyle(start, end, color = span.foregroundColor)
                is RelativeSizeSpan -> TextSpanStyle(start, end, relativeSize = span.sizeChange)
                else -> null
            }
        }
        return StyledText(plain, spans)
    }
}

object MediaItemMapper {

    fun toMediaItem(item: PlayableItem): MediaItem {
        val subtitleConfigs = item.externalSubtitles.map { sub ->
            var flags = 0
            if (sub.isForced) flags = flags or C.SELECTION_FLAG_FORCED
            if (sub.isUserAdded) flags = flags or C.SELECTION_FLAG_DEFAULT
            MediaItem.SubtitleConfiguration.Builder(Uri.parse(sub.uri))
                .setMimeType(sub.mimeType)
                .setLanguage(sub.language)
                .setLabel(sub.label)
                .setSelectionFlags(flags)
                .setRoleFlags(if (sub.isSdh) C.ROLE_FLAG_DESCRIBES_MUSIC_AND_SOUND else 0)
                .setId("external:${sub.uri}")
                .build()
        }
        val mime = when (item.streamType) {
            StreamType.HLS -> MimeTypes.APPLICATION_M3U8
            StreamType.DASH -> MimeTypes.APPLICATION_MPD
            StreamType.SMOOTH_STREAMING -> MimeTypes.APPLICATION_SS
            StreamType.RTSP -> MimeTypes.APPLICATION_RTSP
            StreamType.AUTO -> if (item.uri.startsWith("rtsp://", ignoreCase = true)) MimeTypes.APPLICATION_RTSP else null
            StreamType.PROGRESSIVE -> null
        }
        val metadata = MediaMetadata.Builder()
            .setTitle(item.title)
            .setDisplayTitle(item.title)
            .setArtist(item.subtitle)
            .setMediaType(if (item.kind == MediaKind.VIDEO) MediaMetadata.MEDIA_TYPE_VIDEO else MediaMetadata.MEDIA_TYPE_MUSIC)
            .setIsPlayable(true)
            .setIsBrowsable(false)
            // For local video the artwork loader turns the media URI itself into a thumbnail.
            .setArtworkUri((item.artworkUri ?: if (!item.isStream) item.uri else null)?.let(Uri::parse))
            .build()
        return MediaItem.Builder()
            .setMediaId(item.id)
            .setUri(item.uri)
            .setMimeType(mime)
            .setSubtitleConfigurations(subtitleConfigs)
            .setMediaMetadata(metadata)
            .build()
    }
}

internal object ErrorMapper {

    fun map(error: PlaybackException, item: PlayableItem?): PlaybackError {
        val technical = buildString {
            append(error.errorCodeName)
            error.cause?.let { append(" — ").append(it.javaClass.simpleName).append(": ").append(it.message ?: "") }
        }
        val formatName = (error as? ExoPlaybackException)
            ?.takeIf { it.type == ExoPlaybackException.TYPE_RENDERER }
            ?.rendererFormat
            ?.let { f ->
                listOfNotNull(CodecNames.describe(f), resolutionLabel(f.width, f.height)).joinToString(" ")
            }
        val isStream = item?.isStream == true
        // Media3 reports a revoked/expired permission as a generic I/O error: look at the root cause.
        val causes = generateSequence(error.cause) { it.cause }.take(8).toList()
        val code = when {
            causes.any { it is SecurityException } -> PlaybackException.ERROR_CODE_IO_NO_PERMISSION
            causes.any { it is java.io.FileNotFoundException } && !isStream -> PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND
            else -> error.errorCode
        }
        return when (code) {
            PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> PlaybackError(
                ErrorKind.FILE_NOT_FOUND, "File not found",
                "It may have been moved, renamed or deleted, or the SD card / USB storage holding it is not available.",
                technical, canRetry = false,
            )
            PlaybackException.ERROR_CODE_IO_NO_PERMISSION -> PlaybackError(
                ErrorKind.NO_PERMISSION, "Can't access this file",
                "Mvoon no longer has permission to read it. Open it again from your file manager, or add its folder in Library → Add folder.",
                technical, canRetry = false,
            )
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
            -> PlaybackError(
                ErrorKind.NETWORK, "Can't reach the stream",
                "Check your internet connection and the address, then try again.", technical,
            )
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> PlaybackError(
                ErrorKind.NETWORK, "The server refused the stream",
                "The address may have expired or require a login. Try opening a fresh link.", technical,
            )
            PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED -> PlaybackError(
                ErrorKind.NETWORK, "Insecure connection blocked",
                "This device does not allow plain http:// streaming. Use an https:// address.", technical, canRetry = false,
            )
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED -> PlaybackError(
                ErrorKind.UNSUPPORTED_FORMAT, "Unsupported file format",
                if (isStream) "The stream's format could not be recognised. Try choosing HLS or DASH explicitly when opening the URL."
                else "The system player can't read this container (it handles MKV, WebM, MP4, MOV, AVI, TS/M2TS, FLV, MP3, FLAC, OGG, WAV, AAC and more). " +
                    "WMV/ASF, RealMedia and VOB files need the compatibility engine (Settings → Audio & video).",
                technical, canRetry = false,
            )
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
            -> PlaybackError(
                ErrorKind.CORRUPT_FILE, "This file looks damaged",
                "Part of the file could not be read. It may be incomplete (still downloading) or corrupted.", technical,
            )
            PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
            -> PlaybackError(
                ErrorKind.UNSUPPORTED_CODEC, "Codec not supported on this device",
                "This device has no decoder for ${formatName ?: "one of the tracks"}. Mvoon uses the phone's own video decoders; check Settings → Format support to see what this device can play.",
                technical, canRetry = false,
            )
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
            PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
            PlaybackException.ERROR_CODE_DECODING_FAILED,
            PlaybackException.ERROR_CODE_DECODING_RESOURCES_RECLAIMED,
            -> PlaybackError(
                ErrorKind.DECODER_FAILED, "The decoder stopped working",
                "Decoding ${formatName ?: "this file"} failed. Try again, or switch Settings → Playback → Video decoder to \"Software first\".",
                technical,
            )
            PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED,
            PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED,
            PlaybackException.ERROR_CODE_AUDIO_TRACK_OFFLOAD_INIT_FAILED,
            PlaybackException.ERROR_CODE_AUDIO_TRACK_OFFLOAD_WRITE_FAILED,
            -> PlaybackError(
                ErrorKind.AUDIO_OUTPUT, "Audio output problem",
                "The audio device rejected the stream. Disconnect and reconnect your headphones/speaker, or pick a different audio track.",
                technical,
            )
            in 6000..6999 -> PlaybackError(
                ErrorKind.DRM, "Protected content",
                "This media is protected by DRM and can't be played by Mvoon.", technical, canRetry = false,
            )
            else -> PlaybackError(
                ErrorKind.UNKNOWN, "Playback stopped",
                (error.cause as? MediaCodecRenderer.DecoderInitializationException)?.diagnosticInfo
                    ?: "Something unexpected happened while playing this file.",
                technical,
            )
        }
    }
}
