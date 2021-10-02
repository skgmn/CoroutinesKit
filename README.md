# coroutineskit

```gradle
implementation "com.github.skgmn:coroutineskit:0.3.0"
```

## shareRefCount, stateRefCount

```kotlin
val flow: Flow<T> = ...
val sharedFlow: SharedFlow<T> = flow.shareRefCount()
val stateFlow: StateFlow<T> = flow.stateRefCount { initialValue }
```

Turns `Flow<T>` into `SharedFlow<T>` or `StateFlow<T>`. Unlike `shareIn()` or `stateIn()` it does not require any `CoroutineScope`. It subscribes the upstream when the downstream is firstly collected, and it cancels the upstream when the downstream is lastly completed so that it can be used like RxJava's `publish().refCount()` or `replay().refCount()`.

## listenerFlow, listenerSharedFlow, listenerStateFlow

A series corresponding to `callbackFlow`. They can be used like below.

```kotlin
val flow = listenerFlow(Dispatchers.Main.immediate) {
    val listener = { emit(it) }
    addListener(listener)
    invokeOnClose { removeListener(listener) }
}
```

These have been created because
1. `callbackFlow` is still experimental.
2. `callbackFlow` does not have proper default values for backpressure I think.
3. `callbackFlow` cannot create `SharedFlow` and `StateFlow`.

## stateMap, stateCombine

Operators corresponding to `map` and `combine` keeping `StateFlow<T>` type.

```kotlin
val stateFlow: StateFlow<Int> = ...
val map1 = stateFlow.map { it * 2 }      // This is Flow<Int>
val map2 = stateFlow.stateMap { it * 2 } // This is StateFlow<Int>
```

It only accepts non-suspend lambda.

## runWhile

Run block only while the given `Flow<Boolean>` or `StateFlow<Boolean>` emits true. It can be used similarly to RxJava's `takeUntil()` operator.

```kotlin
val conditionFlow: Flow<Boolean> = ...
runWhile(conditionFlow) {
   // Code here runs while conditionFlow emits true, and cancelled when conditionFlow emits false.
}
```

## capture

Same as RxJava's `withLatestFrom()`.

```kotlin
val flow: Flow<T>
val flow1: Flow<T1>
val flow2: Flow<T2>
flow.capture(flow1, flow2) { source, one, two ->
    someTransform(source, one, two)
}
```

## chunked

Same as RxJava's `buffer()`.

```kotlin
fun <T> Flow<T>.chunked(count: Int): Flow<List<T>>
fun <T> Flow<T>.chunked(timeMillis: Long): Flow<List<T>>
fun <T> Flow<T>.chunked(count: Int, timeMillis: Long): Flow<List<T>>
```

# coroutineskit-lifecycle

```gradle
implementation "com.github.skgmn:coroutineskit-lifecycle:0.3.0"
```

## isAtLeast

```kotlin
fun Lifecycle.isAtLeast(state: Lifecycle.State): StateFlow<Boolean>
fun LifecycleOwner.isAtLeast(state: Lifecycle.State): StateFlow<Boolean>
```

`Flow` version of `Lifecycle.State.isAtLeast`. It's useful when used with `runWhile`.

```kotlin
whenStarted {
    runWhile(isAtLeast(State.STARTED)) {
        // Code here starts on onStart() and stops on onStop().
    }
}
```

## LiveData.toStateFlow

```kotlin
fun <T: Any> LiveData<T>.toStateFlow(): StateFlow<T?>
```

Turns `LiveData<T>` into `StateFlow<T?>`.
