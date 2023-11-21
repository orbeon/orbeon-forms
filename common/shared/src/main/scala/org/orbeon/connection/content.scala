package org.orbeon.connection

import org.orbeon.io.IOUtils.{runQuietly, useAndClose}
import org.orbeon.oxf.http.{EmptyInputStream, Headers}

import java.io.{ByteArrayInputStream, InputStream}
import scala.collection.{immutable => i}


trait Content {
  def inputStream   : InputStream
  def contentType   : Option[String]
  def contentLength : Option[Long]
  def title         : Option[String] // this is only for portlet and should be moved out
}

sealed trait StreamedContentOrRedirect
sealed trait BufferedContentOrRedirect

case class StreamedContent(
  inputStream   : InputStream,
  contentType   : Option[String],
  contentLength : Option[Long],
  title         : Option[String]
) extends StreamedContentOrRedirect with Content {
  def close(): Unit = runQuietly(inputStream.close())
}

object StreamedContent {

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
) extends BufferedContentOrRedirect with Content {
  def inputStream   = new ByteArrayInputStream(body)
  def contentLength = Some(body.size)
}

object BufferedContent {
  def apply(content: StreamedContent)(toByteArray: InputStream => Array[Byte]): BufferedContent =
    BufferedContent(useAndClose(content.inputStream)(toByteArray), content.contentType, content.title)
}

case class Redirect(
  location   : String,
  exitPortal : Boolean = false
) extends StreamedContentOrRedirect with BufferedContentOrRedirect {
  require(location ne null, "Missing redirect location")
}