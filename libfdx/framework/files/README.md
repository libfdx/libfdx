# File input

Whole-file `readBytes` and `readString` return futures. They may complete inline;
compose them instead of waiting on the application loop. Default filesystem I/O
is synchronous and can be scheduled on an application-owned worker. Browser cache
hits complete inline; uncached browser reads use asynchronous fetch.

`FileHandle.openRead(maxReadBytes)` opens an owned `FileDataSource`. Its default
request limit is 64 KiB. Reads write into caller-provided storage, accept byte
offsets, and return a byte count or -1 at EOF. Short reads are legal. Keep the
destination untouched until completion and close the source when finished.
The [declaration](src/main/java/io/github/libfdx/files/FileDataSource.java) owns
the concurrency, failure, offset and disposal contracts.

| Provider path | Bounded behavior |
| --- | --- |
| Default filesystem disk files | Synchronous reads, known length, forward/backward offsets. Backward seeks reopen the stream. |
| Default packaged/classpath streams | Sequential reads, potentially unknown length; arbitrary offsets fail. |
| Browser preloaded/writable assets | Bounded copies from existing encoded storage; no additional whole-file copy. The existing preload still consumes memory. |
| Browser deferred HTTP files | Asynchronous byte ranges, known length after the opening probe, seekable; one request in flight. |
| Other file providers | Optional; the default fails explicitly instead of buffering the entire file. |

For streaming on web, configure `WebApplicationConfig.deferAssets("music/")` or
exact filenames. Files remain packaged and discoverable in the manifest, while
startup skips their download. Synchronous font/skin helpers need preloaded inputs;
load deferred data through future composition or the asset manager.

The HTTP server must return valid uncompressed `206 Content-Range` responses.
An ignored range fails before a whole-file body is consumed. The built-in Gradle
web runner supports these requests; other hosts need byte-range support and, for
cross-origin use, appropriate CORS response headers. Closing a pending browser
source aborts its request and prevents late writes to the destination. One bounded
response buffer is used per request; browser network buffering is browser-owned.

Asset loaders can schedule opens and chunk reads with `AssetLoadContext.asyncFuture`.
Callbacks then share the manager's application-thread update budget. A delivered
source transfers ownership to the loader; it must close or transfer that source,
including when a later stage fails or is cancelled. Music streams and large-map
readers should own independent sources rather than sharing a sequential cursor.
