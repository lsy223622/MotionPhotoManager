package com.lsy223622.motionphotomanager.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.animateDpAsState
import com.lsy223622.motionphotomanager.ui.UiState
import java.util.Locale

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
