package com.deniscerri.ytdl.ui.compose.dialogs

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.preference.PreferenceManager
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.models.DownloadItem
import com.deniscerri.ytdl.database.viewmodel.YTDLPViewModel
import com.deniscerri.ytdl.ui.compose.components.ConfirmDialog
import com.deniscerri.ytdl.ui.compose.host.ComposeDialogFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Filename template editor dialog (Phase 3).
 *
 * Supports live preview via [YTDLPViewModel], user saved templates, and suggested token chips.
 * Replaces `filename_template_dialog.xml`.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FilenameTemplateDialog(
    initialFilename: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    dialogTitle: String = stringResource(R.string.file_name_template),
    itemContext: DownloadItem? = null,
    ytdlpViewModel: YTDLPViewModel? = null,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val preferences = remember { PreferenceManager.getDefaultSharedPreferences(context) }

    val filenameTemplatePresets = remember {
        setOf("%(uploader).30B - %(title).170B")
    }

    var myTemplates by remember {
        val saved = preferences.getStringSet("filename_templates", filenameTemplatePresets)
            ?: filenameTemplatePresets
        mutableStateOf(saved.toSet() + filenameTemplatePresets)
    }

    var textFieldValue by remember(initialFilename) {
        mutableStateOf(TextFieldValue(initialFilename, TextRange(initialFilename.length)))
    }

    var previewText by remember { mutableStateOf<String?>(null) }
    var isPreviewLoading by remember { mutableStateOf(false) }
    var templateToDelete by remember { mutableStateOf<String?>(null) }

    fun fetchPreview(template: String) {
        if (itemContext != null && ytdlpViewModel != null) {
            isPreviewLoading = true
            coroutineScope.launch {
                val preview = withContext(Dispatchers.IO) {
                    ytdlpViewModel.getFilenameTemplatePreview(itemContext, template)
                }
                previewText = preview
                isPreviewLoading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        if (initialFilename.isNotBlank() && itemContext != null && ytdlpViewModel != null) {
            fetchPreview(initialFilename)
        }
    }

    val suggestedTemplates = remember {
        context.resources.getStringArray(R.array.filename_templates).map { raw ->
            val parts = raw.split("___")
            val token = parts.getOrNull(0) ?: raw
            val description = parts.getOrNull(1) ?: ""
            token to description
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = {
            Text(
                text = dialogTitle,
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 450.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                OutlinedTextField(
                    value = textFieldValue,
                    onValueChange = {
                        textFieldValue = it
                        previewText = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.file_name_template)) },
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                val currentText = textFieldValue.text.trim()
                                if (currentText.isNotEmpty() && !myTemplates.contains(currentText)) {
                                    val updated = myTemplates + currentText
                                    myTemplates = updated
                                    preferences.edit().putStringSet("filename_templates", updated).apply()
                                }
                            },
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_plus),
                                contentDescription = stringResource(R.string.save),
                            )
                        }
                    },
                )

                if (itemContext != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            when {
                                isPreviewLoading -> {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                }
                                previewText != null -> {
                                    Text(
                                        text = previewText.orEmpty(),
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 13.sp,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                                else -> {
                                    TextButton(onClick = { fetchPreview(textFieldValue.text) }) {
                                        Text(stringResource(R.string.preview_filename))
                                    }
                                }
                            }
                        }
                    }
                }

                if (myTemplates.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.my_filename_templates),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        myTemplates.forEach { tpl ->
                            val isSelected = textFieldValue.text.contains(tpl)
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    textFieldValue = if (textFieldValue.text == tpl) {
                                        TextFieldValue("", TextRange.Zero)
                                    } else {
                                        TextFieldValue(tpl, TextRange(tpl.length))
                                    }
                                    previewText = null
                                },
                                label = {
                                    Text(
                                        text = tpl,
                                        maxLines = 1,
                                    )
                                },
                                trailingIcon = if (!filenameTemplatePresets.contains(tpl)) {
                                    {
                                        IconButton(
                                            onClick = { templateToDelete = tpl },
                                            modifier = Modifier.size(16.dp),
                                        ) {
                                            Icon(
                                                painter = painterResource(R.drawable.baseline_close_24),
                                                contentDescription = null,
                                                modifier = Modifier.size(12.dp),
                                            )
                                        }
                                    }
                                } else null,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.suggested),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(4.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    suggestedTemplates.forEach { (token, desc) ->
                        val isSelected = textFieldValue.text.contains(token)
                        SuggestionChip(
                            onClick = {
                                val currentText = textFieldValue.text
                                val sel = textFieldValue.selection
                                val newText = currentText.substring(0, sel.min) + token + currentText.substring(sel.max)
                                val newPos = sel.min + token.length
                                textFieldValue = TextFieldValue(newText, TextRange(newPos))
                                previewText = null
                            },
                            label = { Text(token) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(textFieldValue.text) }) {
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = {
                        val browserIntent = Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://github.com/yt-dlp/yt-dlp#user-content-outtmpl-postprocess-note")
                        )
                        context.startActivity(browserIntent)
                    },
                ) {
                    Text("?")
                }
                Spacer(modifier = Modifier.width(4.dp))
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel))
                }
            }
        },
    )

    templateToDelete?.let { tpl ->
        ConfirmDialog(
            title = stringResource(R.string.confirm_delete_history),
            message = tpl,
            onConfirm = {
                val updated = myTemplates - tpl
                myTemplates = updated
                preferences.edit().putStringSet("filename_templates", updated).apply()
                templateToDelete = null
            },
            onDismiss = { templateToDelete = null },
        )
    }
}

/**
 * DialogFragment hosting [FilenameTemplateDialog].
 */
class FilenameTemplateDialogFragment : ComposeDialogFragment() {

    var onConfirmCallback: ((String) -> Unit)? = null
    var onDismissCallback: (() -> Unit)? = null
    var itemContext: DownloadItem? = null
    var ytdlpViewModel: YTDLPViewModel? = null

    @Composable
    override fun Content() {
        val initial = arguments?.getString(ARG_INITIAL) ?: ""
        val title = arguments?.getString(ARG_TITLE) ?: stringResource(R.string.file_name_template)

        FilenameTemplateDialog(
            initialFilename = initial,
            dialogTitle = title,
            itemContext = itemContext,
            ytdlpViewModel = ytdlpViewModel,
            onConfirm = { result ->
                dismiss()
                onConfirmCallback?.invoke(result)
            },
            onDismiss = {
                dismiss()
                onDismissCallback?.invoke()
            },
        )
    }

    companion object {
        const val TAG = "FilenameTemplateDialogFragment"
        private const val ARG_INITIAL = "arg_initial"
        private const val ARG_TITLE = "arg_title"

        fun newInstance(
            initialFilename: String,
            dialogTitle: String,
            itemContext: DownloadItem? = null,
            ytdlpViewModel: YTDLPViewModel? = null,
            onConfirm: (String) -> Unit,
            onDismiss: () -> Unit = {},
        ): FilenameTemplateDialogFragment {
            return FilenameTemplateDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_INITIAL, initialFilename)
                    putString(ARG_TITLE, dialogTitle)
                }
                this.itemContext = itemContext
                this.ytdlpViewModel = ytdlpViewModel
                this.onConfirmCallback = onConfirm
                this.onDismissCallback = onDismiss
            }
        }
    }
}
