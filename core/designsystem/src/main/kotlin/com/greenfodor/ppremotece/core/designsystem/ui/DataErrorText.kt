package com.greenfodor.ppremotece.core.designsystem.ui

import com.greenfodor.ppremotece.core.designsystem.R
import com.greenfodor.ppremotece.core.domain.result.DataError

fun DataError.Network.toUiText(): UiText =
    UiText.StringResource(
        when (this) {
            DataError.Network.NO_CONNECTION -> R.string.error_no_connection
            DataError.Network.TIMEOUT -> R.string.error_timeout
            DataError.Network.NOT_FOUND -> R.string.error_not_found
            DataError.Network.SERVER -> R.string.error_server
            DataError.Network.SERIALIZATION -> R.string.error_serialization
            DataError.Network.UNKNOWN -> R.string.error_unknown
        }
    )
