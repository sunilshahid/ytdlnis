package com.deniscerri.ytdl.ui.compose.youtubeclients

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import com.deniscerri.ytdl.ui.compose.components.YtdlSwitch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.models.YoutubePlayerClientItem
import com.deniscerri.ytdl.database.models.YoutubePoTokenItem
import com.deniscerri.ytdl.ui.compose.components.YtdlBottomSheet

/**
 * Port of `youtube_player_client_create_bottom_sheet.xml`.
 *
 * The sheet used to be a `BottomSheetDialog` inflated by hand inside
 * [com.deniscerri.ytdl.ui.more.settings.advanced.YoutubePlayerClientFragment]; it is now rendered
 * by the same Compose tree as the list. Validation, the `youtube_player_clients` preference and the
 * `YoutubePlayerClientItem` shape are unchanged.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun YoutubePlayerClientSheet(
    currentValue: YoutubePlayerClientItem?,
    existingConfigs: List<YoutubePlayerClientItem>,
    onDismiss: () -> Unit,
    onConfirm: (YoutubePlayerClientItem) -> Unit,
    onDelete: () -> Unit,
) {
    YtdlBottomSheet(onDismissRequest = onDismiss) {
        val context = LocalContext.current
        val clipboard = remember(context) {
            context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        }

        // Suggested chips exclude clients already used by any saved configuration, exactly like
        // the XML implementation.
        val suggestedClients = remember(context, existingConfigs) {
            context.resources.getStringArray(R.array.youtube_player_clients)
                .filter { it.isNotBlank() }
                .filter { candidate -> existingConfigs.none { it.playerClient == candidate } }
        }

        var title by remember(currentValue) {
            mutableStateOf(currentValue?.playerClient.orEmpty())
        }
        var gvs by remember(currentValue) {
            mutableStateOf(currentValue.poToken("gvs"))
        }
        var player by remember(currentValue) {
            mutableStateOf(currentValue.poToken("player"))
        }
        var subs by remember(currentValue) {
            mutableStateOf(currentValue.poToken("subs"))
        }
        var urlRegexInput by remember { mutableStateOf("") }
        var urlRegexes by remember(currentValue) {
            mutableStateOf(currentValue?.urlRegex?.toList().orEmpty())
        }
        var useOnlyPoToken by remember(currentValue) {
            mutableStateOf(currentValue?.useOnlyPoToken ?: false)
        }
        var titleError by remember { mutableStateOf<String?>(null) }
        var poTokenError by remember { mutableStateOf<String?>(null) }

        fun pasteFromClipboard(): String {
            val clip = clipboard.primaryClip ?: return ""
            if (clip.itemCount == 0) return ""
            return clip.getItemAt(0).text?.toString().orEmpty()
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 760.dp)
                .verticalScroll(rememberScrollState())
                .imePadding(),
        ) {
            // Header: title + subtitle on the start, delete + create/edit on the end.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.player_client),
                        fontSize = 20.sp,
                    )
                    Text(
                        text = "YouTube",
                        fontSize = 12.sp,
                    )
                }

                if (currentValue != null) {
                    FilledIconButton(onClick = onDelete) {
                        Icon(
                            painter = painterResource(R.drawable.baseline_delete_24),
                            contentDescription = stringResource(R.string.Remove),
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                }

                ElevatedButton(
                    onClick = {
                        val titleVal = title
                        if (titleVal.isBlank()) {
                            titleError = context.getString(R.string.player_client_not_empty)
                            return@ElevatedButton
                        }

                        val duplicate = existingConfigs.any {
                            it.playerClient == titleVal && it.enabled
                        } && (currentValue == null || currentValue.playerClient != titleVal)
                        if (duplicate) {
                            titleError = context.getString(R.string.player_client_already_created)
                            return@ElevatedButton
                        }

                        if (useOnlyPoToken && gvs.isBlank() && player.isBlank() && subs.isBlank()) {
                            poTokenError = context.getString(R.string.write_atleast_one_potoken)
                            return@ElevatedButton
                        }

                        val obj = YoutubePlayerClientItem(
                            titleVal,
                            mutableListOf(),
                            true,
                            useOnlyPoToken,
                        )
                        if (gvs.isNotBlank()) {
                            obj.poTokens.add(YoutubePoTokenItem("gvs", gvs))
                        }
                        if (player.isNotBlank()) {
                            obj.poTokens.add(YoutubePoTokenItem("player", player))
                        }
                        if (subs.isNotBlank()) {
                            obj.poTokens.add(YoutubePoTokenItem("subs", subs))
                        }
                        obj.urlRegex.addAll(urlRegexes)

                        onConfirm(obj)
                    },
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_check),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(
                            if (currentValue == null) R.string.create else R.string.edit
                        ),
                    )
                }
            }

            Column(modifier = Modifier.padding(15.dp)) {
                val titleErrorText = titleError
                TextField(
                    value = title,
                    onValueChange = {
                        title = it
                        titleError = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.player_client)) },
                    singleLine = true,
                    isError = titleErrorText != null,
                    supportingText = if (titleErrorText != null) {
                        { Text(titleErrorText) }
                    } else {
                        null
                    },
                )

                if (suggestedClients.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.suggested),
                        modifier = Modifier.padding(top = 10.dp),
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(bottom = 10.dp),
                    ) {
                        suggestedClients.forEach { client ->
                            FilterChip(
                                selected = title == client,
                                onClick = {
                                    title = client
                                    titleError = null
                                },
                                label = { Text(client) },
                            )
                        }
                    }
                }

                Text(
                    text = stringResource(R.string.potoken),
                    modifier = Modifier.padding(bottom = 10.dp),
                )

                PoTokenField(
                    value = gvs,
                    onValueChange = {
                        gvs = it
                        poTokenError = null
                    },
                    hint = "GVS",
                    isError = poTokenError != null,
                    supportingText = poTokenError,
                    onPaste = { pasteFromClipboard() },
                )
                PoTokenField(
                    value = player,
                    onValueChange = { player = it },
                    hint = "Player",
                    onPaste = { pasteFromClipboard() },
                )
                PoTokenField(
                    value = subs,
                    onValueChange = { subs = it },
                    hint = "Subs",
                    onPaste = { pasteFromClipboard() },
                )

                TextField(
                    value = urlRegexInput,
                    onValueChange = { urlRegexInput = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
                    label = { Text("URL Regex") },
                    singleLine = true,
                    trailingIcon = if (urlRegexInput.isNotBlank()) {
                        {
                            IconButton(
                                onClick = {
                                    urlRegexes = urlRegexes + urlRegexInput
                                    urlRegexInput = ""
                                },
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_plus),
                                    contentDescription = null,
                                )
                            }
                        }
                    } else {
                        null
                    },
                )

                if (urlRegexes.isNotEmpty()) {
                    FlowRow(
                        modifier = Modifier.padding(bottom = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        urlRegexes.forEach { regex ->
                            AssistChip(
                                onClick = {
                                    urlRegexes = urlRegexes - regex
                                },
                                label = {
                                    Text(
                                        text = regex,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.use_only_po_token),
                        modifier = Modifier.weight(1f),
                        fontSize = 16.sp,
                    )
                    YtdlSwitch(
                        checked = useOnlyPoToken,
                        onCheckedChange = { useOnlyPoToken = it },
                    )
                }
            }
        }
    }
}

private fun YoutubePlayerClientItem?.poToken(context: String): String =
    this?.poTokens?.firstOrNull { it.context == context }?.token.orEmpty()

@Composable
private fun PoTokenField(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    onPaste: () -> String,
    isError: Boolean = false,
    supportingText: String? = null,
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp),
        label = { Text(hint) },
        singleLine = true,
        isError = isError,
        supportingText = if (supportingText != null) {
            { Text(supportingText) }
        } else {
            null
        },
        trailingIcon = {
            IconButton(
                onClick = { onValueChange(onPaste()) },
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_clipboard),
                    contentDescription = null,
                )
            }
        },
    )
}
