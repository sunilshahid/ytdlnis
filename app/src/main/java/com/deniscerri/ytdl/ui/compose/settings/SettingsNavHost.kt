package com.deniscerri.ytdl.ui.compose.settings

import android.view.ViewGroup
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentContainerView
import androidx.navigation.fragment.NavHostFragment
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.ui.more.settings.buildSettingsNavGraph

/**
 * Compose wrapper hosting settings fragment navigation.
 * Replaces `settings_fragment_container.xml` and `settings_nav.xml`.
 */
@Composable
fun SettingsNavHost(
    activity: FragmentActivity,
    modifier: Modifier = Modifier,
    onDestinationChanged: ((Int) -> Unit)? = null,
) {
    AndroidView(
        factory = { ctx ->
            FragmentContainerView(ctx).apply {
                id = R.id.frame_layout
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                val existing = activity.supportFragmentManager.findFragmentById(R.id.frame_layout) as? NavHostFragment
                val navHost = existing ?: NavHostFragment.create(0).also { fragment ->
                    activity.supportFragmentManager.beginTransaction()
                        .replace(R.id.frame_layout, fragment)
                        .setPrimaryNavigationFragment(fragment)
                        .commitNow()
                }

                if (runCatching { navHost.navController.graph }.getOrNull() == null) {
                    navHost.navController.buildSettingsNavGraph()
                }

                if (onDestinationChanged != null) {
                    navHost.navController.addOnDestinationChangedListener { _, destination, _ ->
                        onDestinationChanged(destination.id)
                    }
                }
            }
        },
        modifier = modifier.fillMaxSize()
    )
}
