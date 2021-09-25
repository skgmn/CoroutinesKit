package com.github.skgmn.coroutineutils.lifecycle

import androidx.lifecycle.LiveData
import com.github.skgmn.coroutineutils.listenerStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow

fun <T: Any> LiveData<T>.toStateFlow(): StateFlow<T?> {
    return listenerStateFlow(value, Dispatchers.Main.immediate) {
        val observer: (T) -> Unit = { emit(it) }
        observeForever(observer)
        invokeOnClose { removeObserver(observer) }
    }
}