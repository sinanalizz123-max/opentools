package com.opentools.box

/** Single tool entry shown in the launcher grid. Clean-room catalogue. */
data class ToolEntry(
    val id: String,
    val title: String,
    val category: ToolCategory,
    val activityClass: Class<*>,
)

enum class ToolCategory(val label: String) {
    WIFI("WiFi"),
    BASIC("Basic"),
    CAMERA("Camera"),
    HEALTH("Health"),
    MEASURE("Measure"),
    EDITORS("Editors"),
    AUDIO("Audio"),
    EXTRA("Extra"),
}
