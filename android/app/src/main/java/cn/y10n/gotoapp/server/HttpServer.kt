package cn.y10n.gotoapp.server

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket

/**
 * 极简 HTTP/1.1 服务器。
 *
 * 使用 `ServerSocket(port)` 绑定 IPv4 端口，保持实现紧凑且便于验证。
 *
 * 连接策略：每连接一个守护线程（管理页并发极低，简单优先）。
 * 响应统一 Connection: close，不做 keep-alive。
 */
class HttpServer(
    private val port: Int,
    private val handler: (Request) -> HttpResponse,
) {
    private val serverSocket = ServerSocket(port)
    private var running = false

    fun start() {
        running = true
        val t = Thread {
            while (running) {
                val socket = try {
                    serverSocket.accept()
                } catch (e: Exception) {
                    if (running) e.printStackTrace()
                    break
                }
                Thread { handle(socket) }.apply { isDaemon = true }.start()
            }
        }
        t.isDaemon = true
        t.name = "Goto Http Listener"
        t.start()
    }

    fun close() {
        running = false
        runCatching { serverSocket.close() }
    }

    private fun handle(socket: Socket) {
        try {
            socket.soTimeout = 10_000
            val request = HttpParser.readRequest(socket.getInputStream())
            val resp = runCatching { handler(request) }.getOrElse {
                HttpResponse(500, "application/json; charset=utf-8", """{"error":"${it.message ?: "internal"}"}""".toByteArray())
            }
            writeResponse(socket.getOutputStream(), resp)
        } catch (e: HttpRequestException) {
            runCatching {
                writeResponse(
                    socket.getOutputStream(),
                    HttpResponse.text(
                        e.status,
                        "application/json; charset=utf-8",
                        """{"error":"${e.message}"}""",
                    ),
                )
            }
        } catch (e: Exception) {
            // 客户端断连，静默关闭连接
        } finally {
            runCatching { socket.close() }
        }
    }

    private fun writeResponse(out: OutputStream, resp: HttpResponse) {
        val head = buildString {
            append("HTTP/1.1 ${resp.status} ${reason(resp.status)}\r\n")
            append("Content-Type: ${resp.mime}\r\n")
            append("Content-Length: ${resp.body.size}\r\n")
            append("Connection: close\r\n")
            resp.headers.forEach { (k, v) -> append("$k: $v\r\n") }
            append("\r\n")
        }
        out.write(head.toByteArray(Charsets.UTF_8))
        out.write(resp.body)
        out.flush()
    }

    private fun reason(code: Int): String = when (code) {
        200 -> "OK"; 400 -> "Bad Request"; 401 -> "Unauthorized"
        404 -> "Not Found"; 413 -> "Payload Too Large"
        500 -> "Internal Server Error"; else -> "OK"
    }
}

private class HttpRequestException(val status: Int, message: String) : Exception(message)

data class Request(
    val method: String,
    val path: String,       // 已解码、无 query
    val query: Map<String, String>,
    val headers: Map<String, String>, // key 小写
    val body: ByteArray,
)

data class HttpResponse(
    val status: Int = 200,
    val mime: String = "application/octet-stream",
    val body: ByteArray = ByteArray(0),
    val headers: Map<String, String> = emptyMap(),
) {
    companion object {
        fun text(status: Int, mime: String, s: String, headers: Map<String, String> = emptyMap()) =
            HttpResponse(status, mime, s.toByteArray(Charsets.UTF_8), headers)
    }
}

/** HTTP 请求解析（纯函数，JVM 可单测） */
object HttpParser {

    private const val MAX_HEADER_BYTES = 16 * 1024
    private const val MAX_BODY_BYTES = 1024 * 1024

    /** 解析请求行 "GET /a/b?x=1 HTTP/1.1" → (method, path, queryString) */
    fun parseRequestLine(line: String): Triple<String, String, String> {
        val parts = line.trim().split(" ")
        val method = parts.getOrElse(0) { "GET" }
        val target = parts.getOrElse(1) { "/" }
        val qIdx = target.indexOf('?')
        val path = if (qIdx >= 0) target.substring(0, qIdx) else target
        val query = if (qIdx >= 0) target.substring(qIdx + 1) else ""
        return Triple(method, decodePercent(path), query)
    }

    /** 解析 query string → map（值已解码；无 = 的 key 值为空串） */
    fun parseQuery(query: String): Map<String, String> {
        if (query.isBlank()) return emptyMap()
        val map = LinkedHashMap<String, String>()
        query.split("&").forEach { kv ->
            if (kv.isNotEmpty()) {
                val idx = kv.indexOf('=')
                if (idx >= 0) map[kv.substring(0, idx)] = decodePercent(kv.substring(idx + 1))
                else map[kv] = ""
            }
        }
        return map
    }

    /** 解析头部行（不含请求行），key 统一小写 */
    fun parseHeaders(lines: List<String>): Map<String, String> {
        val map = LinkedHashMap<String, String>()
        lines.forEach { line ->
            val idx = line.indexOf(':')
            if (idx > 0) {
                map[line.substring(0, idx).trim().lowercase()] = line.substring(idx + 1).trim()
            }
        }
        return map
    }

    /**
     * 从流中读完整请求：读头部到 \r\n\r\n，按 Content-Length 读 body。
     * 只支持简单场景（无 chunked，管理页客户端均按 Content-Length 发送）。
     */
    fun readRequest(input: InputStream): Request {
        val headerBytes = readUntil(input, "\r\n\r\n")
        val headerText = String(headerBytes, Charsets.UTF_8)
        val lines = headerText.split("\r\n")
        val (method, path, queryString) = parseRequestLine(lines.firstOrNull() ?: "GET / HTTP/1.1")
        val headers = parseHeaders(lines.drop(1))

        val rawLength = headers["content-length"]
        val len = when {
            rawLength == null -> 0
            else -> rawLength.toLongOrNull()
                ?.takeIf { it >= 0 }
                ?: throw HttpRequestException(400, "invalid content length")
        }
        if (len > MAX_BODY_BYTES) {
            throw HttpRequestException(413, "request body too large")
        }
        val body = if (len > 0) readExact(input, len.toInt()) else ByteArray(0)
        return Request(method, path, parseQuery(queryString), headers, body)
    }

    /** 读取到分隔符，返回分隔符前的字节，并限制请求头大小。 */
    private fun readUntil(input: InputStream, delimiter: String): ByteArray {
        val delim = delimiter.toByteArray(Charsets.UTF_8)
        val out = ByteArrayOutputStream(minOf(MAX_HEADER_BYTES, 4096))
        val tail = ByteArray(delim.size)
        var count = 0
        while (true) {
            val b = input.read()
            if (b < 0) throw HttpRequestException(400, "incomplete request headers")
            out.write(b)
            tail[count % delim.size] = b.toByte()
            count++

            if (count >= delim.size) {
                val delimiterStart = count - delim.size
                val matched = delim.indices.all { i ->
                    tail[(delimiterStart + i) % delim.size] == delim[i]
                }
                if (matched) {
                    val headerSize = count - delim.size
                    if (headerSize > MAX_HEADER_BYTES) {
                        throw HttpRequestException(413, "request headers too large")
                    }
                    return out.toByteArray().copyOf(headerSize)
                }
            }

            if (count > MAX_HEADER_BYTES + delim.size) {
                throw HttpRequestException(413, "request headers too large")
            }
        }
    }

    private fun readExact(input: InputStream, len: Int): ByteArray {
        val buf = ByteArray(len)
        var read = 0
        while (read < len) {
            val n = input.read(buf, read, len - read)
            if (n < 0) throw HttpRequestException(400, "incomplete request body")
            read += n
        }
        return buf
    }

    /** 百分号解码（+ 保持原样，区别于 URLDecoder）。UTF-8 多字节序列整体解码。 */
    fun decodePercent(s: String): String {
        if (!s.contains('%')) return s
        val bytes = ByteArrayOutputStream()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '%' && i + 2 < s.length) {
                val v = s.substring(i + 1, i + 3).toIntOrNull(16)
                if (v != null) {
                    bytes.write(v)
                    i += 3
                    continue
                }
            }
            val cb = c.toString().toByteArray(Charsets.UTF_8)
            bytes.write(cb, 0, cb.size)
            i++
        }
        return String(bytes.toByteArray(), Charsets.UTF_8)
    }
}
