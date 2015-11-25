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

import org.orbeon.oxf.util.ScalaUtils._
import org.orbeon.oxf.xml.SaxonUtils
import spray.json._

//
// Functions to convert JSON to XML and back following the XForms 2.0 specification.
//
// The conversion follows the following principles:
//
// - Any JSON document is convertible to XML.
// - However, the opposite is not true, and only XML documents following a very specific pattern
//   can be converted to JSON. In other words the purpose of the conversion rules is to expose JSON
//   to XML processing and not the other way around.
// - XPath expressions which apply to the resulting XML document feel as natural as possible in most
//   cases and can be written just by looking at the original JSON.
//
// Remaining tasks:
//
// - `strict = true`: validate more cases
// - `strict = false`: make sure fallbacks are in place and that any XML can be thrown in without error
//
trait ConverterTrait {

  type XMLElem

  def localname(elem: XMLElem): String
  def stringValue(elem: XMLElem): String
  def attValueOpt(elem: XMLElem, attName: String): Option[String]
  def childrenElem(elem: XMLElem): Iterator[XMLElem]

  type XMLStream

  def startElem(rcv: XMLStream, name: String): Unit
  def endElem(rcv: XMLStream, name: String): Unit
  def addAttribute(rcv: XMLStream, name: String, value: String)
  def text(rcv: XMLStream, value: String)

  // Convert a JSON AST to a stream of XML events
  protected def jsonToXMLImpl(ast: JsValue, rcv: XMLStream): Unit = {

    def escapeString(s: String) =
      s.iterateCodePoints map { cp ⇒
        if (cp <= 0x1F || cp == 0x7F)
          cp + 0xE000
        else
          cp
      } codePointsToString

    def withElement[T](localName: String, atts: Seq[(String, String)] = Nil)(body: ⇒ T): T = {
      startElem(rcv, localName)
      atts foreach { case (name, value) ⇒ addAttribute(rcv, name, value) }
      val result = body
      endElem(rcv, localName)
      result
    }

    def processValue(jsValue: JsValue): Unit =
      jsValue match {
        case JsString(v) ⇒
          addAttribute(rcv, Symbols.Type, Symbols.String)
          text(rcv, escapeString(v))
        case JsNumber(v) ⇒
          addAttribute(rcv, Symbols.Type, Symbols.Number)
          text(rcv, v.toString)
        case JsBoolean(v) ⇒
          addAttribute(rcv, Symbols.Type, Symbols.Boolean)
          text(rcv, v.toString)
        case JsNull ⇒
          addAttribute(rcv, Symbols.Type, Symbols.Null)
        case JsObject(fields) ⇒
          addAttribute(rcv, Symbols.Type, Symbols.Object)
          fields foreach { case (name, value) ⇒

            val ncName  = SaxonUtils.makeNCName(name, keepFirstIfPossible = true)
            val nameAtt = ncName != name list (Symbols.Name → escapeString(name))

            withElement(ncName, nameAtt) {
              processValue(value)
            }
          }
        case JsArray(arrayValues) ⇒
          addAttribute(rcv, Symbols.Type, Symbols.Array)
          arrayValues foreach { arrayValue ⇒
            withElement(Symbols.Anonymous) {
              processValue(arrayValue)
            }
          }
      }

    withElement(Symbols.JSON) {
      processValue(ast)
    }
  }

  // Convert an XML tree to a JSON AST
  protected def xmlToJsonImpl(root: XMLElem, strict: Boolean): JsValue = {

    def unescapeString(s: String) =
      s.iterateCodePoints map { cp ⇒
        if (cp >= 0xE000 && cp <= 0xE01F || cp == 0xE07F)
          cp - 0xE000
        else
          cp
      } codePointsToString

    def typeOpt (elem: XMLElem) =  attValueOpt(elem, Symbols.Type)
    def elemName(elem: XMLElem) =  attValueOpt(elem, Symbols.Name) map unescapeString getOrElse localname(elem)

    def throwError(s: String) =
      throw new IllegalArgumentException(s)

    def processElement(elem: XMLElem): JsValue =
      typeOpt(elem) match {
        case Some(Symbols.String) | None ⇒ JsString(unescapeString(stringValue(elem)))
        case Some(Symbols.Number)        ⇒ JsNumber(stringValue(elem))
        case Some(Symbols.Boolean)       ⇒ JsBoolean(stringValue(elem).toBoolean)
        case Some(Symbols.Null)          ⇒ JsNull
        case Some(Symbols.Object)        ⇒ JsObject(childrenElem(elem) map (elem ⇒ elemName(elem) → processElement(elem)) toMap)
        case Some(Symbols.Array)         ⇒ JsArray(childrenElem(elem) map processElement toVector)
        case Some(other)                 ⇒
          if (strict)
            throwError(s"""unknown datatype `${Symbols.Type}="$other"`""")
          JsNull
      }

    processElement(root)
  }

  private object Symbols {
    val String    = "string"
    val Number    = "number"
    val Boolean   = "boolean"
    val Null      = "null"
    val Object    = "object"
    val Array     = "array"

    val Type      = "type"
    val Name      = "name"
    val JSON      = "json"
    val Anonymous = "_"
  }
}