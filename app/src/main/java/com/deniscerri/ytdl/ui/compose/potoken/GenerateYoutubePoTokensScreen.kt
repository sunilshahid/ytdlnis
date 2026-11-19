package com.deniscerri.ytdl.ui.compose.potoken

import android.content.Context
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.ui.compose.components.YtdlBottomSheet
import com.deniscerri.ytdl.util.extractors.newpipe.potoken.PoTokenPolicy
import com.deniscerri.ytdl.util.Extensions.isYoutubeURL
import kotlinx.coroutines.delay

/** Values rendered by the "Automatic PO Token Coordination" card. */
data class PolicySectionState(
    val statusText: String,
    val warningText: String?,
    val policy: PoTokenPolicy,
    val regenerating: Boolean,
)

/** Values rendered by the "BgUtils POT Provider" card. */
data class BgUtilsSectionState(
    val enabled: Boolean,
    val method: String,
    val scriptAvailable: Boolean,
)

/** Values rendered by the "Manual Web Po Token Generator" card. */
data class ManualSectionState(
    val enabled: Boolean,
    val gvs: String,
    val player: String,
    val subs: String,
    val visitorData: String,
    val playerClients: String,
    val useVisitorData: Boolean,
)

/**
 * Port of `fragment_generate_youtube_po_token.xml` (Phase 3).
 *
 * All side effects (policy persistence, BgUtils server lifecycle, WebView launches) stay in
 * [com.deniscerri.ytdl.ui.more.settings.advanced.generateyoutubepotokens.GenerateYoutubePoTokensFragment];
 * this composable only renders state and forwards events.
 */
@Composable
fun GenerateYoutubePoTokensScreen(
    policy: PolicySectionState,
    bgUtils: BgUtilsSectionState,
    manual: ManualSectionState,
    onPolicyChange: (PoTokenPolicy) -> Unit,
    onRegenerate: () -> Unit,
    onBgUtilsToggle: (Boolean) -> Unit,
    onBgUtilsMethodChange: (String) -> Unit,
    onRedownloadBgUtils: () -> Unit,
    onManualToggle: (Boolean) -> Unit,
    onCopy: (String) -> Unit,
    onPlayerClientsClick: () -> Unit,
    onRegenerateWeb: () -> Unit,
    onUseVisitorDataChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 120.dp),
    ) {
        PolicyCard(
            state = policy,
            onPolicyChange = onPolicyChange,
            onRegenerate = onRegenerate,
        )

        BgUtilsCard(
            state = bgUtils,
            onToggle = onBgUtilsToggle,
            onMethodChange = onBgUtilsMethodChange,
            onRedownload = onRedownloadBgUtils,
        )

        ManualCard(
            state = manual,
            onToggle = onManualToggle,
            onCopy = onCopy,
            onPlayerClientsClick = onPlayerClientsClick,
            onRegenerate = onRegenerateWeb,
            onUseVisitorDataChange = onUseVisitorDataChange,
        )
    }
}

@Composable
private fun PolicyCard(
    state: PolicySectionState,
    onPolicyChange: (PoTokenPolicy) -> Unit,
    onRegenerate: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.po_token_coordination_title),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.po_token_coordination_summary),
                modifier = Modifier
                    .padding(top = 4.dp)
                    .alpha(0.8f),
                fontSize = 12.sp,
            )
            Text(
                text = state.statusText,
                modifier = Modifier.padding(top = 10.dp),
                fontSize = 13.sp,
            )
            if (!state.warningText.isNullOrBlank()) {
                Text(
                    text = state.warningText,
                    modifier = Modifier.padding(top = 4.dp),
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp,
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
                    .selectableGroup(),
            ) {
                PolicyOption(
                    label = stringResource(R.string.po_token_policy_auto),
                    selected = state.policy == PoTokenPolicy.AUTO,
                    onClick = { onPolicyChange(PoTokenPolicy.AUTO) },
                )
                PolicyOption(
                    label = stringResource(R.string.po_token_policy_session_only),
                    selected = state.policy == PoTokenPolicy.SESSION_ONLY,
                    onClick = { onPolicyChange(PoTokenPolicy.SESSION_ONLY) },
                )
                PolicyOption(
                    label = stringResource(R.string.po_token_policy_anonymous_only),
                    selected = state.policy == PoTokenPolicy.ANONYMOUS_ONLY,
                    onClick = { onPolicyChange(PoTokenPolicy.ANONYMOUS_ONLY) },
                )
                PolicyOption(
                    label = stringResource(R.string.po_token_policy_manual_only),
                    selected = state.policy == PoTokenPolicy.MANUAL_ONLY,
                    onClick = { onPolicyChange(PoTokenPolicy.MANUAL_ONLY) },
                )
            }

            FilledTonalButton(
                onClick = onRegenerate,
                enabled = !state.regenerating,
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(top = 8.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_retries),
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                )
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(stringResource(R.string.regenerate))
            }
        }
    }
}

@Composable
private fun PolicyOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.RadioButton,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(
            text = label,
            modifier = Modifier.padding(start = 8.dp),
            fontSize = 14.sp,
        )
    }
}

@Composable
private fun BgUtilsCard(
    state: BgUtilsSectionState,
    onToggle: (Boolean) -> Unit,
    onMethodChange: (String) -> Unit,
    onRedownload: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(5.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            SwitchRow(
                text = "BgUtils POT Provider",
                checked = state.enabled,
                onCheckedChange = onToggle,
            )

            if (state.enabled) {
                Column(modifier = Modifier.padding(15.dp)) {
                    Text(text = stringResource(R.string.bgutils_info))

                    Column(modifier = Modifier.selectableGroup()) {
                        RadioOption(
                            label = stringResource(R.string.bgutils_pot_generation_script),
                            selected = state.method == "generation_script" && state.scriptAvailable,
                            enabled = state.scriptAvailable,
                            onClick = { onMethodChange("generation_script") },
                        )
                        Text(
                            text = stringResource(R.string.bgutils_pot_generation_script_summary),
                            modifier = Modifier.padding(start = 35.dp),
                        )
                        RadioOption(
                            label = stringResource(R.string.bgutils_pot_generation_server),
                            selected = state.method == "server",
                            onClick = { onMethodChange("server") },
                        )
                        Text(
                            text = stringResource(R.string.bgutils_pot_server_summary),
                            modifier = Modifier.padding(start = 35.dp),
                        )
                    }

                    ElevatedButton(
                        onClick = onRedownload,
                        modifier = Modifier
                            .align(Alignment.End)
                            .padding(20.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_retries),
                            contentDescription = null,
                            modifier = Modifier.size(ButtonDefaults.IconSize),
                        )
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text(stringResource(R.string.redownload))
                    }
                }
            }
        }
    }
}

@Composable
private fun RadioOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                enabled = enabled,
                onClick = onClick,
                role = Role.RadioButton,
            )
            .padding(horizontal = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        Text(
            text = label,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@Composable
private fun ManualCard(
    state: ManualSectionState,
    onToggle: (Boolean) -> Unit,
    onCopy: (String) -> Unit,
    onPlayerClientsClick: () -> Unit,
    onRegenerate: () -> Unit,
    onUseVisitorDataChange: (Boolean) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(5.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            SwitchRow(
                text = stringResource(R.string.manual_po_token_generator),
                checked = state.enabled,
                onCheckedChange = onToggle,
            )

            if (state.enabled) {
                Column(modifier = Modifier.padding(15.dp)) {
                    CopyCard(
                        title = stringResource(R.string.potoken_gvs),
                        content = state.gvs,
                        onClick = { onCopy(state.gvs) },
                    )
                    CopyCard(
                        title = stringResource(R.string.potoken_player),
                        content = state.player,
                        onClick = { onCopy(state.player) },
                    )
                    CopyCard(
                        title = stringResource(R.string.potoken_subs),
                        content = state.subs,
                        onClick = { onCopy(state.subs) },
                    )
                    CopyCard(
                        title = stringResource(R.string.visitor_data),
                        content = state.visitorData,
                        onClick = { onCopy(state.visitorData) },
                    )
                    CopyCard(
                        title = stringResource(R.string.player_client),
                        content = state.playerClients,
                        onClick = onPlayerClientsClick,
                    )

                    ElevatedButton(
                        onClick = onRegenerate,
                        modifier = Modifier
                            .align(Alignment.End)
                            .padding(20.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.baseline_stars_24),
                            contentDescription = null,
                            modifier = Modifier.size(ButtonDefaults.IconSize),
                        )
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text(stringResource(R.string.regenerate))
                    }

                    SwitchRow(
                        text = stringResource(R.string.visitor_data),
                        checked = state.useVisitorData,
                        onCheckedChange = onUseVisitorDataChange,
                        enabled = true,
                    )
                }
            }
        }
    }
}

/**
 * 70 dp MaterialSwitch row used by both collapsible cards (`colorSurfaceContainer` background,
 * 20 dp horizontal content padding, 16 sp bold label).
 */
@Composable
private fun SwitchRow(
    text: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(70.dp)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            color = if (enabled) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            },
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
        )
        Switch(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
        )
    }
}

@Composable
private fun CopyCard(
    title: String,
    content: String,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(5.dp),
        color = Color.Transparent,
    ) {
        Column(modifier = Modifier.padding(15.dp)) {
            Text(
                text = title,
                modifier = Modifier.padding(end = 10.dp),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = content,
                modifier = Modifier
                    .padding(top = 2.dp, end = 15.dp)
                    .fillMaxWidth(),
                fontSize = 12.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Port of `generate_po_token_url_bottom_sheet.xml`. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeneratePoTokenUrlSheet(
    url: String,
    onUrlChange: (String) -> Unit,
    onRegenerate: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    YtdlBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
    ) {
        val focusRequester = remember { FocusRequester() }
        val view = LocalView.current

        // The XML sheet asked for focus 300 ms after show and opened the keyboard.
        LaunchedEffect(Unit) {
            delay(300)
            runCatching { focusRequester.requestFocus() }
            val imm = view.context
                .getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showSoftInput(view, 0)
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 20.dp, bottom = 20.dp)
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            TextField(
                value = url,
                onValueChange = onUrlChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                label = { Text(stringResource(R.string.url)) },
                textStyle = TextStyle(fontSize = 14.sp),
                singleLine = false,
                maxLines = 4,
            )

            ElevatedButton(
                onClick = onRegenerate,
                enabled = url.isYoutubeURL(),
                modifier = Modifier.align(Alignment.End),
            ) {
                Icon(
                    painter = painterResource(R.drawable.baseline_stars_24),
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                )
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(stringResource(R.string.regenerate))
            }
        }
    }
}
