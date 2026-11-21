package com.deniscerri.ytdl.ui.more.settings

import androidx.navigation.NavController
import androidx.navigation.NavGraph
import androidx.navigation.createGraph
import androidx.navigation.fragment.fragment
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.ui.more.settings.advanced.AdvancedSettingsFragment
import com.deniscerri.ytdl.ui.more.settings.advanced.YoutubePlayerClientFragment
import com.deniscerri.ytdl.ui.more.settings.advanced.generateyoutubepotokens.GenerateYoutubePoTokensFragment
import com.deniscerri.ytdl.ui.more.settings.appearance.AppearanceSettingsFragment
import com.deniscerri.ytdl.ui.more.settings.downloading.DownloadSettingsFragment
import com.deniscerri.ytdl.ui.more.settings.folder.FolderSettingsFragment
import com.deniscerri.ytdl.ui.more.settings.folder.temporary.TemporaryFilesFragment
import com.deniscerri.ytdl.ui.more.settings.general.GeneralSettingsFragment
import com.deniscerri.ytdl.ui.more.settings.player.PlayerSettingsFragment
import com.deniscerri.ytdl.ui.more.settings.processing.ProcessingSettingsFragment
import com.deniscerri.ytdl.ui.more.settings.updating.PackagesFragment
import com.deniscerri.ytdl.ui.more.settings.updating.UpdateSettingsFragment
import com.deniscerri.ytdl.ui.more.settings.updating.app.AppUpdateSettingsFragment
import com.deniscerri.ytdl.ui.more.settings.updating.app.ChangeLogFragment
import com.deniscerri.ytdl.ui.more.settings.updating.ytdlp.YtdlpUpdateSettingsFragment

fun NavController.buildSettingsNavGraph(): NavGraph {
    val graph = createGraph(startDestination = R.id.mainSettingsFragment) {
        fragment<MainSettingsFragment>(R.id.mainSettingsFragment) {
            label = "MainSettingsFragment"
            action(R.id.action_mainSettingsFragment_to_appearanceSettingsFragment) {
                destinationId = R.id.appearanceSettingsFragment
            }
            action(R.id.action_mainSettingsFragment_to_generalSettingsFragment) {
                destinationId = R.id.generalSettingsFragment
            }
            action(R.id.action_mainSettingsFragment_to_playerSettingsFragment) {
                destinationId = R.id.playerSettingsFragment
            }
            action(R.id.action_mainSettingsFragment_to_folderSettingsFragment) {
                destinationId = R.id.folderSettingsFragment
            }
            action(R.id.action_mainSettingsFragment_to_downloadSettingsFragment) {
                destinationId = R.id.downloadSettingsFragment
            }
            action(R.id.action_mainSettingsFragment_to_processingSettingsFragment) {
                destinationId = R.id.processingSettingsFragment
            }
            action(R.id.action_mainSettingsFragment_to_updateSettingsFragment) {
                destinationId = R.id.updateSettingsFragment
            }
            action(R.id.action_mainSettingsFragment_to_advancedSettingsFragment) {
                destinationId = R.id.advancedSettingsFragment
            }
        }
        fragment<AppearanceSettingsFragment>(R.id.appearanceSettingsFragment) {
            label = "AppearanceSettingsFragment"
        }
        fragment<GeneralSettingsFragment>(R.id.generalSettingsFragment) {
            label = "GeneralSettingsFragment"
        }
        fragment<PlayerSettingsFragment>(R.id.playerSettingsFragment) {
            label = "PlayerSettingsFragment"
        }
        fragment<ProcessingSettingsFragment>(R.id.processingSettingsFragment) {
            label = "ProcessingSettingsFragment"
        }
        fragment<FolderSettingsFragment>(R.id.folderSettingsFragment) {
            label = "FolderSettingsFragment"
            action(R.id.action_folderSettingsFragment_to_temporaryFilesFragment) {
                destinationId = R.id.temporaryFilesFragment
            }
        }
        fragment<TemporaryFilesFragment>(R.id.temporaryFilesFragment) {
            label = "TemporaryFilesFragment"
        }
        fragment<UpdateSettingsFragment>(R.id.updateSettingsFragment) {
            label = "UpdateSettingsFragment"
            action(R.id.action_updateSettingsFragment_to_changeLogFragment) {
                destinationId = R.id.changeLogFragment
            }
            action(R.id.action_updateSettingsFragment_to_packagesFragment) {
                destinationId = R.id.packagesFragment
            }
            action(R.id.action_updateSettingsFragment_to_ytdlpUpdateSettingsFragment) {
                destinationId = R.id.ytdlpUpdateSettingsFragment
            }
            action(R.id.action_updateSettingsFragment_to_appUpdateSettingsFragment) {
                destinationId = R.id.appUpdateSettingsFragment
            }
        }
        fragment<YtdlpUpdateSettingsFragment>(R.id.ytdlpUpdateSettingsFragment) {
            label = "YtdlpUpdateSettingsFragment"
        }
        fragment<AppUpdateSettingsFragment>(R.id.appUpdateSettingsFragment) {
            label = "AppUpdateSettingsFragment"
        }
        fragment<DownloadSettingsFragment>(R.id.downloadSettingsFragment) {
            label = "DownloadSettingsFragment"
        }
        fragment<AdvancedSettingsFragment>(R.id.advancedSettingsFragment) {
            label = "AdvancedSettingsFragment"
            action(R.id.action_advancedSettingsFragment_to_youtubePlayerClientFragment) {
                destinationId = R.id.youtubePlayerClientFragment
            }
            action(R.id.action_advancedSettingsFragment_to_generateYoutubePoTokensFragment) {
                destinationId = R.id.generateYoutubePoTokensFragment
            }
        }
        fragment<YoutubePlayerClientFragment>(R.id.youtubePlayerClientFragment) {
            label = "YoutubePlayerClientFragment"
        }
        fragment<ChangeLogFragment>(R.id.changeLogFragment) {
            label = "ChangeLogFragment"
        }
        fragment<GenerateYoutubePoTokensFragment>(R.id.generateYoutubePoTokensFragment) {
            label = "GenerateYoutubePoTokensFragment"
        }
        fragment<PackagesFragment>(R.id.packagesFragment) {
            label = "PackagesFragment"
        }
    }
    this.graph = graph
    return graph
}
