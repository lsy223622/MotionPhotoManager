package com.lsy223622.motionphotomanager

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import android.widget.VideoView
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.lsy223622.motionphotomanager.data.MotionPhoto
import com.lsy223622.motionphotomanager.ui.MotionPhotoViewModel
import com.lsy223622.motionphotomanager.ui.UiState
import com.lsy223622.motionphotomanager.ui.theme.MotionPhotoManagerTheme
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val PREVIEW_FADE_IN_DURATION_MS = 140
private const val PREVIEW_SHARED_BOUNDS_DURATION_MS = 260
// 背景淡出与照片缩回动画同步
private const val PREVIEW_FADE_OUT_DURATION_MS = PREVIEW_SHARED_BOUNDS_DURATION_MS

/**
 * 将缩放和位移应用到 bounds，用于退出动画时从放大状态开始过渡
 */
private fun transformedBounds(bounds: Rect, scale: Float, offset: Offset): Rect {
    val center = bounds.center + offset
    val halfWidth = bounds.width * scale / 2f
    val halfHeight = bounds.height * scale / 2f
    return Rect(
        left = center.x - halfWidth,
        top = center.y - halfHeight,
        right = center.x + halfWidth,
        bottom = center.y + halfHeight
    )
}

/**
 * 统一的 sharedBounds 过渡动画曲线
 * 进入时：从小到大，带一点 overshoot
 * 退出时：从当前缩放位置平滑过渡到缩略图
 */
private fun previewBoundsTransform(
    initialBounds: Rect,
    targetBounds: Rect,
    exitScale: Float = 1f,
    exitOffset: Offset = Offset.Zero
) = if ((targetBounds.width * targetBounds.height) > (initialBounds.width * initialBounds.height)) {
    // 进入动画：Grid -> Preview（放大）
    keyframes {
        durationMillis = PREVIEW_SHARED_BOUNDS_DURATION_MS
        lerp(initialBounds, targetBounds, 1.04f) at (PREVIEW_SHARED_BOUNDS_DURATION_MS * 55 / 100) using FastOutLinearInEasing
        targetBounds at PREVIEW_SHARED_BOUNDS_DURATION_MS using LinearOutSlowInEasing
    }
} else {
    // 退出动画：Preview -> Grid（缩小）
    // 从当前缩放/位移的位置开始过渡
    val actualInitialBounds = transformedBounds(initialBounds, exitScale, exitOffset)
    keyframes {
        durationMillis = PREVIEW_SHARED_BOUNDS_DURATION_MS
        actualInitialBounds at 0 using FastOutSlowInEasing
        targetBounds at PREVIEW_SHARED_BOUNDS_DURATION_MS
    }
}

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        )
        window.isNavigationBarContrastEnforced = false
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        setContent {
            MotionPhotoManagerTheme {
                val viewModel: MotionPhotoViewModel = viewModel()
                val uiState by viewModel.uiState.collectAsState()
                val context = LocalContext.current

                val trashLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.StartIntentSenderForResult()
                ) { result ->
                    if (result.resultCode == RESULT_OK) {
                        viewModel.onTrashRequestResult(granted = true)
                        Toast.makeText(context, "Original photos moved to trash", Toast.LENGTH_SHORT).show()
                    } else {
                        viewModel.onTrashRequestResult(granted = false)
                        Toast.makeText(context, "Converted photos kept. Original photos were not moved to trash.", Toast.LENGTH_LONG).show()
                    }
                }

                val permissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions()
                ) { permissions ->
                    val allGranted = permissions.entries.all { it.value }
                    if (allGranted) {
                        viewModel.loadPhotos()
                    } else {
                        Toast.makeText(context, "Permission denied. Please grant permission in settings.", Toast.LENGTH_LONG).show()
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
                        // 共享缩放状态：退出时让 boundsTransform 从放大位置开始动画
                        var exitZoomScale by remember { mutableStateOf(1f) }
                        var exitZoomOffset by remember { mutableStateOf(Offset.Zero) }
                        if (uiState.previewPhoto != null) {
                            lastPreviewPhoto = uiState.previewPhoto
                        }

                        AnimatedVisibility(
                            visible = uiState.previewPhoto == null,
                            enter = fadeIn(
                                animationSpec = tween(
                                    durationMillis = PREVIEW_FADE_IN_DURATION_MS,
                                    easing = FastOutSlowInEasing
                                )
                            ),
                            exit = fadeOut(
                                animationSpec = tween(
                                    durationMillis = PREVIEW_FADE_OUT_DURATION_MS,
                                    easing = FastOutSlowInEasing
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
                                                    text = "Motion Photos",
                                                    style = MaterialTheme.typography.headlineSmall,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                                Text(
                                                    text = "${uiState.photos.size} photos",
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
                                        Text("No motion photos found", color = Color.Gray)
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
                                        exitZoomScale = exitZoomScale,
                                        exitZoomOffset = exitZoomOffset,
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

                        if (uiState.isProcessing) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.3f))
                                    .clickable(enabled = false) {}
                            )
                        }

                        AnimatedVisibility(
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
                                    easing = FastOutSlowInEasing
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
                                    onExitZoomChanged = { scale, offset ->
                                        exitZoomScale = scale
                                        exitZoomOffset = offset
                                    },
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
@OptIn(ExperimentalFoundationApi::class, ExperimentalSharedTransitionApi::class)
fun MotionPhotoGrid(
    photos: List<MotionPhoto>,
    selectedIds: Set<Long>,
    onToggleSelection: (Long) -> Unit,
    onToggleDaySelection: (Set<Long>) -> Unit,
    onPreviewPhoto: (MotionPhoto) -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    scrollState: LazyListState,
    exitZoomScale: Float,
    exitZoomOffset: Offset,
    modifier: Modifier = Modifier
){
    val groupedPhotos = buildDateGroups(photos)

    LazyColumn(
        state = scrollState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
    ) {
        groupedPhotos.forEach { group ->
            val selectedCount = group.photoIds.count { it in selectedIds }
            val dayCheckState = when (selectedCount) {
                0 -> CircleCheckState.Unchecked
                group.photoIds.size -> CircleCheckState.Checked
                else -> CircleCheckState.Indeterminate
            }

            stickyHeader(key = "header_${group.dateKey}") {
                DateGroupHeader(
                    title = group.title,
                    count = group.photos.size,
                    state = dayCheckState,
                    onToggle = { onToggleDaySelection(group.photoIds) }
                )
            }

            val photoRows = group.photos.chunked(4)
            items(
                items = photoRows,
                key = { rowPhotos -> "row_${group.dateKey}_${rowPhotos.first().id}" }
            ) { rowPhotos ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    rowPhotos.forEach { photo ->
                        PhotoGridItem(
                            photo = photo,
                            isSelected = selectedIds.contains(photo.id),
                            onPreviewPhoto = onPreviewPhoto,
                            onToggleSelection = onToggleSelection,
                            sharedTransitionScope = sharedTransitionScope,
                            animatedVisibilityScope = animatedVisibilityScope,
                            exitZoomScale = exitZoomScale,
                            exitZoomOffset = exitZoomOffset,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    repeat(4 - rowPhotos.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }

            item(key = "space_${group.dateKey}") {
                Spacer(modifier = Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun DateGroupHeader(
    title: String,
    count: Int,
    state: CircleCheckState,
    onToggle: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "$count",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 8.dp)
                )
                CircularSelectionCheckbox(
                    state = state,
                    onClick = onToggle
                )
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun PhotoGridItem(
    photo: MotionPhoto,
    isSelected: Boolean,
    onPreviewPhoto: (MotionPhoto) -> Unit,
    onToggleSelection: (Long) -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    exitZoomScale: Float,
    exitZoomOffset: Offset,
    modifier: Modifier = Modifier
){
    val context = LocalContext.current
    // 保持 Coil 缓存强同步
    val imageRequest = remember(photo.uri, photo.id) {
        ImageRequest.Builder(context)
            .data(photo.uri)
            .memoryCacheKey("photo_cache_${photo.id}")
            .placeholderMemoryCacheKey("photo_cache_${photo.id}")
            .build()
    }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clickable { onPreviewPhoto(photo) }
    ) {
        with(sharedTransitionScope) {
            AsyncImage(
                model = imageRequest,
                contentDescription = null,
                modifier = Modifier
                    .sharedBounds(
                        sharedContentState = rememberSharedContentState(key = "photo_${photo.id}"),
                        animatedVisibilityScope = animatedVisibilityScope,
                        boundsTransform = { initialBounds, targetBounds ->
                            previewBoundsTransform(
                                initialBounds = initialBounds,
                                targetBounds = targetBounds,
                                exitScale = exitZoomScale,
                                exitOffset = exitZoomOffset
                            )
                        },
                        resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds(
                            contentScale = ContentScale.Crop
                        ),
                        clipInOverlayDuringTransition = OverlayClip(RoundedCornerShape(10.dp))
                    )
                    .clip(RoundedCornerShape(10.dp))
                    .fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        if (isSelected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.28f))
            )
        }

        CircularSelectionCheckbox(
            state = if (isSelected) CircleCheckState.Checked else CircleCheckState.Unchecked,
            onClick = { onToggleSelection(photo.id) },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp)
        )
    }
}

private enum class CircleCheckState {
    Unchecked,
    Indeterminate,
    Checked
}

@Composable
private fun CircularSelectionCheckbox(
    state: CircleCheckState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isActive = state != CircleCheckState.Unchecked
    Box(
        modifier = modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(
                if (isActive) MaterialTheme.colorScheme.primary
                else Color.Black.copy(alpha = 0.36f)
            )
            .border(
                width = if (isActive) 0.dp else 1.dp,
                color = Color.White.copy(alpha = 0.75f),
                shape = CircleShape
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        when (state) {
            CircleCheckState.Checked -> {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(15.dp)
                )
            }

            CircleCheckState.Indeterminate -> {
                Box(
                    modifier = Modifier
                        .size(width = 11.dp, height = 2.dp)
                        .background(MaterialTheme.colorScheme.onPrimary, CircleShape)
                )
            }

            CircleCheckState.Unchecked -> Unit
        }
    }
}

private data class DatePhotoGroup(
    val dateKey: String,
    val title: String,
    val photoIds: Set<Long>,
    val photos: List<MotionPhoto>
)

private fun buildDateGroups(photos: List<MotionPhoto>): List<DatePhotoGroup> {
    val keyFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val titleFormatter = SimpleDateFormat("yyyy年M月d日 EEEE", Locale.getDefault())

    val grouped = photos
        .sortedByDescending { resolvePhotoTimestamp(it) }
        .groupBy { photo ->
            val time = resolvePhotoTimestamp(photo)
            keyFormatter.format(Date(time))
        }

    return grouped.map { (key, groupPhotos) ->
        val firstTimestamp = resolvePhotoTimestamp(groupPhotos.first())
        DatePhotoGroup(
            dateKey = key,
            title = titleFormatter.format(Date(firstTimestamp)),
            photoIds = groupPhotos.map { it.id }.toSet(),
            photos = groupPhotos
        )
    }
}

private fun resolvePhotoTimestamp(photo: MotionPhoto): Long {
    return when {
        photo.dateTaken > 0L -> photo.dateTaken
        photo.dateModified > 0L -> photo.dateModified * 1000L
        else -> 0L
    }
}

@Composable
fun BottomFloatingConsole(
    uiState: UiState,
    onStartProcessing: () -> Unit,
    onSetConfirming: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val isSelecting = uiState.selectedIds.isNotEmpty() && !uiState.isProcessing
    val consoleHeight by animateDpAsState(
        targetValue = if (isSelecting || uiState.isProcessing) 140.dp else 70.dp,
        label = "ConsoleHeight"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(consoleHeight),
        shape = RoundedCornerShape(35.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 8.dp,
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center
        ) {
            AnimatedVisibility(
                visible = isSelecting || uiState.isProcessing,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (uiState.isProcessing) {
                        Text(
                            "Processing (${uiState.progress}/${uiState.totalToProcess})",
                            fontWeight = FontWeight.Bold
                        )
                        LinearProgressIndicator(
                            progress = { if (uiState.totalToProcess > 0) uiState.progress.toFloat() / uiState.totalToProcess else 0f },
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 16.dp)
                                .height(8.dp)
                                .clip(CircleShape),
                        )
                    } else {
                        val totalSavedMb = uiState.selectedSavingBytes.toDouble() / (1024.0 * 1024.0)
                        Text(
                            "Selected ${uiState.selectedIds.size}",
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Save ~${String.format(Locale.US, "%.1f", totalSavedMb)} MB",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!isSelecting && !uiState.isProcessing) {
                    Text(
                        "Select photos",
                        modifier = Modifier.padding(start = 16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                AnimatedContent(
                    targetState = when {
                        uiState.isProcessing -> "processing"
                        uiState.isConfirming -> "confirming"
                        isSelecting -> "selecting"
                        else -> "idle"
                    },
                    label = "ButtonState"
                ) { state ->
                    when (state) {
                        "confirming" -> {
                            Row {
                                Button(
                                    onClick = { onSetConfirming(false) },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                                    modifier = Modifier.padding(end = 8.dp),
                                    shape = RoundedCornerShape(25.dp)
                                ) {
                                    Text("Cancel", color = MaterialTheme.colorScheme.onSecondaryContainer)
                                }
                                Button(
                                    onClick = onStartProcessing,
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                    shape = RoundedCornerShape(25.dp)
                                ) {
                                    Text("Confirm")
                                }
                            }
                        }

                        "selecting" -> {
                            Button(
                                onClick = { onSetConfirming(true) },
                                shape = RoundedCornerShape(25.dp),
                                contentPadding = PaddingValues(horizontal = 32.dp, vertical = 12.dp)
                            ) {
                                Text("Remove Motion", fontSize = 16.sp)
                            }
                        }

                        "processing" -> {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .size(32.dp)
                                    .padding(end = 8.dp),
                                strokeWidth = 3.dp
                            )
                        }

                        else -> {
                            Button(
                                onClick = {},
                                enabled = false,
                                shape = RoundedCornerShape(25.dp),
                                contentPadding = PaddingValues(horizontal = 32.dp, vertical = 12.dp)
                            ) {
                                Text("Remove Motion")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class, ExperimentalSharedTransitionApi::class)
private fun MotionPhotoPreviewScreen(
    photos: List<MotionPhoto>,
    currentPhoto: MotionPhoto?,
    selectedIds: Set<Long>,
    previewVideoPath: String?,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onToggleSelection: (Long) -> Unit,
    onPhotoChanged: (MotionPhoto) -> Unit,
    onExitZoomChanged: (Float, Offset) -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope
){
    val photo = currentPhoto ?: return
    if (photos.isEmpty()) return

    val initialIndex = photos.indexOfFirst { it.id == photo.id }.coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = initialIndex) { photos.size }

    var togglePlay by remember(currentPhoto.id) { mutableStateOf(false) }
    var pressPlay by remember(currentPhoto.id) { mutableStateOf(false) }
    var isMuted by remember(currentPhoto.id) { mutableStateOf(true) }
    var isPlayerReady by remember(currentPhoto.id) { mutableStateOf(false) }
    var mediaPlayerRef by remember { mutableStateOf<android.media.MediaPlayer?>(null) }
    var videoViewRef by remember { mutableStateOf<VideoView?>(null) }
    val shouldPlay = togglePlay || pressPlay

    // 监听过渡动画状态
    val isTransitionRunning = animatedVisibilityScope.transition.isRunning
    val isExiting = animatedVisibilityScope.transition.targetState == EnterExitState.PostExit

    BackHandler(enabled = true) {
        togglePlay = false
        onDismiss()
    }

    LaunchedEffect(currentPhoto.id, photos) {
        togglePlay = false
        val target = photos.indexOfFirst { it.id == currentPhoto.id }
        if (target >= 0 && pagerState.currentPage != target) {
            pagerState.scrollToPage(target)
        }
    }

    LaunchedEffect(pagerState.currentPage, photos) {
        val current = photos.getOrNull(pagerState.currentPage) ?: return@LaunchedEffect
        if (current.id != currentPhoto.id) {
            onPhotoChanged(current)
        }
    }

    LaunchedEffect(isMuted, isPlayerReady) {
        if (isPlayerReady) {
            val volume = if (isMuted) 0f else 1f
            runCatching { mediaPlayerRef?.setVolume(volume, volume) }
        }
    }

    DisposableEffect(photo.id) {
        onDispose {
            mediaPlayerRef = null
            videoViewRef?.stopPlayback()
            videoViewRef = null
        }
    }

    val activePhoto = photos.getOrNull(pagerState.currentPage) ?: currentPhoto
    val isSelected = selectedIds.contains(activePhoto.id)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            // 禁用过渡动画期间的手势翻页，避免干扰 sharedBounds
            userScrollEnabled = !isTransitionRunning
        ) { page ->
            val pagePhoto = photos[page]
            val isActivePage = pagePhoto.id == currentPhoto.id

            // 核心：使用 Animatable 实现平滑的缩放/位移动画
            val scale = remember(pagePhoto.id) { androidx.compose.animation.core.Animatable(1f) }
            val offsetX = remember(pagePhoto.id) { androidx.compose.animation.core.Animatable(0f) }
            val offsetY = remember(pagePhoto.id) { androidx.compose.animation.core.Animatable(0f) }

            // 捕获退出时的缩放状态（只在退出开始时捕获一次）
            var capturedExitScale by remember { mutableStateOf(1f) }
            var capturedExitOffsetX by remember { mutableStateOf(0f) }
            var capturedExitOffsetY by remember { mutableStateOf(0f) }
            var hasExitStarted by remember { mutableStateOf(false) }

            // 在组合期间立即捕获退出时的缩放状态（确保在 boundsTransform 调用前执行）
            if (isExiting && isActivePage && !hasExitStarted) {
                hasExitStarted = true
                capturedExitScale = scale.value
                capturedExitOffsetX = offsetX.value
                capturedExitOffsetY = offsetY.value
                // 传递给父级用于 Grid 侧的 boundsTransform
                onExitZoomChanged(capturedExitScale, Offset(capturedExitOffsetX, capturedExitOffsetY))
            }

            // 视觉变换 Modifier：退出时直接使用 1f，避免闪烁（因为缩放已经编码到 boundsTransform 中）
            val visualModifier = Modifier.graphicsLayer {
                val effectiveScale = if (hasExitStarted) 1f else scale.value
                val effectiveOffsetX = if (hasExitStarted) 0f else offsetX.value
                val effectiveOffsetY = if (hasExitStarted) 0f else offsetY.value
                scaleX = effectiveScale
                scaleY = effectiveScale
                translationX = effectiveOffsetX
                translationY = effectiveOffsetY
            }

            // 手势处理
            val coroutineScope = rememberCoroutineScope()
            val gestureModifier = Modifier
                .pointerInput(pagePhoto.id, isTransitionRunning) {
                    // 过渡动画期间禁用手势
                    if (isTransitionRunning) return@pointerInput

                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        do {
                            val event = awaitPointerEvent()
                            if (event.changes.any { it.isConsumed }) continue

                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()

                            val isZooming = event.changes.size > 1
                            val isZoomedIn = scale.value > 1.01f

                            if (isZooming || isZoomedIn) {
                                val newScale = (scale.value * zoom).coerceIn(1f, 4f)
                                if (newScale <= 1.01f) {
                                    coroutineScope.launch {
                                        scale.snapTo(1f)
                                        offsetX.snapTo(0f)
                                        offsetY.snapTo(0f)
                                    }
                                } else {
                                    coroutineScope.launch {
                                        scale.snapTo(newScale)
                                        offsetX.snapTo(offsetX.value + pan.x)
                                        offsetY.snapTo(offsetY.value + pan.y)
                                    }
                                }
                                event.changes.forEach { it.consume() }
                            }
                        } while (event.changes.any { it.pressed })

                        // 手势结束后，如果缩放接近 1f，则动画回弹到 1f
                        if (scale.value < 1.1f && scale.value > 0.9f) {
                            coroutineScope.launch {
                                scale.animateTo(1f, tween(150))
                                offsetX.animateTo(0f, tween(150))
                                offsetY.animateTo(0f, tween(150))
                            }
                        }
                    }
                }
                .pointerInput(isActivePage, previewVideoPath, isTransitionRunning) {
                    if (isTransitionRunning) return@pointerInput

                    detectTapGestures(
                        onDoubleTap = {
                            coroutineScope.launch {
                                if (scale.value > 1.1f) {
                                    scale.animateTo(1f, tween(200))
                                    offsetX.animateTo(0f, tween(200))
                                    offsetY.animateTo(0f, tween(200))
                                } else {
                                    scale.animateTo(2f, tween(200))
                                }
                            }
                        },
                        onPress = {
                            if (isActivePage && previewVideoPath != null) {
                                val releasedBeforeThreshold = withTimeoutOrNull(180L) {
                                    tryAwaitRelease()
                                } != null

                                if (!releasedBeforeThreshold) {
                                    try {
                                        pressPlay = true
                                        tryAwaitRelease()
                                    } finally {
                                        pressPlay = false
                                    }
                                }
                            }
                        }
                    )
                }

            // 最外层容器
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .offset(y = (-18).dp),
                contentAlignment = Alignment.Center
            ) {
                // 视频层
                if (isActivePage && previewVideoPath != null) {
                    AndroidView(
                        modifier = Modifier
                            .fillMaxSize()
                            .then(visualModifier)
                            .alpha(if (shouldPlay && isPlayerReady) 1f else 0f),
                        factory = { ctx ->
                            VideoView(ctx).apply {
                                videoViewRef = this
                                isClickable = false
                                isLongClickable = false
                                isFocusable = false
                                setOnTouchListener { _, _ -> false }

                                setVideoPath(previewVideoPath)
                                setOnPreparedListener { player ->
                                    isPlayerReady = true
                                    mediaPlayerRef = player
                                    player.isLooping = true
                                    val volume = if (isMuted) 0f else 1f
                                    runCatching { player.setVolume(volume, volume) }
                                    if (shouldPlay) runCatching { start() }
                                }
                            }
                        },
                        update = { view ->
                            videoViewRef = view
                            if (view.tag != previewVideoPath) {
                                view.tag = previewVideoPath
                                isPlayerReady = false
                                mediaPlayerRef = null
                                view.setVideoPath(previewVideoPath)
                            }
                            if (shouldPlay && !view.isPlaying && isPlayerReady) {
                                runCatching { view.start() }
                            }
                            if (!shouldPlay && view.isPlaying) {
                                runCatching {
                                    view.pause()
                                    view.seekTo(0)
                                }
                            }
                        }
                    )
                }

                // 静态图片层
                val showStaticImage = !shouldPlay || !isPlayerReady
                val imgContext = LocalContext.current
                val imageRequest = remember(pagePhoto.uri, pagePhoto.id) {
                    ImageRequest.Builder(imgContext)
                        .data(pagePhoto.uri)
                        .memoryCacheKey("photo_cache_${pagePhoto.id}")
                        .placeholderMemoryCacheKey("photo_cache_${pagePhoto.id}")
                        .build()
                }

                with(sharedTransitionScope) {
                    val photoAspectRatio = if (pagePhoto.width > 0 && pagePhoto.height > 0) {
                        pagePhoto.width.toFloat() / pagePhoto.height.toFloat()
                    } else {
                        1f
                    }
                    AsyncImage(
                        model = imageRequest,
                        contentDescription = pagePhoto.name,
                        modifier = Modifier
                            .then(
                                if (isActivePage) {
                                    Modifier.sharedBounds(
                                        sharedContentState = rememberSharedContentState(key = "photo_${pagePhoto.id}"),
                                        animatedVisibilityScope = animatedVisibilityScope,
                                        boundsTransform = { initialBounds, targetBounds ->
                                            previewBoundsTransform(
                                                initialBounds = initialBounds,
                                                targetBounds = targetBounds,
                                                exitScale = capturedExitScale,
                                                exitOffset = Offset(capturedExitOffsetX, capturedExitOffsetY)
                                            )
                                        },
                                        resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds(
                                            contentScale = ContentScale.Crop
                                        )
                                    )
                                } else {
                                    Modifier
                                }
                            )
                            .then(visualModifier)
                            .aspectRatio(photoAspectRatio)
                            .alpha(if (showStaticImage) 1f else 0f),
                        contentScale = ContentScale.Crop
                    )
                }

                // 手势拦截层
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(gestureModifier)
                )
            }
        }

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = activePhoto.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${pagerState.currentPage + 1} / ${photos.size}",
                        color = Color.White.copy(alpha = 0.75f),
                        style = MaterialTheme.typography.labelSmall
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    IconButton(
                        onClick = {
                            if (previewVideoPath != null && activePhoto.id == currentPhoto.id) {
                                togglePlay = !togglePlay
                            }
                        },
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector = if (shouldPlay) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (shouldPlay) "Stop video" else "Play video",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    IconButton(
                        onClick = { isMuted = !isMuted },
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = if (isMuted) "Unmute video" else "Mute video",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Box(
                        modifier = Modifier.size(38.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularSelectionCheckbox(
                            state = if (isSelected) CircleCheckState.Checked else CircleCheckState.Unchecked,
                            onClick = { onToggleSelection(activePhoto.id) }
                        )
                    }
                }
            }
        }

    }
}
