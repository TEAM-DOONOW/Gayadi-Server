package com.gayadi.server.route

import com.gayadi.server.common.Location

interface RouteProvider {
    fun estimate(origin: Location, destination: Location, phase: String): RouteEstimate

    data class RouteEstimate(
        val durationMinutes: Int,
        val transferCount: Int,
        val fare: Int,
        val summary: String
    )
}
