package com.p20.roulette

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.net.URLEncoder

class ImageApiTest {

    private fun post(tags: String) = JSONObject().put("tags", tags)

    @Test
    fun filterRemovesBlacklistedPosts() {
        val posts = listOf(
            post("1girl sex solo"),
            post("1girl futanari sex"),
            post("1girl dickgirl_on_female"),
            post(""),
        )
        val out = ImageApi.filterPosts(posts, ImageApi.SNOWFLAKE_FILTERS.getValue("ack"))
        assertEquals(2, out.size)
        assertEquals("1girl sex solo", out[0].optString("tags"))
        assertEquals("", out[1].optString("tags"))
    }

    @Test
    fun emptyBlacklistIsPassthrough() {
        val posts = listOf(post("a"), post("b"))
        assertSame(posts, ImageApi.filterPosts(posts, emptySet()))
    }

    @Test
    fun snowflakeIdsMatchHtml() {
        val html = File("src/main/assets/index.html").readText()
        val ids = Regex("""id: '([a-z]+)', name:""").findAll(html)
            .map { it.groupValues[1] }.toSet()
        assertEquals(ImageApi.SNOWFLAKE_FILTERS.keys, ids)
    }

    @Test
    fun htmlAssetMatchesSource() {
        val asset = File("src/main/assets/index.html").readText()
        val source = File("../../hentai roulette.html").readText()
        assertEquals(source, asset)
    }

    @Test
    fun searchTagInBlacklistReturnsNull() {
        val ack = ImageApi.SNOWFLAKE_FILTERS.getValue("ack")
        assertNull(ImageApi.randomBooruImage("futanari", ack))
    }

    @Test
    fun chainReturnsUrlAndSource() {
        val result = ImageApi.randomBooruImage("gyaru", emptySet())
        assertNotNull(result)
        assertTrue(result!!.url.startsWith("http"))
        assertNotNull(result.source)
        assertTrue(result.source!!.contains("&id="))
        assertNotNull(result.thumb)
        assertTrue(result.thumb!!.startsWith("http"))
    }

    @Test
    fun pagesDiffer() {
        val src = ImageApi.BOORUS[0]
        val p0 = ImageApi.fetchPosts(src, "sex", 0)
        val p1 = ImageApi.fetchPosts(src, "sex", 1)
        assertTrue("page 0 empty", p0.isNotEmpty())
        assertTrue("page 1 empty", p1.isNotEmpty())
        assertNotEquals(p0[0].opt("id"), p1[0].opt("id"))
    }

    @Test
    fun outOfRangePageIsEmpty() {
        val src = ImageApi.BOORUS[0]
        assertTrue(ImageApi.fetchPosts(src, "gyaru", 50).isEmpty())
    }

    @Test
    fun busyTagVariety() {
        val urls = (1..6).mapNotNull {
            ImageApi.randomBooruImage("sex", emptySet())?.url
        }.toSet()
        assertTrue("only ${urls.size} distinct: $urls", urls.size >= 2)
    }

    @Test
    fun handleOk() {
        val (code, body) = ImageApi.handle("tags=gyaru")
        assertEquals(200, code)
        val json = JSONObject(body)
        assertTrue(json.getString("url").startsWith("http"))
        assertTrue(json.optString("source").contains("&id="))
        assertTrue(json.getString("thumb").startsWith("http"))
    }

    private fun artistFixture(inner: String) =
        "<html><body><ul class='tag-list'>$inner</ul></body></html>"

    @Test
    fun extractArtistTwoAnchorBlock() {
        val block = """
            <li class="tag-type-artist tag">
              <a href="index.php?page=wiki&s=list&search=wlop">?</a>
              <a href="index.php?page=post&s=list&tags=wlop">wlop</a>
              <span class="tag-count">350</span>
            </li>
        """.trimIndent()
        assertEquals("wlop", ImageApi.extractArtist(artistFixture(block)))
    }

    @Test
    fun extractArtistSingleAnchorBlock() {
        val block = """
            <li class="tag-type-artist tag">
              <a href="index.php?page=post&s=list&tags=sizuka">sizuka</a>
            </li>
        """.trimIndent()
        assertEquals("sizuka", ImageApi.extractArtist(artistFixture(block)))
    }

    @Test
    fun extractArtistNoneReturnsNull() {
        assertNull(ImageApi.extractArtist(artistFixture("<li class='tag-type-general tag'></li>")))
    }

    @Test
    fun extractArtistMultipleJoined() {
        val a = "<li class='tag-type-artist tag'><a href='x?tags=wlop'>wlop</a></li>"
        val b = "<li class='tag-type-artist tag'><a href='x?tags=bhloopy'>bhloopy</a></li>"
        assertEquals("wlop, bhloopy", ImageApi.extractArtist(artistFixture(a + b)))
    }

    @Test
    fun extractArtistUnescapesEntities() {
        val block = "<li class='tag-type-artist tag'><a href='x?tags=foo_bar'>foo&#039;s_bar</a></li>"
        assertEquals("foo's_bar", ImageApi.extractArtist(artistFixture(block)))
    }

    @Test
    fun fetchArtistForeignHostIsNull() {
        assertNull(ImageApi.fetchArtist("http://169.254.169.254/latest/meta-data/"))
        assertNull(ImageApi.fetchArtist("https://xbooru.com.evil.net/index.php"))
        assertNull(ImageApi.fetchArtist("file:///C:/Windows/win.ini"))
        assertNull(ImageApi.fetchArtist(""))
    }

    @Test
    fun handleArtistLiveXbooru() {
        val src = "https://xbooru.com/index.php?page=post&s=view&id=1282665"
        val (code, body) = ImageApi.handleArtist("source=" + URLEncoder.encode(src, "UTF-8"))
        assertEquals(200, code)
        assertEquals("bhloopy", JSONObject(body).getString("artist"))
    }

    @Test
    fun handleArtistForeignHostNull() {
        val (code, body) = ImageApi.handleArtist(
            "source=" + URLEncoder.encode("http://evil.com/x", "UTF-8"),
        )
        assertEquals(200, code)
        assertTrue(JSONObject(body).isNull("artist"))
    }

    @Test
    fun handleArtistMissingSourceNull() {
        val (code, body) = ImageApi.handleArtist("")
        assertEquals(200, code)
        assertTrue(JSONObject(body).isNull("artist"))
    }

    @Test
    fun handleBlockedTag404() {
        val (code, _) = ImageApi.handle("tags=futanari&filters=ack")
        assertEquals(404, code)
    }

    @Test
    fun handleBogusFilterStillOk() {
        val (code, _) = ImageApi.handle("tags=gyaru&filters=bogus")
        assertEquals(200, code)
    }
}
