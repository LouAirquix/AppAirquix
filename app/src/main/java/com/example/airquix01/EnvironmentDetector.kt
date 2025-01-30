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
                text.contains("circus") ||
                text.contains("factory") ||
                text.contains("cushion") ||
                text.contains("casino") ||
                text.contains("stairs") ||
                text.contains("computer") ||
                text.contains("cookware and bakeware") ||
                text.contains("pier") ||
                text.contains("caving") ||
                text.contains("cave") ||
                text.contains("cabinetry") ||
                text.contains("nightclub") ||
                text.contains("cuisine") ||
                text.contains("stuffed toy") ||
                text.contains("crochet") ||
                text.contains("cutlery") ||
                text.contains("couch") ||
                text.contains("blackboard") ||
                text.contains("whiteboard") ||
                text.contains("factory") ||
                text.contains("bathing") ||
                text.contains("gymnastics") ||
                text.contains("bunk bed") ||
                text.contains("tableware") ||
                text.contains("curtain") ||
                text.contains("ballroom") ||
                text.contains("tablecloth") ||
                text.contains("piano") ||
                text.contains("class") ||
                text.contains("crowd") ||
                text.contains("subwoofer") ||
                text.contains("lampshade") ||
                text.contains("shelf") ||
                text.contains("ballroom") ||
                text.contains("metal") ||
                text.contains("loveseat") ||
                text.contains("lunch") ||
                text.contains("standing") ||
                text.contains("television") ||
                text.contains("product") ||
                text.contains("food") ||
                text.contains("fast food") ||
                text.contains("train") ||
                text.contains("musical instrument") ||
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
                text.contains("bridge") ||
                text.contains("mountain") ||
                text.contains("garden") ||
                text.contains("car") ||
                text.contains("asphalt") ||
                text.contains("infrastructure") ||
                text.contains("cliff") ||
                text.contains("safari") ||
                text.contains("boat") ||
                text.contains("sunset") ||
                text.contains("rock") ||
                text.contains("sunset") ||
                text.contains("tower") ||
                text.contains("playground") ||
                text.contains("construction") ||
                text.contains("picnic") ||
                text.contains("wheelbarrow") ||
                text.contains("windshield") ||
                text.contains("pier") ||
                text.contains("cycling") ||
                text.contains("track") ||
                text.contains("cattle") ||
                text.contains("jungle") ||
                text.contains("skyline") ||
                text.contains("desert") ||
                text.contains("sledding") ||
                text.contains("glacier") ||
                text.contains("bumper") ||
                text.contains("trunk") ||
                text.contains("forest") ||
                text.contains("flora") ||
                text.contains("ruins") ||
                text.contains("horse") ||
                text.contains("motorcycle") ||
                text.contains("lighthouse") ||
                text.contains("river") ||
                text.contains("road") ||
                text.contains("roof") ||
                text.contains("swimming") ||
                text.contains("skiing") ||
                text.contains("wheel") ||
                text.contains("wakeboarding") ||
                text.contains("ranch") ||
                text.contains("snowboarding") ||
                text.contains("cathedral") ||
                text.contains("skyscraper") ||
                text.contains("bench") ||
                text.contains("farm") ||
                text.contains("van") ||
                text.contains("tire") ||
                text.contains("prairie") ||
                text.contains("sailboat") ||
                text.contains("rickshaw") ||
                text.contains("barn") ||
                text.contains("storm") ||
                text.contains("monument") ||
                text.contains("space")

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
