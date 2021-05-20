/**
 * Copyright (c) 2015 Orbeon, Inc. http://orbeon.com
 *
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files (the
 * "Software"), to deal in the Software without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
 * LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
 * OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.orbeon.oxf.json

import org.orbeon.oxf.util.CollectionUtils._
import org.orbeon.oxf.util.StringUtils._
import io.circe.{Json, JsonNumber, parser}

import scala.language.postfixOps
import scala.util.Try

//
// Functions to convert XML to JSON following the XForms 2.0 specification.
//
// Remaining tasks:
//
// - `strict = true`: validate more cases
// - `strict = false`: make sure fallbacks are in place and that any XML can be thrown in without error
//
protected trait XmlToJsonAlgorithm {

  // Abstract members to keep the XML APIs completely separate
  type XmlElem

  def localname   (elem: XmlElem): String
  def stringValue (elem: XmlElem): String
  def attValueOpt (elem: XmlElem, attName: String): Option[String]
  def childrenElem(elem: XmlElem): Iterator[XmlElem]

  // Convert an XML tree to a JSON AST
  def xmlToJsonImpl(root: XmlElem, strict: Boolean): Json = {

    def unescapeString(s: String) =
      s.iterateCodePoints map {
        case cp if cp >= 0xE000 && cp <= 0xE01F || cp == 0xE07F => cp - 0xE000
        case cp                                                 => cp
      } codePointsToString

    def typeOpt (elem: XmlElem) = attValueOpt(elem, Symbols.Type)
    def elemName(elem: XmlElem) = attValueOpt(elem, Symbols.Name) map unescapeString getOrElse localname(elem)

    def jsNullIfBlank(s: String, typ: String, convert: String => Json): Json =
      s.trimAllToOpt map { trimmed =>
        Try(convert(trimmed)) getOrElse {
          if (strict)
            throwError(s"""unable to parse $typ "$trimmed"""")
          else
            Json.Null
        }
      } getOrElse
        Json.Null

    def throwError(s: String) =
      throw new IllegalArgumentException(s)

    def processElement(elem: XmlElem): Json =
      typeOpt(elem) match {
        case Some(Symbols.String) | None => Json.fromString(unescapeString(stringValue(elem)))
        case Some(Symbols.Number)        => jsNullIfBlank(stringValue(elem), "number",  v => Json.fromJsonNumber(JsonNumber.fromString(v).getOrElse(throw new IllegalArgumentException(v))))
        case Some(Symbols.Boolean)       => jsNullIfBlank(stringValue(elem), "boolean", v => Json.fromBoolean(v.toBoolean))
        case Some(Symbols.Null)          => Json.Null
        case Some(Symbols.Object)        => Json.obj(childrenElem(elem).map(elem => elemName(elem) -> processElement(elem)).toVector: _*)
        case Some(Symbols.Array)         => Json.arr(childrenElem(elem).map(processElement).toVector: _*)
        case Some(other)                 =>
          if (strict)
            throwError(s"""unknown datatype `${Symbols.Type}="$other"`""")
          Json.Null
      }

    processElement(root)
  }
}