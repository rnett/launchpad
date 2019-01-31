package com.rnett.launchpad

import kotlinx.coroutines.Dispatchers
import kotlin.coroutines.CoroutineContext

class Provider<P, R>(
    limit: Int,
    initialConcurrency: Int = limit,
    launchStep: Int = 1,
    coroutineContext: CoroutineContext = Dispatchers.Default,
    val product: () -> P
) {
    private val launchpad = Launchpad<R>(limit, initialConcurrency, launchStep, coroutineContext)

    fun queueUse(use: suspend (P) -> R) = launchpad {
        val provided = product()
        use(provided)
    }

    operator fun invoke(use: suspend (P) -> R) = queueUse(use)
}