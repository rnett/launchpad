package com.rnett.launchpad

import kotlinx.coroutines.*

//TODO double extension to get rid of CoroutineScope param

suspend fun <K, V> Map<K, Deferred<V>>.awaitValues() =
    mapValues { it.value.await() }

suspend inline fun <T> Iterable<T>.mapAndJoinAll(block: (T) -> Job) =
    map(block).joinAll()

suspend inline fun <T, R> Iterable<T>.mapAndAwaitAll(block: (T) -> Deferred<R>) =
    map(block).awaitAll()

suspend inline fun <T> Iterable<T>.launchAndJoinAll(
    scope: CoroutineScope,
    crossinline block: suspend CoroutineScope.(T) -> Unit
) =
    mapAndJoinAll { scope.launch { block(it) } }

suspend inline fun <T, R> Iterable<T>.asyncAndAwaitAll(
    scope: CoroutineScope,
    crossinline block: suspend CoroutineScope.(T) -> R
) =
    mapAndAwaitAll { scope.async { block(it) } }

suspend inline fun <K, V, R> Map<K, V>.mapAndAwaitAllValues(block: (Map.Entry<K, V>) -> Deferred<R>) =
    mapValues(block).awaitValues()