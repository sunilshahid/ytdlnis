package com.deniscerri.ytdl.ui.play.device

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.playback.model.PlaybackType
import com.deniscerri.ytdl.util.Extensions.toStringTimeStamp
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors

/**
 * Adapter for the on-device media tab. It intentionally uses the same row layout as the Play
 * library for list mode, so both surfaces behave and animate consistently.
 */
class DeviceFilesAdapter(
    private val thumbnails: DeviceThumbnailLoader,
    private val onFileClick: (DeviceMedia, List<DeviceMedia>) -> Unit,
    private val onFileMenu: (View, DeviceMedia) -> Unit,
    private val onFolderClick: (DeviceFolder) -> Unit,
    private val onFileLongClick: ((DeviceMedia) -> Unit)? = null
) : ListAdapter<DeviceRow, RecyclerView.ViewHolder>(DeviceRowDiff) {

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long {
        if (position !in 0 until itemCount) return RecyclerView.NO_ID
        return getItem(position).stableId.hashCode().toLong()
    }

    var grid: Boolean = false
        private set

    private var selectedMediaIds: Set<String> = emptySet()
    private var selectionMode: Boolean = false

    @SuppressLint("NotifyDataSetChanged")
    fun updateSelection(ids: Set<String>, inSelectionMode: Boolean) {
        if (selectedMediaIds == ids && selectionMode == inSelectionMode) return
        selectedMediaIds = ids
        selectionMode = inSelectionMode
        notifyDataSetChanged()
    }

    fun setGrid(value: Boolean) {
        if (grid == value) return
        grid = value
        notifyDataSetChanged()
    }

    fun visibleFiles(): List<DeviceMedia> = currentList
        .filterIsInstance<DeviceRow.File>()
        .map { it.media }

    fun spanSize(position: Int): Int {
        if (position !in 0 until itemCount) return 1
        return when (getItem(position)) {
            is DeviceRow.Header, is DeviceRow.Message -> 2
            else -> 1
        }
    }

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is DeviceRow.Header -> TYPE_HEADER
        is DeviceRow.File -> if (grid) TYPE_FILE_GRID else TYPE_FILE_LIST
        is DeviceRow.Folder -> if (grid) TYPE_FOLDER_GRID else TYPE_FOLDER_LIST
        is DeviceRow.Message -> TYPE_MESSAGE
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_HEADER -> HeaderHolder(inflater.inflate(R.layout.play_section_header, parent, false))
            TYPE_FILE_LIST, TYPE_FOLDER_LIST ->
                ListHolder(inflater.inflate(R.layout.play_track_row, parent, false))
            TYPE_FILE_GRID, TYPE_FOLDER_GRID ->
                GridHolder(inflater.inflate(R.layout.device_grid_item, parent, false))
            else -> MessageHolder(inflater.inflate(R.layout.play_empty_row, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = getItem(position)) {
            is DeviceRow.Header -> (holder as HeaderHolder).bind(row)
            is DeviceRow.File -> {
                if (holder is GridHolder) holder.bindFile(row) else (holder as ListHolder).bindFile(row)
            }
            is DeviceRow.Folder -> {
                if (holder is GridHolder) holder.bindFolder(row.folder) else
                    (holder as ListHolder).bindFolder(row.folder)
            }
            is DeviceRow.Message -> (holder as MessageHolder).bind(row)
        }
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        if (payloads.contains(PAYLOAD_PLAYING_STATE)) {
            val row = getItem(position) as? DeviceRow.File
            if (row != null) {
                when (holder) {
                    is ListHolder -> holder.updatePlayingState(row)
                    is GridHolder -> holder.updatePlayingState(row)
                }
                return
            }
        }
        super.onBindViewHolder(holder, position, payloads)
    }

    private inner class ListHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val root: View = view.findViewById(R.id.play_track_root)
        private val art: ImageView = view.findViewById(R.id.play_row_art)
        private val selectedScrim: View? = view.findViewById(R.id.play_row_selected_scrim)
        private val selectedBadge: ImageView? = view.findViewById(R.id.play_row_selected_badge)
        private val typeBadge: ImageView = view.findViewById(R.id.play_row_type_badge)
        private val title: TextView = view.findViewById(R.id.play_row_title)
        private val subtitle: TextView = view.findViewById(R.id.play_row_subtitle)
        private val duration: TextView = view.findViewById(R.id.play_row_duration)
        private val equalizer: ImageView = view.findViewById(R.id.play_row_equalizer)
        private val menu: ImageButton = view.findViewById(R.id.play_row_menu)
        private val colorOnSurface = MaterialColors.getColor(view, com.google.android.material.R.attr.colorOnSurface)
        private val colorPrimary = MaterialColors.getColor(view, com.google.android.material.R.attr.colorPrimary)
        private val colorMuted = MaterialColors.getColor(
            view,
            com.google.android.material.R.attr.colorOnSurfaceVariant
        )

        fun bindFile(row: DeviceRow.File) {
            val context = itemView.context
            val media = row.media
            val isSelected = selectionMode && media.mediaId in selectedMediaIds
            title.text = media.title
            title.setTextColor(if (row.isCurrent || isSelected) colorPrimary else colorOnSurface)
            subtitle.text = media.detailParts(context, row.fields, row.status, row.lastPlayedAt)
                .joinToString(" · ")
                .ifBlank { media.folderName }
            subtitle.isVisible = subtitle.text.isNotBlank()
            duration.text = media.durationMs.takeIf { it > 0L }?.toStringTimeStamp().orEmpty()
            duration.isVisible = !duration.text.isNullOrBlank()
            equalizer.isVisible = row.isCurrent && !selectionMode
            menu.isVisible = !selectionMode
            selectedScrim?.isVisible = isSelected
            selectedBadge?.isVisible = isSelected
            art.scaleType = ImageView.ScaleType.CENTER_CROP
            art.imageTintList = null
            thumbnails.load(art, media)
            typeBadge.isVisible = media.type == PlaybackType.VIDEO
            typeBadge.setImageResource(R.drawable.ic_video)
            setActiveBackground(root, row.isCurrent || isSelected)
            itemView.applyTactilePressAnimation()

            itemView.setOnClickListener {
                onFileClick(media, visibleFiles())
            }
            itemView.setOnLongClickListener { view ->
                val handler = onFileLongClick
                if (handler != null) {
                    runCatching { view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS) }
                    handler(media)
                } else {
                    onFileMenu(view, media)
                }
                true
            }
            menu.setOnClickListener { onFileMenu(menu, media) }
        }

        fun updatePlayingState(row: DeviceRow.File) {
            val isSelected = selectionMode && row.media.mediaId in selectedMediaIds
            title.setTextColor(if (row.isCurrent || isSelected) colorPrimary else colorOnSurface)
            equalizer.isVisible = row.isCurrent && !selectionMode
            setActiveBackground(root, row.isCurrent || isSelected)
        }

        fun bindFolder(folder: DeviceFolder) {
            val context = itemView.context
            title.text = folder.label(context)
            title.setTextColor(colorOnSurface)
            subtitle.text = folder.detail(context)
            subtitle.isVisible = true
            duration.text = ""
            duration.isVisible = false
            equalizer.isVisible = false
            menu.isVisible = false
            selectedScrim?.isVisible = false
            selectedBadge?.isVisible = false
            typeBadge.isVisible = false
            art.setImageResource(R.drawable.baseline_folder_24)
            art.imageTintList = ColorStateList.valueOf(colorPrimary)
            art.scaleType = ImageView.ScaleType.CENTER_INSIDE
            art.tag = "folder:${folder.path}"
            setActiveBackground(root, false)
            itemView.setOnClickListener {
                if (!selectionMode) onFolderClick(folder)
            }
            itemView.setOnLongClickListener(null)
        }

        private fun setActiveBackground(target: View, active: Boolean) {
            if (active) {
                target.setBackgroundResource(R.drawable.bg_active_track_row)
            } else {
                val outValue = android.util.TypedValue()
                itemView.context.theme.resolveAttribute(
                    android.R.attr.selectableItemBackground,
                    outValue,
                    true
                )
                target.setBackgroundResource(outValue.resourceId)
            }
        }
    }

    private inner class GridHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val card: MaterialCardView = view.findViewById(R.id.device_grid_root)
        private val art: ImageView = view.findViewById(R.id.device_grid_art)
        private val playing: ImageView = view.findViewById(R.id.device_grid_playing)
        private val selectedScrim: View? = view.findViewById(R.id.device_grid_selected_scrim)
        private val selectedBadge: ImageView? = view.findViewById(R.id.device_grid_selected_badge)
        private val typeBadge: ImageView = view.findViewById(R.id.device_grid_type_badge)
        private val title: TextView = view.findViewById(R.id.device_grid_title)
        private val subtitle: TextView = view.findViewById(R.id.device_grid_subtitle)
        private val menu: ImageButton = view.findViewById(R.id.device_grid_menu)
        private val colorPrimary = MaterialColors.getColor(view, com.google.android.material.R.attr.colorPrimary)
        private val colorOutline = MaterialColors.getColor(
            view,
            com.google.android.material.R.attr.colorOutlineVariant,
            Color.DKGRAY
        )

        fun bindFile(row: DeviceRow.File) {
            val context = itemView.context
            val media = row.media
            val isSelected = selectionMode && media.mediaId in selectedMediaIds
            title.text = media.title
            subtitle.text = media.detailParts(context, row.fields, row.status, row.lastPlayedAt)
                .joinToString(" · ")
                .ifBlank { media.folderName }
            subtitle.isVisible = subtitle.text.isNotBlank()
            playing.isVisible = row.isCurrent
            menu.isVisible = !selectionMode
            selectedScrim?.isVisible = isSelected
            selectedBadge?.isVisible = isSelected
            typeBadge.isVisible = media.type == PlaybackType.VIDEO
            typeBadge.setImageResource(R.drawable.ic_video)
            art.scaleType = ImageView.ScaleType.CENTER_CROP
            art.imageTintList = null
            thumbnails.load(art, media)
            setActiveCard(row.isCurrent || isSelected)
            itemView.applyTactilePressAnimation()
            itemView.setOnClickListener { onFileClick(media, visibleFiles()) }
            itemView.setOnLongClickListener { view ->
                val handler = onFileLongClick
                if (handler != null) {
                    runCatching { view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS) }
                    handler(media)
                } else {
                    onFileMenu(view, media)
                }
                true
            }
            menu.setOnClickListener { onFileMenu(menu, media) }
        }

        fun updatePlayingState(row: DeviceRow.File) {
            val isSelected = selectionMode && row.media.mediaId in selectedMediaIds
            playing.isVisible = row.isCurrent
            setActiveCard(row.isCurrent || isSelected)
        }

        fun bindFolder(folder: DeviceFolder) {
            val context = itemView.context
            title.text = folder.label(context)
            subtitle.text = folder.detail(context)
            subtitle.isVisible = true
            playing.isVisible = false
            menu.isVisible = false
            selectedScrim?.isVisible = false
            selectedBadge?.isVisible = false
            typeBadge.isVisible = false
            art.setImageResource(R.drawable.baseline_folder_24)
            art.imageTintList = ColorStateList.valueOf(colorPrimary)
            art.scaleType = ImageView.ScaleType.CENTER_INSIDE
            art.tag = "folder:${folder.path}"
            setActiveCard(false)
            itemView.setOnClickListener {
                if (!selectionMode) onFolderClick(folder)
            }
            itemView.setOnLongClickListener(null)
        }

        private fun setActiveCard(active: Boolean) {
            if (active) {
                card.strokeWidth = dp(2)
                card.strokeColor = colorPrimary
            } else {
                card.strokeWidth = dp(1)
                card.strokeColor = colorOutline
            }
        }

        private fun dp(value: Int): Int =
            (value * itemView.resources.displayMetrics.density).toInt()
    }

    private inner class HeaderHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val title: TextView = view.findViewById(R.id.play_section_title)
        private val count: TextView = view.findViewById(R.id.play_section_count)
        private val badge: View? = view.findViewById(R.id.play_section_badge_card)

        fun bind(row: DeviceRow.Header) {
            title.text = row.title
            count.text = row.count.toString()
            count.isVisible = row.count > 0
            badge?.isVisible = row.count > 0
        }
    }

    private inner class MessageHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val icon: ImageView = view.findViewById(R.id.play_empty_icon)
        private val title: TextView = view.findViewById(R.id.play_empty_title)
        private val subtitle: TextView = view.findViewById(R.id.play_empty_text)
        private val action: MaterialButton = view.findViewById(R.id.play_empty_action)

        fun bind(row: DeviceRow.Message) {
            icon.setImageResource(row.icon)
            title.text = row.title
            subtitle.text = row.subtitle.orEmpty()
            subtitle.isVisible = !row.subtitle.isNullOrBlank()
            val label = row.actionLabel
            val click = row.action
            action.isVisible = label != null && click != null
            if (label != null) action.text = label
            action.setOnClickListener { click?.invoke() }
            itemView.setOnClickListener { click?.invoke() }
        }
    }

    private object DeviceRowDiff : DiffUtil.ItemCallback<DeviceRow>() {
        override fun areItemsTheSame(oldItem: DeviceRow, newItem: DeviceRow): Boolean =
            oldItem.stableId == newItem.stableId

        override fun areContentsTheSame(oldItem: DeviceRow, newItem: DeviceRow): Boolean =
            oldItem == newItem

        override fun getChangePayload(oldItem: DeviceRow, newItem: DeviceRow): Any? {
            if (oldItem is DeviceRow.File && newItem is DeviceRow.File) {
                if (oldItem.media.mediaId == newItem.media.mediaId &&
                    oldItem.isCurrent != newItem.isCurrent &&
                    oldItem.status == newItem.status &&
                    oldItem.fields == newItem.fields
                ) {
                    return PAYLOAD_PLAYING_STATE
                }
            }
            return null
        }
    }

    companion object {
        const val PAYLOAD_PLAYING_STATE = "payload_playing_state"
        private const val TYPE_HEADER = 0
        private const val TYPE_FILE_LIST = 1
        private const val TYPE_FILE_GRID = 2
        private const val TYPE_FOLDER_LIST = 3
        private const val TYPE_FOLDER_GRID = 4
        private const val TYPE_MESSAGE = 5
    }
}

@SuppressLint("ClickableViewAccessibility")
private fun View.applyTactilePressAnimation() {
    setOnTouchListener { v, event ->
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                v.animate()
                    .scaleX(0.97f)
                    .scaleY(0.97f)
                    .setDuration(90)
                    .setInterpolator(DecelerateInterpolator())
                    .start()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                v.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setDuration(130)
                    .setInterpolator(OvershootInterpolator(1.1f))
                    .start()
            }
        }
        false
    }
}
