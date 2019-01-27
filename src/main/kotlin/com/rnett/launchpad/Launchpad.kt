package com.rnett.launchpad

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.channels.actor
import kotlinx.coroutines.channels.consumeEach
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.CoroutineContext

class Launchpad<R>(val limit: Int, override val coroutineContext: CoroutineContext = Dispatchers.Default) :
    CoroutineScope {

    private val waiting = actor<suspend () -> R>(start = CoroutineStart.LAZY) {
        val runningCount = AtomicInteger(0)

        consumeEach {
            while (runningCount.get() >= limit && limit > 0) {
            }
            runningCount.incrementAndGet()
            resulter.send(async {
                val r = it()
                runningCount.decrementAndGet()
                r
            })
        }
    }

    private val resulter = Channel<Deferred<R>>(capacity = UNLIMITED)

    suspend fun closeAndGetResults(): List<R> {
        val result = mutableListOf<Deferred<R>>()

        while (waiting.isFull) {
        }

        while (!(resulter.isEmpty)) {
            result += resulter.receive()
        }
        waiting.close()
        resulter.close()
        return result.awaitAll()
    }

    fun add(block: suspend () -> R) = launch {
        waiting.send(block)
    }

    operator fun invoke(block: suspend () -> R) = add(block)
}

inline fun <T, R, Resource> doWithLimitedResource(
    limit: Int, data: Iterable<T>,
    crossinline resourceBuilder: () -> Resource,
    crossinline action: (Resource, T) -> R
): Launchpad<R> {
    val launchpad = Launchpad<R>(limit)
    data.forEach {
        launchpad {
            val resource = resourceBuilder()
            action(resource, it)
        }
    }
    return launchpad
}

inline fun <T, R, Resource> Iterable<T>.doWithLimitedResource(
    limit: Int,
    crossinline resourceBuilder: () -> Resource,
    crossinline action: (Resource, T) -> R
) =
    doWithLimitedResource(limit, this, resourceBuilder, action)

