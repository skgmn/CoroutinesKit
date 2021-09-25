package com.github.skgmn.coroutineutils.lifecycle

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.flow.StateFlow

fun LifecycleOwner.isAtLeast(state: Lifecycle.State): StateFlow<Boolean> =
    lifecycle.isAtLeast(state)