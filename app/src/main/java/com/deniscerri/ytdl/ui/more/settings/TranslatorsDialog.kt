package com.deniscerri.ytdl.ui.more.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.ImageView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.ui.compose.theme.YtdlnisTheme
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.gson.JsonParser
import com.squareup.picasso.Callback
import com.squareup.picasso.Picasso
import jp.wasabeef.picasso.transformations.CropCircleTransformation
import java.util.Locale

/**
 * Shows the Weblate translators bundled in assets/translators.json
 * (generated in CI by scripts/fetch_translators.py, may be missing in other builds).
 *
 * Phase 3: the list is Compose now (`translators_dialog.xml`, `translator_item.xml` and
 * `translator_header_item.xml` are gone). The dialog window stays `MaterialAlertDialogBuilder`
 * because this is shown from an XML preference screen; the signature is unchanged for the caller.
 *
 * Avatars still go through Picasso — Coil is not in the offline cache (plan Appendix D), so the
 * network image load stays on an `ImageView` inside `AndroidView`.
 */
object TranslatorsDialog {
    private const val ASSET = "translators.json"
    private const val WEBLATE_URL = "https://hosted.weblate.org/projects/ytdlnis/"

    fun show(context: Context) {
        val entries = load(context)

        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.translators)
            .setView(
                ComposeView(context).apply {
                    setContent {
                        YtdlnisTheme {
                            TranslatorsContent(entries)
                        }
                    }
                },
            )
            .setPositiveButton(R.string.help_translate) { _, _ ->
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(WEBLATE_URL)))
            }
            .setNegativeButton(R.string.dismiss, null)
            .show()
    }

    private fun load(context: Context): List<TranslatorEntry> = runCatching {
        val json = context.assets.open(ASSET).bufferedReader().use { it.readText() }
        val entries = mutableListOf<TranslatorEntry>()
        JsonParser.parseString(json).asJsonObject.entrySet()
            .map { (code, users) -> languageName(code) to users.asJsonArray }
            .sortedBy { it.first }
            .forEach { (language, users) ->
                if (users.size() == 0) return@forEach
                entries.add(TranslatorEntry.Header(language))
                users.forEach {
                    val user = it.asJsonObject
                    entries.add(
                        TranslatorEntry.User(
                            user["username"].asString,
                            user["avatar"]?.asString ?: "",
                        ),
                    )
                }
            }
        entries
    }.getOrDefault(emptyList())

    // Weblate codes look like pt_BR or zh_Hans
    private fun languageName(code: String): String {
        val tag = code.replace('_', '-')
        val name = Locale.forLanguageTag(tag).getDisplayName(Locale.getDefault())
        return if (name.isBlank() || name == tag) code else name.replaceFirstChar { it.uppercase() }
    }
}

private sealed interface TranslatorEntry {
    data class Header(val language: String) : TranslatorEntry
    data class User(val username: String, val avatar: String) : TranslatorEntry
}

@Composable
private fun TranslatorsContent(entries: List<TranslatorEntry>) {
    if (entries.isEmpty()) {
        Text(
            text = stringResource(R.string.translators_unavailable),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(24.dp),
        )
        return
    }

    // Height is capped so the lazy list works inside a wrap-content dialog; short lists still wrap.
    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
        itemsIndexed(entries) { _, entry ->
            when (entry) {
                is TranslatorEntry.Header -> LanguageHeader(entry.language)
                is TranslatorEntry.User -> TranslatorRow(entry.username, entry.avatar)
            }
        }
    }
}

/** `translator_header_item.xml`: titleSmall, primary, 24 dp sides, 16 top / 4 bottom. */
@Composable
private fun LanguageHeader(language: String) {
    Text(
        text = language,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 4.dp),
    )
}

/** `translator_item.xml`: 40 dp avatar, 16 dp gap, bodyLarge username. */
@Composable
private fun TranslatorRow(username: String, avatarUrl: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TranslatorAvatar(
            initials = username.take(1).uppercase(),
            avatarUrl = avatarUrl,
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = username,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

/**
 * `translator_avatar_placeholder.xml` (primaryContainer circle, onPrimaryContainer 16 sp bold
 * initials) with the avatar drawn on top only once Picasso reports success — exactly the
 * visibility dance the RecyclerView adapter did, so a failed load never leaves an empty hole.
 */
@Composable
private fun TranslatorAvatar(initials: String, avatarUrl: String) {
    var loaded by remember(avatarUrl) { mutableStateOf(false) }
    val request = remember(avatarUrl) { AvatarRequest() }

    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        if (!loaded) {
            Text(
                text = initials,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        if (avatarUrl.isNotBlank()) {
            AndroidView(
                modifier = Modifier.matchParentSize(),
                factory = { context ->
                    ImageView(context).apply {
                        scaleType = ImageView.ScaleType.CENTER_CROP
                        if (!request.requested) {
                            request.requested = true
                            Picasso.get()
                                .load(avatarUrl)
                                .transform(CropCircleTransformation())
                                .into(this, object : Callback {
                                    override fun onSuccess() {
                                        loaded = true
                                    }

                                    override fun onError(e: Exception?) = Unit
                                })
                        }
                    }
                },
                onRelease = { Picasso.get().cancelRequest(it) },
            )
        }
    }
}

/** Plain holder: the factory must not write Compose state, or it recomposes itself in a loop. */
private class AvatarRequest {
    var requested = false
}
