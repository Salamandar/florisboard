/*
 * Copyright (C) 2020 Patrick Goldinger
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.patrickgold.florisboard.ime.theme

import android.content.Context
import android.content.res.Configuration
import com.github.michaelbull.result.*
import dev.patrickgold.florisboard.ime.core.PrefHelper
import dev.patrickgold.florisboard.ime.extension.AssetManager
import dev.patrickgold.florisboard.ime.extension.AssetRef
import dev.patrickgold.florisboard.ime.extension.AssetSource
import timber.log.Timber

class ThemeManager private constructor(
    private val applicationContext: Context,
    private val assetManager: AssetManager,
    private val prefs: PrefHelper
) {
    private val callbackReceivers: MutableList<OnThemeUpdatedListener?> =
        mutableListOf()
    var activeTheme: Theme = Theme.empty()
        private set
    var indexedDayThemeRefs: MutableMap<AssetRef, ThemeMetaOnly> = mutableMapOf()
    var indexedNightThemeRefs: MutableMap<AssetRef, ThemeMetaOnly> = mutableMapOf()
    private var themeCache: MutableMap<AssetRef, Theme> = mutableMapOf()

    companion object {
        private const val THEME_PATH_REL: String = "ime/theme"

        private var defaultInstance: ThemeManager? = null

        fun init(
            applicationContext: Context,
            assetManager: AssetManager,
            prefs: PrefHelper
        ): ThemeManager {
            val instance = ThemeManager(applicationContext, assetManager, prefs)
            defaultInstance = instance
            return instance
        }

        fun default(): ThemeManager {
            val instance = defaultInstance
            if (instance != null) {
                return instance
            } else {
                throw UninitializedPropertyAccessException(
                    "${this::class.simpleName} has not been initialized previously. Make sure to call init(prefs) before using default()."
                )
            }
        }
    }

    init {
        update()
    }

    fun update() {
        indexThemeRefs()
        val ref = evaluateActiveThemeRef()
        Timber.i(ref.toString())
        activeTheme = if (ref == null) {
            Theme.BASE_THEME
        } else {
            loadTheme(ref).getOr(Theme.BASE_THEME)
        }
        notifyCallbackReceivers()
    }

    fun requestThemeUpdate(onThemeUpdatedListener: OnThemeUpdatedListener): Boolean {
        onThemeUpdatedListener.onThemeUpdated(activeTheme)
        return true
    }

    @Synchronized
    fun registerOnThemeUpdatedListener(onThemeUpdatedListener: OnThemeUpdatedListener): Boolean {
        return if (!callbackReceivers.contains(onThemeUpdatedListener)) {
            onThemeUpdatedListener.onThemeUpdated(activeTheme)
            callbackReceivers.add(onThemeUpdatedListener)
        } else {
            false
        }
    }

    @Synchronized
    fun unregisterOnThemeUpdatedListener(onThemeUpdatedListener: OnThemeUpdatedListener): Boolean {
        val iterator = callbackReceivers.iterator()
        var removed = false
        while (iterator.hasNext()) {
            val callbackReceiver = iterator.next()
            if (callbackReceiver == null) {
                iterator.remove()
            } else if (callbackReceiver == onThemeUpdatedListener) {
                iterator.remove()
                removed = true
            }
        }
        return removed
    }

    private fun loadTheme(ref: AssetRef): Result<Theme, Throwable> {
        val cached = themeCache[ref]
        return if (cached != null) {
            Ok(cached)
        } else {
            assetManager.loadAsset(ref, Theme::class.java).onSuccess { theme ->
                themeCache[ref.copy()] = theme
            }.onFailure {
                Timber.i(it.toString())
            }
        }
    }

    private fun evaluateActiveThemeRef(): AssetRef? {
        Timber.i(prefs.theme.mode.toString())
        return AssetRef.fromString(when (prefs.theme.mode) {
            ThemeMode.ALWAYS_DAY -> prefs.theme.dayThemeRef
            ThemeMode.ALWAYS_NIGHT -> prefs.theme.nightThemeRef
            ThemeMode.FOLLOW_SYSTEM -> if (applicationContext.resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
            ) {
                prefs.theme.nightThemeRef
            } else {
                prefs.theme.dayThemeRef
            }
            ThemeMode.FOLLOW_TIME -> "assets:ime/theme/floris_day.json"
        }).getOr(null)
    }

    private fun indexThemeRefs() {
        assetManager.listAssets(
            AssetRef(AssetSource.Assets, THEME_PATH_REL),
            ThemeMetaOnly::class.java
        ).onSuccess {
            for ((ref, themeMetaOnly) in it) {
                if (themeMetaOnly.isNightTheme) {
                    indexedNightThemeRefs[ref] = themeMetaOnly
                } else {
                    indexedDayThemeRefs[ref] = themeMetaOnly
                }
            }
        }.onFailure {
            Timber.e(it.toString())
        }
        assetManager.listAssets(
            AssetRef(AssetSource.Internal, THEME_PATH_REL),
            ThemeMetaOnly::class.java
        ).onSuccess {
            for ((ref, themeMetaOnly) in it) {
                if (themeMetaOnly.isNightTheme) {
                    indexedNightThemeRefs[ref] = themeMetaOnly
                } else {
                    indexedDayThemeRefs[ref] = themeMetaOnly
                }
            }
        }.onFailure {
            Timber.e(it.toString())
        }
    }

    private fun notifyCallbackReceivers() {
        for (callbackReceiver in callbackReceivers) {
            callbackReceiver?.onThemeUpdated(activeTheme)
        }
    }

    fun interface OnThemeUpdatedListener {
        fun onThemeUpdated(theme: Theme)
    }
}