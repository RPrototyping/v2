package com.example.shichiire

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.shichiire.model.LocalPromiseStore
import com.example.shichiire.model.PromiseStore
import com.example.shichiire.model.RemotePromiseStore
import com.example.shichiire.time.currentInstant
import com.example.shichiire.ui.AppTheme
import com.example.shichiire.ui.DetailScreen
import com.example.shichiire.ui.HomeScreen
import com.example.shichiire.ui.LocalAppColors
import com.example.shichiire.ui.NewPromiseScreen
import com.example.shichiire.ui.SetupScreen
import kotlinx.coroutines.delay

/** 画面（ナビゲーションライブラリは使わず、状態で切り替える） */
sealed interface Screen {
    data object Home : Screen
    data class Detail(val id: String) : Screen
    data object New : Screen
}

/** 使い方。最初は Setup で選ぶ */
sealed interface Mode {
    data object Setup : Mode
    data object Local : Mode
    data class Remote(val baseUrl: String, val name: String) : Mode
}

/** Android と iOS で共通の入口 */
@Composable
fun App() {
    AppTheme {
        val localStore = remember { LocalPromiseStore() }
        var mode by remember { mutableStateOf<Mode>(Mode.Setup) }
        var screen by remember { mutableStateOf<Screen>(Screen.Home) }
        var now by remember { mutableStateOf(currentInstant()) }
        // 接続画面に戻ったとき、前に入れた値を残しておく
        var lastName by remember { mutableStateOf("") }
        var lastAddress by remember { mutableStateOf("") }

        // 残り時間の表示を更新する（「1分後（デモ用）」の期限切れもすぐ反映される）
        LaunchedEffect(Unit) {
            while (true) {
                delay(1_000)
                now = currentInstant()
            }
        }

        BackHandlerCompat(enabled = mode != Mode.Setup && screen != Screen.Home) { screen = Screen.Home }

        Surface(Modifier.fillMaxSize(), color = LocalAppColors.current.bg) {
            Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                when (val m = mode) {
                    Mode.Setup -> SetupScreen(
                        initialName = lastName,
                        initialAddress = lastAddress,
                        onLocal = { screen = Screen.Home; mode = Mode.Local },
                        onRemote = { url, name, address ->
                            lastName = name
                            lastAddress = address
                            screen = Screen.Home
                            mode = Mode.Remote(url, name)
                        },
                    )
                    Mode.Local -> Screens(localStore, screen, now, onScreen = { screen = it }, onSwitchMode = { mode = Mode.Setup })
                    is Mode.Remote -> {
                        val scope = rememberCoroutineScope()
                        val store = remember(m) { RemotePromiseStore(m.baseUrl, m.name, scope) }
                        DisposableEffect(store) {
                            val job = store.startPolling()
                            onDispose {
                                job.cancel()
                                store.close()
                            }
                        }
                        Screens(store, screen, now, onScreen = { screen = it }, onSwitchMode = { mode = Mode.Setup })
                    }
                }
            }
        }
    }
}

@Composable
private fun Screens(
    store: PromiseStore,
    screen: Screen,
    now: kotlinx.datetime.Instant,
    onScreen: (Screen) -> Unit,
    onSwitchMode: () -> Unit,
) {
    when (screen) {
        Screen.Home -> HomeScreen(
            store = store, now = now,
            onOpen = { onScreen(Screen.Detail(it)) },
            onNew = { onScreen(Screen.New) },
            onSwitchMode = onSwitchMode,
        )
        is Screen.Detail -> DetailScreen(
            store = store, id = screen.id, now = now,
            onBack = { onScreen(Screen.Home) },
        )
        Screen.New -> NewPromiseScreen(
            store = store, now = now,
            onCreated = { onScreen(Screen.Detail(it)) },
            onCancel = { onScreen(Screen.Home) },
        )
    }
}

/** Android の戻るボタン用。iOS では何もしない */
@Composable
expect fun BackHandlerCompat(enabled: Boolean, onBack: () -> Unit)
