package com.deniscerri.ytdl.ui.more.settings.advanced.generateyoutubepotokens

import android.app.Activity
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.core.content.edit
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import androidx.work.WorkManager
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.core.RuntimeManager
import com.deniscerri.ytdl.database.models.YoutubeGeneratePoTokenItem
import com.deniscerri.ytdl.database.models.YoutubePoTokenItem
import com.deniscerri.ytdl.services.BgUtilsPoTokenGeneratorService
import com.deniscerri.ytdl.ui.compose.host.ComposeFragment
import com.deniscerri.ytdl.ui.compose.potoken.BgUtilsSectionState
import com.deniscerri.ytdl.ui.compose.potoken.GeneratePoTokenUrlSheet
import com.deniscerri.ytdl.ui.compose.potoken.GenerateYoutubePoTokensScreen
import com.deniscerri.ytdl.ui.compose.potoken.ManualSectionState
import com.deniscerri.ytdl.ui.compose.potoken.PolicySectionState
import com.deniscerri.ytdl.ui.more.settings.SettingsActivity
import com.deniscerri.ytdl.ui.more.settings.advanced.generateyoutubepotokens.webview.PoTokenWebViewLoginActivity
import com.deniscerri.ytdl.util.BgUtilsPoTokenGeneratorUtil
import com.deniscerri.ytdl.util.Extensions.getIDFromYoutubeURL
import com.deniscerri.ytdl.util.UiUtil
import com.deniscerri.ytdl.util.extractors.newpipe.potoken.PoTokenCoordinator
import com.deniscerri.ytdl.util.extractors.newpipe.potoken.PoTokenFormatting
import com.deniscerri.ytdl.util.extractors.newpipe.potoken.PoTokenPolicy
import com.deniscerri.ytdl.util.extractors.newpipe.potoken.PoTokenSource
import com.deniscerri.ytdl.auth.YouTubeSessionEngine
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Compose port of `fragment_generate_youtube_po_token.xml` (Phase 3).
 *
 * Storage keys (`youtube_generated_po_tokens`, `use_bgutils_potoken_generator`,
 * `bgutils_potoken_method`), the `PoTokenCoordinator` policy and the WebView result contract are
 * unchanged. Only the screen body moved to Compose.
 */
class GenerateYoutubePoTokensFragment : ComposeFragment() {

    private lateinit var settingsActivity: SettingsActivity
    private lateinit var preferences: SharedPreferences
    private lateinit var configuration: MutableList<YoutubeGeneratePoTokenItem>
    private lateinit var workManager: WorkManager

    private lateinit var webPoTokenResultLauncher: ActivityResultLauncher<Intent>

    private var regenerating by mutableStateOf(false)
    private var policy by mutableStateOf(PoTokenPolicy.AUTO)
    private var statusText by mutableStateOf("")
    private var warningText by mutableStateOf<String?>(null)

    private var bgUtilsEnabled by mutableStateOf(false)
    private var bgUtilsMethod by mutableStateOf("server")
    private var denoInstalled = false
    private var nodeInstalled = false

    private var manualEnabled by mutableStateOf(false)
    private var webGvs by mutableStateOf("")
    private var webPlayer by mutableStateOf("")
    private var webSubs by mutableStateOf("")
    private var webVisitorData by mutableStateOf("")
    private var webPlayerClients by mutableStateOf("")
    private var useVisitorData by mutableStateOf(true)
    private var showUrlSheet by mutableStateOf(false)
    private var urlInput by mutableStateOf(SAMPLE_VIDEO_URL)
    private var webConf: YoutubeGeneratePoTokenItem? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settingsActivity = activity as SettingsActivity
        settingsActivity.changeTopAppbarTitle(getString(R.string.generate_potokens))
        preferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        workManager = WorkManager.getInstance(requireContext())

        configuration = runCatching {
            Gson().fromJson(
                preferences.getString("youtube_generated_po_tokens", "[]")!!.ifEmpty { "[]" },
                Array<YoutubeGeneratePoTokenItem>::class.java
            ).toMutableList()
        }.getOrDefault(mutableListOf())

        webPoTokenResultLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val data = result.data ?: return@registerForActivityResult
                val streamingDataPoTokenResult = data.getStringExtra("streaming_potoken") ?: ""
                val playerRequestPoTokenResult = data.getStringExtra("player_potoken") ?: ""
                val subsRequestPoTokenResult = data.getStringExtra("subs_potoken") ?: ""
                val visitorDataResult = data.getStringExtra("visitor_data") ?: ""

                val conf = webConf ?: return@registerForActivityResult
                configuration.remove(conf)
                conf.poTokens.clear()
                conf.poTokens.add(YoutubePoTokenItem("gvs", streamingDataPoTokenResult))
                conf.poTokens.add(YoutubePoTokenItem("player", playerRequestPoTokenResult))
                conf.poTokens.add(YoutubePoTokenItem("subs", subsRequestPoTokenResult))
                conf.visitorData = visitorDataResult

                configuration.add(conf)
                setValues(conf)
                preferences.edit(commit = true) {
                    putString(
                        "youtube_generated_po_tokens",
                        Gson().toJson(configuration).toString()
                    )
                }
            }
        }

        RuntimeManager.getInstance().assertInit()
        initCoordinatedPolicy()
        initBGUtils()
        initWeb()
    }

    @Composable
    override fun Content() {
        GenerateYoutubePoTokensScreen(
            policy = PolicySectionState(
                statusText = if (regenerating) {
                    stringResource(R.string.po_token_regenerating)
                } else {
                    statusText
                },
                warningText = warningText,
                policy = policy,
                regenerating = regenerating,
            ),
            bgUtils = BgUtilsSectionState(
                enabled = bgUtilsEnabled,
                method = bgUtilsMethod,
                scriptAvailable = nodeInstalled,
            ),
            manual = ManualSectionState(
                enabled = manualEnabled,
                gvs = webGvs,
                player = webPlayer,
                subs = webSubs,
                visitorData = webVisitorData,
                playerClients = webPlayerClients,
                useVisitorData = useVisitorData,
            ),
            onPolicyChange = { chosen ->
                val ctx = context ?: return@GenerateYoutubePoTokensScreen
                PoTokenCoordinator.setPolicy(ctx, chosen)
                renderStatus()
            },
            onRegenerate = { regenerateCoordinated() },
            onBgUtilsToggle = { enabled -> updateBgUtilsEnabled(enabled) },
            onBgUtilsMethodChange = { method -> updateBgUtilsMethod(method) },
            onRedownloadBgUtils = { redownloadBgUtils() },
            onManualToggle = { enabled -> updateManualEnabled(enabled) },
            onCopy = { text -> UiUtil.copyToClipboard(text, requireActivity()) },
            onPlayerClientsClick = { showPlayerClientsDialog() },
            onRegenerateWeb = { showGenerateDialog() },
            onUseVisitorDataChange = { checked -> updateUseVisitorData(checked) },
        )

        if (showUrlSheet) {
            GeneratePoTokenUrlSheet(
                url = urlInput,
                onUrlChange = { urlInput = it },
                onRegenerate = {
                    showUrlSheet = false
                    launchWebPoToken(noAuth = true)
                },
                onDismiss = { showUrlSheet = false },
            )
        }
    }

    // ---------------------------------------------------------------- coordination card

    private fun initCoordinatedPolicy() {
        renderStatus()
    }

    private fun renderStatus() {
        val ctx = context ?: return
        val snapshot = PoTokenCoordinator.getStatusSnapshot(ctx)
        policy = snapshot.policy

        val sourceLabel = when (snapshot.activeSource) {
            PoTokenSource.SESSION_WEBVIEW -> getString(R.string.po_token_source_session)
            PoTokenSource.ANONYMOUS_WEBVIEW -> getString(R.string.po_token_source_anonymous)
            else -> getString(R.string.po_token_source_manual)
        }
        val sessionLabel = if (snapshot.signedIn) {
            getString(R.string.po_token_session_signed_in)
        } else {
            getString(R.string.po_token_session_signed_out)
        }
        val visitorMasked = snapshot.maskedVisitorData
            ?: PoTokenFormatting.maskTokenTail(
                YouTubeSessionEngine.getCachedSessionVisitorData(ctx)
            )
        val baseStatus = getString(
            R.string.po_token_status_format,
            sourceLabel,
            visitorMasked,
            sessionLabel
        )
        val expirySuffix = snapshot.expiresAtMs?.let { expiresAt ->
            val remainingMin = ((expiresAt - System.currentTimeMillis()) / 60_000L).coerceAtLeast(0L)
            "\n" + getString(R.string.po_token_expires_at, remainingMin)
        } ?: ""
        statusText = baseStatus + expirySuffix

        warningText = when {
            snapshot.policy == PoTokenPolicy.SESSION_ONLY && !snapshot.signedIn ->
                getString(R.string.po_token_signed_out_warning)

            !snapshot.message.isNullOrBlank() -> snapshot.message

            else -> null
        }
    }

    private fun regenerateCoordinated() {
        val ctx = context ?: return
        regenerating = true
        statusText = getString(R.string.po_token_regenerating)
        lifecycleScope.launch {
            val bundle = withContext(Dispatchers.IO) {
                PoTokenCoordinator.regenerateNow(ctx)
            }
            if (!isAdded) return@launch
            regenerating = false
            renderStatus()
            Toast.makeText(
                requireContext(),
                if (bundle != null) {
                    R.string.po_token_regenerate_success
                } else {
                    R.string.po_token_regenerate_failed
                },
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    // ---------------------------------------------------------------- BgUtils card

    private fun initBGUtils() {
        bgUtilsEnabled = preferences.getBoolean("use_bgutils_potoken_generator", false)
        bgUtilsMethod = preferences.getString("bgutils_potoken_method", "server") ?: "server"
        denoInstalled = RuntimeManager.getInstance().denoLocation.isAvailable
        nodeInstalled = RuntimeManager.getInstance().nodeLocation.isAvailable
    }

    private fun updateBgUtilsEnabled(enabled: Boolean) {
        if (enabled && !denoInstalled) {
            bgUtilsEnabled = false
            preferences.edit(commit = true) {
                putBoolean("use_bgutils_potoken_generator", false)
            }
            snack(getString(R.string.please_install_package, "Deno"))
            return
        }

        bgUtilsEnabled = enabled
        preferences.edit(commit = true) {
            putBoolean("use_bgutils_potoken_generator", enabled)
        }

        if (!enabled) {
            BgUtilsPoTokenGeneratorUtil.stopServer(requireContext())
            return
        }

        val builder = MaterialAlertDialogBuilder(requireContext())
        builder.setTitle("BgUtils POT Provider")
        builder.setMessage(getString(R.string.bgutils_pot_generation_script_info))
        builder.setPositiveButton(R.string.ok) { di: DialogInterface?, _: Int ->
            di?.dismiss()
            runBgUtilsServerWithProgress()
        }
        builder.setCancelable(false)
        builder.setNegativeButton(getString(R.string.cancel)) { dialogInterface: DialogInterface, _: Int ->
            bgUtilsEnabled = false
            preferences.edit(commit = true) {
                putBoolean("use_bgutils_potoken_generator", false)
            }
            dialogInterface.cancel()
        }
        builder.create().show()
    }

    private fun runBgUtilsServerWithProgress() {
        val builder = MaterialAlertDialogBuilder(requireContext())
        builder.setTitle("BgUtils POT Provider")
        builder.setMessage(getString(R.string.please_wait))
        builder.setCancelable(false)

        val dialog = builder.create()
        dialog.show()

        lifecycleScope.launch {
            val resp = withContext(Dispatchers.IO) {
                BgUtilsPoTokenGeneratorUtil.runServer(requireContext()) { progress ->
                    lifecycleScope.launch {
                        withContext(Dispatchers.Main) {
                            dialog.setMessage(progress)
                        }
                    }
                }
            }

            resp.onSuccess {
                dialog.dismiss()
            }
            resp.onFailure { error ->
                dialog.dismiss()
                view?.let {
                    Snackbar.make(it, error.message ?: "", Snackbar.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun updateBgUtilsMethod(method: String) {
        if (method == "generation_script") {
            if (!nodeInstalled) {
                snack(getString(R.string.please_install_package, "NodeJS"))
                applyBgUtilsMethod("server")
                return
            }
            applyBgUtilsMethod("generation_script")
            BgUtilsPoTokenGeneratorUtil.stopServer(requireContext())
        } else {
            applyBgUtilsMethod("server")
            lifecycleScope.launch {
                withContext(Dispatchers.IO) {
                    BgUtilsPoTokenGeneratorUtil.runServer(requireContext()) {}
                }
            }
        }
    }

    private fun applyBgUtilsMethod(method: String) {
        bgUtilsMethod = method
        preferences.edit(commit = true) {
            putString("bgutils_potoken_method", method)
        }
    }

    private fun redownloadBgUtils() {
        val builder = MaterialAlertDialogBuilder(requireContext())
        builder.setTitle("BgUtils POT Provider")
        builder.setMessage(getString(R.string.downloading_update))
        builder.setCancelable(false)

        val dialog = builder.create()
        dialog.show()

        lifecycleScope.launch {
            val resp = withContext(Dispatchers.IO) {
                BgUtilsPoTokenGeneratorUtil.downloadFiles(
                    requireContext(),
                    runServerAfterwards = bgUtilsMethod == "server"
                ) { progress ->
                    lifecycleScope.launch {
                        withContext(Dispatchers.Main) {
                            dialog.setMessage(progress)
                        }
                    }
                }
            }

            resp.onSuccess {
                dialog.dismiss()
            }
            resp.onFailure { error ->
                dialog.dismiss()
                view?.let {
                    Snackbar.make(it, error.message ?: "", Snackbar.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun snack(message: CharSequence) {
        Snackbar.make(
            requireActivity().findViewById(android.R.id.content),
            message,
            Snackbar.LENGTH_SHORT
        ).show()
    }

    // ---------------------------------------------------------------- manual web generator

    private fun initWeb() {
        val confTmp = configuration.find { it.clients.any { client -> client.contains("web") } }
        val conf = confTmp ?: YoutubeGeneratePoTokenItem(
            false,
            mutableListOf("mweb"),
            mutableListOf(),
            "",
            false
        )
        webConf = conf

        manualEnabled = conf.enabled
        useVisitorData = conf.useVisitorData
        setValues(conf)
    }

    private fun setValues(conf: YoutubeGeneratePoTokenItem) {
        webGvs = conf.poTokens.find { it.context == "gvs" }?.token ?: ""
        webPlayer = conf.poTokens.find { it.context == "player" }?.token ?: ""
        webSubs = conf.poTokens.find { it.context == "subs" }?.token ?: ""
        webVisitorData = conf.visitorData
        webPlayerClients = conf.clients.joinToString(", ")
    }

    private fun updateManualEnabled(enabled: Boolean) {
        val conf = webConf ?: return
        manualEnabled = enabled

        configuration.remove(conf)
        conf.enabled = enabled
        configuration.add(conf)
        preferences.edit(commit = true) {
            putString(
                "youtube_generated_po_tokens",
                Gson().toJson(configuration).toString()
            )
        }

        if (enabled && conf.poTokens.isEmpty()) {
            showGenerateDialog()
        }
    }

    private fun updateUseVisitorData(checked: Boolean) {
        val conf = webConf ?: return
        useVisitorData = checked

        configuration.remove(conf)
        conf.useVisitorData = checked
        configuration.add(conf)
        preferences.edit().putString(
            "youtube_generated_po_tokens",
            Gson().toJson(configuration).toString()
        ).apply()
    }

    private fun showPlayerClientsDialog() {
        val conf = webConf ?: return
        val webClients = requireContext().resources.getStringArray(R.array.web_player_clients)
        val selectedItems = webClients.map { conf.clients.contains(it) }.toBooleanArray()

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.player_client))
            .setMultiChoiceItems(webClients, selectedItems) { _, which, isChecked ->
                selectedItems[which] = isChecked
            }
            .setPositiveButton(R.string.ok) { _, _ ->
                val newValues = webClients
                    .filterIndexed { index, _ -> selectedItems[index] }
                    .toMutableSet()

                if (newValues.isNotEmpty()) {
                    configuration.remove(conf)
                    conf.clients.clear()
                    conf.clients.addAll(newValues)
                    configuration.add(conf)
                    preferences.edit().putString(
                        "youtube_generated_po_tokens",
                        Gson().toJson(configuration).toString()
                    ).apply()
                    setValues(conf)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showGenerateDialog() {
        val dialog = MaterialAlertDialogBuilder(requireContext())
        dialog.setTitle(getString(R.string.generate_potokens))
        dialog.setMessage(getString(R.string.potoken_guide))
        dialog.setNegativeButton(getString(R.string.no_auth)) { _: DialogInterface?, _: Int ->
            urlInput = SAMPLE_VIDEO_URL
            showUrlSheet = true
        }
        dialog.setPositiveButton(getString(R.string.auth)) { _: DialogInterface?, _: Int ->
            launchWebPoToken(noAuth = false)
        }
        dialog.setNeutralButton(getString(R.string.cancel)) { dialogInterface: DialogInterface, _: Int ->
            dialogInterface.dismiss()
        }
        dialog.show()
    }

    private fun launchWebPoToken(noAuth: Boolean) {
        val intent = Intent(requireContext(), PoTokenWebViewLoginActivity::class.java)
        if (noAuth) {
            intent.putExtra("url", "https://www.youtube.com")
            intent.putExtra(
                "redirect_url",
                "https://www.youtube.com/watch?v=${urlInput.getIDFromYoutubeURL()}"
            )
        } else {
            intent.putExtra("url", "https://youtube.com/signin")
        }
        intent.putExtra("no_auth", noAuth)
        webPoTokenResultLauncher.launch(intent)
    }

    companion object {
        private const val SAMPLE_VIDEO_URL = "https://youtu.be/aqz-KE-bpKQ" // Big Buck Bunny
    }
}
