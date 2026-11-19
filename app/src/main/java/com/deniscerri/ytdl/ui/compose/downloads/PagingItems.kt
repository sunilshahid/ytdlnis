package com.deniscerri.ytdl.ui.compose.downloads

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.paging.AsyncPagingDataDiffer
import androidx.paging.CombinedLoadStates
import androidx.paging.ItemSnapshotList
import androidx.paging.PagingData
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListUpdateCallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest

/**
 * Lightweight Paging3 Compose bridge without requiring external `paging-compose` artifact.
 * Wraps [AsyncPagingDataDiffer] from `androidx.paging:paging-runtime`.
 */
class PagingItems<T : Any>(
    val differ: AsyncPagingDataDiffer<T>
) {
    var snapshot by mutableStateOf<ItemSnapshotList<T>>(differ.snapshot())
        internal set

    var itemCount by mutableIntStateOf(differ.itemCount)
        internal set

    var loadState by mutableStateOf<CombinedLoadStates?>(null)
        internal set

    operator fun get(index: Int): T? {
        if (index in 0 until differ.itemCount) {
            differ.getItem(index)
        }
        return snapshot.getOrNull(index)
    }

    val items: List<T> get() = snapshot.items

    fun refresh() {
        differ.refresh()
    }

    fun retry() {
        differ.retry()
    }
}

@Composable
fun <T : Any> Flow<PagingData<T>>.collectAsPagingItems(
    diffCallback: DiffUtil.ItemCallback<T>
): PagingItems<T> {
    val pagingItems = remember(this, diffCallback) {
        var holder: PagingItems<T>? = null
        val updateCallback = object : ListUpdateCallback {
            override fun onInserted(position: Int, count: Int) {
                holder?.let {
                    it.snapshot = it.differ.snapshot()
                    it.itemCount = it.differ.itemCount
                }
            }

            override fun onRemoved(position: Int, count: Int) {
                holder?.let {
                    it.snapshot = it.differ.snapshot()
                    it.itemCount = it.differ.itemCount
                }
            }

            override fun onMoved(fromPosition: Int, toPosition: Int) {
                holder?.let {
                    it.snapshot = it.differ.snapshot()
                    it.itemCount = it.differ.itemCount
                }
            }

            override fun onChanged(position: Int, count: Int, payload: Any?) {
                holder?.let {
                    it.snapshot = it.differ.snapshot()
                    it.itemCount = it.differ.itemCount
                }
            }
        }

        val differ = AsyncPagingDataDiffer(
            diffCallback = diffCallback,
            updateCallback = updateCallback,
            mainDispatcher = Dispatchers.Main
        )

        PagingItems(differ).also { created ->
            holder = created
            differ.addLoadStateListener { states ->
                created.loadState = states
                created.snapshot = differ.snapshot()
                created.itemCount = differ.itemCount
            }
        }
    }

    LaunchedEffect(this, pagingItems) {
        collectLatest { pagingData ->
            pagingItems.differ.submitData(pagingData)
        }
    }

    return pagingItems
}
