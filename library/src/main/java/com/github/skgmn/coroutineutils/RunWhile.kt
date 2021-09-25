package com.github.skgmn.coroutineutils

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

@OptIn(DelicateCoroutinesApi::class)
suspend fun <R> runWhile(condition: Flow<Boolean>, block: suspend CoroutineScope.() -> R): R {
    return coroutineScope {
        if (condition.firstOrNull() != true) {
            CancellationException().let { cancel(it); throw it }
        }
        val outerScope = this
        val conditionWatcherJob = GlobalScope.launch {
            if (condition.dropWhile { it }.firstOrNull() == false) {
                outerScope.cancel()
            }
        }
        checkNotNull(coroutineContext[Job]).invokeOnCompletion {
            conditionWatcherJob.cancel()
        }
        block()
    }
}

@OptIn(DelicateCoroutinesApi::class)
suspend fun <R> runWhile(condition: StateFlow<Boolean>, block: suspend CoroutineScope.() -> R): R {
    return coroutineScope {
        if (!condition.value) {
            CancellationException().let { cancel(it); throw it }
        }
        val outerScope = this
        val conditionWatcherJob = GlobalScope.launch {
            if (condition.dropWhile { it }.firstOrNull() == false) {
                outerScope.cancel()
            }
        }
        checkNotNull(coroutineContext[Job]).invokeOnCompletion {
            conditionWatcherJob.cancel()
        }
        block()
    }
}