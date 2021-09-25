package com.github.skgmn.coroutineutils.lifecycle

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.github.skgmn.coroutineutils.listenerStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow

fun Lifecycle.isAtLeast(state: Lifecycle.State): StateFlow<Boolean> {
    return listenerStateFlow(currentState.isAtLeast(state), Dispatchers.Main.immediate) {
        val observer = LifecycleEventObserver { source, _ ->
            emit(source.lifecycle.currentState.isAtLeast(state))
        }
        addObserver(observer)
        invokeOnClose { removeObserver(observer) }
    }
}