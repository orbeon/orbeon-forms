package org.orbeon.oxf.http

import org.orbeon.oxf.externalcontext.{ExternalContext, LocalResponse}
import org.orbeon.oxf.util.{ConnectionResult, TryUtils}
import org.orbeon.oxf.util.TryUtils._

import scala.util.{Failure, Success, Try}
import java.io.ByteArrayOutputStream

case class Ranges(ranges: Seq[Range] = Seq(), ifRange: Option[IfRange] = None) {
  val singleRange: Option[Range] = ranges.headOption
}

case class Range(start: Long, end: Option[Long])

sealed trait IfRange
case class IfRangeDate(date: Long)   extends IfRange
case class IfRangeETag(etag: String) extends IfRange

object Ranges {
  private val Units = "bytes"

  def apply(request: ExternalContext.Request): Try[Ranges] =
    request.getFirstHeaderIgnoreCase(Headers.Range) match {
      case None    => Success(Ranges())
      case Some(s) =>
        for {
          withoutPrefix <- withoutUnitPrefix(s)
          ranges        <- TryUtils.sequenceLazily(withoutPrefix.split(','))(Range.apply)
          // TODO: parse ifRange
        } yield Ranges(ranges, None)
    }

  private def withoutUnitPrefix(s: String): Try[String] = {
    // The header must have the following form: bytes=0-1023
    val parts = s.split('=')

    if (parts.length == 2 && parts(0).trim == Units)
      Success(parts(1).trim)
    else
      Failure(new IllegalArgumentException(s"Invalid range header: $s"))
  }

  def rangeHeadersFromRequest(request: ExternalContext.Request): Seq[(String, List[String])] =
    for {
      header <- Seq(Headers.Range, Headers.IfRange)
      value  <- request.getFirstHeaderIgnoreCase(header)
    } yield header -> List(value)

  def forwardRangeHeadersAndStatus(cxr: ConnectionResult, response: ExternalContext.Response): Unit = {
    for {
      header <- Seq(Headers.AcceptRanges, Headers.ContentRange)
      value  <- cxr.getFirstHeaderIgnoreCase(header)
    } {
      response.setHeader(header, value)
    }

    if (Set(StatusCode.PartialContent, StatusCode.RequestedRangeNotSatisfiable).contains(cxr.statusCode)) {
      // TODO: check if we want to forward other status codes
      response.setStatus(cxr.statusCode)
    }
  }

  def respondWithAcceptRangesHeader(response: ExternalContext.Response, totalSize: Option[Long]): Unit = {
    response.setHeader(Headers.AcceptRanges, Units)
    totalSize.foreach(size => response.setHeader(Headers.ContentLength, size.toString))
  }

  def respondWithContentRangeHeader(response: ExternalContext.Response, start: Long, end: Long, totalSize: Long): Unit = {
    response.setHeader(Headers.ContentRange, s"bytes $start-${end - 1}/$totalSize")
    response.setHeader(Headers.ContentLength, (end - start).toString)
  }

  def extractRangeFromHttpResponseIfNeeded(
    request  : ExternalContext.Request,
    response : ExternalContext.Response
  ): Unit = {
    val successfulResponse = response match {
      case response: LocalResponse => response.statusCode == StatusCode.Ok
      case _                       => true
    }

    if (successfulResponse) {
      if (request.getMethod == HttpMethod.HEAD) {
        respondWithAcceptRangesHeader(response, totalSize = None)
      } else if (request.getMethod == HttpMethod.GET)
        for {
          ranges     <- Ranges(request).toOption
          range      <- ranges.singleRange
          baos       <- Option(response.getOutputStream).collect { case baos: ByteArrayOutputStream => baos }
          bufferSize = baos.size()
          if bufferSize > 0
        } locally {
          val start = range.start.toInt
          val end   = range.end.map(_.toInt + 1).getOrElse(bufferSize)

          val newOutput = baos.toByteArray.slice(start, end)
          baos.reset()
          baos.write(newOutput)

          respondWithContentRangeHeader(response, start, end, bufferSize)
          response.setStatus(StatusCode.PartialContent)
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
