package com.lsy223622.motionphotomanager.ui.screen

import android.app.Activity
import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.kyant.capsule.ContinuousRoundedRectangle
import com.lsy223622.motionphotomanager.data.MotionPhoto
import com.lsy223622.motionphotomanager.ui.components.CircleCheckState
import com.lsy223622.motionphotomanager.ui.components.CircularSelectionCheckbox
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

internal const val PREVIEW_FADE_IN_DURATION_MS = 140
internal const val PREVIEW_SHARED_BOUNDS_DURATION_MS = 260
internal const val PREVIEW_EXIT_BOUNDS_DURATION_MS = 220
internal const val PREVIEW_FADE_OUT_DURATION_MS = PREVIEW_EXIT_BOUNDS_DURATION_MS

@Composable
@OptIn(ExperimentalFoundationApi::class, ExperimentalSharedTransitionApi::class)
internal fun MotionPhotoPreviewScreen(
    photos: List<MotionPhoto>,
    currentPhoto: MotionPhoto?,
    selectedIds: Set<Long>,
    previewVideoPath: String?,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onToggleSelection: (Long) -> Unit,
    onPhotoChanged: (MotionPhoto) -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope
) {
    val photo = currentPhoto ?: return
    if (photos.isEmpty()) return

    // 预览页面状态栏颜色控制
    val view = LocalView.current
    val darkTheme = isSystemInDarkTheme()
    val window = remember { (view.context as Activity).window }
    val insetsController = remember { WindowCompat.getInsetsController(window, view) }
    
    // 进入预览时设置浅色图标（白色，在黑色背景上可见）
    LaunchedEffect(Unit) {
        insetsController.isAppearanceLightStatusBars = false
        insetsController.isAppearanceLightNavigationBars = false
    }
    
    // 监听退出动画：当 AnimatedVisibility 开始退出动画时立即恢复状态栏颜色
    val isExiting = animatedVisibilityScope.transition.targetState != androidx.compose.animation.EnterExitState.Visible
    val useExitBoundsTransform = isExiting
    LaunchedEffect(isExiting) {
        if (isExiting) {
            // 退出动画开始时立即恢复状态栏颜色
            insetsController.isAppearanceLightStatusBars = !darkTheme
            insetsController.isAppearanceLightNavigationBars = !darkTheme
        }
    }

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

    BackHandler(enabled = !isExiting) {
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
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            // 禁用过渡动画期间的手势翻页，避免干扰 sharedBounds
            userScrollEnabled = !isTransitionRunning && !isExiting
        ) { page ->
            val pagePhoto = photos[page]
            val isActivePage = pagePhoto.id == currentPhoto.id

            // 根据图片实际宽高计算真实比例
            val photoAspectRatio = remember(pagePhoto.width, pagePhoto.height) {
                if (pagePhoto.width > 0 && pagePhoto.height > 0) {
                    pagePhoto.width.toFloat() / pagePhoto.height.toFloat()
                } else 1f
            }

            // 使用 Animatable 实现平滑的缩放/位移动画
            val scale = remember(pagePhoto.id) { Animatable(1f) }
            val offsetX = remember(pagePhoto.id) { Animatable(0f) }
            val offsetY = remember(pagePhoto.id) { Animatable(0f) }

            val coroutineScope = rememberCoroutineScope()

            val visualModifier = Modifier
                .offset { IntOffset(offsetX.value.toInt(), offsetY.value.toInt()) }
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints)
                    val scaledWidth = (placeable.width * scale.value).toInt()
                    val scaledHeight = (placeable.height * scale.value).toInt()
                    layout(scaledWidth, scaledHeight) {
                        val x = (scaledWidth - placeable.width) / 2
                        val y = (scaledHeight - placeable.height) / 2
                        placeable.placeWithLayer(x, y) {
                            scaleX = scale.value
                            scaleY = scale.value
                        }
                    }
                }

            val gestureModifier = Modifier
                .pointerInput(pagePhoto.id, isTransitionRunning, isExiting) {
                    if (isTransitionRunning || isExiting) return@pointerInput

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

                        if (scale.value < 1.1f && scale.value > 0.9f) {
                            coroutineScope.launch {
                                scale.animateTo(1f, tween(150))
                                offsetX.animateTo(0f, tween(150))
                                offsetY.animateTo(0f, tween(150))
                            }
                        }
                    }
                }
                .pointerInput(isActivePage, previewVideoPath, isTransitionRunning, isExiting) {
                    if (isTransitionRunning || isExiting) return@pointerInput

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
                            .then(visualModifier)
                            .aspectRatio(photoAspectRatio)
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
                PreviewSharedImage(
                    photo = pagePhoto,
                    photoAspectRatio = photoAspectRatio,
                    isActivePage = isActivePage,
                    isSelected = isActivePage && isSelected,
                    animateSelectionChrome = isActivePage && isSelected && isTransitionRunning && !isExiting,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope,
                    visualModifier = visualModifier,
                    useExitBoundsTransform = useExitBoundsTransform,
                    alpha = if (showStaticImage) 1f else 0f
                )

                // 手势拦截层
                if (!isExiting) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .then(gestureModifier)
                    )
                }
            }
        }

        if (isLoading && !isExiting) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        if (!isExiting) {
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
                    verticalAlignment = Alignment.CenterVertically,
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
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun PreviewSharedImage(
    photo: MotionPhoto,
    photoAspectRatio: Float,
    isActivePage: Boolean,
    isSelected: Boolean,
    animateSelectionChrome: Boolean,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    visualModifier: Modifier,
    useExitBoundsTransform: Boolean,
    alpha: Float
) {
    val imgContext = LocalContext.current
    val imageRequest = remember(photo.uri, photo.id) {
        ImageRequest.Builder(imgContext)
            .data(photo.uri)
            .memoryCacheKey("photo_cache_${photo.id}")
            .placeholderMemoryCacheKey("photo_cache_${photo.id}")
            .build()
    }
    val selectionChromeAlpha = remember(photo.id) { Animatable(0f) }

    LaunchedEffect(photo.id, animateSelectionChrome) {
        if (animateSelectionChrome) {
            selectionChromeAlpha.snapTo(1f)
            selectionChromeAlpha.animateTo(
                targetValue = 0f,
                animationSpec = tween(
                    durationMillis = PREVIEW_SHARED_BOUNDS_DURATION_MS,
                    easing = FastOutLinearInEasing
                )
            )
        } else {
            selectionChromeAlpha.snapTo(0f)
        }
    }

    with(sharedTransitionScope) {
        Box(
            modifier = Modifier
                .then(visualModifier)
                .aspectRatio(photoAspectRatio)
                .alpha(alpha)
        ) {
            Box(
                modifier = Modifier
                .then(
                    if (isActivePage) {
                        Modifier.sharedElement(
                            sharedContentState = rememberSharedContentState(key = "photo_${photo.id}"),
                            animatedVisibilityScope = animatedVisibilityScope,
                            boundsTransform = { _, _ ->
                                if (useExitBoundsTransform) {
                                    tween(
                                        durationMillis = PREVIEW_EXIT_BOUNDS_DURATION_MS,
                                        easing = FastOutLinearInEasing
                                    )
                                } else {
                                    spring(
                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                        stiffness = Spring.StiffnessMediumLow
                                    )
                                }
                            }
                        )
                    } else {
                        Modifier
                    }
                )
                .fillMaxSize()
            ) {
                AsyncImage(
                    model = imageRequest,
                    contentDescription = photo.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                if (isActivePage && isSelected && selectionChromeAlpha.value > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(ContinuousRoundedRectangle(10.dp))
                            .alpha(selectionChromeAlpha.value)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.28f))
                    )

                    CircularSelectionCheckbox(
                        state = CircleCheckState.Checked,
                        onClick = {},
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .alpha(selectionChromeAlpha.value)
                    )
                }
            }
        }
    }
}
