package com.github.skgmn.coroutineutils

interface ListenerFlowCollector<T> {
    fun emit(value: T)
    fun invokeOnClose(block: () -> Unit)
}