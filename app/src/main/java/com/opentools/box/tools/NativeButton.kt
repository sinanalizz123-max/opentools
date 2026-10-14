package com.opentools.box.tools

import android.content.Context
import android.widget.Button
import com.opentools.box.UiStyle

/**
 * Shared native Android button for tool screens.
 * Keeps existing click handlers while avoiding Google Material Components.
 */
class NativeButton(context: Context, primary: Boolean = true) : Button(context) {
    init {
        UiStyle.styleButton(this, primary)
    }
}
