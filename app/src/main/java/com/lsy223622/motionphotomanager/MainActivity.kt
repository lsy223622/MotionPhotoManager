package com.lsy223622.motionphotomanager

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lsy223622.motionphotomanager.data.MotionPhoto
import androidx.compose.ui.res.stringResource
import com.lsy223622.motionphotomanager.ui.MotionPhotoViewModel
import com.lsy223622.motionphotomanager.ui.components.BottomFloatingConsole
import com.lsy223622.motionphotomanager.ui.screen.MotionPhotoGrid
import com.lsy223622.motionphotomanager.ui.screen.MotionPhotoPreviewScreen
import com.lsy223622.motionphotomanager.ui.screen.PREVIEW_FADE_IN_DURATION_MS
import com.lsy223622.motionphotomanager.ui.screen.PREVIEW_FADE_OUT_DURATION_MS
import com.lsy223622.motionphotomanager.ui.theme.MotionPhotoManagerTheme
import android.graphics.Color as AndroidColor

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
        )
        window.isNavigationBarContrastEnforced = false
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            MotionPhotoManagerTheme {
                val viewModel: MotionPhotoViewModel = viewModel()
                val uiState by viewModel.uiState.collectAsState()
                val context = LocalContext.current

                val trashLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.StartIntentSenderForResult()
                ) { result ->
                    if (result.resultCode == RESULT_OK) {
                        viewModel.onTrashRequestResult()
                        Toast.makeText(context, R.string.original_photos_trashed, Toast.LENGTH_SHORT).show()
                    } else {
                        viewModel.onTrashRequestResult()
                        Toast.makeText(context, R.string.original_photos_not_trashed, Toast.LENGTH_LONG).show()
                    }
                }

                val permissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions()
                ) { permissions ->
                    val allGranted = permissions.entries.all { it.value }
                    if (allGranted) {
                        viewModel.loadPhotos()
                    } else {
                        Toast.makeText(context, R.string.permission_denied, Toast.LENGTH_LONG).show()
                    }
                }

                LaunchedEffect(Unit) {
                    val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        arrayOf(
                            Manifest.permission.READ_MEDIA_IMAGES,
                            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
                            Manifest.permission.ACCESS_MEDIA_LOCATION
                        )
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.ACCESS_MEDIA_LOCATION)
                    } else {
                        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.ACCESS_MEDIA_LOCATION)
                    }

                    val allGranted = permissions.all {
                        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
                    }

                    if (allGranted) {
                        viewModel.loadPhotos()
                    } else {
                        permissionLauncher.launch(permissions)
                    }
                }

                Box(modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface)
                ) {
                    SharedTransitionLayout {
                        var lastPreviewPhoto by remember { mutableStateOf<MotionPhoto?>(null) }
                        val gridScrollState = rememberLazyListState()
                        if (uiState.previewPhoto != null) {
                            lastPreviewPhoto = uiState.previewPhoto
                        }

                        AnimatedVisibility(
                            modifier = Modifier.zIndex(if (uiState.previewPhoto == null) 1f else 0f),
                            visible = uiState.previewPhoto == null,
                            enter = EnterTransition.None,
                            exit = fadeOut(
                                animationSpec = tween(
                                    durationMillis = PREVIEW_FADE_OUT_DURATION_MS,
                                    easing = FastOutLinearInEasing
                                )
                            )
                        ) {
                            Scaffold(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .then(if (uiState.isProcessing) Modifier.blur(10.dp) else Modifier),
                                containerColor = Color.Transparent,
                                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                                    topBar = {
                                    LargeTopAppBar(
                                        title = {
                                            Column {
                                                Text(
                                                    text = stringResource(R.string.motion_photos_title),
                                                    style = MaterialTheme.typography.headlineSmall,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                                Text(
                                                    text = stringResource(R.string.photos_count, uiState.photos.size),
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        },
                                        colors = TopAppBarDefaults.topAppBarColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                            scrolledContainerColor = Color.Unspecified,
                                            navigationIconContentColor = Color.Unspecified,
                                            titleContentColor = Color.Unspecified,
                                            actionIconContentColor = Color.Unspecified
                                        )
                                    )
                                }
                            ) { innerPadding ->
                                if (uiState.photos.isEmpty() && !uiState.isLoading) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(innerPadding),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(stringResource(R.string.no_motion_photos_found), color = Color.Gray)
                                    }
                                } else {
                                    MotionPhotoGrid(
                                        photos = uiState.photos,
                                        selectedIds = uiState.selectedIds,
                                        onToggleSelection = { viewModel.toggleSelection(it) },
                                        onToggleDaySelection = { viewModel.toggleSelectionForIds(it) },
                                        onPreviewPhoto = { viewModel.openPreview(it) },
                                        sharedTransitionScope = this@SharedTransitionLayout,
                                        animatedVisibilityScope = this@AnimatedVisibility,
                                        scrollState = gridScrollState,
                                        modifier = Modifier.padding(innerPadding)
                                    )
                                }

                                if (uiState.isLoading) {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator()
                                    }
                                }
                            }
                        }

                        AnimatedVisibility(
                            modifier = Modifier.zIndex(1.5f),
                            visible = uiState.previewPhoto != null,
                            enter = fadeIn(
                                animationSpec = tween(
                                    durationMillis = PREVIEW_FADE_IN_DURATION_MS,
                                    easing = FastOutSlowInEasing
                                )
                            ),
                            exit = fadeOut(
                                animationSpec = tween(
                                    durationMillis = PREVIEW_FADE_OUT_DURATION_MS,
                                    easing = FastOutLinearInEasing
                                )
                            )
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black)
                            )
                        }

                        if (uiState.isProcessing) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.3f))
                                    .clickable(enabled = false) {}
                            )
                        }

                        AnimatedVisibility(
                            modifier = Modifier.zIndex(if (uiState.previewPhoto != null) 2f else -1f),
                            visible = uiState.previewPhoto != null,
                            enter = fadeIn(
                                animationSpec = tween(
                                    durationMillis = PREVIEW_FADE_IN_DURATION_MS,
                                    easing = FastOutSlowInEasing
                                )
                            ),
                            exit = fadeOut(
                                animationSpec = tween(
                                    durationMillis = PREVIEW_FADE_OUT_DURATION_MS,
                                    easing = FastOutLinearInEasing
                                )
                            )
                        ) {
                            lastPreviewPhoto?.let { photo ->
                                MotionPhotoPreviewScreen(
                                    photos = uiState.photos,
                                    currentPhoto = photo,
                                    selectedIds = uiState.selectedIds,
                                    previewVideoPath = uiState.previewVideoPath,
                                    isLoading = uiState.isPreviewLoading,
                                    onDismiss = { viewModel.closePreview() },
                                    onToggleSelection = { viewModel.toggleSelection(it) },
                                    onPhotoChanged = { viewModel.openPreview(it) },
                                    sharedTransitionScope = this@SharedTransitionLayout,
                                    animatedVisibilityScope = this@AnimatedVisibility
                                )
                            }
                        }
                    }

                    BottomFloatingConsole(
                        uiState = uiState,
                        onStartProcessing = { viewModel.startProcessing(trashLauncher) },
                        onSetConfirming = { viewModel.setConfirming(it) },
                        onPreviewPhoto = { viewModel.openPreview(it) },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 32.dp, start = 16.dp, end = 16.dp)
                    )
                }
            }
        }
    }
}
