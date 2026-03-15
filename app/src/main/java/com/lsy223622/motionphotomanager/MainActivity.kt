package com.lsy223622.motionphotomanager

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.MediaController
import android.widget.Toast
import android.widget.VideoView
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import androidx.compose.ui.window.Dialog
import com.lsy223622.motionphotomanager.data.MotionPhoto
import com.lsy223622.motionphotomanager.ui.MotionPhotoViewModel
import com.lsy223622.motionphotomanager.ui.UiState
import com.lsy223622.motionphotomanager.ui.theme.MotionPhotoManagerTheme
import java.util.Locale

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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
                    val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) { // Android 14
                        arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED, Manifest.permission.ACCESS_MEDIA_LOCATION)
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // Android 13
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

                Box(modifier = Modifier.fillMaxSize()) {
                    Scaffold(
                        modifier = Modifier
                            .fillMaxSize()
                            .then(if (uiState.isProcessing) Modifier.blur(10.dp) else Modifier),
                        topBar = {
                            CenterAlignedTopAppBar(
                                title = { Text("Motion Photo Manager", fontWeight = FontWeight.Bold) }
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
                                onPreviewPhoto = { viewModel.openPreview(it) },
                                modifier = Modifier.padding(innerPadding)
                            )
                        }
                        
                        if (uiState.isLoading) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
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

                    BottomFloatingConsole(
                        uiState = uiState,
                        onStartProcessing = { viewModel.startProcessing(trashLauncher) },
                        onSetConfirming = { viewModel.setConfirming(it) },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 32.dp, start = 16.dp, end = 16.dp)
                    )

                    MotionPhotoPreviewDialog(
                        photo = uiState.previewPhoto,
                        previewVideoPath = uiState.previewVideoPath,
                        isLoading = uiState.isPreviewLoading,
                        onDismiss = { viewModel.closePreview() }
                    )
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun MotionPhotoGrid(
    photos: List<MotionPhoto>,
    selectedIds: Set<Long>,
    onToggleSelection: (Long) -> Unit,
    onPreviewPhoto: (MotionPhoto) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(4.dp)
    ) {
        items(photos, key = { it.id }) { photo ->
            val isSelected = selectedIds.contains(photo.id)
            Box(
                modifier = Modifier
                    .padding(4.dp)
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .combinedClickable(
                        onClick = { onToggleSelection(photo.id) },
                        onLongClick = { onPreviewPhoto(photo) }
                    )
            ) {
                AsyncImage(
                    model = photo.uri,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Motion Photo",
                    tint = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(16.dp)
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                )

                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                    )
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                    )
                }
            }
        }
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
                                modifier = Modifier.size(32.dp).padding(end = 8.dp),
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
fun MotionPhotoPreviewDialog(
    photo: MotionPhoto?,
    previewVideoPath: String?,
    isLoading: Boolean,
    onDismiss: () -> Unit
) {
    if (photo == null) return

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 10.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = photo.name,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(9f / 16f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        isLoading -> CircularProgressIndicator()
                        previewVideoPath != null -> {
                            AndroidView(
                                modifier = Modifier.fillMaxSize(),
                                factory = { ctx ->
                                    VideoView(ctx).apply {
                                        val mediaController = MediaController(ctx)
                                        mediaController.setAnchorView(this)
                                        setMediaController(mediaController)
                                        setVideoPath(previewVideoPath)
                                        setOnPreparedListener { player ->
                                            player.isLooping = true
                                            start()
                                        }
                                    }
                                },
                                update = { view ->
                                    if (view.tag != previewVideoPath) {
                                        view.tag = previewVideoPath
                                        view.setVideoPath(previewVideoPath)
                                        view.setOnPreparedListener { player ->
                                            player.isLooping = true
                                            view.start()
                                        }
                                    }
                                }
                            )
                        }
                        else -> Text("Preview unavailable", color = Color.White)
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Close")
                    }
                }
            }
        }
    }
}
