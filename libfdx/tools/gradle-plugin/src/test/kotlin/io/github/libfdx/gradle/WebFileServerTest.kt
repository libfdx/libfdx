package io.github.libfdx.gradle

import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.net.InetSocketAddress
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path

class WebFileServerTest {
    @TempDir lateinit var temporary: Path

    @Test fun `real HTTP ranges HEAD empty files and missing paths have correct bounds`() {
        Files.write(temporary.resolve("bytes.bin"), ByteArray(100) { it.toByte() })
        Files.write(temporary.resolve("empty.bin"), byteArrayOf())
        val root = temporary.toFile().canonicalFile
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { serveWebFile(root, it) }
        server.start()
        try {
            HttpClient.newHttpClient().use { client ->
                fun request(path: String, range: String? = null, method: String = "GET"): HttpResponse<ByteArray> {
                    val builder = HttpRequest.newBuilder(URI("http://127.0.0.1:${server.address.port}/$path"))
                        .method(method, HttpRequest.BodyPublishers.noBody())
                    if(range != null) builder.header("Range", range)
                    return client.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray())
                }
                val first = request("bytes.bin", "bytes=10-19")
                assertEquals(206, first.statusCode())
                assertEquals("bytes 10-19/100", first.headers().firstValue("Content-Range").orElseThrow())
                assertArrayEquals(ByteArray(10) { (it + 10).toByte() }, first.body())
                assertArrayEquals(byteArrayOf(98,99), request("bytes.bin", "bytes=98-999").body())
                assertArrayEquals(byteArrayOf(98,99), request("bytes.bin", "bytes=-2").body())
                assertEquals(2, request("bytes.bin", "bytes=98-").body().size)
                for(range in listOf("bytes=100-", "bytes=4-2", "bytes=-0", "bytes=99999999999999999999-")) {
                    val invalid=request("bytes.bin", range)
                    assertEquals(416, invalid.statusCode()); assertEquals(0, invalid.body().size)
                }
                val head=request("bytes.bin", "bytes=0-9", "HEAD")
                assertEquals(200,head.statusCode()); assertEquals(0,head.body().size)
                assertEquals("100",head.headers().firstValue("Content-Length").orElseThrow())
                assertEquals(200,request("empty.bin").statusCode())
                assertEquals("bytes */0",request("empty.bin","bytes=0-0").headers().firstValue("Content-Range").orElseThrow())
                assertEquals(404,request("missing.bin").statusCode())
                assertEquals(405,request("bytes.bin",method="POST").statusCode())
                assertEquals(100,request("bytes.bin","bytes=0-1,5-6").body().size)
            }
        } finally { server.stop(0) }
    }
}
