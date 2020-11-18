/**
 * Copyright (C) 2010 Orbeon, Inc.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation; either version
 * 2.1 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.xforms.event.events

import org.log4s
import org.orbeon.io.IOUtils
import org.orbeon.io.IOUtils._
import org.orbeon.oxf.common.ValidationException
import org.orbeon.oxf.resources.URLFactory
import org.orbeon.oxf.util.MarkupUtils._
import org.orbeon.oxf.util.StaticXPath.DocumentNodeInfoType
import org.orbeon.oxf.util.TryUtils._
import org.orbeon.oxf.util._
import org.orbeon.oxf.xforms.XFormsGlobalProperties
import org.orbeon.oxf.xforms.event.XFormsEvent
import org.orbeon.oxf.xforms.event.XFormsEvent._
import org.orbeon.oxf.xforms.submission.XFormsModelSubmission
import org.orbeon.oxf.xml._
import org.orbeon.saxon.om
import org.orbeon.xforms.XFormsCrossPlatformSupport

import scala.collection.immutable
import scala.util.Try
import scala.util.control.NonFatal


// Helper trait for `xforms-submit-done`/`xforms-submit-error`
trait SubmitResponseEvent extends XFormsEvent {

  def connectionResult: Option[ConnectionResult]
  final def headers: Option[Map[String, List[String]]] = connectionResult map (_.headers)

  // For a given event, temporarily keep a reference to the body so that it's possible to call
  // `event('response-body')` multiple times.
  var cachedBody: Option[Option[String Either DocumentNodeInfoType]] = None

  override implicit def indentedLogger = containingDocument.getIndentedLogger(XFormsModelSubmission.LOGGING_CATEGORY)
  override def lazyProperties = getters(this, SubmitResponseEvent.Getters)
  override def newPropertyName(name: String) = SubmitResponseEvent.Deprecated.get(name) orElse super.newPropertyName(name)
}

private object SubmitResponseEvent {

  import org.orbeon.oxf.util.XPathCache._

  // "Zero or more elements, each one representing a content header in the error response received by a
  // failed submission. The returned node-set is empty if the failed submission did not receive an error
  // response or if there were no headers. Each element has a local name of header with no namespace URI and
  // two child elements, name and value, whose string contents are the name and value of the header,
  // respectively."
  def headersDocument(headersOpt: Option[Iterable[(String, immutable.Seq[String])]]): Option[DocumentNodeInfoType] =
    headersOpt filter (_.nonEmpty) map { headers =>
      val sb = new StringBuilder
      sb.append("<headers>")
      for ((name, values) <- headers) {
        sb.append("<header><name>")
        sb.append(name.escapeXmlMinimal)
        sb.append("</name>")
        for (value <- values) {
          sb.append("<value>")
          sb.append(value.escapeXmlMinimal)
          sb.append("</value>")
        }
        sb.append("</header>")
      }
      sb.append("</headers>")
      TransformerUtils.stringToTinyTree(XPath.GlobalConfiguration, sb.toString, false, false) // handleXInclude, handleLexical
    }

  def headerElements(e: SubmitResponseEvent): Option[Seq[om.Item]] = {
    implicit val ctx = XPathContext(locationData = e.locationData)
    headersDocument(e.headers) map (evaluateKeepItems("/headers/header", _))
  }

  def body(e: SubmitResponseEvent): Option[AnyRef] = {
    implicit val logger = e.indentedLogger

    def readOrReturn(cxr: ConnectionResult): Option[String Either DocumentNodeInfoType] =
      e.cachedBody getOrElse {
        val result = tryToReadBody(cxr)
        e.cachedBody = Some(result)
        result
      }

    e.connectionResult flatMap readOrReturn map {
      case Left(string)    => string
      case Right(document) => document
    }
  }

  private def tryToReadBody(cxr: ConnectionResult)(implicit logger: IndentedLogger): Option[String Either DocumentNodeInfoType] = {
    // Log response details if not done already
    cxr.logResponseDetailsOnce(log4s.Error)

    if (cxr.hasContent) {

      // XForms 1.1:
      //
      // "When the error response specifies an XML media type as defined by [RFC 3023], the response body is
      // parsed into an XML document and the root element of the document is returned. If the parse fails, or if
      // the error response specifies a text media type (starting with text/), then the response body is returned
      // as a string. Otherwise, an empty string is returned."
      //
      // We go a bit further, trying to read independently from the returned mediatype.

      def warn[T](message: String): PartialFunction[Throwable, Option[T]] = {
        case NonFatal(t) =>
          logger.logWarning("xforms-submit-done|error", message, t)
          None
      }

      // Read the whole stream to a temp URI so we can read it more than once if needed. This allows trying reading
      // as XML then as text.
      val tempURIOpt =
        try {
          XFormsCrossPlatformSupport.inputStreamToRequestUri(cxr.content.inputStream)
        } catch {
          warn("error while reading response body")
        }

      tempURIOpt flatMap { tempURI =>

        // TODO: RFC 7303 says that content type charset must take precedence with any XML mediatype.
        // Should modify readTinyTree() and readDom4j()
        def tryXML: Try[String Either DocumentNodeInfoType] =
          Try {
            Right(
              useAndClose(URLFactory.createURL(tempURI).openStream()) { is =>
                TransformerUtils.readTinyTree(XPath.GlobalConfiguration, is, cxr.url, false, true)
              }
            )
          }

        def tryText: Try[String Either DocumentNodeInfoType]  =
          Try {
            Left(IOUtils.readStreamAsStringAndClose(URLFactory.createURL(tempURI).openStream(), cxr.charset))
          }

        def asString(value: String Either DocumentNodeInfoType) = value match {
          case Left(text) => text
          case Right(xml) => StaticXPath.tinyTreeToString(xml)
        }

        val result = tryXML orElse tryText onFailure warn("error while reading response body") toOption

        // See https://github.com/orbeon/orbeon-forms/issues/3082
        if (XFormsGlobalProperties.getErrorLogging.contains("submission-error-body") && ! cxr.isSuccessResponse)
          result map asString foreach { value =>
            logger.logError("xforms-submit-done|error", "setting body document", "body", s"\n$value")
          }

        result
      }
    } else
      None
  }

  val Deprecated = Map(
    "body" -> "response-body"
  )

  val Getters = Map[String, SubmitResponseEvent => Option[Any]] (
    "response-headers"       -> headerElements,
    "response-reason-phrase" -> (e => throw new ValidationException("Property Not implemented yet: " + "response-reason-phrase", e.locationData)),
    "response-body"          -> body,
    "body"                   -> body,
    "resource-uri"           -> (e => e.connectionResult flatMap (c => Option(c.url))),
    "response-status-code"   -> (e => e.connectionResult flatMap (c => Option(c.statusCode) filter (_ > 0)))
  )
}