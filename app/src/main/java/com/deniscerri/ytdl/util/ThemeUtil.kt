package com.deniscerri.ytdl.util

import android.app.Activity
import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.Spanned
import android.util.TypedValue
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.text.HtmlCompat
import androidx.core.text.parseAsHtml
import androidx.preference.PreferenceManager
import com.deniscerri.ytdl.MainActivity
import com.deniscerri.ytdl.R
import com.google.android.material.color.DynamicColors


object ThemeUtil {

    private val activities = mutableListOf<Activity>()

    fun applyNightMode(context: Context) {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        val theme = sharedPreferences.getString("ytdlnis_theme", "System") ?: "System"
        when (theme.lowercase()) {
            "light" -> {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            }
            "dark", "dusk", "oled" -> {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            }
            else -> {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            }
        }
    }

    fun init(app: Application) {
        applyNightMode(app)
        app.registerActivityLifecycleCallbacks(object: Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(p0: Activity, p1: Bundle?) {
                activities.add(p0)
            }

            override fun onActivityStarted(p0: Activity) {

            }

            override fun onActivityResumed(p0: Activity) {

            }

            override fun onActivityPaused(p0: Activity) {

            }

            override fun onActivityStopped(p0: Activity) {

            }

            override fun onActivitySaveInstanceState(p0: Activity, p1: Bundle) {

            }

            override fun onActivityDestroyed(p0: Activity) {
                activities.remove(p0)
            }
        })

    }

    sealed class AppIcon(
        @param:StringRes val nameResource: Int,
        @param:DrawableRes val iconResource: Int,
        val activityAlias: String
    ) {
        object Default : AppIcon(R.string.auto, R.mipmap.ic_launcher, "Default")
        object Light : AppIcon(R.string.light, R.mipmap.ic_launcher_light, "LightIcon")
        object Dark : AppIcon(R.string.dark, R.mipmap.ic_launcher_dark, "DarkIcon")
        object Blue : AppIcon(R.string.blue, R.mipmap.ic_launcher_blue, "BlueIcon")
        object Green : AppIcon(R.string.green, R.mipmap.ic_launcher_green, "GreenIcon")
    }

    val availableIcons = listOf(
        AppIcon.Default,
        AppIcon.Light,
        AppIcon.Dark,
        AppIcon.Blue,
        AppIcon.Green,
    )

    fun recreateMain() {
        activities.firstOrNull { it.javaClass == MainActivity::class.java }?.recreate()
    }

    fun recreateAllActivities() {
        activities.forEach {
            it.recreate()
        }
    }

    fun updateThemes() {
        activities.forEach {
            updateTheme(it)
            it.recreate()
        }
    }

    fun updateTheme(activity: Activity) {
        applyNightMode(activity)
        val prefs = PreferenceManager.getDefaultSharedPreferences(activity)
        val dynamic = (prefs.getBoolean("material_you", false) || prefs.getString("theme_accent", "blue") == "Default") && android.os.Build.VERSION.SDK_INT >= 31

        // 1. Always install the concrete window theme first.
        activity.setTheme(
            if (dynamic) R.style.BaseTheme
            else themeResFor(prefs.getString("theme_accent", "blue"))
        )

        // 2. Then layer dynamic colours on top (mutates the current theme instance).
        if (dynamic) {
            DynamicColors.applyToActivityIfAvailable(activity)
        }

        if (prefs.getBoolean("high_contrast", false)) {
            activity.theme.applyStyle(R.style.Pure, true)
        }

        val theme = prefs.getString("ytdlnis_theme", "System") ?: "System"
        val iconMode = prefs.getString("ytdlnis_icon", "Default") ?: "Default"
        updateAppIcon(activity, theme, iconMode)
    }

    private fun themeResFor(accent: String?): Int = when (accent?.lowercase()) {
        "blue" -> R.style.Theme_Blue
        "red" -> R.style.Theme_Red
        "green" -> R.style.Theme_Green
        "purple" -> R.style.Theme_Purple
        "yellow" -> R.style.Theme_Yellow
        "orange" -> R.style.Theme_Orange
        "monochrome" -> R.style.Theme_Monochrome
        else -> R.style.Theme_Blue
    }

    fun getThemeColor(context: Context, colorCode: Int): Int {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        val accent = sharedPreferences.getString("theme_accent", "blue")
        return if (accent == "blue"){
            "d43c3b".toInt(16)
        }else{
            val value = TypedValue()
            context.theme.resolveAttribute(colorCode, value, true)
            value.data
        }

    }

    /**
     * Get the styled app name
     */
    fun getStyledAppName(context: Context): Spanned {
        val colorPrimary = getThemeColor(context, androidx.appcompat.R.attr.colorPrimaryDark)
        val hexColor = "#%06X".format(0xFFFFFF and colorPrimary)
        return "<span  style='color:$hexColor';>YTDL</span>nis"
            .parseAsHtml(HtmlCompat.FROM_HTML_MODE_COMPACT)
    }


    fun updateAppIcon(activity: Activity, theme: String, appIconMode: String) {
        //disable old icons
        for (appIcon in availableIcons) {
            val activityClass = "com.deniscerri.ytdl." + appIcon.activityAlias

            // remove old icons
            activity.packageManager.setComponentEnabledSetting(
                ComponentName(activity.packageName, activityClass),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
        }

        var iconMode = appIconMode
        if (appIconMode == "Default") {
            iconMode = theme
        }

        when (iconMode) {
            "LightIcon" -> {
                activity.packageManager.setComponentEnabledSetting(
                    ComponentName(activity.packageName, "com.deniscerri.ytdl.LightIcon"),
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP
                )
            }
            "DarkIcon" -> {
                activity.packageManager.setComponentEnabledSetting(
                    ComponentName(activity.packageName, "com.deniscerri.ytdl.DarkIcon"),
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP
                )
            }
            "BlueIcon" -> {
                activity.packageManager.setComponentEnabledSetting(
                    ComponentName(activity.packageName, "com.deniscerri.ytdl.BlueIcon"),
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP
                )
            }
            "GreenIcon" -> {
                activity.packageManager.setComponentEnabledSetting(
                    ComponentName(activity.packageName, "com.deniscerri.ytdl.GreenIcon"),
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP
                )
            }
            // or "System"
            else -> {
                //set dynamic icon
                activity.packageManager.setComponentEnabledSetting(
                    ComponentName(activity.packageName, "com.deniscerri.ytdl.Default"),
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP
                )
            }
        }
    }
}