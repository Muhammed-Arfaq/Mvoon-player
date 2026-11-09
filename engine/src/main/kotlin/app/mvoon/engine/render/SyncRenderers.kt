package app.mvoon.engine.render

import androidx.media3.common.Format
import androidx.media3.common.Timeline
import androidx.media3.common.util.Clock
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.RendererCapabilities
import androidx.media3.exoplayer.RendererConfiguration
import androidx.media3.exoplayer.analytics.PlayerId
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.ForwardingAudioSink
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.SampleStream
import androidx.media3.exoplayer.text.TextRenderer

/**
 * Audio/video sync offset. The audio renderer is ExoPlayer's master clock, so shifting the position
 * the sink reports shifts *when video frames are shown* relative to the sound:
 * reporting `actual + d` shows frames `d` earlier, i.e. the audio is heard `d` later.
 *
 * Forward jumps of the clock apply instantly; backward jumps are clamped by the renderer, so the
 * engine issues a seek when the delay is reduced.
 */
class DelayAudioSink(sink: AudioSink) : ForwardingAudioSink(sink) {
    @Volatile
    var delayUs: Long = 0

    override fun getCurrentPositionUs(sourceEnded: Boolean): Long {
        val position = super.getCurrentPositionUs(sourceEnded)
        return if (position == AudioSink.CURRENT_POSITION_NOT_SET) position else position + delayUs
    }
}

/**
 * Subtitle timing offset. Media3's [TextRenderer] is final and has no offset API, so this delegates
 * every call and evaluates subtitles at `playbackPosition - delay`. Positive delay = later subtitles.
 * Text samples are buffered well ahead of the playhead, so negative delays (earlier subtitles) work too.
 */
class SubtitleDelayRenderer(private val delegate: TextRenderer) : Renderer {
    @Volatile
    var delayUs: Long = 0

    override fun getName(): String = delegate.name
    override fun getTrackType(): Int = delegate.trackType
    override fun getCapabilities(): RendererCapabilities = delegate.capabilities
    override fun init(index: Int, playerId: PlayerId, clock: Clock) = delegate.init(index, playerId, clock)
    override fun getMediaClock() = delegate.mediaClock
    override fun getState(): Int = delegate.state

    override fun enable(
        configuration: RendererConfiguration,
        formats: Array<out Format>,
        stream: SampleStream,
        positionUs: Long,
        joining: Boolean,
        mayRenderStartOfStream: Boolean,
        startPositionUs: Long,
        offsetUs: Long,
        mediaPeriodId: MediaSource.MediaPeriodId,
    ) = delegate.enable(
        configuration, formats, stream, positionUs - delayUs, joining, mayRenderStartOfStream,
        startPositionUs, offsetUs, mediaPeriodId,
    )

    override fun start() = delegate.start()

    override fun replaceStream(
        formats: Array<out Format>,
        stream: SampleStream,
        startPositionUs: Long,
        offsetUs: Long,
        mediaPeriodId: MediaSource.MediaPeriodId,
    ) = delegate.replaceStream(formats, stream, startPositionUs, offsetUs, mediaPeriodId)

    override fun getStream(): SampleStream? = delegate.stream
    override fun hasReadStreamToEnd(): Boolean = delegate.hasReadStreamToEnd()
    override fun getReadingPositionUs(): Long = delegate.readingPositionUs
    override fun getDurationToProgressUs(positionUs: Long, elapsedRealtimeUs: Long): Long =
        delegate.getDurationToProgressUs(positionUs - delayUs, elapsedRealtimeUs)

    override fun setCurrentStreamFinal() = delegate.setCurrentStreamFinal()
    override fun isCurrentStreamFinal(): Boolean = delegate.isCurrentStreamFinal
    override fun maybeThrowStreamError() = delegate.maybeThrowStreamError()
    override fun resetPosition(positionUs: Long, sampleStreamIsResetToKeyFrame: Boolean) =
        delegate.resetPosition(positionUs - delayUs, sampleStreamIsResetToKeyFrame)

    override fun supportsResetPositionWithoutKeyFrameReset(positionUs: Long): Boolean =
        delegate.supportsResetPositionWithoutKeyFrameReset(positionUs - delayUs)

    override fun setPlaybackSpeed(currentPlaybackSpeed: Float, targetPlaybackSpeed: Float) =
        delegate.setPlaybackSpeed(currentPlaybackSpeed, targetPlaybackSpeed)

    override fun enableMayRenderStartOfStream() = delegate.enableMayRenderStartOfStream()
    override fun setTimeline(timeline: Timeline) = delegate.setTimeline(timeline)
    override fun render(positionUs: Long, elapsedRealtimeUs: Long) =
        delegate.render(positionUs - delayUs, elapsedRealtimeUs)

    override fun isReady(): Boolean = delegate.isReady

    // With a positive delay the renderer lags the playhead; once its stream is fully read it must
    // not hold the player in READY at the end of the file.
    override fun isEnded(): Boolean =
        delegate.isEnded || (delayUs > 0 && delegate.hasReadStreamToEnd() && delegate.isCurrentStreamFinal)

    override fun stop() = delegate.stop()
    override fun disable() = delegate.disable()
    override fun reset() = delegate.reset()
    override fun release() = delegate.release()
    override fun handleMessage(messageType: Int, message: Any?) = delegate.handleMessage(messageType, message)
}
