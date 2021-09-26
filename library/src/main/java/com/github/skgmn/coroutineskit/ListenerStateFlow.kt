package com.github.skgmn.coroutineskit

import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.CoroutineContext

/**
 * Turns observer pattern into StateFlow. It can be used like this:
 *
 * ```kotlin
 * val stateFlow = listenerStateFlow(
 *     onGetInitialValue = { initialValue },
 *     context = Dispatchers.Main.immediate
 * ) {
 *     val listener = { emit(it) }
 *     addListener(listener)
 *     invokeOnClose { removeListener(listener) }
 * }
 * ```
 *
 * @param onGetInitialValue A lambda that provides initial value of this StateFlow. This is called
 *   when the first subscriber is registered, or whenever value is queried when there are no
 *   subscribers
 * @param context [CoroutineContext] which [block] runs with. When it's null, it would be run with
 *   the same coroutine context of the first flow collector.
 */
fun <T> listenerStateFlow(
    onGetInitialValue: () -> T,
    context: CoroutineContext? = null,
    block: ListenerFlowCollector<T>.() -> Unit
): StateFlow<T> {
    return ListenerStateFlow(onGetInitialValue, context, block)
}

private class ListenerStateFlow<T>(
    private val onGetInitialValue: () -> T,
    private val context: CoroutineContext?,
    private val block: ListenerFlowCollector<T>.() -> Unit
) : StateFlow<T> {
    private val refCountState = AtomicReference<RefCountState<T>?>()

    @OptIn(InternalCoroutinesApi::class)
    override suspend fun collect(collector: FlowCollector<T>) {
        val state = increaseRefCount()
        if (state.refCount == 1) {
            context?.let { withContext(it) { state.block() } } ?: state.block()
        }
        try {
            state.stateFlow.collect(collector)
        } finally {
            decreaseRefCount()?.onClose?.let { onClose ->
                context?.let { withContext(it + NonCancellable) { onClose() } } ?: onClose()
            }
        }
    }

    override val replayCache: List<T>
        get() = refCountState.get()?.stateFlow?.replayCache ?: listOf(onGetInitialValue())
    override val value: T
        get() {
            val state = refCountState.get()
            return if (state != null) {
                state.stateFlow.value
            } else {
                onGetInitialValue()
            }
        }

    private fun increaseRefCount(): RefCountState<T> {
        val newStateFlow by lazy(LazyThreadSafetyMode.NONE) {
            MutableStateFlow(onGetInitialValue())
        }
        while (true) {
            val curState = refCountState.get()
            val newState = curState?.run { copy(refCount = refCount + 1) }
                ?: RefCountState(this, 1, null, newStateFlow)
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
        val outer: ListenerStateFlow<T>,
        val refCount: Int,
        val onClose: (() -> Unit)?,
        val stateFlow: MutableStateFlow<T>
    ) : ListenerFlowCollector<T> {
        override fun emit(value: T): Boolean {
            stateFlow.value = value
            return true
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