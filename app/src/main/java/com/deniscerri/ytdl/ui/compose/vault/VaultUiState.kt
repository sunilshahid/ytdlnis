package com.deniscerri.ytdl.ui.compose.vault

import com.deniscerri.ytdl.ui.play.device.DeviceMedia
import com.deniscerri.ytdl.vault.VaultField
import com.deniscerri.ytdl.vault.VaultLayoutMode
import com.deniscerri.ytdl.vault.VaultPickerField
import com.deniscerri.ytdl.vault.VaultSettings
import com.deniscerri.ytdl.vault.VaultSortBy
import com.deniscerri.ytdl.vault.VaultUiItem

enum class VaultStage {
    SETUP,
    UNLOCK,
    GALLERY,
    SETTINGS,
    PICKER
}

data class VaultSettingsData(
    val biometricEnabled: Boolean = false,
    val biometricAvailable: Boolean = false,
    val biometricInvalidated: Boolean = false,
    val passwordManagerEnabled: Boolean = false,
    val stealthEnabled: Boolean = false,
    val stealthPin: String = "",
    val decoyConfigured: Boolean = false,
    val autoWipeThreshold: Int = 10,
    val keepOpenInSession: Boolean = false,
)

data class VaultPickerState(
    val hasPermission: Boolean = true,
    val isLoading: Boolean = false,
    val allVideos: List<DeviceMedia> = emptyList(),
    val visibleVideos: List<DeviceMedia> = emptyList(),
    val selectedIds: Set<String> = emptySet(),
    val searchQuery: String = "",
    val isGridMode: Boolean = true,
    val visibleFields: Set<VaultPickerField> = VaultSettings.DEFAULT_PICKER_VISIBLE_FIELDS,
)

data class VaultUiState(
    val stage: VaultStage = VaultStage.UNLOCK,
    val isInitialized: Boolean = false,
    val isUnlocked: Boolean = false,
    val isDecoy: Boolean = false,
    val setupOnlyMode: Boolean = false,
    // Gallery state
    val allItems: List<VaultUiItem> = emptyList(),
    val visibleItems: List<VaultUiItem> = emptyList(),
    val selectedIds: Set<String> = emptySet(),
    val isSelectionMode: Boolean = false,
    val searchQuery: String = "",
    val isSearchActive: Boolean = false,
    val layoutMode: VaultLayoutMode = VaultLayoutMode.GRID,
    val visibleFields: Set<VaultField> = VaultSettings.DEFAULT_VISIBLE_FIELDS,
    val sortBy: VaultSortBy = VaultSortBy.DATE,
    val sortDescending: Boolean = true,
    // Unlock / Setup feedback
    val setupError: String? = null,
    val unlockError: String? = null,
    val lockoutRemainingSeconds: Int? = null,
    val canBiometricUnlock: Boolean = false,
    // Busy overlay
    val isBusy: Boolean = false,
    val busyLabel: String = "",
    val isBlackout: Boolean = false,
    // Sub-screens
    val settingsData: VaultSettingsData = VaultSettingsData(),
    val pickerState: VaultPickerState = VaultPickerState(),
)
