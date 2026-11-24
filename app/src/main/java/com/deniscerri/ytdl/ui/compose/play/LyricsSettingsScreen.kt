package com.deniscerri.ytdl.ui.compose.play

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import com.deniscerri.ytdl.ui.compose.components.YtdlSwitch
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.lyrics.LyricsProvider
import com.deniscerri.ytdl.ui.compose.components.PreferenceCard
import com.deniscerri.ytdl.ui.compose.components.SectionHeader

/**
 * Lyrics Settings Screen in Jetpack Compose (Phase 6c).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsSettingsScreen(
    anchorLabel: String,
    fontSizeLabel: String,
    providers: List<LyricsProvider>,
    providerEnabledMap: Map<String, Boolean>,
    onAnchorClick: () -> Unit,
    onFontSizeClick: () -> Unit,
    onMoveProviderUp: (Int) -> Unit,
    onMoveProviderDown: (Int) -> Unit,
    onToggleProvider: (String, Boolean) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.lyrics_settings),
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            painter = painterResource(R.drawable.ic_back),
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                ),
                windowInsets = WindowInsets(0),
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp),
        ) {
            // Category: Lyrics Appearance & Behavior
            SectionHeader(text = stringResource(R.string.lyrics))

            // Scroll Anchor Card
            PreferenceCard(
                title = stringResource(R.string.lyrics_scroll_anchor),
                summary = anchorLabel,
                iconRes = R.drawable.ic_speed,
                showChevron = true,
                onClick = onAnchorClick,
            )

            // Font Size Card
            PreferenceCard(
                title = stringResource(R.string.lyrics_text_size),
                summary = fontSizeLabel,
                iconRes = R.drawable.ic_textformat,
                showChevron = true,
                onClick = onFontSizeClick,
            )

            Spacer(Modifier.height(8.dp))

            // Category: Lyrics Providers
            SectionHeader(text = stringResource(R.string.lyrics_providers))

            providers.forEachIndexed { index, provider ->
                val isEnabled = providerEnabledMap[provider.id] ?: true
                val iconRes = when (provider.id) {
                    "lyricsplus" -> R.drawable.ic_subtitles
                    "youtube_subs" -> R.drawable.ic_subtitles_alternative
                    else -> R.drawable.ic_lyrics
                }

                val summaryText = when (provider.id) {
                    "betterlyrics" -> stringResource(R.string.lyrics_provider_betterlyrics_summary)
                    "lyricsplus" -> stringResource(R.string.lyrics_provider_lyricsplus_summary)
                    "lrclib" -> stringResource(R.string.lyrics_provider_lrclib_summary)
                    "youtube_subs" -> stringResource(R.string.lyrics_provider_youtube_subs_summary)
                    else -> ""
                }

                PreferenceCard(
                    title = provider.displayName,
                    summary = summaryText.ifEmpty { null },
                    iconRes = iconRes,
                    onClick = { onToggleProvider(provider.id, !isEnabled) },
                    trailing = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            // Up Button
                            IconButton(
                                onClick = { onMoveProviderUp(index) },
                                enabled = index > 0,
                                modifier = Modifier
                                    .size(34.dp)
                                    .alpha(if (index > 0) 1f else 0.3f),
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_up),
                                    contentDescription = stringResource(R.string.lyrics_move_up),
                                    modifier = Modifier.size(18.dp),
                                )
                            }

                            // Down Button
                            IconButton(
                                onClick = { onMoveProviderDown(index) },
                                enabled = index < providers.size - 1,
                                modifier = Modifier
                                    .size(34.dp)
                                    .alpha(if (index < providers.size - 1) 1f else 0.3f),
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_down),
                                    contentDescription = stringResource(R.string.lyrics_move_down),
                                    modifier = Modifier.size(18.dp),
                                )
                            }

                            // Switch
                            YtdlSwitch(
                                checked = isEnabled,
                                onCheckedChange = { onToggleProvider(provider.id, it) },
                            )
                        }
                    },
                )
            }
        }
    }
}
