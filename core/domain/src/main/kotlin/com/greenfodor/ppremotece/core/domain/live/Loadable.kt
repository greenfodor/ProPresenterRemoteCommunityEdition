package com.greenfodor.ppremotece.core.domain.live

/**
 * Content fed by the status stream: [NotLoaded] until the connection's first frame for it,
 * [Loaded] after it, and [Unavailable] when ProPresenter rejected its subscription.
 */
sealed interface Loadable<out T> {
    data object NotLoaded : Loadable<Nothing>

    data object Unavailable : Loadable<Nothing>

    data class Loaded<out T>(
        val value: T
    ) : Loadable<T>
}

/** The loaded content passed through [transform]; other states as they are. */
fun <T, R> Loadable<T>.map(transform: (T) -> R): Loadable<R> =
    when (this) {
        is Loadable.Loaded -> Loadable.Loaded(transform(value))
        Loadable.NotLoaded -> Loadable.NotLoaded
        Loadable.Unavailable -> Loadable.Unavailable
    }

/** The loaded list, or an empty list in any other state. */
fun <T> Loadable<List<T>>.orEmpty(): List<T> = (this as? Loadable.Loaded)?.value.orEmpty()
