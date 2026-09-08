package com.sameuo.dashcam.data.gps

/** One decoded GPS sample embedded in a Novatek recording. */
data class GpsData(
    val datetime: String,
    val latitude: Double,
    val longitude: Double,
    /** Speed in km/h. */
    val speedKmh: Double,
    /** Heading/track in degrees, if present. */
    val track: Double? = null,
    val altitude: Double? = null,
    val magneticVariation: String? = null,
    /** G-sensor accelerometer triplet x/y/z, if present. */
    val accelerometer: DoubleArray? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GpsData) return false
        return datetime == other.datetime && latitude == other.latitude && longitude == other.longitude
    }
    override fun hashCode(): Int = 31 * datetime.hashCode() + latitude.hashCode()
}
