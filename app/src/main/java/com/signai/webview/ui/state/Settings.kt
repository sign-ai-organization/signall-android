package com.signai.webview.ui.state

data class Settings(
    val debug: Boolean = false,
    val freeCam: Boolean = false,
    val grid: Boolean = true,
    val bg: Boolean = false,
)

enum class Setting {
    Debug,
    FreeCam,
    Grid,
    Background,
}
