package com.opentools.box.tools

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.opentools.box.ToolScaffold
import com.opentools.box.UiStyle

/** About OpenTools with version information and project links. */
class AboutActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val packageInfo = packageManager.getPackageInfo(packageName, 0)
        val versionName = packageInfo.versionName ?: "unknown"
        val versionCode = androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(packageInfo)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = UiStyle.dp(context, 20f)
            setPadding(pad, pad, pad, pad)
            addView(TextView(context).apply {
                text = "OpenTools"
                UiStyle.styleText(this, 30f, true)
            })
            addView(TextView(context).apply {
                text = "An open-source everyday utility toolbox for Android."
                UiStyle.styleText(this, 16f, false, UiStyle.muted(context))
                setPadding(0, UiStyle.dp(context, 6f), 0, UiStyle.dp(context, 18f))
            })
            addView(TextView(context).apply {
                text = "Built for useful tools, clear interfaces, and local-first behavior."
                UiStyle.styleText(this, 15f, false)
                setPadding(UiStyle.dp(context, 16f), UiStyle.dp(context, 16f),
                    UiStyle.dp(context, 16f), UiStyle.dp(context, 16f))
                background = UiStyle.rounded(context, UiStyle.surface(context), 16f, UiStyle.border(context))
            })
            addView(ToolScaffold.sectionTitle(context, "Principles"))
            addView(TextView(context).apply {
                text = "Original implementation\nNo ads or trackers\nMinimal per-tool permissions\nLocal-first utilities"
                UiStyle.styleText(this, 15f, false)
                setPadding(UiStyle.dp(context, 16f), UiStyle.dp(context, 14f),
                    UiStyle.dp(context, 16f), UiStyle.dp(context, 14f))
                background = UiStyle.rounded(context, UiStyle.surface(context), 14f, UiStyle.border(context))
            })
            addView(ToolScaffold.sectionTitle(context, "Version"))
            addView(TextView(context).apply {
                text = "Version $versionName · Build $versionCode"
                UiStyle.styleText(this, 14f, false, UiStyle.muted(context))
            })
            addView(NativeButton(context, false).apply {
                text = "View project on GitHub"
                setOnClickListener {
                    try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/sinanalizz123-max/opentools"))) }
                    catch (_: Exception) { }
                }
            }, LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = UiStyle.dp(context, 18f)
            })
        }
        setContentView(ToolScaffold.wrap(this, "About", ScrollView(this).apply {
            isFillViewport = true
            addView(content)
        }))
    }
}
