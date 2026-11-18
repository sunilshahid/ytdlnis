package com.deniscerri.ytdl.ui.play

import android.annotation.SuppressLint
import android.graphics.Color
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.playback.model.PlaybackItem
import com.deniscerri.ytdl.playback.model.PlaybackSource
import com.deniscerri.ytdl.util.Extensions.applyTactilePressAnimation
import com.deniscerri.ytdl.util.Extensions.extractAudioFormat
import com.deniscerri.ytdl.util.Extensions.toStringTimeStamp
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors

class AudioQueueAdapter(
    private val onItemClick: (Int) -> Unit,
    private val onStartDrag: (RecyclerView.ViewHolder) -> Unit
) : RecyclerView.Adapter<AudioQueueAdapter.QueueViewHolder>() {

    private val items = mutableListOf<PlaybackItem>()
    private var currentIndex = -1
    private var dragInProgress = false
    private var dragStartPosition = RecyclerView.NO_POSITION
    private var dragCurrentPosition = RecyclerView.NO_POSITION

    @SuppressLint("NotifyDataSetChanged")
    fun submitQueue(newItems: List<PlaybackItem>, activeIndex: Int) {
        // While a row is being dragged the local list already holds the user's
        // ordering. Playback ticks keep publishing the old queue, and rebinding
        // the list mid-drag is what made the handle jump around.
        if (dragInProgress) return
        if (items == newItems) {
            updateCurrentIndex(activeIndex)
            return
        }
        items.clear()
        items.addAll(newItems)
        currentIndex = activeIndex
        notifyDataSetChanged()
    }

    fun updateCurrentIndex(index: Int) {
        if (index == currentIndex) return
        val oldIndex = currentIndex
        currentIndex = index
        if (oldIndex in items.indices) notifyItemChanged(oldIndex)
        if (currentIndex in items.indices) notifyItemChanged(currentIndex)
    }

    /** Remembers the row's starting position when its drag handle is pressed. */
    fun beginDrag(position: Int) {
        if (position == RecyclerView.NO_POSITION) return
        dragInProgress = true
        dragStartPosition = position
        dragCurrentPosition = position
    }

    /**
     * Ends the drag and returns the original and final positions, or null when
     * the row did not move. The queue itself is committed by the caller once,
     * instead of issuing a controller update on every row crossing.
     */
    fun endDrag(): Pair<Int, Int>? {
        dragInProgress = false
        val from = dragStartPosition
        val to = dragCurrentPosition
        dragStartPosition = RecyclerView.NO_POSITION
        dragCurrentPosition = RecyclerView.NO_POSITION
        return if (from != RecyclerView.NO_POSITION && to != RecyclerView.NO_POSITION && from != to) {
            from to to
        } else {
            null
        }
    }

    fun moveItem(from: Int, to: Int) {
        if (from !in items.indices || to !in items.indices || from == to) return
        val moved = items.removeAt(from)
        items.add(to, moved)
        if (currentIndex == from) {
            currentIndex = to
        } else if (from < currentIndex && to >= currentIndex) {
            currentIndex--
        } else if (from > currentIndex && to <= currentIndex) {
            currentIndex++
        }
        notifyItemMoved(from, to)
        if (dragInProgress) dragCurrentPosition = to
    }

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QueueViewHolder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.audio_queue_row, parent, false)
        return QueueViewHolder(v)
    }

    override fun onBindViewHolder(holder: QueueViewHolder, position: Int) {
        holder.bind(items[position], position == currentIndex)
    }

    inner class QueueViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val card: MaterialCardView = view.findViewById(R.id.queue_row_root)
        private val art: ImageView = view.findViewById(R.id.queue_row_art)
        private val equalizer: ImageView = view.findViewById(R.id.queue_row_equalizer)
        private val title: TextView = view.findViewById(R.id.queue_row_title)
        private val subtitle: TextView = view.findViewById(R.id.queue_row_subtitle)
        private val formatBadge: TextView = view.findViewById(R.id.queue_row_format_badge)
        private val duration: TextView = view.findViewById(R.id.queue_row_duration)
        val dragHandle: ImageView = view.findViewById(R.id.queue_row_drag_handle)

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

        @SuppressLint("ClickableViewAccessibility")
        fun bind(item: PlaybackItem, isCurrent: Boolean) {
            title.text = item.title
            title.setTextColor(if (isCurrent) colorPrimary else colorOnSurface)
            val cleanArtist = item.artist?.takeIf { it.isNotBlank() } ?: item.folderName
            subtitle.text = cleanArtist
            subtitle.isVisible = !cleanArtist.isNullOrBlank()

            val format = extractAudioFormat(null, item.localUri, null)
                ?: if (item.source == PlaybackSource.YOUTUBE) "YT" else null
            if (format != null) {
                formatBadge.text = format
                formatBadge.isVisible = true
            } else {
                formatBadge.isVisible = false
            }

            duration.text = item.durationMs?.takeIf { it > 0 }?.toStringTimeStamp().orEmpty()
            duration.isVisible = duration.text.isNotBlank()

            equalizer.isVisible = isCurrent
            PlaybackArtworkLoader.load(art, item)

            val density = itemView.resources.displayMetrics.density
            if (isCurrent) {
                card.setCardBackgroundColor(colorSecondaryContainer)
                card.strokeColor = colorPrimary
                card.strokeWidth = (2f * density).toInt()
            } else {
                card.setCardBackgroundColor(colorSurfaceContainer)
                card.strokeColor = (colorOutlineVariant and 0x00FFFFFF) or 0x33000000
                card.strokeWidth = (1f * density).toInt()
            }

            itemView.applyTactilePressAnimation()
            itemView.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    onItemClick(pos)
                }
            }

            dragHandle.setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    onStartDrag(this)
                    // Consume the press so the row's click listener does not fire
                    // when the handle is tapped without moving.
                    return@setOnTouchListener true
                }
                false
            }
        }
    }
}
