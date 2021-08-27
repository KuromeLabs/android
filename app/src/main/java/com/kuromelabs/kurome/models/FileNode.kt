package com.kuromelabs.kurome.models

import kotlinx.serialization.Serializable

@Serializable
data class FileNode(val fileName: String, val isDirectory: Boolean, val size: Long)