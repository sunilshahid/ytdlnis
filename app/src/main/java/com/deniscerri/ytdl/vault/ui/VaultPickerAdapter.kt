package com.deniscerri.ytdl.vault.ui

import android.annotation.SuppressLint
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.ui.play.device.DeviceMedia
import com.deniscerri.ytdl.ui.play.device.DeviceThumbnailLoader
import com.deniscerri.ytdl.util.Extensions.toStringTimeStamp
import com.deniscerri.ytdl.vault.VaultPickerField
import com.deniscerri.ytdl.vault.VaultSettings
import com.google.android.material.card.MaterialCardView
import java.text.DateFormat
import java.util.Date

/**
 * Multi-select adapter for Vault's in-app device video picker (`+` FAB).
 * Reuses `item_vault_video_grid` / `item_vault_video_list` and [DeviceThumbnailLoader] so
 * thumbnails and selection styling match the Vault gallery while hiding item overflow menus.
 */
class VaultPickerAdapter(
    private val thumbnails: DeviceThumbnailLoader,
    private val onToggleItem: (DeviceMedia) -> Unit
) : ListAdapter<DeviceMedia, VaultPickerAdapter.PickerHolder>(DIFF) {

    var isGridMode: Boolean = true
    var visibleFields: Set<VaultPickerField> = VaultSettings.DEFAULT_PICKER_VISIBLE_FIELDS
    private var selectedIds: Set<String> = emptySet()

    @SuppressLint("NotifyDataSetChanged")
    fun updateSelection(ids: Set<String>) {
        if (selectedIds == ids) return
        selectedIds = ids
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int =
        if (isGridMode) TYPE_GRID else TYPE_LIST

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PickerHolder {
        val layoutRes = if (viewType == TYPE_GRID) {
            R.layout.item_vault_video_grid
        } else {
            R.layout.item_vault_video_list
        }
        val view = LayoutInflater.from(parent.context).inflate(layoutRes, parent, false)
        return PickerHolder(view)
    }

    override fun onBindViewHolder(holder: PickerHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class PickerHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cardView: MaterialCardView = itemView.findViewById(R.id.vault_item_card)
        private val thumbView: ImageView = itemView.findViewById(R.id.vault_item_thumb)
        private val selectedScrim: View = itemView.findViewById(R.id.vault_item_selected_scrim)
        private val selectedBadge: ImageView = itemView.findViewById(R.id.vault_item_selected_badge)
        private val durationView: TextView = itemView.findViewById(R.id.vault_item_duration)
        private val titleView: TextView = itemView.findViewById(R.id.vault_item_title)
        private val subtitleView: TextView = itemView.findViewById(R.id.vault_item_subtitle)
        private val moreBtn: ImageButton = itemView.findViewById(R.id.vault_item_more)

        fun bind(item: DeviceMedia) {
            val context = itemView.context
            val isSelected = item.mediaId in selectedIds

            titleView.text = item.title
            val durationText = item.durationMs.takeIf { it > 0L }?.toStringTimeStamp().orEmpty()
            durationView.text = durationText
            durationView.visibility = if (durationText.isNotBlank()) View.VISIBLE else View.GONE

            val parts = buildList {
                if (VaultPickerField.PATH in visibleFields) {
                    item.folderName.takeIf { it.isNotBlank() }?.let(::add)
                }
                if (VaultPickerField.RESOLUTION in visibleFields && item.width > 0 && item.height > 0) {
                    add("${item.width}×${item.height}")
                }
                if (VaultPickerField.SIZE in visibleFields && item.sizeBytes > 0L) {
                    add(Formatter.formatShortFileSize(context, item.sizeBytes))
                }
                if (VaultPickerField.DURATION in visibleFields && durationText.isNotBlank()) {
                    add(durationText)
                }
                if (VaultPickerField.DATE in visibleFields && item.dateAddedMs > 0L) {
                    add(DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(item.dateAddedMs)))
                }
            }
            subtitleView.text = parts.joinToString(" · ")
            subtitleView.visibility = if (subtitleView.text.isNotBlank()) View.VISIBLE else View.GONE

            thumbView.scaleType = ImageView.ScaleType.CENTER_CROP
            thumbView.imageTintList = null
            thumbnails.load(thumbView, item)

            moreBtn.visibility = View.GONE
            val strokePx = if (isSelected) {
                (2.5f * itemView.resources.displayMetrics.density).toInt()
            } else {
                0
            }
            cardView.strokeWidth = strokePx
            selectedScrim.visibility = if (isSelected) View.VISIBLE else View.GONE
            selectedBadge.visibility = if (isSelected) View.VISIBLE else View.GONE

            itemView.setOnClickListener { onToggleItem(item) }
        }
    }

    private companion object {
        const val TYPE_GRID = 0
        const val TYPE_LIST = 1

        val DIFF = object : DiffUtil.ItemCallback<DeviceMedia>() {
            override fun areItemsTheSame(oldItem: DeviceMedia, newItem: DeviceMedia): Boolean =
                oldItem.mediaId == newItem.mediaId

            override fun areContentsTheSame(oldItem: DeviceMedia, newItem: DeviceMedia): Boolean =
                oldItem == newItem
        }
    }
}
