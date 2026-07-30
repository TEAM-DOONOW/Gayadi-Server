package com.gayadi.server.common

import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull

data class Location(
    @field:NotBlank val label: String,
    @field:NotNull @field:DecimalMin("-90.0") @field:DecimalMax("90.0") val latitude: Double,
    @field:NotNull @field:DecimalMin("-180.0") @field:DecimalMax("180.0") val longitude: Double
)
