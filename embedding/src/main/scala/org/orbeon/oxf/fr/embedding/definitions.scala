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

import enumeratum.EnumEntry.Lowercase
import enumeratum._
import org.orbeon.connection.StreamedContent
import org.orbeon.oxf.http.{Headers, HttpClient}
import org.orbeon.oxf.util.CollectionUtils.combineValues
import org.orbeon.oxf.util.PathUtils._
import org.orbeon.xforms.Constants

import java.io.{OutputStream, Writer}
import scala.collection.immutable
import scala.util.Try
import scala.util.matching.Regex


sealed trait FormRunnerMode extends EnumEntry with Lowercase

object FormRunnerMode extends Enum[FormRunnerMode] {

  val values = findValues

  case object New  extends FormRunnerMode
  case object Edit extends FormRunnerMode
  case object View extends FormRunnerMode
}

case class RequestDetails(
  content : Option[StreamedContent],
  url     : String,
  path    : String, // this is the path used to create the URL
  headers : immutable.Seq[(String, String)],
  params  : immutable.Seq[(String, String)]
) {
  def contentType: Option[String] =
    content flatMap (_.contentType)

  def contentTypeHeader: Option[(String, String)] =
    contentType map (Headers.ContentType ->)

  def headersMapWithContentType: Map[String, List[String]] =
    combineValues[String, String, List](headers).toMap ++ (contentType map (Headers.ContentType -> List(_)))
}

trait EmbeddingContext {
  def namespace                                        : String
  def getSessionAttribute(name: String)                : Option[AnyRef]
  def setSessionAttribute(name: String, value: AnyRef) : Unit
  def removeSessionAttribute(name: String)             : Unit
  def httpClient                                       : HttpClient[org.apache.http.client.CookieStore]
  def client                                           : String
}

trait EmbeddingContextWithResponse extends EmbeddingContext{
  def writer                                 : Writer
  def outputStream                           : Try[OutputStream]
  def setHeader(name: String, value: String) : Unit
  def setStatusCode(code: Int)               : Unit
  def decodeURL(encoded: String)             : String
}

private case class EmbeddingSettings(
  formRunnerURL  : String,
  orbeonPrefix   : String,
  resourcesRegex : String,
  httpClient     : HttpClient[org.apache.http.client.CookieStore]
) {
  val OrbeonSubmitPathRegex       : Regex = s"${Regex.quote(orbeonPrefix)}/(?:[^/]+/)?${Constants.XFormsServerSubmit.dropStartingSlash}".r
  val OrbeonResourcePathRegex     : Regex = s"${Regex.quote(orbeonPrefix)}/([^/]+)(/.+)".r
  val FormRunnerResourcePathRegex : Regex = resourcesRegex.r
}