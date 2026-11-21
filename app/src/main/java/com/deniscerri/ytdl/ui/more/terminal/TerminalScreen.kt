package com.deniscerri.ytdl.ui.more.terminal

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.edit
import androidx.lifecycle.ViewModelProvider
import androidx.preference.PreferenceManager
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.viewmodel.CommandTemplateViewModel
import com.deniscerri.ytdl.database.viewmodel.TerminalViewModel
import com.deniscerri.ytdl.ui.more.terminal.virtualkeys.VirtualKeysConstants
import com.deniscerri.ytdl.ui.more.terminal.virtualkeys.VirtualKeysInfo
import com.deniscerri.ytdl.ui.more.terminal.virtualkeys.VirtualKeysListener
import com.deniscerri.ytdl.ui.more.terminal.virtualkeys.VirtualKeysView
import com.deniscerri.ytdl.util.FileUtil
import com.deniscerri.ytdl.util.UiUtil
import com.termux.terminal.TerminalSession
import com.termux.view.TerminalView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val VIRTUAL_KEYS_LAYOUT = "[" +
        "\n  [\"ESC\", {\"key\": \"/\", \"popup\": \"\\\\\"}, {\"key\": \"-\", \"popup\": \"|\"}, \"HOME\", \"UP\", \"END\", \"PGUP\"]," +
        "\n  [\"TAB\", \"CTRL\", \"ALT\", \"LEFT\", \"DOWN\", \"RIGHT\", \"PGDN\"]" +
        "\n]"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(
    terminalViewModel: TerminalViewModel,
    activity: TerminalActivity,
    initialSessionId: String? = null,
    isNewSession: Boolean = false,
    sessionShareURL: String? = null,
    onBack: () -> Unit,
    onNewSession: () -> Unit,
    onSessionClosed: () -> Unit = onBack,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val sharedPreferences = remember { PreferenceManager.getDefaultSharedPreferences(context) }

    var activeSessionId by remember(initialSessionId) { mutableStateOf(initialSessionId) }
    var activeSession by remember { mutableStateOf<TerminalSession?>(null) }
    var termView by remember { mutableStateOf<TerminalView?>(null) }
    var vkView by remember { mutableStateOf<VirtualKeysView?>(null) }

    var textSize by remember {
        mutableFloatStateOf(
            run {
                val z = sharedPreferences.getFloat("terminal_text_zoom", 35f)
                if (z.isFinite()) z.coerceIn(10f, 37f) else 35f
            }
        )
    }

    var showTextSizeSlider by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }

    val commandTemplateViewModel = remember {
        ViewModelProvider(activity)[CommandTemplateViewModel::class.java]
    }
    var templateCount by remember { mutableIntStateOf(0) }
    var shortcutCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        templateCount = withContext(Dispatchers.IO) { commandTemplateViewModel.getTotalNumber() }
        shortcutCount = withContext(Dispatchers.IO) { commandTemplateViewModel.getTotalShortcutNumber() }
    }

    fun writeSpaced(text: String) {
        val session = activeSession ?: return
        val needsSpace = runCatching {
            val emulator = session.emulator ?: return@runCatching true
            val col = emulator.cursorCol
            if (col <= 0) false
            else {
                val before = emulator.screen.getSelectedText(col - 1, emulator.cursorRow, col - 1, emulator.cursorRow)
                before.isNotEmpty() && !before.last().isWhitespace()
            }
        }.getOrDefault(true)
        session.write(if (needsSpace) " $text" else text)
    }

    fun clearTerminal() {
        val session = activeSession ?: return
        session.emulator?.let {
            val seq = "\u001b[3J".toByteArray()
            it.append(seq, seq.size)
            termView?.onScreenUpdated()
        }
        session.write("\u0001\u000b\u000c")
    }

    fun exitTerminal() {
        val session = activeSession ?: return
        session.emulator?.let {
            val seq = "\u001b[3J".toByteArray()
            it.append(seq, seq.size)
            termView?.onScreenUpdated()
        }
        session.write("\u0001\u000b\u000cexit\n")
    }

    fun exportToClipboard() {
        val session = activeSession ?: return
        coroutineScope.launch(Dispatchers.IO) {
            val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val transcript = session.emulator?.screen?.transcriptText ?: ""
            clipboard.setPrimaryClip(ClipData.newPlainText("Terminal", transcript))
            withContext(Dispatchers.Main) {
                Toast.makeText(activity, R.string.export_from_clipboard, Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun toggleKeyboard() {
        val view = termView ?: return
        view.requestFocus()
        val imm = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0)
    }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                activity.contentResolver?.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                writeSpaced("\"${FileUtil.formatPath(uri.toString())}\" ")
            }
        }
    }

    // Attach session when terminal view and service are bound
    LaunchedEffect(terminalViewModel.isBound, termView, vkView, initialSessionId, isNewSession) {
        val tv = termView ?: return@LaunchedEffect
        val vk = vkView ?: return@LaunchedEffect
        val sessionBinder = terminalViewModel.sessionBinder ?: return@LaunchedEffect
        val service = sessionBinder.getService()

        val client = TerminalBackEnd(tv, activity) {
            activeSessionId?.let { sessionBinder.terminateSession(it) }
            onSessionClosed()
        }
        tv.setTerminalViewClient(client)

        if (!initialSessionId.isNullOrBlank()) {
            terminalViewModel.changeSession(activity, sessionBinder, initialSessionId)
            activeSessionId = initialSessionId
        }

        val currentSession = sessionBinder.getSession(service.currentSession.value)
        val s = if (isNewSession || currentSession == null) {
            val newId = KeyShortcutHandler.generateUniqueSessionId(activity)
            activeSessionId = newId
            sessionBinder.createSession(newId, client)
        } else {
            if (activeSessionId.isNullOrBlank()) activeSessionId = service.currentSession.value
            currentSession
        }
        activeSession = s
        s.updateTerminalSessionClient(client)

        tv.setTextSize(textSize.toInt())
        tv.setTypeface(TerminalUtils.typeface)
        tv.setTerminalViewClient(client)
        tv.attachSession(s)
        tv.requestFocus()

        val color = TerminalUtils.getViewColor(activity)
        val bgColor = TerminalUtils.getBackgroundColor(activity)
        tv.mEmulator?.mColors?.mCurrentColors?.apply {
            set(256, color)
            set(257, bgColor)
            set(258, color)
        }

        vk.virtualKeysViewClient = tv.mTermSession?.let { VirtualKeysListener(it) }
        vk.buttonTextColor = color
        vk.reload(VirtualKeysInfo(VIRTUAL_KEYS_LAYOUT, "", VirtualKeysConstants.CONTROL_CHARS_ALIASES))

        terminalViewModel.setTerminalView(tv)
        terminalViewModel.setVirtualKeysView(vk)
    }

    LaunchedEffect(sessionShareURL, activeSession) {
        val url = sessionShareURL
        val s = activeSession
        if (!url.isNullOrBlank() && s != null) {
            delay(500)
            withContext(Dispatchers.Main) {
                val writeYTDLPTerminal = sharedPreferences.getBoolean("write_ytdlp_terminal", true)
                s.write("${if (writeYTDLPTerminal) "" else "yt-dlp"} \"$url\"")
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            terminalViewModel.setTerminalView(null)
            terminalViewModel.setVirtualKeysView(null)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = activeSessionId ?: stringResource(R.string.terminal),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_back),
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { clearTerminal() }) {
                        Icon(
                            painter = painterResource(R.drawable.baseline_delete_24),
                            contentDescription = stringResource(R.string.clear)
                        )
                    }
                    IconButton(onClick = { toggleKeyboard() }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_textformat),
                            contentDescription = stringResource(R.string.textformat)
                        )
                    }
                    IconButton(onClick = { exitTerminal() }) {
                        Icon(
                            painter = painterResource(R.drawable.baseline_exit_to_app_24),
                            contentDescription = stringResource(R.string.exit)
                        )
                    }
                    Box {
                        IconButton(onClick = { showMoreMenu = true }) {
                            Icon(
                                painter = painterResource(R.drawable.baseline_more_vert_24),
                                contentDescription = stringResource(R.string.more)
                            )
                        }
                        DropdownMenu(
                            expanded = showMoreMenu,
                            onDismissRequest = { showMoreMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.add)) },
                                leadingIcon = {
                                    Icon(painterResource(R.drawable.ic_plus), contentDescription = null)
                                },
                                onClick = {
                                    showMoreMenu = false
                                    onNewSession()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.export_from_clipboard)) },
                                leadingIcon = {
                                    Icon(painterResource(R.drawable.ic_copy), contentDescription = null)
                                },
                                onClick = {
                                    showMoreMenu = false
                                    exportToClipboard()
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            Column(modifier = Modifier.fillMaxWidth()) {
                AnimatedVisibility(visible = showTextSizeSlider) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.baseline_format_size_24),
                                contentDescription = stringResource(R.string.text_size),
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.width(8.dp))
                            Slider(
                                value = textSize,
                                onValueChange = { newZoom ->
                                    textSize = newZoom
                                    termView?.setTextSize(newZoom.toInt())
                                    sharedPreferences.edit { putFloat("terminal_text_zoom", newZoom) }
                                },
                                valueRange = 10f..37f,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "${textSize.toInt()}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    tonalElevation = 2.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                if (templateCount == 0) {
                                    Toast.makeText(
                                        activity,
                                        activity.getString(R.string.add_template_first),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } else {
                                    coroutineScope.launch {
                                        UiUtil.showCommandTemplates(activity, commandTemplateViewModel) { templates ->
                                            writeSpaced(templates.joinToString("") { c -> "${c.content} " })
                                            termView?.requestFocus()
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.alpha(if (templateCount > 0) 1f else 0.38f)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_terminal),
                                contentDescription = stringResource(R.string.command_templates)
                            )
                        }

                        IconButton(
                            onClick = {
                                if (shortcutCount > 0) {
                                    coroutineScope.launch {
                                        UiUtil.showShortcuts(
                                            activity,
                                            commandTemplateViewModel,
                                            itemSelected = { sh -> writeSpaced(sh) },
                                            itemRemoved = {}
                                        )
                                    }
                                }
                            },
                            modifier = Modifier.alpha(if (shortcutCount > 0) 1f else 0.38f)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_shortcut),
                                contentDescription = stringResource(R.string.shortcuts)
                            )
                        }

                        IconButton(onClick = {
                            UiUtil.showFilenameTemplateDialog(activity, "") { filenameSelected ->
                                writeSpaced("\"$filenameSelected\" ")
                            }
                        }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_edit),
                                contentDescription = stringResource(R.string.file_name_template)
                            )
                        }

                        IconButton(onClick = {
                            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                                addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                            }
                            folderPickerLauncher.launch(intent)
                        }) {
                            Icon(
                                painter = painterResource(R.drawable.baseline_folder_24),
                                contentDescription = stringResource(R.string.command_directory)
                            )
                        }

                        IconButton(onClick = { showTextSizeSlider = !showTextSizeSlider }) {
                            Icon(
                                painter = painterResource(R.drawable.baseline_format_size_24),
                                contentDescription = stringResource(R.string.text_size),
                                tint = if (showTextSizeSlider) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AndroidView(
                factory = { ctx ->
                    TerminalView(ctx, null).apply {
                        isFocusable = true
                        isFocusableInTouchMode = true
                        keepScreenOn = true
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        termView = this
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )

            AndroidView(
                factory = { ctx ->
                    VirtualKeysView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            (70 * ctx.resources.displayMetrics.density).toInt()
                        )
                        buttonTextColor = TerminalUtils.getViewColor(ctx)
                        reload(VirtualKeysInfo(VIRTUAL_KEYS_LAYOUT, "", VirtualKeysConstants.CONTROL_CHARS_ALIASES))
                        vkView = this
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(70.dp)
            )
        }
    }
}
