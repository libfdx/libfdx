package io.github.libfdx.gradle

import com.sun.net.httpserver.HttpExchange
import java.io.EOFException
import java.io.File
import java.io.RandomAccessFile

/** Bounded local asset serving, including one HTTP byte range and HEAD metadata. */
internal fun serveWebFile(root: File, exchange: HttpExchange) {
    exchange.use {
        val method = exchange.requestMethod
        if(method != "GET" && method != "HEAD") {
            exchange.responseHeaders.set("Allow", "GET, HEAD")
            exchange.sendResponseHeaders(405, -1)
            return
        }
        val rawPath = exchange.requestURI.path.trimStart('/')
        val requested = File(root, if(rawPath.isEmpty()) "index.html" else rawPath).canonicalFile
        val file = if(requested.isDirectory) File(requested, "index.html").canonicalFile else requested
        if(!file.toPath().startsWith(root.toPath()) || !file.isFile) {
            exchange.sendResponseHeaders(404, -1)
            return
        }
        RandomAccessFile(file, "r").use { input ->
            val size = input.length()
            val range = if(method == "GET" && exchange.requestHeaders.getFirst("If-Range") == null)
                exchange.requestHeaders.getFirst("Range") else null
            var start = 0L
            var end = size - 1
            var partial = false
            // Multiple ranges and unknown units are deliberately ignored (a full 200 response).
            if(range != null && range.startsWith("bytes=") && !range.contains(',')) {
                val match = Regex("bytes=([0-9]*)-([0-9]*)").matchEntire(range)
                var valid = match != null && size > 0
                if(valid) {
                    val first = match!!.groupValues[1]
                    val last = match.groupValues[2]
                    if(first.isEmpty()) {
                        val suffix = last.toLongOrNull()
                        valid = suffix != null && suffix > 0
                        if(valid) start = (size - suffix!!).coerceAtLeast(0)
                    } else {
                        val lower = first.toLongOrNull()
                        val upper = if(last.isEmpty()) size - 1 else last.toLongOrNull()
                        valid = lower != null && upper != null && lower < size && upper >= lower
                        if(valid) { start = lower!!; end = upper!!.coerceAtMost(size - 1) }
                    }
                }
                if(!valid) {
                    exchange.responseHeaders.set("Content-Range", "bytes */$size")
                    exchange.sendResponseHeaders(416, -1)
                    return
                }
                partial = true
            }
            val count = if(size == 0L) 0 else end - start + 1
            exchange.responseHeaders.set("Accept-Ranges", "bytes")
            exchange.responseHeaders.set("Content-Type", webContentType(file.name))
            exchange.responseHeaders.set("Content-Length", count.toString())
            if(partial) exchange.responseHeaders.set("Content-Range", "bytes $start-$end/$size")
            exchange.sendResponseHeaders(if(partial) 206 else 200, if(method == "HEAD" || count == 0L) -1 else count)
            if(method == "HEAD" || count == 0L) return
            input.seek(start)
            val buffer = ByteArray(minOf(65536L, count).toInt())
            var remaining = count
            while(remaining > 0) {
                val read = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                if(read < 0) throw EOFException("Asset changed while serving ${file.name}")
                exchange.responseBody.write(buffer, 0, read)
                remaining -= read
            }
        }
    }
}

private fun webContentType(name: String): String {
    return when {
        name.endsWith(".html") -> "text/html; charset=utf-8"
        name.endsWith(".js") -> "text/javascript; charset=utf-8"
        name.endsWith(".wasm") -> "application/wasm"
        name.endsWith(".json") || name.endsWith(".gltf") -> "application/json; charset=utf-8"
        name.endsWith(".glb") -> "model/gltf-binary"
        name.endsWith(".bin") -> "application/octet-stream"
        name.endsWith(".txt") -> "text/plain; charset=utf-8"
        name.endsWith(".css") -> "text/css; charset=utf-8"
        name.endsWith(".png") -> "image/png"
        name.endsWith(".jpg") || name.endsWith(".jpeg") -> "image/jpeg"
        else -> "application/octet-stream"
    }
}
