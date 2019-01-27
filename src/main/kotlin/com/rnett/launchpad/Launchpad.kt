package com.rnett.launchpad

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.actor
import kotlinx.coroutines.channels.sendBlocking
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.math.min

suspend inline fun <E> ReceiveChannel<E>.consumeEach(
    maxConcurrency: Int,
    initialConcurrency: Int = 10,
    coroutineContext: CoroutineContext = EmptyCoroutineContext,
    crossinline action: suspend (E) -> Unit
) =
    withContext(coroutineContext) {

        if (maxConcurrency <= 0)
            if (initialConcurrency > maxConcurrency)
                throw IllegalArgumentException("initialConcurrency must be less than or equal to maxConcurrency")
            else if (initialConcurrency < 0)
                throw IllegalArgumentException("Can not have a negative initialConcurrency")


        val busy = AtomicInteger(0)

        val workers = MutableList(min(maxConcurrency, initialConcurrency)) {
            launch {
                while (true) {
                    busy.incrementAndGet()
                    action(this@consumeEach.receive())
                    busy.decrementAndGet()
                }
            }
        }

        if (maxConcurrency > initialConcurrency || maxConcurrency <= 0) {
            while (this.isActive) {
                if (busy.get() == workers.size && (workers.size < maxConcurrency || maxConcurrency <= 0)) {
                    val recieved = receive()

                    workers += launch {
                        busy.incrementAndGet()
                        action(recieved)
                        busy.decrementAndGet()

                        while (true) {
                            busy.incrementAndGet()
                            action(this@consumeEach.receive())
                            busy.decrementAndGet()
                        }
                    }

                    println("Added worker: at ${workers.size}")
                }
                delay(10)
            }
        }
        workers.joinAll()
    }


class Launchpad<R>(
    val limit: Int,
    val initialConcurrency: Int = limit,
    override val coroutineContext: CoroutineContext = Dispatchers.Default
) : CoroutineScope {

    private val used = AtomicInteger(0)

    private val waiting = actor<suspend () -> R>(capacity = Channel.UNLIMITED, start = CoroutineStart.LAZY) {
        consumeEach(limit, initialConcurrency) {
            resulter.send(it())

            used.decrementAndGet()
        }
    }

    private val resulter = Channel<R>(capacity = Channel.UNLIMITED)

    suspend fun closeAndGetResults(): List<R> {
        val result = mutableListOf<R>()

        while (waiting.isFull || used.get() > 0) {
        }

        while (!(resulter.isEmpty)) {
            val r = resulter.receive()
            result += r
        }
        waiting.close()
        resulter.close()
        return result
    }

    fun add(block: suspend () -> R) {
        used.incrementAndGet()
        waiting.sendBlocking(block)
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

fun main() {
    val launchpad = Launchpad<Int>(15, 5)
    (1..100).forEach {
        launchpad {
            println(it)
            delay(1000)
            it
        }
    }

    println("Launching done")

    runBlocking {
        //delay(1000 * 20)
        println("Size: " + launchpad.closeAndGetResults().size)
    }
}