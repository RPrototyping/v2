package com.example.shichiire.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.shichiire.net.checkServer
import com.example.shichiire.net.normalizeServerUrl
import kotlinx.coroutines.launch

/**
 * 最初の画面。使い方を選ぶ。
 * - 2台で同期：同じWi-Fiにある中継サーバー（PCで ./gradlew :server:run）につなぐ
 * - 1台で使う：オフライン。端末を手渡しして2人で申告する
 */
@Composable
fun SetupScreen(
    initialName: String,
    initialAddress: String,
    onLocal: () -> Unit,
    onRemote: (baseUrl: String, name: String, address: String) -> Unit,
) {
    val c = LocalAppColors.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(initialName) }
    var address by remember { mutableStateOf(initialAddress) }
    var error by remember { mutableStateOf<String?>(null) }
    var connecting by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(APP_NAME, fontSize = 34.sp, fontWeight = FontWeight.Black)
        Text(APP_TAGLINE, color = c.sub, fontSize = 14.sp, lineHeight = 20.sp)

        Text("2台で同期する", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
        Text(
            "同じWi-FiのPCで中継サーバーを動かし、\n2台のスマホからつなぎます。\n2台には、それぞれ別の名前を入れてください。",
            color = c.sub, fontSize = 14.sp, lineHeight = 20.sp,
        )
        OutlinedTextField(
            value = name, onValueChange = { if (it.length <= 12) name = it },
            label = { Text("あなたの名前") }, placeholder = { Text("例：けんた") },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = address, onValueChange = { address = it },
            label = { Text("サーバーのアドレス") }, placeholder = { Text("例：192.168.1.23:8787") },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        )
        error?.let { Text(it, color = c.shu, fontSize = 14.sp) }
        BigButton(
            if (connecting) "つないでいます…" else "つなぐ", c.ink, contentColor = c.bg,
            onClick = {
                if (connecting) return@BigButton
                val n = name.trim()
                val url = normalizeServerUrl(address)
                error = when {
                    n.isEmpty() -> "あなたの名前を書いてください。"
                    url == null -> "サーバーのアドレスを書いてください（例：192.168.1.23:8787）。"
                    else -> null
                }
                if (error != null || url == null) return@BigButton
                connecting = true
                scope.launch {
                    val ok = checkServer(url)
                    connecting = false
                    if (ok) onRemote(url, n, address.trim())
                    else error = "つながりませんでした。\nPCでサーバーが動いているか、\n同じWi-Fiにいるかを確認してください。"
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )

        Text("1台で使う", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp))
        Text("ネットにつながなくても使えます。\n端末を2人で手渡しして申告します。", color = c.sub, fontSize = 14.sp, lineHeight = 20.sp)
        OutlinedButton(
            onClick = onLocal,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            shape = RoundedCornerShape(12.dp),
        ) {
            Text("1台で使う（オフライン）", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = c.ink)
        }
    }
}
