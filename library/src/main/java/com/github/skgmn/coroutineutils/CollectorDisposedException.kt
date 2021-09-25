package com.github.skgmn.coroutineutils

import kotlinx.coroutines.CancellationException

internal class CollectorDisposedException : CancellationException() {
    override fun fillInStackTrace(): Throwable {
        stackTrace = emptyArray()
        return this
    }
}