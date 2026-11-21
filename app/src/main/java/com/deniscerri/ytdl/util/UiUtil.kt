package com.deniscerri.ytdl.util

import android.animation.Animator
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.text.Editable
import android.text.TextWatcher
import android.text.format.DateFormat
import android.text.method.DigitsKeyListener
import android.text.method.LinkMovementMethod
import android.util.DisplayMetrics
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.Window
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.TextView
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.annotation.DimenRes
import androidx.annotation.OptIn
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import com.deniscerri.ytdl.ui.compose.dialogs.FilenameTemplateDialogFragment
import com.deniscerri.ytdl.ui.compose.downloadcard.AddEditYtdlpSourceSheet
import com.deniscerri.ytdl.ui.compose.downloadcard.FormatDetailsSheet
import com.deniscerri.ytdl.ui.compose.downloadcard.ShortcutsListSheet
import com.deniscerri.ytdl.ui.compose.downloadcard.ShortcutsSheet
import com.deniscerri.ytdl.ui.compose.downloadcard.SubtitleLanguageDialog
import com.deniscerri.ytdl.ui.compose.downloadcard.TemplateEditorSheet
import com.deniscerri.ytdl.ui.compose.downloadcard.TemplatePickerSheet
import com.deniscerri.ytdl.ui.compose.downloadcard.YtdlpSourceItem
import com.deniscerri.ytdl.ui.compose.downloadcard.YtdlpSourcesSheet
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.asFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.deniscerri.ytdl.ui.compose.downloads.DownloadDetailSheetContent
import com.deniscerri.ytdl.ui.compose.downloads.HistoryDetailSheetContent
import com.deniscerri.ytdl.ui.compose.observesources.ObserveSourceDetailsSheet
import com.deniscerri.ytdl.ui.compose.observesources.ObserveSourceSettingsSheet
import com.deniscerri.ytdl.ui.compose.theme.YtdlnisTheme
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.FileProvider
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.afollestad.materialdialogs.utils.MDUtil.getStringArray
import com.afollestad.materialdialogs.utils.MDUtil.textChanged
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.core.packages.PackageBase
import com.deniscerri.ytdl.database.enums.DownloadType
import com.deniscerri.ytdl.database.models.CommandTemplate
import com.deniscerri.ytdl.database.models.DownloadItem
import com.deniscerri.ytdl.database.models.Format
import com.deniscerri.ytdl.database.models.GithubRelease
import com.deniscerri.ytdl.database.models.HistoryItem
import com.deniscerri.ytdl.database.models.PackageItem
import com.deniscerri.ytdl.database.models.TemplateShortcut
import com.deniscerri.ytdl.database.models.observeSources.ObserveSourcesItem
import com.deniscerri.ytdl.database.repository.DownloadRepository
import com.deniscerri.ytdl.database.viewmodel.CommandTemplateViewModel
import com.deniscerri.ytdl.database.viewmodel.HistoryViewModel
import com.deniscerri.ytdl.database.viewmodel.YTDLPViewModel
import com.deniscerri.ytdl.playback.PlaybackLauncher
import com.deniscerri.ytdl.ui.downloadcard.VideoCutListener
import com.deniscerri.ytdl.ui.downloadcard.crop.VideoCropListener
import com.deniscerri.ytdl.util.Extensions.calculateNextTimeForObserving
import com.deniscerri.ytdl.util.Extensions.createBadge
import com.deniscerri.ytdl.util.Extensions.displayStatus
import com.deniscerri.ytdl.util.Extensions.enableTextHighlight
import com.deniscerri.ytdl.util.Extensions.getMediaDuration
import com.deniscerri.ytdl.util.Extensions.hasPermission
import com.deniscerri.ytdl.util.Extensions.scheduleSummary
import com.deniscerri.ytdl.util.Extensions.toStringDuration
import com.google.android.material.badge.BadgeDrawable
import com.google.android.material.badge.BadgeUtils
import com.google.android.material.badge.ExperimentalBadgeUtils
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.DateValidatorPointForward
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.MarkwonConfiguration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale


object UiUtil {
    fun populateCommandCard(card: MaterialCardView, item: CommandTemplate){
        card.findViewById<TextView>(R.id.title).text = item.title
        card.findViewById<TextView>(R.id.content).text = item.content
        card.alpha = 1f
        card.tag = item.id
    }

     fun showCommandTemplateCreationOrUpdatingSheet(
         item: CommandTemplate?,
         context: Activity,
         lifeCycle: LifecycleOwner,
         commandTemplateViewModel: CommandTemplateViewModel,
         newTemplate: (newTemplate: CommandTemplate) -> Unit,
         dismissed: () -> Unit
     ){
        val bottomSheet = BottomSheetDialog(context)
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        val enableDataFetching = sharedPreferences.getBoolean("enable_data_fetching_extra_commands", false)

        val composeView = ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val shortcuts by commandTemplateViewModel.shortcuts.asFlow().collectAsState(initial = emptyList())
                YtdlnisTheme {
                    TemplateEditorSheet(
                        template = item,
                        availableShortcuts = shortcuts.map { it.content },
                        enableDataFetchingOption = enableDataFetching,
                        onSave = { saved ->
                            if (item == null) {
                                commandTemplateViewModel.insert(saved)
                            } else {
                                commandTemplateViewModel.update(saved)
                            }
                            newTemplate(saved)
                            bottomSheet.dismiss()
                        },
                        onDelete = { toDelete ->
                            commandTemplateViewModel.delete(toDelete)
                            bottomSheet.dismiss()
                        },
                        onEditShortcuts = {
                            showShortcutsSheet(context, lifeCycle, commandTemplateViewModel)
                        },
                        onDismiss = {
                            bottomSheet.dismiss()
                        }
                    )
                }
            }
        }
        bottomSheet.setContentView(composeView)
        bottomSheet.setOnDismissListener { dismissed() }
        bottomSheet.show()
    }

    fun showShortcutsSheet(context: Activity, lifeCycle: LifecycleOwner, commandTemplateViewModel: CommandTemplateViewModel){
        val bottomSheet = BottomSheetDialog(context)
        val composeView = ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val shortcuts by commandTemplateViewModel.shortcuts.asFlow().collectAsState(initial = emptyList())
                YtdlnisTheme {
                    ShortcutsSheet(
                        shortcuts = shortcuts.map { it.content },
                        onAddShortcut = { content ->
                            commandTemplateViewModel.insertShortcut(TemplateShortcut(0, content))
                        },
                        onRemoveShortcut = { content ->
                            shortcuts.find { it.content == content }?.let {
                                commandTemplateViewModel.deleteShortcut(it)
                            }
                        },
                        onDismiss = { bottomSheet.dismiss() }
                    )
                }
            }
        }
        bottomSheet.setContentView(composeView)
        bottomSheet.show()
    }

    fun copyLinkToClipBoard(context: Context, url: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(context.getString(R.string.url), url)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, context.getString(R.string.link_copied_to_clipboard), Toast.LENGTH_SHORT)
            .show()
    }

    fun openLinkIntent(context: Context, url: String) {
        val i = Intent(Intent.ACTION_VIEW)
        i.data = Uri.parse(url)
        context.startActivity(i)
    }



    fun showDatePickerOnly(fragmentManager: FragmentManager , onSubmit : (chosenDate: Calendar) -> Unit ){
        val date = Calendar.getInstance()

        val datePicker = MaterialDatePicker.Builder.datePicker()
            .setCalendarConstraints(
                CalendarConstraints.Builder()
                    .setValidator(DateValidatorPointForward.now())
                    .build()
            )
            .setSelection(MaterialDatePicker.todayInUtcMilliseconds())
            .build()

        datePicker.addOnPositiveButtonClickListener{
            date.timeInMillis = it
            onSubmit(date)
        }
        datePicker.show(fragmentManager, "datepicker")
    }

    fun showDatePicker(fragmentManager: FragmentManager , preferences: SharedPreferences, onSubmit : (chosenDate: Calendar) -> Unit ){
        val currentDate = Calendar.getInstance()
        currentDate.timeInMillis = (currentDate.timeInMillis - (currentDate.timeInMillis % 1800000)) + 1800000
        val date = Calendar.getInstance()

        val datePicker = MaterialDatePicker.Builder.datePicker()
            .setCalendarConstraints(
                CalendarConstraints.Builder()
                    .setValidator(DateValidatorPointForward.now())
                    .build()
            )
            .setSelection(MaterialDatePicker.todayInUtcMilliseconds())
            .build()

        datePicker.addOnPositiveButtonClickListener{
            date.timeInMillis = it

            showTimePicker(fragmentManager, preferences) { chosenTime ->
                date[Calendar.HOUR_OF_DAY] = chosenTime[Calendar.HOUR_OF_DAY]
                date[Calendar.MINUTE] = chosenTime[Calendar.MINUTE]
                onSubmit(date)
            }

        }
        datePicker.show(fragmentManager, "datepicker")
    }

    fun showTimePicker(fragmentManager: FragmentManager , preferences: SharedPreferences, onSubmit : (chosenTime: Calendar) -> Unit ){
        val currentDate = Calendar.getInstance()
        currentDate.timeInMillis = (currentDate.timeInMillis - (currentDate.timeInMillis % 1800000)) + 1800000
        val date = Calendar.getInstance()

        val lastTime = preferences.getString("latest_timepicker_date", "${currentDate.get(Calendar.HOUR_OF_DAY)}:${currentDate.get(Calendar.MINUTE)}")!!
        val hour = lastTime.split(":")[0].toInt()
        val minute = lastTime.split(":")[1].toInt()

        val timepicker = MaterialTimePicker.Builder()
            .setTimeFormat(TimeFormat.CLOCK_24H)
            .setHour(hour)
            .setMinute(minute)
            .build()

        timepicker.addOnPositiveButtonClickListener{
            date[Calendar.HOUR_OF_DAY] = timepicker.hour
            date[Calendar.MINUTE] = timepicker.minute
            preferences.edit().putString("latest_timepicker_date", "${timepicker.hour}:${timepicker.minute}").apply()
            onSubmit(date)
        }
        timepicker.show(fragmentManager, "timepicker")
    }

    fun showDownloadItemDetailsCard(
        item: DownloadItem,
        context: Activity,
        status: DownloadRepository.Status,
        ytdlpViewModel: YTDLPViewModel,
        preferences: SharedPreferences,
        removeItem : (DownloadItem, BottomSheetDialog) -> Unit,
        downloadItem: (DownloadItem) -> Unit,
        longClickDownloadButton: (DownloadItem) -> Unit,
        scheduleButtonClick: (DownloadItem) -> Unit?
    ){
        val bottomSheet = BottomSheetDialog(context)
        bottomSheet.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val composeView = ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                YtdlnisTheme {
                    DownloadDetailSheetContent(
                        item = item,
                        status = status,
                        ytdlpViewModel = ytdlpViewModel,
                        preferences = preferences,
                        onDismiss = { bottomSheet.dismiss() },
                        onRemoveItem = { itm ->
                            removeItem(itm, bottomSheet)
                        },
                        onDownloadItem = { itm ->
                            bottomSheet.dismiss()
                            downloadItem(itm)
                        },
                        onLongClickDownloadButton = { itm ->
                            bottomSheet.cancel()
                            longClickDownloadButton(itm)
                        },
                        onScheduleButtonClick = { itm ->
                            bottomSheet.dismiss()
                            scheduleButtonClick(itm)
                        }
                    )
                }
            }
        }
        bottomSheet.setContentView(composeView)
        bottomSheet.show()
        val displayMetrics = DisplayMetrics()
        context.windowManager.defaultDisplay.getMetrics(displayMetrics)
        bottomSheet.behavior.peekHeight = displayMetrics.heightPixels
        bottomSheet.window!!.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    fun showHistoryItemDetailsCard(
        item: HistoryItem?,
        context: Activity,
        isPresent: Boolean,
        preferences: SharedPreferences,
        removeItem: (item:HistoryItem, removeFiles: Boolean) -> Unit,
        redownloadItem: (HistoryItem) -> Unit,
        redownloadShowDownloadCard: (HistoryItem) -> Unit,
    ){
        if (item == null) return
        val bottomSheet = BottomSheetDialog(context)
        bottomSheet.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val composeView = ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                YtdlnisTheme {
                    HistoryDetailSheetContent(
                        item = item,
                        isPresent = isPresent,
                        preferences = preferences,
                        onDismiss = { bottomSheet.dismiss() },
                        onRemoveItem = { itm, removeFiles ->
                            removeItem(itm, removeFiles)
                            bottomSheet.dismiss()
                        },
                        onRedownloadItem = { itm ->
                            redownloadItem(itm)
                            bottomSheet.dismiss()
                        },
                        onRedownloadShowDownloadCard = { itm ->
                            redownloadShowDownloadCard(itm)
                            bottomSheet.dismiss()
                        }
                    )
                }
            }
        }
        bottomSheet.setContentView(composeView)
        bottomSheet.show()
    }
    
    fun showFormatDetails(format: Format, activity: Activity){
        val bottomSheet = BottomSheetDialog(activity)
        val composeView = ComposeView(activity).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                YtdlnisTheme {
                    FormatDetailsSheet(
                        format = format,
                        onDismiss = { bottomSheet.dismiss() }
                    )
                }
            }
        }
        bottomSheet.setContentView(composeView)
        bottomSheet.show()
    }

    @SuppressLint("RestrictedApi")
    fun showSubtitleLanguagesDialog(context: Activity, availableSubtitles: List<String>, currentValue: String, ok: (newValue: String) -> Unit){
        val bottomSheet = BottomSheetDialog(context)
        val composeView = ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                YtdlnisTheme {
                    SubtitleLanguageDialog(
                        currentValue = currentValue,
                        availableSubtitles = availableSubtitles,
                        onConfirm = {
                            ok(it)
                            bottomSheet.dismiss()
                        },
                        onDismiss = {
                            bottomSheet.dismiss()
                        }
                    )
                }
            }
        }
        bottomSheet.setContentView(composeView)
        bottomSheet.show()
    }

    fun showAudioBitrateDialog(context: Activity, currentValue: String, ok: (newValue: String) -> Unit) {
        val entries = context.resources.getStringArray(R.array.audio_bitrate)
        val entryValues = context.resources.getStringArray(R.array.audio_bitrate_values)
        val prefIndex = entryValues.indexOf(currentValue)
        MaterialAlertDialogBuilder(context)
            .setTitle(context.getString(R.string.bitrate))
            .setSingleChoiceItems(entries, prefIndex) { dialog, index ->
                ok(entryValues[index])
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    fun copyToClipboard(text: String, activity: Activity){
        val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(text, text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(activity, activity.getString(R.string.copied_to_clipboard), Toast.LENGTH_SHORT)
            .show()
    }


    suspend fun showCommandTemplates(activity: Activity, commandTemplateViewModel: CommandTemplateViewModel, onlyOne: Boolean = false, itemSelected: (itemSelected: List<CommandTemplate>) -> Unit) {
        val bottomSheet = BottomSheetDialog(activity)
        val list = withContext(Dispatchers.IO){
            commandTemplateViewModel.getAll()
        }
        val composeView = ComposeView(activity).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                YtdlnisTheme {
                    TemplatePickerSheet(
                        templates = list,
                        onlyOne = onlyOne,
                        onSelect = {
                            itemSelected(it)
                            bottomSheet.dismiss()
                        },
                        onDismiss = {
                            bottomSheet.dismiss()
                        }
                    )
                }
            }
        }
        bottomSheet.setContentView(composeView)
        bottomSheet.show()
    }

    suspend fun showShortcuts(activity: Activity, commandTemplateViewModel: CommandTemplateViewModel, itemSelected: (itemSelected: String) -> Unit, itemRemoved: (itemRemoved: String) -> Unit){
        val bottomSheet = BottomSheetDialog(activity)
        val shortcutList = withContext(Dispatchers.IO){
            commandTemplateViewModel.getAllShortcuts()
        }
        val composeView = ComposeView(activity).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                YtdlnisTheme {
                    ShortcutsListSheet(
                        shortcuts = shortcutList.map { it.content },
                        onSelect = {
                            itemSelected(it)
                            bottomSheet.dismiss()
                        },
                        onDismiss = {
                            bottomSheet.dismiss()
                        }
                    )
                }
            }
        }
        bottomSheet.setContentView(composeView)
        bottomSheet.show()
    }

    fun configureVideo(
        view: View,
        context: Activity,
        ytdlpViewModel: YTDLPViewModel,
        downloadItem: DownloadItem,
        itemChanged: (item: DownloadItem) -> Unit,
        videoCutListener: VideoCutListener? = null,
        videoCropListener: VideoCropListener? = null
    ) {
        // Migrated to DownloadConfigSheet (Compose)
    }

    fun configureAudio(
        view: View,
        context: Activity,
        ytdlpViewModel: YTDLPViewModel,
        downloadItem: DownloadItem,
        itemChanged: (item: DownloadItem) -> Unit
    ) {
        // Migrated to DownloadConfigSheet (Compose)
    }

    fun configureCommand(
        view: View,
        size: Int,
        shortcutCount: Int,
        newTemplateClicked: () -> Unit,
        editSelectedClicked: () -> Unit,
        shortcutClicked: suspend () -> Unit,
    ) {
        // Migrated to DownloadConfigSheet (Compose)
    }

    fun handleNoResults(context: Activity, message: String, url: String? = null, continueAnyway: Boolean = false, continued: () -> Unit, closed: () -> Unit, cookieFetch: () -> Unit) {
        val errDialog = MaterialAlertDialogBuilder(context)
            .setTitle(R.string.no_results)
            .setMessage(message)

        errDialog.setPositiveButton(R.string.copy_log) { d:DialogInterface?, _:Int ->
            val clipboard: ClipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setText(message)
            d?.dismiss()
        }

        val cookieRelated = message.contains("cookie", true) || message.contains("sign in", true)
        if (cookieRelated && !url.isNullOrBlank()) {
            errDialog.setNeutralButton(context.getString(R.string.get_cookies)) { d: DialogInterface?, _ : Int ->
                cookieFetch()
            }
        }else if (continueAnyway) {
            errDialog.setNeutralButton(R.string.continue_anyway) {d: DialogInterface?, _:Int ->
                continued()
            }
        }



        errDialog.setOnCancelListener {
            closed()
        }

        errDialog.show()
    }

    fun showErrorDialog(context: Context, it: String){
        val title = context.getString(R.string.errored)

        val errDialog = MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setMessage(it)
            .setPositiveButton(android.R.string.copy) { d:DialogInterface?, _:Int ->
                val clipboard: ClipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setText(it)
                d?.dismiss()
            }

        errDialog.show()
    }

    fun showGenericDeleteDialog(context: Context, itemTitle: String, accepted: () -> Unit){
        val deleteDialog = MaterialAlertDialogBuilder(context)
        deleteDialog.setTitle(context.getString(R.string.you_are_going_to_delete) + " \"" + itemTitle + "\"!")
        deleteDialog.setNegativeButton(context.getString(R.string.cancel)) { dialogInterface: DialogInterface, _: Int -> dialogInterface.cancel() }
        deleteDialog.setPositiveButton(context.getString(R.string.ok)) { _: DialogInterface?, _: Int ->
            accepted()
        }
        deleteDialog.show()
    }

    fun showGenericConfirmDialog(context: Context, title: String, content:String, accepted: () -> Unit){
        val deleteDialog = MaterialAlertDialogBuilder(context)
        deleteDialog.setTitle(title)
        deleteDialog.setMessage(content)
        deleteDialog.setNegativeButton(context.getString(R.string.cancel)) { dialogInterface: DialogInterface, _: Int -> dialogInterface.cancel() }
        deleteDialog.setPositiveButton(context.getString(R.string.continue_anyway)) { _: DialogInterface?, _: Int ->
            accepted()
        }
        deleteDialog.show()
    }

    fun showGenericDeleteAllDialog(context: Context, accepted: () -> Unit){
        val deleteDialog = MaterialAlertDialogBuilder(context)
        deleteDialog.setTitle(context.getString(R.string.you_are_going_to_delete_multiple_items))
        deleteDialog.setNegativeButton(context.getString(R.string.cancel)) { dialogInterface: DialogInterface, _: Int -> dialogInterface.cancel() }
        deleteDialog.setPositiveButton(context.getString(R.string.ok)) { _: DialogInterface?, _: Int ->
            accepted()
        }
        deleteDialog.show()
    }

    fun showRemoveHistoryItemDialog(item: HistoryItem, context: Activity, delete: (item: HistoryItem, deleteFile: Boolean) -> Unit){
        val deleteFile = booleanArrayOf(false)
        val deleteDialog = MaterialAlertDialogBuilder(context)
        deleteDialog.setTitle(context.getString(R.string.you_are_going_to_delete) + " \"" + item.title + "\"!")
        val path = item.downloadPath
        if (path.any { File(it).exists() && it.isNotEmpty() }) {
            deleteDialog.setMultiChoiceItems(
                arrayOf(context.getString(R.string.delete_file_too)),
                booleanArrayOf(false)
            ) { _: DialogInterface?, _: Int, b: Boolean -> deleteFile[0] = b }
        }
        deleteDialog.setNegativeButton(context.getString(R.string.cancel)) { dialogInterface: DialogInterface, _: Int -> dialogInterface.cancel() }
        deleteDialog.setPositiveButton(context.getString(R.string.ok)) { _: DialogInterface?, _: Int ->
            delete(item, deleteFile[0])
        }
        deleteDialog.show()
    }

    fun showFilenameTemplateDialog(
        context: Activity,
        currentFilename: String,
        dialogTitle: String = context.getString(R.string.file_name_template),
        ytdlpViewModel: YTDLPViewModel? = null,
        itemContext: DownloadItem? = null,
        filenameSelected: (f: String) -> Unit,
    ) {
        if (context is androidx.fragment.app.FragmentActivity) {
            FilenameTemplateDialogFragment.newInstance(
                initialFilename = currentFilename,
                dialogTitle = dialogTitle,
                itemContext = itemContext,
                ytdlpViewModel = ytdlpViewModel,
                onConfirm = filenameSelected,
            ).show(context.supportFragmentManager, FilenameTemplateDialogFragment.TAG)
            return
        }

        val dialog = AlertDialog.Builder(context).create()
        val composeView = ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                YtdlnisTheme {
                    com.deniscerri.ytdl.ui.compose.dialogs.FilenameTemplateDialog(
                        initialFilename = currentFilename,
                        dialogTitle = dialogTitle,
                        itemContext = itemContext,
                        ytdlpViewModel = ytdlpViewModel,
                        onConfirm = {
                            dialog.dismiss()
                            filenameSelected(it)
                        },
                        onDismiss = { dialog.dismiss() },
                    )
                }
            }
        }
        dialog.setView(composeView)
        dialog.show()
    }

    fun showSelectRangeDialog(context: Activity, itemCount: Int, rangeSelected: (rangeSelected: Pair<Int, Int>) -> Unit) {
        val builder = MaterialAlertDialogBuilder(context)
        builder.setTitle(context.getString(R.string.select_between))
        builder.setMessage(context.getString(R.string.select_between_desc))
        builder.setIcon(R.drawable.baseline_format_list_numbered_24)

        var firstIndex = 0
        var secondIndex = (itemCount - 1).coerceAtLeast(0)
        var isValid = true

        lateinit var dialog: AlertDialog

        val composeView = ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                YtdlnisTheme {
                    var startText by remember { mutableStateOf("1") }
                    var endText by remember { mutableStateOf(itemCount.toString()) }
                    var startError by remember { mutableStateOf<String?>(null) }
                    var endError by remember { mutableStateOf<String?>(null) }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        OutlinedTextField(
                            value = startText,
                            onValueChange = { newText ->
                                val digits = newText.filter { it.isDigit() }.take(9)
                                startText = digits
                                val sVal = digits.toIntOrNull()
                                val eVal = endText.toIntOrNull()
                                val sOk = sVal != null && sVal > 0
                                val eOk = eVal != null && eVal <= itemCount
                                startError = if (!sOk && digits.isNotEmpty()) "Invalid Number" else null
                                endError = if (!eOk && endText.isNotEmpty()) "Invalid Number" else null
                                isValid = sOk && eOk
                                if (sVal != null) firstIndex = sVal - 1
                                dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = isValid
                            },
                            label = { Text(stringResource(R.string.start)) },
                            isError = startError != null,
                            supportingText = startError?.let { { Text(it) } },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )

                        OutlinedTextField(
                            value = endText,
                            onValueChange = { newText ->
                                val digits = newText.filter { it.isDigit() }.take(9)
                                endText = digits
                                val sVal = startText.toIntOrNull()
                                val eVal = digits.toIntOrNull()
                                val sOk = sVal != null && sVal > 0
                                val eOk = eVal != null && eVal <= itemCount
                                startError = if (!sOk && startText.isNotEmpty()) "Invalid Number" else null
                                endError = if (!eOk && digits.isNotEmpty()) "Invalid Number" else null
                                isValid = sOk && eOk
                                if (eVal != null) secondIndex = eVal - 1
                                dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = isValid
                            },
                            label = { Text(stringResource(R.string.end)) },
                            isError = endError != null,
                            supportingText = endError?.let { { Text(it) } },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        builder.setView(composeView)
        builder.setPositiveButton(context.getString(R.string.ok)) { _: DialogInterface?, _: Int ->
            if (isValid) {
                rangeSelected(Pair(firstIndex, secondIndex))
            }
        }
        builder.setNegativeButton(context.getString(R.string.cancel)) { _: DialogInterface?, _: Int -> }

        dialog = builder.create()
        dialog.show()
    }

    fun showGeneratedCommand(context: Activity, preferences: SharedPreferences, command: String) {
        val builder = MaterialAlertDialogBuilder(context)
        builder.setTitle(context.getString(R.string.command))
        val composeView = ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                YtdlnisTheme {
                    SelectionContainer {
                        Text(
                            text = command,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 350.dp)
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 24.dp, vertical = 12.dp),
                        )
                    }
                }
            }
        }

        builder.setView(composeView)
        builder.setPositiveButton(
            context.getString(android.R.string.copy)
        ) { _: DialogInterface?, _: Int ->
            copyToClipboard(command, context)
        }

        // handle the negative button of the alert dialog
        builder.setNegativeButton(
            context.getString(R.string.dismiss)
        ) { _: DialogInterface?, _: Int -> }

        val dialog = builder.create()
        dialog.show()
    }

    fun showFullTextDialog(context: Activity, txt: String, title: String){
        val builder = MaterialAlertDialogBuilder(context)
        builder.setTitle(title)
        val composeView = ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                YtdlnisTheme {
                    SelectionContainer {
                        Text(
                            text = txt,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 350.dp)
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 24.dp, vertical = 12.dp),
                        )
                    }
                }
            }
        }

        builder.setView(composeView)
        builder.setPositiveButton(
            context.getString(android.R.string.copy)
        ) { _: DialogInterface?, _: Int ->
            copyToClipboard(txt, context)
        }

        // handle the negative button of the alert dialog
        builder.setNegativeButton(
            context.getString(R.string.dismiss)
        ) { _: DialogInterface?, _: Int -> }

        val dialog = builder.create()
        dialog.show()
    }


    fun getAlphaAnimator(view: View, alphaTo: Float): Animator {
        return ObjectAnimator.ofFloat(view, View.ALPHA, view.alpha, alphaTo)
    }

    fun getScaleXAnimator(view: View, scaleTo: Float): Animator {
        return ObjectAnimator.ofFloat(view, View.SCALE_X, view.scaleX, scaleTo)
    }

    fun getScaleYAnimator(view: View, scaleTo: Float): Animator {
        return ObjectAnimator.ofFloat(view, View.SCALE_Y, view.scaleX, scaleTo)
    }

    fun getElevationAnimator(view: MaterialCardView, @DimenRes elevationTo: Int): Animator {
        val valueFrom = view.cardElevation
        val valueTo = view.context.resources.getDimensionPixelSize(elevationTo).toFloat()

        return ValueAnimator.ofFloat(valueFrom, valueTo).apply {
            addUpdateListener {
                view.cardElevation = it.animatedValue as? Float ?: return@addUpdateListener
            }
        }
    }

    fun openOrShareFiles(context: Activity, paths: List<String>, isOpen: Boolean) {
        if (paths.size <= 1) {
            if (isOpen && paths.isNotEmpty()) {
                FileUtil.openFileIntent(context, paths.first())
            } else {
                FileUtil.shareFileIntent(context, paths)
            }
        } else {
            showPathChooserDialog(context, paths, isOpen)
        }
    }

    private fun showPathChooserDialog(context: Activity, paths: List<String>, isOpen: Boolean) {
        val names = paths.map { File(it).name }.toTypedArray()
        val checked = BooleanArray(paths.size) { !isOpen }
        var selectedIndex = -1

        val builder = MaterialAlertDialogBuilder(context)
            .setTitle(if (isOpen) context.getString(R.string.open_file) else context.getString(R.string.share))
            .setNegativeButton(context.getString(R.string.cancel), null)
            .setPositiveButton(context.getString(R.string.ok), null)

        var okButton: Button? = null

        if (isOpen) {
            builder.setSingleChoiceItems(names, -1) { _, which ->
                selectedIndex = which
                okButton?.isEnabled = true
            }
        } else {
            builder.setMultiChoiceItems(names, checked) { _, which, isChecked ->
                checked[which] = isChecked
                okButton?.isEnabled = checked.any { it }
            }
            builder.setNeutralButton(context.getString(R.string.toggle_all), null)
        }

        val dialog = builder.create()
        dialog.show()

        val ok = dialog.getButton(DialogInterface.BUTTON_POSITIVE)
        okButton = ok
        ok.isEnabled = !isOpen
        if (!isOpen) {
            dialog.getButton(DialogInterface.BUTTON_NEUTRAL).setOnClickListener {
                val newState = checked.any { !it }
                for (i in checked.indices) {
                    checked[i] = newState
                    dialog.listView.setItemChecked(i, newState)
                }
                ok.isEnabled = newState
            }
        }

        ok.setOnClickListener {
            if (isOpen) {
                if (selectedIndex >= 0) FileUtil.openFileIntent(context, paths[selectedIndex])
            } else {
                val selected = paths.filterIndexed { i, _ -> checked[i] }
                if (selected.isNotEmpty()) FileUtil.shareFileIntent(context, selected)
            }
            dialog.dismiss()
        }
    }

    fun showYTDLCustomSourceBottomSheet(context: Activity, preferences: SharedPreferences, selectedSource: (title: String, repo: String) -> Unit) {
        val bottomSheet = BottomSheetDialog(context)
        val defaultSourceTitles = context.getStringArray(R.array.ytdlp_source)
        val defaultSourceValues = context.getStringArray(R.array.ytdlp_source_values)

        val composeView = ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                var sourcesList by remember {
                    val raw = runCatching {
                        preferences.getStringSet("custom_ytdlp_sources", emptySet())!!.toMutableList()
                    }.getOrDefault(mutableListOf())
                    mutableStateOf(raw.mapNotNull { s ->
                        val arr = s.split("___")
                        if (arr.size >= 2) YtdlpSourceItem(arr[0], arr[1]) else null
                    })
                }
                var showAddEditDialog by remember { mutableStateOf(false) }
                var editingItem by remember { mutableStateOf<YtdlpSourceItem?>(null) }
                val currentRepo = preferences.getString("ytdlp_source", "stable") ?: "stable"

                YtdlnisTheme {
                    if (showAddEditDialog) {
                        AddEditYtdlpSourceSheet(
                            initialTitle = editingItem?.title ?: "",
                            initialRepo = editingItem?.repo ?: "",
                            onConfirm = { t, r ->
                                val oldItem = editingItem
                                if (oldItem != null) {
                                    val oldStr = "${oldItem.title}___${oldItem.repo}"
                                    val newStr = "${t}___${r}"
                                    val currentSet = preferences.getStringSet("custom_ytdlp_sources", emptySet())?.toMutableSet() ?: mutableSetOf()
                                    currentSet.remove(oldStr)
                                    currentSet.add(newStr)
                                    preferences.edit().putStringSet("custom_ytdlp_sources", currentSet).apply()
                                    sourcesList = sourcesList.map { if (it == oldItem) YtdlpSourceItem(t, r) else it }
                                    if (currentRepo == oldItem.repo) {
                                        selectedSource(t, r)
                                    }
                                } else {
                                    val newStr = "${t}___${r}"
                                    val currentSet = preferences.getStringSet("custom_ytdlp_sources", emptySet())?.toMutableSet() ?: mutableSetOf()
                                    currentSet.add(newStr)
                                    preferences.edit().putStringSet("custom_ytdlp_sources", currentSet).apply()
                                    sourcesList = sourcesList + YtdlpSourceItem(t, r)
                                    selectedSource(t, r)
                                }
                                showAddEditDialog = false
                                editingItem = null
                            },
                            onDismiss = {
                                showAddEditDialog = false
                                editingItem = null
                            }
                        )
                    } else {
                        YtdlpSourcesSheet(
                            sources = sourcesList,
                            currentSourceRepo = currentRepo,
                            onSelectSource = { t, r ->
                                bottomSheet.dismiss()
                                selectedSource(t, r)
                            },
                            onAddSource = {
                                editingItem = null
                                showAddEditDialog = true
                            },
                            onEditSource = { item ->
                                editingItem = item
                                showAddEditDialog = true
                            },
                            onDeleteSource = { item ->
                                val str = "${item.title}___${item.repo}"
                                val currentSet = preferences.getStringSet("custom_ytdlp_sources", emptySet())?.toMutableSet() ?: mutableSetOf()
                                currentSet.remove(str)
                                preferences.edit().putStringSet("custom_ytdlp_sources", currentSet).apply()
                                sourcesList = sourcesList - item
                                if (currentRepo == item.repo) {
                                    selectedSource(defaultSourceTitles.first(), defaultSourceValues.first())
                                    bottomSheet.dismiss()
                                }
                            },
                            onDismiss = { bottomSheet.dismiss() }
                        )
                    }
                }
            }
        }
        bottomSheet.setContentView(composeView)
        bottomSheet.show()
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    fun showNewAppUpdateDialog(
        v: GithubRelease,
        context: Activity,
        updateUtil: UpdateUtil,
        lifecycleOwner: LifecycleOwner,
        preferences: SharedPreferences,
        installLauncher: ActivityResultLauncher<Intent>
    ) {
        if (context.isFinishing || context.isDestroyed) return
        var positiveButton: Button? = null
        var negativeButton: Button? = null
        var neutralButton: Button? = null
        var tmpDownloadJob: Job? = null

        val skippedVersions = preferences.getString("skip_updates", "")?.split(",")?.distinct()?.toMutableList() ?: mutableListOf()

        val updateDialog = MaterialAlertDialogBuilder(context)
            .setTitle(v.tag_name)
            .setMessage(v.body)
            .setCancelable(false)
            .setIcon(R.drawable.ic_update_app)
            .setNeutralButton(R.string.ignore){ d: DialogInterface?, _:Int ->
                tmpDownloadJob?.cancel()
                skippedVersions.add(v.tag_name)
                preferences.edit().putString("skip_updates", skippedVersions.joinToString(",")).apply()
                d?.dismiss()
            }
            .setNegativeButton(R.string.cancel) { _: DialogInterface?, _: Int ->
                tmpDownloadJob?.cancel()
            }
            .setPositiveButton(R.string.update, null)
        val view = updateDialog.show()
        val textView = view.findViewById<TextView>(android.R.id.message)
        textView!!.movementMethod = LinkMovementMethod.getInstance()
        val mw = Markwon.builder(context).usePlugin(object: AbstractMarkwonPlugin() {

            override fun configureConfiguration(builder: MarkwonConfiguration.Builder) {
                builder.linkResolver { view, link ->
                    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(link))
                    context.startActivity(browserIntent)
                }
            }
        }).build()
        mw.setMarkdown(textView, v.body)

        positiveButton = view.getButton(android.app.AlertDialog.BUTTON_POSITIVE)
        negativeButton = view.getButton(android.app.AlertDialog.BUTTON_NEGATIVE)
        neutralButton = view.getButton(android.app.AlertDialog.BUTTON_NEUTRAL)

        positiveButton?.setOnClickListener {
            positiveButton.isEnabled = false
            positiveButton.text = "0%"

            val lifecycleScope = lifecycleOwner.lifecycleScope

            tmpDownloadJob = lifecycleScope.launch {
                val fileResp = updateUtil.downloadReleaseApk(v) { progress ->
                    lifecycleScope.launch {
                        withContext(Dispatchers.Main) {
                            positiveButton.text = "$progress%"
                        }
                    }
                }

                fileResp.onFailure {
                    lifecycleScope.launch {
                        withContext(Dispatchers.Main) {
                            view.dismiss()
                            Snackbar.make(
                                context.findViewById(R.id.frame_layout),
                                it.message ?: context.getString(R.string.errored),
                                Snackbar.LENGTH_LONG
                            ).show()
                        }
                    }
                }

                fileResp.onSuccess { file ->
                    lifecycleScope.launch {
                        withContext(Dispatchers.Main) {
                            positiveButton.text = context.getString(R.string.please_wait)
                            negativeButton.isEnabled = false
                            neutralButton.isEnabled = false

                            ApkInstallUtil.installApk(context, file, installLauncher) { result ->
                                result.onSuccess {
                                }.onFailure { f ->
                                    Snackbar.make(context.findViewById(R.id.frame_layout), f.message ?: "", Snackbar.LENGTH_LONG).show()
                                }
                                view.dismiss()
                            }
                        }
                    }
                }
            }
        }
    }

    fun showNewReleaseUpdateDialog(
        item: PackageBase.PackageRelease,
        packageItem: PackageItem,
        context: Activity,
        lifecycleOwner: LifecycleOwner,
        activityView: View,
        snackbarAnchorView: View?,
        installLauncher: ActivityResultLauncher<Intent>,
        onResult: (result: Result<Unit>) -> Unit
    ) {
        var tmpDownloadJob : Job? = null

        var positiveButton: Button? = null
        var negativeButton: Button? = null

        val updateDialog = MaterialAlertDialogBuilder(context)
            .setTitle("${item.tag_name} (${FileUtil.convertFileSize(item.downloadSize)})")
            .setMessage(item.body)
            .setIcon(R.drawable.ic_update_app)
            .setCancelable(false)
            .setNegativeButton(context.getString(R.string.cancel)) { _: DialogInterface?, _: Int ->
                tmpDownloadJob?.cancel()
            }
            .setPositiveButton(context.getString(R.string.download), null)
        val view = updateDialog.show()
        val textView = view.findViewById<TextView>(android.R.id.message)
        textView!!.movementMethod = LinkMovementMethod.getInstance()
        val mw = Markwon.builder(context).usePlugin(object: AbstractMarkwonPlugin() {

            override fun configureConfiguration(builder: MarkwonConfiguration.Builder) {
                builder.linkResolver { view, link ->
                    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(link))
                    context.startActivity(browserIntent)
                }
            }
        }).build()
        mw.setMarkdown(textView, item.body)

        val lifecycleScope = lifecycleOwner.lifecycleScope

        positiveButton = view.getButton(android.app.AlertDialog.BUTTON_POSITIVE)
        negativeButton = view.getButton(android.app.AlertDialog.BUTTON_NEGATIVE)

        positiveButton?.setOnClickListener {
            positiveButton.isEnabled = false
            positiveButton.text = "0%"

            tmpDownloadJob = lifecycleScope.launch {
                val instance = packageItem.getInstance()
                val fileResp = instance.downloadReleaseApk(item) { progress ->
                    lifecycleScope.launch {
                        withContext(Dispatchers.Main) {
                            positiveButton.text = "$progress%"
                        }
                    }
                }

                fileResp.onFailure {
                    lifecycleScope.launch {
                        withContext(Dispatchers.Main) {
                            view.dismiss()
                            val snackbar = Snackbar.make(activityView, it.message ?: context.getString(R.string.errored), Snackbar.LENGTH_LONG)
                            snackbar.anchorView = snackbarAnchorView
                            snackbar.show()
                        }
                    }
                }

                fileResp.onSuccess { file ->
                    lifecycleScope.launch {
                        withContext(Dispatchers.Main) {
                            positiveButton.text = context.getString(R.string.please_wait)
                            negativeButton.isEnabled = false

                            ApkInstallUtil.installApk(context, file, installLauncher) { result ->
                                onResult(result)
                                view.dismiss()
                            }
                        }
                    }
                }
            }
        }
    }


    fun showObserveSourceDetailsCard(
        item: ObserveSourcesItem,
        context: Activity,
        onCheckNow: () -> Unit,
        onPauseResume: () -> Unit,
        onEdit: () -> Unit,
        onDelete: () -> Unit,
        onReScanFromScratch: () -> Unit,
        onSkipBacklog: () -> Unit,
        onUnskipIgnored: () -> Unit,
    ) {
        val bottomSheet = BottomSheetDialog(context)
        bottomSheet.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val composeView = ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                YtdlnisTheme {
                    ObserveSourceDetailsSheet(
                        item = item,
                        onDismiss = { bottomSheet.dismiss() },
                        onOpenSettings = {
                            val settingsSheet = BottomSheetDialog(context)
                            settingsSheet.requestWindowFeature(Window.FEATURE_NO_TITLE)
                            val settingsComposeView = ComposeView(context).apply {
                                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                                setContent {
                                    YtdlnisTheme {
                                        ObserveSourceSettingsSheet(
                                            item = item,
                                            onDismiss = { settingsSheet.dismiss() },
                                            onReScanFromScratch = {
                                                settingsSheet.dismiss()
                                                onReScanFromScratch()
                                            },
                                            onSkipBacklog = {
                                                settingsSheet.dismiss()
                                                onSkipBacklog()
                                            },
                                            onUnskipIgnored = {
                                                settingsSheet.dismiss()
                                                onUnskipIgnored()
                                            }
                                        )
                                    }
                                }
                            }
                            settingsSheet.setContentView(settingsComposeView)
                            settingsSheet.show()
                        },
                        onCheckNow = {
                            bottomSheet.dismiss()
                            onCheckNow()
                        },
                        onPauseResume = {
                            bottomSheet.dismiss()
                            onPauseResume()
                        },
                        onEdit = {
                            bottomSheet.dismiss()
                            onEdit()
                        },
                        onDelete = {
                            bottomSheet.dismiss()
                            onDelete()
                        },
                        onOpenLink = { url ->
                            openLinkIntent(context, url)
                        },
                        onCopyLink = { url ->
                            copyLinkToClipBoard(context, url)
                        },
                        onShowLinkList = { title, links ->
                            showFullTextDialog(context, links.joinToString("\n"), title)
                        }
                    )
                }
            }
        }
        bottomSheet.setContentView(composeView)
        bottomSheet.show()
    }

    fun showDownloadDelayDialog(context: Activity, sharedPreferences: SharedPreferences, rangeSelected: (rangeSelected: Pair<Float, Float>) -> Unit, resetSelected: () -> Unit) {
        val builder = MaterialAlertDialogBuilder(context)
        builder.setTitle(context.getString(R.string.download_delay))
        builder.setMessage(context.getString(R.string.download_delay_summary))
        builder.setIcon(R.drawable.baseline_timer_24)

        val downloadDelay = sharedPreferences.getString("download_delay", "0-0") ?: "0-0"
        val parts = downloadDelay.split("-")
        val initialMin = parts.getOrNull(0) ?: "0"
        val initialMax = parts.getOrNull(1) ?: "0"

        var firstIndex = initialMin.toFloatOrNull() ?: 0f
        var secondIndex = initialMax.toFloatOrNull() ?: 0f
        var isValid = true

        lateinit var dialog: AlertDialog

        val composeView = ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                YtdlnisTheme {
                    var minText by remember { mutableStateOf(initialMin) }
                    var maxText by remember { mutableStateOf(initialMax) }
                    var minError by remember { mutableStateOf<String?>(null) }
                    var maxError by remember { mutableStateOf<String?>(null) }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = minText,
                            onValueChange = { newText ->
                                minText = newText
                                val sVal = newText.toFloatOrNull()
                                val eVal = maxText.toFloatOrNull()
                                val sOk = sVal != null && sVal >= 0f
                                val eOk = eVal != null && sVal != null && eVal >= sVal
                                minError = if (!sOk && newText.isNotBlank()) "Invalid Number" else null
                                maxError = if (!eOk && maxText.isNotBlank()) "Invalid Number" else null
                                isValid = sOk && eOk
                                if (sVal != null) firstIndex = sVal
                                dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = isValid
                            },
                            label = { Text(stringResource(R.string.min_delay)) },
                            suffix = { Text("s") },
                            isError = minError != null,
                            supportingText = minError?.let { { Text(it) } },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )

                        Text(
                            text = "–",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = if (minError != null || maxError != null) 16.dp else 0.dp),
                        )

                        OutlinedTextField(
                            value = maxText,
                            onValueChange = { newText ->
                                maxText = newText
                                val sVal = minText.toFloatOrNull()
                                val eVal = newText.toFloatOrNull()
                                val sOk = sVal != null && sVal >= 0f
                                val eOk = eVal != null && sVal != null && eVal >= sVal
                                minError = if (!sOk && minText.isNotBlank()) "Invalid Number" else null
                                maxError = if (!eOk && newText.isNotBlank()) "Invalid Number" else null
                                isValid = sOk && eOk
                                if (eVal != null) secondIndex = eVal
                                dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = isValid
                            },
                            label = { Text(stringResource(R.string.max_delay)) },
                            suffix = { Text("s") },
                            isError = maxError != null,
                            supportingText = maxError?.let { { Text(it) } },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        builder.setView(composeView)
        builder.setPositiveButton(context.getString(R.string.ok)) { _: DialogInterface?, _: Int ->
            if (isValid) {
                rangeSelected(Pair(firstIndex, secondIndex))
            }
        }
        builder.setNegativeButton(context.getString(R.string.cancel)) { _: DialogInterface?, _: Int -> }
        builder.setNeutralButton(context.getString(R.string.reset)) { _: DialogInterface?, _: Int ->
            resetSelected()
        }

        dialog = builder.create()
        dialog.show()
    }

    fun showChooseInstallerAppDialog(context: Activity, appIdSelected: (appId: String) -> Unit) {
        val builder = MaterialAlertDialogBuilder(context)
        builder.setTitle(context.getString(R.string.choose_installer_app))
        builder.setIcon(R.drawable.baseline_install_mobile_24)

        data class ExternalInstallerApp(
            val appName: String,
            val packageName: String,
            val icon: Drawable
        )

        lateinit var alertDialog: AlertDialog

        val installersList = mutableListOf<ExternalInstallerApp>()
        val packageManager = context.packageManager
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse("content://dummy"), "application/vnd.android.package-archive")
        }

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong())
        } else {
            0
        }

        val resolveInfoList = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(intent, flags as PackageManager.ResolveInfoFlags)
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL)
        }

        for (resolveInfo in resolveInfoList) {
            val activityInfo = resolveInfo.activityInfo ?: continue
            val appPackageName = activityInfo.packageName

            // Exclude your own app from the list of external options
            if (appPackageName == context.packageName) continue

            val appName = resolveInfo.loadLabel(packageManager).toString()
            val appIcon = resolveInfo.loadIcon(packageManager)

            installersList.add(
                ExternalInstallerApp(
                    appName = appName,
                    packageName = appPackageName,
                    icon = appIcon
                )
            )
        }

        val finalList = installersList.distinctBy { it.packageName }.sortedBy { it.appName }
        if (finalList.isEmpty()) {
            return
        }

        val composeView = ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                YtdlnisTheme {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp)
                            .padding(vertical = 8.dp),
                    ) {
                        items(finalList) { app ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        appIdSelected(app.packageName)
                                        alertDialog.dismiss()
                                    }
                                    .padding(horizontal = 20.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                AndroidView(
                                    factory = { ctx ->
                                        ImageView(ctx).apply {
                                            scaleType = ImageView.ScaleType.CENTER_CROP
                                        }
                                    },
                                    update = { it.setImageDrawable(app.icon) },
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape),
                                )
                                Spacer(Modifier.width(16.dp))
                                Text(
                                    text = app.appName,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }
        }

        builder.setView(composeView)
        alertDialog = builder.create()
        alertDialog.show()
    }
}