package com.lsy223622.motionphotomanager.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.lsy223622.motionphotomanager.R
import com.lsy223622.motionphotomanager.data.MotionPhoto
import com.lsy223622.motionphotomanager.ui.UiState
import kotlinx.coroutines.delay
import sv.lib.squircleshape.SquircleShape

private data class AnimatedThumbnailItem(
    val photo: MotionPhoto,
    val visibilityState: MutableTransitionState<Boolean>
)

@Composable
fun BottomFloatingConsole(
    uiState: UiState,
    onStartProcessing: () -> Unit,
    onStopProcessing: () -> Unit,
    onSetConfirming: (Boolean) -> Unit,
    onPreviewPhoto: (MotionPhoto) -> Unit,
    modifier: Modifier = Modifier
) {
    val isSelecting = uiState.selectedIds.isNotEmpty() && !uiState.isProcessing
    val showExpandedConsole = isSelecting || uiState.isProcessing
    val bottomActionPadding = 16.dp
    val collapsedConsoleHeight = 80.dp
    val cancelActionButtonWidth = 92.dp
    val confirmActionButtonWidth = 100.dp
    val consoleHeight by animateDpAsState(
        targetValue = if (showExpandedConsole) 140.dp else collapsedConsoleHeight,
        label = "ConsoleHeight"
    )
    val animatedThumbnailItems = remember { mutableStateListOf<AnimatedThumbnailItem>() }
    val thumbnailListState = rememberLazyListState()
    var previousDisplayPhotoIds by remember { mutableStateOf<List<Long>>(emptyList()) }

    val displayPhotos = remember(uiState.photos, uiState.selectedIds, uiState.processingPhotoIds, uiState.isProcessing) {
        val photoById = uiState.photos.associateBy { it.id }
        if (uiState.isProcessing) {
            uiState.processingPhotoIds.mapNotNull(photoById::get)
        } else {
            uiState.photos.filter { it.id in uiState.selectedIds }
        }
    }
    val highlightedPhotoId = if (uiState.isProcessing) uiState.currentProcessingPhotoId else null
    val summaryBytes = if (uiState.isProcessing) uiState.processedSavingBytes else uiState.selectedSavingBytes
    val summaryMb = summaryBytes.toDouble() / (1024.0 * 1024.0)
    val context = LocalContext.current

    val actionButtonContainerColor by animateColorAsState(
        targetValue = if (isSelecting) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f),
        animationSpec = tween(durationMillis = 220),
        label = "ActionButtonContainerColor"
    )
    val actionButtonContentColor by animateColorAsState(
        targetValue = if (isSelecting) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(durationMillis = 220),
        label = "ActionButtonContentColor"
    )
    val primaryButtonContainerColor by animateColorAsState(
        targetValue = when {
            uiState.isProcessing && uiState.isStopRequested -> MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
            uiState.isProcessing -> MaterialTheme.colorScheme.error
            uiState.isConfirming -> MaterialTheme.colorScheme.primary
            else -> actionButtonContainerColor
        },
        animationSpec = tween(durationMillis = 220),
        label = "PrimaryButtonContainerColor"
    )
    val primaryButtonContentColor by animateColorAsState(
        targetValue = when {
            uiState.isProcessing -> MaterialTheme.colorScheme.onError
            uiState.isConfirming -> MaterialTheme.colorScheme.onPrimary
            else -> actionButtonContentColor
        },
        animationSpec = tween(durationMillis = 220),
        label = "PrimaryButtonContentColor"
    )
    val cancelButtonContainerColor by animateColorAsState(
        targetValue = MaterialTheme.colorScheme.secondary,
        animationSpec = tween(durationMillis = 220),
        label = "CancelButtonContainerColor"
    )
    val cancelButtonContentColor by animateColorAsState(
        targetValue = MaterialTheme.colorScheme.onSecondary,
        animationSpec = tween(durationMillis = 220),
        label = "CancelButtonContentColor"
    )
    val cancelButtonWidth by animateDpAsState(
        targetValue = if (uiState.isConfirming && !uiState.isProcessing) cancelActionButtonWidth else 0.dp,
        animationSpec = tween(durationMillis = 220),
        label = "CancelButtonWidth"
    )
    val confirmButtonWidth by animateDpAsState(
        targetValue = if (uiState.isConfirming || uiState.isProcessing) confirmActionButtonWidth else 152.dp,
        animationSpec = tween(durationMillis = 220),
        label = "ConfirmButtonWidth"
    )
    val actionButtonsGap by animateDpAsState(
        targetValue = if (uiState.isConfirming && !uiState.isProcessing) 8.dp else 0.dp,
        animationSpec = tween(durationMillis = 220),
        label = "ActionButtonsGap"
    )
    val cancelTextAlpha by animateFloatAsState(
        targetValue = if (uiState.isConfirming && !uiState.isProcessing) 1f else 0f,
        animationSpec = tween(durationMillis = 180),
        label = "CancelTextAlpha"
    )
    val cancelButtonAlpha by animateFloatAsState(
        targetValue = if (uiState.isConfirming && !uiState.isProcessing) 1f else 0f,
        animationSpec = tween(durationMillis = 180),
        label = "CancelButtonAlpha"
    )

    val targetLeftLabel = when {
        uiState.isProcessing -> "processing"
        isSelecting -> "selecting"
        else -> "idle"
    }
    var visibleLeftLabel by remember { mutableStateOf(targetLeftLabel) }
    var showLeftLabel by remember { mutableStateOf(true) }
    LaunchedEffect(targetLeftLabel) {
        if (targetLeftLabel == visibleLeftLabel) {
            showLeftLabel = true
        } else {
            showLeftLabel = false
            delay(90)
            visibleLeftLabel = targetLeftLabel
            showLeftLabel = true
        }
    }
    val idleTextAlpha by animateFloatAsState(
        targetValue = if (showLeftLabel && visibleLeftLabel == "idle") 1f else 0f,
        animationSpec = tween(durationMillis = 90),
        label = "IdleTextAlpha"
    )
    val selectingTextAlpha by animateFloatAsState(
        targetValue = if (showLeftLabel && visibleLeftLabel == "selecting") 1f else 0f,
        animationSpec = tween(durationMillis = 90),
        label = "SelectingTextAlpha"
    )
    val processingTextAlpha by animateFloatAsState(
        targetValue = if (showLeftLabel && visibleLeftLabel == "processing") 1f else 0f,
        animationSpec = tween(durationMillis = 90),
        label = "ProcessingTextAlpha"
    )

    val targetPrimaryButtonLabel = when {
        uiState.isProcessing -> "stop"
        uiState.isConfirming -> "confirm"
        else -> "remove"
    }
    var visiblePrimaryButtonLabel by remember { mutableStateOf(targetPrimaryButtonLabel) }
    var showPrimaryButtonLabel by remember { mutableStateOf(true) }
    LaunchedEffect(targetPrimaryButtonLabel) {
        if (targetPrimaryButtonLabel != visiblePrimaryButtonLabel) {
            showPrimaryButtonLabel = false
            delay(90)
            visiblePrimaryButtonLabel = targetPrimaryButtonLabel
            showPrimaryButtonLabel = true
        } else {
            showPrimaryButtonLabel = true
        }
    }
    val removeMotionTextAlpha by animateFloatAsState(
        targetValue = if (showPrimaryButtonLabel && visiblePrimaryButtonLabel == "remove") 1f else 0f,
        animationSpec = tween(durationMillis = 90),
        label = "RemoveMotionTextAlpha"
    )
    val confirmTextAlpha by animateFloatAsState(
        targetValue = if (showPrimaryButtonLabel && visiblePrimaryButtonLabel == "confirm") 1f else 0f,
        animationSpec = tween(durationMillis = 90),
        label = "ConfirmTextAlpha"
    )
    val stopTextAlpha by animateFloatAsState(
        targetValue = if (showPrimaryButtonLabel && visiblePrimaryButtonLabel == "stop") 1f else 0f,
        animationSpec = tween(durationMillis = 90),
        label = "StopTextAlpha"
    )

    LaunchedEffect(displayPhotos) {
        val currentDisplayPhotoIds = displayPhotos.map { it.id }
        val addedPhotoId = currentDisplayPhotoIds.firstOrNull { it !in previousDisplayPhotoIds }
        val scrollTargetIndex = if (addedPhotoId != null) currentDisplayPhotoIds.indexOf(addedPhotoId) else -1
        val displayPhotoIds = currentDisplayPhotoIds.toSet()
        val displayOrder = displayPhotos.mapIndexed { index, photo -> photo.id to index }.toMap()

        displayPhotos.forEach { photo ->
            val existingIndex = animatedThumbnailItems.indexOfFirst { it.photo.id == photo.id }
            if (existingIndex >= 0) {
                animatedThumbnailItems[existingIndex] = animatedThumbnailItems[existingIndex].copy(photo = photo)
                animatedThumbnailItems[existingIndex].visibilityState.targetState = true
            } else {
                animatedThumbnailItems += AnimatedThumbnailItem(
                    photo = photo,
                    visibilityState = MutableTransitionState(false).apply { targetState = true }
                )
            }
        }

        animatedThumbnailItems.forEach { item ->
            if (item.photo.id !in displayPhotoIds) {
                item.visibilityState.targetState = false
            }
        }

        val currentOrder = animatedThumbnailItems.mapIndexed { index, item -> item.photo.id to index }.toMap()
        animatedThumbnailItems.sortBy { item ->
            displayOrder[item.photo.id] ?: (currentOrder[item.photo.id] ?: Int.MAX_VALUE)
        }

        previousDisplayPhotoIds = currentDisplayPhotoIds

        if (scrollTargetIndex >= 0 && animatedThumbnailItems.isNotEmpty()) {
            val targetIndex = scrollTargetIndex.coerceAtMost(animatedThumbnailItems.lastIndex)
            withFrameNanos { }
            val layoutInfo = thumbnailListState.layoutInfo
            val targetItemInfo = layoutInfo.visibleItemsInfo.firstOrNull { it.index == targetIndex }
            val isFullyVisible = targetItemInfo != null &&
                targetItemInfo.offset >= layoutInfo.viewportStartOffset &&
                targetItemInfo.offset + targetItemInfo.size <= layoutInfo.viewportEndOffset

            if (!isFullyVisible) {
                thumbnailListState.animateScrollToItem(index = targetIndex)
            }
        }
    }

    Surface(
        modifier = modifier.fillMaxWidth().height(consoleHeight),
        shape = SquircleShape(40.dp, smoothing = 20),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 8.dp,
        shadowElevation = 8.dp
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AnimatedVisibility(
                visible = showExpandedConsole,
                enter = slideInVertically(
                    initialOffsetY = { it / 2 },
                    animationSpec = tween(durationMillis = 220)
                ) + fadeIn(animationSpec = tween(durationMillis = 220)),
                exit = slideOutVertically(
                    targetOffsetY = { it / 2 },
                    animationSpec = tween(durationMillis = 180)
                ) + fadeOut(animationSpec = tween(durationMillis = 180)),
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                val thumbnailLayoutInfo = thumbnailListState.layoutInfo
                val visibleThumbnailItems = thumbnailLayoutInfo.visibleItemsInfo
                val hasLeftOverflow = visibleThumbnailItems.firstOrNull()?.let { item ->
                    item.index > 0 || item.offset < thumbnailLayoutInfo.viewportStartOffset
                } == true
                val hasRightOverflow = visibleThumbnailItems.lastOrNull()?.let { item ->
                    item.index < animatedThumbnailItems.lastIndex ||
                        item.offset + item.size > thumbnailLayoutInfo.viewportEndOffset
                } == true

                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 24.dp, top = 14.dp, end = 24.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SubcomposeLayout(modifier = Modifier.fillMaxWidth()) { constraints ->
                        val spacingPx = 8.dp.roundToPx()
                        val looseConstraints = constraints.copy(minWidth = 0, minHeight = 0)

                        val summaryPlaceables = subcompose("summary") {
                            Text(
                                text = stringResource(R.string.save_mb, summaryMb),
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }.map { it.measure(looseConstraints) }

                        val summaryWidth = summaryPlaceables.maxOfOrNull { it.width } ?: 0
                        val summaryHeight = summaryPlaceables.maxOfOrNull { it.height } ?: 0
                        val listMaxWidth = (constraints.maxWidth - summaryWidth - spacingPx).coerceAtLeast(0)

                        val listPlaceables = subcompose("thumbnails") {
                            Box(modifier = Modifier.fillMaxWidth().height(44.dp)) {
                                LazyRow(
                                    state = thumbnailListState,
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    contentPadding = PaddingValues(end = 16.dp)
                                ) {
                                    items(animatedThumbnailItems, key = { it.photo.id }) { item ->
                                        LaunchedEffect(item.visibilityState.isIdle, item.visibilityState.currentState) {
                                            if (item.visibilityState.isIdle && !item.visibilityState.currentState) {
                                                animatedThumbnailItems.removeAll { it.photo.id == item.photo.id }
                                            }
                                        }
                                        val imageRequest = remember(item.photo.uri, item.photo.id) {
                                            ImageRequest.Builder(context)
                                                .data(item.photo.uri)
                                                .memoryCacheKey("photo_cache_${item.photo.id}")
                                                .placeholderMemoryCacheKey("photo_cache_${item.photo.id}")
                                                .build()
                                        }
                                        val shape = SquircleShape(8.dp, smoothing = 20)
                                        val isHighlighted = item.photo.id == highlightedPhotoId
                                        androidx.compose.animation.AnimatedVisibility(
                                            visibleState = item.visibilityState,
                                            enter = expandHorizontally(
                                                expandFrom = Alignment.Start,
                                                animationSpec = tween(durationMillis = 180)
                                            ) + fadeIn(animationSpec = tween(durationMillis = 180)) + scaleIn(
                                                initialScale = 0.88f,
                                                animationSpec = tween(durationMillis = 180)
                                            ),
                                            exit = shrinkHorizontally(
                                                shrinkTowards = Alignment.Start,
                                                animationSpec = tween(durationMillis = 140)
                                            ) + fadeOut(animationSpec = tween(durationMillis = 140)) + scaleOut(
                                                targetScale = 0.88f,
                                                animationSpec = tween(durationMillis = 140)
                                            )
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(44.dp)
                                                    .clip(shape)
                                                    .border(
                                                        width = if (isHighlighted) 2.dp else 0.dp,
                                                        color = if (isHighlighted) MaterialTheme.colorScheme.error else Color.Transparent,
                                                        shape = shape
                                                    )
                                                    .clickable { onPreviewPhoto(item.photo) }
                                            ) {
                                                AsyncImage(
                                                    model = imageRequest,
                                                    contentDescription = null,
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .padding(if (isHighlighted) 2.dp else 0.dp)
                                                        .clip(shape),
                                                    contentScale = ContentScale.Crop
                                                )
                                            }
                                        }
                                    }
                                }

                                if (hasLeftOverflow) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.CenterStart)
                                            .width(6.dp)
                                            .fillMaxHeight()
                                            .background(
                                                Brush.horizontalGradient(
                                                    colors = listOf(
                                                        MaterialTheme.colorScheme.surfaceVariant,
                                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0f)
                                                    )
                                                )
                                            )
                                    )
                                }

                                if (hasRightOverflow) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.CenterEnd)
                                            .width(6.dp)
                                            .fillMaxHeight()
                                            .background(
                                                Brush.horizontalGradient(
                                                    colors = listOf(
                                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0f),
                                                        MaterialTheme.colorScheme.surfaceVariant
                                                    )
                                                )
                                            )
                                    )
                                }
                            }
                        }.map {
                            it.measure(looseConstraints.copy(minWidth = listMaxWidth, maxWidth = listMaxWidth))
                        }

                        val listHeight = listPlaceables.maxOfOrNull { it.height } ?: 0
                        val layoutHeight = maxOf(summaryHeight, listHeight)
                        layout(constraints.maxWidth, layoutHeight) {
                            listPlaceables.forEach { it.placeRelative(0, (layoutHeight - it.height) / 2) }
                            summaryPlaceables.forEach {
                                it.placeRelative(
                                    x = constraints.maxWidth - it.width,
                                    y = (layoutHeight - it.height) / 2
                                )
                            }
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(86.dp)
                    .background(
                        brush = Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.0f to Color.Transparent,
                                0.18f to MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
                                0.32f to MaterialTheme.colorScheme.surfaceVariant,
                                1.0f to MaterialTheme.colorScheme.surfaceVariant
                            )
                        )
                    )
            )

            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(start = 16.dp, top = 6.dp, end = 16.dp, bottom = bottomActionPadding),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.padding(start = 16.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = stringResource(R.string.select_photos),
                        modifier = Modifier.alpha(idleTextAlpha),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(R.string.selected_count, uiState.selectedIds.size),
                        modifier = Modifier.alpha(selectingTextAlpha),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = stringResource(R.string.progress_fraction, uiState.progress, uiState.totalToProcess),
                        modifier = Modifier.alpha(processingTextAlpha),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                Row(
                    modifier = Modifier.height(48.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.width(cancelButtonWidth).fillMaxHeight()
                    ) {
                        if (cancelButtonWidth > 0.dp) {
                            Button(
                                onClick = { onSetConfirming(false) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = cancelButtonContainerColor,
                                    contentColor = cancelButtonContentColor
                                ),
                                modifier = Modifier.fillMaxSize().alpha(cancelButtonAlpha),
                                shape = SquircleShape(25.dp, smoothing = 20),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.cancel),
                                    modifier = Modifier.alpha(cancelTextAlpha),
                                    fontSize = 16.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Clip
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(actionButtonsGap))

                    Box(
                        modifier = Modifier.width(confirmButtonWidth).fillMaxHeight()
                    ) {
                        Button(
                            onClick = {
                                when {
                                    uiState.isProcessing -> onStopProcessing()
                                    uiState.isConfirming -> onStartProcessing()
                                    isSelecting -> onSetConfirming(true)
                                }
                            },
                            enabled = !uiState.isProcessing || !uiState.isStopRequested,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = primaryButtonContainerColor,
                                contentColor = primaryButtonContentColor,
                                disabledContainerColor = primaryButtonContainerColor,
                                disabledContentColor = primaryButtonContentColor
                            ),
                            modifier = Modifier.fillMaxSize(),
                            shape = SquircleShape(25.dp, smoothing = 20),
                            contentPadding = if (uiState.isConfirming || uiState.isProcessing) {
                                PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                            } else {
                                PaddingValues(horizontal = 32.dp, vertical = 12.dp)
                            }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = stringResource(R.string.remove_motion),
                                    modifier = Modifier.alpha(removeMotionTextAlpha),
                                    fontSize = 16.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Clip
                                )
                                Text(
                                    text = stringResource(R.string.confirm),
                                    modifier = Modifier.alpha(confirmTextAlpha),
                                    fontSize = 16.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Clip
                                )
                                Text(
                                    text = stringResource(R.string.stop),
                                    modifier = Modifier.alpha(stopTextAlpha),
                                    fontSize = 16.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Clip
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
