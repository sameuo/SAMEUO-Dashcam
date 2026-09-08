package com.sameuo.dashcam.ui.ride

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sameuo.dashcam.data.ServiceLocator
import com.sameuo.dashcam.data.gps.GpsData
import com.sameuo.dashcam.data.gps.Mp4GpsExtractor
import com.sameuo.dashcam.data.local.db.DownloadedMedia
import com.sameuo.dashcam.data.protocol.model.MediaKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sqrt

data class TripStats(
    val points: List<GpsData>,
    val distanceKm: Double,
    val maxSpeedKmh: Double,
    val avgSpeedKmh: Double,
    val startedAt: String?,
    val endedAt: String?,
) {
    companion object { val EMPTY = TripStats(emptyList(), 0.0, 0.0, 0.0, null, null) }
}

data class RideUi(
    val videos: List<DownloadedMedia> = emptyList(),
    val selected: DownloadedMedia? = null,
    val loading: Boolean = false,
    val trip: TripStats = TripStats.EMPTY,
    val message: String? = null,
)

class RideViewModel : ViewModel() {
    private val db = ServiceLocator.database
    private val extractor = Mp4GpsExtractor()

    private val _ui = MutableStateFlow(RideUi())
    val ui: StateFlow<RideUi> = _ui.asStateFlow()

    init { loadVideos() }

    fun loadVideos() = viewModelScope.launch {
        val all = db.listMedia()
        _ui.value = _ui.value.copy(videos = all.filter { it.kind == MediaKind.MOVIE || it.kind == MediaKind.EMERGENCY })
    }

    fun analyze(media: DownloadedMedia) {
        _ui.value = _ui.value.copy(selected = media, loading = true, message = null)
        viewModelScope.launch {
            val trip = withContext(Dispatchers.IO) {
                runCatching { buildStats(extractor.extract(media.localPath)) }
                    .getOrElse { TripStats.EMPTY }
            }
            _ui.value = _ui.value.copy(
                loading = false, trip = trip,
                message = if (trip.points.isEmpty()) "No embedded GPS track found in this file." else null,
            )
        }
    }

    private fun buildStats(points: List<GpsData>): TripStats {
        if (points.isEmpty()) return TripStats.EMPTY
        var dist = 0.0
        for (i in 1 until points.size) dist += haversineKm(points[i - 1], points[i])
        val speeds = points.map { it.speedKmh }.filter { it >= 0 }
        return TripStats(
            points = points,
            distanceKm = dist,
            maxSpeedKmh = speeds.maxOrNull() ?: 0.0,
            avgSpeedKmh = if (speeds.isNotEmpty()) speeds.average() else 0.0,
            startedAt = points.first().datetime,
            endedAt = points.last().datetime,
        )
    }

    private fun haversineKm(a: GpsData, b: GpsData): Double {
        val r = 6371.0
        val dLat = Math.toRadians(b.latitude - a.latitude)
        val dLon = Math.toRadians(b.longitude - a.longitude)
        val la1 = Math.toRadians(a.latitude); val la2 = Math.toRadians(b.latitude)
        val h = sin2(dLat) + cos(la1) * cos(la2) * sin2(dLon)
        return 2 * r * asin(sqrt(h).coerceAtMost(1.0))
    }
    private fun sin2(v: Double): Double { val s = kotlin.math.sin(v / 2); return s * s }
}
