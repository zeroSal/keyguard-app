package com.artemchep.keyguard.feature.navigation.state

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.artemchep.keyguard.common.usecase.GetDebugScreenDelay
import com.artemchep.keyguard.common.usecase.GetScreenState
import com.artemchep.keyguard.common.usecase.PutScreenState
import com.artemchep.keyguard.common.usecase.ShowMessage
import com.artemchep.keyguard.common.usecase.WindowCoroutineScope
import com.artemchep.keyguard.common.util.flow.persistingStateIn
import com.artemchep.keyguard.feature.navigation.LocalNavigationController
import com.artemchep.keyguard.feature.navigation.LocalNavigationNodeLogicalStack
import com.artemchep.keyguard.feature.navigation.N
import com.artemchep.keyguard.feature.navigation.navigationNodeStack
import com.artemchep.keyguard.platform.LocalLeContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flattenConcat
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.withIndex
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import org.kodein.di.compose.rememberInstance

private const val TAG = "ScreenState"

@Composable
fun <T> rememberScreenStateFlow(
    key: String,
    args: Array<Any?> = emptyArray(),
    rargs: Array<Any?> = emptyArray(),
    initial: T,
    init: suspend RememberStateFlowScope.() -> Flow<T>,
): StateFlow<T> {
    val finalTag = N.tag(TAG)
    // Key is the one provided by the user or
    // the one generated by the compose runtime.
    val finalKey = navigationNodeStack() + ":" + key

    val f = LocalNavigationNodeLogicalStack.current.last()
    val c = LocalNavigationController.current
    val viewModel = f.vm

    val context = LocalLeContext

    val colorSchemeState = rememberUpdatedState(MaterialTheme.colorScheme)

    val toastService by rememberInstance<ShowMessage>()
    val getScreenState by rememberInstance<GetScreenState>()
    val getDebugScreenDelay by rememberInstance<GetDebugScreenDelay>()
    val putScreenState by rememberInstance<PutScreenState>()
    val windowCoroutineScope by rememberInstance<WindowCoroutineScope>()
    val json by rememberInstance<Json>()
    return remember(
        finalKey,
        viewModel,
        c,
        toastService,
        getScreenState,
        putScreenState,
        windowCoroutineScope,
        json,
        context,
        *rargs,
    ) {
        val now = Clock.System.now()
        val flow: RememberStateFlowScopeZygote.() -> Flow<T> = {
            val structureFlow = flow {
                val shouldDelay = getDebugScreenDelay().firstOrNull() == true
                if (shouldDelay) {
                    delay(5500L)
                }

                val structure = withContext(Dispatchers.Default) {
                    init()
                }
                emit(structure)
            }
                // We don't want to recreate a state producer
                // each time we re-subscribe to it.
                .shareIn(this, SharingStarted.Lazily, 1)
            val stateFlow = structureFlow
                .flattenConcat()
                .withIndex()
                .map { it.value }
                .flowOn(Dispatchers.Default)
            merge(
                stateFlow,
                keepAliveFlow
                    .filter { false } as Flow<T>,
            )
                .persistingStateIn(screenScope, SharingStarted.WhileSubscribed(), initial)
        }
        val flow2 = viewModel.getOrPut(
            finalKey,
            c,
            toastService,
            getScreenState,
            putScreenState,
            windowCoroutineScope,
            json,
            f.id,
            key,
            context,
            colorSchemeState,
            flow,
        ) as StateFlow<T>
        flow2
    }
}
