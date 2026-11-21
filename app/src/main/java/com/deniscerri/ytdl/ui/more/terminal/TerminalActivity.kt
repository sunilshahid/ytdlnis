package com.deniscerri.ytdl.ui.more.terminal

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModelProvider
import com.deniscerri.ytdl.database.viewmodel.TerminalViewModel
import com.deniscerri.ytdl.ui.BaseActivity
import com.deniscerri.ytdl.ui.compose.theme.YtdlnisTheme
import com.deniscerri.ytdl.util.FileUtil

class TerminalActivity : BaseActivity() {
    lateinit var terminalViewModel: TerminalViewModel
    val pendingShare = mutableStateOf<String?>(null)

    @SuppressLint("SetTextI18n")
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        terminalViewModel = ViewModelProvider(this)[TerminalViewModel::class.java]
        val initialShare = extractShareText(intent)
        pendingShare.value = initialShare

        setContent {
            YtdlnisTheme {
                TerminalActivityScreen(
                    terminalViewModel = terminalViewModel,
                    activity = this,
                    initialShareText = pendingShare.value,
                    onFinish = { finish() }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val text = extractShareText(intent)
        if (!text.isNullOrBlank()) {
            pendingShare.value = text
        }
    }

    private fun extractShareText(intent: Intent): String? {
        val action = intent.action
        val type = intent.type
        Log.e(TAG, "$action $type")
        if (action == Intent.ACTION_SEND && type != null) {
            Log.e(TAG, action)
            return if (intent.getStringExtra(Intent.EXTRA_TEXT) == null) {
                val uri = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_STREAM)
                }
                "-a \"${FileUtil.formatPath(uri?.path ?: "")}\""
            } else {
                intent.getStringExtra(Intent.EXTRA_TEXT)
            }
        }
        return null
    }

    override fun onStart() {
        super.onStart()
        terminalViewModel.startAndBindService(this)
    }

    override fun onStop() {
        super.onStop()
        terminalViewModel.unbindService(this)
    }

    companion object {
        private const val TAG = "TerminalActivity"
    }
}
