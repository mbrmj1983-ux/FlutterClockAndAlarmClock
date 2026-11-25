package com.example.clockalarm

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId

@Composable
internal fun WorldClockDashboard(now: Instant) {
    val context = LocalContext.current
    var cities by remember { mutableStateOf(WorldClockStore.load(context)) }
    var showCityPicker by remember { mutableStateOf(false) }
    val deviceZoneId = ZoneId.systemDefault().id
    val availableCities = WorldClockZones.cities.filterNot { city ->
        cities.any { it.zoneId == city.zoneId }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("الساعة العالمية", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "الفارق محسوب نسبةً إلى توقيت الجهاز",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedButton(
                    onClick = { showCityPicker = true },
                    enabled = availableCities.isNotEmpty(),
                ) { Text("إضافة مدينة") }
            }

            if (cities.isEmpty()) {
                Text(
                    "أضف مدينة أو منطقة زمنية لعرض وقتها.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                Column(
                    modifier = Modifier
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    cities.forEach { city ->
                        val reading = WorldClock.reading(now, city, deviceZoneId)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(city.name, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "${city.zoneId} · ${WorldClock.formatOffset(reading.utcOffsetSeconds)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    WorldClock.formatDate(now, city),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    WorldClock.formatDeviceDifference(reading.differenceFromDeviceSeconds),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    WorldClock.formatTime(now, city),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Light,
                                )
                                TextButton(
                                    onClick = {
                                        val updated = cities.filterNot { it.zoneId == city.zoneId }
                                        cities = updated
                                        WorldClockStore.save(context, updated)
                                    },
                                ) { Text("إزالة") }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCityPicker) {
        AlertDialog(
            onDismissRequest = { showCityPicker = false },
            title = { Text("إضافة منطقة زمنية") },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 400.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    availableCities.forEach { city ->
                        TextButton(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                val updated = cities + city
                                cities = updated
                                WorldClockStore.save(context, updated)
                                showCityPicker = false
                            },
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(city.name)
                                Text(
                                    city.zoneId,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCityPicker = false }) { Text("إغلاق") }
            },
        )
    }
}
