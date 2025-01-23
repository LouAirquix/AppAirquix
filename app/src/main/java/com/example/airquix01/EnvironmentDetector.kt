package com.example.airquix01

import com.google.mlkit.vision.label.ImageLabel
import kotlin.math.max

object EnvironmentDetector {

    /**
     * detectEnvironmentWithConfidence:
     * Liefert eine Pair(ENV, CONFIDENCE) zurück.
     * "Inside" oder "Outside" anhand einfacher Heuristik.
     */
    fun detectEnvironmentWithConfidence(labels: List<ImageLabel>): Pair<String, Float> {
        // Wie bisher Inside vs Outside – wir schauen aber, ob es ein Label mit >= x% gibt
        // Wir nehmen das Maximum der "Inside"-relevanten Begriffe und das Maximum der "Outside"-relevanten
        var insideScore = 0f
        var outsideScore = 0f

        for (label in labels) {
            val text = label.text.lowercase()
            val score = label.confidence

            if (text.contains("room") ||
                text.contains("bathroom") ||
                text.contains("bedroom") ||
                text.contains("kitchen") ||
                text.contains("desk") ||
                text.contains("chair") ||
                text.contains("furniture") ||
                text.contains("infrastructure") ||
                text.contains("couch")

                ) {
                insideScore = max(insideScore, score)
            }
            if (text.contains("tree") ||
                text.contains("street") ||
                text.contains("grass") ||
                text.contains("field") ||
                text.contains("park") ||
                text.contains("sky") ||
                text.contains("lake") ||
                text.contains("beach") ||
                text.contains("field") ||
                text.contains("mountain") ||
                text.contains("garden") ||
                text.contains("car") ||
                text.contains("asphalt")

            ) {
                outsideScore = max(outsideScore, score)
            }
        }

        return if (insideScore >= outsideScore && insideScore > 0f) {
            "Inside" to insideScore
        } else if (outsideScore > insideScore && outsideScore > 0f) {
            "Outside" to outsideScore
        } else {
            "Unknown" to 0f
        }
    }
}
