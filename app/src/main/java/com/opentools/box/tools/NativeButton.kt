package com.opentools.box.tools

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatButton
import com.opentools.box.UiStyle

/**
 * Shared native Android button for tool screens.
 * Keeps existing click handlers while avoiding Google Material Components.
 */
class NativeButton : AppCompatButton {
    constructor(context: Context) : this(context, true, null)
    constructor(context: Context, primary: Boolean) : this(context, primary, null)
    constructor(context: Context, attrs: AttributeSet?) : this(context, true, attrs)

    private var primary: Boolean = true

    private constructor(context: Context, primary: Boolean, attrs: AttributeSet?) : super(context, attrs) {
        this.primary = primary
        UiStyle.styleButton(this, primary)
    }
}
