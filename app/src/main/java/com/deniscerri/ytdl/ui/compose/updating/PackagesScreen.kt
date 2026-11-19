package com.deniscerri.ytdl.ui.compose.updating

import android.text.format.DateFormat
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.core.packages.PackageBase
import com.deniscerri.ytdl.database.models.PackageItem
import com.deniscerri.ytdl.ui.compose.components.EmptyState
import com.deniscerri.ytdl.ui.compose.components.YtdlBottomSheet
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Port of `fragment_packages.xml`, `plugin_item.xml`, `plugin_releases_bottom_sheet.xml` and
 * `plugin_release_item.xml`.
 *
 * The data comes from [com.deniscerri.ytdl.core.RuntimeManager] and the release list from the
 * package's GitHub releases endpoint; only the presentation moved to Compose.
 */

/** One row of the package list: the adapter's bind logic expressed as a value. */
data class PackageRowState(
    val item: PackageItem,
    val location: PackageBase.PackageLocation,
    val version: String?,
    val isDownloaded: Boolean,
    val isBundled: Boolean,
)

/** State of the release bottom sheet shown after tapping a package. */
data class PackageReleaseSheetState(
    val title: String,
    val isLoading: Boolean = true,
    val releases: List<PackageBase.PackageRelease> = emptyList(),
)

@Composable
fun PackagesScreen(
    packages: List<PackageRowState>,
    onPackageClick: (PackageRowState) -> Unit,
    onPackageLongClick: (PackageRowState) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        PackagesInfoCard()

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(packages, key = { it.item.title }) { item ->
                PackageRow(
                    item = item,
                    onClick = { onPackageClick(item) },
                    onLongClick = { onPackageLongClick(item) },
                )
            }
        }
    }
}

/** `MaterialCardView` with `colorSecondaryContainer`, 20 dp corners and a 20 dp margin. */
@Composable
private fun PackagesInfoCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Text(
            text = stringResource(R.string.packages_description),
            modifier = Modifier.padding(15.dp),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

/**
 * 1:1 with `plugin_item.xml`: 5 dp corners, transparent card, 20×10 dp content padding,
 * 30 dp code icon, 17 sp bold title and a 15 sp monospace version line.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PackageRow(
    item: PackageRowState,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val shape = RoundedCornerShape(5.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_code),
            contentDescription = null,
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(5.dp)),
            contentScale = ContentScale.Crop,
        )
        Spacer(Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.item.title,
                modifier = Modifier.padding(horizontal = 5.dp),
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            Row(
                modifier = Modifier.padding(top = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = item.version ?: "",
                    modifier = Modifier.padding(horizontal = 5.dp),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                if (item.isDownloaded) {
                    PackageChip(
                        text = stringResource(R.string.installed),
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                if (item.isBundled) {
                    PackageChip(
                        text = stringResource(R.string.bundled),
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
            }
        }

        Icon(
            painter = painterResource(R.drawable.outline_arrow_forward_24),
            contentDescription = stringResource(R.string.preferred_download_type),
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.secondary,
        )
    }
}

/**
 * The `TextView` chips in `plugin_item.xml` draw `rounded_corner.xml` (solid white, 5 dp radius)
 * and take their text colour from the FAB styles, so the white pill is intentional and not a
 * theme colour role.
 */
@Composable
private fun PackageChip(text: String, contentColor: Color) {
    Box(
        modifier = Modifier
            .padding(start = 5.dp)
            .defaultMinSize(minWidth = 30.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(Color.White)
            .padding(horizontal = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = contentColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

/**
 * Replaces the imperatively shown `BottomSheetDialog`. The list, spinner and "no results" state
 * are the three visibility combinations of the old layout.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PackageReleasesSheet(
    state: PackageReleaseSheetState,
    onDismissRequest: () -> Unit,
    onDownloadRelease: (PackageBase.PackageRelease) -> Unit,
    onDeleteRelease: (PackageBase.PackageRelease) -> Unit,
) {
    YtdlBottomSheet(onDismissRequest = onDismissRequest) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(top = 20.dp),
            ) {
                Text(
                    text = stringResource(R.string.packages),
                    fontSize = 20.sp,
                    maxLines = 2,
                )
                Text(
                    text = state.title,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            when {
                state.isLoading -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(70.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }

                state.releases.isEmpty() -> EmptyState(
                    title = stringResource(R.string.no_results),
                    iconRes = R.drawable.ic_no_results,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(70.dp),
                )

                else -> LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) {
                    items(state.releases, key = { it.version }) { release ->
                        PackageReleaseRow(
                            release = release,
                            onDownload = { onDownloadRelease(release) },
                            onDelete = { onDeleteRelease(release) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * 1:1 with `plugin_release_item.xml`. The progress payload the old adapter accepted was never
 * sent by any call-site, so there is no progress state here.
 */
@Composable
private fun PackageReleaseRow(
    release: PackageBase.PackageRelease,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "v${release.version}",
                modifier = Modifier.padding(horizontal = 5.dp),
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = formatReleaseDate(release.published_at),
                modifier = Modifier.padding(horizontal = 5.dp),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (!release.isBundled) {
            IconButton(onClick = if (release.isInstalled) onDelete else onDownload) {
                Icon(
                    painter = painterResource(
                        if (release.isInstalled) {
                            R.drawable.ic_baseline_delete_outline_24
                        } else {
                            R.drawable.ic_down
                        }
                    ),
                    contentDescription = stringResource(
                        if (release.isInstalled) R.string.delete else R.string.download
                    ),
                    tint = MaterialTheme.colorScheme.secondary,
                )
            }
        }
    }
}

/** Same parser/pattern pair as the old `PackageReleaseAdapter`. */
private fun formatReleaseDate(publishedAt: String): String {
    val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault())
    parser.timeZone = TimeZone.getTimeZone("UTC")
    val date = runCatching { parser.parse(publishedAt) }.getOrNull() ?: return ""

    return SimpleDateFormat(
        DateFormat.getBestDateTimePattern(Locale.getDefault(), "ddMMMyyyy - HHmm"),
        Locale.getDefault(),
    ).format(date.time)
}
