package com.sih.drugtestclassifier.models

import org.json.JSONObject

/**
 * One named reference color a kit's test area can turn. Ported from
 * lib/models/kit_profile.dart.
 */
data class OutcomeReference(val outcome: TestOutcome, val labColor: LabColor) {
    companion object {
        fun fromJson(json: JSONObject): OutcomeReference {
            val lab = json.getJSONObject("lab")
            return OutcomeReference(
                outcomeFromString(json.getString("outcome")),
                LabColor(lab.getDouble("l"), lab.getDouble("a"), lab.getDouble("b")),
            )
        }

        private fun outcomeFromString(s: String): TestOutcome = when (s.lowercase()) {
            "positive" -> TestOutcome.POSITIVE
            "negative" -> TestOutcome.NEGATIVE
            else -> TestOutcome.INCONCLUSIVE
        }
    }
}

/**
 * A configurable colour-outcome mapping for ONE field-test kit type.
 *
 * IMPORTANT: [maxConfidentDeltaE] and the reference colours below must be
 * sourced from the kit manufacturer's own documented colour-interpretation
 * reference. The values in default_kit_profile.json are PLACEHOLDERS —
 * swap them for the real kit's documented reference colours before use.
 */
data class KitProfile(
    val kitId: String,
    val displayName: String,
    val testAreaOffsetXFractionOfCardWidth: Double,
    val testAreaOffsetYFractionOfCardHeight: Double,
    val testAreaWidthFractionOfCardWidth: Double,
    val testAreaHeightFractionOfCardHeight: Double,
    val references: List<OutcomeReference>,
    val maxConfidentDeltaE: Double,
) {
    companion object {
        fun fromJsonString(jsonStr: String): KitProfile = fromJson(JSONObject(jsonStr))

        fun fromJson(json: JSONObject): KitProfile {
            val refsArray = json.getJSONArray("references")
            val refs = mutableListOf<OutcomeReference>()
            for (i in 0 until refsArray.length()) {
                refs.add(OutcomeReference.fromJson(refsArray.getJSONObject(i)))
            }
            return KitProfile(
                kitId = json.getString("kitId"),
                displayName = json.getString("displayName"),
                testAreaOffsetXFractionOfCardWidth = json.getDouble("testAreaOffsetXFractionOfCardWidth"),
                testAreaOffsetYFractionOfCardHeight = json.getDouble("testAreaOffsetYFractionOfCardHeight"),
                testAreaWidthFractionOfCardWidth = json.getDouble("testAreaWidthFractionOfCardWidth"),
                testAreaHeightFractionOfCardHeight = json.getDouble("testAreaHeightFractionOfCardHeight"),
                maxConfidentDeltaE = json.getDouble("maxConfidentDeltaE"),
                references = refs,
            )
        }
    }
}
