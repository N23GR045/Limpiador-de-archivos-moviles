package com.despeja.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.despeja.app.model.AppCacheInfo
import com.despeja.app.model.Category
import com.despeja.app.model.ScanResult
import com.despeja.app.model.StorageSummary
import com.despeja.app.model.TrashEntry
import com.despeja.app.scanner.HashCache
import com.despeja.app.scanner.StorageScanner
import com.despeja.app.system.AppCaches
import com.despeja.app.system.FileOps
import com.despeja.app.system.Permissions
import com.despeja.app.system.StorageInfo
import com.despeja.app.system.TrashManager
import com.despeja.app.system.Updater
import com.despeja.app.ui.formatSize
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface Screen {
    data object Home : Screen
    data object Scanning : Screen
    data class CategoryDetail(val category: Category) : Screen
    data object AppCache : Screen
    data object Trash : Screen
}

data class UiState(
    val screen: Screen = Screen.Home,
    val hasStorageAccess: Boolean = false,
    val hasUsageAccess: Boolean = false,
    val storage: StorageSummary? = null,
    val progress: StorageScanner.Progress? = null,
    val result: ScanResult? = null,
    val selections: Map<Category, Set<String>> = emptyMap(),
    val appCaches: List<AppCacheInfo> = emptyList(),
    val loadingAppCaches: Boolean = false,
    val trash: List<TrashEntry> = emptyList(),
    val working: Boolean = false,
    val message: String? = null,
    val update: Updater.Result? = null,
    val checkingUpdate: Boolean = false,
    val showUpdateDialog: Boolean = false,
) {
    val scanning: Boolean get() = screen == Screen.Scanning
    val appCacheTotal: Long get() = appCaches.sumOf { it.cacheBytes }
    val trashSize: Long get() = trash.sumOf { it.size }
}

class MainViewModel(private val app: Application) : AndroidViewModel(app) {

    private val hashCache = HashCache(app)
    private val scanner = StorageScanner(app, hashCache)
    private val trashManager = TrashManager(app)
    private val fileOps = FileOps(app, trashManager)

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var scanJob: Job? = null

    init {
        refresh()
        checkForUpdates(manual = false)
    }

    /** Se llama al arrancar y cada vez que la app vuelve a primer plano (p. ej. desde Ajustes). */
    fun refresh() {
        val hadUsage = _state.value.hasUsageAccess
        _state.update {
            it.copy(
                hasStorageAccess = Permissions.hasStorageAccess(app),
                hasUsageAccess = Permissions.hasUsageAccess(app),
                storage = runCatching { StorageInfo.summary() }.getOrNull(),
            )
        }
        val s = _state.value
        if (s.hasStorageAccess) loadTrash(purge = true)
        if (s.hasUsageAccess && (!hadUsage || s.appCaches.isEmpty())) loadAppCaches()
    }

    // ---------- Navegación ----------

    fun open(screen: Screen) = _state.update { it.copy(screen = screen) }

    fun back() {
        when (_state.value.screen) {
            Screen.Scanning -> cancelScan()
            else -> open(Screen.Home)
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    // ---------- Escaneo ----------

    fun startScan() {
        if (scanJob?.isActive == true) return
        if (!_state.value.hasStorageAccess) {
            _state.update { it.copy(message = "Primero concede acceso a los archivos") }
            return
        }
        _state.update { it.copy(screen = Screen.Scanning, progress = null) }
        scanJob = viewModelScope.launch {
            try {
                val result = scanner.scan { p -> _state.update { it.copy(progress = p) } }
                _state.update {
                    it.copy(
                        result = result,
                        selections = result.categories.mapValues { (_, c) -> c.defaultSelection },
                        screen = Screen.Home,
                        message = "Análisis completado: ${result.scannedFiles} archivos revisados",
                    )
                }
                if (_state.value.hasUsageAccess) loadAppCaches()
            } catch (e: CancellationException) {
                _state.update { it.copy(screen = Screen.Home, message = "Análisis cancelado") }
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(screen = Screen.Home, message = "Error al analizar: ${e.message}") }
            }
        }
    }

    fun cancelScan() {
        scanJob?.cancel()
    }

    // ---------- Selección ----------

    fun toggle(category: Category, path: String) = _state.update { s ->
        val current = s.selections[category].orEmpty()
        val next = if (path in current) current - path else current + path
        s.copy(selections = s.selections + (category to next))
    }

    /** Marca o desmarca solo los archivos indicados (los que se ven con el filtro actual). */
    fun setSelected(category: Category, paths: Collection<String>, selected: Boolean) = _state.update { s ->
        val current = s.selections[category].orEmpty()
        val next = if (selected) current + paths else current - paths.toSet()
        s.copy(selections = s.selections + (category to next))
    }

    /** Aplica la selección recomendada solo dentro de los archivos indicados. */
    fun selectRecommended(category: Category, paths: Collection<String>) = _state.update { s ->
        val scope = paths.toSet()
        val rec = s.result?.categories?.get(category)?.defaultSelection.orEmpty()
        val current = s.selections[category].orEmpty()
        val next = (current - scope) + rec.intersect(scope)
        s.copy(selections = s.selections + (category to next))
    }

    // ---------- Borrado ----------

    /** Borra lo marcado. Con [onlyPaths], solo lo marcado dentro de ese conjunto (lo visible con el filtro). */
    fun deleteSelected(category: Category, useTrash: Boolean, onlyPaths: Set<String>? = null) {
        val marked = _state.value.selections[category].orEmpty()
        val paths = if (onlyPaths == null) marked else marked.intersect(onlyPaths)
        if (paths.isEmpty()) return
        _state.update { it.copy(working = true) }
        viewModelScope.launch {
            val outcome = withContext(Dispatchers.IO) { fileOps.remove(paths, useTrash) }
            _state.update { s ->
                val removed = outcome.removed
                val verb = if (useTrash) "Movidos a la papelera" else "Liberados"
                val failNote = if (outcome.failed > 0) " · ${outcome.failed} no se pudieron borrar" else ""
                s.copy(
                    working = false,
                    result = s.result?.without(removed),
                    selections = s.selections.mapValues { (_, sel) -> sel - removed },
                    storage = runCatching { StorageInfo.summary() }.getOrNull() ?: s.storage,
                    message = "$verb ${formatSize(outcome.bytes)}$failNote",
                )
            }
            loadTrash(purge = false)
        }
    }

    // ---------- Caché de apps ----------

    fun loadAppCaches() {
        if (_state.value.loadingAppCaches) return
        _state.update { it.copy(loadingAppCaches = true) }
        viewModelScope.launch {
            val list = withContext(Dispatchers.IO) { runCatching { AppCaches.query(app) }.getOrDefault(emptyList()) }
            _state.update { it.copy(appCaches = list, loadingAppCaches = false) }
        }
    }

    fun onAppCacheCleared() {
        _state.update { it.copy(storage = runCatching { StorageInfo.summary() }.getOrNull() ?: it.storage) }
        loadAppCaches()
    }

    // ---------- Papelera ----------

    private fun loadTrash(purge: Boolean) {
        viewModelScope.launch {
            val list = withContext(Dispatchers.IO) {
                if (purge) runCatching { trashManager.purgeExpired() }
                trashManager.list()
            }
            _state.update { it.copy(trash = list) }
        }
    }

    fun restore(entry: TrashEntry) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { trashManager.restore(entry) }
            _state.update { it.copy(message = if (ok) "Restaurado: ${entry.name}" else "No se pudo restaurar ${entry.name}") }
            loadTrash(purge = false)
        }
    }

    fun emptyTrash() {
        _state.update { it.copy(working = true) }
        viewModelScope.launch {
            val freed = withContext(Dispatchers.IO) { trashManager.empty() }
            _state.update {
                it.copy(
                    working = false,
                    storage = runCatching { StorageInfo.summary() }.getOrNull() ?: it.storage,
                    message = "Papelera vaciada: ${formatSize(freed)} liberados",
                )
            }
            loadTrash(purge = false)
        }
    }

    // ---------- Actualizaciones ----------

    fun checkForUpdates(manual: Boolean) {
        if (_state.value.checkingUpdate) return
        _state.update { it.copy(checkingUpdate = true) }
        viewModelScope.launch {
            val r = Updater.check()
            _state.update {
                it.copy(
                    update = r,
                    checkingUpdate = false,
                    // Al abrir la app solo se avisa si hay versión nueva; a mano, siempre se responde.
                    showUpdateDialog = manual || r is Updater.Result.Available,
                )
            }
        }
    }

    fun dismissUpdateDialog() = _state.update { it.copy(showUpdateDialog = false) }
}
