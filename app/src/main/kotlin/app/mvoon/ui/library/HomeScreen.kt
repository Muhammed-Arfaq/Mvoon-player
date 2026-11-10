package app.mvoon.ui.library

import com.composables.icons.lucide.History
import androidx.compose.animation.SizeTransform
import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.mvoon.core.media.MediaKind
import app.mvoon.core.media.PlayableItem
import app.mvoon.core.settings.LibraryLayout
import app.mvoon.core.util.formatDuration
import app.mvoon.library.db.LibraryRow
import app.mvoon.library.db.PlaybackStateEntity
import app.mvoon.ui.components.EmptyState
import app.mvoon.ui.components.FolderTab
import app.mvoon.ui.components.FolderTabs
import app.mvoon.ui.components.IconTile
import app.mvoon.ui.components.MediaThumbnail
import app.mvoon.ui.components.NavItem
import app.mvoon.ui.components.NotchedNavBar
import app.mvoon.ui.components.PageHeader
import app.mvoon.ui.components.RingStat
import app.mvoon.ui.components.RowSkeleton
import app.mvoon.ui.components.SectionLabel
import app.mvoon.ui.components.SkeletonBox
import app.mvoon.ui.components.SkeletonLine
import app.mvoon.ui.components.SlidingChips
import app.mvoon.ui.components.SoftIconButton
import app.mvoon.ui.components.StaggerState
import app.mvoon.ui.components.VideoCardSkeleton
import app.mvoon.ui.components.folderCardShape
import app.mvoon.ui.components.rememberStaggerState
import app.mvoon.ui.components.staggeredEntrance
import app.mvoon.ui.theme.LocalMvoonExtras
import app.mvoon.ui.theme.Brand
import app.mvoon.ui.theme.MvoonMark
import app.mvoon.ui.theme.MvoonWordmark
import app.mvoon.ui.theme.TabularNumbers
import com.composables.icons.lucide.ArrowDownWideNarrow
import com.composables.icons.lucide.Captions
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.CircleCheck
import com.composables.icons.lucide.Film
import com.composables.icons.lucide.Folder
import com.composables.icons.lucide.FolderPlus
import com.composables.icons.lucide.House
import com.composables.icons.lucide.LayoutGrid
import com.composables.icons.lucide.ListMusic
import com.composables.icons.lucide.Lock
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Music
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.Rows3
import com.composables.icons.lucide.Search
import com.composables.icons.lucide.Settings
import com.composables.icons.lucide.Shield
import com.composables.icons.lucide.Shuffle
import com.composables.icons.lucide.X
import kotlinx.coroutines.delay
import java.util.Calendar
import kotlin.math.roundToInt

class HomeCallbacks(
    val openFolder: (String) -> Unit,
    val openPlaylist: (Long) -> Unit,
    val openSearch: () -> Unit,
    val openSettings: () -> Unit,
    val requestAccess: () -> Unit,
    val pickFiles: () -> Unit,
    val pickFolder: () -> Unit,
)

private val NavItems = listOf(
    NavItem("Home", Lucide.House),
    NavItem("Videos", Lucide.Film),
    NavItem("Music", Lucide.Music),
    NavItem("Folders", Lucide.Folder),
    NavItem("Playlists", Lucide.ListMusic),
)

/** Header actions shared by every tab. */
private class HeaderActions(val onSearch: () -> Unit, val onAdd: () -> Unit, val onSettings: () -> Unit)

@Composable
fun HomeScreen(vm: LibraryViewModel, callbacks: HomeCallbacks) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val prefs by vm.preferences.collectAsStateWithLifecycle()
    val scanning by vm.isScanning.collectAsStateWithLifecycle()
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    val history by vm.repo.recent.collectAsStateWithLifecycle(emptyList())
    val lastVideo by vm.lastVideo.collectAsStateWithLifecycle()
    val nowPlaying = vm.playerState.collectAsStateWithLifecycle().value.currentItem

    var actionRow by remember { mutableStateOf<LibraryRow?>(null) }
    val deleteMedia = rememberDeleteMedia(onDeleted = vm::onDeleted)
    var playlistTarget by remember { mutableStateOf<List<PlayableItem>?>(null) }
    var showSort by remember { mutableStateOf(false) }
    var showAdd by remember { mutableStateOf(false) }
    var showUrl by remember { mutableStateOf(false) }
    var bottomBarHeight by remember { mutableStateOf(110.dp) }
    val actions = remember(callbacks) { HeaderActions(callbacks.openSearch, { showAdd = true }, callbacks.openSettings) }

    // Surface supplies the theme's content colour (text and icons) to everything below.
    Surface(color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onBackground) {
    Box(Modifier.fillMaxSize()) {
        val bottomPad = bottomBarHeight + 16.dp
        PullToRefreshBox(isRefreshing = scanning && vm.tab != LibraryTab.HOME, onRefresh = { vm.refresh() }, modifier = Modifier.fillMaxSize()) {
            AnimatedContent(
                targetState = vm.tab,
                transitionSpec = {
                    val forward = targetState.ordinal > initialState.ordinal
                    (slideInHorizontally(spring(dampingRatio = 0.9f, stiffness = 420f)) { w -> if (forward) w / 5 else -w / 5 } + fadeIn(tween(240))) togetherWith
                        (slideOutHorizontally(tween(220)) { w -> if (forward) -w / 10 else w / 10 } + fadeOut(tween(150)))
                },
                label = "tabs",
            ) { tab ->
                when (tab) {
                    LibraryTab.HOME -> HomeTab(vm, callbacks, actions, bottomPad, onMore = { actionRow = it }, openTab = { vm.tab = it })
                    LibraryTab.VIDEOS -> VideosTab(vm, callbacks, actions, bottomPad, onMore = { actionRow = it }, onSort = { showSort = true })
                    LibraryTab.MUSIC -> MusicTab(vm, callbacks, actions, bottomPad, onMore = { actionRow = it }, onSort = { showSort = true })
                    LibraryTab.FOLDERS -> FoldersTab(vm, callbacks, actions, bottomPad)
                    LibraryTab.PLAYLISTS -> PlaylistsTab(vm, callbacks, actions, bottomPad)
                }
            }
        }
        Column(Modifier.align(Alignment.BottomCenter).onSizeChanged { bottomBarHeight = with(density) { it.height.toDp() } }) {
            // Video tabs only, and not while something plays (the mini player is there then).
            val videoTab = vm.tab == LibraryTab.HOME || vm.tab == LibraryTab.VIDEOS || vm.tab == LibraryTab.FOLDERS
            val showPlayLast = lastVideo != null && nowPlaying == null && videoTab
            AnimatedVisibility(
                showPlayLast,
                modifier = Modifier.align(Alignment.End),
                enter = scaleIn(spring(dampingRatio = 0.6f, stiffness = 500f)) + fadeIn(),
                exit = scaleOut() + fadeOut(),
            ) {
                lastVideo?.let { entry -> PlayLastButton(entry, onClick = { vm.playLastVideo(context) }) }
            }
            MiniPlayer(vm)
            NotchedNavBar(NavItems, selected = vm.tab.ordinal, onSelect = { vm.tab = LibraryTab.entries[it] })
        }
    }
    }

    actionRow?.let { row ->
        ItemActionsSheet(
            row = row,
            onDismiss = { actionRow = null },
            onPlay = { vm.play(context, listOf(row), 0) },
            onPlayFromStart = { vm.forget(row.media.id); vm.play(context, listOf(row), 0) },
            onFavorite = { vm.setFavorite(row.media.id, it) },
            onWatched = { vm.setWatched(row.media.id, it) },
            onAddToPlaylist = { playlistTarget = listOf(row.toPlayable()); actionRow = null },
            onHideFolder = { vm.hideFolder(row.media.folderKey) },
            onDelete = { deleteMedia(row.toDeletable()) },
        )
    }
    playlistTarget?.let { items ->
        AddToPlaylistDialog(
            items, playlists,
            onDismiss = { playlistTarget = null },
            onAdd = { id -> vm.addToPlaylist(id, items); playlistTarget = null },
            onCreate = { name -> vm.createPlaylist(name, items); playlistTarget = null },
        )
    }
    if (showSort) {
        val music = vm.tab == LibraryTab.MUSIC
        SortSheet(
            current = if (music) prefs.library.audioSort else prefs.library.videoSort,
            onDismiss = { showSort = false },
            onSelect = { if (music) vm.setAudioSort(it) else vm.setVideoSort(it) },
        )
    }
    if (showAdd) AddSourceSheet({ showAdd = false }, callbacks.pickFiles, callbacks.pickFolder, { showUrl = true })
    if (showUrl) {
        OpenUrlDialog(
            recent = history.filter { it.isStream }.map { it.uri }.distinct(),
            onDismiss = { showUrl = false },
            onPlay = { url, type, title -> showUrl = false; vm.playUrl(context, url, type, title) },
        )
    }
}

// ---------------------------------------------------------------- shared pieces

/** Dark (true) or light (false) status-bar icons for what is currently under the status bar. */
@Composable
private fun StatusBarIcons(dark: Boolean) {
    val view = LocalView.current
    DisposableEffect(dark) {
        val window = (view.context as? ComponentActivity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val previous = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = dark
        onDispose { if (previous != null) controller.isAppearanceLightStatusBars = previous }
    }
}

/** Solid band behind the status bar once content scrolls under it. */
@Composable
private fun StatusBarScrim(visible: Boolean) {
    val color by animateColorAsState(
        if (visible) MaterialTheme.colorScheme.background.copy(alpha = 0.97f) else Color.Transparent,
        label = "statusScrim",
    )
    Box(Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars).background(color))
}

@Composable
private fun HeaderButtons(actions: HeaderActions) {
    SoftIconButton(Lucide.Search, "Search", actions.onSearch)
    SoftIconButton(Lucide.Plus, "Open file, folder or stream", actions.onAdd)
    SoftIconButton(Lucide.Settings, "Settings", actions.onSettings)
}

private fun LazyGridScope.fullWidth(key: String, content: @Composable () -> Unit) =
    item(key = key, span = { GridItemSpan(maxLineSpan) }, contentType = key) { content() }

/** Lets a full-width row bleed past the grid's side padding (chips scroll to the screen edge). */
private fun Modifier.bleed(horizontal: Dp): Modifier = layout { measurable, constraints ->
    val px = horizontal.roundToPx()
    val placeable = measurable.measure(constraints.copy(minWidth = 0, maxWidth = constraints.maxWidth + px * 2))
    layout(constraints.maxWidth, placeable.height) { placeable.place(-px, 0) }
}

private fun groupShape(index: Int, size: Int): RoundedCornerShape {
    val top = if (index == 0) 26.dp else 6.dp
    val bottom = if (index == size - 1) 26.dp else 6.dp
    return RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
}

private fun compactCount(n: Int): String = if (n >= 10_000) "${n / 1000}k" else n.toString()

/** "38 hours", "2.5 hours", "12 minutes". */
private fun amountOf(ms: Long): String {
    val h = ms / 3_600_000.0
    return when {
        h >= 10 -> "${h.roundToInt()} hours"
        h >= 1 -> "${"%.1f".format(h)} hours"
        else -> (ms / 60_000).coerceAtLeast(if (ms > 0) 1 else 0).let { m -> "$m minute${if (m == 1L) "" else "s"}" }
    }
}

private fun greeting(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in 5..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    in 17..21 -> "Good evening"
    else -> "Up late?"
}

// ---------------------------------------------------------------- home dashboard

@Composable
private fun HomeTab(
    vm: LibraryViewModel,
    callbacks: HomeCallbacks,
    actions: HeaderActions,
    bottomPad: Dp,
    onMore: (LibraryRow) -> Unit,
    openTab: (LibraryTab) -> Unit,
) {
    val context = LocalContext.current
    val dashboard by vm.dashboard.collectAsStateWithLifecycle()
    val scanning by vm.isScanning.collectAsStateWithLifecycle()
    val playerState by vm.playerState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val stagger = rememberStaggerState()
    val heroVisible by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset < 220 } }
    // Light icons over the navy hero; dark ones once light content scrolls under the status bar.
    StatusBarIcons(dark = !heroVisible && !LocalMvoonExtras.current.isDark)
    val heroCallbacks = remember(vm) {
        HeroCallbacks(
            playVideo = { row -> vm.play(context, listOf(row), 0) },
            playSong = { rows, index -> vm.play(context, rows, index) },
            browse = { tab, recent ->
                if (tab == LibraryTab.VIDEOS) vm.videoFilter = if (recent) LibraryFilter.RECENT else LibraryFilter.ALL
                else vm.musicFilter = if (recent) LibraryFilter.RECENT else LibraryFilter.ALL
                openTab(tab)
            },
        )
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(state = listState, contentPadding = PaddingValues(bottom = bottomPad), modifier = Modifier.fillMaxSize()) {
            item("hero") { HomeHero(dashboard, scanning, playerState.currentItem?.id, actions, heroCallbacks) }
            if (vm.access != AccessLevel.FULL) item("access") { AccessBanner(vm.access, callbacks.requestAccess, callbacks.pickFolder, Modifier.padding(16.dp)) }
            val d = dashboard
            if (d == null) {
                item("skeleton") { HomeSkeleton() }
                return@LazyColumn
            }
            if (d.recentlyAdded.isNotEmpty()) {
                item("added-label") {
                    SectionLabel("Recently added", Modifier.padding(top = 10.dp), count = d.videos, actionLabel = "See all", onAction = { vm.videoFilter = LibraryFilter.ALL; openTab(LibraryTab.VIDEOS) })
                }
                itemsIndexed(d.recentlyAdded, key = { _, r -> "added:${r.media.id}" }) { index, row ->
                    RecentRow(
                        row,
                        shape = groupShape(index, d.recentlyAdded.size),
                        onClick = { vm.play(context, listOf(row), 0) },
                        onMore = { onMore(row) },
                        modifier = Modifier.animateItem().staggeredEntrance(stagger, "added:${row.media.id}", index),
                    )
                }
            }
            if (d.videos == 0 && d.songs == 0) {
                item("empty") {
                    if (scanning) {
                        HomeSkeleton()
                    } else {
                        EmptyState(
                            "Your library is empty",
                            if (vm.access == AccessLevel.NONE) "Allow access to your videos and music, add a folder, or open a file to get started."
                            else "Videos and music on this phone will appear here automatically.",
                        ) {
                            Button(onClick = callbacks.pickFolder) { Icon(Lucide.FolderPlus, null, Modifier.size(18.dp)); Text("  Add a folder") }
                        }
                    }
                }
            }
        }
        StatusBarScrim(visible = !heroVisible)
    }
}

/** What the hero card's shelves open when tapped. */
private class HeroCallbacks(
    val playVideo: (LibraryRow) -> Unit,
    val playSong: (List<LibraryRow>, Int) -> Unit,
    val browse: (tab: LibraryTab, recent: Boolean) -> Unit,
)

@Composable
private fun HomeHero(d: Dashboard?, scanning: Boolean, nowPlayingId: String?, actions: HeaderActions, hero: HeroCallbacks) {
    val extras = LocalMvoonExtras.current
    val onHero = extras.onHero
    val density = LocalDensity.current
    var shelf by rememberSaveable { mutableIntStateOf(0) }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 36.dp, bottomEnd = 36.dp))
            .background(extras.hero)
            .statusBarsPadding()
            .padding(bottom = 22.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            MvoonMark(30.dp)
            Spacer(Modifier.width(10.dp))
            // The wordmark is a logo: fixed physical size, so large system fonts can't push the buttons off-screen.
            MvoonWordmark(fontSize = with(density) { 25.dp.toSp() }, color = onHero, modifier = Modifier.weight(1f))
            val chip = onHero.copy(alpha = 0.12f)
            SoftIconButton(Lucide.Search, "Search", actions.onSearch, container = chip, tint = onHero)
            SoftIconButton(Lucide.Plus, "Open file, folder or stream", actions.onAdd, container = chip, tint = onHero)
            SoftIconButton(Lucide.Settings, "Settings", actions.onSettings, container = chip, tint = onHero)
        }
        AnimatedVisibility(scanning) {
            LinearProgressIndicator(
                Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 6.dp).height(3.dp).clip(CircleShape),
                color = onHero,
                trackColor = onHero.copy(alpha = 0.15f),
            )
        }
        Text(greeting(), style = MaterialTheme.typography.titleMedium, color = onHero.copy(alpha = 0.7f), modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 12.dp))
        Text("What's playing today?", style = MaterialTheme.typography.headlineMedium, color = onHero, modifier = Modifier.padding(start = 22.dp, end = 22.dp))
        Spacer(Modifier.height(18.dp))
        Column(Modifier.padding(horizontal = 16.dp)) {
            FolderTabs(
                tabs = listOf(FolderTab("Watching", d?.videos?.let(::compactCount)), FolderTab("Listening", d?.songs?.let(::compactCount))),
                selected = shelf,
                onSelect = { shelf = it },
                cardColor = extras.card,
                mutedColor = onHero.copy(alpha = 0.6f),
            )
            // No side padding: the carousel runs to the card's edges and is clipped by its corners.
            Column(
                Modifier.fillMaxWidth().clip(folderCardShape(shelf, 2)).background(extras.card).padding(top = 6.dp, bottom = 16.dp),
            ) {
                if (d == null) {
                    SkeletonLine(140.dp, Modifier.padding(start = 16.dp, top = 12.dp, bottom = 14.dp))
                    RecentCarouselSkeleton(if (shelf == 0) MediaKind.VIDEO else MediaKind.AUDIO)
                } else {
                    AnimatedContent(
                        shelf,
                        transitionSpec = { (fadeIn(tween(220, delayMillis = 60)) togetherWith fadeOut(tween(120))) using SizeTransform(clip = false) },
                        label = "shelf",
                    ) { tab ->
                        if (tab == 0) {
                            RecentShelf(
                                summary = "${d.videos} video${if (d.videos == 1) "" else "s"} · ${amountOf(d.videoMs)}",
                                items = d.recentlyWatched,
                                kind = MediaKind.VIDEO,
                                nowPlayingId = nowPlayingId,
                                emptyTitle = "Nothing watched yet",
                                emptyBody = "Videos you watch will show up here, newest first, so you can jump straight back in.",
                                browseLabel = "Browse videos",
                                onItem = { _, row -> hero.playVideo(row) },
                                onSeeAll = { hero.browse(LibraryTab.VIDEOS, true) },
                                onBrowse = { hero.browse(LibraryTab.VIDEOS, false) },
                            )
                        } else {
                            RecentShelf(
                                summary = "${d.songs} song${if (d.songs == 1) "" else "s"} · ${amountOf(d.musicMs)}",
                                items = d.recentMusic,
                                kind = MediaKind.AUDIO,
                                nowPlayingId = nowPlayingId,
                                emptyTitle = "No songs played yet",
                                emptyBody = "Songs you listen to will show up here, newest first.",
                                browseLabel = "Browse music",
                                onItem = { index, _ -> hero.playSong(d.recentMusic, index) },
                                onSeeAll = { hero.browse(LibraryTab.MUSIC, true) },
                                onBrowse = { hero.browse(LibraryTab.MUSIC, false) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** The hero card's content: a carousel of recently played items, or what will appear here. */
@Composable
private fun RecentShelf(
    summary: String,
    items: List<LibraryRow>,
    kind: MediaKind,
    nowPlayingId: String?,
    emptyTitle: String,
    emptyBody: String,
    browseLabel: String,
    onItem: (Int, LibraryRow) -> Unit,
    onSeeAll: () -> Unit,
    onBrowse: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 16.dp, end = 6.dp).height(48.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (items.isEmpty()) summary else "Recently ${if (kind == MediaKind.VIDEO) "watched" else "played"}",
                style = MaterialTheme.typography.labelLarge,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (items.isNotEmpty()) {
                TextButton(onClick = onSeeAll, contentPadding = PaddingValues(horizontal = 10.dp)) { Text("See all") }
            }
        }
        if (items.isEmpty()) {
            Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                IconTile(if (kind == MediaKind.VIDEO) Lucide.History else Lucide.Music, container = scheme.primaryContainer, tint = scheme.onPrimaryContainer)
                Column(Modifier.weight(1f)) {
                    Text(emptyTitle, style = MaterialTheme.typography.titleSmall)
                    Text(emptyBody, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                    Button(onClick = onBrowse, modifier = Modifier.padding(top = 10.dp), contentPadding = PaddingValues(horizontal = 16.dp)) {
                        Text(browseLabel)
                    }
                }
            }
        } else {
            RecentCarousel(items, kind, nowPlayingId, onItem)
        }
    }
}

@Composable
private fun RecentRow(row: LibraryRow, shape: RoundedCornerShape, onClick: () -> Unit, onMore: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier
            .padding(horizontal = 16.dp, vertical = 1.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(LocalMvoonExtras.current.card)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.width(72.dp).height(46.dp).clip(RoundedCornerShape(14.dp))) {
            MediaThumbnail(row.media.uri, MediaKind.VIDEO, Modifier.fillMaxSize(), iconSize = 18.dp, seed = row.media.folderName + row.media.title)
        }
        Column(Modifier.weight(1f)) {
            Text(row.media.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(row.media.folderName, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (row.durationMs > 0) Text(formatDuration(row.durationMs), style = MaterialTheme.typography.titleSmall.merge(TabularNumbers))
        IconButton(onClick = onMore, modifier = Modifier.size(32.dp)) { Icon(Lucide.ChevronRight, "More options for ${row.media.title}", tint = scheme.onSurfaceVariant, modifier = Modifier.size(18.dp)) }
    }
}

@Composable
private fun HomeSkeleton() {
    Column(Modifier.fillMaxWidth().padding(top = 18.dp)) {
        SkeletonLine(150.dp, Modifier.padding(start = 22.dp, bottom = 14.dp))
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(2) { SkeletonBox(Modifier.width(232.dp).height(200.dp), RoundedCornerShape(28.dp)) }
        }
        SkeletonLine(120.dp, Modifier.padding(start = 22.dp, top = 24.dp, bottom = 8.dp))
        repeat(4) { RowSkeleton(square = false) }
    }
}

// ---------------------------------------------------------------- videos

@Composable
private fun VideosTab(vm: LibraryViewModel, callbacks: HomeCallbacks, actions: HeaderActions, bottomPad: Dp, onMore: (LibraryRow) -> Unit, onSort: () -> Unit) {
    val context = LocalContext.current
    val videos by vm.videos.collectAsStateWithLifecycle()
    val loaded by vm.loaded.collectAsStateWithLifecycle()
    val scanning by vm.isScanning.collectAsStateWithLifecycle()
    val prefs by vm.preferences.collectAsStateWithLifecycle()
    val grid = prefs.library.layout == LibraryLayout.GRID
    val stagger = rememberStaggerState()
    val loading = !loaded || (scanning && videos.isEmpty() && vm.videoFilter == LibraryFilter.ALL)
    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = if (grid) GridCells.Adaptive(150.dp) else GridCells.Adaptive(380.dp),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding(), bottom = bottomPad),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(if (grid) 12.dp else 4.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            fullWidth("header") { PageHeader("Videos", Modifier.bleed(14.dp), count = videos.size.takeIf { !loading }) { HeaderButtons(actions) } }
            if (vm.access != AccessLevel.FULL) fullWidth("access") { AccessBanner(vm.access, callbacks.requestAccess, callbacks.pickFolder) }
            fullWidth("filters") {
                Column {
                    SlidingChips(LibraryFilter.entries, vm.videoFilter, { it.label }, { vm.videoFilter = it }, Modifier.bleed(14.dp))
                    Row(Modifier.fillMaxWidth().padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = onSort) { Icon(Lucide.ArrowDownWideNarrow, "Sort", Modifier.size(20.dp)) }
                        IconButton(onClick = vm::toggleLayout) {
                            Icon(if (grid) Lucide.Rows3 else Lucide.LayoutGrid, if (grid) "Show as list" else "Show as grid", Modifier.size(20.dp))
                        }
                    }
                }
            }
            when {
                loading -> items(6, key = { "skeleton$it" }) { if (grid) VideoCardSkeleton() else RowSkeleton(square = false) }
                videos.isEmpty() -> fullWidth("empty") {
                    if (vm.videoFilter == LibraryFilter.ALL) {
                        EmptyState(
                            "No videos yet",
                            if (vm.access == AccessLevel.NONE) "Allow access to your videos, add a folder, or open a file to get started."
                            else "Videos you download or record will appear here automatically.",
                        ) { Button(onClick = callbacks.pickFolder) { Icon(Lucide.FolderPlus, null, Modifier.size(18.dp)); Text("  Add a folder") } }
                    } else {
                        EmptyState("Nothing here", "No videos match \"${vm.videoFilter.label}\".")
                    }
                }
                else -> itemsIndexed(videos, key = { _, r -> r.media.id }, contentType = { _, _ -> if (grid) "card" else "row" }) { index, row ->
                    val onClick = {
                        val queue = folderQueue(videos, row)
                        vm.play(context, queue, queue.indexOf(row).coerceAtLeast(0))
                    }
                    val itemModifier = Modifier.animateItem().staggeredEntrance(stagger, row.media.id, index)
                    if (grid) VideoCard(row, onClick = onClick, onLongClick = { onMore(row) }, modifier = itemModifier)
                    else MediaRow(row, onClick = onClick, onMore = { onMore(row) }, modifier = itemModifier)
                }
            }
        }
        StatusBarScrim(visible = true)
    }
}

/** Playing a video queues the rest of its folder in natural order (episodes play on). */
private fun folderQueue(all: List<LibraryRow>, row: LibraryRow): List<LibraryRow> =
    all.filter { it.media.folderKey == row.media.folderKey }.sortedWith(NaturalOrder)

private val NaturalOrder = Comparator<LibraryRow> { a, b -> naturalCompare(a.media.title, b.media.title) }

/** "Episode 2" sorts before "Episode 10". */
fun naturalCompare(a: String, b: String): Int {
    val ra = Regex("\\d+|\\D+").findAll(a.lowercase()).map { it.value }.toList()
    val rb = Regex("\\d+|\\D+").findAll(b.lowercase()).map { it.value }.toList()
    for (i in 0 until minOf(ra.size, rb.size)) {
        val x = ra[i]
        val y = rb[i]
        val cmp = if (x[0].isDigit() && y[0].isDigit()) {
            x.trimStart('0').length.compareTo(y.trimStart('0').length).takeIf { it != 0 } ?: x.trimStart('0').compareTo(y.trimStart('0'))
        } else {
            x.compareTo(y)
        }
        if (cmp != 0) return cmp
    }
    return ra.size.compareTo(rb.size)
}

// ---------------------------------------------------------------- music

private val MusicFilters = listOf(LibraryFilter.ALL, LibraryFilter.FAVORITES, LibraryFilter.RECENT)

@Composable
private fun MusicTab(vm: LibraryViewModel, callbacks: HomeCallbacks, actions: HeaderActions, bottomPad: Dp, onMore: (LibraryRow) -> Unit, onSort: () -> Unit) {
    val context = LocalContext.current
    val music by vm.music.collectAsStateWithLifecycle()
    val loaded by vm.loaded.collectAsStateWithLifecycle()
    val scanning by vm.isScanning.collectAsStateWithLifecycle()
    val dashboard by vm.dashboard.collectAsStateWithLifecycle()
    val playerState by vm.playerState.collectAsStateWithLifecycle()
    val extras = LocalMvoonExtras.current
    val stagger = rememberStaggerState()
    val loading = !loaded || (scanning && music.isEmpty() && vm.musicFilter == LibraryFilter.ALL)
    val selected = MusicFilters.indexOf(vm.musicFilter).coerceAtLeast(0)
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding(), bottom = bottomPad),
            modifier = Modifier.fillMaxSize(),
        ) {
            item("header") { PageHeader("Music", count = dashboard?.songs) { HeaderButtons(actions) } }
            if (vm.access != AccessLevel.FULL) item("access") { AccessBanner(vm.access, callbacks.requestAccess, callbacks.pickFolder, Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) }
            item("tabs") {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    FolderTabs(
                        tabs = listOf(
                            FolderTab("Songs", dashboard?.songs?.toString()),
                            FolderTab("Loved", dashboard?.favouriteSongs?.toString()),
                            FolderTab("Recent"),
                        ),
                        selected = selected,
                        onSelect = { vm.musicFilter = MusicFilters[it] },
                        cardColor = extras.card,
                    )
                    Column(Modifier.fillMaxWidth().clip(folderCardShape(selected, 3)).background(extras.card).padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("${music.size} track${if (music.size == 1) "" else "s"}", style = MaterialTheme.typography.titleMedium, maxLines = 1)
                                Text(
                                    "${amountOf(music.sumOf { it.durationMs })} · ${vm.musicFilter.label.lowercase()}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            IconButton(onClick = onSort) { Icon(Lucide.ArrowDownWideNarrow, "Sort", Modifier.size(20.dp)) }
                        }
                        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(onClick = { vm.play(context, music, 0) }, enabled = music.isNotEmpty(), modifier = Modifier.weight(1f)) {
                                Icon(Icons.Rounded.PlayArrow, null, Modifier.size(20.dp))
                                Text(" Play all", maxLines = 1)
                            }
                            Button(
                                onClick = { vm.play(context, music, 0, shuffle = true) },
                                enabled = music.isNotEmpty(),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary, contentColor = MaterialTheme.colorScheme.onSecondary),
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(Lucide.Shuffle, null, Modifier.size(18.dp))
                                Text(" Shuffle", maxLines = 1)
                            }
                        }
                    }
                }
            }
            when {
                loading -> items(8, key = { "skeleton$it" }) { RowSkeleton() }
                music.isEmpty() -> item("empty") {
                    EmptyState(
                        if (vm.musicFilter == LibraryFilter.ALL) "No music yet" else "Nothing here",
                        if (vm.musicFilter == LibraryFilter.ALL) "Songs and audio files on this device will appear here." else "No songs in \"${vm.musicFilter.label}\" yet.",
                    )
                }
                else -> itemsIndexed(music, key = { _, r -> r.media.id }, contentType = { _, _ -> "row" }) { index, row ->
                    MediaRow(
                        row,
                        onClick = { vm.play(context, music, index) },
                        onMore = { onMore(row) },
                        playing = playerState.currentItem?.id == row.media.id,
                        modifier = Modifier.padding(horizontal = 6.dp).animateItem().staggeredEntrance(stagger, row.media.id, index),
                    )
                }
            }
        }
        StatusBarScrim(visible = true)
    }
}

// ---------------------------------------------------------------- folders

@Composable
private fun FoldersTab(vm: LibraryViewModel, callbacks: HomeCallbacks, actions: HeaderActions, bottomPad: Dp) {
    val folders by vm.folders.collectAsStateWithLifecycle()
    val loaded by vm.loaded.collectAsStateWithLifecycle()
    val extras = LocalMvoonExtras.current
    val stagger = rememberStaggerState()
    var hideCandidate by remember { mutableStateOf<String?>(null) }
    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(340.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding(), bottom = bottomPad),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            fullWidth("header") { PageHeader("Folders", Modifier.bleed(16.dp), count = folders.size.takeIf { loaded }) { HeaderButtons(actions) } }
            fullWidth("add") {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(extras.contrastCard).clickable(onClick = callbacks.pickFolder).padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    IconTile(Lucide.FolderPlus, container = Color.White.copy(alpha = 0.1f), tint = extras.contrastAccent)
                    Column(Modifier.weight(1f)) {
                        Text("Add a folder", style = MaterialTheme.typography.titleMedium, color = extras.contrastAccent)
                        Text("SD cards, USB drives, Downloads — and sidecar subtitles", style = MaterialTheme.typography.bodySmall, color = extras.onContrastCard.copy(alpha = 0.7f))
                    }
                    Icon(Lucide.ChevronRight, null, tint = extras.onContrastCard.copy(alpha = 0.6f), modifier = Modifier.size(20.dp))
                }
            }
            when {
                !loaded -> items(5, key = { "skeleton$it" }) { RowSkeleton() }
                folders.isEmpty() -> fullWidth("empty") { EmptyState("No folders", "Folders that contain videos or music show up here.") }
                else -> itemsIndexed(folders, key = { _, f -> f.folderKey }) { index, folder ->
                    Box(Modifier.animateItem().staggeredEntrance(stagger, folder.folderKey, index)) {
                        FolderCard(folder, onClick = { callbacks.openFolder(folder.folderKey) }, onLongClick = { hideCandidate = folder.folderKey })
                    }
                }
            }
        }
        StatusBarScrim(visible = true)
    }
    hideCandidate?.let { key ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { hideCandidate = null },
            title = { Text("Hide this folder?") },
            text = { Text("Its files won't appear in Videos, Music or search. You can show it again from Settings → Library.") },
            confirmButton = { TextButton(onClick = { vm.hideFolder(key); hideCandidate = null }) { Text("Hide") } },
            dismissButton = { TextButton(onClick = { hideCandidate = null }) { Text("Cancel") } },
        )
    }
}

// ---------------------------------------------------------------- playlists

@Composable
private fun PlaylistsTab(vm: LibraryViewModel, callbacks: HomeCallbacks, actions: HeaderActions, bottomPad: Dp) {
    val context = LocalContext.current
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    val stagger = rememberStaggerState()
    var creating by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(156.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding(), bottom = bottomPad),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            fullWidth("header") {
                PageHeader("Playlists", Modifier.bleed(16.dp), count = playlists.size) {
                    Button(onClick = { creating = true }, contentPadding = PaddingValues(horizontal = 14.dp), modifier = Modifier.padding(end = 6.dp)) {
                        Icon(Lucide.Plus, null, Modifier.size(18.dp))
                        Text(" New")
                    }
                    SoftIconButton(Lucide.Settings, "Settings", actions.onSettings)
                }
            }
            if (playlists.isEmpty()) {
                fullWidth("empty") { EmptyState("No playlists yet", "Long-press any video or song and choose \"Add to playlist\".") }
            }
            itemsIndexed(playlists, key = { _, p -> p.playlist.id }) { index, p ->
                PlaylistCard(
                    p,
                    onClick = { callbacks.openPlaylist(p.playlist.id) },
                    onPlay = { vm.playPlaylist(context, p.playlist.id) },
                    modifier = Modifier.animateItem().staggeredEntrance(stagger, p.playlist.id, index),
                )
            }
        }
        StatusBarScrim(visible = true)
    }
    if (creating) {
        AddToPlaylistDialog(emptyList(), emptyList(), onDismiss = { creating = false }, onAdd = {}, onCreate = { vm.createPlaylist(it, emptyList()); creating = false })
    }
}

@Composable
private fun AccessBanner(level: AccessLevel, onAllow: () -> Unit, onFolder: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(scheme.primaryContainer).padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        IconTile(Lucide.Lock, container = scheme.primary, tint = scheme.onPrimary)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                if (level == AccessLevel.PARTIAL) "Mvoon can see only the videos you selected" else "Let Mvoon find your videos and music",
                style = MaterialTheme.typography.titleMedium,
                color = scheme.onPrimaryContainer,
            )
            Text(
                "Access is used only to build your library on this device. You can also add individual folders instead.",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onPrimaryContainer.copy(alpha = 0.8f),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onAllow,
                    colors = ButtonDefaults.buttonColors(containerColor = scheme.secondary, contentColor = scheme.onSecondary),
                ) { Text(if (level == AccessLevel.PARTIAL) "Change access" else "Allow access") }
                TextButton(onClick = onFolder) { Text("Add a folder", color = scheme.onPrimaryContainer) }
            }
        }
    }
}

// ---------------------------------------------------------------- play last video

/**
 * Round play button, as in MX Player: continues the video watched last. The ring shows how far into
 * it you got.
 */
@Composable
private fun PlayLastButton(entry: PlaybackStateEntity, onClick: () -> Unit) {
    val progress = if (!entry.completed && entry.durationMs > 0) (entry.positionMs.toFloat() / entry.durationMs).coerceIn(0f, 1f) else 0f
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.9f else 1f, spring(dampingRatio = 0.5f, stiffness = 600f), label = "playLastScale")
    Box(
        Modifier
            .padding(end = 18.dp, bottom = 12.dp)
            .size(60.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(14.dp, CircleShape, ambientColor = Brand.Azure.copy(alpha = 0.5f), spotColor = Brand.Azure.copy(alpha = 0.6f))
            .clip(CircleShape)
            .background(Brand.Azure)
            .clickable(interactionSource = interaction, indication = ripple(color = Brand.OnAzure), onClickLabel = "Play") { onClick() }
            .semantics { contentDescription = "Play last video: ${entry.title}" },
        contentAlignment = Alignment.Center,
    ) {
        if (progress > 0f) {
            Canvas(Modifier.fillMaxSize().padding(5.dp)) {
                val stroke = 2.5.dp.toPx()
                val arc = Size(size.width - stroke, size.height - stroke)
                drawArc(Brand.OnAzure.copy(alpha = 0.2f), 0f, 360f, false, Offset(stroke / 2, stroke / 2), arc, style = Stroke(stroke))
                drawArc(Brand.OnAzure, -90f, 360f * progress, false, Offset(stroke / 2, stroke / 2), arc, style = Stroke(stroke, cap = StrokeCap.Round))
            }
        }
        Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = Brand.OnAzure, modifier = Modifier.size(32.dp))
    }
}

// ---------------------------------------------------------------- mini player

@Composable
private fun MiniPlayer(vm: LibraryViewModel) {
    val context = LocalContext.current
    val state by vm.playerState.collectAsStateWithLifecycle()
    val item = state.currentItem
    val extras = LocalMvoonExtras.current
    var position by remember { mutableLongStateOf(0L) }
    LaunchedEffect(item?.id) {
        while (item != null) {
            position = vm.engine.positionMs
            delay(500)
        }
    }
    AnimatedVisibility(
        item != null,
        enter = slideInVertically(spring(dampingRatio = 0.8f, stiffness = 400f)) { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
    ) {
        if (item == null) return@AnimatedVisibility
        val shape = RoundedCornerShape(28.dp)
        Row(
            Modifier
                .padding(start = 14.dp, end = 14.dp, bottom = 8.dp)
                .fillMaxWidth()
                .shadow(14.dp, shape, ambientColor = Color.Black.copy(alpha = 0.3f))
                .clip(shape)
                .background(extras.contrastCard)
                .clickable(onClickLabel = "Open player") { vm.coordinator.openPlayer(context) }
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MediaThumbnail(item.artworkUri ?: item.uri, item.kind, Modifier.size(46.dp).clip(RoundedCornerShape(16.dp)), iconSize = 18.dp, seed = item.subtitle ?: item.title)
            Column(Modifier.weight(1f)) {
                Text(item.title, style = MaterialTheme.typography.titleSmall, color = extras.onContrastCard, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    item.subtitle ?: if (item.kind == MediaKind.VIDEO) "Playing in background" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = extras.onContrastCard.copy(alpha = 0.6f),
                    maxLines = 1,
                )
            }
            val progress = if (state.durationMs > 0) position.toFloat() / state.durationMs else 0f
            Box(
                Modifier.size(46.dp).clip(CircleShape).clickable(onClickLabel = if (state.isPlaying) "Pause" else "Play") { vm.engine.togglePlayPause() },
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val stroke = 3.dp.toPx()
                    val arc = Size(size.width - stroke, size.height - stroke)
                    drawArc(Color.White.copy(alpha = 0.14f), 0f, 360f, false, Offset(stroke / 2, stroke / 2), arc, style = Stroke(stroke))
                    drawArc(Brand.Azure, -90f, 360f * progress.coerceIn(0f, 1f), false, Offset(stroke / 2, stroke / 2), arc, style = Stroke(stroke, cap = StrokeCap.Round))
                }
                Box(Modifier.size(34.dp).clip(CircleShape).background(Brand.Azure), contentAlignment = Alignment.Center) {
                    Icon(if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, contentDescription = null, tint = Brand.OnAzure, modifier = Modifier.size(22.dp))
                }
            }
            if (state.hasNext) {
                IconButton(onClick = { vm.engine.next() }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Rounded.SkipNext, "Next", tint = extras.onContrastCard)
                }
            }
            IconButton(onClick = { vm.coordinator.persistNow(); vm.engine.stop() }, modifier = Modifier.size(36.dp)) {
                Icon(Lucide.X, "Stop", tint = extras.onContrastCard.copy(alpha = 0.6f), modifier = Modifier.size(20.dp))
            }
        }
    }
}

// ---------------------------------------------------------------- onboarding

@Composable
fun OnboardingScreen(onAllow: () -> Unit, onChooseFolder: () -> Unit, onSkip: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Brand.Midnight)
            .background(Brush.radialGradient(listOf(Brand.Azure.copy(alpha = 0.28f), Color.Transparent), center = Offset(540f, 520f), radius = 1100f)),
    ) {
        Column(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).windowInsetsPadding(WindowInsets.navigationBars).padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))
            MvoonMark(112.dp, glow = true)
            Spacer(Modifier.height(20.dp))
            MvoonWordmark(fontSize = with(LocalDensity.current) { 50.dp.toSp() }, color = Color.White)
            Text("Every format, beautifully.", style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.7f), textAlign = TextAlign.Center)
            Spacer(Modifier.height(32.dp))
            Column(Modifier.clip(RoundedCornerShape(28.dp)).background(Color.White.copy(alpha = 0.05f)).padding(vertical = 8.dp, horizontal = 14.dp)) {
                Feature(Lucide.CircleCheck, "Plays almost anything", "MKV, MP4, AVI, MOV, WebM, FLAC, Opus — Dolby and DTS audio built in, and a VLC engine for WMV, RealMedia and older formats.")
                Feature(Lucide.Captions, "Subtitles, your way", "Found automatically, styled to taste, moved and resized with your fingers.")
                Feature(Lucide.Shield, "Private by design", "No account, no ads, no tracking. Your library never leaves this phone.")
            }
            Spacer(Modifier.weight(1f))
            Button(onClick = onAllow, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Icon(Lucide.Lock, null, Modifier.size(18.dp))
                Text("  Allow access to videos & music")
            }
            TextButton(onClick = onChooseFolder, modifier = Modifier.fillMaxWidth()) { Text("Choose folders instead", color = Color.White) }
            TextButton(onClick = onSkip) { Text("Not now", color = Color.White.copy(alpha = 0.6f)) }
        }
    }
}

@Composable
private fun Feature(icon: ImageVector, title: String, body: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        IconTile(icon, container = Brand.Azure.copy(alpha = 0.18f), tint = Brand.Sky, size = 40.dp)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = Color.White)
            Text(body, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.65f))
        }
    }
}
