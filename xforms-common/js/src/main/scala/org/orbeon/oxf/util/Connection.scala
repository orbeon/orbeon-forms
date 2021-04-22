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
import org.orbeon.oxf.externalcontext.ExternalContext
import org.orbeon.oxf.http.{BasicCredentials, HttpMethod, StreamedContent}


object Connection extends ConnectionTrait {

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
  ): ConnectionResult = ???

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
  ): Eval[ConnectionResult] = ???

  def isInternalPath(path: String): Boolean = false

  def findInternalUrl(
    normalizedUrl : URI,
    filter        : String => Boolean)(implicit
    ec            : ExternalContext
  ): Option[String] = None

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
  ): Map[String, List[String]] = customHeaders

  def headersToForwardFromProperty: Set[String]  = Set.empty
  def cookiesToForwardFromProperty: List[String] = Nil

  def sessionCookieHeaderCapitalized(
    externalContext  : ExternalContext,
    cookiesToForward : List[String])(implicit
    logger           : IndentedLogger
  ): Option[(String, List[String])] = None
}
