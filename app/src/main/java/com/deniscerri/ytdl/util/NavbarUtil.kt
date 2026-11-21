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

    private val tokenToEntryName = mapOf(
        "Home" to "homeFragment",
        "History" to "historyFragment",
        "Queue" to "downloadQueueMainFragment",
        "Play" to "playFragment",
        "More" to "moreFragment",
        "Terminal" to "terminalActivity",
        "homeFragment" to "homeFragment",
        "historyFragment" to "historyFragment",
        "downloadQueueMainFragment" to "downloadQueueMainFragment",
        "playFragment" to "playFragment",
        "moreFragment" to "moreFragment",
        "terminalActivity" to "terminalActivity"
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
        if (pref.isBlank()) return R.id.homeFragment
        val targetId = navItems[pref]
            ?: navItems[tokenToEntryName[pref]]
            ?: when (pref) {
                "homeFragment" -> R.id.homeFragment
                "historyFragment" -> R.id.historyFragment
                "downloadQueueMainFragment" -> R.id.downloadQueueMainFragment
                "playFragment" -> R.id.playFragment
                "moreFragment" -> R.id.moreFragment
                "terminalActivity" -> R.id.terminalActivity
                else -> R.id.homeFragment
            }
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
        val name = when (itemId) {
            R.id.homeFragment -> "homeFragment"
            R.id.historyFragment -> "historyFragment"
            R.id.downloadQueueMainFragment -> "downloadQueueMainFragment"
            R.id.playFragment -> "playFragment"
            R.id.moreFragment -> "moreFragment"
            R.id.terminalActivity -> "terminalActivity"
            else -> "homeFragment"
        }
        settings.edit().putString("start_destination", name).apply()
    }

    fun getNavBarItems(context: Context): List<MenuItem> {
        migrateNavBarPrefsIfNeeded(context)
        val p = buildDefaultPopupMenu(context)
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
            val clean = token.removePrefix("-")
            val name = tokenToEntryName[clean] ?: clean
            val hidden = if (name == "playFragment") false else token.startsWith("-")
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

        // Ensure play tab is placed before more tab
        val playItemIndex = result.indexOfFirst { it.itemId == R.id.playFragment }
        val moreItemIndex = result.indexOfFirst { it.itemId == R.id.moreFragment }
        if (playItemIndex >= 0 && moreItemIndex >= 0 && playItemIndex > moreItemIndex) {
            val playItem = result.removeAt(playItemIndex)
            val newMoreIndex = result.indexOfFirst { it.itemId == R.id.moreFragment }
            if (newMoreIndex >= 0) {
                result.add(newMoreIndex, playItem)
            } else {
                result.add(playItem)
            }
        }

        // Fallback: If all items ended up hidden, restore the default 5 tabs
        if (result.none { it.isVisible }) {
            val defaultVisible = setOf("homeFragment", "historyFragment", "downloadQueueMainFragment", "playFragment", "moreFragment")
            result.forEach { item ->
                val name = context.resources.getResourceEntryName(item.itemId)
                if (defaultVisible.contains(name)) {
                    item.isVisible = true
                }
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
     * Converts the old index-based value ("0,1,2,3,-4") or alias strings ("Home,History,Queue,More")
     * into stable resource entry names.
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
            tokens.forEach { token ->
                val clean = token.removePrefix("-")
                val entryName = tokenToEntryName[clean] ?: clean
                // Ensure playFragment is visible by default as requested
                val hidden = if (entryName == "playFragment") false else token.startsWith("-")
                migrated.add(if (hidden) "-$entryName" else entryName)
            }
        }

        val playIdx = migrated.indexOfFirst { it.removePrefix("-") == "playFragment" }
        val moreIdx = migrated.indexOfFirst { it.removePrefix("-") == "moreFragment" }
        if (playIdx >= 0 && moreIdx >= 0 && playIdx > moreIdx) {
            val playItem = migrated.removeAt(playIdx)
            val newMoreIdx = migrated.indexOfFirst { it.removePrefix("-") == "moreFragment" }
            if (newMoreIdx >= 0) {
                migrated.add(newMoreIdx, playItem)
            } else {
                migrated.add(playItem)
            }
        } else if (playIdx == -1) {
            val insertIdx = migrated.indexOfFirst { it.removePrefix("-") == "moreFragment" }
            if (insertIdx >= 0) {
                migrated.add(insertIdx, "playFragment")
            } else {
                migrated.add("playFragment")
            }
        }
        val newValue = migrated.joinToString(",")
        if (newValue != raw) {
            settings.edit().putString("navigation_bar", newValue).apply()
        }
    }

    private fun buildDefaultPopupMenu(context: Context): PopupMenu {
        val p = PopupMenu(context, null)
        p.menu.add(Menu.NONE, R.id.homeFragment, 0, R.string.home).setIcon(R.drawable.ic_home)
        p.menu.add(Menu.NONE, R.id.historyFragment, 1, R.string.downloads).setIcon(R.drawable.ic_downloads)
        p.menu.add(Menu.NONE, R.id.downloadQueueMainFragment, 2, R.string.queue).setIcon(R.drawable.baseline_downloading_24)
        p.menu.add(Menu.NONE, R.id.playFragment, 3, R.string.play).setIcon(R.drawable.ic_lyrics_play)
        p.menu.add(Menu.NONE, R.id.moreFragment, 4, R.string.more).setIcon(R.drawable.ic_more)
        p.menu.add(Menu.NONE, R.id.terminalActivity, 5, R.string.terminal).apply {
            setIcon(R.drawable.ic_terminal)
            isVisible = false
        }
        return p
    }

    private fun getDefaultNavBarItems(context: Context): List<MenuItem> {
        val p = buildDefaultPopupMenu(context)
        val navBarItems = mutableListOf<MenuItem>()
        p.menu.forEach {
            navBarItems.add(it)
        }
        return navBarItems
    }

    fun getShellNavTabs(context: Context): List<com.deniscerri.ytdl.ui.compose.shell.ShellNavTab> {
        val navBarItems = getNavBarItems(context).filter { it.isVisible }.toMutableList()
        val playIdx = navBarItems.indexOfFirst { it.itemId == R.id.playFragment }
        val moreIdx = navBarItems.indexOfFirst { it.itemId == R.id.moreFragment }
        if (playIdx >= 0 && moreIdx >= 0 && playIdx > moreIdx) {
            val playItem = navBarItems.removeAt(playIdx)
            val newMoreIdx = navBarItems.indexOfFirst { it.itemId == R.id.moreFragment }
            if (newMoreIdx >= 0) {
                navBarItems.add(newMoreIdx, playItem)
            } else {
                navBarItems.add(playItem)
            }
        }
        if (navBarItems.isEmpty()) {
            return listOf(
                com.deniscerri.ytdl.ui.compose.shell.ShellNavTab(R.id.homeFragment, R.string.home, R.drawable.ic_home),
                com.deniscerri.ytdl.ui.compose.shell.ShellNavTab(R.id.historyFragment, R.string.downloads, R.drawable.ic_downloads),
                com.deniscerri.ytdl.ui.compose.shell.ShellNavTab(R.id.downloadQueueMainFragment, R.string.queue, R.drawable.baseline_downloading_24),
                com.deniscerri.ytdl.ui.compose.shell.ShellNavTab(R.id.playFragment, R.string.play, R.drawable.ic_lyrics_play),
                com.deniscerri.ytdl.ui.compose.shell.ShellNavTab(R.id.moreFragment, R.string.more, R.drawable.ic_more),
            )
        }
        return navBarItems.take(5).map { item ->
            val iconRes = when (item.itemId) {
                R.id.homeFragment -> R.drawable.ic_home
                R.id.historyFragment -> R.drawable.ic_downloads
                R.id.downloadQueueMainFragment -> R.drawable.baseline_downloading_24
                R.id.moreFragment -> R.drawable.ic_more
                R.id.terminalActivity -> R.drawable.ic_terminal
                R.id.playFragment -> R.drawable.ic_lyrics_play
                else -> R.drawable.ic_home
            }
            val labelRes = when (item.itemId) {
                R.id.homeFragment -> R.string.home
                R.id.historyFragment -> R.string.downloads
                R.id.downloadQueueMainFragment -> R.string.queue
                R.id.moreFragment -> R.string.more
                R.id.terminalActivity -> R.string.terminal
                R.id.playFragment -> R.string.play
                else -> R.string.app_name
            }
            com.deniscerri.ytdl.ui.compose.shell.ShellNavTab(
                id = item.itemId,
                labelRes = labelRes,
                iconRes = iconRes
            )
        }
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