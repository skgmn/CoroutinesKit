package com.github.skgmn.coroutineskit

import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.CoroutineContext

/**
 * Turns observer pattern into StateFlow. It can be used like this:
 *
 * ```kotlin
 * val stateFlow = listenerStateFlow(initialValue, Dispatchers.Main.immediate) {
 *     val listener = { emit(it) }
 *     addListener(listener)
 *     invokeOnClose { removeListener(listener) }
 * }
 * ```
 *
 * @param context [CoroutineContext] which [block] runs with. When it's null, it would be run with
 *   the same coroutine context of the first flow collector.
 */
fun <T> listenerStateFlow(
    initialValue: T,
    context: CoroutineContext? = null,
    block: ListenerFlowCollector<T>.() -> Unit
): StateFlow<T> {
    return ListenerStateFlow(initialValue, context, block)
}

private class ListenerStateFlow<T>(
    initialValue: T,
    private val context: CoroutineContext?,
    private val block: ListenerFlowCollector<T>.() -> Unit
) : StateFlow<T>, ListenerFlowCollector<T> {
    private val stateFlow = MutableStateFlow(initialValue)
    private val listenerState = AtomicReference<ListenerState?>()

    @OptIn(InternalCoroutinesApi::class)
    override suspend fun collect(collector: FlowCollector<T>) {
        val state = increaseRefCount()
        if (state.refCount == 1) {
            withContextOrRun(context) { block() }
        }
        try {
            stateFlow.collect(collector)
        } finally {
            decreaseRefCount()?.onClose?.let {
                withContextOrRun(context) { it() }
            }
        }
    }

    override val replayCache: List<T>
        get() = stateFlow.replayCache
    override val value: T
        get() = stateFlow.value

    override fun emit(value: T) {
        stateFlow.value = value
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