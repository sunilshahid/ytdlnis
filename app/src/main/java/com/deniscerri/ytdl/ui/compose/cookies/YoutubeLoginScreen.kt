package com.deniscerri.ytdl.ui.compose.cookies

import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.ui.compose.theme.YtdlnisTheme

/**
 * Compose screen for YouTube sign-in (eleXML §5 D3).
 * Replaces activity_youtube_login.xml and youtube_login_menu.xml.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YoutubeLoginScreen(
    isLoading: Boolean,
    onBackClick: () -> Unit,
    onDoneClick: () -> Unit,
    webViewFactory: () -> WebView,
    modifier: Modifier = Modifier,
) {
    YtdlnisTheme {
        Scaffold(
            modifier = modifier.fillMaxSize(),
            topBar = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    TopAppBar(
                        title = { Text(stringResource(R.string.sign_in_to_youtube)) },
                        navigationIcon = {
                            IconButton(onClick = onBackClick) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_back),
                                    contentDescription = stringResource(R.string.back)
                                )
                            }
                        },
                        actions = {
                            TextButton(onClick = onDoneClick) {
                                Text(
                                    text = stringResource(R.string.done),
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            titleContentColor = MaterialTheme.colorScheme.onSurface,
                        )
                    )
                    if (isLoading) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(MaterialTheme.colorScheme.background)
            ) {
                AndroidView(
                    factory = { webViewFactory() },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
