package com.example.sakurapet

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.Executors

private val Rose = Color(0xFFD979A8)
private val Ink = Color(0xFF493746)
private val Blush = Color(0xFFFFF3F8)

class MainActivity : ComponentActivity() {
    private var tts: TextToSpeech? = null
    private var speech: SpeechRecognizer? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale.SIMPLIFIED_CHINESE)
                tts?.setSpeechRate(0.91f)
                tts?.setPitch(1.16f)
            }
        }
        setContent {
            SakuraPetApp(
                onSpeak = { speak(it) },
                onStartListening = { callback -> startListening(callback) }
            )
        }
    }
    private fun speak(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "sakura-${System.currentTimeMillis()}")
    }
    private fun startListening(onResult: (String) -> Unit) {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) return
        speech?.destroy()
        speech = SpeechRecognizer.createSpeechRecognizer(this)
        speech?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {}
            override fun onResults(results: Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (!text.isNullOrBlank()) onResult(text)
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "和小樱说句话吧")
        }
        speech?.startListening(intent)
    }
    override fun onDestroy() {
        speech?.destroy(); tts?.stop(); tts?.shutdown(); super.onDestroy()
    }
}

data class ChatLine(val text: String, val fromSakura: Boolean)

@Composable
fun SakuraPetApp(onSpeak: (String) -> Unit, onStartListening: ((String) -> Unit) -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("sakura", 0) }
    var mood by remember { mutableStateOf(prefs.getString("mood", "开心") ?: "开心") }
    var input by remember { mutableStateOf("") }
    var affection by remember { mutableIntStateOf(prefs.getInt("affection", 3)) }
    var endpoint by remember { mutableStateOf(prefs.getString("endpoint", "") ?: "") }
    var showSettings by remember { mutableStateOf(false) }
    var listening by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val messages = remember { mutableStateListOf(ChatLine("主人～你来啦！今天想和我聊些什么？", true)) }
    val moods = listOf("开心", "害羞", "撒娇", "困倦", "元气")
    val executor = remember { Executors.newSingleThreadExecutor() }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) onStartListening { recognized -> input = recognized; listening = false }
    }
    val overlayPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (Settings.canDrawOverlays(context)) {
            ContextCompat.startForegroundService(context, Intent(context, SakuraOverlayService::class.java))
        }
    }

    fun changeMood(value: String) { mood = value; prefs.edit().putString("mood", value).apply() }
    fun sendMessage(raw: String) {
        val text = raw.trim()
        if (text.isEmpty() || busy) return
        messages.add(ChatLine(text, false)); input = ""; busy = true
        val url = endpoint.trim()
        if (url.isEmpty()) {
            val reply = localReply(text)
            messages.add(ChatLine(reply, true)); onSpeak(reply); busy = false
            changeMood(if (text.contains("难过") || text.contains("累")) "温柔" else if (text.contains("喜欢")) "害羞" else "开心")
            return
        }
        executor.execute {
            val reply = requestAiReply(url, text, messages.filter { it.text != text }.takeLast(12))
            (context as? ComponentActivity)?.runOnUiThread {
                messages.add(ChatLine(reply, true)); onSpeak(reply); busy = false
                changeMood(if (reply.contains("抱抱") || reply.contains("辛苦")) "温柔" else "开心")
            }
        }
    }

    MaterialTheme(colorScheme = lightColorScheme(primary = Rose, background = Blush, surface = Color.White)) {
        Column(Modifier.fillMaxSize().background(Blush).padding(horizontal = 16.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("小樱 Sakura", fontSize = 23.sp, fontWeight = FontWeight.Bold, color = Ink)
                    Text("你的 AI 少女伙伴 · $mood", color = Rose, fontSize = 12.sp)
                }
                Icon(Icons.Default.Favorite, contentDescription = null, tint = Rose)
                Text(" $affection", color = Ink, fontWeight = FontWeight.SemiBold)
                IconButton(onClick = { showSettings = true }) { Icon(Icons.Default.Settings, contentDescription = "设置", tint = Rose) }
            }
            Card(Modifier.fillMaxWidth().weight(0.95f), shape = RoundedCornerShape(26.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Box(Modifier.fillMaxSize()) {
                    Image(painterResource(R.drawable.sakura), contentDescription = "小樱立绘", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                    Surface(Modifier.align(Alignment.TopStart).padding(12.dp), color = Color(0xEFFFFFFF), shape = RoundedCornerShape(18.dp)) {
                        Text(when (mood) { "害羞" -> "诶？主人别盯着我看啦…///"; "撒娇" -> "摸摸头嘛～♡"; "困倦" -> "陪我休息一会儿吧…"; "元气" -> "今天也要加油哦！"; "温柔" -> "辛苦啦，先休息一下吧♡"; else -> "主人～我一直在这里哦♡" }, Modifier.padding(horizontal = 13.dp, vertical = 9.dp), color = Ink, fontSize = 13.sp)
                    }
                    Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        moods.forEach { item -> FilterChip(selected = mood == item, onClick = { changeMood(item) }, label = { Text(item, fontSize = 10.sp) }) }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                ActionButton("摸摸头", Modifier.weight(1f)) { changeMood("开心"); affection++; prefs.edit().putInt("affection", affection).apply(); onSpeak("嘿嘿，最喜欢主人摸摸头啦！") }
                ActionButton("戳戳脸", Modifier.weight(1f)) { changeMood("害羞"); affection++; prefs.edit().putInt("affection", affection).apply(); onSpeak("呀！主人别戳啦，我会害羞的！") }
                ActionButton("显示悬浮", Modifier.weight(1f)) {
                    if (Settings.canDrawOverlays(context)) ContextCompat.startForegroundService(context, Intent(context, SakuraOverlayService::class.java))
                    else overlayPermissionLauncher.launch(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")))
                }
                ActionButton("关闭悬浮", Modifier.weight(1f)) {
                    context.stopService(Intent(context, SakuraOverlayService::class.java))
                }
            }
            Text("和小樱聊天", fontWeight = FontWeight.Bold, color = Ink, fontSize = 17.sp)
            LazyColumn(Modifier.weight(0.8f).fillMaxWidth().padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(messages) { line ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (line.fromSakura) Arrangement.Start else Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                        Surface(color = if (line.fromSakura) Color.White else Color(0xFFFFDDEB), shape = RoundedCornerShape(17.dp), modifier = Modifier.widthIn(max = 290.dp)) {
                            Text(line.text, Modifier.padding(horizontal = 13.dp, vertical = 10.dp), color = Ink, fontSize = 14.sp)
                        }
                        if (line.fromSakura) IconButton(onClick = { onSpeak(line.text) }) { Icon(Icons.Default.VolumeUp, contentDescription = "朗读", tint = Rose) }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(value = input, onValueChange = { input = it }, modifier = Modifier.weight(1f), placeholder = { Text("和小樱说说话…") }, shape = RoundedCornerShape(24.dp), singleLine = true)
                IconButton(onClick = {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                        listening = true; onStartListening { recognized -> input = recognized; listening = false }
                    } else micPermission.launch(Manifest.permission.RECORD_AUDIO)
                }) { Icon(Icons.Default.Mic, contentDescription = "语音输入", tint = if (listening) Color.Red else Rose) }
                FilledIconButton(onClick = { sendMessage(input) }, enabled = !busy) { Icon(Icons.Default.Send, contentDescription = "发送") }
            }
            Text(if (endpoint.isBlank()) "演示模式：本地示例回复 · 设置 AI 服务地址可启用联网对话" else "AI 服务已配置 · 请确认地址指向兼容接口的安全 HTTPS 后端", color = Color.Gray, fontSize = 10.sp, modifier = Modifier.padding(bottom = 6.dp))
        }
        if (showSettings) AlertDialog(
            onDismissRequest = { showSettings = false },
            title = { Text("小樱设置") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("AI 后端地址（可选）", fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(value = endpoint, onValueChange = { endpoint = it }, placeholder = { Text("https://你的域名/v1/chat/completions") }, singleLine = true)
                    Text("请填写你自己的 HTTPS 兼容后端地址。不要把模型 API Key 写进 APK。留空则使用本地演示对话。", fontSize = 12.sp, color = Color.Gray)
                }
            },
            confirmButton = { TextButton(onClick = { prefs.edit().putString("endpoint", endpoint.trim()).apply(); showSettings = false }) { Text("保存") } },
            dismissButton = { TextButton(onClick = { showSettings = false }) { Text("取消") } }
        )
    }
}

@Composable
private fun ActionButton(label: String, modifier: Modifier, action: () -> Unit) {
    OutlinedButton(onClick = action, modifier = modifier, contentPadding = PaddingValues(horizontal = 3.dp, vertical = 2.dp)) { Text(label, fontSize = 11.sp) }
}

private fun requestAiReply(endpoint: String, userText: String, history: List<ChatLine>): String {
    var connection: HttpURLConnection? = null
    return try {
        connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; connectTimeout = 12000; readTimeout = 20000; doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
        }
        val msgs = JSONArray().put(JSONObject().put("role", "system").put("content", "你是小樱，一位温柔可爱、略带害羞的二次元少女伙伴。用自然、简短、真诚的中文回应。尊重用户，不假装拥有现实身体或真实人类情感。"))
        history.forEach { msgs.put(JSONObject().put("role", if (it.fromSakura) "assistant" else "user").put("content", it.text)) }
        msgs.put(JSONObject().put("role", "user").put("content", userText))
        val body = JSONObject().put("model", "default").put("messages", msgs).put("temperature", 0.8)
        connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
        val response = stream.bufferedReader().use { it.readText() }
        val json = JSONObject(response)
        json.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.optString("content")
            ?.takeIf { it.isNotBlank() } ?: "主人，我刚刚有点走神了，可以再说一次吗？"
    } catch (_: Exception) {
        "主人，我现在连不上 AI 服务呢。检查一下后端地址和网络，再陪我聊好吗？♡"
    } finally { connection?.disconnect() }
}

private fun localReply(text: String): String = when {
    listOf("难过", "伤心", "不开心", "累").any { text.contains(it) } -> "主人，先给你一个抱抱。想说的话我会认真听的。♡"
    listOf("开心", "高兴", "快乐").any { text.contains(it) } -> "听起来今天有好事发生呢！我也跟着开心起来啦～"
    listOf("你好", "在吗", "嗨").any { text.contains(it) } -> "在在在！小樱一直都在哦，主人想聊什么？"
    text.contains("喜欢") -> "欸……突然说这个，我会害羞的啦/// 不过我很珍惜和主人聊天的时间。"
    else -> "嗯嗯，我有在认真听哦。主人可以再多告诉我一点吗？♡"
}
