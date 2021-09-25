package com.github.skgmn.coroutineutils

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlin.coroutines.CoroutineContext

@OptIn(ExperimentalCoroutinesApi::class)
fun <T> listenerFlow(
    context: CoroutineContext? = null,
    block: ListenerFlowCollector<T>.() -> Unit
): Flow<T> {
    return channelFlow {
        var onClose: (() -> Unit)? = null
        val collector = object : ListenerFlowCollector<T> {
            override fun emit(value: T) {
                trySend(value)
            }

            override fun invokeOnClose(block: () -> Unit) {
                onClose = block
            }
        }
        collector.block()
        awaitClose { onClose?.invoke() }
    }.run {
        context?.let { flowOn(it) } ?: this
    }.buffer(Channel.UNLIMITED)
}