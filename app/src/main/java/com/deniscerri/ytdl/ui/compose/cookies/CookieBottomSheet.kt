package com.deniscerri.ytdl.ui.compose.cookies

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.models.CookieItem
import com.deniscerri.ytdl.database.repository.CookieRepository
import com.deniscerri.ytdl.ui.compose.components.YtdlBottomSheet
import com.deniscerri.ytdl.util.Extensions.isURL

/**
 * Bottom sheet for adding or editing a cookie (Phase 3).
 * Replaces `cookie_bottom_sheet.xml`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CookieBottomSheet(
    item: CookieItem?,
    onDismissRequest: () -> Unit,
    onSave: (CookieItem) -> Unit,
    onGetCookies: (url: String, description: String, incognito: Boolean) -> Unit,
    onCopyContent: (String) -> Unit,
) {
    var url by remember(item) { mutableStateOf(item?.url ?: "https://") }
    var description by remember(item) { mutableStateOf(item?.description ?: "") }
    var incognito by remember { mutableStateOf(false) }

    val isUrlValid = remember(url) { url.isURL() }

    YtdlBottomSheet(onDismissRequest = onDismissRequest) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (item != null) {
                // Card displaying current cookie content
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.cookies),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            IconButton(
                                onClick = {
                                    val header = CookieRepository.cookieHeader
                                    onCopyContent("$header\n${item.content}")
                                },
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_copy),
                                    contentDescription = stringResource(android.R.string.copy),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        SelectionContainer {
                            Text(
                                text = item.content,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 140.dp)
                                    .verticalScroll(rememberScrollState()),
                            )
                        }
                    }
                }

                // Description/Title input
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.title)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }

            // URL input
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text(stringResource(R.string.url)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = url.isNotEmpty() && !isUrlValid,
            )

            // Incognito Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.incognito),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Switch(
                    checked = incognito,
                    onCheckedChange = { incognito = it },
                )
            }

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (item != null) {
                    FilledTonalIconButton(
                        onClick = {
                            val updated = item.copy(
                                description = description,
                                url = url,
                            )
                            onSave(updated)
                            onDismissRequest()
                        },
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.baseline_save_24),
                            contentDescription = stringResource(R.string.save),
                        )
                    }
                } else {
                    Spacer(Modifier.width(1.dp))
                }

                Button(
                    onClick = {
                        onGetCookies(url, description, incognito)
                        onDismissRequest()
                    },
                    enabled = isUrlValid,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.baseline_cookie_24),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (item != null) {
                            stringResource(R.string.update)
                        } else {
                            stringResource(R.string.get_cookies)
                        },
                    )
                }
            }
        }
    }
}
