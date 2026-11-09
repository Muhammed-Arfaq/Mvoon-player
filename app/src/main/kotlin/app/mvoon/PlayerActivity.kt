package app.mvoon

import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.session.MediaController
import app.mvoon.core.media.MediaKind
import app.mvoon.core.media.StreamType
import app.mvoon.engine.service.PlaybackService
import app.mvoon.ui.player.PlayerScreen
import app.mvoon.ui.player.PlayerViewModel
import app.mvoon.ui.theme.MvoonTheme
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Full-screen playback. A single instance on top of the library in the app's task (one card in
 * Recents); picture-in-picture moves it into a floating window while the library stays usable, and
 * "Open with Mvoon" from other apps lands straight in the player.
 */
class PlayerActivity : ComponentActivity() {

    private val vm: PlayerViewModel by viewModels()
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var inPip by mutableStateOf(false)
    private var videoRect: android.graphics.Rect? = null
    private var lastOrientation: Int? = null
    private val pipSupported by lazy { packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        window.attributes = window.attributes.apply {
            layoutInDisplayCutoutMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            } else {
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        hideSystemBars()
        if (savedInstanceState == null) handleIntent(intent)

        ContextCompat.registerReceiver(this, pipReceiver, IntentFilter(ACTION_PIP_CONTROL), ContextCompat.RECEIVER_NOT_EXPORTED)
        // System back / predictive back leaves the player the same way as the on-screen back button
        // (panels, lock and "move subtitles" register their own handlers in Compose, which take priority).
        onBackPressedDispatcher.addCallback(this) { exitPlayer() }

        setContent {
            val prefs by vm.preferences.collectAsStateWithLifecycle()
            MvoonTheme(prefs.appearance, forceDark = true) {
                PlayerScreen(
                    vm = vm,
                    isInPip = inPip,
                    onBack = ::exitPlayer,
                    onEnterPip = ::enterPip,
                    onVideoRect = ::onVideoRect,
                )
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.state.collect {
                    updatePipParams()
                    val orientation = vm.requestedOrientation()
                    if (orientation != null && orientation != lastOrientation && !inPip) {
                        lastOrientation = orientation
                        requestedOrientation = orientation
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        val coordinator = appContainer.playback
        if (intent.action in PLAY_ACTIONS) vm.forgetRestorePoint() else vm.restoreIfReleased()
        when (intent.action) {
            Intent.ACTION_VIEW -> intent.data?.let { coordinator.playUris(this, listOf(it), intent.type) }
            Intent.ACTION_SEND -> {
                val stream = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                when {
                    stream != null -> coordinator.playUris(this, listOf(stream), intent.type)
                    text != null -> URL_PATTERN.find(text)?.value?.let { coordinator.playUrl(this, it, StreamType.AUTO, null) }
                }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val uris = IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
                coordinator.playUris(this, uris, intent.type)
            }
        }
        // Opened from the notification after the process was killed: nothing to show.
        if (intent.action == null && appContainer.engine.state.value.queue.isEmpty() && !appContainer.playback.isPreparing.value) {
            finish()
        }
    }

    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent): Boolean {
        if (keyCode == android.view.KeyEvent.KEYCODE_VOLUME_UP || keyCode == android.view.KeyEvent.KEYCODE_VOLUME_DOWN) {
            val audioManager = getSystemService(android.media.AudioManager::class.java)
            if (vm.handleVolumeKey(up = keyCode == android.view.KeyEvent.KEYCODE_VOLUME_UP, audioManager)) return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onStart() {
        super.onStart()
        // Binding a controller creates the media session service (notification, headset & Bluetooth keys).
        controllerFuture = PlaybackService.connect(this)
        vm.engine.setVideoEnabled(true)
        // Back after a long pause: the service (and with it the player) may be gone; bring the file back.
        vm.restoreIfReleased()
    }

    override fun onStop() {
        super.onStop()
        controllerFuture?.let(PlaybackService::release)
        controllerFuture = null
        // A finishing player already stopped/handed off playback in exitPlayer(); its late onStop must
        // not touch the shared engine, which may already be playing something opened in a new player.
        if (isChangingConfigurations || isFinishing) return
        vm.persist()
        if (inPip) {
            // The PiP window was dismissed.
            closePlayback()
            return
        }
        val state = vm.state.value
        val item = state.currentItem ?: return
        if (item.kind == MediaKind.VIDEO && state.playWhenReady) {
            if (vm.preferences.value.playback.backgroundPlayback) {
                vm.engine.setVideoEnabled(false) // keep the sound, skip decoding pictures nobody sees
            } else {
                vm.engine.pause()
            }
        }
    }

    override fun onDestroy() {
        unregisterReceiver(pipReceiver)
        super.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    private fun hideSystemBars() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun exitPlayer() {
        vm.persist()
        val state = vm.state.value
        // Music keeps playing (mini player + notification); leaving a video stops it.
        if (state.currentItem?.kind != MediaKind.AUDIO || state.error != null) vm.engine.stop()
        finish()
    }

    private fun closePlayback() {
        vm.persist()
        if (vm.state.value.currentItem?.kind != MediaKind.AUDIO) vm.engine.stop() else vm.engine.pause()
        // Not finishAndRemoveTask(): on Android 12+ a dismissed PiP activity can already be back in the
        // app's task, and removing that would close the library too. An emptied PiP task goes by itself.
        finish()
    }

    // ------------------------------------------------------------ picture-in-picture

    private fun onVideoRect(rect: Rect) {
        val location = IntArray(2)
        window.decorView.getLocationInWindow(location)
        videoRect = android.graphics.Rect(
            rect.left.roundToInt().coerceAtLeast(0), rect.top.roundToInt().coerceAtLeast(0),
            rect.right.roundToInt(), rect.bottom.roundToInt(),
        )
        updatePipParams()
    }

    private fun canAutoPip(): Boolean {
        val s = vm.state.value
        // "About to play" counts too: leaving during the first second of a video should still PiP.
        return pipSupported && vm.preferences.value.playback.autoPictureInPicture &&
            s.currentItem?.kind == MediaKind.VIDEO && s.playWhenReady && s.error == null
    }

    private fun pipParams(): PictureInPictureParams {
        val s = vm.state.value
        val aspect = s.videoSize?.let { v ->
            val ratio = (v.aspectRatio).coerceIn(1f / 2.39f, 2.39f)
            Rational((ratio * 1000).roundToInt(), 1000)
        } ?: Rational(16, 9)
        val builder = PictureInPictureParams.Builder()
            .setAspectRatio(aspect)
            .setActions(pipActions(s.isPlaying))
        videoRect?.takeIf { it.width() > 0 && it.height() > 0 }?.let(builder::setSourceRectHint)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(canAutoPip())
            builder.setSeamlessResizeEnabled(false)
        }
        return builder.build()
    }

    private fun updatePipParams() {
        if (!pipSupported) return
        runCatching { setPictureInPictureParams(pipParams()) }
    }

    private fun enterPip() {
        if (!pipSupported || vm.state.value.currentItem?.kind != MediaKind.VIDEO) return
        vm.panel = null
        runCatching { enterPictureInPictureMode(pipParams()) }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // Android 12+ enters PiP automatically via setAutoEnterEnabled.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && canAutoPip()) enterPip()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPip = isInPictureInPictureMode
        if (isInPictureInPictureMode) {
            vm.panel = null
        } else if (lifecycle.currentState == Lifecycle.State.CREATED) {
            // Closed from the PiP window's ✕ (activity is no longer visible).
            closePlayback()
        } else {
            hideSystemBars()
        }
    }

    private fun pipActions(isPlaying: Boolean): List<RemoteAction> = listOf(
        remoteAction(CONTROL_REWIND, R.drawable.ic_pip_rewind, "Back 10 seconds"),
        if (isPlaying) remoteAction(CONTROL_PAUSE, R.drawable.ic_pip_pause, "Pause") else remoteAction(CONTROL_PLAY, R.drawable.ic_pip_play, "Play"),
        remoteAction(CONTROL_FORWARD, R.drawable.ic_pip_forward, "Forward 10 seconds"),
    )

    private fun remoteAction(control: Int, icon: Int, title: String) = RemoteAction(
        Icon.createWithResource(this, icon),
        title,
        title,
        PendingIntent.getBroadcast(
            this,
            control,
            Intent(ACTION_PIP_CONTROL).setPackage(packageName).putExtra(EXTRA_CONTROL, control),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        ),
    )

    private val pipReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val increment = vm.preferences.value.playback.seekIncrementMs
            when (intent.getIntExtra(EXTRA_CONTROL, -1)) {
                CONTROL_PLAY -> vm.engine.play()
                CONTROL_PAUSE -> vm.engine.pause()
                CONTROL_REWIND -> vm.engine.seekBy(-increment)
                CONTROL_FORWARD -> vm.engine.seekBy(increment)
            }
        }
    }

    private companion object {
        const val ACTION_PIP_CONTROL = "app.mvoon.PIP_CONTROL"
        const val EXTRA_CONTROL = "control"
        const val CONTROL_PLAY = 1
        const val CONTROL_PAUSE = 2
        const val CONTROL_REWIND = 3
        const val CONTROL_FORWARD = 4
        val URL_PATTERN = Regex("(https?|rtsp)://\\S+", RegexOption.IGNORE_CASE)
        val PLAY_ACTIONS = setOf(Intent.ACTION_VIEW, Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE)
    }
}
