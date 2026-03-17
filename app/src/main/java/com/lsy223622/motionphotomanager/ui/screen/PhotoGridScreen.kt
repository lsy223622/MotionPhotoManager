package com.lsy223622.motionphotomanager.ui.screen

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.lsy223622.motionphotomanager.data.MotionPhoto
import com.lsy223622.motionphotomanager.ui.components.CircleCheckState
import com.lsy223622.motionphotomanager.ui.components.CircularSelectionCheckbox
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    modifier: Modifier = Modifier
) {
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
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
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
                        boundsTransform = { _, _ ->
                            spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMediumLow
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
