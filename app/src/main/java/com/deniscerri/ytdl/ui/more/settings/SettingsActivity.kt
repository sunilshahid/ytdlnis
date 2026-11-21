package com.deniscerri.ytdl.ui.more.settings

import android.content.Context
import android.os.Bundle
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.models.SearchSettingsItem
import com.deniscerri.ytdl.database.viewmodel.SettingsViewModel
import com.deniscerri.ytdl.ui.BaseActivity
import com.deniscerri.ytdl.ui.compose.settings.SettingsHostScreen
import com.deniscerri.ytdl.ui.compose.settings.SettingsNavHost
import com.deniscerri.ytdl.ui.compose.theme.YtdlnisTheme
import com.deniscerri.ytdl.util.ApkInstallUtil
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class SettingsActivity : BaseActivity() {
    var context: Context? = null
    private lateinit var settingViewModel: SettingsViewModel

    private val navController: NavController?
        get() = (supportFragmentManager.findFragmentById(R.id.frame_layout) as? NavHostFragment)?.navController

    var topBarTitle by mutableStateOf("")
    var isSearchAllowed by mutableStateOf(true)
    var isSearchActive by mutableStateOf(false)
    var searchQuery by mutableStateOf("")
    var searchResults by mutableStateOf<List<SearchSettingsItem>>(emptyList())

    fun refreshUI() {
        settingViewModel.indexSearchSettings()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        context = baseContext
        settingViewModel = ViewModelProvider(this)[SettingsViewModel::class.java]

        topBarTitle = getString(R.string.settings)

        onBackPressedDispatcher.addCallback(this) {
            if (isSearchActive) {
                isSearchActive = false
            } else if (navController?.currentDestination?.id == R.id.mainSettingsFragment) {
                navController?.popBackStack()
                finishAndRemoveTask()
            } else {
                navController?.navigateUp()
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                settingViewModel.settingsFlow.collectLatest { res ->
                    val items = res.first
                    val query = res.second

                    val filtered = items.filter { item ->
                        val titleMatch = item.title.contains(query, ignoreCase = true)
                        val summaryMatch = item.summary?.contains(query, ignoreCase = true) == true
                        val groupMatch = item.groupTitle?.contains(query, ignoreCase = true) == true
                        titleMatch || summaryMatch || groupMatch
                    }

                    searchResults = filtered
                }
            }
        }

        val savedSearch = intent.getStringExtra("search_query")
        if (!savedSearch.isNullOrBlank()) {
            isSearchActive = true
            searchQuery = savedSearch
            intent.removeExtra("search_query")
            settingViewModel.setSearchQuery(savedSearch)
        }

        ApkInstallUtil.registerShizukuPermissionListener()

        setContent {
            YtdlnisTheme {
                SettingsHostScreen(
                    title = topBarTitle,
                    isSearchAllowed = isSearchAllowed,
                    isSearchActive = isSearchActive,
                    searchQuery = searchQuery,
                    searchResults = searchResults,
                    onSearchActiveChange = { active ->
                        isSearchActive = active
                        if (!active) {
                            searchQuery = ""
                            settingViewModel.setSearchQuery("")
                        }
                    },
                    onSearchQueryChange = { query ->
                        searchQuery = query
                        settingViewModel.setSearchQuery(query)
                    },
                    onSearchResultClick = { item ->
                        handleSearchResultClick(item)
                    },
                    onSearchResultLongClick = { item ->
                        handleSearchResultLongClick(item)
                    },
                    onBackClick = {
                        onBackPressedDispatcher.onBackPressed()
                    },
                    fragmentContainer = {
                        SettingsNavHost(
                            activity = this@SettingsActivity,
                            onDestinationChanged = { destId ->
                                if (destId == R.id.mainSettingsFragment) {
                                    changeTopAppbarTitle(getString(R.string.settings), false)
                                    settingViewModel.indexSearchSettings()
                                }
                            }
                        )
                    }
                )
            }
        }
    }

    private fun handleSearchResultClick(item: SearchSettingsItem) {
        handleSearchResultLongClick(item)
    }

    private fun handleSearchResultLongClick(item: SearchSettingsItem) {
        val bundle = Bundle().apply {
            putString("highlight_key", item.key)
        }
        val destinationId = if (item.destinationId != 0) item.destinationId else R.id.mainSettingsFragment
        closeSearchView()
        navController?.navigate(destinationId, bundle)
    }

    override fun onDestroy() {
        super.onDestroy()
        ApkInstallUtil.unregisterShizukuPermissionListener()
    }

    override fun onResume() {
        refreshUI()
        super.onResume()
    }

    fun closeSearchView() {
        isSearchActive = false
        searchQuery = ""
        settingViewModel.setSearchQuery("")
    }

    fun changeTopAppbarTitle(text: String, hideSearch: Boolean = true) {
        topBarTitle = text
        isSearchAllowed = !hideSearch
    }
}
