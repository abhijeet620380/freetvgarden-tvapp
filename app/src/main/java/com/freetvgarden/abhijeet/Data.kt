package com.freetvgarden.abhijeet

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

// ── Content type ─────────────────────────────────────────────────────────────
enum class MediaType { TV, RADIO, WEBCAM }

// ── Sort options ──────────────────────────────────────────────────────────────
enum class SortOption(val label: String) {
    NAME_ASC("Name (A–Z)"),
    NAME_DESC("Name (Z–A)"),
    LIVE_FIRST("Live First"),
    HD_FIRST("Quality (HD First)"),
    COUNTRY_ASC("Country (A–Z)"),
    LANGUAGE_ASC("Language (A–Z)")
}

// ── Data models ───────────────────────────────────────────────────────────────
data class Source(val url: String, val ua: String? = null, val referrer: String? = null)

data class Channel(
    val id: String,
    val name: String,
    val quality: String,
    val country: String,
    val language: String = "",   // tvg-language
    val sources: List<Source>,
    val logo: String = "",       // tvg-logo URL
    val group: String = "",      // group-title / category
    val status: String = "",     // live / down / unknown
    val mediaType: MediaType = MediaType.TV
)

// ── Base URL ──────────────────────────────────────────────────────────────────
private const val RAW = "https://raw.githubusercontent.com/abhijeet620380/freetvgarden-data-v2/main"
private const val IPTV_M3U   = "$RAW/iptv/index.m3u"
private const val RADIO_M3U  = "$RAW/radio/index.m3u"
private const val WEBCAM_M3U = "$RAW/webcams/index.m3u"

// ── HTTP helper ───────────────────────────────────────────────────────────────
internal fun http(url: String): String {
    val c = URL(url).openConnection() as HttpURLConnection
    c.connectTimeout = 20000
    c.readTimeout    = 40000
    c.setRequestProperty("User-Agent", "FreeTvGarden-Android/1.0")
    return try {
        c.inputStream.bufferedReader().use { it.readText() }
    } finally {
        c.disconnect()
    }
}

// ── M3U parser ────────────────────────────────────────────────────────────────
private val qualityRx   = Regex("""\s*\((\d{3,4}[pi])\)\s*$""")
private val idRx        = Regex("""tvg-id="([^"]*)"""")
private val logoRx      = Regex("""tvg-logo="([^"]*)"""")
private val countryRx   = Regex("""tvg-country="([^"]*)"""")
private val languageRx  = Regex("""tvg-language="([^"]*)"""")
private val groupRx     = Regex("""group-title="([^"]*)"""")
private val statusRx    = Regex("""tvg-status="([^"]*)"""")
private val uaRx        = Regex("""#EXTVLCOPT:http-user-agent=(.+)""")
private val refRx       = Regex("""#EXTVLCOPT:http-referrer=(.+)""")

fun parseM3u(text: String, type: MediaType): List<Channel> {
    val map = LinkedHashMap<String, Channel>()
    var title: String? = null
    var tvgId = ""; var ua: String? = null; var ref: String? = null
    var logo = ""; var country = ""; var lang = ""; var group = ""; var status = ""

    for (raw in text.lineSequence()) {
        val line = raw.trim()
        if (line.isEmpty()) continue
        when {
            line.startsWith("#EXTINF") -> {
                tvgId   = idRx.find(line)?.groupValues?.get(1).orEmpty()
                logo    = logoRx.find(line)?.groupValues?.get(1).orEmpty()
                country = countryRx.find(line)?.groupValues?.get(1).orEmpty()
                lang    = languageRx.find(line)?.groupValues?.get(1).orEmpty()
                group   = groupRx.find(line)?.groupValues?.get(1).orEmpty()
                status  = statusRx.find(line)?.groupValues?.get(1).orEmpty()
                val comma = line.indexOf(',', maxOf(line.lastIndexOf('"'), 0))
                title = if (comma >= 0) line.substring(comma + 1).trim() else "Channel"
                ua = null; ref = null
            }
            line.startsWith("#EXTVLCOPT:http-user-agent=") -> ua = uaRx.find(line)?.groupValues?.get(1)
            line.startsWith("#EXTVLCOPT:http-referrer=")   -> ref = refRx.find(line)?.groupValues?.get(1)
            line.startsWith("#") -> {}
            title != null -> {
                val q    = qualityRx.find(title!!)?.groupValues?.get(1).orEmpty()
                val name = title!!.replace(qualityRx, "").trim().ifEmpty { "Channel" }
                val id   = tvgId.ifEmpty { "${type.name}:${name.lowercase()}" }
                val src  = Source(line, ua, ref)
                val old  = map[id]
                map[id] = if (old == null)
                    Channel(id, name, q, country, lang, listOf(src), logo, group, status, type)
                else if (old.sources.any { it.url == line }) old
                else old.copy(sources = old.sources + src)
                title = null
            }
        }
    }
    return map.values.sortedBy { it.name.lowercase() }
}

// ── JSON serialisation (for favourites / recents) ─────────────────────────────
private fun Channel.toJson() = JSONObject()
    .put("id", id).put("name", name).put("q", quality).put("c", country).put("lang", language)
    .put("logo", logo).put("group", group).put("status", status).put("mt", mediaType.name)
    .put("s", JSONArray(sources.map {
        JSONObject().put("u", it.url).put("ua", it.ua).put("r", it.referrer)
    }))

private fun channelFromJson(o: JSONObject): Channel {
    val a = o.getJSONArray("s")
    val srcs = (0 until a.length()).map {
        val s = a.getJSONObject(it)
        Source(s.getString("u"), s.optString("ua").ifEmpty { null }, s.optString("r").ifEmpty { null })
    }
    val mt = runCatching { MediaType.valueOf(o.optString("mt", "TV")) }.getOrDefault(MediaType.TV)
    return Channel(
        o.getString("id"), o.getString("name"), o.optString("q"), o.optString("c"),
        o.optString("lang"), srcs, o.optString("logo"), o.optString("group"), o.optString("status"), mt
    )
}

// ── Repository ────────────────────────────────────────────────────────────────
class Repo(ctx: Context) {
    private val dir = File(ctx.filesDir, "ftg_cache").apply { mkdirs() }

    fun clearCache() {
        dir.listFiles()?.forEach { it.delete() }
    }

    private suspend fun fetchM3u(url: String, cacheFile: String, force: Boolean): String =
        withContext(Dispatchers.IO) {
            val f = File(dir, cacheFile)
            if (!force && f.exists() && f.length() > 0) return@withContext f.readText()
            try {
                http(url).also { f.writeText(it) }
            } catch (e: Exception) {
                if (f.exists() && f.length() > 0) f.readText() else throw e
            }
        }

    suspend fun tvChannels(force: Boolean): List<Channel> = withContext(Dispatchers.IO) {
        parseM3u(fetchM3u(IPTV_M3U, "iptv.m3u", force), MediaType.TV)
    }

    suspend fun radioChannels(force: Boolean): List<Channel> = withContext(Dispatchers.IO) {
        parseM3u(fetchM3u(RADIO_M3U, "radio.m3u", force), MediaType.RADIO)
    }

    suspend fun webcamChannels(force: Boolean): List<Channel> = withContext(Dispatchers.IO) {
        parseM3u(fetchM3u(WEBCAM_M3U, "webcams.m3u", force), MediaType.WEBCAM)
    }
}

// ── Store (favourites, recents & settings) ────────────────────────────────────
class Store(ctx: Context) {
    private val sp = ctx.getSharedPreferences("freetvgarden", Context.MODE_PRIVATE)

    val favs    = mutableStateListOf<Channel>().apply { addAll(read("favs")) }
    val recents = mutableStateListOf<Channel>().apply { addAll(read("recents")) }

    // Settings state
    var sortOrder by mutableIntStateOf(sp.getInt("sort_order", 0))
    var hideOffline by mutableStateOf(sp.getBoolean("hide_offline", false))
    var playerResizeMode by mutableIntStateOf(sp.getInt("resize_mode", 0)) // 0=FIT, 1=ZOOM, 2=FILL

    fun setSort(order: Int) {
        sortOrder = order
        sp.edit().putInt("sort_order", order).apply()
    }

    fun toggleHideOffline() {
        hideOffline = !hideOffline
        sp.edit().putBoolean("hide_offline", hideOffline).apply()
    }

    fun setResize(mode: Int) {
        playerResizeMode = mode
        sp.edit().putInt("resize_mode", mode).apply()
    }

    private fun read(k: String): List<Channel> = runCatching {
        val a = JSONArray(sp.getString(k, "[]"))
        (0 until a.length()).map { channelFromJson(a.getJSONObject(it)) }
    }.getOrDefault(emptyList())

    private fun write(k: String, l: List<Channel>) {
        sp.edit().putString(k, JSONArray(l.map { it.toJson() }).toString()).apply()
    }

    fun isFav(c: Channel) = favs.any { it.id == c.id }

    fun toggleFav(c: Channel) {
        val i = favs.indexOfFirst { it.id == c.id }
        if (i >= 0) favs.removeAt(i) else favs.add(0, c)
        write("favs", favs)
    }

    fun addRecent(c: Channel) {
        recents.removeAll { it.id == c.id }
        recents.add(0, c)
        while (recents.size > 30) recents.removeAt(recents.lastIndex)
        write("recents", recents)
    }
}
