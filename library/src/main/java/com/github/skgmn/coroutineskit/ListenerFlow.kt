package com.github.skgmn.coroutineskit

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlin.coroutines.CoroutineContext

/**
 * Turns observer pattern into Flow. It can be used like this:
 *
 * ```kotlin
 * val flow = listenerFlow(Dispatchers.Main.immediate) {
 *     val listener = { emit(it) }
 *     addListener(listener)
 *     invokeOnClose { removeListener(listener) }
 * }
 * ```
 *
 * @param context [CoroutineContext] which [block] runs with. When it's null, it would be run with
 *   the same coroutine context of flow collector.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun <T> listenerFlow(
    extraBufferCapacity: Int = 256,
    onBufferOverflow: BufferOverflow = BufferOverflow.DROP_OLDEST,
    context: CoroutineContext? = Dispatchers.Main.immediate,
    block: ListenerFlowCollector<T>.() -> Unit
): Flow<T> {
    return channelFlow {
        var onClose: (() -> Unit)? = null
        val collector = object : ListenerFlowCollector<T> {
            override fun emit(value: T): Boolean {
                return trySend(value).isSuccess
            }

            override fun invokeOnClose(block: () -> Unit) {
                onClose = block
            }
        }
        collector.block()
        awaitClose { onClose?.invoke() }
    }.run {
        context?.let { flowOn(it) } ?: this
    }.buffer(extraBufferCapacity, onBufferOverflow)
}