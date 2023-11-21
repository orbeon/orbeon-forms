package org.orbeon.connection

import cats.effect.IO
import cats.effect.unsafe.implicits.global

import java.io.{ByteArrayInputStream, InputStream}
import scala.concurrent.{ExecutionContext, Future}


object ConnectionSupport {

  // Convert the `fs2.Stream` to an `InputStream`. Of course, We'd like to stream all the way ideally, but this is a
  // first step. We cannot use `fs2.io.toInputStream` because it requires running two threads, which doesn't work in
  // JavaScript. So we go through an in-memory `Array` for now. Note that sending data also works with `Array`s.
  def fs2StreamToInputStreamInMemory(s: fs2.Stream[IO, Byte]): Future[InputStream] =
    s.compile.to(Array).map(new ByteArrayInputStream(_)).unsafeToFuture()

  def asyncToSyncStreamedContent(content: AsyncStreamedContent)(implicit ec: ExecutionContext): Future[StreamedContent] =
    fs2StreamToInputStreamInMemory(content.stream).map(is =>
      StreamedContent(
        inputStream   = is,
        contentType   = content.contentType,
        contentLength = content.contentLength,
        title         = content.title
      )
    )

  def syncToAsyncStreamedContent(content: StreamedContent): AsyncStreamedContent =
    StreamedContent(
      fs2.io.readInputStream(
        IO.pure(content.stream),
        4096, // TODO: configurable?
        closeAfterUse = false
      ),
      content.contentType,
      content.contentLength
    )
}
