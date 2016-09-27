/**
 * Copyright (C) 2014 Orbeon, Inc.
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
package org.orbeon.oxf.fr.embedding

import java.io.{OutputStream, Writer}

import org.orbeon.oxf.http.{Headers, HttpClient, StreamedContent}
import org.orbeon.oxf.util.CollectionUtils.combineValues

import scala.collection.immutable

sealed trait  Mode              { val name: String }
case   object New  extends Mode { val name = "new" }
case   object Edit extends Mode { val name = "edit" }
case   object View extends Mode { val name = "view" }

case class RequestDetails(
  content : Option[StreamedContent],
  url     : String,
  headers : immutable.Seq[(String, String)],
  params  : immutable.Seq[(String, String)]
) {
  def contentType =
    content flatMap (_.contentType)

  def contentTypeHeader =
    contentType map (Headers.ContentType →)

  def headersMapWithContentType =
    combineValues[String, String, List](headers).toMap ++ (contentType map (Headers.ContentType → List(_)))
}

trait EmbeddingContext {
  def namespace                                        : String
  def getSessionAttribute(name: String)                : AnyRef
  def setSessionAttribute(name: String, value: AnyRef) : Unit
  def removeSessionAttribute(name: String)             : Unit
  def httpClient                                       : HttpClient
}

trait EmbeddingContextWithResponse extends EmbeddingContext{
  def writer                                 : Writer
  def outputStream                           : OutputStream
  def setHeader(name: String, value: String) : Unit
  def setStatusCode(code: Int)               : Unit
  def decodeURL(encoded: String)             : String
}

private case class EmbeddingSettings(
  formRunnerURL : String,
  orbeonPrefix  : String,
  httpClient    : HttpClient
) {
  val OrbeonResourceRegex = s"$orbeonPrefix/([^/]+)(/.+)".r
}