package com.example.airquix01

import com.google.mlkit.vision.label.ImageLabel

/**
 * EnvironmentDetector: Weil zu wissen, ob du drinnen oder draußen bist, esenziell ist
 */

object EnvironmentDetector {
    fun detectEnvironment(labels: List<ImageLabel>): String {
        // Konvertiere alle Labels in Kleinbuchstaben weil Großbuchstaben nur verwirren
        val labelNames = labels.map { it.text.lowercase() }
        return when {
            //falls 'In Car' label Probleme macht, einfach die nächste Zeile löschen oder kommentieren
            labelNames.any {  it.contains("automobile") } -> "In Car"

            // siehst du Möbel odr Räume? willkommen im 21. Jahrhundert wo wir Innenräume erkennen können.
            labelNames.any { it.contains("indoor") || it.contains("room") || it.contains("bathroom") || it.contains("bedroom") || it.contains("kitchen") || it.contains("desk") || it.contains("chair") || it.contains("furniture") || it.contains("couch") } -> "Inside"

            // wenn du Bäume, Straßen oder Gras siehst, bist du wharscheinlich nicht in deinem Wohnzimmer
            labelNames.any { it.contains("outdoor") || it.contains("tree") || it.contains("street") || it.contains("grass") || it.contains("field") || it.contains("park") || it.contains("sky") || it.contains("car") || it.contains("lake") || it.contains("building") || it.contains("beach") || it.contains("field") || it.contains("mountain") || it.contains("garden") || it.contains("asphalt") } -> "Outside"
            else -> "Unknown"
        }
    }
}
