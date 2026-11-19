package com.deniscerri.ytdl.ui.compose.commands

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.DBManager.SORTING
import com.deniscerri.ytdl.database.models.CommandTemplate
import com.deniscerri.ytdl.database.repository.CommandTemplateRepository.CommandTemplateSortType
import com.deniscerri.ytdl.ui.compose.components.EmptyState
import com.deniscerri.ytdl.ui.compose.components.YtdlBottomSheet
import com.deniscerri.ytdl.ui.compose.components.YtdlScaffold

/**
 * Command templates (Phase 3). Replaces `fragment_command_templates.xml`,
 * `command_template_item.xml`, `command_templates_sort_sheet.xml` and `TemplatesAdapter`.
 *
 * Behaviour kept 1:1: the chip row (new / shortcuts / sort), live search, empty state only when the
 * table itself is empty, swipe-left delete with confirmation, long-press multi-select handled by the
 * AppCompat `ActionMode` in the host fragment, and the import/export overflow actions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommandTemplatesScreen(
    templates: List<CommandTemplate>,
    isTableEmpty: Boolean,
    selectedIds: Set<Long>,
    sortType: CommandTemplateSortType,
    sortOrder: SORTING,
    swipeGestures: Boolean,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    onNewTemplate: () -> Unit,
    onOpenShortcuts: () -> Unit,
    onSortTypeSelected: (CommandTemplateSortType) -> Unit,
    onOpenTemplate: (CommandTemplate) -> Unit,
    onToggleSelection: (CommandTemplate) -> Unit,
    onDeleteTemplate: (CommandTemplate) -> Unit,
    onExportToClipboard: () -> Unit,
    onImportFromClipboard: () -> Unit,
) {
    var searchActive by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var showSortSheet by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current

    fun closeSearch() {
        searchActive = false
        keyboard?.hide()
    }

    YtdlScaffold(
        title = stringResource(R.string.command_templates),
        onBack = onBack,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        actions = {
            IconButton(onClick = { searchActive = true }) {
                Icon(
                    painter = painterResource(R.drawable.ic_search),
                    contentDescription = stringResource(R.string.search),
                )
            }
            TemplatesOverflowMenu(
                onImport = onImportFromClipboard,
                onExport = onExportToClipboard,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (searchActive) {
                SearchField(
                    query = query,
                    onQueryChange = {
                        query = it
                        onSearchQueryChanged(it)
                    },
                    onClose = {
                        query = ""
                        onSearchQueryChanged("")
                        closeSearch()
                    },
                    onSearch = { closeSearch() },
                )
            }

            TemplatesChipRow(
                sortType = sortType,
                sortOrder = sortOrder,
                onNewTemplate = onNewTemplate,
                onOpenShortcuts = onOpenShortcuts,
                onSortClick = { showSortSheet = true },
            )

            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 8.dp),
                ) {
                    items(templates, key = { it.id }) { item ->
                        val card: @Composable () -> Unit = {
                            CommandTemplateCard(
                                item = item,
                                selected = selectedIds.contains(item.id),
                                onClick = {
                                    if (selectedIds.isEmpty()) onOpenTemplate(item)
                                    else onToggleSelection(item)
                                },
                                onLongClick = { onToggleSelection(item) },
                            )
                        }

                        if (swipeGestures) {
                            val dismissState = rememberSwipeToDismissBoxState(
                                confirmValueChange = { value ->
                                    // The old ItemTouchHelper restored the row and showed the
                                    // confirmation dialog, so refuse the dismissal and ask instead.
                                    if (value == SwipeToDismissBoxValue.EndToStart) {
                                        onDeleteTemplate(item)
                                    }
                                    false
                                },
                            )
                            SwipeToDismissBox(
                                state = dismissState,
                                enableDismissFromStartToEnd = false,
                                backgroundContent = { SwipeDeleteBackground() },
                            ) {
                                card()
                            }
                        } else {
                            card()
                        }
                    }
                }

                // `no_results` was only shown when the table had no rows at all, not per filter.
                if (isTableEmpty) {
                    EmptyState(
                        title = stringResource(R.string.no_results),
                        iconRes = R.drawable.ic_no_results,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            }
        }
    }

    if (showSortSheet) {
        SortTemplatesSheet(
            sortType = sortType,
            sortOrder = sortOrder,
            onSortTypeSelected = onSortTypeSelected,
            onDismiss = { showSortSheet = false },
        )
    }
}

@Composable
private fun TemplatesOverflowMenu(
    onImport: () -> Unit,
    onExport: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                painter = painterResource(R.drawable.baseline_more_vert_24),
                contentDescription = null,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.import_from_clipboard)) },
                onClick = {
                    expanded = false
                    onImport()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.export_from_clipboard)) },
                onClick = {
                    expanded = false
                    onExport()
                },
            )
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    onSearch: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 10.dp, end = 10.dp, top = 4.dp)
            .focusRequester(focusRequester),
        placeholder = { Text(stringResource(R.string.search_command_hint)) },
        leadingIcon = {
            Icon(
                painter = painterResource(R.drawable.ic_search),
                contentDescription = null,
            )
        },
        trailingIcon = {
            IconButton(onClick = onClose) {
                Icon(
                    painter = painterResource(R.drawable.baseline_close_24),
                    contentDescription = stringResource(R.string.cancel),
                )
            }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
    )
}

@Composable
private fun TemplatesChipRow(
    sortType: CommandTemplateSortType,
    sortOrder: SORTING,
    onNewTemplate: () -> Unit,
    onOpenShortcuts: () -> Unit,
    onSortClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AssistChip(
            onClick = onNewTemplate,
            label = { Text(stringResource(R.string.new_template)) },
            leadingIcon = {
                Icon(
                    painter = painterResource(R.drawable.ic_plus),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            },
        )
        AssistChip(
            onClick = onOpenShortcuts,
            label = { Text(stringResource(R.string.shortcuts)) },
            leadingIcon = {
                Icon(
                    painter = painterResource(R.drawable.ic_shortcut),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            },
        )

        // `Divider.Vertical`: 1 dp light grey, 10 dp top/bottom margins.
        Box(
            modifier = Modifier
                .padding(horizontal = 5.dp)
                .width(1.dp)
                .height(12.dp)
                .background(Color(0xFFA6A6A6)),
        )

        AssistChip(
            onClick = onSortClick,
            label = { Text(sortTypeLabel(sortType)) },
            leadingIcon = {
                Icon(
                    painter = painterResource(sortOrderIcon(sortOrder)),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            },
        )
    }
}

@Composable
private fun sortTypeLabel(sortType: CommandTemplateSortType): String = when (sortType) {
    CommandTemplateSortType.DATE -> stringResource(R.string.date_added)
    CommandTemplateSortType.TITLE -> stringResource(R.string.title)
    CommandTemplateSortType.LENGTH -> stringResource(R.string.length)
}

private fun sortOrderIcon(sortOrder: SORTING): Int = when (sortOrder) {
    SORTING.ASC -> R.drawable.ic_up
    SORTING.DESC -> R.drawable.ic_down
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SortTemplatesSheet(
    sortType: CommandTemplateSortType,
    sortOrder: SORTING,
    onSortTypeSelected: (CommandTemplateSortType) -> Unit,
    onDismiss: () -> Unit,
) {
    YtdlBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.sort_by),
                modifier = Modifier.padding(20.dp),
                fontSize = 12.sp,
            )
            SortRow(
                text = stringResource(R.string.date_added),
                selected = sortType == CommandTemplateSortType.DATE,
                sortOrder = sortOrder,
                onClick = { onSortTypeSelected(CommandTemplateSortType.DATE) },
            )
            SortRow(
                text = stringResource(R.string.title),
                selected = sortType == CommandTemplateSortType.TITLE,
                sortOrder = sortOrder,
                onClick = { onSortTypeSelected(CommandTemplateSortType.TITLE) },
            )
            SortRow(
                text = stringResource(R.string.length),
                selected = sortType == CommandTemplateSortType.LENGTH,
                sortOrder = sortOrder,
                onClick = { onSortTypeSelected(CommandTemplateSortType.LENGTH) },
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SortRow(
    text: String,
    selected: Boolean,
    sortOrder: SORTING,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selected) {
            Icon(
                painter = painterResource(sortOrderIcon(sortOrder)),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(30.dp))
        }
        Text(text = text, fontSize = 15.sp)
    }
}

/**
 * 1:1 with `command_template_item.xml`: transparent 5 dp card, 15 dp content padding, 15 sp bold
 * title, 12 sp three-line command preview and a wrapping badge row.
 */
@Composable
private fun CommandTemplateCard(
    item: CommandTemplate,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    // Preserves the old `View.popup()` bind animation (0.75 → 1, 300 ms, ease-out quint).
    var appeared by remember(item.id) { mutableStateOf(false) }
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (appeared) 1f else 0.75f,
        animationSpec = androidx.compose.animation.core.tween(
            durationMillis = 300,
            easing = androidx.compose.animation.core.CubicBezierEasing(0.22f, 1f, 0.36f, 1f),
        ),
        label = "commandTemplatePop",
    )
    LaunchedEffect(item.id) { appeared = true }

    val shape = RoundedCornerShape(5.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .then(
                if (selected) {
                    Modifier.border(5.dp, MaterialTheme.colorScheme.primary, shape)
                } else {
                    Modifier
                }
            )
            .padding(15.dp),
    ) {
        Text(
            text = item.title,
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = 10.dp),
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
        )

        Text(
            text = item.content,
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = 15.dp),
            fontSize = 12.sp,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )

        TemplatesBadgeRow(item = item)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TemplatesBadgeRow(item: CommandTemplate) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
    ) {
        if (item.preferredCommandTemplate) {
            TemplateBadge(
                text = stringResource(R.string.preferred_command_template),
                container = MaterialTheme.colorScheme.primaryContainer,
                content = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        if (item.useAsExtraCommand) {
            val extraAudio = if (item.useAsExtraCommandAudio) stringResource(R.string.audio) else null
            val extraVideo = if (item.useAsExtraCommandVideo) stringResource(R.string.video) else null
            val text = stringResource(R.string.extra_command) + " " + listOfNotNull(
                extraAudio,
                extraVideo,
            ).joinToString(" ", "[", "]", -1)
            TemplateBadge(
                text = text,
                container = MaterialTheme.colorScheme.secondaryContainer,
                content = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
        if (item.useAsExtraCommandDataFetching) {
            TemplateBadge(
                text = stringResource(R.string.data_fetching_extra_command),
                container = MaterialTheme.colorScheme.tertiaryContainer,
                content = MaterialTheme.colorScheme.onTertiaryContainer,
            )
        }
        if (item.urlRegex.isNotEmpty()) {
            TemplateBadge(
                text = "URL Regex: ${item.urlRegex.joinToString(", ")}",
                container = MaterialTheme.colorScheme.tertiaryContainer,
                content = MaterialTheme.colorScheme.onTertiaryContainer,
            )
        }
    }
}

/** `rounded_corner.xml` + the FAB text styles: 5 dp corners, 12 sp bold, 30 dp min width. */
@Composable
private fun TemplateBadge(text: String, container: Color, content: Color) {
    Box(
        modifier = Modifier
            .defaultMinSize(minWidth = 30.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(container)
            .padding(horizontal = 5.dp, vertical = 1.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = content,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** Red swipe background with the delete icon, matching the old `RecyclerViewSwipeDecorator`. */
@Composable
private fun SwipeDeleteBackground() {
    Box(
        modifier = Modifier
            .fillMaxSize()
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
