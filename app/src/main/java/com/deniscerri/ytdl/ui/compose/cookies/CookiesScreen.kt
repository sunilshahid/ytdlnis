package com.deniscerri.ytdl.ui.compose.cookies

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import com.deniscerri.ytdl.ui.compose.components.YtdlSwitch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.models.CookieItem
import com.deniscerri.ytdl.ui.compose.components.EmptyState
import com.deniscerri.ytdl.ui.compose.components.YtdlScaffold

/**
 * Cookies management screen (Phase 3).
 * Replaces `fragment_cookies.xml`, `cookie_item.xml`, `cookies_menu.xml`, and `CookieAdapter`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CookiesScreen(
    cookies: List<CookieItem>,
    useCookies: Boolean,
    onUseCookiesChanged: (Boolean) -> Unit,
    youtubeAccountCookies: Boolean,
    onYoutubeAccountCookiesChanged: (Boolean) -> Unit,
    checkYoutubeCookies: Boolean,
    onCheckYoutubeCookiesChanged: (Boolean) -> Unit,
    youtubeAccountSummary: String,
    youtubeCookieStatusText: String?,
    onCheckSessionNow: () -> Unit,
    onManageYoutubeAccount: () -> Unit,
    swipeGestures: Boolean,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onNewCookie: () -> Unit,
    onCookieClick: (CookieItem) -> Unit,
    onCookieEnabledChanged: (CookieItem, Boolean) -> Unit,
    onDeleteCookie: (CookieItem) -> Unit,
    onDeleteAllCookies: () -> Unit,
    onImportClipboard: () -> Unit,
    onExportClipboard: () -> Unit,
    onExportFile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    YtdlScaffold(
        title = stringResource(R.string.cookies),
        modifier = modifier,
        onBack = onBack,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        actions = {
            if (useCookies && youtubeAccountCookies) {
                IconButton(onClick = onManageYoutubeAccount) {
                    Icon(
                        painter = painterResource(R.drawable.baseline_person_24),
                        contentDescription = stringResource(R.string.select_youtube_account),
                    )
                }
            }

            IconButton(onClick = { menuExpanded = true }) {
                Icon(
                    painter = painterResource(R.drawable.baseline_more_vert_24),
                    contentDescription = stringResource(R.string.more),
                )
            }

            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.delete_all_cookies)) },
                    onClick = {
                        menuExpanded = false
                        onDeleteAllCookies()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.import_from_clipboard)) },
                    onClick = {
                        menuExpanded = false
                        onImportClipboard()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.export_from_clipboard)) },
                    onClick = {
                        menuExpanded = false
                        onExportClipboard()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.export_file)) },
                    onClick = {
                        menuExpanded = false
                        onExportFile()
                    },
                )
            }
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            // Header Settings Container
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Use Cookies Master Switch
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.use_cookies),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                        )
                        YtdlSwitch(
                            checked = useCookies,
                            onCheckedChange = onUseCookiesChanged,
                        )
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 20.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    )

                    // YouTube Account Cookies Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .alpha(if (useCookies) 1.0f else 0.45f)
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 16.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.youtube_account_cookies),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = youtubeAccountSummary,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        YtdlSwitch(
                            checked = youtubeAccountCookies,
                            onCheckedChange = onYoutubeAccountCookiesChanged,
                            enabled = useCookies,
                        )
                    }

                    // Check YouTube Cookies Section
                    AnimatedVisibility(
                        visible = useCookies && youtubeAccountCookies,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically(),
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 20.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(end = 16.dp),
                                ) {
                                    Text(
                                        text = stringResource(R.string.check_youtube_cookies_on_open),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = stringResource(R.string.check_youtube_cookies_on_open_summary),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )

                                    if (youtubeCookieStatusText != null) {
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = youtubeCookieStatusText,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }

                                    Spacer(Modifier.height(4.dp))
                                    TextButton(
                                        onClick = onCheckSessionNow,
                                        contentPadding = PaddingValues(0.dp),
                                    ) {
                                        Text(
                                            text = stringResource(R.string.check_now),
                                            style = MaterialTheme.typography.labelLarge,
                                        )
                                    }
                                }

                                YtdlSwitch(
                                    checked = checkYoutubeCookies,
                                    onCheckedChange = onCheckYoutubeCookiesChanged,
                                )
                            }
                        }
                    }
                }
            }

            // New Cookie AssistChip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
            ) {
                AssistChip(
                    onClick = onNewCookie,
                    enabled = useCookies,
                    label = { Text(stringResource(R.string.new_cookie)) },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(R.drawable.ic_plus),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                )
            }

            // Cookie Items List
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                if (cookies.isEmpty()) {
                    EmptyState(
                        title = stringResource(R.string.no_results),
                        iconRes = R.drawable.ic_no_results,
                        modifier = Modifier.align(Alignment.Center),
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(
                            items = cookies,
                            key = { it.id },
                        ) { item ->
                            val cardContent = @Composable {
                                CookieCard(
                                    item = item,
                                    onClick = { onCookieClick(item) },
                                    onLongClick = { onDeleteCookie(item) },
                                    onEnabledChange = { isEnabled ->
                                        onCookieEnabledChanged(item, isEnabled)
                                    },
                                )
                            }

                            if (swipeGestures) {
                                val dismissState = rememberSwipeToDismissBoxState(
                                    confirmValueChange = { value ->
                                        if (value == SwipeToDismissBoxValue.EndToStart) {
                                            onDeleteCookie(item)
                                        }
                                        false
                                    },
                                )
                                SwipeToDismissBox(
                                    state = dismissState,
                                    enableDismissFromStartToEnd = false,
                                    backgroundContent = { SwipeDeleteBackground() },
                                ) {
                                    cardContent()
                                }
                            } else {
                                cardContent()
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CookieCard(
    item: CookieItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 12.dp),
            ) {
                Text(
                    text = item.description.ifBlank { item.url },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = item.content,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            YtdlSwitch(
                checked = item.enabled,
                onCheckedChange = onEnabledChange,
            )
        }
    }
}

@Composable
private fun SwipeDeleteBackground() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Red)
            .padding(end = 24.dp),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Icon(
            painter = painterResource(R.drawable.baseline_delete_24),
            contentDescription = stringResource(R.string.delete),
            tint = Color.White,
        )
    }
}
