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
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lsy223622.motionphotomanager.data.MotionPhoto
import com.lsy223622.motionphotomanager.data.MotionPhotoProcessingMode
import com.lsy223622.motionphotomanager.ui.MotionPhotoViewModel
import com.lsy223622.motionphotomanager.ui.components.BottomFloatingConsole
import com.lsy223622.motionphotomanager.ui.screen.MotionPhotoGrid
import com.lsy223622.motionphotomanager.ui.screen.MotionPhotoPreviewScreen
import com.lsy223622.motionphotomanager.ui.screen.PREVIEW_FADE_IN_DURATION_MS
import com.lsy223622.motionphotomanager.ui.screen.PREVIEW_FADE_OUT_DURATION_MS
import com.lsy223622.motionphotomanager.ui.theme.MotionPhotoManagerTheme
import android.graphics.Color as AndroidColor
import sv.lib.squircleshape.SquircleShape

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
                                    HomeTopBar(
                                        photoCount = uiState.photos.size,
                                        selectedMode = uiState.processingMode,
                                        deleteOriginalAfterProcessing = uiState.deleteOriginalAfterProcessing,
                                        onModeSelected = { viewModel.setProcessingMode(it) },
                                        onDeleteOriginalChanged = {
                                            viewModel.setDeleteOriginalAfterProcessing(it)
                                        }
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
                                        isRefreshing = uiState.isLoading && uiState.photos.isNotEmpty(),
                                        onRefresh = { viewModel.loadPhotos() },
                                        onToggleSelection = { viewModel.toggleSelection(it) },
                                        onToggleDaySelection = { viewModel.toggleSelectionForIds(it) },
                                        onPreviewPhoto = { viewModel.openPreview(it) },
                                        sharedTransitionScope = this@SharedTransitionLayout,
                                        animatedVisibilityScope = this@AnimatedVisibility,
                                        scrollState = gridScrollState,
                                        modifier = Modifier.padding(innerPadding)
                                    )
                                }

                                if (uiState.isLoading && uiState.photos.isEmpty()) {
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
                        onStopProcessing = { viewModel.requestStopProcessing() },
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

@Composable
private fun HomeTopBar(
    photoCount: Int,
    selectedMode: MotionPhotoProcessingMode,
    deleteOriginalAfterProcessing: Boolean,
    onModeSelected: (MotionPhotoProcessingMode) -> Unit,
    onDeleteOriginalChanged: (Boolean) -> Unit
) {
    var isHelpVisible by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 20.dp, top = 6.dp, end = 12.dp, bottom = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.motion_photos_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = stringResource(R.string.photos_count, photoCount),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Box {
                    IconButton(onClick = { isHelpVisible = true }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.HelpOutline,
                            contentDescription = stringResource(R.string.processing_mode_help),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    DropdownMenu(
                        expanded = isHelpVisible,
                        onDismissRequest = { isHelpVisible = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.keep_photo_only_help)) },
                            onClick = { isHelpVisible = false }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.keep_video_only_help)) },
                            onClick = { isHelpVisible = false }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.split_both_parts_help)) },
                            onClick = { isHelpVisible = false }
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = stringResource(R.string.processing_mode_delete_original_help),
                                    modifier = Modifier.padding(top = 6.dp)
                                )
                            },
                            onClick = { isHelpVisible = false }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            HomeActionOptionsRow(
                selectedMode = selectedMode,
                deleteOriginalAfterProcessing = deleteOriginalAfterProcessing,
                onModeSelected = onModeSelected,
                onDeleteOriginalChanged = onDeleteOriginalChanged
            )
        }
    }
}

@Composable
private fun HomeActionOptionsRow(
    selectedMode: MotionPhotoProcessingMode,
    deleteOriginalAfterProcessing: Boolean,
    onModeSelected: (MotionPhotoProcessingMode) -> Unit,
    onDeleteOriginalChanged: (Boolean) -> Unit
) {
    val optionLabels = listOf(
        MotionPhotoProcessingMode.PHOTO_ONLY to stringResource(R.string.retain_photo),
        MotionPhotoProcessingMode.VIDEO_ONLY to stringResource(R.string.retain_video),
        MotionPhotoProcessingMode.SPLIT_BOTH to stringResource(R.string.retain_photo_and_video)
    )
    val density = LocalDensity.current
    val selectorHorizontalInset = 4.dp
    val selectorVerticalInset = 4.dp
    val selectedIndex = optionLabels.indexOfFirst { it.first == selectedMode }.coerceAtLeast(0)
    val optionMetrics = remember(optionLabels) {
        mutableStateListOf<OptionMetric>().apply {
            repeat(optionLabels.size) {
                add(OptionMetric(0.dp, 0.dp))
            }
        }
    }
    var selectorRootOffset by remember { mutableStateOf(0.dp) }
    val selectedMetric = optionMetrics.getOrNull(selectedIndex) ?: OptionMetric(0.dp, 0.dp)
    val indicatorOffset by animateDpAsState(
        targetValue = selectedMetric.offset,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "ModeIndicatorOffset"
    )
    val indicatorWidth by animateDpAsState(
        targetValue = selectedMetric.width,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "ModeIndicatorWidth"
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.retain_prefix),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(end = 8.dp)
        )

        Box(
            modifier = Modifier
                .wrapContentWidth()
                .height(44.dp)
                .clip(SquircleShape(22.dp, smoothing = 20))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f))
                .onGloballyPositioned { coordinates ->
                    selectorRootOffset = with(density) { coordinates.positionInRoot().x.toDp() }
                }
        ) {
            if (indicatorWidth > 0.dp) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .offset(x = indicatorOffset)
                        .width(indicatorWidth)
                        .height(44.dp - selectorVerticalInset * 2)
                        .clip(SquircleShape(18.dp, smoothing = 20))
                        .background(MaterialTheme.colorScheme.primary)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(horizontal = selectorHorizontalInset, vertical = selectorVerticalInset),
                verticalAlignment = Alignment.CenterVertically
            ) {
                optionLabels.forEachIndexed { index, (mode, label) ->
                    val interactionSource = remember { MutableInteractionSource() }
                    val optionMetric = optionMetrics[index]
                    val overlayStartPx = with(density) {
                        (indicatorOffset - optionMetric.offset).toPx()
                    }
                    val overlayEndPx = with(density) {
                        (indicatorOffset + indicatorWidth - optionMetric.offset).toPx()
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .onGloballyPositioned { coordinates ->
                                optionMetrics[index] = OptionMetric(
                                    offset = with(density) {
                                        (coordinates.positionInRoot().x.toDp() - selectorRootOffset)
                                    },
                                    width = with(density) { coordinates.size.width.toDp() }
                                )
                            }
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null
                            ) { onModeSelected(mode) },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .drawWithContent {
                                    val left = overlayStartPx.coerceIn(0f, size.width)
                                    val right = overlayEndPx.coerceIn(0f, size.width)
                                    if (right > left) {
                                        clipRect(left = left, right = right) {
                                            this@drawWithContent.drawContent()
                                        }
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(horizontal = 12.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularToggleCheckbox(
                checked = deleteOriginalAfterProcessing,
                onCheckedChange = onDeleteOriginalChanged
            )
            Text(
                text = stringResource(R.string.delete_original_file),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

private data class OptionMetric(
    val offset: Dp,
    val width: Dp
)

@Composable
private fun CircularToggleCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Box(
        modifier = Modifier
            .padding(end = 6.dp)
            .size(24.dp)
            .clip(CircleShape)
            .background(
                if (checked) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceContainerHighest
            )
            .clickable { onCheckedChange(!checked) },
        contentAlignment = Alignment.Center
    ) {
        if (checked) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(15.dp)
            )
        }
    }
}
