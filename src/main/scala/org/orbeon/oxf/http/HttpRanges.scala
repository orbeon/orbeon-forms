package org.orbeon.oxf.http

import org.orbeon.oxf.externalcontext.ExternalContext
import org.orbeon.oxf.util.TryUtils._
import org.orbeon.oxf.util.{ConnectionResult, TryUtils}

import java.io.{File, InputStream}
import scala.jdk.CollectionConverters._
import scala.util.{Failure, Success, Try}

// ifRange is not supported yet (implement ifRange method if needed)
case class HttpRanges(ranges: Seq[HttpRange] = Seq(), ifRange: Option[IfRange] = None) {
  val singleRange: Option[HttpRange] = ranges.headOption

  def streamedFile(file: File, newFileInputStream: => InputStream): Try[StreamedFile] = {
    val fileLength  = file.length()

    // Only support single ranges for now (browsers don't seem to send requests for multiple ranges)
    singleRange match {
      case Some(range) =>
        val outOfBounds = range.start >= fileLength || range.end.exists(_ >= fileLength)

        if (outOfBounds)
          Failure(new Exception(s"Range ${range.asString} out of bounds (file size: $fileLength)"))
        else
          Try(StreamedFile(file, StatusCode.PartialContent, range.responseHeaders(fileLength), FileRangeInputStream(file, range)))

      case None =>
        Try(StreamedFile(file, StatusCode.Ok, HttpRanges.acceptRangesHeader(fileLength), newFileInputStream))
    }
  }
}

// End value is inclusive
case class HttpRange(start: Long, end: Option[Long]) {
  def asString: String =
    s"$start-${end.map(_.toString).getOrElse("")}"

  def effectiveEnd(totalSize: Long): Long =
    end.getOrElse(totalSize - 1)

  def length(totalSize: Long): Long =
    effectiveEnd(totalSize) - start + 1

  def contentRangeHeaderValue(totalSize: Long): String =
    s"bytes $start-${effectiveEnd(totalSize)}/$totalSize"

  def responseHeaders(totalSize: Long): Map[String, List[String]] = Map(
    Headers.ContentRange  -> List(contentRangeHeaderValue(totalSize)),
    Headers.ContentLength -> List(length(totalSize).toString)
  )

  def rangeHeaderValue: String =
    s"bytes=$asString"

  def requestHeaders: Map[String, List[String]] =
    Map(Headers.Range -> List(rangeHeaderValue))
}

sealed trait IfRange
case class IfRangeDate(date: Long)   extends IfRange
case class IfRangeETag(etag: String) extends IfRange

case class StreamedFile(file: File, statusCode: Int, headers: Map[String, List[String]], inputStream: InputStream)

object HttpRanges {
  private val Units = "bytes"

  def apply[T](headers: Iterable[(String, T)])(implicit ev: T => Iterable[String]): Try[HttpRanges] =
    for {
      ranges  <- ranges(headers)
      ifRange <- ifRange(headers)
    } yield HttpRanges(ranges, ifRange)

  def apply(request: ExternalContext.Request): Try[HttpRanges] =
    apply(request.getHeaderValuesMap.asScala)

  private def withoutUnitPrefix(s: String): Try[String] = {
    // The header must have the following form: bytes=0-1023
    val parts = s.split('=')

    if (parts.length == 2 && parts(0).trim == Units)
      Success(parts(1).trim)
    else
      Failure(new IllegalArgumentException(s"Invalid range header: $s"))
  }

  private def ranges[T](headers: Iterable[(String, T)])(implicit ev: T => Iterable[String]): Try[Seq[HttpRange]] =
    Headers.firstItemIgnoreCase(headers, Headers.Range) match {
      case None    => Success(Seq())
      case Some(s) =>
        for {
          withoutPrefix <- withoutUnitPrefix(s)
          ranges        <- TryUtils.sequenceLazily(withoutPrefix.split(','))(HttpRange.apply)
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

  def acceptRangesHeader(totalSize: Long): Map[String, List[String]] = Map(
    Headers.AcceptRanges  -> List(Units),
    Headers.ContentLength -> List(totalSize.toString)
  )
}

object HttpRange {
  def apply(s: String): Try[HttpRange] = {
    val trimmed = s.trim
    val parts   = trimmed.split('-').map(_.trim)

    def httpRange(startString: String, endString: Option[String]): Try[HttpRange] =
      (
        for {
          start <- Try(startString.toLong)
          end   <- Try(endString.map(_.toLong))
          _     <- end match {
            case Some(end) if end < start => Failure(new IllegalArgumentException(s"Invalid range: $s"))
            case _                        => Success(())
          }
        } yield HttpRange(start, end)
      ).onFailure {
        case _: NumberFormatException =>
          // Rewrite exception message ("For input string") to something more meaningful
          Failure(new NumberFormatException(s"Invalid number found in range: $s"))
      }

    if (parts.length == 2 && ! trimmed.endsWith("-"))
      httpRange(parts(0), Some(parts(1)))
    else if (parts.length == 1 && trimmed.endsWith("-"))
      httpRange(parts(0), None)
    else
      Failure(new IllegalArgumentException(s"Invalid range: $s"))
  }
}
