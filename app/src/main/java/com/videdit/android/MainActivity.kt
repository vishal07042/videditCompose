package com.videdit.android

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.videdit.android.ui.EditorScreen
import com.videdit.android.ui.EditorViewModel
import com.videdit.android.ui.theme.VidEditTheme

class MainActivity : ComponentActivity() {
    private val viewModel: EditorViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val state by viewModel.uiState.collectAsState()
            val launcher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.GetContent(),
            ) { uri ->
                if (uri != null) viewModel.onVideoPicked(uri)
            }
            val writePermissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission(),
            ) {
                viewModel.onPermissionStatusUpdated()
                if (!viewModel.needsManageAllFilesAccess() && !viewModel.needsLegacyWritePermission()) {
                    viewModel.exportVideo()
                }
            }
            val manageAllFilesLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.StartActivityForResult(),
            ) {
                viewModel.onPermissionStatusUpdated()
                if (!viewModel.needsManageAllFilesAccess() && !viewModel.needsLegacyWritePermission()) {
                    viewModel.exportVideo()
                }
            }

            VidEditTheme {
                EditorScreen(
                    state = state,
                    onPickVideo = { launcher.launch("video/*") },
                    onLanguageChange = viewModel::setLanguage,
                    onTranscribe = viewModel::transcribe,
                    onWordDelete = viewModel::toggleWordDeletion,
                    onUndoDelete = viewModel::undoLastDelete,
                    onClearDelete = viewModel::clearDeletedSegments,
                    onExport = {
                        when {
                            viewModel.needsManageAllFilesAccess() -> {
                                val intent = Intent(
                                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                    Uri.parse("package:$packageName"),
                                )
                                manageAllFilesLauncher.launch(intent)
                            }
                            viewModel.needsLegacyWritePermission() -> {
                                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                                    writePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                                } else {
                                    viewModel.exportVideo()
                                }
                            }
                            else -> viewModel.exportVideo()
                        }
                    },
                )
            }
        }
    }
}
