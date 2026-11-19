package com.deniscerri.ytdl.ui.downloadcard

import android.app.Activity
import android.content.DialogInterface
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.deniscerri.ytdl.database.models.AlreadyExistsItem
import com.deniscerri.ytdl.database.models.DownloadItem
import com.deniscerri.ytdl.database.viewmodel.DownloadViewModel
import com.deniscerri.ytdl.database.viewmodel.DownloadViewModel.AlreadyExistsIDs
import com.deniscerri.ytdl.database.viewmodel.HistoryViewModel
import com.deniscerri.ytdl.database.viewmodel.ResultViewModel
import com.deniscerri.ytdl.ui.compose.downloadcard.ConflictResolutionSheet
import com.deniscerri.ytdl.ui.compose.theme.YtdlTheme
import com.deniscerri.ytdl.util.UiUtil
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

class DownloadsAlreadyExistDialog : BottomSheetDialogFragment() {
    private var activity: Activity? = null
    private lateinit var downloadViewModel: DownloadViewModel
    private lateinit var resultViewModel: ResultViewModel
    private lateinit var historyViewModel: HistoryViewModel

    private var duplicateIDs: MutableList<AlreadyExistsIDs> = mutableListOf()
    private var duplicates: MutableList<AlreadyExistsItem> = mutableListOf()
    private lateinit var preferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activity = getActivity()
        downloadViewModel = ViewModelProvider(requireActivity())[DownloadViewModel::class.java]
        resultViewModel = ViewModelProvider(requireActivity())[ResultViewModel::class.java]
        historyViewModel = ViewModelProvider(requireActivity())[HistoryViewModel::class.java]
        preferences = PreferenceManager.getDefaultSharedPreferences(requireContext())

        kotlin.runCatching {
            duplicateIDs = if (Build.VERSION.SDK_INT >= 33) {
                arguments?.getParcelableArrayList("duplicates", AlreadyExistsIDs::class.java)!!.toMutableList()
            } else {
                arguments?.getParcelableArrayList<AlreadyExistsIDs>("duplicates")!!.toMutableList()
            }

            if (duplicateIDs.isEmpty()) {
                dismiss()
            }
        }.onFailure {
            dismiss()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        runBlocking {
            val items = withContext(Dispatchers.IO) {
                downloadViewModel.getAllByIDs(duplicateIDs.map { it.downloadItemID })
            }
            duplicates = items.map { item ->
                AlreadyExistsItem(item, duplicateIDs.firstOrNull { it.downloadItemID == item.id }?.historyItemID)
            }.toMutableList()
        }

        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                YtdlTheme {
                    ConflictResolutionSheet(
                        conflicts = duplicates,
                        onDownloadAgain = {
                            CoroutineScope(Dispatchers.IO).launch {
                                downloadViewModel.deleteWithDuplicateStatus()
                                val items = duplicates.map { it.downloadItem }
                                items.forEach { it.id = 0 }
                                val result = downloadViewModel.queueDownloads(items, true)
                                if (result.message.isNotBlank()) {
                                    lifecycleScope.launch {
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(requireContext(), result.message, Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }
                                withContext(Dispatchers.Main) {
                                    dismiss()
                                }
                            }
                        },
                        onSkipExisting = {
                            dismiss()
                        },
                        onDismiss = {
                            dismiss()
                        }
                    )
                }
            }
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        CoroutineScope(Dispatchers.IO).launch {
            downloadViewModel.deleteWithDuplicateStatus()
        }
    }
}
