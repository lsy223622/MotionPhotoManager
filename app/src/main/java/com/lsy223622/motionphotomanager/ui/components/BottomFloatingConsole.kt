package com.lsy223622.motionphotomanager.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.animateDpAsState
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.lsy223622.motionphotomanager.R
import com.lsy223622.motionphotomanager.data.MotionPhoto
import com.lsy223622.motionphotomanager.ui.UiState

@Composable
fun BottomFloatingConsole(
    uiState: UiState,
    onStartProcessing: () -> Unit,
    onSetConfirming: (Boolean) -> Unit,
    onPreviewPhoto: (MotionPhoto) -> Unit,
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
                        val context = LocalContext.current
                        
                        LazyRow(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            contentPadding = PaddingValues(end = 16.dp)
                        ) {
                            items(selectedPhotos, key = { it.id }) { photo ->
                                val imageRequest = remember(photo.uri, photo.id) {
                                    ImageRequest.Builder(context)
                                        .data(photo.uri)
                                        .memoryCacheKey("photo_cache_${photo.id}")
                                        .placeholderMemoryCacheKey("photo_cache_${photo.id}")
                                        .build()
                                }
                                AsyncImage(
                                    model = imageRequest,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { onPreviewPhoto(photo) },
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.width(8.dp))
                        
                        Text(
                            stringResource(R.string.save_mb, totalSavedMb),
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
                // 左侧文字：idle时显示"选择照片"，selecting时显示"已选择 x 项"
                AnimatedContent(
                    targetState = when {
                        uiState.isProcessing -> "processing"
                        uiState.isConfirming -> "confirming"
                        isSelecting -> "selecting"
                        else -> "idle"
                    },
                    label = "TextState"
                ) { state ->
                    when (state) {
                        "idle" -> {
                            Text(
                                stringResource(R.string.select_photos),
                                modifier = Modifier.padding(start = 16.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        "selecting" -> {
                            Text(
                                stringResource(R.string.selected_count, uiState.selectedIds.size),
                                modifier = Modifier.padding(start = 16.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        else -> {
                            // confirming 和 processing 时不显示左侧文字
                            Spacer(modifier = Modifier)
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // 右侧按钮：始终在原位
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
                                    Text(stringResource(R.string.cancel), color = MaterialTheme.colorScheme.onSecondaryContainer)
                                }
                                Button(
                                    onClick = onStartProcessing,
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                    shape = RoundedCornerShape(25.dp)
                                ) {
                                    Text(stringResource(R.string.confirm))
                                }
                            }
                        }

                        "selecting" -> {
                            Button(
                                onClick = { onSetConfirming(true) },
                                shape = RoundedCornerShape(25.dp),
                                contentPadding = PaddingValues(horizontal = 32.dp, vertical = 12.dp)
                            ) {
                                Text(stringResource(R.string.remove_motion), fontSize = 16.sp)
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
                                Text(stringResource(R.string.remove_motion))
                            }
                        }
                    }
                }
            }
        }
    }
}
