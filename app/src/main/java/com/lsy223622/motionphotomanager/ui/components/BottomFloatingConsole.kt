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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.lsy223622.motionphotomanager.R
import com.lsy223622.motionphotomanager.data.MotionPhoto
import com.lsy223622.motionphotomanager.ui.UiState
import kotlinx.coroutines.delay

private data class AnimatedThumbnailItem(
    val photo: MotionPhoto,
    val visibilityState: MutableTransitionState<Boolean>
)

@Composable
fun BottomFloatingConsole(
    uiState: UiState,
    onStartProcessing: () -> Unit,
    onSetConfirming: (Boolean) -> Unit,
    onPreviewPhoto: (MotionPhoto) -> Unit,
    modifier: Modifier = Modifier
) {
    val isSelecting = uiState.selectedIds.isNotEmpty() && !uiState.isProcessing
    val bottomActionPadding = 16.dp
    val collapsedConsoleHeight = 80.dp
    val cancelActionButtonWidth = 92.dp
    val confirmActionButtonWidth = 100.dp
    val consoleHeight by animateDpAsState(
        targetValue = if (isSelecting || uiState.isProcessing) 140.dp else collapsedConsoleHeight,
        label = "ConsoleHeight"
    )
    val animatedThumbnailItems = remember { mutableStateListOf<AnimatedThumbnailItem>() }
    val thumbnailListState = rememberLazyListState()
    var previousSelectedPhotoIds by remember { mutableStateOf<List<Long>>(emptyList()) }
    val actionButtonContainerColor by animateColorAsState(
        targetValue = if (isSelecting) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f)
        },
        animationSpec = tween(durationMillis = 220),
        label = "ActionButtonContainerColor"
    )
    val actionButtonContentColor by animateColorAsState(
        targetValue = if (isSelecting) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(durationMillis = 220),
        label = "ActionButtonContentColor"
    )
    val confirmButtonContainerColor by animateColorAsState(
        targetValue = MaterialTheme.colorScheme.primary,
        animationSpec = tween(durationMillis = 220),
        label = "ConfirmButtonContainerColor"
    )
    val confirmButtonContentColor by animateColorAsState(
        targetValue = MaterialTheme.colorScheme.onPrimary,
        animationSpec = tween(durationMillis = 220),
        label = "ConfirmButtonContentColor"
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
        targetValue = if (uiState.isConfirming) cancelActionButtonWidth else 0.dp,
        animationSpec = tween(durationMillis = 220),
        label = "CancelButtonWidth"
    )
    val confirmButtonWidth by animateDpAsState(
        targetValue = if (uiState.isConfirming) confirmActionButtonWidth else 152.dp,
        animationSpec = tween(durationMillis = 220),
        label = "ConfirmButtonWidth"
    )
    val actionButtonsGap by animateDpAsState(
        targetValue = if (uiState.isConfirming) 8.dp else 0.dp,
        animationSpec = tween(durationMillis = 220),
        label = "ActionButtonsGap"
    )
    val targetLeftLabel = when {
        uiState.isProcessing -> "hidden"
        isSelecting -> "selecting"
        else -> "idle"
    }
    var visibleLeftLabel by remember { mutableStateOf(targetLeftLabel) }
    var showLeftLabel by remember { mutableStateOf(targetLeftLabel != "hidden") }
    LaunchedEffect(targetLeftLabel) {
        if (targetLeftLabel == visibleLeftLabel) {
            showLeftLabel = targetLeftLabel != "hidden"
        } else {
            showLeftLabel = false
            delay(90)
            visibleLeftLabel = targetLeftLabel
            showLeftLabel = targetLeftLabel != "hidden"
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
    val targetPrimaryButtonLabel = if (uiState.isConfirming) "confirm" else "remove"
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
    val cancelTextAlpha by animateFloatAsState(
        targetValue = if (uiState.isConfirming) 1f else 0f,
        animationSpec = tween(durationMillis = 180),
        label = "CancelTextAlpha"
    )
    val cancelButtonAlpha by animateFloatAsState(
        targetValue = if (uiState.isConfirming) 1f else 0f,
        animationSpec = tween(durationMillis = 180),
        label = "CancelButtonAlpha"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(consoleHeight),
        shape = RoundedCornerShape(40.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 8.dp,
        shadowElevation = 8.dp
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
        ) {
            AnimatedVisibility(
                visible = isSelecting || uiState.isProcessing,
                enter = slideInVertically(
                    initialOffsetY = { it / 2 },
                    animationSpec = tween(durationMillis = 220)
                ) +
                    fadeIn(animationSpec = tween(durationMillis = 220)),
                exit = slideOutVertically(
                    targetOffsetY = { it / 2 },
                    animationSpec = tween(durationMillis = 180)
                ) + fadeOut(animationSpec = tween(durationMillis = 180)),
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 24.dp, top = 14.dp, end = 24.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (uiState.isProcessing) {
                        Text(
                            stringResource(R.string.processing_progress, uiState.progress, uiState.totalToProcess),
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
                        val selectedPhotos = remember(uiState.photos, uiState.selectedIds) {
                            uiState.photos.filter { it.id in uiState.selectedIds }
                        }
                        LaunchedEffect(selectedPhotos) {
                            val currentSelectedPhotoIds = selectedPhotos.map { it.id }
                            val addedPhotoId = currentSelectedPhotoIds.firstOrNull { it !in previousSelectedPhotoIds }
                            val removedPhotoId = previousSelectedPhotoIds.firstOrNull { it !in currentSelectedPhotoIds }
                            val scrollTargetIndex = when {
                                addedPhotoId != null -> currentSelectedPhotoIds.indexOf(addedPhotoId)
                                removedPhotoId != null -> previousSelectedPhotoIds.indexOf(removedPhotoId)
                                else -> -1
                            }
                            val selectedIds = selectedPhotos.map { it.id }.toSet()
                            val selectedOrder = selectedPhotos.mapIndexed { index, photo -> photo.id to index }.toMap()

                            selectedPhotos.forEach { photo ->
                                val existingIndex = animatedThumbnailItems.indexOfFirst { it.photo.id == photo.id }
                                if (existingIndex >= 0) {
                                    animatedThumbnailItems[existingIndex] =
                                        animatedThumbnailItems[existingIndex].copy(photo = photo)
                                    animatedThumbnailItems[existingIndex].visibilityState.targetState = true
                                } else {
                                    animatedThumbnailItems += AnimatedThumbnailItem(
                                        photo = photo,
                                        visibilityState = MutableTransitionState(false).apply {
                                            targetState = true
                                        }
                                    )
                                }
                            }

                            animatedThumbnailItems.forEach { item ->
                                if (item.photo.id !in selectedIds) {
                                    item.visibilityState.targetState = false
                                }
                            }

                            val currentOrder = animatedThumbnailItems.mapIndexed { index, item -> item.photo.id to index }.toMap()
                            animatedThumbnailItems.sortBy { item ->
                                selectedOrder[item.photo.id] ?: (currentOrder[item.photo.id] ?: Int.MAX_VALUE)
                            }

                            previousSelectedPhotoIds = currentSelectedPhotoIds

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
                        val context = LocalContext.current
                        val thumbnailLayoutInfo = thumbnailListState.layoutInfo
                        val visibleThumbnailItems = thumbnailLayoutInfo.visibleItemsInfo
                        val hasLeftOverflow = visibleThumbnailItems.firstOrNull()?.let { firstVisibleItem ->
                            firstVisibleItem.index > 0 || firstVisibleItem.offset < thumbnailLayoutInfo.viewportStartOffset
                        } == true
                        val hasRightOverflow = visibleThumbnailItems.lastOrNull()?.let { lastVisibleItem ->
                            lastVisibleItem.index < animatedThumbnailItems.lastIndex ||
                                lastVisibleItem.offset + lastVisibleItem.size > thumbnailLayoutInfo.viewportEndOffset
                        } == true

                        SubcomposeLayout(modifier = Modifier.fillMaxWidth()) { constraints ->
                            val spacingPx = 8.dp.roundToPx()
                            val looseConstraints = constraints.copy(minWidth = 0, minHeight = 0)

                            val summaryPlaceables = subcompose("summary") {
                                Text(
                                    stringResource(R.string.save_mb, totalSavedMb),
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
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(44.dp)
                                ) {
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
                                            androidx.compose.animation.AnimatedVisibility(
                                                visibleState = item.visibilityState,
                                                enter = expandHorizontally(
                                                    expandFrom = Alignment.Start,
                                                    animationSpec = tween(durationMillis = 180)
                                                ) +
                                                    fadeIn(animationSpec = tween(durationMillis = 180)) +
                                                    scaleIn(
                                                        initialScale = 0.88f,
                                                        animationSpec = tween(durationMillis = 180)
                                                    ),
                                                exit = shrinkHorizontally(
                                                    shrinkTowards = Alignment.Start,
                                                    animationSpec = tween(durationMillis = 140)
                                                ) +
                                                    fadeOut(animationSpec = tween(durationMillis = 140)) +
                                                    scaleOut(
                                                        targetScale = 0.88f,
                                                        animationSpec = tween(durationMillis = 140)
                                                    )
                                            ) {
                                                AsyncImage(
                                                    model = imageRequest,
                                                    contentDescription = null,
                                                    modifier = Modifier
                                                        .size(44.dp)
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .clickable { onPreviewPhoto(item.photo) },
                                                    contentScale = ContentScale.Crop
                                                )
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
                                it.measure(
                                    looseConstraints.copy(
                                        minWidth = listMaxWidth,
                                        maxWidth = listMaxWidth
                                    )
                                )
                            }

                            val listHeight = listPlaceables.maxOfOrNull { it.height } ?: 0
                            val layoutHeight = maxOf(summaryHeight, listHeight)

                            layout(constraints.maxWidth, layoutHeight) {
                                listPlaceables.forEach { placeable ->
                                    placeable.placeRelative(0, (layoutHeight - placeable.height) / 2)
                                }
                                summaryPlaceables.forEach { placeable ->
                                    placeable.placeRelative(
                                        x = constraints.maxWidth - placeable.width,
                                        y = (layoutHeight - placeable.height) / 2
                                    )
                                }
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
                        stringResource(R.string.select_photos),
                        modifier = Modifier.alpha(idleTextAlpha),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        stringResource(R.string.selected_count, uiState.selectedIds.size),
                        modifier = Modifier.alpha(selectingTextAlpha),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                Box(
                    modifier = Modifier
                        .width(236.dp)
                        .height(48.dp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    if (uiState.isProcessing) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.CenterEnd
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(32.dp),
                                strokeWidth = 3.dp
                            )
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(cancelButtonWidth)
                                    .fillMaxHeight()
                            ) {
                                if (cancelButtonWidth > 0.dp) {
                                    Button(
                                        onClick = { onSetConfirming(false) },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = cancelButtonContainerColor,
                                            contentColor = cancelButtonContentColor
                                        ),
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .alpha(cancelButtonAlpha),
                                        shape = RoundedCornerShape(25.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
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
                            }

                            Spacer(modifier = Modifier.width(actionButtonsGap))

                            Box(
                                modifier = Modifier
                                    .width(confirmButtonWidth)
                                    .fillMaxHeight()
                            ) {
                                Button(
                                    onClick = {
                                        if (uiState.isConfirming) {
                                            onStartProcessing()
                                        } else if (isSelecting) {
                                            onSetConfirming(true)
                                        }
                                    },
                                    enabled = true,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (uiState.isConfirming) {
                                            confirmButtonContainerColor
                                        } else {
                                            actionButtonContainerColor
                                        },
                                        contentColor = if (uiState.isConfirming) {
                                            confirmButtonContentColor
                                        } else {
                                            actionButtonContentColor
                                        },
                                        disabledContainerColor = actionButtonContainerColor,
                                        disabledContentColor = actionButtonContentColor
                                    ),
                                    modifier = Modifier.fillMaxSize(),
                                    shape = RoundedCornerShape(25.dp),
                                    contentPadding = if (uiState.isConfirming) {
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
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
