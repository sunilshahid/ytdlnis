package com.deniscerri.ytdl.util

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.widget.PopupMenu
import androidx.core.view.forEach
import androidx.core.view.get
import androidx.preference.PreferenceManager
import com.deniscerri.ytdl.R
import com.google.android.material.navigation.NavigationBarView


object NavbarUtil {

    lateinit var settings : SharedPreferences

    fun init(context: Context){
        settings = PreferenceManager.getDefaultSharedPreferences(context)
    }

    private var navItems = mapOf(
        "Home" to R.id.homeFragment,
        "History" to R.id.historyFragment,
        "Play" to R.id.playFragment,
        "Queue" to R.id.downloadQueueMainFragment,
        "Terminal" to R.id.terminalActivity,
        "More" to R.id.moreFragment
    )

    /**
     * Order of the old, index-based navigation_bar pref. It is only used by the one-time
     * migration; everything after that stores stable resource entry names.
     */
    private val legacyMenuOrder = listOf(
        "homeFragment",
        "historyFragment",
        "downloadQueueMainFragment",
        "moreFragment",
        "terminalActivity"
    )

    private const val DEFAULT_NAV_BAR =
        "homeFragment,historyFragment,downloadQueueMainFragment,playFragment,moreFragment,-terminalActivity"

    fun getStartFragmentId(context: Context) : Int {
        val pref = settings.getString("start_destination", "")!!
        if (pref == "") return R.id.homeFragment
        val targetId = navItems[pref] ?: return R.id.homeFragment
        val navBarHasPref = getNavBarItems(context).any { it.itemId == targetId && it.isVisible }
        return if (navBarHasPref) targetId else R.id.homeFragment
    }

    fun setNavBarItems(items: List<MenuItem>, context: Context) {
        val prefString = items.map { item ->
            val name = context.resources.getResourceEntryName(item.itemId)
            if (item.isVisible) name else "-$name"
        }
        settings.edit().putString("navigation_bar", prefString.joinToString(",")).apply()
    }

    fun setStartFragment(itemId: Int) {
        settings.edit().putString("start_destination", navItems.filter { it.value == itemId }.keys.first()).apply()
    }

    fun getNavBarItems(context: Context): List<MenuItem> {
        migrateNavBarPrefsIfNeeded(context)
        val p = PopupMenu(context, null)
        MenuInflater(context).inflate(R.menu.bottom_nav_menu, p.menu)
        val allItems = mutableListOf<MenuItem>()
        p.menu.forEach { allItems.add(it) }

        val prefTokens = try {
            getNavBarPrefs()
        } catch (e: Exception) {
            Log.e("fail to parse nav items", e.toString())
            return allItems
        }

        val byName = allItems.associateBy { context.resources.getResourceEntryName(it.itemId) }
        val result = mutableListOf<MenuItem>()
        val used = mutableSetOf<String>()

        for (token in prefTokens) {
            val hidden = token.startsWith("-")
            val name = token.removePrefix("-")
            val item = byName[name] ?: continue
            if (used.add(name)) {
                item.isVisible = !hidden
                result.add(item)
            }
        }

        // A tab added in a later version is appended hidden instead of resetting the user's
        // custom navigation bar.
        allItems.forEach { item ->
            val name = context.resources.getResourceEntryName(item.itemId)
            if (used.add(name)) {
                item.isVisible = false
                result.add(item)
            }
        }
        return result
    }

    private fun getNavBarPrefs(): List<String> {
        return settings
            .getString("navigation_bar", DEFAULT_NAV_BAR)!!
            .split(",")
            .filter { it.isNotBlank() }
    }

    /**
     * Converts the old index-based value ("0,1,2,3,-4") into stable resource entry names.
     * The newly added Play tab is appended hidden so existing users keep their exact bar and can
     * enable it from the navigation settings.
     */
    private fun migrateNavBarPrefsIfNeeded(context: Context) {
        val raw = settings.getString("navigation_bar", null) ?: return
        val tokens = raw.split(",").filter { it.isNotBlank() }
        if (tokens.isEmpty()) return

        val numeric = tokens.all { it.removePrefix("-").toIntOrNull() != null }
        val migrated = mutableListOf<String>()
        if (numeric) {
            tokens.forEach { token ->
                val index = token.removePrefix("-").toIntOrNull() ?: return@forEach
                val name = legacyMenuOrder.getOrNull(index) ?: return@forEach
                migrated.add(if (token.startsWith("-")) "-$name" else name)
            }
        } else {
            migrated.addAll(tokens)
        }

        if (migrated.none { it.removePrefix("-") == "playFragment" }) {
            migrated.add("-playFragment")
        }
        val newValue = migrated.joinToString(",")
        if (newValue != raw) {
            settings.edit().putString("navigation_bar", newValue).apply()
        }
    }

    private fun getDefaultNavBarItems(context: Context): List<MenuItem> {
        val p = PopupMenu(context, null)
        MenuInflater(context).inflate(R.menu.bottom_nav_menu, p.menu)
        val navBarItems = mutableListOf<MenuItem>()
        p.menu.forEach {
            navBarItems.add(it)
        }
        return navBarItems
    }

    fun NavigationBarView.setLabelVisibility() {
        val labelVisibilityMode = when (
            settings.getString("label_visibility", "always")
        ) {
            "always" -> NavigationBarView.LABEL_VISIBILITY_LABELED
            "selected" -> NavigationBarView.LABEL_VISIBILITY_SELECTED
            "never" -> NavigationBarView.LABEL_VISIBILITY_UNLABELED
            else -> NavigationBarView.LABEL_VISIBILITY_AUTO
        }
        this.labelVisibilityMode = labelVisibilityMode
    }

    fun NavigationBarView.applyNavBarStyle(): Int {
        setLabelVisibility()

        val navBarItems = getNavBarItems(this.context)
        this.menu.clear()

        val maxItems = if (this is com.google.android.material.bottomnavigation.BottomNavigationView) 5 else 7
        var count = 0
        navBarItems.forEach { navBarItem ->
            if (navBarItem.isVisible && count < maxItems) {
                this.menu.add(
                    navBarItem.groupId,
                    navBarItem.itemId,
                    Menu.NONE,
                    navBarItem.title
                ).apply {
                    icon = navBarItem.icon
                    isEnabled = navBarItem.isEnabled
                }
                count++
            }
        }
        return getStartFragmentId(this.context)
    }
}