package com.sih.drugtestclassifier.models

import android.content.res.AssetManager

/**
 * Loads a [KitProfile] from a bundled asset JSON file. Ported from
 * lib/models/kit_profile_loader.dart (rootBundle.loadString -> AssetManager.open).
 */
object KitProfileLoader {
    fun loadDefault(assets: AssetManager): KitProfile =
        loadFromAsset(assets, "kit_profiles/default_kit_profile.json")

    fun loadFromAsset(assets: AssetManager, assetPath: String): KitProfile {
        val jsonStr = assets.open(assetPath).bufferedReader(Charsets.UTF_8).use { it.readText() }
        return KitProfile.fromJsonString(jsonStr)
    }
}
