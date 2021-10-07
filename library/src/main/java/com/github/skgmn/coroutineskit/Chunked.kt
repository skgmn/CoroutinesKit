@file:OptIn(ExperimentalCoroutinesApi::class)

package com.github.skgmn.coroutineskit

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow

fun <T> Flow<T>.chunked(count: Int): Flow<List<T>> {
    return flow {
        val buffer = mutableListOf<T>()
        collect {
            buffer += it
            if (buffer.size >= count) {
                val list = buffer.toList()
                buffer.clear()
                emit(list)
            }
        }
        if (buffer.isNotEmpty()) {
            emit(buffer)
        }
    }
}

fun <T> Flow<T>.chunked(timeMillis: Long): Flow<List<T>> {
    return channelFlow {
        coroutineScope {
            val buffer = mutableListOf<T>()
            val timerJob = launch {
                while (isActive) {
                    delay(timeMillis)
                    val list = synchronized(buffer) {
                        buffer.toList().also { buffer.clear() }
                    }
                    send(list)
                }
            }
            try {
                collect {
                    synchronized(buffer) { buffer += it }
                }
                timerJob.cancel()
                val finalList = synchronized(buffer) { buffer.toList() }
                if (finalList.isNotEmpty()) {
                    send(finalList)
                }
            } finally {
                timerJob.cancel()
            }
        }
    }
}

fun <T> Flow<T>.chunked(count: Int, timeMillis: Long): Flow<List<T>> {
    return channelFlow {
        coroutineScope {
            val buffer = mutableListOf<T>()
            suspend fun CoroutineScope.timer() {
                while (isActive) {
                    delay(timeMillis)
                    val list = synchronized(buffer) {
                        buffer.toList().also { buffer.clear() }
                    }
                    send(list)
                }
            }

            var timerJob = launch { timer() }
            try {
                collect {
                    val list = synchronized(buffer) {
                        buffer += it
                        if (buffer.size >= count) {
                            buffer.toList().also {
                                buffer.clear()
                                timerJob.cancel()
                            }
                        } else {
                            null
                        }
                    }
                    list?.let {
                        send(it)
                        timerJob = launch { timer() }
                    }
                }
                timerJob.cancel()
                val finalList = synchronized(buffer) { buffer.toList() }
                if (finalList.isNotEmpty()) {
                    send(finalList)
                }
            } finally {
                timerJob.cancel()
            }
        }
    }
}