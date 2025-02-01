package com.example.airquix01.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun LogItem(logLine: String) {
    // CSV-Felder (15 Felder):
    // 0: timestamp
    // 1: PLACES_top1, 2: places_top1_conf
    // 3: PLACES_top2, 4: places_top2_conf
    // 5: ACT, 6: ACT_confidence
    // 7: YAMNET_top1, 8: top1_conf
    // 9: YAMNET_top2, 10: top2_conf
    // 11: YAMNET_top3, 12: top3_conf
    // 13: VEHICLE_label, 14: vehicle_conf
    val parts = remember(logLine) { parseCsvLine(logLine) }
    val timestamp = parts.getOrNull(0) ?: ""
    val placesTop1 = parts.getOrNull(1) ?: ""
    val placesTop1Conf = parts.getOrNull(2) ?: ""
    val placesTop2 = parts.getOrNull(3) ?: ""
    val placesTop2Conf = parts.getOrNull(4) ?: ""
    val act = parts.getOrNull(5) ?: ""
    val actConf = parts.getOrNull(6) ?: ""
    val yamTop1 = parts.getOrNull(7) ?: ""
    val yamTop1Conf = parts.getOrNull(8) ?: ""
    val yamTop2 = parts.getOrNull(9) ?: ""
    val yamTop2Conf = parts.getOrNull(10) ?: ""
    val yamTop3 = parts.getOrNull(11) ?: ""
    val yamTop3Conf = parts.getOrNull(12) ?: ""
    val veh = parts.getOrNull(13) ?: ""
    val vehConf = parts.getOrNull(14) ?: ""

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text("Timestamp: $timestamp", style = MaterialTheme.typography.bodySmall)
            Text("Places Top-1: $placesTop1 (conf: $placesTop1Conf)")
            Text("Places Top-2: $placesTop2 (conf: $placesTop2Conf)")
            Text("Activity: $act (conf: $actConf)")
            Text("YAMNET Top-1: $yamTop1 (conf: $yamTop1Conf)")
            Text("YAMNET Top-2: $yamTop2 (conf: $yamTop2Conf)")
            Text("YAMNET Top-3: $yamTop3 (conf: $yamTop3Conf)")
            Text("Vehicle (Top-1): $veh (conf: $vehConf)")
        }
    }
}

fun parseCsvLine(line: String): List<String> {
    val result = mutableListOf<String>()
    var current = StringBuilder()
    var insideQuotes = false
    var i = 0
    while (i < line.length) {
        val c = line[i]
        when {
            c == '"' -> {
                if (insideQuotes && i + 1 < line.length && line[i + 1] == '"') {
                    current.append('"')
                    i++
                } else {
                    insideQuotes = !insideQuotes
                }
            }
            c == ',' && !insideQuotes -> {
                result.add(current.toString())
                current = StringBuilder()
            }
            else -> {
                current.append(c)
            }
        }
        i++
    }
    result.add(current.toString())
    return result
}
