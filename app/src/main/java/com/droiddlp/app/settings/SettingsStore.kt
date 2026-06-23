package com.droiddlp.app.settings

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-wide settings backed by [SharedPreferences], exposed as observable
 * [StateFlow]s. Initialised once from [com.droiddlp.app.DroidDlpApp]. CLAUDE.md §6 P2.
 */
object SettingsStore {
    private var prefs: SharedPreferences? = null

    private val _maxConcurrent = MutableStateFlow(DEFAULT_MAX_CONCURRENT)
    val maxConcurrent: StateFlow<Int> = _maxConcurrent.asStateFlow()

    private val _subDir = MutableStateFlow(DEFAULT_SUBDIR)
    val subDir: StateFlow<String> = _subDir.asStateFlow()

    private val _dynamicColor = MutableStateFlow(true)
    val dynamicColor: StateFlow<Boolean> = _dynamicColor.asStateFlow()

    fun init(context: Context) {
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        _maxConcurrent.value = p.getInt(KEY_MAX_CONCURRENT, DEFAULT_MAX_CONCURRENT).coerceIn(1, 5)
        _subDir.value = p.getString(KEY_SUBDIR, DEFAULT_SUBDIR).orEmpty()
        _dynamicColor.value = p.getBoolean(KEY_DYNAMIC_COLOR, true)
    }

    fun setMaxConcurrent(value: Int) {
        val v = value.coerceIn(1, 5)
        _maxConcurrent.value = v
        prefs?.edit()?.putInt(KEY_MAX_CONCURRENT, v)?.apply()
    }

    fun setSubDir(value: String) {
        val v = value.replace(ILLEGAL_PATH_CHARS, "_")
        _subDir.value = v
        prefs?.edit()?.putString(KEY_SUBDIR, v)?.apply()
    }

    fun setDynamicColor(value: Boolean) {
        _dynamicColor.value = value
        prefs?.edit()?.putBoolean(KEY_DYNAMIC_COLOR, value)?.apply()
    }

    /** The subfolder to use, falling back to the default when blank. */
    fun effectiveSubDir(): String = _subDir.value.ifBlank { DEFAULT_SUBDIR }

    const val DEFAULT_MAX_CONCURRENT = 3
    const val DEFAULT_SUBDIR = "DroidDLP"
    private const val PREFS = "droiddlp_settings"
    private const val KEY_MAX_CONCURRENT = "max_concurrent"
    private const val KEY_SUBDIR = "subdir"
    private const val KEY_DYNAMIC_COLOR = "dynamic_color"
    private val ILLEGAL_PATH_CHARS = Regex("[\\\\/:*?\"<>|]")
}
