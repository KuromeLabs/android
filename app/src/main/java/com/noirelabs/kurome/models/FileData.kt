package com.noirelabs.kurome.models

import kotlinx.serialization.Serializable

@Serializable
data class FileData(val fileName: String, val isDirectory: Boolean, val size: Long)
