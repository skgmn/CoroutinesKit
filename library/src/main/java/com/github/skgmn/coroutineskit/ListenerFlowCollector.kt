package com.github.skgmn.coroutineskit

interface ListenerFlowCollector<T> {
    fun emit(value: T)
    fun invokeOnClose(block: () -> Unit)
}