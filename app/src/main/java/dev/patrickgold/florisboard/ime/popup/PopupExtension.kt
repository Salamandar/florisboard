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

package dev.patrickgold.florisboard.ime.popup

import android.content.Context
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dev.patrickgold.florisboard.ime.extension.Asset
import dev.patrickgold.florisboard.ime.text.key.KeyData
import dev.patrickgold.florisboard.ime.text.key.KeyTypeAdapter
import dev.patrickgold.florisboard.ime.text.key.KeyVariation
import dev.patrickgold.florisboard.ime.text.key.KeyVariationAdapter

/**
 * An object which maps each base key to its extended popups. This can be done for each
 * key variation. [KeyVariation.ALL] is always the fallback for each key.
 */
typealias PopupMapping = Map<KeyVariation, Map<String, PopupSet<KeyData>>>

/**
 * Class which contains an extended popup mapping to use for adding popups subtype based on the
 * keyboard layout.
 *
 * @property mapping The mapping of the base keys to their popups. See [PopupMapping] for more info.
 */
class PopupExtension(
    override val name: String,
    override val label: String = name,
    override val authors: List<String>,
    val mapping: PopupMapping
) : Asset {
    companion object : Asset.Companion<PopupExtension> {
        override fun empty() = PopupExtension("", "", listOf(), mapOf())

        override fun fromFile(context: Context, path: String): Result<PopupExtension, Throwable> {
            return try {
                val raw = context.assets.open(path).bufferedReader().use { it.readText() }
                val asset = fromJsonString(raw)
                if (asset != null) {
                    Ok(asset)
                } else {
                    Err(NullPointerException())
                }
            } catch (e: Exception) {
                Err(e)
            }
        }

        fun fromJsonString(json: String): PopupExtension? {
            val moshi = Moshi.Builder()
                .add(KotlinJsonAdapterFactory())
                .add(KeyTypeAdapter())
                .add(KeyVariationAdapter())
                .build()
            val layoutAdapter = moshi.adapter(PopupExtension::class.java)
            return layoutAdapter.fromJson(json)
        }
    }
}
