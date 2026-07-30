package com.gayadi.server.common

import org.springframework.http.HttpStatus

class ApiException(val status: HttpStatus, message: String) : RuntimeException(message)
