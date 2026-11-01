package com.god.ApplicationManager.security

import android.content.Context
import android.util.Log
import com.god.ApplicationManager.DB.AppDatabase
import com.god.ApplicationManager.DB.FeedInfo
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONArray
import org.json.JSONObject
import org.yaml.snakeyaml.Yaml

/**
 * Downloads one remote threat-intel feed and normalizes it into the app's own
 * JSON schema, cached at `filesDir/threat_feeds/feed_<id>.json`.
 *
 * Two formats are understood (auto-detected):
 * - "json": our schema `{schema:1, entries:[{package,severity,label,source}], certs:[{sha1,...}]}`.
 * - "yaml": the AssoEchap/stalkerware-indicators `ioc.yaml` layout
 *   (`[{name, type: stalkerware|watchware, packages:[...], certificates:[...]}]`).
 *
 * The cache is only replaced after a fully successful parse, so a failed
 * refresh never wipes the last good copy. Called from a background thread.
 */
object RemoteFeedFetcher {
    private const val TAG = "RemoteFeedFetcher"
    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 30_000
    private const val MAX_BYTES = 8 * 1024 * 1024

    data class FetchResult(val ok: Boolean, val status: String, val entryCount: Int)

    @JvmStatic
    fun fetch(context: Context, feed: FeedInfo): FetchResult {
        val result = try {
            downloadAndNormalize(context, feed)
        } catch (e: Exception) {
            Log.w(TAG, "Feed fetch failed: ${feed.url}", e)
            FetchResult(false, "error: ${e.message?.take(120) ?: e.javaClass.simpleName}", 0)
        }
        try {
            val db = AppDatabase.getInstance(context)
            feed.lastFetch = System.currentTimeMillis()
            feed.lastStatus = result.status
            feed.entryCount = result.entryCount
            db.securityDao().updateFeed(feed)
        } catch (e: Exception) {
            Log.w(TAG, "Could not persist feed status", e)
        }
        return result
    }

    private fun downloadAndNormalize(context: Context, feed: FeedInfo): FetchResult {
        val url = URL(feed.url)
        require(url.protocol == "https") { "only https feeds are allowed" }
        val conn = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("User-Agent", "apps-manager-threat-intel/1.0")
            setRequestProperty("Accept", "application/json, text/yaml, */*")
            instanceFollowRedirects = true
        }
        try {
            val code = conn.responseCode
            require(code in 200..299) { "http $code" }
            val body = conn.inputStream.use { stream ->
                val out = StringBuilder()
                val buf = ByteArray(8192)
                var total = 0
                while (true) {
                    val n = stream.read(buf)
                    if (n < 0) break
                    total += n
                    require(total <= MAX_BYTES) { "feed too large (>${MAX_BYTES / 1024 / 1024}MB)" }
                    out.append(String(buf, 0, n, Charsets.UTF_8))
                }
                out.toString()
            }
            val normalized = normalize(context, feed, body)
            val count = normalized.optJSONArray("entries")?.length() ?: 0
            return FetchResult(true, "ok", count)
        } finally {
            conn.disconnect()
        }
    }

    private fun normalize(context: Context, feed: FeedInfo, body: String): JSONObject {
        val trimmed = body.trimStart()
        val normalized = when {
            trimmed.startsWith("{") -> normalizeJsonFeed(feed, JSONObject(body))
            else -> normalizeYamlFeed(feed, body)
        }
        // Write cache only after successful normalization.
        val file = ThreatIntelStore.cacheFile(context.applicationContext, feed.id)
        file.writeText(normalized.toString())
        return normalized
    }

    private fun normalizeJsonFeed(feed: FeedInfo, json: JSONObject): JSONObject {
        require(json.optInt("schema", -1) == 1) { "unsupported feed schema" }
        val entries = JSONArray()
        val certs = JSONArray()
        val source = "feed:${feed.name.ifBlank { feed.id.toString() }}"
        json.optJSONArray("entries")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val pkg = o.optString("package")
                if (pkg.isBlank()) continue
                entries.put(
                    JSONObject()
                        .put("package", pkg)
                        .put("severity", ThreatSeverity.parse(o.optString("severity")).name.lowercase())
                        .put("label", o.optString("label").ifBlank { pkg })
                        .put("source", source),
                )
            }
        }
        json.optJSONArray("certs")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val sha1 = o.optString("sha1").uppercase()
                if (sha1.length != 40) continue
                certs.put(
                    JSONObject()
                        .put("sha1", sha1)
                        .put("severity", ThreatSeverity.parse(o.optString("severity")).name.lowercase())
                        .put("label", o.optString("label").ifBlank { sha1.take(12) })
                        .put("source", source),
                )
            }
        }
        return JSONObject()
            .put("schema", 1)
            .put("fetched_at", System.currentTimeMillis())
            .put("entries", entries)
            .put("certs", certs)
    }

    @Suppress("UNCHECKED_CAST")
    private fun normalizeYamlFeed(feed: FeedInfo, body: String): JSONObject {
        val yaml = Yaml()
        val doc = yaml.load<List<Map<String, Any?>>>(body)
            ?: throw IllegalArgumentException("empty yaml")
        val entries = JSONArray()
        val certs = JSONArray()
        val source = "feed:${feed.name.ifBlank { feed.id.toString() }}"
        val pkgRe = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+$")
        for (item in doc) {
            val type = (item["type"] as? String).orEmpty()
            val severity = if (type.equals("stalkerware", ignoreCase = true)) "malware" else "suspect"
            val label = (item["name"] as? String).orEmpty().ifBlank { "unknown" }
            (item["packages"] as? List<*>)?.forEach { raw ->
                val pkg = raw?.toString().orEmpty().trim()
                if (pkgRe.matches(pkg)) {
                    entries.put(
                        JSONObject()
                            .put("package", pkg)
                            .put("severity", severity)
                            .put("label", label)
                            .put("source", source),
                    )
                }
            }
            (item["certificates"] as? List<*>)?.forEach { raw ->
                val sha1 = raw?.toString().orEmpty().trim().uppercase()
                if (sha1.length == 40) {
                    certs.put(
                        JSONObject()
                            .put("sha1", sha1)
                            .put("severity", severity)
                            .put("label", label)
                            .put("source", source),
                    )
                }
            }
        }
        return JSONObject()
            .put("schema", 1)
            .put("fetched_at", System.currentTimeMillis())
            .put("entries", entries)
            .put("certs", certs)
    }
}
