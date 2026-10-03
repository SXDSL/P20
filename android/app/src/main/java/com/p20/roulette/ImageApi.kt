package com.p20.roulette

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Random

object ImageApi {

    data class BooruSource(val name: String, val api: String, val base: String)

    data class Image(val url: String, val source: String?, val thumb: String?)

    val BOORUS = listOf(
        BooruSource("xbooru", "https://xbooru.com/index.php", "https://xbooru.com"),
        BooruSource("tbib", "https://tbib.org/index.php", "https://tbib.org"),
        BooruSource("hypnohub", "https://hypnohub.net/index.php", "https://hypnohub.net"),
        BooruSource("safebooru", "https://safebooru.org/index.php", "https://safebooru.org"),
    )

    val SNOWFLAKE_FILTERS: Map<String, Set<String>> = mapOf(
        "propaganda" to setOf(
            "yaoi", "gay", "femboy", "twink", "homosexual", "boyslove",
            "male_on_male", "solo_male", "male_focus", "2boys",
        ),
        "sunset" to setOf(
            "dark_skin", "dark-skinned", "dark-skinned_female",
            "dark-skinned_male", "tanned",
        ),
        "water" to setOf("ai_generated", "stable_diffusion", "novelai", "ai_art"),
        "demons" to setOf(
            "lolicon", "shotacon", "loli", "shota", "underage", "teen", "young_girl",
        ),
        "ballpit" to setOf(
            "furry", "anthro", "anthropomorphic", "beastman", "kemono", "furry_female",
        ),
        "ack" to setOf(
            "futanari", "dickgirl", "dickgirl_on_female", "futanari_on_female",
            "futanari_on_male", "hermaphrodite", "intersex", "transgender", "shemale",
        ),
    )

    const val RANDOM_PAGES = 10

    private val ARTIST_LI = Regex(
        """<li[^>]*class=["'][^"']*\btag-type-artist\b[^"']*["'][^>]*>(.*?)</li>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
    private val ARTIST_ANCHOR = Regex(
        """<a[^>]*href=["']([^"']*)["'][^>]*>(.*?)</a>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
    val ALLOWED_ARTIST_HOSTS =
        setOf("xbooru.com", "tbib.org", "hypnohub.net", "safebooru.org")
    private val artistCache = HashMap<String, String?>()

    private val random = Random()

    private fun str(post: JSONObject, key: String): String? {
        val value = post.opt(key) ?: return null
        if (value === JSONObject.NULL) return null
        return value.toString()
    }

    internal fun fetchPosts(source: BooruSource, tags: String, pid: Int): List<JSONObject> {
        val query = "page=dapi&s=post&q=index&json=1&limit=50&pid=$pid" +
            "&tags=${URLEncoder.encode(tags, "UTF-8")}"
        try {
            val conn = URL(source.api + "?" + query).openConnection() as HttpURLConnection
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            conn.setRequestProperty("User-Agent", "Mozilla/5.0")
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            if (body.isBlank()) return emptyList()
            val parsed = JSONArray(body)
            return (0 until parsed.length()).map { parsed.getJSONObject(it) }
        } catch (e: Exception) {
            Log.w("P20", "${source.name} fetch failed for \"$tags\" (pid $pid): $e")
            return emptyList()
        }
    }

    internal fun postImageUrl(source: BooruSource, post: JSONObject): String? {
        str(post, "file_url")?.let { return it }
        str(post, "sample_url")?.let { return it }
        val dir = str(post, "directory")
        val image = str(post, "image")
        if (dir != null && image != null) return "${source.base}/images/$dir/$image"
        return null
    }

    internal fun postPageUrl(source: BooruSource, post: JSONObject): String? {
        val id = str(post, "id") ?: return null
        return source.base + "/index.php?page=post&s=view&id=$id"
    }

    internal fun filterPosts(posts: List<JSONObject>, blacklist: Set<String>): List<JSONObject> {
        if (blacklist.isEmpty()) return posts
        return posts.filter { post ->
            val tags = post.optString("tags").split(Regex("\\s+")).filter { it.isNotEmpty() }
            tags.none { it in blacklist }
        }
    }

    fun randomBooruImage(tags: String, blacklist: Set<String>): Image? {
        if (tags in blacklist) return null
        for (source in BOORUS) {
            val pid = random.nextInt(RANDOM_PAGES)
            var posts: List<JSONObject> = emptyList()
            for (attempt in (if (pid == 0) listOf(0) else listOf(pid, 0))) {
                posts = fetchPosts(source, tags, attempt)
                if (posts.isNotEmpty()) break
            }
            if (posts.isEmpty()) continue
            val filtered = filterPosts(posts, blacklist)
            if (filtered.isEmpty()) continue
            val post = filtered[random.nextInt(filtered.size)]
            val url = postImageUrl(source, post) ?: continue
            Log.d("P20", "image for \"$tags\" from ${source.name}")
            val thumb = str(post, "preview_url") ?: url
            return Image(url, postPageUrl(source, post), thumb)
        }
        return null
    }

    internal fun unescape(s: String): String = s
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#039;", "'")
        .replace("&#39;", "'")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")

    internal fun extractArtist(page: String): String? {
        val names = mutableListOf<String>()
        for (li in ARTIST_LI.findAll(page)) {
            var name: String? = null
            for (anchor in ARTIST_ANCHOR.findAll(li.groupValues[1])) {
                val href = anchor.groupValues[1]
                val text = unescape(anchor.groupValues[2].replace(Regex("<[^>]+>"), "")).trim()
                if (text.isEmpty() || text == "?") continue
                if (href.contains("tags=")) {
                    name = text
                    break
                }
                if (name == null) name = text
            }
            if (name != null && !names.contains(name)) names.add(name)
        }
        return if (names.isEmpty()) null else names.joinToString(", ")
    }

    internal fun fetchArtist(sourceUrl: String): String? {
        if (sourceUrl.isEmpty()) return null
        synchronized(artistCache) {
            if (artistCache.containsKey(sourceUrl)) return artistCache[sourceUrl]
        }
        val parsed = try {
            URL(sourceUrl)
        } catch (e: Exception) {
            return null
        }
        if (parsed.protocol != "http" && parsed.protocol != "https") return null
        if ((parsed.host ?: "").lowercase() !in ALLOWED_ARTIST_HOSTS) return null
        val page = try {
            val conn = URL(sourceUrl).openConnection() as HttpURLConnection
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            conn.setRequestProperty("User-Agent", "Mozilla/5.0")
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            body
        } catch (e: Exception) {
            return null
        }
        val artist = extractArtist(page)
        synchronized(artistCache) {
            if (artistCache.size >= 1000) artistCache.clear()
            artistCache[sourceUrl] = artist
        }
        return artist
    }

    private fun parseQuery(query: String): Map<String, String> {
        if (query.isEmpty()) return emptyMap()
        return query.split("&").filter { it.isNotEmpty() }.associate { pair ->
            val idx = pair.indexOf('=')
            val key = if (idx >= 0) pair.substring(0, idx) else pair
            val value = if (idx >= 0) pair.substring(idx + 1) else ""
            URLDecoder.decode(key, "UTF-8") to URLDecoder.decode(value, "UTF-8")
        }
    }

    fun handle(query: String): Pair<Int, String> {
        return try {
            val params = parseQuery(query)
            val tags = params["tags"] ?: ""
            val blacklist = mutableSetOf<String>()
            for (name in (params["filters"] ?: "").split(",")) {
                val key = name.trim()
                if (key.isNotEmpty()) blacklist += SNOWFLAKE_FILTERS[key] ?: emptySet()
            }
            val result = randomBooruImage(tags, blacklist)
                ?: return 404 to """{"error": "no results"}"""
            val json = JSONObject().put("url", result.url)
                .put("source", result.source ?: JSONObject.NULL)
                .put("thumb", result.thumb ?: JSONObject.NULL)
            200 to json.toString()
        } catch (e: Exception) {
            Log.e("P20", "api error", e)
            502 to JSONObject().put("error", e.message ?: "error").toString()
        }
    }

    fun handleArtist(query: String): Pair<Int, String> {
        return try {
            val params = parseQuery(query)
            val artist = fetchArtist(params["source"] ?: "")
            200 to JSONObject().put("artist", artist ?: JSONObject.NULL).toString()
        } catch (e: Exception) {
            Log.e("P20", "artist api error", e)
            200 to """{"artist": null}"""
        }
    }
}
