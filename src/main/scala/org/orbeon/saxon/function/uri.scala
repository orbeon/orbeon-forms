/**
 * Copyright (C) 2019 Orbeon, Inc.
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
package org.orbeon.saxon.function

import java.net.URI

import org.orbeon.oxf.util.PathUtils
import org.orbeon.oxf.xml.FunctionSupport
import org.orbeon.saxon.expr.XPathContext
import org.orbeon.saxon.om._
import org.orbeon.saxon.value.{AtomicValue, Int64Value, IntegerValue, StringValue}
import org.orbeon.scaxon.Implicits._

trait UriFunction[T <: AtomicValue] extends FunctionSupport {

  def evaluateUriPart(uri: URI, raw: Option[Boolean])(implicit ctx: XPathContext): T

  override def evaluateItem(context: XPathContext): T = {
    implicit val ctx = context
    evaluateUriPart(URI.create(stringArgument(0)), booleanArgumentOpt(1))
  }
}

// TODO: `xf:location-uri() as xs:anyURI` (depends on runtime context or XFCD)
// TODO: `xf:location-param($name as xs:string) as xs:string*` (depends on runtime context or XFCD)

// xf:uri-scheme($uri as xs:string) as xs:string?
class UriScheme extends UriFunction[StringValue] {
  def evaluateUriPart(uri: URI, raw: Option[Boolean])(implicit ctx: XPathContext): StringValue =
    uri.getScheme
}

// xf:uri-scheme-specific-part($uri as xs:string, $raw as xs:boolean) as xs:string?
class UriSchemeSpecificPart extends UriFunction[StringValue] {
  def evaluateUriPart(uri: URI, raw: Option[Boolean])(implicit ctx: XPathContext): StringValue =
    if (raw.contains(true)) uri.getRawSchemeSpecificPart else uri.getSchemeSpecificPart
}

// xf:uri-authority($uri as xs:string, $raw as xs:boolean) as xs:string?
class UriAuthority extends UriFunction[StringValue] {
  def evaluateUriPart(uri: URI, raw: Option[Boolean])(implicit ctx: XPathContext): StringValue =
    if (raw.contains(true)) uri.getRawAuthority else uri.getAuthority
}

// xf:uri-user-info($uri as xs:string, $raw as xs:boolean) as xs:string?
class UriUserInfo extends UriFunction[StringValue] {
  def evaluateUriPart(uri: URI, raw: Option[Boolean])(implicit ctx: XPathContext): StringValue =
    if (raw.contains(true)) uri.getRawUserInfo else uri.getUserInfo
}

// xf:uri-host($uri as xs:string) as xs:string?
class UriHost extends UriFunction[StringValue] {
  def evaluateUriPart(uri: URI, raw: Option[Boolean])(implicit ctx: XPathContext): StringValue =
    uri.getHost
}

// xf:uri-port($uri as xs:string) as xs:integer?
class UriPort extends UriFunction[IntegerValue] {
  def evaluateUriPart(uri: URI, raw: Option[Boolean])(implicit ctx: XPathContext): IntegerValue =
    new Int64Value(uri.getPort)
}

// xf:uri-path($uri as xs:string, $raw as xs:boolean) as xs:string?
class UriPath extends UriFunction[StringValue] {
  def evaluateUriPart(uri: URI, raw: Option[Boolean])(implicit ctx: XPathContext): StringValue =
    if (raw.contains(true)) uri.getRawPath else uri.getPath
}

// xf:uri-query($uri as xs:string, $raw as xs:boolean) as xs:string?
class UriQuery extends UriFunction[StringValue] {
  def evaluateUriPart(uri: URI, raw: Option[Boolean])(implicit ctx: XPathContext): StringValue =
    if (raw.contains(true)) uri.getRawQuery else uri.getQuery
}

// xf:uri-fragment($uri as xs:string, $raw as xs:boolean) as xs:string?
class UriFragment extends UriFunction[StringValue] {
  def evaluateUriPart(uri: URI, raw: Option[Boolean])(implicit ctx: XPathContext): StringValue =
    if (raw.contains(true)) uri.getRawFragment else uri.getFragment
}

// xf:uri-param-names($uri as xs:string) as xs:string*
class UriParamNames extends FunctionSupport {
  override def iterate(context: XPathContext): SequenceIterator = {
    implicit val ctx = context
    PathUtils.decodeSimpleQuery(URI.create(stringArgument(0)).getRawQuery) map (_._1)
  }
}

// xf:uri-param-values($uri as xs:string, $name as xs:string) as xs:string*
class UriParamValues extends FunctionSupport {
  override def iterate(context: XPathContext): SequenceIterator = {
    implicit val ctx = context
    val paramName = stringArgument(1)
    PathUtils.decodeSimpleQuery(URI.create(stringArgument(0)).getRawQuery) collect { case (`paramName`, value) => value}
  }
}
