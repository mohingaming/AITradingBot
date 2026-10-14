package com.example.aibot

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object MercuryClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    fun analyze(apiKey: String, marketData: String): String {
        val prompt = """You are a trading chart analyst.
Data: $marketData
Reply ONLY valid JSON, nothing else:
{"signal":"UP" or "DOWN" or "WAIT","confidence":0-100,"reason":"max 12 words"}"""

        val payload = JSONObject().apply {
            put("model", "mercury-2.5")
            put("messages", JSONArray().put(JSONObject().apply {
                put("role", "user")
                put("content", prompt)
            }))
            put("reasoning_effort", "medium")
            put("max_completion_tokens", 2048)
            put("temperature", 0.3)
        }

        val req = Request.Builder()
            .url("https://api.inceptionlabs.ai/v1/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(req).execute().use { resp ->
            val body = resp.body!!.string()
            if (!resp.isSuccessful) return "API error ${resp.code}: ${body.take(150)}"
            return try {
                val msg = JSONObject(body)
                    .getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                if (msg.isNull("content")) {
                    "Empty reply: ${body.take(200)}"
                } else {
                    msg.getString("content").trim()
                }
            } catch (e: Exception) {
                "Parse error: ${body.take(200)}"
            }
        }
    }
}
