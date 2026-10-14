package com.despeja.app.ui

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.despeja.app.MainViewModel
import com.despeja.app.Screen
import com.despeja.app.system.Permissions
import com.despeja.app.ui.screens.AppCacheScreen
import com.despeja.app.ui.screens.CategoryScreen
import com.despeja.app.ui.screens.HomeScreen
import com.despeja.app.ui.screens.ScanningScreen
import com.despeja.app.ui.screens.TrashScreen

@Composable
fun DespejaApp(vm: MainViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }

    // Al volver de Ajustes, comprobar de nuevo los permisos.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refresh() }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            vm.consumeMessage()
        }
    }

    val legacyPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { vm.refresh() }

    val requestStorage: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Permissions.openAllFilesAccess(context)
        } else {
            legacyPermissionLauncher.launch(Permissions.legacyPermissions)
        }
    }

    BackHandler(enabled = state.screen != Screen.Home) { vm.back() }

    val snackHost: @Composable () -> Unit = { SnackbarHost(snackbar) }

    when (val screen = state.screen) {
        Screen.Home -> HomeScreen(
            state = state,
            snackbarHost = snackHost,
            onRequestStorage = requestStorage,
            onScan = vm::startScan,
            onOpenCategory = { vm.open(Screen.CategoryDetail(it)) },
            onOpenAppCache = { vm.open(Screen.AppCache) },
            onOpenTrash = { vm.open(Screen.Trash) },
        )

        Screen.Scanning -> ScanningScreen(progress = state.progress, onCancel = vm::cancelScan)

        is Screen.CategoryDetail -> CategoryScreen(
            category = screen.category,
            result = state.result?.categories?.get(screen.category),
            selection = state.selections[screen.category].orEmpty(),
            working = state.working,
            snackbarHost = snackHost,
            onBack = vm::back,
            onToggle = { vm.toggle(screen.category, it) },
            onSelectAll = { vm.selectAll(screen.category, it) },
            onSelectRecommended = { vm.selectRecommended(screen.category) },
            onDelete = { useTrash -> vm.deleteSelected(screen.category, useTrash) },
        )

        Screen.AppCache -> AppCacheScreen(
            state = state,
            snackbarHost = snackHost,
            onBack = vm::back,
            onGrantUsage = { Permissions.openUsageAccess(context) },
            onOpenApp = { Permissions.openAppDetails(context, it) },
            onRefresh = vm::loadAppCaches,
            onCleared = vm::onAppCacheCleared,
        )

        Screen.Trash -> TrashScreen(
            entries = state.trash,
            working = state.working,
            snackbarHost = snackHost,
            onBack = vm::back,
            onRestore = vm::restore,
            onEmpty = vm::emptyTrash,
        )
    }
}
