package com.github.skgmn.coroutineskit

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.firstOrNull

/**
 * Run block only while the given Flow<Boolean> emits true. It can be used similarly to RxJava's
 * takeUntil() operator.
 */
@OptIn(DelicateCoroutinesApi::class)
suspend fun <R> runWhile(condition: Flow<Boolean>, block: suspend CoroutineScope.() -> R): R {
    return coroutineScope {
        if (condition.firstOrNull() != true) {
            CancelledByConditionException().let { cancel(it); throw it }
        }
        val outerScope = this
        val conditionWatcherJob = GlobalScope.launch {
            if (condition.dropWhile { it }.firstOrNull() == false) {
                outerScope.cancel(CancelledByConditionException())
            }
        }
        checkNotNull(coroutineContext[Job]).invokeOnCompletion {
            conditionWatcherJob.cancel()
        }
        block()
    }
}

/**
 * Run block only while the given StateFlow<Boolean> emits true. It can be used similarly to
 * RxJava's takeUntil() operator.
 */
@OptIn(DelicateCoroutinesApi::class)
suspend fun <R> runWhile(condition: StateFlow<Boolean>, block: suspend CoroutineScope.() -> R): R {
    return coroutineScope {
        if (!condition.value) {
            CancelledByConditionException().let { cancel(it); throw it }
        }
        val outerScope = this
        val conditionWatcherJob = GlobalScope.launch {
            if (condition.dropWhile { it }.firstOrNull() == false) {
                outerScope.cancel(CancelledByConditionException())
            }
        }
        checkNotNull(coroutineContext[Job]).invokeOnCompletion {
            conditionWatcherJob.cancel()
        }
        block()
    }
}

class CancelledByConditionException : CancellationException()