package com.deniscerri.ytdl.ui.play

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.playback.model.PlaybackItem
import com.deniscerri.ytdl.playback.model.PlaybackSource
import com.deniscerri.ytdl.playback.model.PlaybackType
import com.deniscerri.ytdl.util.Extensions.toStringTimeStamp
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import com.deniscerri.ytdl.ui.play.PlaybackArtworkLoader
import android.animation.ValueAnimator
import android.graphics.Color
import androidx.core.graphics.ColorUtils
import com.google.android.material.card.MaterialCardView
import com.google.android.material.slider.Slider
import com.deniscerri.ytdl.playback.PlaybackController


sealed interface PlayRow {
    val stableId: String

    data class Continue(
        val item: PlaybackItem,
        val playing: Boolean,
        val positionMs: Long,
        val durationMs: Long
    ) : PlayRow {
        override val stableId: String get() = "continue:${item.mediaId}"
    }

    data class Actions(
        val shuffleEnabled: Boolean,
        val downloadsEnabled: Boolean
    ) : PlayRow {
        override val stableId: String get() = "actions"
    }

    data class Header(
        val title: String,
        val count: Int? = null
    ) : PlayRow {
        override val stableId: String get() = "header:$title"
    }

    data class Track(
        val item: PlaybackItem,
        val subtitle: String?,
        val trailing: String?,
        val queue: List<PlaybackItem>,
        val index: Int,
        val currentQueue: Boolean = false,
        val isCurrent: Boolean = false
    ) : PlayRow {
        override val stableId: String get() = "track:$currentQueue:${item.mediaId}:$index"
    }

    data class Empty(
        val title: String,
        val subtitle: String? = null,
        val icon: Int = R.drawable.ic_music
    ) : PlayRow {
        override val stableId: String get() = "empty:$title"
    }
}

/**
 * Flat, sectioned list for the Play tab. [ListAdapter] diffs every playback tick, so only the
 * continue card and the affected track rows are rebound; artwork requests are cached per holder.
 */
class PlayAdapter(
    private val onContinueClick: (PlayRow.Continue) -> Unit,
    private val onContinueButton: (PlayRow.Continue) -> Unit,
    private val onShuffle: () -> Unit,
    private val onPlayDownloads: () -> Unit,
    private val onTrackClick: (PlayRow.Track) -> Unit,
    private val onTrackMenu: (View, PlayRow.Track) -> Unit
) : ListAdapter<PlayRow, RecyclerView.ViewHolder>(PlayRowDiff) {

    private var isGrid = false

    fun setGrid(grid: Boolean) {
        if (isGrid == grid) return
        isGrid = grid
        notifyDataSetChanged()
    }

    fun spanSize(position: Int): Int {
        if (position !in 0 until itemCount) return 2
        return if (getItem(position) is PlayRow.Track) 1 else 2
    }

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is PlayRow.Continue -> TYPE_CONTINUE
        is PlayRow.Actions -> TYPE_ACTIONS
        is PlayRow.Header -> TYPE_HEADER
        is PlayRow.Track -> if (isGrid) TYPE_TRACK_GRID else TYPE_TRACK
        is PlayRow.Empty -> TYPE_EMPTY
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_CONTINUE -> ContinueHolder(inflater.inflate(R.layout.play_continue_card, parent, false))
            TYPE_ACTIONS -> ActionsHolder(inflater.inflate(R.layout.play_quick_actions, parent, false))
            TYPE_HEADER -> HeaderHolder(inflater.inflate(R.layout.play_section_header, parent, false))
            TYPE_TRACK_GRID -> TrackHolder(inflater.inflate(R.layout.play_track_grid_item, parent, false))
            TYPE_TRACK -> TrackHolder(inflater.inflate(R.layout.play_track_row, parent, false))
            else -> EmptyHolder(inflater.inflate(R.layout.play_empty_row, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = getItem(position)) {
            is PlayRow.Continue -> (holder as ContinueHolder).bind(row)
            is PlayRow.Actions -> (holder as ActionsHolder).bind(row)
            is PlayRow.Header -> (holder as HeaderHolder).bind(row)
            is PlayRow.Track -> (holder as TrackHolder).bind(row)
            is PlayRow.Empty -> (holder as EmptyHolder).bind(row)
        }
    }

    private inner class ContinueHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val rootCard: MaterialCardView = view.findViewById(R.id.play_continue_root)
        private val artCard: MaterialCardView = view.findViewById(R.id.play_continue_art_card)
        private val contentLayout: View = view.findViewById(R.id.play_continue_content)
        private val art: ImageView = view.findViewById(R.id.play_continue_art)
        private val label: TextView = view.findViewById(R.id.play_continue_label)
        private val title: TextView = view.findViewById(R.id.play_continue_title)
        private val subtitle: TextView = view.findViewById(R.id.play_continue_subtitle)
        private val slider: Slider = view.findViewById(R.id.play_continue_slider)
        private val position: TextView = view.findViewById(R.id.play_continue_position)
        private val duration: TextView = view.findViewById(R.id.play_continue_duration)
        private val button: ImageButton = view.findViewById(R.id.play_continue_button)

        private var isUserSeeking = false
        private var glowAnimator: ValueAnimator? = null

        init {
            slider.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
                override fun onStartTrackingTouch(slider: Slider) {
                    isUserSeeking = true
                    slider.parent?.requestDisallowInterceptTouchEvent(true)
                }

                override fun onStopTrackingTouch(slider: Slider) {
                    isUserSeeking = false
                    slider.parent?.requestDisallowInterceptTouchEvent(false)
                    PlaybackController.seekTo(slider.value.toLong())
                }
            })

            slider.addOnChangeListener { _, value, fromUser ->
                if (fromUser) {
                    position.text = value.toLong().toStringTimeStamp()
                }
            }

            itemView.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {}
                override fun onViewDetachedFromWindow(v: View) {
                    glowAnimator?.cancel()
                }
            })
        }

        fun bind(row: PlayRow.Continue) {
            label.setText(if (row.playing) R.string.now_playing else R.string.continue_playing)
            title.text = row.item.title
            subtitle.text = subtitleFor(itemView.context, row.item)
            loadArtwork(art, row.item)

            if (row.durationMs > 0L) {
                slider.isVisible = true
                slider.valueFrom = 0f
                slider.valueTo = row.durationMs.toFloat()
                if (!isUserSeeking) {
                    slider.value = row.positionMs.toFloat().coerceIn(0f, row.durationMs.toFloat())
                    position.text = row.positionMs.coerceAtLeast(0L).toStringTimeStamp()
                }
                duration.text = row.durationMs.toStringTimeStamp()
            } else {
                slider.isVisible = false
                position.text = ""
                duration.text = ""
            }

            button.setImageResource(
                if (row.playing) R.drawable.ic_lyrics_pause else R.drawable.ic_lyrics_play
            )
            button.contentDescription = itemView.context.getString(
                if (row.playing) R.string.pause else R.string.play
            )

            val openAction = View.OnClickListener { onContinueClick(row) }
            itemView.setOnClickListener(openAction)
            rootCard.setOnClickListener(openAction)
            contentLayout.setOnClickListener(openAction)
            artCard.setOnClickListener(openAction)
            button.setOnClickListener { onContinueButton(row) }

            updateGlow(row.playing)
        }

        private fun updateGlow(playing: Boolean) {
            glowAnimator?.cancel()
            val primaryColor = MaterialColors.getColor(
                itemView,
                com.google.android.material.R.attr.colorPrimary,
                Color.WHITE
            )
            val outlineVariant = MaterialColors.getColor(
                itemView,
                com.google.android.material.R.attr.colorOutlineVariant,
                Color.DKGRAY
            )

            if (playing) {
                artCard.strokeWidth = dpToPx(1.5f)
                val dimColor = ColorUtils.setAlphaComponent(primaryColor, 60)
                val brightColor = ColorUtils.setAlphaComponent(primaryColor, 230)
                glowAnimator = ValueAnimator.ofArgb(dimColor, brightColor).apply {
                    duration = 1400L
                    repeatCount = ValueAnimator.INFINITE
                    repeatMode = ValueAnimator.REVERSE
                    addUpdateListener { anim ->
                        val color = anim.animatedValue as Int
                        rootCard.strokeColor = color
                        artCard.strokeColor = color
                    }
                    start()
                }
                rootCard.strokeWidth = dpToPx(2f)
            } else {
                rootCard.strokeColor = outlineVariant
                rootCard.strokeWidth = dpToPx(1f)
                artCard.strokeWidth = 0
            }
        }

        private fun dpToPx(dp: Float): Int {
            return (dp * itemView.resources.displayMetrics.density).toInt()
        }
    }

    private inner class ActionsHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val shuffle: MaterialButton = view.findViewById(R.id.play_action_shuffle)
        private val downloads: MaterialButton = view.findViewById(R.id.play_action_downloads)

        fun bind(row: PlayRow.Actions) {
            shuffle.isEnabled = row.shuffleEnabled
            shuffle.alpha = if (row.shuffleEnabled) 1f else 0.45f
            downloads.isEnabled = row.downloadsEnabled
            downloads.alpha = if (row.downloadsEnabled) 1f else 0.45f
            shuffle.setOnClickListener { onShuffle() }
            downloads.setOnClickListener { onPlayDownloads() }
        }
    }

    private inner class HeaderHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val title: TextView = view.findViewById(R.id.play_section_title)
        private val count: TextView = view.findViewById(R.id.play_section_count)
        private val badgeCard: View? = view.findViewById(R.id.play_section_badge_card)

        fun bind(row: PlayRow.Header) {
            title.text = row.title
            val countValue = row.count ?: 0
            badgeCard?.isVisible = countValue > 0
            count.isVisible = countValue > 0
            count.text = countValue.toString()
        }
    }

    private inner class TrackHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val root: View = view.findViewById(R.id.play_track_root)
        private val art: ImageView = view.findViewById(R.id.play_row_art)
        private val typeBadge: ImageView? = view.findViewById(R.id.play_row_type_badge)
        private val title: TextView = view.findViewById(R.id.play_row_title)
        private val subtitle: TextView = view.findViewById(R.id.play_row_subtitle)
        private val duration: TextView = view.findViewById(R.id.play_row_duration)
        private val equalizer: ImageView = view.findViewById(R.id.play_row_equalizer)
        private val menu: ImageButton = view.findViewById(R.id.play_row_menu)
        private val colorOnSurface = MaterialColors.getColor(
            view,
            com.google.android.material.R.attr.colorOnSurface
        )
        private val colorPrimary = MaterialColors.getColor(
            view,
            com.google.android.material.R.attr.colorPrimary
        )
        private val colorOutlineVariant = MaterialColors.getColor(
            view,
            com.google.android.material.R.attr.colorOutlineVariant
        )

        fun bind(row: PlayRow.Track) {
            title.text = row.item.title
            title.setTextColor(if (row.isCurrent) colorPrimary else colorOnSurface)
            subtitle.text = row.subtitle
            subtitle.isVisible = !row.subtitle.isNullOrBlank()
            duration.text = row.trailing.orEmpty()
            duration.isVisible = !row.trailing.isNullOrBlank()
            equalizer.isVisible = row.isCurrent

            val density = itemView.resources.displayMetrics.density
            if (root is MaterialCardView) {
                if (row.isCurrent) {
                    root.strokeColor = colorPrimary
                    root.strokeWidth = (2f * density).toInt()
                } else {
                    root.strokeColor = colorOutlineVariant
                    root.strokeWidth = (1f * density).toInt()
                }
            } else if (row.isCurrent) {
                root.setBackgroundResource(R.drawable.bg_active_track_row)
            } else {
                val outValue = android.util.TypedValue()
                itemView.context.theme.resolveAttribute(
                    android.R.attr.selectableItemBackground,
                    outValue,
                    true
                )
                root.setBackgroundResource(outValue.resourceId)
            }

            if (typeBadge != null) {
                if (row.item.type == PlaybackType.VIDEO) {
                    typeBadge.setImageResource(R.drawable.ic_video)
                    typeBadge.isVisible = true
                } else {
                    typeBadge.isVisible = false
                }
            }

            loadArtwork(art, row.item)
            itemView.setOnClickListener { onTrackClick(row) }
            itemView.setOnLongClickListener {
                onTrackMenu(it, row)
                true
            }
            menu.setOnClickListener { onTrackMenu(menu, row) }
        }
    }

    private inner class EmptyHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val icon: ImageView = view.findViewById(R.id.play_empty_icon)
        private val title: TextView = view.findViewById(R.id.play_empty_title)
        private val subtitle: TextView = view.findViewById(R.id.play_empty_text)

        fun bind(row: PlayRow.Empty) {
            icon.setImageResource(row.icon)
            title.text = row.title
            subtitle.text = row.subtitle.orEmpty()
            subtitle.isVisible = !row.subtitle.isNullOrBlank()
        }
    }

    private fun loadArtwork(target: ImageView, item: PlaybackItem) {
        PlaybackArtworkLoader.load(target, item)
    }

    private fun subtitleFor(context: android.content.Context, item: PlaybackItem): String {
        val source = when (item.source) {
            PlaybackSource.YOUTUBE -> context.getString(R.string.youtube)
            PlaybackSource.DOWNLOAD -> context.getString(R.string.downloads)
            PlaybackSource.LOCAL -> null
        }
        val type = context.getString(
            if (item.type == PlaybackType.VIDEO) R.string.video else R.string.audio
        )
        return listOfNotNull(
            item.artist?.takeIf { it.isNotBlank() },
            source,
            type
        ).joinToString(" · ")
    }

    private object PlayRowDiff : DiffUtil.ItemCallback<PlayRow>() {
        override fun areItemsTheSame(oldItem: PlayRow, newItem: PlayRow): Boolean =
            oldItem.stableId == newItem.stableId

        override fun areContentsTheSame(oldItem: PlayRow, newItem: PlayRow): Boolean =
            oldItem == newItem
    }

    companion object {
        private const val TYPE_CONTINUE = 0
        private const val TYPE_ACTIONS = 1
        private const val TYPE_HEADER = 2
        private const val TYPE_TRACK = 3
        private const val TYPE_TRACK_GRID = 4
        private const val TYPE_EMPTY = 5
    }
}
