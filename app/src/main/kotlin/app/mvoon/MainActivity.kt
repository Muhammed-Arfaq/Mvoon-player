package app.mvoon

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import android.graphics.Color as AndroidColor
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.mvoon.ui.library.AccessLevel
import app.mvoon.ui.library.FolderScreen
import app.mvoon.ui.library.HomeCallbacks
import app.mvoon.ui.library.HomeScreen
import app.mvoon.ui.library.LibraryViewModel
import app.mvoon.ui.library.OnboardingScreen
import app.mvoon.ui.library.PlaylistScreen
import app.mvoon.ui.library.SearchScreen
import app.mvoon.ui.settings.AboutScreen
import app.mvoon.ui.settings.FormatSupportScreen
import app.mvoon.ui.settings.SettingsScreen
import app.mvoon.ui.settings.SettingsViewModel
import app.mvoon.ui.settings.SubtitleAppearanceScreen
import app.mvoon.ui.theme.LocalMvoonExtras
import app.mvoon.ui.theme.MvoonTheme

class MainActivity : ComponentActivity() {

    private val vm: LibraryViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Hold the splash until saved preferences are read, so returning users never see onboarding flash.
        splash.setKeepOnScreenCondition { !appContainer.playback.preferencesLoaded.value }
        setContent {
            val prefs by vm.preferences.collectAsStateWithLifecycle()
            MvoonTheme(prefs.appearance) { AppRoot(vm, prefs.library.onboardingDone) }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.onAccessChanged(currentAccess())
    }
}

/** Media permissions to request on this Android version. */
private fun mediaPermissions(): Array<String> = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> arrayOf(
        Manifest.permission.READ_MEDIA_VIDEO,
        Manifest.permission.READ_MEDIA_AUDIO,
        Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
    )
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_AUDIO)
    else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
}

fun Context.currentAccess(): AccessLevel {
    fun granted(p: String) = ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val video = granted(Manifest.permission.READ_MEDIA_VIDEO)
        val audio = granted(Manifest.permission.READ_MEDIA_AUDIO)
        when {
            video && audio -> AccessLevel.FULL
            video || audio -> AccessLevel.PARTIAL
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && granted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) -> AccessLevel.PARTIAL
            else -> AccessLevel.NONE
        }
    } else {
        if (granted(Manifest.permission.READ_EXTERNAL_STORAGE)) AccessLevel.FULL else AccessLevel.NONE
    }
}

@Composable
private fun AppRoot(vm: LibraryViewModel, onboardingDone: Boolean) {
    val context = LocalContext.current
    // System bar icons follow what is on screen: light icons on the (always dark) onboarding and dark theme.
    val darkBars = !onboardingDone || LocalMvoonExtras.current.isDark
    DisposableEffect(darkBars) {
        val style = if (darkBars) {
            SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        } else {
            SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
        }
        (context as ComponentActivity).enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
        onDispose {}
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        vm.onAccessChanged(context.currentAccess())
    }
    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) {
            Toast.makeText(context, "Indexing folder…", Toast.LENGTH_SHORT).show()
            vm.addFolder(uri) { count -> Toast.makeText(context, "Added $count item${if (count == 1) "" else "s"}", Toast.LENGTH_SHORT).show() }
        }
    }
    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) vm.playUris(context, uris)
    }
    val requestAccess = { permissionLauncher.launch(mediaPermissions()) }
    val pickFolder = { folderLauncher.launch(null) }
    val pickFiles = { fileLauncher.launch(arrayOf("video/*", "audio/*", "application/x-matroska", "application/octet-stream")) }

    if (!onboardingDone) {
        OnboardingScreen(
            onAllow = { requestAccess(); vm.finishOnboarding() },
            onChooseFolder = { pickFolder(); vm.finishOnboarding() },
            onSkip = { vm.finishOnboarding() },
        )
        return
    }

    val nav = rememberNavController()
    NavHost(
        navController = nav,
        startDestination = "home",
        // Pushed screens glide in from the right while the screen behind settles back a little.
        enterTransition = { slideInHorizontally(spring(dampingRatio = 0.9f, stiffness = 380f)) { it / 3 } + fadeIn(tween(220)) },
        exitTransition = { scaleOut(targetScale = 0.96f, animationSpec = tween(260)) + fadeOut(tween(260)) },
        popEnterTransition = { scaleIn(initialScale = 0.96f, animationSpec = tween(260)) + fadeIn(tween(260)) },
        popExitTransition = { slideOutHorizontally(tween(240)) { it / 3 } + fadeOut(tween(200)) },
    ) {
        composable("home") {
            HomeScreen(
                vm,
                HomeCallbacks(
                    openFolder = { key -> nav.navigate("folder/${Uri.encode(key)}") },
                    openPlaylist = { id -> nav.navigate("playlist/$id") },
                    openSearch = { nav.navigate("search") },
                    openSettings = { nav.navigate("settings") },
                    requestAccess = requestAccess,
                    pickFiles = pickFiles,
                    pickFolder = pickFolder,
                ),
            )
        }
        composable("folder/{key}", arguments = listOf(navArgument("key") { type = NavType.StringType })) { entry ->
            FolderScreen(vm, Uri.decode(entry.arguments?.getString("key").orEmpty()), onBack = { nav.popBackStack() })
        }
        composable("playlist/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
            PlaylistScreen(vm, entry.arguments?.getLong("id") ?: 0L, onBack = { nav.popBackStack() })
        }
        composable("search") { SearchScreen(vm, onBack = { nav.popBackStack() }) }
        composable("settings") {
            val settingsVm: SettingsViewModel = viewModel()
            SettingsScreen(
                settingsVm,
                onBack = { nav.popBackStack() },
                onSubtitleStyle = { nav.navigate("settings/subtitles") },
                onFormats = { nav.navigate("settings/formats") },
                onAbout = { nav.navigate("about") },
                onAddFolder = pickFolder,
            )
        }
        composable("settings/subtitles") {
            val settingsVm: SettingsViewModel = viewModel()
            SubtitleAppearanceScreen(settingsVm, onBack = { nav.popBackStack() })
        }
        composable("settings/formats") { FormatSupportScreen(onBack = { nav.popBackStack() }) }
        composable("about") { AboutScreen(onBack = { nav.popBackStack() }) }
    }
}
