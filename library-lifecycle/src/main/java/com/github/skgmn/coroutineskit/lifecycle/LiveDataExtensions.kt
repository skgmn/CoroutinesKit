package com.github.skgmn.coroutineskit.lifecycle

import androidx.lifecycle.LiveData
import com.github.skgmn.coroutineskit.listenerStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow

fun <T: Any> LiveData<T>.toStateFlow(): StateFlow<T?> {
    return listenerStateFlow(
        onGetInitialValue = { value },
        context = Dispatchers.Main.immediate
    ) {
        val observer: (T) -> Unit = { emit(it) }
        observeForever(observer)
        invokeOnClose { removeObserver(observer) }
    }
}