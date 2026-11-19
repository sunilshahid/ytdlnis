package com.deniscerri.ytdl.ui.compose.updating

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.text.format.DateFormat
import android.util.TypedValue
import android.widget.TextView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.models.GithubRelease
import com.deniscerri.ytdl.ui.compose.components.EmptyState
import com.deniscerri.ytdl.ui.compose.components.YtdlActionChip
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.MarkwonConfiguration
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Port of `fragment_changelog.xml` + `changelog_item.xml`.
 *
 * The release body stays Markdown-rendered by Markwon inside an `AndroidView`: `Text` cannot render
 * Markdown without a new dependency, and dropping to plain text would lose headings and links. All
 * chrome (version, date, download chips, divider) is Compose.
 */
@Composable
fun ChangelogScreen(
    releases: List<GithubRelease>,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
) {
    val context = LocalContext.current
    val markwon = remember(context) {
        Markwon.builder(context).usePlugin(object : AbstractMarkwonPlugin() {
            override fun configureConfiguration(builder: MarkwonConfiguration.Builder) {
                builder.linkResolver { _, link ->
                    ContextCompat.startActivity(
                        context,
                        Intent(Intent.ACTION_VIEW, Uri.parse(link)),
                        Bundle(),
                    )
                }
            }
        }).build()
    }

    Box(modifier = modifier.fillMaxSize()) {
        when {
            releases.isNotEmpty() -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 96.dp),
            ) {
                items(releases, key = { it.html_url }) { release ->
                    ChangelogItem(release = release, markwon = markwon)
                }
            }

            !isLoading -> EmptyState(
                title = stringResource(R.string.no_results),
                iconRes = R.drawable.ic_no_results,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChangelogItem(release: GithubRelease, markwon: Markwon) {
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxWidth()) {
        HorizontalDivider(
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.secondary,
        )
        Spacer(Modifier.height(10.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp)
                .padding(bottom = 10.dp),
        ) {
            Text(
                text = release.tag_name,
                modifier = Modifier.padding(10.dp),
                fontFamily = FontFamily.Monospace,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = remember(release.published_at) {
                    SimpleDateFormat(
                        DateFormat.getBestDateTimePattern(Locale.getDefault(), "ddMMMyyyy - HHmm"),
                        Locale.getDefault(),
                    ).format(release.published_at.time)
                },
                modifier = Modifier.padding(horizontal = 10.dp),
                fontSize = 14.sp,
            )

            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp),
                factory = { ctx ->
                    TextView(ctx).apply {
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                        movementMethod = LinkMovementMethod.getInstance()
                        markwon.setMarkdown(this, release.body)
                    }
                },
                update = { view ->
                    if (view.tag != release.body) {
                        view.tag = release.body
                        markwon.setMarkdown(view, release.body)
                    }
                },
            )

            if (release.assets.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                        .padding(bottom = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    release.assets.forEach { asset ->
                        YtdlActionChip(
                            label = asset.name,
                            onClick = {
                                ContextCompat.startActivity(
                                    context,
                                    Intent(Intent.ACTION_VIEW, Uri.parse(asset.browser_download_url)),
                                    Bundle(),
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}
