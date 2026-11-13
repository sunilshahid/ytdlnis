package com.deniscerri.ytdl.util.extractors.newpipe.potoken

import android.util.Log
import android.webkit.CookieManager
import com.deniscerri.ytdl.App
import com.deniscerri.ytdl.BuildConfig
import kotlinx.coroutines.runBlocking
import org.schabi.newpipe.extractor.services.youtube.PoTokenProvider
import org.schabi.newpipe.extractor.services.youtube.PoTokenResult

class NewPipePoTokenGenerator : PoTokenProvider {
    private val tag = NewPipePoTokenGenerator::class.simpleName
    private val supportsWebView by lazy { runCatching { CookieManager.getInstance() }.isSuccess }

    override fun getWebClientPoToken(videoId: String): PoTokenResult? {
        if (!supportsWebView) {
            return null
        }

        val bundle = runCatching {
            runBlocking {
                PoTokenCoordinator.getOrMintForNewPipe(App.instance, videoId)
            }
        }.onFailure { throwable ->
            Log.e(tag, "Failed to obtain coordinated PO token", throwable)
        }.getOrNull() ?: return null

        val playerPot = bundle.playerToken ?: return null
        if (BuildConfig.DEBUG) {
            Log.d(
                tag,
                "Resolved PO token for video=$videoId source=${bundle.source.id} visitorData=${bundle.maskedVisitorData}"
            )
        }
        return PoTokenResult(bundle.visitorData, playerPot, bundle.streamingToken)
    }

    override fun getWebEmbedClientPoToken(videoId: String?): PoTokenResult? = null

    override fun getAndroidClientPoToken(videoId: String?): PoTokenResult? = null

    override fun getIosClientPoToken(videoId: String?): PoTokenResult? = null
}
