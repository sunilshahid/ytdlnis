package com.deniscerri.ytdl.ui.compose.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Matches `res/layout/preference_category.xml`: 13.5 sp medium, `colorPrimary`. */
@Composable
fun SectionHeader(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 20.dp, top = 18.dp, end = 20.dp, bottom = 8.dp),
        color = MaterialTheme.colorScheme.primary,
        fontSize = 13.5.sp,
        fontWeight = FontWeight.Medium,
        fontFamily = FontFamily.SansSerif,
    )
}
