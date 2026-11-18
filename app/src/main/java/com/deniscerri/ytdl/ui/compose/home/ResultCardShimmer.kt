package com.deniscerri.ytdl.ui.compose.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.deniscerri.ytdl.ui.compose.components.ShimmerBox

/**
 * Shimmer placeholder for result cards (Phase 4b).
 *
 * Replaces `res/layout/result_card_shimmer.xml`.
 */
@Composable
fun ResultCardShimmer(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .aspectRatio(16f / 9f),
    ) {
        ShimmerBox(
            modifier = Modifier.matchParentSize(),
            shape = RoundedCornerShape(20.dp),
        )
    }
}
