package com.floria.phototinder

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
import com.floria.phototinder.ui.theme.PhotoTinderTheme
import kotlinx.coroutines.launch
import java.util.*
import kotlin.math.abs

private const val PREFS_NAME = "PhotoTinderPrefs"
private const val KEY_FIRST_RUN = "first_run"

class MainActivity : ComponentActivity() {

    private val viewModel: MediaViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()

    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.all { it }) {
            viewModel.loadMedia()
        }
    }

    private val requestManageStorageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                viewModel.loadMedia()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val isFirstRun = prefs.getBoolean(KEY_FIRST_RUN, true)

        setContent {
            val language by settingsViewModel.language
            val theme by settingsViewModel.theme
            val pureBlack by settingsViewModel.pureBlack

            val locale = Locale(language)
            val configuration = LocalConfiguration.current
            configuration.setLocale(locale)
            val context = LocalContext.current
            context.resources.updateConfiguration(configuration, context.resources.displayMetrics)

            PhotoTinderTheme(
                darkTheme = when (theme) {
                    Theme.DARK -> true
                    Theme.LIGHT -> false
                    Theme.SYSTEM -> isSystemInDarkTheme()
                },
                pureBlack = pureBlack
            ) {
                LaunchedEffect(Unit) {
                    if (!isFirstRun) {
                        requestPermissions()
                    }
                }

                val navController = rememberNavController()
                val bottomNavItems = listOf("photos", "videos", "trash")
                val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route

                Scaffold(
                    bottomBar = {
                        if (currentRoute in bottomNavItems) {
                            NavigationBar {
                                bottomNavItems.forEach { screen ->
                                    NavigationBarItem(
                                        icon = {
                                            when (screen) {
                                                "photos" -> Icon(Icons.Default.PhotoLibrary, contentDescription = stringResource(R.string.photos))
                                                "videos" -> Icon(Icons.Default.Videocam, contentDescription = stringResource(R.string.videos))
                                                "trash" -> Icon(Icons.Default.DeleteOutline, contentDescription = stringResource(R.string.trash))
                                            }
                                        },
                                        label = { Text(stringResource(id = when(screen) {
                                            "photos" -> R.string.photos
                                            "videos" -> R.string.videos
                                            else -> R.string.trash
                                        })) },
                                        selected = currentRoute == screen,
                                        onClick = { navController.navigate(screen) { launchSingleTop = true } }
                                    )
                                }
                            }
                        }
                    }
                ) { padding ->
                    NavHost(navController = navController, startDestination = if (isFirstRun) "onboarding_language" else "photos", modifier = Modifier.padding(padding)) {
                        composable("onboarding_language") { OnboardingLanguageScreen(settingsViewModel) { navController.navigate("onboarding_theme") } }
                        composable("onboarding_theme") { OnboardingThemeScreen(settingsViewModel) { navController.navigate("onboarding_tutorial") } }
                        composable("onboarding_tutorial") { OnboardingTutorialScreen { 
                                prefs.edit().putBoolean(KEY_FIRST_RUN, false).apply()
                                requestPermissions()
                                navController.navigate("photos") { popUpTo("onboarding_language") { inclusive = true } }
                            } 
                        }
                        composable("photos") { PhotoScreen(viewModel, navController) }
                        composable("videos") { VideoScreen(viewModel, navController) }
                        composable("trash") { TrashScreen(viewModel) }
                        composable("info") { InfoScreen(navController) }
                        composable("settings") { SettingsScreen(navController) }
                        composable("settings_appearance") { AppearanceScreen(settingsViewModel, navController) }
                        composable("settings_language") { LanguageScreen(settingsViewModel, navController) }
                    }
                }
            }
        }
    }

    private fun requestPermissions() {
        val permissionsToRequest = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(Manifest.permission.READ_MEDIA_IMAGES)
            permissionsToRequest.add(Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        val allPermissionsGranted = permissionsToRequest.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

        if (allPermissionsGranted) {
            viewModel.loadMedia()
        } else {
            requestPermissionsLauncher.launch(permissionsToRequest.toTypedArray())
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
            intent.data = Uri.parse("package:$packageName")
            requestManageStorageLauncher.launch(intent)
        }
    }
}

@Composable
fun OnboardingLanguageScreen(settingsViewModel: SettingsViewModel, onContinueClicked: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(stringResource(R.string.language), style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))
        Row {
            Button(onClick = { settingsViewModel.setLanguage("en"); onContinueClicked() }) { Text("English") }
            Spacer(Modifier.width(16.dp))
            Button(onClick = { settingsViewModel.setLanguage("it"); onContinueClicked() }) { Text("Italiano") }
        }
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.settings_subtitle), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun OnboardingThemeScreen(settingsViewModel: SettingsViewModel, onContinueClicked: () -> Unit) {
    // ... (This screen remains the same, but now navigates to the tutorial)
}

@Composable
fun OnboardingTutorialScreen(onFinish: () -> Unit) {
    var step by remember { mutableIntStateOf(0) }
    val haptic = LocalHapticFeedback.current

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        when (step) {
            0 -> {
                Text("Let's learn the basics!", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(16.dp))
                Button(onClick = { step++ }) { Text("Continue") }
            }
            1 -> TutorialStep(
                text = "Swipe left to delete a photo.", 
                onSwipe = { step++ },
                isLeftSwipe = true
            )
            2 -> TutorialStep(
                text = "Swipe right to keep it.", 
                onSwipe = { step++ },
                isLeftSwipe = false
            )
            3 -> {
                Text("You're all set!", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(16.dp))
                Button(onClick = onFinish) { Text("Finish") }
            }
        }
    }
}

@Composable
fun TutorialStep(text: String, onSwipe: () -> Unit, isLeftSwipe: Boolean) {
    val scope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    val haptic = LocalHapticFeedback.current

    Text(text, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
    Spacer(Modifier.height(32.dp))

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp) 
    ) {
        // Dummy Card
        Surface(
            modifier = Modifier
                .fillMaxSize(0.7f)
                .graphicsLayer {
                    translationX = offsetX.value
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDrag = { change, dragAmount ->
                            change.consume()
                            scope.launch { offsetX.snapTo(offsetX.value + dragAmount.x) }
                        },
                        onDragEnd = {
                            scope.launch {
                                val threshold = 200f
                                if (isLeftSwipe && offsetX.value < -threshold) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    offsetX.animateTo(-1000f)
                                    onSwipe()
                                } else if (!isLeftSwipe && offsetX.value > threshold) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    offsetX.animateTo(1000f)
                                    onSwipe()
                                } else {
                                    offsetX.animateTo(0f)
                                }
                            }
                        }
                    )
                },
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {}

        val iconAlpha = (abs(offsetX.value) / 300f).coerceIn(0f, 1f)
        if (iconAlpha > 0) {
            SwipeIcon(isLeft = offsetX.value < 0, alpha = iconAlpha)
        }
    }
}

// ... Other main screens remain the same ...

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoScreen(viewModel: MediaViewModel, navController: NavController) { /* ... */ }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoScreen(viewModel: MediaViewModel, navController: NavController) { /* ... */ }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashScreen(viewModel: MediaViewModel) { /* ... */ }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InfoScreen(navController: NavController) { /* ... */ }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavController) { /* ... */ }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceScreen(settingsViewModel: SettingsViewModel, navController: NavController) { /* ... */ }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageScreen(settingsViewModel: SettingsViewModel, navController: NavController) { /* ... */ }


// --- SHARED COMPOSABLES ---

@Composable
fun VideoCard(video: Video, exoPlayer: ExoPlayer, isVisible: Boolean, modifier: Modifier = Modifier) { /* ... */ }

@Composable
fun BoxScope.SwipeIcon(isLeft: Boolean, alpha: Float) { /* ... */ }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(navController: NavController, onRestart: (() -> Unit)? = null) { /* ... */ }

@Composable
fun PhotoCard(photo: Photo, modifier: Modifier = Modifier) { /* ... */ }

@Composable
fun SettingsSectionHeader(title: String) { /* ... */ }

@Composable
fun SettingsItem(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) { /* ... */ }

@Composable
fun SettingsSwitchItem(icon: ImageVector, title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) { /* ... */ }

@Composable
fun SettingsClickableItem(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) { /* ... */ }
