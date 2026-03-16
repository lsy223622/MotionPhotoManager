package com.lsy223622.motionphotomanager.data

import android.net.Uri

data class MotionPhoto(
    val id: Long,
    val uri: Uri,
    val name: String,
    val size: Long,
    val dateTaken: Long,
    val dateModified: Long,
    val videoOffset: Long = -1,
    val isSelected: Boolean = false,
    val width: Int = 0,
    val height: Int = 0
)
