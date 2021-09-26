package com.github.skgmn.coroutineskit

interface ListenerFlowCollector<T> {
    fun emit(value: T): Boolean
    fun invokeOnClose(block: () -> Unit)
}