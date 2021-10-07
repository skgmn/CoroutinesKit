package com.github.skgmn.coroutineskit

import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector

@OptIn(InternalCoroutinesApi::class)
fun <T> defer(block: suspend () -> Flow<T>): Flow<T> {
    return object : Flow<T> {
        override suspend fun collect(collector: FlowCollector<T>) {
            block().collect(collector)
        }
    }
}