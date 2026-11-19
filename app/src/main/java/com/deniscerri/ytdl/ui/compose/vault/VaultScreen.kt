package com.deniscerri.ytdl.ui.compose.vault

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.deniscerri.ytdl.ui.play.device.DeviceMedia
import com.deniscerri.ytdl.ui.play.device.DeviceThumbnailLoader
import com.deniscerri.ytdl.vault.VaultUiItem

@Composable
fun VaultScreen(
    state: VaultUiState,
    pickerThumbnailLoader: DeviceThumbnailLoader?,
    onBackClick: () -> Unit,
    // Setup
    onCreateVault: (password: String, confirm: String) -> Unit,
    // Unlock
    onUnlockPassword: (password: String) -> Unit,
    onUnlockBiometric: () -> Unit,
    // Gallery
    onItemClick: (VaultUiItem) -> Unit,
    onItemLongClick: (VaultUiItem) -> Unit,
    onItemRename: (VaultUiItem) -> Unit,
    onItemExport: (VaultUiItem) -> Unit,
    onItemDelete: (VaultUiItem) -> Unit,
    onToggleSelectAll: () -> Unit,
    onExitSelectionMode: () -> Unit,
    onDeleteSelected: () -> Unit,
    onExportSelected: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onToggleSearch: () -> Unit,
    onOpenOptionsSheet: () -> Unit,
    onOpenSettings: () -> Unit,
    onLockVault: () -> Unit,
    onOpenPicker: () -> Unit,
    // Settings
    onCloseSettings: () -> Unit,
    onToggleBiometric: (Boolean) -> Unit,
    onTogglePasswordManager: (Boolean) -> Unit,
    onToggleStealth: (Boolean) -> Unit,
    onSaveStealthPin: (String) -> Unit,
    onSetupDecoy: () -> Unit,
    onClearDecoy: () -> Unit,
    onChangePassword: () -> Unit,
    onSelectAutoWipe: () -> Unit,
    onToggleKeepOpen: (Boolean) -> Unit,
    onWipeVault: () -> Unit,
    // Picker
    onClosePicker: () -> Unit,
    onPickerToggleItem: (DeviceMedia) -> Unit,
    onPickerToggleSelectAll: () -> Unit,
    onPickerImportSelected: () -> Unit,
    onPickerGrantPermission: () -> Unit,
    onPickerSearchChange: (String) -> Unit,
    onPickerToggleLayout: () -> Unit,
    onPickerOpenOptions: () -> Unit,
    onPickerBrowseSystem: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = state.stage,
            transitionSpec = {
                when {
                    targetState == VaultStage.SETTINGS || targetState == VaultStage.PICKER -> {
                        (slideInHorizontally { width -> width } + fadeIn()).togetherWith(
                            slideOutHorizontally { width -> -width } + fadeOut()
                        )
                    }
                    initialState == VaultStage.SETTINGS || initialState == VaultStage.PICKER -> {
                        (slideInHorizontally { width -> -width } + fadeIn()).togetherWith(
                            slideOutHorizontally { width -> width } + fadeOut()
                        )
                    }
                    else -> fadeIn().togetherWith(fadeOut())
                }
            },
            label = "vault_stage_anim"
        ) { stage ->
            when (stage) {
                VaultStage.SETUP -> {
                    VaultSetupContent(
                        errorMessage = state.setupError,
                        onCreateVault = onCreateVault,
                        onBackClick = onBackClick
                    )
                }

                VaultStage.UNLOCK -> {
                    VaultUnlockContent(
                        errorMessage = state.unlockError,
                        lockoutSeconds = state.lockoutRemainingSeconds,
                        canBiometricUnlock = state.canBiometricUnlock,
                        onUnlockPassword = onUnlockPassword,
                        onUnlockBiometric = onUnlockBiometric,
                        onBackClick = onBackClick
                    )
                }

                VaultStage.GALLERY -> {
                    VaultGalleryContent(
                        items = state.visibleItems,
                        selectedIds = state.selectedIds,
                        isSelectionMode = state.isSelectionMode,
                        isDecoy = state.isDecoy,
                        searchQuery = state.searchQuery,
                        isSearchActive = state.isSearchActive,
                        layoutMode = state.layoutMode,
                        visibleFields = state.visibleFields,
                        onItemClick = onItemClick,
                        onItemLongClick = onItemLongClick,
                        onItemRename = onItemRename,
                        onItemExport = onItemExport,
                        onItemDelete = onItemDelete,
                        onToggleSelectAll = onToggleSelectAll,
                        onExitSelectionMode = onExitSelectionMode,
                        onDeleteSelected = onDeleteSelected,
                        onExportSelected = onExportSelected,
                        onSearchQueryChange = onSearchQueryChange,
                        onToggleSearch = onToggleSearch,
                        onOpenOptionsSheet = onOpenOptionsSheet,
                        onOpenSettings = onOpenSettings,
                        onLockVault = onLockVault,
                        onOpenPicker = onOpenPicker,
                        onBackClick = onBackClick
                    )
                }

                VaultStage.SETTINGS -> {
                    VaultSettingsContent(
                        settings = state.settingsData,
                        onToggleBiometric = onToggleBiometric,
                        onTogglePasswordManager = onTogglePasswordManager,
                        onToggleStealth = onToggleStealth,
                        onSaveStealthPin = onSaveStealthPin,
                        onSetupDecoy = onSetupDecoy,
                        onClearDecoy = onClearDecoy,
                        onChangePassword = onChangePassword,
                        onSelectAutoWipe = onSelectAutoWipe,
                        onToggleKeepOpen = onToggleKeepOpen,
                        onWipeVault = onWipeVault,
                        onBackClick = onCloseSettings
                    )
                }

                VaultStage.PICKER -> {
                    VaultPickerContent(
                        state = state.pickerState,
                        thumbnailLoader = pickerThumbnailLoader,
                        onToggleItem = onPickerToggleItem,
                        onToggleSelectAll = onPickerToggleSelectAll,
                        onImportSelected = onPickerImportSelected,
                        onGrantPermission = onPickerGrantPermission,
                        onSearchChange = onPickerSearchChange,
                        onToggleLayout = onPickerToggleLayout,
                        onOpenOptions = onPickerOpenOptions,
                        onBrowseSystem = onPickerBrowseSystem,
                        onBackClick = onClosePicker
                    )
                }
            }
        }

        // Busy Modal Dialog
        if (state.isBusy) {
            Dialog(
                onDismissRequest = { /* Modal progress cannot be dismissed */ },
                properties = DialogProperties(
                    dismissOnBackPress = false,
                    dismissOnClickOutside = false
                )
            ) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    tonalElevation = 6.dp
                ) {
                    Column(
                        modifier = Modifier.padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(44.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 3.5.dp
                        )
                        if (state.busyLabel.isNotBlank()) {
                            Spacer(Modifier.height(20.dp))
                            Text(
                                text = state.busyLabel,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        // Blackout Veil
        if (state.isBlackout) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            )
        }
    }
}
