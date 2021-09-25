# coroutineutils

## shareRefCount, stateRefCount

```kotlin
val flow: Flow<T> = ...
val sharedFlow: SharedFlow<T> = flow.shareRefCount()
val stateFlow: StateFlow<T> = flow.stateRefCount(initialValue)
```

Turns `Flow<T>` into `SharedFlow<T>` or `StateFlow<T>`. Unlike `shareIn()` or `stateIn()` it does not require any `CoroutineScope`. It subscribe the upstream when the downstream is firstly collected, and it cancels the upstream when the downstream is lastly cancelled so that it can be used like RxJava's `publish().refCount()` or `replay(1).refCount()`.

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
2. Developer has to consider backpressure every time `callbackFlow` is used.
3. `callbackFlow` cannot create `SharedFlow` and `StateFlow`.

## stateMap, stateCombine

Operators corresponding to `map` and `combine` keeping `StateFlow<T>` type.

```kotlin
val stateFlow: StateFlow<Int> = ...
val map1 = stateFlow.map { it * 2 }      // This is Flow<Int>
val map2 = stateFlow.stateMap { it * 2 } // This is StateFlow<Int>
```

It only accepts not suspending lambda.

## runWhile

Run block only while the given `Flow<Boolean>` or `StateFlow<Boolean>` emits true. It can be used similarly to RxJava's `takeUntil()` operator.

```kotlin
val conditionFlow: Flow<Boolean> = ...
runWhile(conditionFlow) {
   // Code here runs while conditionFlow emits true, and cancelled when conditionFlow emits false.
}
```

# coroutineutils-lifecycle

## isAtLeast

```kotlin
fun Lifecycle.isAtLeast(state: Lifecycle.State): StateFlow<Boolean>
fun LifecycleOwner.isAtLeast(state: Lifecycle.State): StateFlow<Boolean>
```

`Flow` version of `Lifecycle.State.isAtLeast`. It's useful when used with `runWhile`.

```kotlin
whenStarted {
    runWhile(isAtLeast(State.STARTED)) {
        // Code here runs "once" between onStart() and onStop().
    }
}
```

## LiveData.toStateFlow

```kotlin
fun <T: Any> LiveData<T>.toStateFlow(): StateFlow<T?>
```

Turns `LiveData<T>` into `StateFlow<T?>`.
