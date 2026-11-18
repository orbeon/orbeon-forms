package org.orbeon.connection

import cats.effect.IO

import java.io.{ByteArrayInputStream, InputStream}


object ConnectionSupport {

  // Convert an `fs2.Stream` to an in-memory `InputStream` for small XML/text submission responses in shared/JS code.
  // On the JVM, `Connection.connectAsync` uses `fs2.io.toInputStreamResource` instead to stream without buffering.
  def fs2StreamToInputStreamInMemory(s: fs2.Stream[IO, Byte]): IO[InputStream] =
    s.compile.to(Array).map(new ByteArrayInputStream(_))

  def syncToAsyncStreamedContent(content: StreamedContent): AsyncStreamedContent =
    StreamedContent(
      fs2.io.readInputStream(
        IO.pure(content.stream),
        chunkSize      = 8192,
        closeAfterUse  = true
      ),
      content.contentType,
      content.contentLength
    )
}
