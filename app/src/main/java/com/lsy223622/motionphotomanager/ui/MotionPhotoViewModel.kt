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
    val selectedSavingBytes: Long = 0L,
    val isProcessing: Boolean = false,
    val processingPhotoIds: List<Long> = emptyList(),
    val currentProcessingPhotoId: Long? = null,
    val processedSavingBytes: Long = 0L,
    val isStopRequested: Boolean = false,
    val progress: Int = 0,
    val totalToProcess: Int = 0,
    val isConfirming: Boolean = false,
    val previewPhoto: MotionPhoto? = null,
    val previewVideoPath: String? = null,
    val isPreviewLoading: Boolean = false
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
                val selectedIds = _uiState.value.selectedIds.intersect(photos.map { it.id }.toSet())
                val selectedSavingBytes = photos.filter { it.id in selectedIds }.sumOf { it.videoOffset.coerceAtLeast(0L) }
                _uiState.update {
                    it.copy(
                        photos = photos,
                        selectedIds = selectedIds,
                        selectedSavingBytes = selectedSavingBytes,
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
            val selectedSavingBytes = state.photos
                .asSequence()
                .filter { it.id in newSelected }
                .sumOf { it.videoOffset.coerceAtLeast(0L) }
            state.copy(selectedIds = newSelected, selectedSavingBytes = selectedSavingBytes, isConfirming = false)
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

            val selectedSavingBytes = state.photos
                .asSequence()
                .filter { it.id in newSelected }
                .sumOf { it.videoOffset.coerceAtLeast(0L) }

            state.copy(selectedIds = newSelected, selectedSavingBytes = selectedSavingBytes, isConfirming = false)
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
        val selectedPhotos = _uiState.value.photos.filter { it.id in _uiState.value.selectedIds }
        if (selectedPhotos.isEmpty()) return

        _uiState.update {
            it.copy(
                isProcessing = true,
                processingPhotoIds = selectedPhotos.map { photo -> photo.id },
                currentProcessingPhotoId = selectedPhotos.firstOrNull()?.id,
                processedSavingBytes = 0L,
                isStopRequested = false,
                progress = 0,
                totalToProcess = selectedPhotos.size
            )
        }

        viewModelScope.launch {
            runCatching {
                val successfulUris = mutableListOf<Uri>()
                var processedSavingBytes = 0L

                for (photo in selectedPhotos) {
                    if (_uiState.value.isStopRequested) break

                    _uiState.update { state ->
                        state.copy(currentProcessingPhotoId = photo.id)
                    }

                    val success = repository.compactPhoto(photo)
                    if (success) {
                        successfulUris.add(photo.uri)
                        processedSavingBytes += photo.videoOffset.coerceAtLeast(0L)
                    }

                    val stopRequested = _uiState.value.isStopRequested
                    _uiState.update { state ->
                        val remainingProcessingIds = state.processingPhotoIds.filterNot { it == photo.id }
                        state.copy(
                            processingPhotoIds = remainingProcessingIds,
                            currentProcessingPhotoId = if (stopRequested) null else remainingProcessingIds.firstOrNull(),
                            processedSavingBytes = processedSavingBytes,
                            progress = state.progress + 1
                        )
                    }

                    if (stopRequested) break
                }

                // After processing, request to trash the original photos
                if (successfulUris.isNotEmpty()) {
                    pendingTrashUris = successfulUris.toList()
                    requestTrash(successfulUris, intentLauncher)
                }

                successfulUris.isNotEmpty()
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
                    processedSavingBytes = 0L,
                    isStopRequested = false,
                    progress = 0,
                    totalToProcess = 0,
                    selectedIds = emptySet(),
                    selectedSavingBytes = 0L,
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
            // For older versions, we might need a different approach or just skip trashing for now
            // In a real app, you'd handle this more robustly.
        }
    }
}
