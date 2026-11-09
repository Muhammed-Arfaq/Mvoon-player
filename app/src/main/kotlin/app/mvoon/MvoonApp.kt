package app.mvoon

import android.app.Application
import android.app.PendingIntent
import android.content.Intent
import app.mvoon.engine.AndroidPlayerEngine
import app.mvoon.engine.Media3PlayerEngine
import app.mvoon.engine.SubtitleFilePreparer
import app.mvoon.engine.service.EngineHost
import app.mvoon.library.LibraryRepository
import app.mvoon.library.db.MvoonDatabase
import app.mvoon.library.settings.SettingsRepository
import app.mvoon.playback.PlaybackCoordinator
import app.mvoon.playback.PlaybackRouter
import app.mvoon.ui.components.MediaThumbFetcher
import app.mvoon.ui.components.MediaThumbKeyer
import app.mvoon.vlc.VlcPlayerEngine
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.memory.MemoryCache
import coil3.request.crossfade
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Manual dependency container: everything is created lazily so cold start stays fast. */
class AppContainer(private val app: MvoonApp) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val database by lazy { MvoonDatabase.create(app) }
    val settings by lazy { SettingsRepository(app) }
    val library by lazy { LibraryRepository(app, database, scope) }
    val engine by lazy { PlaybackRouter(Media3PlayerEngine(app), { VlcPlayerEngine(app) }, scope) }
    val subtitlePreparer by lazy { SubtitleFilePreparer(app) }
    val playback by lazy { PlaybackCoordinator(app, engine, library, settings, subtitlePreparer, scope) }
}

class MvoonApp : Application(), EngineHost, SingletonImageLoader.Factory {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.playback.start()
    }

    override val playerEngine: AndroidPlayerEngine get() = container.engine

    override val notificationIconRes: Int = R.drawable.ic_notification

    override fun sessionActivityIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, PlayerActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    override fun newImageLoader(context: PlatformContext): ImageLoader = ImageLoader.Builder(context)
        .components {
            add(MediaThumbKeyer())
            add(MediaThumbFetcher.Factory(this@MvoonApp))
        }
        .memoryCache { MemoryCache.Builder().maxSizePercent(context, 0.2).build() }
        .crossfade(180)
        .build()
}

val android.content.Context.appContainer: AppContainer
    get() = (applicationContext as MvoonApp).container
