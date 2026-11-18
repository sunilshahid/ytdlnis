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
import androidx.core.graphics.ColorUtils
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.playback.model.PlaybackType
import com.deniscerri.ytdl.util.Extensions.applyTactilePressAnimation
import com.deniscerri.ytdl.util.Extensions.extractAudioFormat
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

    var currentDominantColor: Int? = null
        set(value) {
            if (field != value) {
                field = value
                notifyDataSetChanged()
            }
        }

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
            is DeviceRow.Header, is DeviceRow.Message, is DeviceRow.FolderCarousel -> 2
            else -> 1
        }
    }

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is DeviceRow.Header -> TYPE_HEADER
        is DeviceRow.File -> if (grid) TYPE_FILE_GRID else TYPE_FILE_LIST
        is DeviceRow.Folder -> if (grid) TYPE_FOLDER_GRID else TYPE_FOLDER_LIST
        is DeviceRow.FolderCarousel -> TYPE_FOLDER_CAROUSEL
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
            TYPE_FOLDER_CAROUSEL ->
                CarouselHolder(inflater.inflate(R.layout.device_folder_carousel, parent, false))
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
            is DeviceRow.FolderCarousel -> (holder as CarouselHolder).bind(row)
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
        private val card: MaterialCardView = view.findViewById(R.id.play_track_root)
        private val art: ImageView = view.findViewById(R.id.play_row_art)
        private val selectedScrim: View? = view.findViewById(R.id.play_row_selected_scrim)
        private val selectedBadge: ImageView? = view.findViewById(R.id.play_row_selected_badge)
        private val typeBadge: ImageView = view.findViewById(R.id.play_row_type_badge)
        private val title: TextView = view.findViewById(R.id.play_row_title)
        private val subtitle: TextView = view.findViewById(R.id.play_row_subtitle)
        private val formatBadge: TextView? = view.findViewById(R.id.play_row_format_badge)
        private val duration: TextView = view.findViewById(R.id.play_row_duration)
        private val equalizer: ImageView = view.findViewById(R.id.play_row_equalizer)
        private val menu: ImageButton = view.findViewById(R.id.play_row_menu)
        private val colorOnSurface = MaterialColors.getColor(view, com.google.android.material.R.attr.colorOnSurface)
        private val colorPrimary = MaterialColors.getColor(view, com.google.android.material.R.attr.colorPrimary)
        private val colorSurfaceContainer = MaterialColors.getColor(
            view,
            com.google.android.material.R.attr.colorSurfaceContainer,
            Color.parseColor("#1E232B")
        )
        private val colorSecondaryContainer = MaterialColors.getColor(
            view,
            com.google.android.material.R.attr.colorSecondaryContainer,
            Color.parseColor("#2A3A4A")
        )
        private val colorOutlineVariant = MaterialColors.getColor(
            view,
            com.google.android.material.R.attr.colorOutlineVariant,
            Color.DKGRAY
        )

        fun bindFile(row: DeviceRow.File) {
            val context = itemView.context
            val media = row.media
            val isSelected = selectionMode && media.mediaId in selectedMediaIds
            val isCurrent = row.isCurrent || isSelected
            title.text = media.title
            title.setTextColor(if (isCurrent) colorPrimary else colorOnSurface)
            val format = extractAudioFormat(media.displayName, media.filePath, media.mimeType)
            if (format != null) {
                formatBadge?.text = format
                formatBadge?.isVisible = true
            } else {
                formatBadge?.isVisible = false
            }
            val artistText = media.artist?.takeIf { it.isNotBlank() } ?: media.folderName
            subtitle.text = artistText
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
            setActiveCard(card, isCurrent)
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
            val isCurrent = row.isCurrent || isSelected
            title.setTextColor(if (isCurrent) colorPrimary else colorOnSurface)
            equalizer.isVisible = row.isCurrent && !selectionMode
            setActiveCard(card, isCurrent)
        }

        fun bindFolder(folder: DeviceFolder) {
            val context = itemView.context
            title.text = folder.label(context)
            title.setTextColor(colorOnSurface)
            subtitle.text = folder.detail(context)
            subtitle.isVisible = true
            formatBadge?.isVisible = false
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
            setActiveCard(card, false)
            itemView.applyTactilePressAnimation()
            itemView.setOnClickListener {
                if (!selectionMode) onFolderClick(folder)
            }
            itemView.setOnLongClickListener(null)
        }

        private fun setActiveCard(targetCard: MaterialCardView, active: Boolean) {
            val density = targetCard.resources.displayMetrics.density
            val tint = currentDominantColor
            if (active) {
                val bg = if (tint != null) {
                    ColorUtils.blendARGB(colorSecondaryContainer, tint, 0.28f)
                } else {
                    colorSecondaryContainer
                }
                targetCard.setCardBackgroundColor(bg)
                targetCard.strokeColor = tint ?: colorPrimary
                targetCard.strokeWidth = (2f * density).toInt()
            } else {
                val bg = if (tint != null) {
                    ColorUtils.blendARGB(colorSurfaceContainer, tint, 0.10f)
                } else {
                    colorSurfaceContainer
                }
                targetCard.setCardBackgroundColor(bg)
                val outline = if (tint != null) {
                    ColorUtils.blendARGB(colorOutlineVariant, tint, 0.30f)
                } else {
                    (colorOutlineVariant and 0x00FFFFFF) or 0x33000000
                }
                targetCard.strokeColor = outline
                targetCard.strokeWidth = (1f * density).toInt()
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

    private inner class CarouselHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val recycler: RecyclerView = view.findViewById(R.id.folder_carousel_recycler)

        init {
            recycler.layoutManager = LinearLayoutManager(view.context, LinearLayoutManager.HORIZONTAL, false)
            recycler.setHasFixedSize(true)
        }

        fun bind(row: DeviceRow.FolderCarousel) {
            recycler.adapter = FolderCardAdapter(row.folders, row.selectedPath)
        }
    }

    private inner class FolderCardAdapter(
        private val folders: List<DeviceFolder>,
        private val selectedPath: String?
    ) : RecyclerView.Adapter<FolderCardViewHolder>() {

        override fun getItemCount(): Int = folders.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FolderCardViewHolder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.device_folder_card, parent, false)
            return FolderCardViewHolder(v)
        }

        override fun onBindViewHolder(holder: FolderCardViewHolder, position: Int) {
            holder.bind(folders[position], folders[position].path == selectedPath)
        }
    }

    private inner class FolderCardViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val card: MaterialCardView = view.findViewById(R.id.folder_card_root)
        private val art1: ImageView = view.findViewById(R.id.folder_tile_art1)
        private val art2: ImageView = view.findViewById(R.id.folder_tile_art2)
        private val art3: ImageView = view.findViewById(R.id.folder_tile_art3)
        private val name: TextView = view.findViewById(R.id.folder_card_name)
        private val count: TextView = view.findViewById(R.id.folder_card_count)
        private val colorPrimary = MaterialColors.getColor(view, com.google.android.material.R.attr.colorPrimary)
        private val colorOutline = MaterialColors.getColor(view, com.google.android.material.R.attr.colorOutlineVariant)
        private val colorSurfaceSelected = MaterialColors.getColor(view, com.google.android.material.R.attr.colorSurfaceContainerHighest)
        private val colorSurfaceNormal = MaterialColors.getColor(view, com.google.android.material.R.attr.colorSurfaceContainerHigh)

        fun bind(folder: DeviceFolder, isSelected: Boolean) {
            val context = itemView.context
            name.text = folder.label(context)
            count.text = "(${folder.items.size})"

            val tint = currentDominantColor
            if (isSelected) {
                card.strokeWidth = dp(2)
                card.strokeColor = tint ?: colorPrimary
                val bg = if (tint != null) {
                    ColorUtils.blendARGB(colorSurfaceSelected, tint, 0.28f)
                } else {
                    colorSurfaceSelected
                }
                card.setCardBackgroundColor(bg)
            } else {
                card.strokeWidth = dp(1)
                card.strokeColor = if (tint != null) {
                    ColorUtils.blendARGB(colorOutline, tint, 0.35f)
                } else {
                    colorOutline
                }
                val bg = if (tint != null) {
                    ColorUtils.blendARGB(colorSurfaceNormal, tint, 0.10f)
                } else {
                    colorSurfaceNormal
                }
                card.setCardBackgroundColor(bg)
            }

            val items = folder.items
            if (items.isNotEmpty()) {
                art1.isVisible = true
                thumbnails.load(art1, items[0])
            } else {
                art1.setImageResource(R.drawable.ic_music)
            }
            if (items.size > 1) {
                art2.isVisible = true
                thumbnails.load(art2, items[1])
            } else {
                art2.setImageResource(R.drawable.ic_music)
            }
            if (items.size > 2) {
                art3.isVisible = true
                thumbnails.load(art3, items[2])
            } else {
                art3.setImageResource(R.drawable.ic_music)
            }

            itemView.applyTactilePressAnimation()
            itemView.setOnClickListener { onFolderClick(folder) }
        }

        private fun dp(value: Int): Int =
            (value * itemView.resources.displayMetrics.density).toInt()
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
        private const val TYPE_FOLDER_CAROUSEL = 6
    }
}
