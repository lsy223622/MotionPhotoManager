package com.lsy223622.motionphotomanager

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import android.widget.VideoView
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.lsy223622.motionphotomanager.data.MotionPhoto
import com.lsy223622.motionphotomanager.ui.MotionPhotoViewModel
import com.lsy223622.motionphotomanager.ui.UiState
import com.lsy223622.motionphotomanager.ui.theme.MotionPhotoManagerTheme
import java.text.SimpleDateFormat
import java.util.Date
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

                Box(modifier = Modifier.fillMaxSize()) {
                    Scaffold(
                        modifier = Modifier
                            .fillMaxSize()
                            .then(if (uiState.isProcessing) Modifier.blur(10.dp) else Modifier),
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
                                colors = TopAppBarDefaults.largeTopAppBarColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
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

                    MotionPhotoPreviewScreen(
                        photos = uiState.photos,
                        currentPhoto = uiState.previewPhoto,
                        selectedIds = uiState.selectedIds,
                        previewVideoPath = uiState.previewVideoPath,
                        isLoading = uiState.isPreviewLoading,
                        onDismiss = { viewModel.closePreview() },
                        onToggleSelection = { viewModel.toggleSelection(it) },
                        onPhotoChanged = { viewModel.openPreview(it) }
                    )

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
@OptIn(ExperimentalFoundationApi::class)
fun MotionPhotoGrid(
    photos: List<MotionPhoto>,
    selectedIds: Set<Long>,
    onToggleSelection: (Long) -> Unit,
    onToggleDaySelection: (Set<Long>) -> Unit,
    onPreviewPhoto: (MotionPhoto) -> Unit,
    modifier: Modifier = Modifier
) {
    val groupedPhotos = buildDateGroups(photos)

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
    ) {
        groupedPhotos.forEach { group ->
            val selectedCount = group.photoIds.count { it in selectedIds }
            val dayCheckState = when {
                selectedCount == 0 -> CircleCheckState.Unchecked
                selectedCount == group.photoIds.size -> CircleCheckState.Checked
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
                fontWeight = FontWeight.SemiBold
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

@Composable
private fun PhotoGridItem(
    photo: MotionPhoto,
    isSelected: Boolean,
    onPreviewPhoto: (MotionPhoto) -> Unit,
    onToggleSelection: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(10.dp))
            .clickable { onPreviewPhoto(photo) }
    ) {
        AsyncImage(
            model = photo.uri,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        if (isSelected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
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
@OptIn(ExperimentalFoundationApi::class)
private fun MotionPhotoPreviewScreen(
    photos: List<MotionPhoto>,
    currentPhoto: MotionPhoto?,
    selectedIds: Set<Long>,
    previewVideoPath: String?,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onToggleSelection: (Long) -> Unit,
    onPhotoChanged: (MotionPhoto) -> Unit
) {
    val photo = currentPhoto ?: return
    if (photos.isEmpty()) return
    BackHandler(enabled = true) { onDismiss() }

    val initialIndex = photos.indexOfFirst { it.id == photo.id }.coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = initialIndex) { photos.size }

    var shouldPlay by remember(currentPhoto.id) { mutableStateOf(false) }
    var isMuted by remember(currentPhoto.id) { mutableStateOf(true) }
    var isPlayerReady by remember(currentPhoto.id) { mutableStateOf(false) }
    var mediaPlayerRef by remember { mutableStateOf<android.media.MediaPlayer?>(null) }
    var videoViewRef by remember { mutableStateOf<VideoView?>(null) }

    LaunchedEffect(currentPhoto.id, photos) {
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 96.dp, bottom = 220.dp),
            contentAlignment = Alignment.Center
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val pagePhoto = photos[page]
                val isActivePage = pagePhoto.id == currentPhoto.id

                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (isActivePage && shouldPlay && previewVideoPath != null) {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { ctx ->
                                VideoView(ctx).apply {
                                    videoViewRef = this
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
                                    runCatching { view.pause() }
                                }
                            }
                        )
                    } else {
                        AsyncImage(
                            model = pagePhoto.uri,
                            contentDescription = pagePhoto.name,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    }
                }
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
                Text(
                    text = activePhoto.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                CircularSelectionCheckbox(
                    state = if (isSelected) CircleCheckState.Checked else CircleCheckState.Unchecked,
                    onClick = { onToggleSelection(activePhoto.id) },
                    modifier = Modifier.padding(start = 8.dp)
                )

                IconButton(
                    onClick = {
                        if (previewVideoPath != null && activePhoto.id == currentPhoto.id) {
                            shouldPlay = !shouldPlay
                        }
                    },
                    modifier = Modifier
                        .padding(start = 10.dp)
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.45f))
                ) {
                    Icon(
                        imageVector = if (shouldPlay) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (shouldPlay) "Pause video" else "Play video",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                IconButton(
                    onClick = { isMuted = !isMuted },
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.45f))
                ) {
                    Icon(
                        imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = if (isMuted) "Unmute video" else "Mute video",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        Text(
            text = "${pagerState.currentPage + 1} / ${photos.size}",
            color = Color.White.copy(alpha = 0.85f),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 236.dp)
                .background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                .padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}
