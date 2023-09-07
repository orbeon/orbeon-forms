package org.orbeon.oxf.http

import org.orbeon.oxf.externalcontext.{ExternalContext, LocalResponse}
import org.orbeon.oxf.util.TryUtils._
import org.orbeon.oxf.util.{ConnectionResult, TryUtils}

import java.io.{ByteArrayOutputStream, File, InputStream}
import scala.jdk.CollectionConverters._
import scala.util.{Failure, Success, Try}

case class Ranges(ranges: Seq[Range] = Seq(), ifRange: Option[IfRange] = None) {
  val singleRange: Option[Range] = ranges.headOption

  def streamedFile(file: File, newFileInputStream: => InputStream): Try[StreamedFile] = {
    val fileLength  = file.length()

    // Only support single range for now
    singleRange match {
      case Some(range) =>
        val outOfBounds = range.start >= fileLength || range.end.exists(_ >= fileLength)

        if (outOfBounds)
          Failure(new Exception(s"Range ${range.asString} out of bounds (file size: $fileLength)"))
        else
          Try(StreamedFile(file, StatusCode.PartialContent, range.headers(fileLength), FileRangeInputStream(file, range)))

      case None =>
        Try(StreamedFile(file, StatusCode.Ok, Ranges.acceptRangesHeader(fileLength), newFileInputStream))
    }
  }
}

// End value is inclusive
case class Range(start: Long, end: Option[Long]) {
  def asString: String =
    s"$start-${end.map(_.toString).getOrElse("")}"

  def effectiveEnd(totalSize: Long): Long =
    end.getOrElse(totalSize - 1)

  def length(totalSize: Long): Long =
    effectiveEnd(totalSize) - start + 1

  def contentRangeHeader(totalSize: Long): String =
    s"bytes $start-${effectiveEnd(totalSize)}/$totalSize"

  def headers(totalSize: Long): Map[String, List[String]] = Map(
    Headers.ContentRange  -> List(contentRangeHeader(totalSize)),
    Headers.ContentLength -> List(length(totalSize).toString)
  )
}

sealed trait IfRange
case class IfRangeDate(date: Long)   extends IfRange
case class IfRangeETag(etag: String) extends IfRange

case class StreamedFile(file: File, statusCode: Int, headers: Map[String, List[String]], inputStream: InputStream)

object Ranges {
  private val Units = "bytes"

  def apply[T](headers: Iterable[(String, T)])(implicit ev: T => Iterable[String]): Try[Ranges] =
    for {
      ranges  <- ranges(headers)
      ifRange <- ifRange(headers)
    } yield Ranges(ranges, ifRange)

  def apply(request: ExternalContext.Request): Try[Ranges] =
    apply(request.getHeaderValuesMap.asScala)

  private def withoutUnitPrefix(s: String): Try[String] = {
    // The header must have the following form: bytes=0-1023
    val parts = s.split('=')

    if (parts.length == 2 && parts(0).trim == Units)
      Success(parts(1).trim)
    else
      Failure(new IllegalArgumentException(s"Invalid range header: $s"))
  }

  private def ranges[T](headers: Iterable[(String, T)])(implicit ev: T => Iterable[String]): Try[Seq[Range]] =
    Headers.firstItemIgnoreCase(headers, Headers.Range) match {
      case None    => Success(Seq())
      case Some(s) =>
        for {
          withoutPrefix <- withoutUnitPrefix(s)
          ranges        <- TryUtils.sequenceLazily(withoutPrefix.split(','))(Range.apply)
        } yield ranges
    }

  private def ifRange[T](headers: Iterable[(String, T)])(implicit ev: T => Iterable[String]): Try[Option[IfRange]] =
    Headers.firstItemIgnoreCase(headers, Headers.IfRange) match {
      case None    => Success(None)
      case Some(s) =>
        // TODO: parse If-Range header
        Success(None)
    }

  def rangeHeadersFromRequest(request: ExternalContext.Request): Map[String, List[String]] =
    (for {
      header <- Seq(Headers.Range, Headers.IfRange)
      value  <- request.getFirstHeaderIgnoreCase(header)
    } yield header -> List(value)).toMap

  def forwardRangeHeaders(cxr: ConnectionResult, response: ExternalContext.Response): Unit =
    for {
      header <- Seq(Headers.AcceptRanges, Headers.ContentRange)
      value  <- cxr.getHeaderIgnoreCase(header)
    } locally {
      response.addHeader(header, value)
    }

  implicit class ResponseOps(response: ExternalContext.Response) {
    def addHeaders(headers: Map[String, List[String]]): Unit =
      for {
        (name, values) <- headers
        value          <- values
      } locally {
        response.addHeader(name, value)
      }
  }

  def acceptRangesHeader(totalSize: Long): Map[String, List[String]] = Map(
    Headers.AcceptRanges  -> List(Units),
    Headers.ContentLength -> List(totalSize.toString)
  )

  def extractRangeFromHttpResponseIfNeeded(request: ExternalContext.Request, response: ExternalContext.Response): Unit = {
    val successfulResponse = response match {
      case response: LocalResponse => response.statusCode == StatusCode.Ok
      case _                       => true
    }

    if (successfulResponse && (request.getMethod == HttpMethod.HEAD || request.getMethod == HttpMethod.GET)) {
      for {
        ranges     <- Ranges(request).toOption
        baos       <- Option(response.getOutputStream).collect { case baos: ByteArrayOutputStream => baos }
        bufferSize = baos.size()
        if bufferSize > 0
      } locally {
        ranges.singleRange match {
          case Some(range) if request.getMethod == HttpMethod.GET =>
            val start = range.start.toInt
            val end   = range.effectiveEnd(bufferSize).toInt

            val newOutput = baos.toByteArray.slice(start, end + 1)
            baos.reset()
            baos.write(newOutput)

            response.addHeaders(range.headers(bufferSize))
            response.setStatus(StatusCode.PartialContent)

          case _ =>
            response.addHeaders(Ranges.acceptRangesHeader(bufferSize))
        }
      }
    }
  }
}

object Range {
  def apply(s: String): Try[Range] = {
    val trimmed = s.trim
    val parts   = trimmed.split('-').map(_.trim)

    def range(startString: String, endString: Option[String]): Try[Range] =
      (
        for {
          start <- Try(startString.toLong)
          end   <- Try(endString.map(_.toLong))
          _     <- end match {
            case Some(end) if end < start => Failure(new IllegalArgumentException(s"Invalid range: $s"))
            case _                        => Success(())
          }
        } yield Range(start, end)
      ).onFailure {
        case _: NumberFormatException =>
          // Rewrite exception message ("For input string") to something more meaningful
          Failure(new NumberFormatException(s"Invalid number found in range: $s"))
      }

    if (parts.length == 2 && ! trimmed.endsWith("-"))
      range(parts(0), Some(parts(1)))
    else if (parts.length == 1 && trimmed.endsWith("-"))
      range(parts(0), None)
    else
      Failure(new IllegalArgumentException(s"Invalid range: $s"))
  }
}
