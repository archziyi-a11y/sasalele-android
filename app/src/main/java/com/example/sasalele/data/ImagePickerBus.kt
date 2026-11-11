package com.example.sasalele.data

import android.net.Uri
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object ImagePickerBus {
    private val _imageUriFlow = MutableSharedFlow<Uri>()
    val imageUriFlow = _imageUriFlow.asSharedFlow()

    suspend fun sendUri(uri: Uri) {
        _imageUriFlow.emit(uri)
    }
}
