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
import org.orbeon.oxf.util.XPath
import org.orbeon.oxf.xml._
import org.orbeon.saxon.om.{DocumentInfo, NodeInfo}
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
object Converter {

  // Convert a JSON String to a readonly DocumentInfo
  def jsonStringToXML(source: String): DocumentInfo = {
    val (builder, receiver) = TransformerUtils.createTinyBuilder(XPath.GlobalConfiguration)
    jsonStringToXML(source, receiver)
    builder.getCurrentRoot.asInstanceOf[DocumentInfo]
  }

  // Convert a JSON String to a readonly DocumentInfo
  def jsonToXML(ast: JsValue): DocumentInfo = {
    val (builder, receiver) = TransformerUtils.createTinyBuilder(XPath.GlobalConfiguration)
    jsonToXML(ast, receiver)
    builder.getCurrentRoot.asInstanceOf[DocumentInfo]
  }

  // Convert a JSON String to a stream of XML events
  def jsonStringToXML(source: String, receiver: XMLReceiver): Unit =
    jsonToXML(source.parseJson, receiver)

  // Convert a JSON AST to a stream of XML events
  def jsonToXML(ast: JsValue, receiver: XMLReceiver): Unit = {

    import XMLReceiverSupport._

    implicit val rcv = new DeferredXMLReceiverImpl(receiver)

    def escapeString(s: String) =
      s.iterateCodePoints map { cp ⇒
        if (cp <= 0x1F || cp == 0x7F)
          cp + 0xE000
        else
          cp
      } codePointsToString

    def processValue(jsValue: JsValue): Unit =
      jsValue match {
        case JsString(v) ⇒
          rcv.addAttribute(Symbols.Type, Symbols.String)
          text(escapeString(v))
        case JsNumber(v) ⇒
          rcv.addAttribute(Symbols.Type, Symbols.Number)
          text(v.toString)
        case JsBoolean(v) ⇒
          rcv.addAttribute(Symbols.Type, Symbols.Boolean)
          text(v.toString)
        case JsNull ⇒
          rcv.addAttribute(Symbols.Type, Symbols.Null)
        case JsObject(fields) ⇒
          rcv.addAttribute(Symbols.Type, Symbols.Object)
          fields foreach { case (name, value) ⇒

            val ncName  = SaxonUtils.makeNCName(name, keepFirstIfPossible = true)
            val nameAtt = ncName != name list (Symbols.Name → escapeString(name))

            withElement(ncName, nameAtt) {
              processValue(value)
            }
          }
        case JsArray(arrayValues) ⇒
          rcv.addAttribute(Symbols.Type, Symbols.Array)
          arrayValues foreach { arrayValue ⇒
            withElement(Symbols.Anonymous) {
              processValue(arrayValue)
            }
          }
      }

    withDocument {
      withElement(Symbols.JSON) {
        processValue(ast)
      }
    }
  }

  // Convert an XML tree to a JSON String
  def xmlToJsonString(root: NodeInfo, strict: Boolean): String =
    xmlToJson(root, strict).toString

  // Convert an XML tree to a JSON AST
  def xmlToJson(root: NodeInfo, strict: Boolean): JsValue = {

    import org.orbeon.scaxon.XML._

    def unescapeString(s: String) =
      s.iterateCodePoints map { cp ⇒
        if (cp >= 0xE000 && cp <= 0xE01F || cp == 0xE07F)
          cp - 0xE000
        else
          cp
      } codePointsToString

    def typeOpt (elem: NodeInfo) =  elem attValueOpt Symbols.Type
    def elemName(elem: NodeInfo) =  elem attValueOpt Symbols.Name map unescapeString getOrElse elem.localname

    def throwError(s: String) =
      throw new IllegalArgumentException(s)

    def processElement(elem: NodeInfo): JsValue =
      typeOpt(elem) match {
        case Some(Symbols.String) | None ⇒ JsString(unescapeString(elem.stringValue))
        case Some(Symbols.Number)        ⇒ JsNumber(elem.stringValue)
        case Some(Symbols.Boolean)       ⇒ JsBoolean(elem.stringValue.toBoolean)
        case Some(Symbols.Null)          ⇒ JsNull
        case Some(Symbols.Object)        ⇒ JsObject(elem / * map (elem ⇒ elemName(elem) → processElement(elem)) toMap)
        case Some(Symbols.Array)         ⇒ JsArray(elem / * map processElement toVector)
        case Some(other)                 ⇒
          if (strict)
            throwError(s"""unknown datatype `${Symbols.Type}="$other"`""")
          JsNull
      }

    processElement(
      if (isDocument(root))
        root.rootElement
      else if (isElement(root))
        root
      else
        throwError("node must be an element or document")
    )
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