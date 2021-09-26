package com.github.skgmn.coroutineskit

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.CoroutineContext

/**
 * Turns observer pattern into SharedFlow. It can be used like this:
 *
 * ```kotlin
 * val sharedFlow = listenerSharedFlow(Dispatchers.Main.immediate) {
 *     val listener = { emit(it) }
 *     addListener(listener)
 *     invokeOnClose { removeListener(listener) }
 * }
 * ```
 *
 * @param context [CoroutineContext] which [block] runs with. When it's null, it would be run with
 *   the same coroutine context of the first flow collector.
 */
fun <T> listenerSharedFlow(
    replay: Int = 0,
    extraBufferCapacity: Int = 256,
    onBufferOverflow: BufferOverflow = BufferOverflow.DROP_OLDEST,
    context: CoroutineContext? = Dispatchers.Main.immediate,
    block: ListenerFlowCollector<T>.() -> Unit
): SharedFlow<T> {
    // I don't know why but MutableStateFlow with zero capacity always fails with tryEmit()
    require(extraBufferCapacity > 0) {
        "extraBufferCapacity should be greater than zero, or value would never be delivered"
    }
    return ListenerSharedFlow(replay, extraBufferCapacity, onBufferOverflow, context, block)
}

private class ListenerSharedFlow<T>(
    replay: Int,
    extraBufferCapacity: Int,
    onBufferOverflow: BufferOverflow,
    private val context: CoroutineContext?,
    private val block: ListenerFlowCollector<T>.() -> Unit
) : SharedFlow<T>, ListenerFlowCollector<T> {
    private val sharedFlow = MutableSharedFlow<T>(replay, extraBufferCapacity, onBufferOverflow)
    private val listenerState = AtomicReference<ListenerState?>()

    @OptIn(InternalCoroutinesApi::class)
    override suspend fun collect(collector: FlowCollector<T>) {
        val state = increaseRefCount()
        if (state.refCount == 1) {
            context?.let { withContext(it) { block() } } ?: block()
        }
        try {
            sharedFlow.collect(collector)
        } finally {
            decreaseRefCount()?.onClose?.let { onClose ->
                context?.let { withContext(it + NonCancellable) { onClose() } } ?: onClose()
            }
        }
    }

    override val replayCache: List<T>
        get() = sharedFlow.replayCache

    override fun emit(value: T): Boolean {
        return sharedFlow.tryEmit(value)
    }

    override fun invokeOnClose(block: () -> Unit) {
        while (true) {
            val curState = listenerState.get() ?: return
            val newState = curState.copy(onClose = block)
            if (listenerState.compareAndSet(curState, newState)) {
                return
            }
        }
    }

    private fun increaseRefCount(): ListenerState {
        while (true) {
            val curState = listenerState.get()
            val newState = curState?.copy(refCount = curState.refCount + 1)
                ?: ListenerState(1, null)
            if (listenerState.compareAndSet(curState, newState)) {
                return newState
            }
        }
    }

    private fun decreaseRefCount(): ListenerState? {
        while (true) {
            val curState = listenerState.get() ?: return null
            val newState = if (curState.refCount == 1) {
                null
            } else {
                curState.copy(refCount = curState.refCount - 1)
            }
            if (listenerState.compareAndSet(curState, newState)) {
                return curState.takeIf { newState == null }
            }
        }
    }

    private data class ListenerState(
        val refCount: Int,
        val onClose: (() -> Unit)?
    )
}