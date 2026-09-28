package com.greenfodor.ppremotece.core.data.network

import com.greenfodor.ppremotece.core.domain.result.DataError
import com.greenfodor.ppremotece.core.domain.result.EmptyResult
import com.greenfodor.ppremotece.core.domain.result.Result
import io.ktor.client.call.body
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import io.ktor.serialization.ContentConvertException
import kotlinx.serialization.SerializationException
import java.io.IOException
import java.net.SocketTimeoutException

internal suspend inline fun <reified T> safeCall(execute: () -> HttpResponse): Result<T, DataError.Network> =
    try {
        val response = execute()
        if (response.status.isSuccess()) {
            Result.Success(
                response.body<T>()
            )
        } else {
            Result.Failure(response.status.toNetworkError())
        }
    } catch (e: IOException) {
        Result.Failure(e.toNetworkError())
    } catch (_: SerializationException) {
        Result.Failure(DataError.Network.SERIALIZATION)
    } catch (_: ContentConvertException) {
        Result.Failure(DataError.Network.SERIALIZATION)
    }

internal suspend inline fun safeEmptyCall(execute: () -> HttpResponse): EmptyResult<DataError.Network> =
    try {
        val response = execute()
        if (response.status.isSuccess()) Result.Success(Unit) else Result.Failure(response.status.toNetworkError())
    } catch (e: IOException) {
        Result.Failure(e.toNetworkError())
    }

internal fun HttpStatusCode.toNetworkError(): DataError.Network =
    when {
        this == HttpStatusCode.NotFound -> DataError.Network.NOT_FOUND
        value >= HttpStatusCode.InternalServerError.value -> DataError.Network.SERVER
        else -> DataError.Network.UNKNOWN
    }

internal fun IOException.toNetworkError(): DataError.Network =
    when (this) {
        is HttpRequestTimeoutException,
        is ConnectTimeoutException,
        is SocketTimeoutException -> DataError.Network.TIMEOUT
        else -> DataError.Network.NO_CONNECTION
    }
