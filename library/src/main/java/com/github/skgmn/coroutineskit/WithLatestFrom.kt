@file:Suppress("UNCHECKED_CAST")

package com.github.skgmn.coroutineskit

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch

fun <T, T1, R> Flow<T>.withLatestFrom(
    other: Flow<T1>,
    transform: suspend (T, T1) -> R
): Flow<R> {
    return withLatestFromImpl(
        arrayOf(other)
    ) {
        transform(
            it[0] as T,
            it[1] as T1
        )
    }
}

fun <T, T1, T2, R> Flow<T>.withLatestFrom(
    other1: Flow<T1>,
    other2: Flow<T2>,
    transform: suspend (T, T1, T2) -> R
): Flow<R> {
    return withLatestFromImpl(
        arrayOf(other1, other2)
    ) {
        transform(
            it[0] as T,
            it[1] as T1,
            it[2] as T2
        )
    }
}

fun <T, T1, T2, T3, R> Flow<T>.withLatestFrom(
    other1: Flow<T1>,
    other2: Flow<T2>,
    other3: Flow<T3>,
    transform: suspend (T, T1, T2, T3) -> R
): Flow<R> {
    return withLatestFromImpl(
        arrayOf(other1, other2, other3)
    ) {
        transform(
            it[0] as T,
            it[1] as T1,
            it[2] as T2,
            it[3] as T3
        )
    }
}

fun <T, T1, T2, T3, T4, R> Flow<T>.withLatestFrom(
    other1: Flow<T1>,
    other2: Flow<T2>,
    other3: Flow<T3>,
    other4: Flow<T4>,
    transform: suspend (T, T1, T2, T3, T4) -> R
): Flow<R> {
    return withLatestFromImpl(
        arrayOf(other1, other2, other3, other4)
    ) {
        transform(
            it[0] as T,
            it[1] as T1,
            it[2] as T2,
            it[3] as T3,
            it[4] as T4
        )
    }
}

fun <T, T1, T2, T3, T4, T5, R> Flow<T>.withLatestFrom(
    other1: Flow<T1>,
    other2: Flow<T2>,
    other3: Flow<T3>,
    other4: Flow<T4>,
    other5: Flow<T5>,
    transform: suspend (T, T1, T2, T3, T4, T5) -> R
): Flow<R> {
    return withLatestFromImpl(
        arrayOf(other1, other2, other3, other4, other5)
    ) {
        transform(
            it[0] as T,
            it[1] as T1,
            it[2] as T2,
            it[3] as T3,
            it[4] as T4,
            it[5] as T5
        )
    }
}

fun <T, T1, T2, T3, T4, T5, T6, R> Flow<T>.withLatestFrom(
    other1: Flow<T1>,
    other2: Flow<T2>,
    other3: Flow<T3>,
    other4: Flow<T4>,
    other5: Flow<T5>,
    other6: Flow<T6>,
    transform: suspend (T, T1, T2, T3, T4, T5, T6) -> R
): Flow<R> {
    return withLatestFromImpl(
        arrayOf(other1, other2, other3, other4, other5, other6)
    ) {
        transform(
            it[0] as T,
            it[1] as T1,
            it[2] as T2,
            it[3] as T3,
            it[4] as T4,
            it[5] as T5,
            it[6] as T6
        )
    }
}

fun <T, T1, T2, T3, T4, T5, T6, T7, R> Flow<T>.withLatestFrom(
    other1: Flow<T1>,
    other2: Flow<T2>,
    other3: Flow<T3>,
    other4: Flow<T4>,
    other5: Flow<T5>,
    other6: Flow<T6>,
    other7: Flow<T7>,
    transform: suspend (T, T1, T2, T3, T4, T5, T6, T7) -> R
): Flow<R> {
    return withLatestFromImpl(
        arrayOf(other1, other2, other3, other4, other5, other6, other7)
    ) {
        transform(
            it[0] as T,
            it[1] as T1,
            it[2] as T2,
            it[3] as T3,
            it[4] as T4,
            it[5] as T5,
            it[6] as T6,
            it[7] as T7
        )
    }
}

fun <T, T1, T2, T3, T4, T5, T6, T7, T8, R> Flow<T>.withLatestFrom(
    other1: Flow<T1>,
    other2: Flow<T2>,
    other3: Flow<T3>,
    other4: Flow<T4>,
    other5: Flow<T5>,
    other6: Flow<T6>,
    other7: Flow<T7>,
    other8: Flow<T8>,
    transform: suspend (T, T1, T2, T3, T4, T5, T6, T7, T8) -> R
): Flow<R> {
    return withLatestFromImpl(
        arrayOf(other1, other2, other3, other4, other5, other6, other7, other8)
    ) {
        transform(
            it[0] as T,
            it[1] as T1,
            it[2] as T2,
            it[3] as T3,
            it[4] as T4,
            it[5] as T5,
            it[6] as T6,
            it[7] as T7,
            it[8] as T8
        )
    }
}

fun <T, R> Flow<T>.withLatestFrom(
    others: Array<Flow<T>>,
    transform: suspend (Array<T>) -> R
): Flow<R> {
    return withLatestFromImpl(others as Array<Flow<Any?>>, transform as suspend (Array<Any?>) -> R)
}

@Suppress("UNCHECKED_CAST")
private fun <R> Flow<*>.withLatestFromImpl(
    others: Array<Flow<Any?>>,
    transform: suspend (Array<Any?>) -> R
): Flow<R> {
    return flow {
        coroutineScope {
            val latestValues = Array<Any?>(others.size) { Uninitialized }
            val outerScope = this

            others.forEachIndexed { i, other ->
                launch {
                    try {
                        other.collect {
                            synchronized(latestValues) {
                                latestValues[i] = it
                            }
                        }
                    } catch (e: CancellationException) {
                        outerScope.cancel(e)
                    }
                }
            }

            collect { item ->
                val values = synchronized(latestValues) {
                    Array(1 + others.size) {
                        if (it == 0) {
                            item
                        } else {
                            latestValues[it - 1]
                        }
                    }
                }
                if (values.all { it !== Uninitialized }) {
                    emit(transform(values))
                }
            }
        }
    }
}