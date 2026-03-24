package com.lsy223622.motionphotomanager.ui

import android.app.Application
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lsy223622.motionphotomanager.data.MotionPhoto
import com.lsy223622.motionphotomanager.data.MotionPhotoProcessingMode
import com.lsy223622.motionphotomanager.data.MotionPhotoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale

data class UiState(
    val photos: List<MotionPhoto> = emptyList(),
    val isLoading: Boolean = false,
    val selectedIds: Set<Long> = emptySet(),
    val selectedReclaimedBytes: Long = 0L,
    val isProcessing: Boolean = false,
    val processingPhotoIds: List<Long> = emptyList(),
    val currentProcessingPhotoId: Long? = null,
    val processedReclaimedBytes: Long = 0L,
    val processedOriginalBytes: Long = 0L,
    val processedConvertedBytes: Long = 0L,
    val processedPhotoBytes: Long = 0L,
    val processedVideoBytes: Long = 0L,
    val isStopRequested: Boolean = false,
    val progress: Int = 0,
    val totalToProcess: Int = 0,
    val isConfirming: Boolean = false,
    val previewPhoto: MotionPhoto? = null,
    val previewVideoPath: String? = null,
    val isPreviewLoading: Boolean = false,
    val processingMode: MotionPhotoProcessingMode = MotionPhotoProcessingMode.PHOTO_ONLY,
    val deleteOriginalAfterProcessing: Boolean = true
)

class MotionPhotoViewModel(application: Application) : AndroidViewModel(application) {
    private val tag = "MotionPhotoViewModel"
    private val repository = MotionPhotoRepository(application)
    private var pendingTrashUris: List<Uri> = emptyList()

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    fun loadPhotos() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            runCatching {
                repository.fetchMotionPhotos()
            }.onSuccess { photos ->
                val currentState = _uiState.value
                val selectedIds = currentState.selectedIds.intersect(photos.map { it.id }.toSet())
                val selectedReclaimedBytes = calculateReclaimedBytes(
                    photos = photos,
                    selectedIds = selectedIds,
                    mode = currentState.processingMode,
                    deleteOriginalAfterProcessing = currentState.deleteOriginalAfterProcessing
                )
                _uiState.update {
                    it.copy(
                        photos = photos,
                        selectedIds = selectedIds,
                        selectedReclaimedBytes = selectedReclaimedBytes,
                        isLoading = false
                    )
                }
            }.onFailure { throwable ->
                Log.e(tag, "Failed to load motion photos", throwable)
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun toggleSelection(photoId: Long) {
        _uiState.update { state ->
            val newSelected = if (state.selectedIds.contains(photoId)) {
                state.selectedIds - photoId
            } else {
                state.selectedIds + photoId
            }
            state.copy(
                selectedIds = newSelected,
                selectedReclaimedBytes = calculateReclaimedBytes(
                    photos = state.photos,
                    selectedIds = newSelected,
                    mode = state.processingMode,
                    deleteOriginalAfterProcessing = state.deleteOriginalAfterProcessing
                ),
                isConfirming = false
            )
        }
    }

    fun toggleSelectionForIds(photoIds: Set<Long>) {
        if (photoIds.isEmpty()) return

        _uiState.update { state ->
            val allSelected = photoIds.all { it in state.selectedIds }
            val newSelected = if (allSelected) {
                state.selectedIds - photoIds
            } else {
                state.selectedIds + photoIds
            }

            state.copy(
                selectedIds = newSelected,
                selectedReclaimedBytes = calculateReclaimedBytes(
                    photos = state.photos,
                    selectedIds = newSelected,
                    mode = state.processingMode,
                    deleteOriginalAfterProcessing = state.deleteOriginalAfterProcessing
                ),
                isConfirming = false
            )
        }
    }

    fun setProcessingMode(mode: MotionPhotoProcessingMode) {
        _uiState.update { state ->
            state.copy(
                processingMode = mode,
                selectedReclaimedBytes = calculateReclaimedBytes(
                    photos = state.photos,
                    selectedIds = state.selectedIds,
                    mode = mode,
                    deleteOriginalAfterProcessing = state.deleteOriginalAfterProcessing
                ),
                isConfirming = false
            )
        }
    }

    fun setDeleteOriginalAfterProcessing(deleteOriginalAfterProcessing: Boolean) {
        _uiState.update { state ->
            state.copy(
                deleteOriginalAfterProcessing = deleteOriginalAfterProcessing,
                selectedReclaimedBytes = calculateReclaimedBytes(
                    photos = state.photos,
                    selectedIds = state.selectedIds,
                    mode = state.processingMode,
                    deleteOriginalAfterProcessing = deleteOriginalAfterProcessing
                ),
                isConfirming = false
            )
        }
    }

    fun openPreview(photo: MotionPhoto) {
        _uiState.update {
            it.copy(
                previewPhoto = photo,
                previewVideoPath = null,
                isPreviewLoading = true
            )
        }
        viewModelScope.launch {
            val previewPath = runCatching { repository.preparePreviewVideo(photo) }
                .onFailure { throwable -> Log.e(tag, "Failed to prepare preview", throwable) }
                .getOrNull()

            _uiState.update {
                it.copy(
                    previewVideoPath = previewPath,
                    isPreviewLoading = false
                )
            }
        }
    }

    fun closePreview() {
        _uiState.update {
            it.copy(
                previewPhoto = null,
                previewVideoPath = null,
                isPreviewLoading = false
            )
        }
    }

    fun setConfirming(confirming: Boolean) {
        _uiState.update { it.copy(isConfirming = confirming) }
    }

    fun startProcessing(intentLauncher: ActivityResultLauncher<IntentSenderRequest>) {
        val state = _uiState.value
        val selectedPhotos = state.photos.filter { it.id in state.selectedIds }
        if (selectedPhotos.isEmpty()) return

        _uiState.update {
            it.copy(
                isProcessing = true,
                processingPhotoIds = selectedPhotos.map { photo -> photo.id },
                currentProcessingPhotoId = selectedPhotos.firstOrNull()?.id,
                processedReclaimedBytes = 0L,
                processedOriginalBytes = 0L,
                processedConvertedBytes = 0L,
                processedPhotoBytes = 0L,
                processedVideoBytes = 0L,
                isStopRequested = false,
                progress = 0,
                totalToProcess = selectedPhotos.size
            )
        }

        viewModelScope.launch {
            runCatching {
                val successfulUris = mutableListOf<Uri>()
                var processedReclaimedBytes = 0L
                var processedOriginalBytes = 0L
                var processedConvertedBytes = 0L
                var processedPhotoBytes = 0L
                var processedVideoBytes = 0L

                for (photo in selectedPhotos) {
                    if (_uiState.value.isStopRequested) break

                    _uiState.update { currentState ->
                        currentState.copy(currentProcessingPhotoId = photo.id)
                    }

                    val currentMode = _uiState.value.processingMode
                    val deleteOriginalAfterProcessing = _uiState.value.deleteOriginalAfterProcessing
                    val success = repository.processPhoto(photo, currentMode)
                    if (success) {
                        successfulUris.add(photo.uri)
                        processedPhotoBytes += calculatePhotoBytes(photo)
                        processedVideoBytes += calculateVideoBytes(photo)
                        processedOriginalBytes += calculateOriginalBytes(photo)
                        processedConvertedBytes += calculateConvertedBytes(photo, currentMode)
                        processedReclaimedBytes += calculateReclaimedBytes(
                            photo = photo,
                            mode = currentMode,
                            deleteOriginalAfterProcessing = deleteOriginalAfterProcessing
                        )
                    }

                    val stopRequested = _uiState.value.isStopRequested
                    _uiState.update { currentState ->
                        val remainingProcessingIds = currentState.processingPhotoIds.filterNot { it == photo.id }
                        currentState.copy(
                            processingPhotoIds = remainingProcessingIds,
                            currentProcessingPhotoId = if (stopRequested) null else remainingProcessingIds.firstOrNull(),
                            processedReclaimedBytes = processedReclaimedBytes,
                            processedOriginalBytes = processedOriginalBytes,
                            processedConvertedBytes = processedConvertedBytes,
                            processedPhotoBytes = processedPhotoBytes,
                            processedVideoBytes = processedVideoBytes,
                            progress = currentState.progress + 1
                        )
                    }

                    if (stopRequested) break
                }

                val deleteOriginalAfterProcessing = _uiState.value.deleteOriginalAfterProcessing
                if (successfulUris.isNotEmpty() && deleteOriginalAfterProcessing) {
                    pendingTrashUris = successfulUris.toList()
                    requestTrash(successfulUris, intentLauncher)
                }

                successfulUris.isNotEmpty() && deleteOriginalAfterProcessing
            }.onFailure { throwable ->
                Log.e(tag, "Failed during processing", throwable)
            }.onSuccess { requestedTrash ->
                if (!requestedTrash) {
                    loadPhotos()
                }
            }

            _uiState.update {
                it.copy(
                    isProcessing = false,
                    processingPhotoIds = emptyList(),
                    currentProcessingPhotoId = null,
                    processedReclaimedBytes = 0L,
                    processedOriginalBytes = 0L,
                    processedConvertedBytes = 0L,
                    processedPhotoBytes = 0L,
                    processedVideoBytes = 0L,
                    isStopRequested = false,
                    progress = 0,
                    totalToProcess = 0,
                    selectedIds = emptySet(),
                    selectedReclaimedBytes = 0L,
                    isConfirming = false
                )
            }
        }
    }

    fun requestStopProcessing() {
        _uiState.update { state ->
            if (!state.isProcessing) state else state.copy(isStopRequested = true)
        }
    }

    fun onTrashRequestResult() {
        pendingTrashUris = emptyList()
        loadPhotos()
    }

    private fun requestTrash(uris: List<Uri>, intentLauncher: ActivityResultLauncher<IntentSenderRequest>) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val manufacturer = Build.MANUFACTURER.lowercase(Locale.ROOT)
            val pendingIntent = if (manufacturer.contains("xiaomi")) {
                MediaStore.createDeleteRequest(
                    getApplication<Application>().contentResolver,
                    uris
                )
            } else {
                MediaStore.createTrashRequest(
                    getApplication<Application>().contentResolver,
                    uris,
                    true
                )
            }
            intentLauncher.launch(IntentSenderRequest.Builder(pendingIntent.intentSender).build())
        } else {
            // For older versions, we might need a different approach or just skip trashing for now.
        }
    }

    private fun calculateReclaimedBytes(
        photos: List<MotionPhoto>,
        selectedIds: Set<Long>,
        mode: MotionPhotoProcessingMode,
        deleteOriginalAfterProcessing: Boolean
    ): Long {
        return photos
            .asSequence()
            .filter { it.id in selectedIds }
            .sumOf {
                calculateReclaimedBytes(
                    photo = it,
                    mode = mode,
                    deleteOriginalAfterProcessing = deleteOriginalAfterProcessing
                )
            }
    }

    private fun calculateReclaimedBytes(
        photo: MotionPhoto,
        mode: MotionPhotoProcessingMode,
        deleteOriginalAfterProcessing: Boolean
    ): Long {
        if (!deleteOriginalAfterProcessing) return 0L
        return when (mode) {
            MotionPhotoProcessingMode.PHOTO_ONLY -> photo.videoOffset.coerceAtLeast(0L)
            MotionPhotoProcessingMode.VIDEO_ONLY -> (photo.size - photo.videoOffset).coerceAtLeast(0L)
            MotionPhotoProcessingMode.SPLIT_BOTH -> 0L
        }
    }

    private fun calculateOriginalBytes(photo: MotionPhoto): Long {
        return photo.size.coerceAtLeast(0L)
    }

    private fun calculatePhotoBytes(photo: MotionPhoto): Long {
        return (photo.size - photo.videoOffset.coerceAtLeast(0L)).coerceAtLeast(0L)
    }

    private fun calculateVideoBytes(photo: MotionPhoto): Long {
        return photo.videoOffset.coerceAtLeast(0L)
    }

    private fun calculateConvertedBytes(
        photo: MotionPhoto,
        mode: MotionPhotoProcessingMode
    ): Long {
        val videoBytes = calculateVideoBytes(photo)
        val photoBytes = calculatePhotoBytes(photo)
        return when (mode) {
            MotionPhotoProcessingMode.PHOTO_ONLY -> photoBytes
            MotionPhotoProcessingMode.VIDEO_ONLY -> videoBytes
            MotionPhotoProcessingMode.SPLIT_BOTH -> photoBytes + videoBytes
        }
    }
}
