package cn.y10n.gotoapp

import cn.y10n.gotoapp.server.HttpParser
import cn.y10n.gotoapp.server.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class HttpParserTest {

    @Test
    fun parseRequestLine_basic() {
        val (m, p, q) = HttpParser.parseRequestLine("GET /assets/x.js HTTP/1.1")
        assertEquals("GET", m)
        assertEquals("/assets/x.js", p)
        assertEquals("", q)
    }

    @Test
    fun parseRequestLine_withQuery_andDecode() {
        val (m, p, q) = HttpParser.parseRequestLine("GET /api/icon/com.a.b?token=123%20456 HTTP/1.1")
        assertEquals("GET", m)
        assertEquals("/api/icon/com.a.b", p)
        assertEquals("token=123%20456", q)
        assertEquals(mapOf("token" to "123 456"), HttpParser.parseQuery(q))
    }

    @Test
    fun parseQuery_multiple() {
        val q = HttpParser.parseQuery("a=1&b=hello%20world&flag&c=%E4%B8%AD")
        assertEquals("1", q["a"])
        assertEquals("hello world", q["b"])
        assertEquals("", q["flag"])
        assertEquals("中", q["c"])
    }

    @Test
    fun decodePercent_keepsPlus() {
        // URLDecoder 会把 + 变空格；我们的实现必须保留（path/query 语义）
        assertEquals("a+b", HttpParser.decodePercent("a+b"))
        assertEquals("a b", HttpParser.decodePercent("a%20b"))
        assertEquals("中文", HttpParser.decodePercent("%E4%B8%AD%E6%96%87"))
        assertEquals("100%", HttpParser.decodePercent("100%"))
    }

    @Test
    fun readRequest_parsesHeadersAndBody() {
        val raw = ("POST /api/config HTTP/1.1\r\n" +
                "Host: example.test:8787\r\n" +
                "Content-Type: application/json\r\n" +
                "X-Access-Token: abc123\r\n" +
                "Content-Length: 22\r\n" +
                "\r\n" +
                """{"webFavorites":["a"]}""").toByteArray(Charsets.UTF_8)
        val req: Request = HttpParser.readRequest(ByteArrayInputStream(raw))
        assertEquals("POST", req.method)
        assertEquals("/api/config", req.path)
        assertEquals("application/json", req.headers["content-type"])
        assertEquals("abc123", req.headers["x-access-token"])
        assertEquals("""{"webFavorites":["a"]}""", String(req.body, Charsets.UTF_8))
    }

    @Test
    fun readRequest_getNoBody() {
        val raw = ("GET /api/info HTTP/1.1\r\n" +
                "Host: example.test:8787\r\n" +
                "\r\n").toByteArray(Charsets.UTF_8)
        val req = HttpParser.readRequest(ByteArrayInputStream(raw))
        assertEquals("GET", req.method)
        assertEquals("/api/info", req.path)
        assertEquals(0, req.body.size)
        assertFalse(req.headers.isEmpty())
        assertTrue(req.query.isEmpty())
    }
}
