package com.floria.phototinder

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.*
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private const val PREFS_NAME = "PhotoTinderPrefs"
private const val KEY_FIRST_RUN = "first_run"

class MainActivity : ComponentActivity() {

    private val viewModel: MediaViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()

    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> viewModel.loadMedia() }

    private val requestManageStorageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { _ -> viewModel.loadMedia() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val isFirstRun = prefs.getBoolean(KEY_FIRST_RUN, true)

        setContent {
            val language by settingsViewModel.language
            val theme by settingsViewModel.theme
            val pureBlack by settingsViewModel.pureBlack
            val primaryColor by settingsViewModel.primaryColor
            val secondaryColor by settingsViewModel.secondaryColor
            val tertiaryColor by settingsViewModel.tertiaryColor

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
                pureBlack = pureBlack,
                primaryColor = if (primaryColor == Color.Transparent) null else primaryColor,
                secondaryColor = if (secondaryColor == Color.Transparent) null else secondaryColor,
                tertiaryColor = if (tertiaryColor == Color.Transparent) null else tertiaryColor
            ) {
                LaunchedEffect(Unit) {
                    if (!isFirstRun) checkAndLoadMedia()
                }

                val navController = rememberNavController()
                val bottomNavItems = listOf("photos", "videos", "trash")
                val currentBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = currentBackStackEntry?.destination?.route

                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    GlobalRandomMorphingBackground(currentRoute)

                    Scaffold(
                        containerColor = Color.Transparent,
                        bottomBar = {
                            if (currentRoute in bottomNavItems) {
                                NavigationBar(containerColor = Color.Transparent) {
                                    bottomNavItems.forEach { screen ->
                                        NavigationBarItem(
                                            icon = {
                                                when (screen) {
                                                    "photos" -> Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                                                    "videos" -> Icon(Icons.Default.Videocam, contentDescription = null)
                                                    "trash" -> Icon(Icons.Default.DeleteOutline, contentDescription = null)
                                                }
                                            },
                                            label = {
                                                Text(stringResource(id = when(screen) {
                                                    "photos" -> R.string.photos
                                                    "videos" -> R.string.videos
                                                    else -> R.string.trash
                                                }))
                                            },
                                            selected = currentRoute == screen,
                                            onClick = { navController.navigate(screen) { launchSingleTop = true } }
                                        )
                                    }
                                }
                            }
                        }
                    ) { padding ->
                        NavHost(
                            navController = navController,
                            startDestination = if (isFirstRun) "welcome" else "photos",
                            modifier = Modifier.padding(padding)
                        ) {
                            composable("welcome") { WelcomeScreen { navController.navigate("onboarding_language") } }
                            composable("onboarding_language") { OnboardingLanguageScreen(settingsViewModel) { navController.navigate("onboarding_theme") } }
                            composable("onboarding_theme") { OnboardingThemeScreen(settingsViewModel) { navController.navigate("permission_screen") } }
                            composable("permission_screen") { 
                                PermissionScreen(
                                    onFinish = { navController.navigate("onboarding_tutorial") },
                                    onRequestAllFiles = { requestManageStorage() },
                                    onRequestMedia = { requestPermissionsInternal() }
                                ) 
                            }
                            composable("onboarding_tutorial") { 
                                OnboardingTutorialScreen { 
                                    prefs.edit().putBoolean(KEY_FIRST_RUN, false).apply()
                                    checkAndLoadMedia()
                                    navController.navigate("photos") { popUpTo("welcome") { inclusive = true } }
                                } 
                            }
                            composable("photos") { PhotoScreen(viewModel, navController) }
                            composable("videos") { VideoScreen(viewModel, navController) }
                            composable("trash") { TrashScreen(viewModel, navController) }
                            composable("info") { InfoScreen(navController) }
                            composable("settings") { SettingsScreen(navController) }
                            composable("settings_appearance") { AppearanceScreen(settingsViewModel, navController) }
                            composable("settings_language") { LanguageScreen(settingsViewModel, navController) }
                        }
                    }
                }
            }
        }
    }

    private fun checkAndLoadMedia() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
            permissions.add(Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        val allGranted = permissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
        
        val manageStorageGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else true

        if (allGranted && manageStorageGranted) {
            viewModel.loadMedia()
        } else {
            requestPermissionsInternal()
        }
    }

    private fun requestPermissionsInternal() {
        val permissionsToRequest = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(Manifest.permission.READ_MEDIA_IMAGES)
            permissionsToRequest.add(Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        requestPermissionsLauncher.launch(permissionsToRequest.toTypedArray())
    }

    private fun requestManageStorage() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
            intent.data = Uri.parse("package:$packageName")
            requestManageStorageLauncher.launch(intent)
        }
    }
}

data class BackgroundShape(
    val x: Float, val y: Float, val radius: Dp, val isSquiggly: Boolean = false, val isHollow: Boolean = false
)

@Composable
fun GlobalRandomMorphingBackground(currentRoute: String?) {
    val infiniteTransition = rememberInfiniteTransition(label = "living")
    val wiggleOffset by infiniteTransition.animateFloat(
        initialValue = -10f,
        targetValue = 10f,
        animationSpec = infiniteRepeatable(tween(3000, easing = LinearEasing), RepeatMode.Reverse),
        label = "wiggle"
    )
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(tween(4000, easing = LinearEasing), RepeatMode.Reverse),
        label = "pulse"
    )

    val targets = remember(currentRoute) {
        listOf(
            BackgroundShape(Random.nextFloat(), Random.nextFloat(), Random.nextInt(150, 250).dp, isSquiggly = true),
            BackgroundShape(Random.nextFloat(), Random.nextFloat(), Random.nextInt(100, 200).dp, isHollow = false),
            BackgroundShape(Random.nextFloat(), Random.nextFloat(), Random.nextInt(150, 300).dp, isHollow = true)
        )
    }

    val animStates = targets.mapIndexed { i, target ->
        val x by animateFloatAsState(target.x, tween(1500, easing = FastOutSlowInEasing), label = "x$i")
        val y by animateFloatAsState(target.y, tween(1500, easing = FastOutSlowInEasing), label = "y$i")
        val r by animateDpAsState(target.radius, tween(1500, easing = FastOutSlowInEasing), label = "r$i")
        Triple(x, y, r)
    }

    val shapeColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)

    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        animStates.forEachIndexed { i, (x, y, r) ->
            val centerX = w * x + wiggleOffset.dp.toPx()
            val centerY = h * y + wiggleOffset.dp.toPx()
            val radius = r.toPx() * pulseScale

            if (targets[i].isSquiggly) {
                val path = Path()
                val points = 30
                for (j in 0..points) {
                    val angle = (j.toFloat() / points) * 2 * PI.toFloat()
                    val offset = if (j % 2 == 0) 15.dp.toPx() else -15.dp.toPx()
                    val rx = radius + offset * pulseScale
                    val px = centerX + rx * cos(angle)
                    val py = centerY + rx * sin(angle)
                    if (j == 0) path.moveTo(px, py) else path.lineTo(px, py)
                }
                path.close()
                drawPath(path, color = shapeColor, style = Stroke(width = 3.dp.toPx()))
            } else if (targets[i].isHollow) {
                drawCircle(color = shapeColor, radius = radius, center = Offset(centerX, centerY), style = Stroke(width = 2.dp.toPx()))
            } else {
                drawCircle(color = shapeColor, radius = radius, center = Offset(centerX, centerY))
            }
        }
    }
}

@Composable
fun WelcomeScreen(onContinueClicked: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                stringResource(R.string.welcome_message),
                style = MaterialTheme.typography.displayLarge,
                lineHeight = 64.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Start,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(32.dp)
        ) {
            Button(
                onClick = onContinueClicked,
                modifier = Modifier.height(56.dp).widthIn(min = 120.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            ) {
                Text(stringResource(R.string.get_started), style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}

@Composable
fun OnboardingLanguageScreen(settingsViewModel: SettingsViewModel, onContinueClicked: () -> Unit) {
    val languages = listOf("en", "it", "es", "fr", "de")
    val currentLanguage = settingsViewModel.language.value

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(64.dp))
        Text(stringResource(R.string.language), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(48.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            items(languages) { language ->
                OnboardingSelectionCard(
                    text = Locale(language).getDisplayLanguage(Locale(language)).replaceFirstChar { it.uppercase() },
                    isSelected = currentLanguage == language,
                    onClick = { settingsViewModel.setLanguage(language); onContinueClicked() }
                )
            }
        }
    }
}

@Composable
fun OnboardingThemeScreen(settingsViewModel: SettingsViewModel, onContinueClicked: () -> Unit) {
    val currentTheme = settingsViewModel.theme.value

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(64.dp))
        Text(stringResource(R.string.theme), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(48.dp))
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Theme.entries.forEach { theme ->
                OnboardingSelectionCard(
                    text = theme.name.lowercase().replaceFirstChar { it.uppercase() },
                    isSelected = currentTheme == theme,
                    onClick = { settingsViewModel.setTheme(theme); onContinueClicked() }
                )
            }
        }
    }
}

@Composable
fun OnboardingSelectionCard(text: String, isSelected: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        border = if (isSelected) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else null,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Box(modifier = Modifier.padding(28.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(
                text = text, 
                style = MaterialTheme.typography.titleLarge, 
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun PermissionScreen(onFinish: () -> Unit, onRequestAllFiles: () -> Unit, onRequestMedia: () -> Unit) {
    val context = LocalContext.current
    var allFilesGranted by remember { mutableStateOf(false) }
    var mediaGranted by remember { mutableStateOf(false) }

    fun check() {
        allFilesGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Environment.isExternalStorageManager() else true
        mediaGranted = ContextCompat.checkSelfPermission(
            context,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    }

    LaunchedEffect(Unit) {
        while (true) {
            check()
            delay(800)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(stringResource(R.string.almost_done), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.permissions_required), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(48.dp))

        OnboardingPermissionCard(
            text = stringResource(R.string.all_files_permission),
            isGranted = allFilesGranted,
            onClick = { if (!allFilesGranted) onRequestAllFiles() }
        )
        
        Spacer(Modifier.height(16.dp))
        
        OnboardingPermissionCard(
            text = stringResource(R.string.photos_videos_permission),
            isGranted = mediaGranted,
            onClick = { if (!mediaGranted) onRequestMedia() }
        )

        if (allFilesGranted && mediaGranted) {
            Spacer(Modifier.height(64.dp))
            Button(
                onClick = onFinish,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(28.dp)
            ) { 
                Text(stringResource(R.string.continue_text), style = MaterialTheme.typography.titleMedium) 
            }
        }
    }
}

@Composable
fun OnboardingPermissionCard(text: String, isGranted: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(enabled = !isGranted, onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        border = if (isGranted) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else null,
        colors = CardDefaults.cardColors(
            containerColor = if (isGranted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = text, 
                    style = MaterialTheme.typography.titleLarge, 
                    fontWeight = FontWeight.Medium,
                    color = if (isGranted) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                )
                if (isGranted) {
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
            if (isGranted) {
                Text(stringResource(R.string.access_granted), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun OnboardingTutorialScreen(onFinish: () -> Unit) {
    var step by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        when (step) {
            0 -> {
                Text("Let's learn the basics!", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(48.dp))
                Button(onClick = { step++ }, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(28.dp)) { Text("Continue") }
            }
            1 -> TutorialStep(text = "Swipe left to delete.", onSwipe = { step++ }, isLeftSwipe = true)
            2 -> TutorialStep(text = "Swipe right to keep.", onSwipe = { step++ }, isLeftSwipe = false)
            3 -> {
                Text("You're all set!", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(48.dp))
                Button(onClick = onFinish, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(28.dp)) { 
                    Text(stringResource(R.string.start_swiping), style = MaterialTheme.typography.titleMedium) 
                }
            }
        }
    }
}

@Composable
fun TutorialStep(text: String, onSwipe: () -> Unit, isLeftSwipe: Boolean) {
    val scope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }

    Text(text, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
    Spacer(Modifier.height(32.dp))

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.fillMaxWidth().fillMaxHeight(0.7f).aspectRatio(9 / 16f)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize(0.9f)
                .graphicsLayer {
                    translationX = offsetX.value
                    rotationZ = offsetX.value / 20f
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDrag = { change, dragAmount ->
                            change.consume()
                            scope.launch { offsetX.snapTo(offsetX.value + dragAmount.x) }
                        },
                        onDragEnd = {
                            scope.launch {
                                val threshold = 350f
                                if (isLeftSwipe && offsetX.value < -threshold) {
                                    offsetX.animateTo(-2000f, tween(350))
                                    onSwipe()
                                } else if (!isLeftSwipe && offsetX.value > threshold) {
                                    offsetX.animateTo(2000f, tween(350))
                                    onSwipe()
                                } else {
                                    offsetX.animateTo(0f, tween(250))
                                }
                            }
                        }
                    )
                },
            shape = RoundedCornerShape(32.dp),
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (isLeftSwipe) Icons.Default.ArrowBack else Icons.Default.ArrowForward, 
                    contentDescription = null, 
                    modifier = Modifier.size(80.dp), 
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                )
            }
        }

        val iconAlpha = (abs(offsetX.value) / 450f).coerceIn(0f, 1f)
        if (iconAlpha > 0) {
            SwipeIcon(isLeft = offsetX.value < 0, alpha = iconAlpha)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoScreen(viewModel: MediaViewModel, navController: NavController) {
    val photos by viewModel.photos
    val currentPhotoIndex = viewModel.currentPhotoIndex
    val haptic = LocalHapticFeedback.current

    Scaffold(
        containerColor = Color.Transparent,
        topBar = { AppTopBar(navController, onRestart = { viewModel.restartPhotos() }) }
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(it),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (photos.isNotEmpty()) {
                val photo = photos.getOrNull(currentPhotoIndex)
                if (photo != null) {
                    val offsetX = remember { Animatable(0f) }
                    val scope = rememberCoroutineScope()

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        scope.launch { offsetX.snapTo(offsetX.value + dragAmount.x) }
                                    },
                                    onDragEnd = {
                                        scope.launch {
                                            val threshold = 350f
                                            if (offsetX.value < -300f) {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                offsetX.animateTo(-1500f, tween(350))
                                                viewModel.swipePhotoLeft()
                                                offsetX.snapTo(0f)
                                            } else if (offsetX.value > 300f) {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                offsetX.animateTo(1000f, tween(350))
                                                viewModel.swipePhotoRight()
                                                offsetX.snapTo(0f)
                                            } else {
                                                offsetX.animateTo(0f, tween(250))
                                            }
                                        }
                                    }
                                )
                            }
                    ) {
                        PhotoCard(photo = photo, modifier = Modifier.graphicsLayer {
                            translationX = offsetX.value
                            rotationZ = offsetX.value / 25f
                        })
                        val iconAlpha = (abs(offsetX.value) / 450f).coerceIn(0f, 1f)
                        if (iconAlpha > 0) SwipeIcon(isLeft = offsetX.value < 0, alpha = iconAlpha)
                    }
                }
            } else {
                Text(stringResource(R.string.all_reviewed), style = MaterialTheme.typography.titleLarge)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoScreen(viewModel: MediaViewModel, navController: NavController) {
    val videos by viewModel.videos
    val currentVideoIndex = viewModel.currentVideoIndex
    val context = LocalContext.current
    val exoPlayer = remember { ExoPlayer.Builder(context).build().apply { repeatMode = Player.REPEAT_MODE_ONE } }
    val haptic = LocalHapticFeedback.current

    Scaffold(
        containerColor = Color.Transparent,
        topBar = { AppTopBar(navController, onRestart = null) }
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(it),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (videos.isNotEmpty()) {
                val video = videos[currentVideoIndex]
                if (video != null) {
                    val offsetX = remember { Animatable(0f) }
                    val scope = rememberCoroutineScope()

                    LaunchedEffect(video) {
                        exoPlayer.setMediaItem(MediaItem.fromUri(video.uri))
                        exoPlayer.prepare()
                        exoPlayer.playWhenReady = true
                    }

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        scope.launch { offsetX.snapTo(offsetX.value + dragAmount.x) }
                                    },
                                    onDragEnd = {
                                        scope.launch {
                                            val threshold = 350f
                                            if (offsetX.value < -300f) {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                offsetX.animateTo(-1500f, tween(350))
                                                viewModel.swipeVideoLeft()
                                                offsetX.snapTo(0f)
                                            } else if (offsetX.value > threshold) {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                offsetX.animateTo(1000f, tween(350))
                                                viewModel.swipeVideoRight()
                                                offsetX.snapTo(0f)
                                            } else {
                                                offsetX.animateTo(0f, tween(250))
                                            }
                                        }
                                    }
                                )
                            }
                    ) {
                        VideoCard(video = video, exoPlayer = exoPlayer, isVisible = true, modifier = Modifier.graphicsLayer {
                            translationX = offsetX.value
                            rotationZ = offsetX.value / 25f
                        })
                        val iconAlpha = (abs(offsetX.value) / 450f).coerceIn(0f, 1f)
                        if (iconAlpha > 0) SwipeIcon(isLeft = offsetX.value < 0, alpha = iconAlpha)
                    }
                }
            } else {
                Text(stringResource(R.string.all_videos_reviewed), style = MaterialTheme.typography.titleLarge)
            }
        }
    }
    DisposableEffect(Unit) { onDispose { exoPlayer.release() } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashScreen(viewModel: MediaViewModel, navController: NavController) {
    val trashedPhotos by viewModel.trashedPhotos
    val trashedVideos by viewModel.trashedVideos
    val allTrash = trashedPhotos + trashedVideos

    Scaffold(
        containerColor = Color.Transparent,
        topBar = { AppTopBar(navController, onRestart = null) },
        floatingActionButton = {
            if (allTrash.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = { viewModel.emptyTrash() },
                    icon = { Icon(Icons.Default.DeleteForever, contentDescription = null) },
                    text = { Text(stringResource(R.string.empty_trash)) },
                    shape = RoundedCornerShape(24.dp)
                )
            }
        }
    ) {
        if (allTrash.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(it), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.no_trashed_media), style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyVerticalGrid(columns = GridCells.Fixed(3), modifier = Modifier.padding(it).fillMaxSize(), contentPadding = PaddingValues(8.dp)) {
                items(allTrash) { media ->
                    Surface(
                        modifier = Modifier.aspectRatio(1f).padding(4.dp).clickable { viewModel.restoreFromTrash(media) },
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    ) {
                        if (media is Photo) {
                            AsyncImage(model = media.uri, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                        } else {
                            Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                                Icon(Icons.Default.Videocam, contentDescription = null, tint = Color.White, modifier = Modifier.align(Alignment.Center))
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavController) {
    Scaffold(
        containerColor = Color.Transparent,
        topBar = { AppTopBar(navController, onRestart = null, showSettings = false) }
    ) {
        LazyColumn(
            modifier = Modifier.padding(it).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { SettingsSectionHeader(stringResource(R.string.interface_label)) }
            item { SettingsGroupCard {
                SettingsClickableItem(icon = Icons.Default.Palette, title = stringResource(R.string.appearance)) { navController.navigate("settings_appearance") }
                SettingsClickableItem(icon = Icons.Default.Language, title = stringResource(R.string.language)) { navController.navigate("settings_language") }
            } }
            
            item { SettingsGroupCard {
                SettingsClickableItem(icon = Icons.Default.Info, title = stringResource(R.string.information)) { navController.navigate("info") }
            } }
        }
    }
}

@Composable
fun SettingsGroupCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            content()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceScreen(settingsViewModel: SettingsViewModel, navController: NavController) {
    val currentTheme by settingsViewModel.theme
    val pureBlack by settingsViewModel.pureBlack
    val primaryColor by settingsViewModel.primaryColor
    
    val isDark = when (currentTheme) {
        Theme.DARK -> true
        Theme.LIGHT -> false
        Theme.SYSTEM -> isSystemInDarkTheme()
    }

    val palettes = listOf(
        ColorPalette(Color(0xFF8F0000), Color(0xFFFFDAD4), Color(0xFFFFB4A9)), // Dark Red
        ColorPalette(Color(0xFF720B33), Color(0xFFFFD9E2), Color(0xFFFFB1C1)), // Berry
        ColorPalette(Color(0xFF6750A4), Color(0xFFEADDFF), Color(0xFFD0BCFF)), // Default Purple
        ColorPalette(Color(0xFF3F51B5), Color(0xFFDEE0FF), Color(0xFFBFC2FF)), // Indigo
        ColorPalette(Color(0xFF2196F3), Color(0xFFD1E4FF), Color(0xFF9ECAFF)), // Blue
        ColorPalette(Color(0xFF006A6A), Color(0xFF00F0F0), Color(0xFF00D1D1)), // Cyan
        ColorPalette(Color(0xFF4CAF50), Color(0xFFC8E6C9), Color(0xFFA5D6A7)), // Green
        ColorPalette(Color(0xFFFF9800), Color(0xFFFFE0B2), Color(0xFFFFCC80)), // Orange
        ColorPalette(Color(0xFF795548), Color(0xFFD7CCC8), Color(0xFFBCAAA4))  // Brown
    )

    Scaffold(
        containerColor = Color.Transparent,
        topBar = { AppTopBar(navController, onRestart = null, showSettings = false) }
    ) {
        LazyColumn(modifier = Modifier.padding(it).padding(horizontal = 16.dp)) {
            item { ThemePreviewCard() }
            
            item { Spacer(Modifier.height(24.dp)) }
            item { Text(stringResource(R.string.theme_mode), style = MaterialTheme.typography.titleMedium) }
            item { ThemeModeSelector(currentTheme, onThemeSelected = { settingsViewModel.setTheme(it) }) }
            
            item { Spacer(Modifier.height(24.dp)) }
            item { Text(stringResource(R.string.color_palette), style = MaterialTheme.typography.titleMedium) }
            item { ColorPaletteSelector(primaryColor, palettes) { p, s, t -> settingsViewModel.setPalette(p, s, t) } }
            
            item { Spacer(Modifier.height(24.dp)) }
            item { SettingsGroupCard {
                SettingsSwitchItem(
                    icon = Icons.Default.InvertColors, 
                    title = stringResource(R.string.pure_black_mode), 
                    checked = pureBlack, 
                    onCheckedChange = { settingsViewModel.setPureBlack(it) },
                    enabled = isDark
                )
            } }
        }
    }
}

data class ColorPalette(val primary: Color, val secondary: Color, val tertiary: Color)

@Composable
fun ThemePreviewCard() {
    Card(
        modifier = Modifier.fillMaxWidth().height(200.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(modifier = Modifier.size(60.dp, 40.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)))
                    Box(modifier = Modifier.size(60.dp, 40.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f)))
                }
                Spacer(Modifier.height(8.dp))
                Box(modifier = Modifier.fillMaxWidth(0.6f).height(60.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primaryContainer))
            }
        }
    }
}

@Composable
fun ThemeModeSelector(currentTheme: Theme, onThemeSelected: (Theme) -> Unit) {
    Row(modifier = Modifier.padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Theme.entries.forEach { theme ->
            val isSelected = currentTheme == theme
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    .border(if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else BorderStroke(0.dp, Color.Transparent), CircleShape)
                    .clickable { onThemeSelected(theme) },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when(theme) {
                        Theme.SYSTEM -> Icons.Default.BrightnessAuto
                        Theme.LIGHT -> Icons.Default.LightMode
                        Theme.DARK -> Icons.Default.DarkMode
                    },
                    contentDescription = null,
                    tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
fun ColorPaletteSelector(currentPrimary: Color, palettes: List<ColorPalette>, onPaletteSelected: (Color, Color, Color) -> Unit) {
    LazyRow(modifier = Modifier.padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            // Dynamic Palette Icon
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    .border(if (currentPrimary == Color.Transparent) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else BorderStroke(0.dp, Color.Transparent), CircleShape)
                    .clickable { onPaletteSelected(Color.Transparent, Color.Transparent, Color.Transparent) },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Palette, contentDescription = null)
            }
        }
        items(palettes) { palette ->
            val isSelected = currentPrimary == palette.primary
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .border(if (isSelected) BorderStroke(3.dp, MaterialTheme.colorScheme.onBackground) else BorderStroke(0.dp, Color.Transparent), CircleShape)
                    .clickable { onPaletteSelected(palette.primary, palette.secondary, palette.tertiary) },
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    // Left half: Primary
                    drawRect(
                        color = palette.primary,
                        size = Size(size.width / 2, size.height)
                    )
                    // Top right quarter: Secondary
                    drawRect(
                        color = palette.secondary,
                        topLeft = Offset(size.width / 2, 0f),
                        size = Size(size.width / 2, size.height / 2)
                    )
                    // Bottom right quarter: Tertiary
                    drawRect(
                        color = palette.tertiary,
                        topLeft = Offset(size.width / 2, size.height / 2),
                        size = Size(size.width / 2, size.height / 2)
                    )
                }
                if (isSelected) Icon(Icons.Default.Check, contentDescription = null, tint = Color.White)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InfoScreen(navController: NavController) {
    Scaffold(
        containerColor = Color.Transparent,
        topBar = { AppTopBar(navController, onRestart = null, showSettings = false) }
    ) {
        Column(modifier = Modifier.padding(it).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            ) {
                Column(modifier = Modifier.padding(32.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Box(modifier = Modifier.size(64.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(R.string.app_name).uppercase(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text("0.41 Alpha • UNIVERSAL", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            
            Text("Lead Developer", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start)
            Spacer(Modifier.height(16.dp))
            Box(modifier = Modifier.size(120.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer))
            Spacer(Modifier.height(16.dp))
            Text("Mattia Floria", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                AboutButton(Icons.Default.Public, "Website")
                AboutButton(Icons.Default.Code, "GitHub")
                AboutButton(Icons.Default.CameraAlt, "Instagram")
            }
            
            Spacer(Modifier.height(24.dp))
            SettingsGroupCard {
                ListItem(
                    headlineContent = { Text("Like what I do?") },
                    supportingContent = { Text("Buy me a coffee") },
                    leadingContent = { Icon(Icons.Default.Coffee, contentDescription = null) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }
        }
    }
}

@Composable
fun AboutButton(icon: ImageVector, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(modifier = Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null)
        }
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageScreen(settingsViewModel: SettingsViewModel, navController: NavController) {
    val currentLanguage by settingsViewModel.language
    val languages = listOf("en", "it", "es", "fr", "de")
    Scaffold(
        containerColor = Color.Transparent,
        topBar = { AppTopBar(navController, onRestart = null, showSettings = false) }
    ) {
        LazyColumn(modifier = Modifier.padding(it)) {
            item { SettingsSectionHeader(title = stringResource(R.string.language)) }
            items(languages) { lang ->
                val locale = Locale(lang)
                SettingsItem(
                    icon = Icons.Default.Language,
                    title = locale.getDisplayLanguage(locale).replaceFirstChar { it.uppercase() },
                    subtitle = if (currentLanguage == lang) "Selected" else "",
                    onClick = { settingsViewModel.setLanguage(lang) },
                    selected = currentLanguage == lang
                )
            }
        }
    }
}

@Composable
fun VideoCard(video: Video, exoPlayer: ExoPlayer, isVisible: Boolean, modifier: Modifier = Modifier) {
    Surface(modifier = modifier.fillMaxSize(0.88f).aspectRatio(9 / 16f), shape = RoundedCornerShape(32.dp), border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)), color = Color.Black) {
        AndroidView(factory = { context -> PlayerView(context).apply { player = exoPlayer; useController = true } }, modifier = Modifier.fillMaxSize())
    }
}

@Composable
fun BoxScope.SwipeIcon(isLeft: Boolean, alpha: Float) {
    val icon = if (isLeft) Icons.Default.Delete else Icons.Default.Favorite
    val color = if (isLeft) Color.Red else Color.Green
    Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = if (isLeft) Alignment.CenterStart else Alignment.CenterEnd) {
        Icon(imageVector = icon, contentDescription = null, tint = color.copy(alpha = alpha), modifier = Modifier.size(80.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(navController: NavController, onRestart: (() -> Unit)?, showSettings: Boolean = true) {
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    TopAppBar(
        title = { Text(stringResource(R.string.app_name), fontWeight = FontWeight.Bold) },
        modifier = Modifier.statusBarsPadding().height(72.dp),
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
        navigationIcon = {
            if (navController.previousBackStackEntry != null && currentRoute !in listOf("photos", "videos", "trash")) {
                IconButton(onClick = { navController.navigateUp() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) }
            }
        },
        actions = {
            if (onRestart != null) IconButton(onClick = onRestart) { Icon(Icons.Default.RestartAlt, contentDescription = null) }
            if (showSettings && currentRoute !in listOf("settings", "welcome", "onboarding_language", "onboarding_theme", "onboarding_tutorial", "permission_screen", "info", "settings_appearance", "settings_language")) {
                IconButton(onClick = { navController.navigate("settings") }) { Icon(Icons.Default.Settings, contentDescription = null) }
            }
        }
    )
}

@Composable
fun PhotoCard(photo: Photo, modifier: Modifier = Modifier) {
    Surface(modifier = modifier.fillMaxSize(0.88f).aspectRatio(9 / 16f), shape = RoundedCornerShape(32.dp), border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))) {
        AsyncImage(model = photo.uri, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
    }
}

@Composable
fun SettingsSectionHeader(title: String) { Text(text = title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) }

@Composable
fun SettingsItem(icon: ImageVector, title: String, subtitle: String = "", onClick: () -> Unit, selected: Boolean = false) {
    ListItem(
        headlineContent = { Text(title, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal) },
        supportingContent = if (subtitle.isNotEmpty()) { { Text(subtitle) } } else null,
        leadingContent = { Icon(icon, contentDescription = null, tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) },
        modifier = Modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else Color.Transparent)
    )
}

@Composable
fun SettingsSwitchItem(icon: ImageVector, title: String, subtitle: String = "", checked: Boolean, onCheckedChange: (Boolean) -> Unit, enabled: Boolean = true) {
    ListItem(
        headlineContent = { Text(title, color = if (enabled) Color.Unspecified else Color.Gray) },
        supportingContent = if (subtitle.isNotEmpty()) { { Text(subtitle, color = if (enabled) Color.Unspecified else Color.Gray) } } else null,
        leadingContent = { Icon(icon, contentDescription = null, tint = if (enabled) LocalContentColor.current else Color.Gray) },
        trailingContent = { Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled) },
        modifier = Modifier.clickable(enabled = enabled) { onCheckedChange(!checked) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
fun SettingsClickableItem(icon: ImageVector, title: String, subtitle: String = "", onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = if (subtitle.isNotEmpty()) { { Text(subtitle) } } else null,
        leadingContent = { Icon(icon, contentDescription = null) },
        trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = null) },
        modifier = Modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}
