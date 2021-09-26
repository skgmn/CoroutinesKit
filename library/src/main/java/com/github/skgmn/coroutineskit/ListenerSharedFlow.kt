package com.github.skgmn.coroutineskit

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
    context: CoroutineContext? = null,
    block: ListenerFlowCollector<T>.() -> Unit
): SharedFlow<T> {
    // I don't know why but MutableStateFlow with zero capacity always fails with tryEmit()
    require(extraBufferCapacity > 0) {
        "extraBufferCapacity should be greater than zero, or value would never be delivered"
    }
    return ListenerSharedFlow(replay, extraBufferCapacity, onBufferOverflow, context, block)
}

private class ListenerSharedFlow<T>(
    private val replay: Int,
    private val extraBufferCapacity: Int,
    private val onBufferOverflow: BufferOverflow,
    private val context: CoroutineContext?,
    private val block: ListenerFlowCollector<T>.() -> Unit
) : SharedFlow<T> {
    private val refCountState = AtomicReference<RefCountState<T>?>()

    @OptIn(InternalCoroutinesApi::class)
    override suspend fun collect(collector: FlowCollector<T>) {
        val state = increaseRefCount()
        if (state.refCount == 1) {
            context?.let { withContext(it) { state.block() } } ?: state.block()
        }
        try {
            state.sharedFlow.collect(collector)
        } finally {
            decreaseRefCount()?.onClose?.let { onClose ->
                context?.let { withContext(it + NonCancellable) { onClose() } } ?: onClose()
            }
        }
    }

    override val replayCache: List<T>
        get() = refCountState.get()?.sharedFlow?.replayCache ?: emptyList()

    private fun increaseRefCount(): RefCountState<T> {
        val newSharedFlow by lazy(LazyThreadSafetyMode.NONE) {
            MutableSharedFlow<T>(replay, extraBufferCapacity, onBufferOverflow)
        }
        while (true) {
            val curState = refCountState.get()
            val newState = curState?.run { copy(refCount = refCount + 1) }
                ?: RefCountState(this, 1, null, newSharedFlow)
            if (refCountState.compareAndSet(curState, newState)) {
                return newState
            }
        }
    }

    private fun decreaseRefCount(): RefCountState<T>? {
        while (true) {
            val curState = refCountState.get() ?: return null
            val newState = if (curState.refCount == 1) {
                null
            } else {
                curState.copy(refCount = curState.refCount - 1)
            }
            if (refCountState.compareAndSet(curState, newState)) {
                return curState.takeIf { newState == null }
            }
        }
    }

    private data class RefCountState<T>(
        val outer: ListenerSharedFlow<T>,
        val refCount: Int,
        val onClose: (() -> Unit)?,
        val sharedFlow: MutableSharedFlow<T>
    ) : ListenerFlowCollector<T> {
        override fun emit(value: T): Boolean {
            return sharedFlow.tryEmit(value)
        }

        override fun invokeOnClose(block: () -> Unit) {
            while (true) {
                val curState = outer.refCountState.get() ?: return
                val newState = curState.copy(onClose = block)
                if (outer.refCountState.compareAndSet(curState, newState)) {
                    return
                }
            }
        }
    }
}