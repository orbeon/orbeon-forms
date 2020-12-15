/**
 * Copyright (C) 2020 Orbeon, Inc.
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
package org.orbeon.oxf.util

import java.net.URI

import cats.Eval
import org.orbeon.io.UriScheme
import org.orbeon.oxf.externalcontext.ExternalContext
import org.orbeon.oxf.http.Headers._
import org.orbeon.oxf.http.HttpMethod.{GET, HttpMethodsWithRequestBody, POST}
import org.orbeon.oxf.http.{BasicCredentials, HttpMethod, StreamedContent}
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.util.Logging.debug

import scala.jdk.CollectionConverters._


trait ConnectionTrait {

  def connectNow(
    method          : HttpMethod,
    url             : URI,
    credentials     : Option[BasicCredentials],
    content         : Option[StreamedContent],
    headers         : Map[String, List[String]],
    loadState       : Boolean,
    saveState       : Boolean,
    logBody         : Boolean)(implicit
    logger          : IndentedLogger,
    externalContext : ExternalContext
  ): ConnectionResult

  def connectLater(
    method          : HttpMethod,
    url             : URI,
    credentials     : Option[BasicCredentials],
    content         : Option[StreamedContent],
    headers         : Map[String, List[String]],
    loadState       : Boolean,
    logBody         : Boolean)(implicit
    logger          : IndentedLogger,
    externalContext : ExternalContext
  ): Eval[ConnectionResult]

  def isInternalPath(path: String): Boolean

  def findInternalUrl(
    normalizedUrl : URI,
    filter        : String => Boolean)(implicit
    ec            : ExternalContext
  ): Option[String]

  def buildConnectionHeadersCapitalizedIfNeeded(
    url                      : URI, // scheme can be `null`; should we force a scheme, for example `http:/my/service`?
    hasCredentials           : Boolean,
    customHeaders            : Map[String, List[String]],
    headersToForward         : Set[String],
    cookiesToForward         : List[String],
    getHeader                : String => Option[List[String]])(implicit
    logger                   : IndentedLogger,
    externalContext          : ExternalContext,
    coreCrossPlatformSupport : CoreCrossPlatformSupportTrait
  ): Map[String, List[String]]

  def headersToForwardFromProperty: Set[String]
  def cookiesToForwardFromProperty: List[String]

  def getHeaderFromRequest(request: ExternalContext.Request): String => Option[List[String]] =
    Option(request) match {
      case Some(request) => name => request.getHeaderValuesMap.asScala.get(name) map (_.toList)
      case None          => _    => None
    }

  // TODO: `ExternalContext` must expose cookie directly
  def sessionCookieHeaderCapitalized(
    externalContext  : ExternalContext,
    cookiesToForward : List[String])(implicit
    logger           : IndentedLogger
  ): Option[(String, List[String])]

  private def buildSOAPHeadersCapitalizedIfNeeded(
    method                    : HttpMethod,
    mediatypeMaybeWithCharset : Option[String],
    encoding                  : String)(implicit
    logger                    : IndentedLogger
  ): List[(String, List[String])] = {

    require(encoding ne null)

    import org.orbeon.oxf.util.ContentTypes._

    val contentTypeMediaType = mediatypeMaybeWithCharset flatMap getContentTypeMediaType

    // "If the submission mediatype contains a charset MIME parameter, then it is appended to the application/soap+xml
    // MIME type. Otherwise, a charset MIME parameter with same value as the encoding attribute (or its default) is
    // appended to the application/soap+xml MIME type." and "the charset MIME parameter is appended . The charset
    // parameter value from the mediatype attribute is used if it is specified. Otherwise, the value of the encoding
    // attribute (or its default) is used."
    def charsetSuffix(charset: Option[String]) =
      "; charset=" + charset.getOrElse(encoding)

    val newHeaders =
      method match {
        case GET if contentTypeMediaType contains SoapContentType =>
          // Set an Accept header

          val acceptHeader = SoapContentType + charsetSuffix(mediatypeMaybeWithCharset flatMap getContentTypeCharset)

          // Accept header with optional charset
          List(Accept -> List(acceptHeader))

        case POST if contentTypeMediaType contains SoapContentType =>
          // Set Content-Type and optionally SOAPAction headers

          val parameters            = mediatypeMaybeWithCharset map getContentTypeParameters getOrElse Map.empty
          val overriddenContentType = "text/xml" + charsetSuffix(parameters.get(ContentTypes.CharsetParameter))
          val actionParameter       = parameters.get(ContentTypes.ActionParameter)

          // Content-Type with optional charset and SOAPAction header if any
          List(ContentType -> List(overriddenContentType)) ++ (actionParameter map (a => SOAPAction -> List(a)))
        case _ =>
          // Not a SOAP submission
          Nil
      }

    if (newHeaders.nonEmpty)
      debug("adding SOAP headers", newHeaders map { case (k, v) => k -> v.head })

    newHeaders
  }

  def buildConnectionHeadersCapitalizedWithSOAPIfNeeded(
    url                      : URI,
    method                   : HttpMethod,
    hasCredentials           : Boolean,
    mediatypeOpt             : Option[String],
    encodingForSOAP          : String,
    customHeaders            : Map[String, List[String]],
    headersToForward         : Set[String],
    getHeader                : String => Option[List[String]])(implicit
    logger                   : IndentedLogger,
    externalContext          : ExternalContext,
    coreCrossPlatformSupport : CoreCrossPlatformSupportTrait
  ): Map[String, List[String]] =
    if ((url.getScheme eq null) || UriScheme.SchemesWithHeaders(UriScheme.withName(url.getScheme))) {

      // "If a header element defines the Content-Type header, then this setting overrides a Content-type set by the
      // mediatype attribute"
      val headersWithContentTypeIfNeeded =
        mediatypeOpt match {
          case Some(mediatype) if HttpMethodsWithRequestBody(method) && firstItemIgnoreCase(customHeaders, ContentType).isEmpty =>
            customHeaders + (ContentType -> List(mediatype))
          case _ =>
            customHeaders
        }

      // Also make sure that if a header element defines Content-Type, this overrides the mediatype attribute
      def soapMediatypeWithContentType =
        firstItemIgnoreCase(headersWithContentTypeIfNeeded, ContentTypeLower) orElse mediatypeOpt

      // NOTE: SOAP processing overrides Content-Type in the case of a POST
      // So we have: @serialization -> @mediatype ->  xf:header -> SOAP
      val connectionHeadersCapitalized =
        buildConnectionHeadersCapitalized(
          url.normalize,
          hasCredentials,
          headersWithContentTypeIfNeeded,
          headersToForward,
          cookiesToForwardFromProperty,
          getHeader
        )

      val soapHeadersCapitalized =
        buildSOAPHeadersCapitalizedIfNeeded(
          method,
          soapMediatypeWithContentType,
          encodingForSOAP
        )

      connectionHeadersCapitalized ++ soapHeadersCapitalized
    } else
      EmptyHeaders

  /**
   * Build connection headers to send given:
   *
   * - the incoming request if present
   * - a list of headers names and values to set
   * - whether explicit credentials are available (disables forwarding of session cookies and Authorization header)
   * - a list of headers to forward
   */
  def buildConnectionHeadersCapitalized(
    normalizedUrl            : URI,
    hasCredentials           : Boolean,
    customHeadersCapitalized : Map[String, List[String]],
    headersToForward         : Set[String],
    cookiesToForward         : List[String],
    getHeader                : String => Option[List[String]])(implicit
    logger                   : IndentedLogger,
    externalContext          : ExternalContext,
    coreCrossPlatformSupport : CoreCrossPlatformSupportTrait
  ): Map[String, List[String]] = {

    // 1. Caller-specified list of headers to forward based on a space-separated list of header names
    val headersToForwardCapitalized =
      getHeadersToForwardCapitalized(hasCredentials, headersToForward, getHeader)

    // 2. Explicit caller-specified header name/values

    // 3. Forward cookies for session handling only if no credentials have been explicitly set
    val newCookieHeaderCapitalized =
      if (! hasCredentials)
        sessionCookieHeaderCapitalized(externalContext, cookiesToForward)
      else
        None

    // 4. Authorization token only for internal connections
    // https://github.com/orbeon/orbeon-forms/issues/4388
    val tokenHeaderCapitalized = findInternalUrl(normalizedUrl, _ => true).isDefined list {

      // Get token from web app scope
      val token =
        externalContext.getWebAppContext.attributes.getOrElseUpdate(OrbeonTokenLower, coreCrossPlatformSupport.randomHexId).asInstanceOf[String]

      OrbeonToken -> List(token)
    }

    // Don't forward headers for which a value is explicitly passed by the caller, so start with headersToForward
    // New cookie header, if present, overrides any existing cookies
    headersToForwardCapitalized.toMap ++ customHeadersCapitalized ++ newCookieHeaderCapitalized ++ tokenHeaderCapitalized
  }

  // From header names and a getter for header values, find the list of headers to forward
  def getHeadersToForwardCapitalized(
    hasCredentials         : Boolean, // exclude `Authorization` header when true
    headerNamesCapitalized : Set[String],
    getHeader              : String => Option[List[String]])(implicit
    logger                 : IndentedLogger
  ): List[(String, List[String])] = {

    // NOTE: Forwarding the `Cookie` header may yield unpredictable results.

    def canForwardHeader(nameLower: String) = {
      // Only forward Authorization header if there is no credentials provided
      val canForward = nameLower != AuthorizationLower || ! hasCredentials

      if (! canForward)
        debug("not forwarding Authorization header because credentials are present")

      canForward
    }

    for {
      nameCapitalized <- headerNamesCapitalized.toList
      nameLower       = nameCapitalized.toLowerCase
      values          <- getHeader(nameLower)
      if canForwardHeader(nameLower)
    } yield {
      debug("forwarding header", List(
        "name"  -> nameCapitalized,
        "value" -> (values mkString " ")
        )
      )
      nameCapitalized -> values
    }
  }
}