package com.deniscerri.ytdl.ui.more.settings.updating

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.core.RuntimeManager
import com.deniscerri.ytdl.core.packages.PackageBase
import com.deniscerri.ytdl.database.models.PackageItem
import com.deniscerri.ytdl.ui.compose.host.ComposeFragment
import com.deniscerri.ytdl.ui.compose.updating.PackageReleaseSheetState
import com.deniscerri.ytdl.ui.compose.updating.PackageReleasesSheet
import com.deniscerri.ytdl.ui.compose.updating.PackageRowState
import com.deniscerri.ytdl.ui.compose.updating.PackagesScreen
import com.deniscerri.ytdl.ui.more.settings.SettingsActivity
import com.deniscerri.ytdl.util.ApkInstallUtil
import com.deniscerri.ytdl.util.UiUtil
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

/**
 * Packages list hosted by [SettingsActivity] (which owns the app bar, hence no top bar here).
 *
 * Presentation moved to [PackagesScreen] / [PackageReleasesSheet]. Install/uninstall launchers,
 * `UiUtil` update dialogs, the GitHub release fetch and the `RuntimeManager` re-init are unchanged.
 */
class PackagesFragment : ComposeFragment() {

    private lateinit var settingsActivity: SettingsActivity
    private lateinit var installLauncher: ActivityResultLauncher<Intent>

    private var packages by mutableStateOf<List<PackageRowState>>(emptyList())
    private var releaseSheet by mutableStateOf<PackageReleaseSheetState?>(null)

    private var tmpItem: PackageItem? = null
    private var tmpInstance: PackageBase? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installLauncher = ApkInstallUtil.registerInstallLauncher(this)

        settingsActivity = requireActivity() as SettingsActivity
        settingsActivity.changeTopAppbarTitle(getString(R.string.packages))

        RuntimeManager.getInstance().assertInit()
        refreshPackages()
    }

    override fun onResume() {
        super.onResume()
        refreshPackages()
    }

    @Composable
    override fun Content() {
        PackagesScreen(
            packages = packages,
            onPackageClick = { row -> openReleases(row) },
            onPackageLongClick = { row ->
                if (row.location.isAvailable && row.isDownloaded) {
                    deleteDownloadedVersion(row.item, row.version)
                }
            },
        )

        val sheet = releaseSheet
        if (sheet != null) {
            PackageReleasesSheet(
                state = sheet,
                onDismissRequest = { releaseSheet = null },
                onDownloadRelease = { release -> onDownloadReleaseClick(release) },
                onDeleteRelease = { release -> onDeleteReleaseClick(release) },
            )
        }
    }

    /** Replaces `PackagesAdapter`'s bind logic; a new list instance is what triggers recomposition. */
    private fun refreshPackages() {
        if (!isAdded) return

        packages = RuntimeManager.packages.map { item ->
            val instance = item.getInstance()
            val location = instance.location
            val version = if (location.isAvailable) {
                if (location.isDownloaded) instance.downloadedVersion else instance.bundledVersion
            } else {
                getString(R.string.not_installed)
            }

            PackageRowState(
                item = item,
                location = location,
                version = version,
                isDownloaded = location.isDownloaded,
                isBundled = location.isBundled,
            )
        }
    }

    /** Former `onCardClick`: open the release sheet and fetch the package's releases. */
    private fun openReleases(row: PackageRowState) {
        tmpItem = row.item
        releaseSheet = PackageReleaseSheetState(title = row.item.title)

        lifecycleScope.launch {
            val result = row.item.getInstance().getReleases()
            result.onFailure { failure ->
                Toast.makeText(
                    requireContext(),
                    failure.message ?: getString(R.string.errored),
                    Toast.LENGTH_SHORT,
                ).show()
            }

            val releases = result.getOrElse { listOf() }
            releaseSheet = releaseSheet?.copy(isLoading = false, releases = releases)
        }
    }

    private var uninstallLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (tmpInstance?.isPackageAppInstalled(requireContext()) == false) {
            val resp = tmpInstance?.uninstallDownloadedRuntimeDir(requireContext())
            resp?.onFailure {
                Snackbar.make(
                    requireView(),
                    it.message ?: getString(R.string.errored),
                    Snackbar.LENGTH_LONG,
                ).show()
            }
            resp?.onSuccess {
                releaseSheet = null
                RuntimeManager.reInit(requireContext())
                refreshPackages()
            }
        }
    }

    /** Former `onDeleteDownloadedPackageClick`. */
    private fun onDeleteReleaseClick(release: PackageBase.PackageRelease) {
        val packageItem = tmpItem ?: return
        deleteDownloadedVersion(packageItem, release.version)
    }

    /** Former `onDeleteDownloadedVersion`. */
    private fun deleteDownloadedVersion(item: PackageItem, version: String?) {
        UiUtil.showGenericDeleteDialog(
            requireContext(),
            "${item.title} (${version})"
        ) {
            val instance = item.getInstance()
            tmpInstance = instance
            val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${instance.apkPackage}")
            }
            uninstallLauncher.launch(intent)
        }
    }

    /** Former `onDownloadReleaseClick`. */
    private fun onDownloadReleaseClick(release: PackageBase.PackageRelease) {
        releaseSheet = null

        val packageItem = tmpItem ?: return
        UiUtil.showNewReleaseUpdateDialog(
            release,
            packageItem,
            requireActivity(),
            viewLifecycleOwner,
            requireView(),
            null,
            installLauncher
        ) { result ->
            result.onSuccess {
                RuntimeManager.reInit(requireContext())
                refreshPackages()
            }.onFailure { f ->
                Snackbar.make(
                    requireActivity().findViewById(R.id.frame_layout),
                    f.message ?: "",
                    Snackbar.LENGTH_LONG
                ).show()
            }
        }
    }
}
