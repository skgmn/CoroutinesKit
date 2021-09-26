package com.github.skgmn.coroutineskit

import kotlinx.coroutines.CancellationException

internal class CollectorDisposedException : CancellationException() {
    override fun fillInStackTrace(): Throwable {
        stackTrace = emptyArray()
        return this
    }
}