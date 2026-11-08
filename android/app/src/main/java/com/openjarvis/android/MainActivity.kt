package com.openjarvis.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : ComponentActivity() {
    private fun securePrefs() = EncryptedSharedPreferences.create(
        this,
        "openjarvis_secure",
        MasterKey.Builder(this).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                OpenJarvisApp(
                    loadBase = { securePrefs().getString("base", "https://api.openai.com/v1") ?: "" },
                    loadKey = { securePrefs().getString("key", "") ?: "" },
                    saveSettings = { base, key ->
                        securePrefs().edit().putString("base", base.trim().trimEnd('/'))
                            .putString("key", key).apply()
                    }
                )
            }
        }
    }
}

private data class ChatLine(val role: String, val text: String)

@Composable
private fun OpenJarvisApp(
    loadBase: () -> String,
    loadKey: () -> String,
    saveSettings: (String, String) -> Unit
) {
    var base by remember { mutableStateOf(loadBase()) }
    var key by remember { mutableStateOf(loadKey()) }
    var model by remember { mutableStateOf("gpt-4o-mini") }
    var draft by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Configure your API endpoint and key in Settings.") }
    var settings by remember { mutableStateOf(false) }
    val messages = remember { mutableStateListOf(ChatLine("assistant", "Hello. I’m OpenJarvis. Add an API key in Settings to begin.")) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("OpenJarvis") },
                actions = { TextButton(onClick = { settings = !settings }) { Text(if (settings) "Chat" else "Settings") } }
            )
        }
    ) { padding ->
        if (settings) {
            Column(Modifier.fillMaxSize().padding(padding).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Connection settings", style = MaterialTheme.typography.headlineSmall)
                OutlinedTextField(base, { base = it }, label = { Text("OpenAI-compatible base URL") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(key, { key = it }, label = { Text("API key") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(model, { model = it }, label = { Text("Model name") }, modifier = Modifier.fillMaxWidth())
                Button(onClick = {
                    saveSettings(base, key)
                    status = "Settings saved securely on this device."
                    settings = false
                }) { Text("Save settings") }
                Text("The API key is stored locally using Android encrypted preferences. Use HTTPS endpoints.")
            }
        } else {
            Column(Modifier.fillMaxSize().padding(padding).padding(12.dp)) {
                Text(status, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(messages) { item ->
                        Surface(
                            color = if (item.role == "user") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text(if (item.role == "user") "You" else "OpenJarvis", style = MaterialTheme.typography.labelMedium)
                                Text(item.text)
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = draft, onValueChange = { draft = it },
                        modifier = Modifier.weight(1f), placeholder = { Text("Ask OpenJarvis…") },
                        maxLines = 4
                    )
                    Button(enabled = !busy && draft.isNotBlank(), onClick = {
                        val prompt = draft.trim()
                        draft = ""
                        messages.add(ChatLine("user", prompt))
                        busy = true
                        status = "Thinking…"
                        scope.launch {
                            try {
                                val answer = withContext(Dispatchers.IO) { requestChat(base, key, model, messages.toList()) }
                                messages.add(ChatLine("assistant", answer))
                                status = "Connected"
                            } catch (e: Exception) {
                                status = e.message ?: "Request failed"
                                messages.add(ChatLine("assistant", "I couldn’t complete that request. Check Settings and your network, then try again."))
                            } finally { busy = false }
                        }
                    }) { Text("Send") }
                }
            }
        }
    }
}

private fun requestChat(base: String, key: String, model: String, history: List<ChatLine>): String {
    require(base.startsWith("https://")) { "Use a secure HTTPS API base URL in Settings." }
    require(key.isNotBlank()) { "Add your API key in Settings first." }
    val body = JSONObject().put("model", model).put("messages", JSONArray().apply {
        history.forEach { put(JSONObject().put("role", it.role).put("content", it.text)) }
    })
    val connection = (URL(base.trimEnd('/') + "/chat/completions").openConnection() as HttpURLConnection).apply {
        requestMethod = "POST"
        connectTimeout = 20000
        readTimeout = 60000
        doOutput = true
        setRequestProperty("Content-Type", "application/json")
        setRequestProperty("Authorization", "Bearer $key")
    }
    try {
        connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
        val response = stream.bufferedReader().use { it.readText() }
        if (connection.responseCode !in 200..299) throw IllegalStateException("API error ${connection.responseCode}: ${response.take(300)}")
        return JSONObject(response).getJSONArray("choices").getJSONObject(0)
            .getJSONObject("message").getString("content")
    } finally { connection.disconnect() }
}
