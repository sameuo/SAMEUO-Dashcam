package com.sameuo.dashcam.ui.ride

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sameuo.dashcam.data.gps.GpsData
import com.sameuo.dashcam.ui.components.EmptyView
import com.sameuo.dashcam.ui.components.LoadingView
import com.sameuo.dashcam.ui.components.SectionCard
import com.sameuo.dashcam.ui.components.StatPill
import com.sameuo.dashcam.ui.sameuoVm
import kotlin.math.max
import kotlin.math.min

@Composable
fun RideScreen() {
    val vm = sameuoVm { RideViewModel() }
    val ui by vm.ui.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Ride tracks", style = MaterialTheme.typography.headlineMedium)
        if (ui.videos.isEmpty()) {
            EmptyView("No downloaded rides yet",
                "Download a recording from the Album and its embedded GPS route shows up here.")
            return@Column
        }
        SectionCard {
            Text("Pick a downloaded recording", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            ui.videos.take(6).forEach { v ->
                Surface(
                    onClick = { vm.analyze(v) },
                    color = if (v.id == ui.selected?.id) MaterialTheme.colorScheme.surfaceVariant
                    else MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                ) { Text(v.name, Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium) }
            }
        }
        when {
            ui.loading -> LoadingView("Extracting GPS track…")
            ui.trip.points.isNotEmpty() -> TripResult(ui.trip)
            ui.message != null -> Text(ui.message!!, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun TripResult(trip: TripStats) {
    SectionCard {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatPill("Distance", "%.2f km".format(trip.distanceKm))
            StatPill("Top speed", "%.0f".format(trip.maxSpeedKmh) + " km/h")
            StatPill("Average", "%.0f".format(trip.avgSpeedKmh) + " km/h")
            StatPill("Points", trip.points.size.toString())
        }
        Spacer(Modifier.height(8.dp))
        Text("${trip.startedAt ?: ""}  →  ${trip.endedAt ?: ""}",
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        TrackCanvas(trip.points, Modifier.fillMaxWidth().height(180.dp))
    }
}

@Composable
private fun TrackCanvas(points: List<GpsData>, modifier: Modifier = Modifier) {
    val line = MaterialTheme.colorScheme.primary
    val bg = MaterialTheme.colorScheme.surfaceVariant
    Canvas(modifier) {
        drawRect(bg)
        if (points.size < 2) return@Canvas
        val lats = points.map { it.latitude }; val lons = points.map { it.longitude }
        val minLat = lats.min(); val maxLat = max(lats.max(), minLat + 1e-6)
        val minLon = lons.min(); val maxLon = max(lons.max(), minLon + 1e-6)
        val pad = 24f
        fun px(lon: Double) = pad + ((lon - minLon) / (maxLon - minLon)).toFloat() * (size.width - 2 * pad)
        fun py(lat: Double) = pad + (1f - ((lat - minLat) / (maxLat - minLat)).toFloat()) * (size.height - 2 * pad)
        val path = Path().apply {
            moveTo(px(points.first().longitude), py(points.first().latitude))
            points.drop(1).forEach { lineTo(px(it.longitude), py(it.latitude)) }
        }
        drawPath(path, line, style = Stroke(width = 5f))
        drawCircle(line, 8f, Offset(px(points.first().longitude), py(points.first().latitude)))
    }
}
