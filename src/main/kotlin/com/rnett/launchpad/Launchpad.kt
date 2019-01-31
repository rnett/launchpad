package com.rnett.launchpad

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.math.min

suspend inline fun <E> ReceiveChannel<E>.consumeEach(
    maxConcurrency: Int,
    initialConcurrency: Int = 10,
    launchStep: Int = 1,
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
                while (isActive && !(isClosedForReceive && isEmpty)) {
                    busy.incrementAndGet()
                    action(this@consumeEach.receive())
                    busy.decrementAndGet()
                }
            }
        }

        if (maxConcurrency > initialConcurrency || maxConcurrency <= 0) {
            while (isActive && !(isClosedForReceive && isEmpty) && (workers.size < maxConcurrency || maxConcurrency <= 0)) {
                if (busy.get() == workers.size) {
                    val received = receive()

                    workers += launch {
                        busy.incrementAndGet()
                        action(received)
                        busy.decrementAndGet()

                        while (isActive && !(isClosedForReceive && isEmpty)) {
                            busy.incrementAndGet()
                            action(this@consumeEach.receive())
                            busy.decrementAndGet()
                        }
                    }

                    for (i in (1..launchStep)) {

                        if (workers.size >= maxConcurrency && maxConcurrency > 0)
                            break

                        workers += launch {
                            while (isActive && !(isClosedForReceive && isEmpty)) {
                                busy.incrementAndGet()
                                action(this@consumeEach.receive())
                                busy.decrementAndGet()
                            }
                        }
                    }

                }
                delay(10)
            }
        }

        workers.joinAll()
    }

inline fun <E, R> ReceiveChannel<E>.map(
    maxConcurrency: Int,
    initialConcurrency: Int = 10,
    launchStep: Int = 1,
    coroutineContext: CoroutineContext = EmptyCoroutineContext,
    crossinline transform: suspend (E) -> R
) =
    GlobalScope.produce(coroutineContext) {
        this@map.consumeEach(maxConcurrency, initialConcurrency, launchStep, coroutineContext) {
            val r = transform(it)
            this.send(r)
        }
    }

private data class RunwayLaunch<R>(val block: suspend () -> R, val result: CompletableDeferred<R>)

class Launchpad<R>(
    val limit: Int,
    val initialConcurrency: Int = limit,
    val launchStep: Int = 1,
    override val coroutineContext: CoroutineContext = Dispatchers.Default
) : CoroutineScope {

    private val used = AtomicInteger(0)

    private val actionQueue = actor<RunwayLaunch<R>>(capacity = Channel.UNLIMITED) {
        this@actor.consumeEach(limit, initialConcurrency, launchStep, this@Launchpad.coroutineContext) {
            val result = it.block()
            it.result.complete(result)
            used.decrementAndGet()
        }
    }

    fun add(block: suspend () -> R): Deferred<R> {
        used.incrementAndGet()
        val result = CompletableDeferred<R>()
        actionQueue.sendBlocking(RunwayLaunch(block, result))
        return result
    }

    operator fun invoke(block: suspend () -> R) = add(block)
}

@InternalCoroutinesApi
inline fun <T, R, Resource> doWithLimitedResource(
    limit: Int, data: Iterable<T>,
    crossinline resourceBuilder: () -> Resource,
    crossinline action: (Resource, T) -> R
): List<Deferred<R>> {
    val launchpad = Launchpad<R>(limit)
    return data.map {
        launchpad {
            val resource = resourceBuilder()
            action(resource, it)
        }
    }

}

@InternalCoroutinesApi
inline fun <T, R, Resource> Iterable<T>.doWithLimitedResource(
    limit: Int,
    crossinline resourceBuilder: () -> Resource,
    crossinline action: (Resource, T) -> R
) =
    doWithLimitedResource(limit, this, resourceBuilder, action)

/*
@InternalCoroutinesApi
fun main() {
    val launchpad = Launchpad<Int>(10)

    runBlocking {
        (1..100).map { i ->
            launchpad {
                println(i)
                delay(1000)
                i
            }.let {
                    println("Done: ${it.await()}")
            }
        }
    }


    println("\n")

    runBlocking {
        val r = launchpad.closeAndGetResults()

        println(r)
        println("Size: ${r.size}")
        println("Sum: ${r.sum()}, should be ${(1..100).sum()}")
    }

}*/
