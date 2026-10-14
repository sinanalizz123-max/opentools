package com.opentools.box

/** Single tool entry shown in the launcher grid. Clean-room catalogue. */
data class ToolEntry(
    val id: String,
    val title: String,
    val category: ToolCategory,
    val activityClass: Class<*>,
)

enum class ToolCategory(val label: String) {
    LIGHT("Light"),
    SENSORS("Sensors"),
    AUDIO("Audio"),
    EVERYDAY("Everyday"),
    NETWORK("Network"),
    MEDIA("Media"),
}
