package org.orbeon.connection

import cats.effect.IO
import org.orbeon.io.IOUtils.{runQuietly, useAndClose}
import org.orbeon.oxf.http.{EmptyInputStream, Headers}

import java.io.{ByteArrayInputStream, InputStream}
import scala.collection.{immutable => i}


sealed trait ContentT[S] {
  def stream        : S
  def contentType   : Option[String]
  def contentLength : Option[Long]
  def title         : Option[String] // this is only for portlet and should be moved out
}

case class StreamedContentT[S](
  stream       : S,
  contentType  : Option[String],
  contentLength: Option[Long],
  title        : Option[String]
) extends ContentT[S] {
  def close(): Unit = runQuietly(stream match {
    case is: InputStream => is.close()
    case _               => // anything to do with `fs2.Stream`?
  }
  )
}

object StreamedContent {

  def apply(
    inputStream   : InputStream,
    contentType   : Option[String],
    contentLength : Option[Long],
    title         : Option[String]
  ): StreamedContent =
    StreamedContentT(
      stream        = inputStream,
      contentType   = contentType,
      contentLength = contentLength,
      title         = title
    )

  def apply(
    stream        : fs2.Stream[IO, Byte],
    contentType   : Option[String],
    contentLength : Option[Long]
  ): AsyncStreamedContent =
    StreamedContentT(
      stream        = stream,
      contentType   = contentType,
      contentLength = contentLength,
      title         = None
    )

  def fromBytes(bytes: Array[Byte], contentType: Option[String], title: Option[String] = None): StreamedContent =
    StreamedContent(
      inputStream   = new ByteArrayInputStream(bytes),
      contentType   = contentType,
      contentLength = Some(bytes.size.toLong),
      title         = title
    )

  def fromStreamAndHeaders(inputStream: InputStream, headers: Map[String, i.Seq[String]], title: Option[String] = None): StreamedContent =
    StreamedContent(
      inputStream   = inputStream,
      contentType   = Headers.firstItemIgnoreCase(headers, Headers.ContentType),
      contentLength = Headers.firstNonNegativeLongHeaderIgnoreCase(headers, Headers.ContentLength),
      title         = title
    )

  val Empty: StreamedContent =
    StreamedContent(
      inputStream   = EmptyInputStream,
      contentType   = None,
      contentLength = None,
      title         = None
    )
}

case class BufferedContent(
  body        : Array[Byte],
  contentType : Option[String],
  title       : Option[String]
) extends ContentT[InputStream] {
  def stream   = new ByteArrayInputStream(body)
  def contentLength: Some[Long] = Some(body.size)
}

object BufferedContent {
  def apply(content: StreamedContent)(toByteArray: InputStream => Array[Byte]): BufferedContent =
    BufferedContent(useAndClose(content.stream)(toByteArray), content.contentType, content.title)
}