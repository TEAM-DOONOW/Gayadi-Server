package com.gayadi.server.route

import com.gayadi.server.common.Location
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

@Component
@Profile("!external-route")
class LocalRouteProvider : RouteProvider {

    override fun estimate(origin: Location, destination: Location, phase: String): RouteProvider.RouteEstimate {
        val lat = origin.latitude - destination.latitude
        val lng = origin.longitude - destination.longitude
        val straightKm = sqrt(lat * lat + lng * lng) * 88.0
        val duration = max(12, (straightKm * 4.2 + 10).roundToInt())
        val transfers = when {
            duration > 45 -> 2
            duration > 25 -> 1
            else -> 0
        }
        val fare = 1_500 + transfers * 250
        return RouteProvider.RouteEstimate(
            duration, transfers, fare,
            "LOCAL_STUB: 대중교통 우선 예상 경로 (외부 경로 API 연동 전)"
        )
    }
}
