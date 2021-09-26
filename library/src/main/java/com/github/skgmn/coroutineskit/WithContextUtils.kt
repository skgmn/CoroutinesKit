package com.github.skgmn.coroutineskit

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

internal suspend fun <R> withContextOrRun(
    context: CoroutineContext?,
    block: suspend CoroutineScope.() -> R
): R {
    return context?.let { withContext(it, block) }
        ?: coroutineScope { block() }
}