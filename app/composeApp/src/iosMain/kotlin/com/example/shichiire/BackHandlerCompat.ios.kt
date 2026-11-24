package com.example.shichiire

import androidx.compose.runtime.Composable

// iOS には戻るボタン（ハードウェア）がないので何もしない。画面内の「← 一覧へ」で戻る。
@Composable
actual fun BackHandlerCompat(enabled: Boolean, onBack: () -> Unit) {
}
