package com.deniscerri.ytdl.ui.compose.observesources

import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.text.format.DateFormat
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentManager
import androidx.preference.PreferenceManager
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.enums.DownloadType
import com.deniscerri.ytdl.database.models.DownloadItem
import com.deniscerri.ytdl.database.models.observeSources.ObserveSourcesItem
import com.deniscerri.ytdl.database.models.observeSources.ObserveSourcesMonthlyConfig
import com.deniscerri.ytdl.database.models.observeSources.ObserveSourcesWeeklyConfig
import com.deniscerri.ytdl.database.repository.ObserveSourcesRepository
import com.deniscerri.ytdl.database.viewmodel.DownloadViewModel
import com.deniscerri.ytdl.ui.compose.components.YtdlBottomSheet
import com.deniscerri.ytdl.util.UiUtil
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Observe source configuration bottom sheet composable.
 * Replaces `observe_sources_bottom_sheet.xml`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ObserveSourceConfigSheet(
    currentItem: ObserveSourcesItem?,
    initialType: DownloadType,
    downloadViewModel: DownloadViewModel,
    fragmentManager: FragmentManager,
    onDismiss: () -> Unit,
    onSave: (ObserveSourcesItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    YtdlBottomSheet(onDismissRequest = onDismiss) {
        ObserveSourceConfigContent(
            currentItem = currentItem,
            initialType = initialType,
            downloadViewModel = downloadViewModel,
            fragmentManager = fragmentManager,
            onDismiss = onDismiss,
            onSave = onSave,
            modifier = modifier,
        )
    }
}

private enum class EndsOption { NEVER, ON_DATE, AFTER_RUNS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ObserveSourceConfigContent(
    currentItem: ObserveSourcesItem?,
    initialType: DownloadType,
    downloadViewModel: DownloadViewModel,
    fragmentManager: FragmentManager,
    onDismiss: () -> Unit,
    onSave: (ObserveSourcesItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val sharedPreferences = remember { PreferenceManager.getDefaultSharedPreferences(context) }

    var title by remember { mutableStateOf(currentItem?.name ?: "") }
    var url by remember { mutableStateOf(currentItem?.url ?: "") }
    var selectedType by remember {
        mutableStateOf(currentItem?.downloadItemTemplate?.type ?: initialType)
    }

    var everyNr by remember { mutableStateOf(currentItem?.everyNr?.toString() ?: "1") }
    var selectedCategory by remember {
        mutableStateOf(currentItem?.everyCategory ?: ObserveSourcesRepository.EveryCategory.HOUR)
    }
    var everyTime by remember {
        mutableLongStateOf(currentItem?.everyTime ?: 0L)
    }

    // Weekly config
    var selectedDays by remember {
        mutableStateOf(currentItem?.weeklyConfig?.weekDays?.toSet() ?: emptySet())
    }

    // Monthly config
    var everyMonthDay by remember {
        mutableStateOf(
            currentItem?.monthlyConfig?.everyMonthDay?.toString()
                ?: Calendar.getInstance().get(Calendar.DAY_OF_MONTH).toString()
        )
    }
    var startsMonth by remember {
        mutableIntStateOf(
            currentItem?.monthlyConfig?.startsMonth
                ?: Calendar.getInstance().get(Calendar.MONTH)
        )
    }

    // Starts Date
    var startsTime by remember {
        mutableLongStateOf(
            if ((currentItem?.startsTime ?: 0L) > 0L) currentItem!!.startsTime
            else Calendar.getInstance().timeInMillis
        )
    }

    // Ends
    var endsOption by remember {
        mutableStateOf(
            when {
                (currentItem?.endsDate ?: 0L) > 0L -> EndsOption.ON_DATE
                (currentItem?.endsAfterCount ?: 0) > 0 -> EndsOption.AFTER_RUNS
                else -> EndsOption.NEVER
            }
        )
    }
    var endsDate by remember {
        mutableLongStateOf(
            if ((currentItem?.endsDate ?: 0L) > 0L) currentItem!!.endsDate
            else Calendar.getInstance().apply { add(Calendar.MONTH, 5) }.timeInMillis
        )
    }
    var endsAfterNr by remember {
        mutableStateOf(
            if ((currentItem?.endsAfterCount ?: 0) > 0) currentItem!!.endsAfterCount.toString()
            else "10"
        )
    }

    // Switches
    var retryMissingDownloads by remember { mutableStateOf(currentItem?.retryMissingDownloads ?: false) }
    var getOnlyNewUploads by remember { mutableStateOf(currentItem?.getOnlyNewUploads ?: false) }
    var syncWithSource by remember { mutableStateOf(currentItem?.syncWithSource ?: false) }

    // Validation
    val isTitleValid = title.isNotBlank()
    val isUrlValid = url.isNotBlank()
    val isEveryNrValid = (everyNr.toIntOrNull() ?: 0) > 0
    val isEveryTimeValid = when (selectedCategory) {
        ObserveSourcesRepository.EveryCategory.MINUTE, ObserveSourcesRepository.EveryCategory.HOUR -> true
        else -> everyTime > 0L
    }
    val isEndsValid = when (endsOption) {
        EndsOption.NEVER -> true
        EndsOption.ON_DATE -> endsDate > 0L
        EndsOption.AFTER_RUNS -> (endsAfterNr.toIntOrNull() ?: 0) > 0
    }
    val isValid = isTitleValid && isUrlValid && isEveryNrValid && isEveryTimeValid && isEndsValid

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        // Header Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.observe_sources),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.configure_download),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Button(
                onClick = {
                    val template: DownloadItem = currentItem?.downloadItemTemplate
                        ?: downloadViewModel.createDownloadItemFromResult(
                            result = null,
                            url = url,
                            givenType = selectedType
                        )
                    template.url = url

                    val finalItem = ObserveSourcesItem(
                        id = currentItem?.id ?: 0,
                        name = title,
                        url = url,
                        downloadItemTemplate = template,
                        everyNr = everyNr.toIntOrNull() ?: 1,
                        everyCategory = selectedCategory,
                        everyTime = everyTime,
                        weeklyConfig = if (selectedCategory == ObserveSourcesRepository.EveryCategory.WEEK) {
                            ObserveSourcesWeeklyConfig(weekDays = selectedDays.toList())
                        } else null,
                        monthlyConfig = if (selectedCategory == ObserveSourcesRepository.EveryCategory.MONTH) {
                            ObserveSourcesMonthlyConfig(
                                everyMonthDay = everyMonthDay.toIntOrNull() ?: 1,
                                startsMonth = startsMonth
                            )
                        } else null,
                        status = ObserveSourcesRepository.SourceStatus.ACTIVE,
                        startsTime = startsTime,
                        endsDate = if (endsOption == EndsOption.ON_DATE) endsDate else 0L,
                        endsAfterCount = if (endsOption == EndsOption.AFTER_RUNS) endsAfterNr.toIntOrNull() ?: 0 else 0,
                        runCount = currentItem?.runCount ?: 0,
                        getOnlyNewUploads = getOnlyNewUploads,
                        syncWithSource = syncWithSource,
                        retryMissingDownloads = retryMissingDownloads,
                        alreadyProcessedLinks = currentItem?.alreadyProcessedLinks ?: mutableListOf(),
                        ignoredLinks = if (!getOnlyNewUploads) mutableListOf() else currentItem?.ignoredLinks ?: mutableListOf()
                    )
                    onSave(finalItem)
                },
                enabled = isValid
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_check),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = if (currentItem != null) stringResource(R.string.update) else stringResource(R.string.ok))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Title Input
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text(stringResource(R.string.title)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(12.dp))

        // URL Input with paste/clear
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("URL") },
            trailingIcon = {
                if (url.isNotEmpty()) {
                    IconButton(onClick = { url = "" }) {
                        Icon(painter = painterResource(R.drawable.ic_delete_all), contentDescription = stringResource(R.string.clear))
                    }
                } else {
                    IconButton(onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        url = clipboard.primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
                    }) {
                        Icon(painter = painterResource(R.drawable.ic_clipboard), contentDescription = stringResource(R.string.paste))
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Download Type Selector Tabs
        Text(
            text = stringResource(R.string.download_type),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(6.dp))
        val downloadTypes = listOf(DownloadType.audio, DownloadType.video, DownloadType.command)
        val selectedIndex = downloadTypes.indexOf(selectedType).coerceAtLeast(0)
        PrimaryTabRow(selectedTabIndex = selectedIndex) {
            downloadTypes.forEachIndexed { index, type ->
                Tab(
                    selected = selectedIndex == index,
                    onClick = { selectedType = type },
                    text = {
                        Text(
                            when (type) {
                                DownloadType.audio -> stringResource(R.string.audio)
                                DownloadType.video -> stringResource(R.string.video)
                                else -> stringResource(R.string.command)
                            }
                        )
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Every X Category
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = everyNr,
                onValueChange = { everyNr = it },
                label = { Text(stringResource(R.string.every)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
                singleLine = true
            )

            var catDropdownExpanded by remember { mutableStateOf(false) }
            val categories = ObserveSourcesRepository.EveryCategory.entries
            ExposedDropdownMenuBox(
                expanded = catDropdownExpanded,
                onExpandedChange = { catDropdownExpanded = !catDropdownExpanded },
                modifier = Modifier.weight(1.5f)
            ) {
                val catLabel = stringResource(ObserveSourcesRepository.everyCategoryName[selectedCategory] ?: R.string.hours)
                OutlinedTextField(
                    value = catLabel,
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = catDropdownExpanded) },
                    modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                )
                ExposedDropdownMenu(
                    expanded = catDropdownExpanded,
                    onDismissRequest = { catDropdownExpanded = false }
                ) {
                    categories.forEach { cat ->
                        val label = stringResource(ObserveSourcesRepository.everyCategoryName[cat] ?: R.string.hours)
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                selectedCategory = cat
                                catDropdownExpanded = false
                            }
                        )
                    }
                }
            }
        }

        // At Time (for Day/Week/Month)
        if (selectedCategory != ObserveSourcesRepository.EveryCategory.MINUTE && selectedCategory != ObserveSourcesRepository.EveryCategory.HOUR) {
            Spacer(modifier = Modifier.height(12.dp))
            val timeText = remember(everyTime) {
                if (everyTime > 0L) {
                    val pattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), "HHmm")
                    SimpleDateFormat(pattern, Locale.getDefault()).format(everyTime)
                } else ""
            }
            OutlinedButton(
                onClick = {
                    UiUtil.showTimePicker(fragmentManager, sharedPreferences) { cal ->
                        everyTime = cal.timeInMillis
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(painter = painterResource(R.drawable.ic_clock), contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = if (timeText.isNotEmpty()) timeText else stringResource(R.string.time))
            }
        }

        // Weekdays (for Week)
        if (selectedCategory == ObserveSourcesRepository.EveryCategory.WEEK) {
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val cal = Calendar.getInstance().apply {
                    set(Calendar.YEAR, 2024)
                    set(Calendar.MONTH, Calendar.JANUARY)
                    set(Calendar.DAY_OF_MONTH, 1) // Monday
                }
                for (i in 1..7) {
                    val dayName = cal.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.SHORT, Locale.getDefault()) ?: ""
                    FilterChip(
                        selected = i in selectedDays,
                        onClick = {
                            selectedDays = if (i in selectedDays) selectedDays - i else selectedDays + i
                        },
                        label = { Text(dayName) }
                    )
                    cal.add(Calendar.DAY_OF_WEEK, 1)
                }
            }
        }

        // Monthly Config (Day of month + Start month)
        if (selectedCategory == ObserveSourcesRepository.EveryCategory.MONTH) {
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = everyMonthDay,
                onValueChange = { everyMonthDay = it },
                label = { Text(stringResource(R.string.day_of_month)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }

        // Start Date
        Spacer(modifier = Modifier.height(12.dp))
        val startDateText = remember(startsTime) {
            val pattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), "ddMMMyyyy")
            SimpleDateFormat(pattern, Locale.getDefault()).format(startsTime)
        }
        OutlinedButton(
            onClick = {
                UiUtil.showDatePickerOnly(fragmentManager) { cal ->
                    startsTime = cal.timeInMillis
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(painter = painterResource(R.drawable.ic_clock), contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = "${stringResource(R.string.start_date)}: $startDateText")
        }

        // Ends Section
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.ends),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            RadioButton(
                selected = endsOption == EndsOption.NEVER,
                onClick = { endsOption = EndsOption.NEVER }
            )
            Text(stringResource(R.string.never), modifier = Modifier.padding(start = 4.dp))
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            RadioButton(
                selected = endsOption == EndsOption.ON_DATE,
                onClick = { endsOption = EndsOption.ON_DATE }
            )
            Text(stringResource(R.string.on), modifier = Modifier.padding(start = 4.dp))
            if (endsOption == EndsOption.ON_DATE) {
                Spacer(modifier = Modifier.width(8.dp))
                val endDateText = remember(endsDate) {
                    val pattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), "ddMMMyyyy")
                    SimpleDateFormat(pattern, Locale.getDefault()).format(endsDate)
                }
                OutlinedButton(onClick = {
                    UiUtil.showDatePickerOnly(fragmentManager) { cal -> endsDate = cal.timeInMillis }
                }) {
                    Text(endDateText)
                }
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            RadioButton(
                selected = endsOption == EndsOption.AFTER_RUNS,
                onClick = { endsOption = EndsOption.AFTER_RUNS }
            )
            Text(stringResource(R.string.after), modifier = Modifier.padding(start = 4.dp))
            if (endsOption == EndsOption.AFTER_RUNS) {
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedTextField(
                    value = endsAfterNr,
                    onValueChange = { endsAfterNr = it },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(100.dp),
                    singleLine = true
                )
            }
        }

        // Switches
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = stringResource(R.string.retry_missing_downloads), style = MaterialTheme.typography.bodyMedium)
            Switch(checked = retryMissingDownloads, onCheckedChange = { retryMissingDownloads = it })
        }

        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = stringResource(R.string.get_new_uploads), style = MaterialTheme.typography.bodyMedium)
            Switch(checked = getOnlyNewUploads, onCheckedChange = { getOnlyNewUploads = it })
        }

        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = stringResource(R.string.sync_with_source), style = MaterialTheme.typography.bodyMedium)
            Switch(checked = syncWithSource, onCheckedChange = { syncWithSource = it })
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}
