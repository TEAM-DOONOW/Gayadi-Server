package com.gayadi.server

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class GayadiServerApplication

fun main(args: Array<String>) {
    runApplication<GayadiServerApplication>(*args)
}
