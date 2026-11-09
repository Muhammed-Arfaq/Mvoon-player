package app.mvoon.engine.render

import android.content.Context
import android.os.Looper
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.text.TextOutput
import androidx.media3.exoplayer.text.TextRenderer
import app.mvoon.core.settings.AudioDecoderMode
import app.mvoon.core.settings.VideoDecoderMode

/**
 * Renderer set used by Mvoon:
 *  - platform MediaCodec video decoders (hardware first unless the user prefers software), with
 *    automatic fallback to the next decoder (usually a software one) if initialisation fails;
 *  - platform audio decoders plus the FFmpeg extension (AC-3, E-AC-3, DTS, TrueHD, …) — placed
 *    after the platform decoders by default, or before them when the user prefers FFmpeg;
 *  - an audio sink and text renderer that implement A/V and subtitle sync offsets.
 */
class MvoonRenderersFactory(
    context: Context,
    videoDecoderMode: VideoDecoderMode,
    audioDecoderMode: AudioDecoderMode,
) : DefaultRenderersFactory(context) {

    var audioSink: DelayAudioSink? = null
        private set
    var subtitleRenderer: SubtitleDelayRenderer? = null
        private set

    init {
        setEnableDecoderFallback(true)
        setExtensionRendererMode(
            if (audioDecoderMode == AudioDecoderMode.FFMPEG_FIRST) EXTENSION_RENDERER_MODE_PREFER
            else EXTENSION_RENDERER_MODE_ON,
        )
        setMediaCodecSelector(
            if (videoDecoderMode == VideoDecoderMode.SOFTWARE_FIRST) MediaCodecSelector.PREFER_SOFTWARE
            else MediaCodecSelector.DEFAULT,
        )
    }

    override fun buildAudioSink(
        context: Context,
        enableFloatOutput: Boolean,
        enableAudioOutputPlaybackParams: Boolean,
    ): AudioSink {
        val base = super.buildAudioSink(context, enableFloatOutput, enableAudioOutputPlaybackParams)
            ?: error("DefaultRenderersFactory returned no audio sink")
        return DelayAudioSink(base).also { audioSink = it }
    }

    override fun buildTextRenderers(
        context: Context,
        output: TextOutput,
        outputLooper: Looper,
        extensionRendererMode: Int,
        out: ArrayList<Renderer>,
    ) {
        val built = ArrayList<Renderer>()
        super.buildTextRenderers(context, output, outputLooper, extensionRendererMode, built)
        built.forEach { renderer ->
            if (renderer is TextRenderer && subtitleRenderer == null) {
                out += SubtitleDelayRenderer(renderer).also { subtitleRenderer = it }
            } else {
                out += renderer
            }
        }
    }
}
