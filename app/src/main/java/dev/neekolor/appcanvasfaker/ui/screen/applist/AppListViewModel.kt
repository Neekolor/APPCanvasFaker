package dev.neekolor.appcanvasfaker.ui.screen.applist

import android.app.Application
import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.neekolor.appcanvasfaker.R
import dev.neekolor.appcanvasfaker.core.ConfigRepository
import dev.neekolor.appcanvasfaker.core.InstalledApp
import dev.neekolor.appcanvasfaker.ui.component.SearchStatus
import dev.neekolor.appcanvasfaker.ui.util.PinyinUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private const val PREFS_NAME = "app_list"
private const val KEY_SHOW_SYSTEM = "show_system_apps"
private const val KEY_SORT_MODE = "sort_mode"
private const val KEY_SORT_REVERSE = "sort_reverse"
private const val RECENTLY_INSTALLED_WINDOW_MILLIS = 60 * 60 * 1000L

@OptIn(ExperimentalCoroutinesApi::class)
class AppListViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = ConfigRepository(application)
    private val pm = application.packageManager
    private val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(AppListUiState())
    val uiState: StateFlow<AppListUiState> = _uiState.asStateFlow()

    private val searchQuery = MutableStateFlow("")
    private val loadMutex = Mutex()
    private var isNeedRefresh = false

    init {
        _uiState.update {
            it.copy(
                showSystemApps = prefs.getBoolean(KEY_SHOW_SYSTEM, false),
                sortType = AppSortType.fromKey(prefs.getString(KEY_SORT_MODE, AppSortType.NAME.key) ?: AppSortType.NAME.key),
                reversed = prefs.getBoolean(KEY_SORT_REVERSE, false),
                searchStatus = SearchStatus(application.getString(R.string.app_search_hint)),
            )
        }
        viewModelScope.launch {
            searchQuery.debounce(250).collectLatest { query ->
                runSearch(query)
            }
        }
    }

    fun markNeedRefresh() {
        isNeedRefresh = true
    }

    fun loadAppList(force: Boolean = false): Job = viewModelScope.launch {
        if (force || _uiState.value.apps.isEmpty() || isNeedRefresh) {
            fetchAppList()
        }
    }

    fun updateSearchText(text: String) {
        updateSearchStatus(_uiState.value.searchStatus.copy(searchText = text))
    }

    fun updateSearchStatus(status: SearchStatus) {
        val previous = _uiState.value.searchStatus
        _uiState.update { it.copy(searchStatus = status) }
        if (previous.searchText != status.searchText) {
            searchQuery.value = status.searchText
        }
    }

    fun toggleShowSystemApps() {
        val newValue = !_uiState.value.showSystemApps
        prefs.edit().putBoolean(KEY_SHOW_SYSTEM, newValue).apply()
        _uiState.update { it.copy(showSystemApps = newValue) }
        viewModelScope.launch { fetchAppList() }
    }

    fun updateSortConfig(type: AppSortType, reversed: Boolean) {
        prefs.edit()
            .putString(KEY_SORT_MODE, type.key)
            .putBoolean(KEY_SORT_REVERSE, reversed)
            .apply()
        _uiState.update { it.copy(sortType = type, reversed = reversed) }
        viewModelScope.launch { fetchAppList() }
    }

    private suspend fun fetchAppList(): Unit = loadMutex.withLock {
        fetchLocked()
    }

    /** 执行一次全量加载，调用方必须已持有 [loadMutex]。 */
    private suspend fun fetchLocked() {
        if (_uiState.value.isRefreshing) return
        _uiState.update { it.copy(isRefreshing = true, error = null) }
        try {
            val state = _uiState.value
            val query = searchQuery.value
            val (apps, recent, results) = withContext(Dispatchers.IO) {
                val list = repo.getInstalledApps("", state.showSystemApps, state.reversed, state.sortType.key)
                val items = toItems(list)
                Triple(items, recentlyInstalled(items), filterQuery(items, query))
            }
            _uiState.update {
                it.copy(
                    apps = apps,
                    recentlyInstalledResults = recent,
                    searchResults = results,
                    isRefreshing = false,
                    hasLoaded = true,
                    searchStatus = it.searchStatus.copy(
                        resultStatus = if (query.isBlank()) {
                            SearchStatus.ResultStatus.DEFAULT
                        } else {
                            SearchStatus.ResultStatus.SHOW
                        }
                    )
                )
            }
        } catch (t: Throwable) {
            // 失败必须复位 isRefreshing，否则后续刷新会被永久跳过
            _uiState.update { it.copy(isRefreshing = false, error = t) }
        } finally {
            // 刷新诉求已落实（或被跳过时由进行中的那次全量加载覆盖）；
            // 置于锁内清理，避免并发 loadAppList 误清导致刷新丢失
            isNeedRefresh = false
        }
    }

    private suspend fun runSearch(query: String) {
        if (query.isBlank()) {
            _uiState.update {
                it.copy(
                    searchResults = emptyList(),
                    searchStatus = it.searchStatus.copy(resultStatus = SearchStatus.ResultStatus.DEFAULT)
                )
            }
            return
        }
        // 与 fetchAppList 共用同一把锁：优先过滤已加载列表，
        // 避免每次搜索都全量重扫 PackageManager；首载未完成时先等它完成
        val results = loadMutex.withLock {
            var base = _uiState.value.apps
            if (base.isEmpty()) {
                fetchLocked()
                base = _uiState.value.apps
            }
            withContext(Dispatchers.Default) { filterQuery(base, query) }
        }
        _uiState.update {
            it.copy(
                searchResults = results,
                searchStatus = it.searchStatus.copy(resultStatus = SearchStatus.ResultStatus.SHOW)
            )
        }
    }

    private fun toItems(apps: List<InstalledApp>): List<AppListItem> {
        val infoMap = loadApplicationInfos()
        return apps.map { app ->
            AppListItem(
                label = app.label,
                packageName = app.packageName,
                isSystem = app.isSystem,
                firstInstallTime = app.firstInstallTime,
                lastUpdateTime = app.lastUpdateTime,
                applicationInfo = infoMap[app.packageName],
                rule = app.rule,
            )
        }
    }

    private fun loadApplicationInfos(): Map<String, ApplicationInfo> =
        runCatching { pm.getInstalledApplications(0).associateBy { it.packageName } }.getOrDefault(emptyMap())

    private fun filterQuery(items: List<AppListItem>, query: String): List<AppListItem> {
        if (query.isBlank()) return items
        return items.filter { item ->
            item.label.contains(query, true) ||
                item.packageName.contains(query, true) ||
                runCatching { PinyinUtil.toPinyin(item.label) }.getOrDefault("").contains(query, true)
        }
    }

    private fun recentlyInstalled(items: List<AppListItem>): List<AppListItem> {
        val cutoff = System.currentTimeMillis() - RECENTLY_INSTALLED_WINDOW_MILLIS
        return items.filter { it.firstInstallTime >= cutoff }
            .sortedByDescending { it.firstInstallTime }
    }
}