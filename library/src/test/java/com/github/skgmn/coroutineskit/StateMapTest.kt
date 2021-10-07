package com.github.skgmn.coroutineskit

import io.mockk.spyk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StateMapTest {
    private lateinit var source: MutableStateFlow<Int>
    private lateinit var transformer: (Int) -> Int
    private lateinit var transformed: StateFlow<Int>

    @Before
    fun setUp() {
        source = MutableStateFlow(123)
        transformer = spyk({ it * 2 })
        transformed = source.stateMap(transformer)
    }

    @Test
    fun queryValueBeforeCollecting() {
        assertEquals(246, transformed.value)

        source.value = 456
        assertEquals(912, transformed.value)
    }

    @Test
    fun queryValueWhileCollecting() = runBlockingTest {
        val job = launch {
            transformed.collect()
        }
        assertEquals(123 * 2, transformed.value)

        source.value = 456
        assertEquals(456 * 2, transformed.value)

        job.cancel()
    }

    @Test
    fun queryValueAfterCollecting() = runBlockingTest {
        val job = launch {
            transformed.collect()
        }
        source.value = 456
        assertEquals(456 * 2, transformed.value)
        job.cancel()

        source.value = 789
        assertEquals(789 * 2, transformed.value)
    }

    @Test
    fun distinctValues() = runBlockingTest {
        val listDeferred = async {
            transformed.take(3).toList()
        }
        source.value = 123
        source.value = 456
        source.value = 456
        source.value = 789
        source.value = 789

        val list = listDeferred.await()
        assertEquals(3, list.size)
        assertEquals(246, list[0])
        assertEquals(912, list[1])
        assertEquals(1578, list[2])
    }
}