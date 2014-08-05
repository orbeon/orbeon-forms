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

import org.orbeon.oxf.resources.URLFactory

import collection.JavaConverters._
import org.orbeon.oxf.common.ValidationException
import org.orbeon.oxf.util._
import org.orbeon.oxf.xforms.event.XFormsEvent
import org.orbeon.oxf.xforms.submission.XFormsModelSubmission
import org.orbeon.oxf.xml._
import org.orbeon.saxon.om._
import org.apache.log4j.Level
import org.orbeon.oxf.util.ScalaUtils._
import java.net.URL
import org.orbeon.oxf.xforms.XFormsProperties
import java.io.InputStreamReader
import org.orbeon.oxf.xforms.event.XFormsEvent._
import scala.collection.immutable
import scala.util.control.NonFatal

// Helper trait for xforms-submit-done/xforms-submit-error
trait SubmitResponseEvent extends XFormsEvent {

    def connectionResult: Option[ConnectionResult]
    final def headers = connectionResult map (_.responseHeaders)

    override implicit def indentedLogger = containingDocument.getIndentedLogger(XFormsModelSubmission.LOGGING_CATEGORY)
    override def lazyProperties = getters(this, SubmitResponseEvent.Getters)
    override def newPropertyName(name: String) = SubmitResponseEvent.Deprecated.get(name) orElse super.newPropertyName(name)
}

private object SubmitResponseEvent {

    import NamespaceMapping.EMPTY_MAPPING
    import XPathCache._

    // "Zero or more elements, each one representing a content header in the error response received by a
    // failed submission. The returned node-set is empty if the failed submission did not receive an error
    // response or if there were no headers. Each element has a local name of header with no namespace URI and
    // two child elements, name and value, whose string contents are the name and value of the header,
    // respectively."
    def headersDocument(headersOpt: Option[Iterable[(String, immutable.Seq[String])]]): Option[DocumentInfo] =
        headersOpt filter (_.nonEmpty) map { headers ⇒
            val sb = new StringBuilder
            sb.append("<headers>")
            for ((name, values) ← headers) {
                sb.append("<header><name>")
                sb.append(XMLUtils.escapeXMLMinimal(name))
                sb.append("</name>")
                for (value ← values) {
                    sb.append("<value>")
                    sb.append(XMLUtils.escapeXMLMinimal(value))
                    sb.append("</value>")
                }
                sb.append("</header>")
            }
            sb.append("</headers>")
            TransformerUtils.stringToTinyTree(XPath.GlobalConfiguration, sb.toString, false, false) // handleXInclude, handleLexical
        }

    def headerElements(e: SubmitResponseEvent): Option[Seq[Item]] = headersDocument(e.headers) map { document: Item ⇒
        evaluateKeepItems(
            Seq(document).asJava,
            1,
            "/headers/header",
            EMPTY_MAPPING,
            null, null, null, null,
            e.locationData,
            e.containingDocument.getRequestStats.addXPathStat).asScala
    }

    def body(e: SubmitResponseEvent): Option[AnyRef] = {
        implicit val logger = e.indentedLogger
        e.connectionResult flatMap tryToReadBody map {
            case Left(string)    ⇒ string
            case Right(document) ⇒ document
        }
    }

    def tryToReadBody(connectionResult: ConnectionResult)(implicit logger: IndentedLogger): Option[String Either DocumentInfo] = {
        // Log response details if not done already
        connectionResult.logResponseDetailsIfNeeded(logger, Level.ERROR, "xforms-submit-error")

        // Try to add body information if present
        if (connectionResult.hasContent) {

            // "When the error response specifies an XML media type as defined by [RFC 3023], the response body is
            // parsed into an XML document and the root element of the document is returned. If the parse fails, or if
            // the error response specifies a text media type (starting with text/), then the response body is returned
            // as a string. Otherwise, an empty string is returned."

            def warn[T](message: String): PartialFunction[Throwable, Option[T]] = {
                case NonFatal(t) ⇒
                    logger.logWarning("xforms-submit-error", message, t)
                    None
            }

            // Read the whole stream to a temp URI so we can read it more than once if needed
            val tempURIOpt =
                try useAndClose(connectionResult.getResponseInputStream) { is ⇒
                    Option(NetUtils.inputStreamToAnyURI(is, NetUtils.REQUEST_SCOPE, logger.getLogger))
                } catch
                    warn("error while reading response body.")

            tempURIOpt flatMap { tempURI ⇒

                def asDocument =
                    if (XMLUtils.isXMLMediatype(connectionResult.getResponseMediaType)) {
                        // XML content-type
                        // Read stream into Document
                        // TODO: In case of text/xml, charset is not handled. Should modify readTinyTree() and readDom4j()
                        try useAndClose(new URL(tempURI).openStream()) { is ⇒
                            val document = TransformerUtils.readTinyTree(XPath.GlobalConfiguration, is, connectionResult.resourceURI, false, true)
                            if (XFormsProperties.getErrorLogging.contains("submission-error-body"))
                                logger.logError("xforms-submit-error", "setting body document", "body", "\n" + TransformerUtils.tinyTreeToString(document))
                            Some(document)
                        } catch
                            warn("error while parsing response body as XML, defaulting to plain text.")
                    } else
                        None

                def asString =
                    if (XMLUtils.isTextOrJSONContentType(connectionResult.getResponseMediaType)) {
                        // XML parsing failed, or we got a text content-type
                        // Read stream into String
                        try {
                            val charset = NetUtils.getTextCharsetFromContentType(connectionResult.getResponseContentType)
                            val is = URLFactory.createURL(tempURI).openStream()
                            useAndClose(new InputStreamReader(is, charset)) { reader ⇒
                                val string = NetUtils.readStreamAsString(reader)
                                if (XFormsProperties.getErrorLogging.contains("submission-error-body"))
                                    logger.logError("xforms-submit-error", "setting body string", "body", "\n" + string)
                                Some(string)
                            }
                        } catch
                            warn("error while reading response body ")
                    } else {
                        // This is binary
                        // Don't store anything for now
                        None
                    }

                asDocument orElse asString match {
                    case Some(document: DocumentInfo) ⇒ Some(Right(document))
                    case Some(string: String) ⇒ Some(Left(string))
                    case _ ⇒ None
                }
            }
        } else
            None
    }

    val Deprecated = Map(
        "body" → "response-body"
    )

    val Getters = Map[String, SubmitResponseEvent ⇒ Option[Any]] (
        "response-headers"       → headerElements,
        "response-reason-phrase" → (e ⇒ throw new ValidationException("Property Not implemented yet: " + "response-reason-phrase", e.locationData)),
        "response-body"          → body,
        "body"                   → body,
        "resource-uri"           → (e ⇒ e.connectionResult flatMap (c ⇒ Option(c.resourceURI))),
        "response-status-code"   → (e ⇒ e.connectionResult flatMap (c ⇒ Option(c.statusCode) filter (_ > 0)))
    )
}