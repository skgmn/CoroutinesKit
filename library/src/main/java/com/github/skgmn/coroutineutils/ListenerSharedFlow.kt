package com.github.skgmn.coroutineutils

import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.CoroutineContext

fun <T> listenerSharedFlow(
    replay: Int = 0,
    context: CoroutineContext? = null,
    block: ListenerFlowCollector<T>.() -> Unit
): SharedFlow<T> {
    return ListenerSharedFlow(replay, context, block)
}

class ListenerSharedFlow<T> internal constructor(
    replay: Int,
    private val context: CoroutineContext?,
    private val block: ListenerFlowCollector<T>.() -> Unit
) : SharedFlow<T>, ListenerFlowCollector<T> {
    private val sharedFlow = MutableSharedFlow<T>(replay, Int.MAX_VALUE, BufferOverflow.DROP_OLDEST)
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
            decreaseRefCount()?.onClose?.invoke()
        }
    }

    override val replayCache: List<T>
        get() = sharedFlow.replayCache

    override fun emit(value: T) {
        sharedFlow.tryEmit(value)
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