package dev.neekolor.appcanvasfaker.ui.viewmodel

import android.content.pm.ApplicationInfo
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import dev.neekolor.appcanvasfaker.acfApp
import dev.neekolor.appcanvasfaker.core.ConfigRepository
import dev.neekolor.appcanvasfaker.ui.screen.log.LogItem
import dev.neekolor.appcanvasfaker.ui.screen.log.LogUiState
import dev.neekolor.appcanvasfaker.ui.screen.log.buildVisibleLogItems
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

class LogViewModel(
    private val repo: ConfigRepository = ConfigRepository(acfApp),
) : ViewModel() {

    private val _uiState = MutableStateFlow(LogUiState(loggingEnabled = repo.enableLogging()))
    val uiState: StateFlow<LogUiState> = _uiState.asStateFlow()

    private val itemsFlow = MutableStateFlow<List<LogItem>>(emptyList())
    private val searchTextFlow = MutableStateFlow("")
    private val selectedFiltersFlow = MutableStateFlow<Set<String>>(emptySet())

    // DateTimeFormatter 不可变、线程安全；refresh() 可能并发执行
    private val timeFormat = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss", Locale.ROOT)
    private val appInfoCache = ConcurrentHashMap<String, Pair<String, ApplicationInfo?>>()

    init {
        viewModelScope.launch(Dispatchers.Default) {
            combine(itemsFlow, searchTextFlow, selectedFiltersFlow) { items, searchText, selectedFilters ->
                buildVisibleLogItems(items, searchText, selectedFilters)
            }.collect { visibleItems ->
                _uiState.update { it.copy(visibleItems = visibleItems) }
            }
        }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            val items = repo.getLogs().asReversed().map { entry ->
                val (label, appInfo) = resolveApp(entry.packageName.orEmpty())
                LogItem(
                    timestamp = entry.timestamp,
                    timeText = runCatching {
                        timeFormat.format(Instant.ofEpochMilli(entry.timestamp).atZone(ZoneId.systemDefault()))
                    }.getOrDefault(entry.timestamp.toString()),
                    tag = entry.tag,
                    packageName = entry.packageName ?: entry.message,
                    appLabel = label,
                    applicationInfo = appInfo,
                )
            }
            itemsFlow.value = items
            _uiState.update {
                it.copy(items = items, loggingEnabled = repo.enableLogging())
            }
        }
    }

    fun clearLogs() {
        repo.clearLogs()
        refresh()
    }

    fun setSearchText(searchText: String) {
        searchTextFlow.value = searchText
        _uiState.update { it.copy(searchText = searchText) }
    }

    fun toggleFilter(filterTag: String?) {
        _uiState.update { currentState ->
            val selectedFilters = if (filterTag == null) {
                emptySet()
            } else {
                currentState.selectedFilters.toMutableSet().apply {
                    if (!add(filterTag)) remove(filterTag)
                }
            }
            selectedFiltersFlow.value = selectedFilters
            currentState.copy(selectedFilters = selectedFilters)
        }
    }

    private fun resolveApp(pkg: String): Pair<String, ApplicationInfo?> {
        appInfoCache[pkg]?.let { return it }
        val resolved = runCatching {
            val ai = acfApp.packageManager.getApplicationInfo(pkg, 0)
            val label = acfApp.packageManager.getApplicationLabel(ai).toString()
            label to ai
        }.getOrElse { pkg to null }
        appInfoCache[pkg] = resolved
        return resolved
    }
}