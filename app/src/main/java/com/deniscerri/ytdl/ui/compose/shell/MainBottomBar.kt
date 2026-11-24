package com.deniscerri.ytdl.ui.compose.shell

import androidx.annotation.DrawableRes
import androidx.annotation.IdRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deniscerri.ytdl.R

/**
 * Model representing a primary navigation tab in the Single-Activity shell.
 */
data class ShellNavTab(
    @param:IdRes val id: Int,
    @param:StringRes val labelRes: Int,
    @param:DrawableRes val iconRes: Int,
    val badgeCount: Int = 0,
)

/**
 * Phase 10: Material 3 NavigationBar for phones and compact portrait layouts.
 */
@Composable
fun MainBottomBar(
    tabs: List<ShellNavTab>,
    selectedId: Int,
    onSelect: (Int) -> Unit,
    onReselect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    visible: Boolean = true,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = androidx.compose.runtime.remember { androidx.preference.PreferenceManager.getDefaultSharedPreferences(context) }
    val isGlassDock = prefs.getBoolean("glass_dock", false)

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = modifier
    ) {
        NavigationBar(
            modifier = Modifier.fillMaxWidth(),
            containerColor = if (isGlassDock) {
                MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.72f)
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            },
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            tonalElevation = if (isGlassDock) 0.dp else 3.dp
        ) {
            tabs.forEach { tab ->
                val isSelected = selectedId == tab.id
                NavigationBarItem(
                    selected = isSelected,
                    onClick = {
                        if (isSelected) onReselect(tab.id) else onSelect(tab.id)
                    },
                    icon = {
                        if (tab.badgeCount > 0) {
                            BadgedBox(
                                badge = {
                                    Badge(
                                        containerColor = MaterialTheme.colorScheme.error,
                                        contentColor = MaterialTheme.colorScheme.onError
                                    ) {
                                        Text(
                                            text = if (tab.badgeCount > 99) "99+" else "${tab.badgeCount}",
                                            fontSize = 10.sp
                                        )
                                    }
                                }
                            ) {
                                Icon(
                                    painter = painterResource(tab.iconRes),
                                    contentDescription = stringResource(tab.labelRes),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        } else {
                            Icon(
                                painter = painterResource(tab.iconRes),
                                contentDescription = stringResource(tab.labelRes),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    },
                    label = {
                        Text(
                            text = stringResource(tab.labelRes),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    alwaysShowLabel = true,
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        selectedTextColor = MaterialTheme.colorScheme.onSurface,
                        indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
        }
    }
}

/**
 * Phase 10: Material 3 NavigationRail for large screens (sw600dp) and landscape layouts.
 */
@Composable
fun MainNavigationRail(
    tabs: List<ShellNavTab>,
    selectedId: Int,
    onSelect: (Int) -> Unit,
    onReselect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    header: (@Composable () -> Unit)? = null,
) {
    NavigationRail(
        modifier = modifier.fillMaxHeight(),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        header = header?.let {
            {
                it()
            }
        }
    ) {
        tabs.forEach { tab ->
            val isSelected = selectedId == tab.id
            NavigationRailItem(
                selected = isSelected,
                onClick = {
                    if (isSelected) onReselect(tab.id) else onSelect(tab.id)
                },
                icon = {
                    if (tab.badgeCount > 0) {
                        BadgedBox(
                            badge = {
                                Badge(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = MaterialTheme.colorScheme.onError
                                ) {
                                    Text(
                                        text = if (tab.badgeCount > 99) "99+" else "${tab.badgeCount}",
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        ) {
                            Icon(
                                painter = painterResource(tab.iconRes),
                                contentDescription = stringResource(tab.labelRes),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    } else {
                        Icon(
                            painter = painterResource(tab.iconRes),
                            contentDescription = stringResource(tab.labelRes),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                },
                label = {
                    Text(
                        text = stringResource(tab.labelRes),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelSmall
                    )
                },
                alwaysShowLabel = false,
                colors = NavigationRailItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                    indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}
