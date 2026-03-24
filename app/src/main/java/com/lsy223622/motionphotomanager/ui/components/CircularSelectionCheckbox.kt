package com.lsy223622.motionphotomanager.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

enum class CircleCheckState {
    Unchecked,
    Indeterminate,
    Checked
}

@Composable
fun CircularSelectionCheckbox(
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
                else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.72f)
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
