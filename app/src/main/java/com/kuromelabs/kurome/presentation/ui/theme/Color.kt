package com.kuromelabs.kurome.UI.theme

import androidx.compose.material.Colors
import androidx.compose.ui.graphics.Color


val kurome_orange = Color(0xFFFF9833)
val kurome_black = Color(0xFF1C1B1F)

val kurome_light_primary = Color(0xFF303f9f)
val kurome_light_surface = Color(0xFFFFFBFE)
val kurome_light_on_surface = Color(0xFF1C1B1F)

val kurome_dark_primary = Color(0xffbac3ff)
val kurome_dark_surface = Color.Black
val kurome_dark_on_surface = Color(0xFFE6E1E5)
val kurome_dark_on_primary = Color(0xff112286)

val Colors.topAppBar: Color
    get() = if (isLight) kurome_light_surface else Color.Black