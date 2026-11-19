package com.deniscerri.ytdl.ui.compose.vault

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deniscerri.ytdl.R

enum class VaultImportStage {
    DISCLOSURE,
    SETUP,
    UNLOCK,
    PROGRESS
}

data class VaultImportUiState(
    val stage: VaultImportStage = VaultImportStage.DISCLOSURE,
    val disclosureMessage: String = "",
    val setupError: String? = null,
    val unlockError: String? = null,
    val canBiometricUnlock: Boolean = false,
    val progressMessage: String = "",
    val isBlackout: Boolean = false,
)

@Composable
fun VaultImportScreen(
    state: VaultImportUiState,
    onContinueDisclosure: () -> Unit,
    onCancel: () -> Unit,
    onCreateVault: (password: String, confirm: String) -> Unit,
    onUnlockPassword: (password: String) -> Unit,
    onUnlockBiometric: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xDD000000))
    ) {
        AnimatedContent(
            targetState = state.stage,
            transitionSpec = { fadeIn().togetherWith(fadeOut()) },
            label = "import_stage_anim"
        ) { stage ->
            when (stage) {
                VaultImportStage.DISCLOSURE -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState()),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                        ) {
                            Column(modifier = Modifier.padding(24.dp)) {
                                Text(
                                    text = stringResource(R.string.vault_import_title),
                                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
                                )

                                Spacer(Modifier.height(12.dp))

                                Text(
                                    text = state.disclosureMessage,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Spacer(Modifier.height(24.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Spacer(Modifier.weight(1f))
                                    TextButton(onClick = onCancel) {
                                        Text(stringResource(R.string.cancel))
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Button(onClick = onContinueDisclosure) {
                                        Text(stringResource(R.string.vault_import_continue))
                                    }
                                }
                            }
                        }
                    }
                }

                VaultImportStage.SETUP -> {
                    VaultSetupContent(
                        errorMessage = state.setupError,
                        onCreateVault = onCreateVault,
                        onBackClick = onCancel
                    )
                }

                VaultImportStage.UNLOCK -> {
                    VaultUnlockContent(
                        errorMessage = state.unlockError,
                        lockoutSeconds = null,
                        canBiometricUnlock = state.canBiometricUnlock,
                        onUnlockPassword = onUnlockPassword,
                        onUnlockBiometric = onUnlockBiometric,
                        onBackClick = onCancel
                    )
                }

                VaultImportStage.PROGRESS -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
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
                                if (state.progressMessage.isNotBlank()) {
                                    Spacer(Modifier.height(20.dp))
                                    Text(
                                        text = state.progressMessage,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                        fontSize = 15.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (state.isBlackout) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            )
        }
    }
}
