package com.github.skgmn.coroutineskit

import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.CoroutineContext

fun <T> listenerSharedFlow(
    replay: Int = 0,
    extraBufferCapacity: Int = Channel.UNLIMITED,
    onBufferOverflow: BufferOverflow = BufferOverflow.DROP_OLDEST,
    context: CoroutineContext? = null,
    block: ListenerFlowCollector<T>.() -> Unit
): SharedFlow<T> {
    require(onBufferOverflow != BufferOverflow.SUSPEND) {
        "SUSPEND mode is not supported because listeners are not suspend functions."
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
            withContextOrRun(context) { block() }
        }
        try {
            sharedFlow.collect(collector)
        } finally {
            decreaseRefCount()?.onClose?.let {
                withContextOrRun(context) { it() }
            }
        }
    }

    override val replayCache: List<T>
        get() = sharedFlow.replayCache

    override fun emit(value: T) {
        check(sharedFlow.tryEmit(value)) { "This should not haapen" }
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