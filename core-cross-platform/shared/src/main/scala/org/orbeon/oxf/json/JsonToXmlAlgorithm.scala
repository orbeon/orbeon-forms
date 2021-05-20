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

import io.circe.Json
import org.orbeon.oxf.util.CollectionUtils._
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.util.StringUtils._

import scala.language.postfixOps

//
// Functions to convert JSON to XML following the XForms 2.0 specification.
//
protected trait JsonToXmlAlgorithm {

  // Abstract members to keep the XML APIs completely separate
  type XmlStream

  def startElem   (rcv: XmlStream, name: String): Unit
  def endElem     (rcv: XmlStream, name: String): Unit
  def addAttribute(rcv: XmlStream, name: String, value: String): Unit
  def text        (rcv: XmlStream, value: String): Unit

  def makeNCName(name: String): String

  // Convert a JSON AST to a stream of XML events
  def jsonToXmlImpl(ast: Json, rcv: XmlStream, rootElementName: String = Symbols.JSON): Unit = {

    def escapeString(s: String) =
      s.iterateCodePoints map {
        case cp @ (0x09 | 0x0a | 0x0d)      => cp
        case cp if cp <= 0x1F || cp == 0x7F => cp + 0xE000
        case cp                             => cp
      } codePointsToString

    def withElement[T](localName: String, atts: Seq[(String, String)] = Nil)(body: => T): T = {
      startElem(rcv, localName)
      atts foreach { case (name, value) => addAttribute(rcv, name, value) }
      val result = body
      endElem(rcv, localName)
      result
    }

    def processValue(jsValue: Json): Unit =
      jsValue.fold(
        jsonNull    = addAttribute(rcv, Symbols.Type, Symbols.Null),
        jsonBoolean = v => {
          addAttribute(rcv, Symbols.Type, Symbols.Boolean)
          text(rcv, v.toString)
        },
        jsonNumber  = v => {
          addAttribute(rcv, Symbols.Type, Symbols.Number)
          text(rcv, v.toString)
        },
        jsonString  = v => {
          // Don't add `type="string"` since it's the default
          text(rcv, escapeString(v))
        },
        jsonArray   = v => {
          addAttribute(rcv, Symbols.Type, Symbols.Array)
          v foreach { arrayValue =>
            withElement(Symbols.Anonymous) {
              processValue(arrayValue)
            }
          }
        },
        jsonObject  = v => {
          addAttribute(rcv, Symbols.Type, Symbols.Object)
            v.toIterable foreach { case (name, value) =>

              val ncName  = makeNCName(name)
              val nameAtt = ncName != name list (Symbols.Name -> escapeString(name))

              withElement(ncName, nameAtt) {
                processValue(value)
              }
            }
        },
      )

    withElement(rootElementName) {
      processValue(ast)
    }
  }
}