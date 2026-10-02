package com.greenfodor.ppremotece.core.domain.result

sealed interface DataError : Error {
    enum class Network : DataError {
        NO_CONNECTION,
        TIMEOUT,
        NOT_FOUND,
        SERVER,
        SERIALIZATION,
        UNKNOWN
    }

    enum class Local : DataError {
        WRITE_FAILED
    }
}
