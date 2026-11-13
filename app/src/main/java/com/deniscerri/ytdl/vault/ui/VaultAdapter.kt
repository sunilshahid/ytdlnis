package com.deniscerri.ytdl.vault.ui

import android.annotation.SuppressLint
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.vault.VaultUiItem
import com.google.android.material.card.MaterialCardView
import java.util.Locale

class VaultAdapter(
    private val onItemClick: (VaultUiItem) -> Unit,
    private val onItemLongClick: (VaultUiItem) -> Unit,
    private val onMoreClick: (anchor: View, item: VaultUiItem) -> Unit
) : ListAdapter<VaultUiItem, VaultAdapter.VaultViewHolder>(DIFF_CALLBACK) {

    var isGridMode: Boolean = true
    var visibleFields: Set<com.deniscerri.ytdl.vault.VaultField> =
        com.deniscerri.ytdl.vault.VaultSettings.DEFAULT_VISIBLE_FIELDS
        @SuppressLint("NotifyDataSetChanged")
        set(value) {
            if (field != value) {
                field = value
                lastAnimatedPosition = -1
                notifyDataSetChanged()
            }
        }

    private var lastAnimatedPosition = -1
    private var selectedIds: Set<String> = emptySet()
    private var selectionMode: Boolean = false

    @SuppressLint("NotifyDataSetChanged")
    fun updateSelection(ids: Set<String>, inSelectionMode: Boolean) {
        if (selectedIds == ids && selectionMode == inSelectionMode) return
        selectedIds = ids
        selectionMode = inSelectionMode
        notifyDataSetChanged()
    }

    fun resetEntranceAnimations() {
        lastAnimatedPosition = -1
    }

    override fun getItemViewType(position: Int): Int =
        if (isGridMode) VIEW_TYPE_GRID else VIEW_TYPE_LIST

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VaultViewHolder {
        val layout = if (viewType == VIEW_TYPE_GRID) {
            R.layout.item_vault_video_grid
        } else {
            R.layout.item_vault_video_list
        }
        val view = LayoutInflater.from(parent.context).inflate(layout, parent, false)
        return VaultViewHolder(view)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onBindViewHolder(holder: VaultViewHolder, position: Int) {
        holder.bind(getItem(position))
        val adapterPos = holder.bindingAdapterPosition
        if (adapterPos > lastAnimatedPosition && adapterPos != RecyclerView.NO_POSITION) {
            lastAnimatedPosition = adapterPos
            holder.itemView.alpha = 0f
            holder.itemView.translationY = 18f * holder.itemView.resources.displayMetrics.density
            holder.itemView.scaleX = 0.96f
            holder.itemView.scaleY = 0.96f
            val delay = (adapterPos.coerceAtMost(8) * 28L)
            holder.itemView.animate()
                .alpha(1f)
                .translationY(0f)
                .scaleX(1f)
                .scaleY(1f)
                .setStartDelay(delay)
                .setDuration(230L)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }
    }

    override fun onViewDetachedFromWindow(holder: VaultViewHolder) {
        holder.itemView.animate().cancel()
        holder.itemView.alpha = 1f
        holder.itemView.translationY = 0f
        holder.itemView.scaleX = 1f
        holder.itemView.scaleY = 1f
        super.onViewDetachedFromWindow(holder)
    }

    inner class VaultViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cardView: MaterialCardView = itemView.findViewById(R.id.vault_item_card)
        private val thumbView: ImageView = itemView.findViewById(R.id.vault_item_thumb)
        private val selectedScrim: View = itemView.findViewById(R.id.vault_item_selected_scrim)
        private val selectedBadge: ImageView = itemView.findViewById(R.id.vault_item_selected_badge)
        private val durationView: TextView = itemView.findViewById(R.id.vault_item_duration)
        private val titleView: TextView = itemView.findViewById(R.id.vault_item_title)
        private val subtitleView: TextView = itemView.findViewById(R.id.vault_item_subtitle)
        private val moreBtn: ImageButton = itemView.findViewById(R.id.vault_item_more)

        fun bind(item: VaultUiItem) {
            val meta = item.meta
            val isSelected = meta.id in selectedIds
            titleView.text = meta.title
            durationView.text = formatDuration(meta.durationMs)
            durationView.visibility = if (meta.durationMs > 0L) View.VISIBLE else View.GONE

            val parts = mutableListOf<String>()
            if (com.deniscerri.ytdl.vault.VaultField.RESOLUTION in visibleFields &&
                meta.width > 0 && meta.height > 0
            ) {
                parts.add("${meta.width}×${meta.height}")
            }
            if (com.deniscerri.ytdl.vault.VaultField.SIZE in visibleFields &&
                meta.filesizeBytes > 0L
            ) {
                parts.add(formatFileSize(meta.filesizeBytes))
            }
            if (com.deniscerri.ytdl.vault.VaultField.DURATION in visibleFields &&
                meta.durationMs > 0L
            ) {
                parts.add(formatDuration(meta.durationMs))
            }
            if (com.deniscerri.ytdl.vault.VaultField.DATE in visibleFields &&
                meta.dateAddedMs > 0L
            ) {
                parts.add(
                    java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM)
                        .format(java.util.Date(meta.dateAddedMs))
                )
            }
            if (com.deniscerri.ytdl.vault.VaultField.AUTHOR in visibleFields &&
                meta.author.isNotBlank()
            ) {
                parts.add(meta.author)
            }
            if (com.deniscerri.ytdl.vault.VaultField.WEBSITE in visibleFields &&
                meta.website.isNotBlank()
            ) {
                parts.add(meta.website)
            }
            subtitleView.text = parts.joinToString(" · ")
            subtitleView.visibility = if (parts.isEmpty()) View.GONE else View.VISIBLE

            val bmp = item.thumbnail
            if (bmp != null && !bmp.isRecycled) {
                thumbView.setImageBitmap(bmp)
                thumbView.scaleType = ImageView.ScaleType.CENTER_CROP
            } else {
                thumbView.setImageResource(R.drawable.ic_video)
                thumbView.scaleType = ImageView.ScaleType.CENTER
            }

            val strokePx = if (isSelected) {
                (2.5f * itemView.resources.displayMetrics.density).toInt()
            } else {
                0
            }
            cardView.strokeWidth = strokePx
            selectedScrim.visibility = if (isSelected) View.VISIBLE else View.GONE
            selectedBadge.visibility = if (isSelected) View.VISIBLE else View.GONE
            moreBtn.visibility = if (selectionMode) View.GONE else View.VISIBLE

            itemView.setOnTouchListener { v, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN ->
                        v.animate().scaleX(0.97f).scaleY(0.97f).setDuration(90L).start()
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                        v.animate().scaleX(1f).scaleY(1f).setDuration(140L)
                            .setInterpolator(DecelerateInterpolator()).start()
                }
                false
            }
            itemView.setOnClickListener { onItemClick(item) }
            itemView.setOnLongClickListener { view ->
                runCatching { view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS) }
                onItemLongClick(item)
                true
            }
            moreBtn.setOnClickListener { onMoreClick(it, item) }
        }
    }

    companion object {
        private const val VIEW_TYPE_GRID = 1
        private const val VIEW_TYPE_LIST = 2

        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<VaultUiItem>() {
            override fun areItemsTheSame(oldItem: VaultUiItem, newItem: VaultUiItem): Boolean =
                oldItem.meta.id == newItem.meta.id

            override fun areContentsTheSame(oldItem: VaultUiItem, newItem: VaultUiItem): Boolean =
                oldItem.meta == newItem.meta && oldItem.thumbnail === newItem.thumbnail
        }

        private fun formatDuration(durationMs: Long): String {
            if (durationMs <= 0L) return ""
            val totalSeconds = durationMs / 1000L
            val h = totalSeconds / 3600L
            val m = (totalSeconds % 3600L) / 60L
            val s = totalSeconds % 60L
            return if (h > 0L) {
                String.format(Locale.US, "%d:%02d:%02d", h, m, s)
            } else {
                String.format(Locale.US, "%02d:%02d", m, s)
            }
        }

        private fun formatFileSize(bytes: Long): String {
            if (bytes < 1024L) return "$bytes B"
            val kb = bytes / 1024.0
            if (kb < 1024.0) return String.format(Locale.US, "%.0f KB", kb)
            val mb = kb / 1024.0
            if (mb < 1024.0) return String.format(Locale.US, "%.1f MB", mb)
            val gb = mb / 1024.0
            return String.format(Locale.US, "%.2f GB", gb)
        }
    }
}
