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

import org.orbeon.oxf.util.StaticXPath
import org.orbeon.oxf.xml._
import org.orbeon.saxon.om
import org.orbeon.scaxon.SimplePath._
import org.xml.sax.helpers.AttributesImpl
import spray.json._

import scala.language.postfixOps
import org.orbeon.oxf.xml.SaxonUtils


//
// Concrete functions to convert JSON to XML and back following the XForms 2.0 specification.
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
object Converter extends XmlToJsonAlgorithm with JsonToXmlAlgorithm {

  type XmlElem = om.NodeInfo

  def localname(elem: XmlElem)                    = elem.localname
  def stringValue(elem: XmlElem)                  = elem.stringValue
  def attValueOpt(elem: XmlElem, attName: String) = elem attValueOpt attName
  def childrenElem(elem: XmlElem)                 = elem / * iterator

  type XmlStream = DeferredXMLReceiver

  def startElem(rcv: XmlStream, name: String)                   = rcv.startElement("", name, name, new AttributesImpl)
  def endElem(rcv: XmlStream, name: String)                     = rcv.endElement("", name, name)
  def addAttribute(rcv: XmlStream, name: String, value: String) = rcv.addAttribute("", name, name, value)
  def text(rcv: XmlStream, value: String)                       = { val a = value.toCharArray; rcv.characters(a, 0, a.length) }

  def makeNCName(name: String): String                          = SaxonUtils.makeNCName(name, keepFirstIfPossible = true)

  // Convert a JSON String to a readonly DocumentInfo
  def jsonStringToXmlDoc(source: String, rootElementName: String = Symbols.JSON): StaticXPath.DocumentNodeInfoType = {
    val (receiver, result) = StaticXPath.newTinyTreeReceiver
    jsonStringToXmlStream(source, receiver, rootElementName)
    result()
  }

  // Convert a JSON String to a readonly DocumentInfo
  def jsonToXmlDoc(ast: JsValue): StaticXPath.DocumentNodeInfoType = {
    val (receiver, result) = StaticXPath.newTinyTreeReceiver
    jsonToXmlStream(ast, receiver)
    result()
  }

  // Convert a JSON String to a stream of XML events
  def jsonStringToXmlStream(source: String, receiver: XMLReceiver, rootElementName: String = Symbols.JSON): Unit =
    jsonToXmlStream(source.parseJson, receiver, rootElementName)

  // Convert a JSON AST to a stream of XML events
  def jsonToXmlStream(ast: JsValue, receiver: XMLReceiver, rootElementName: String = Symbols.JSON): Unit = {
    receiver.startDocument()
    jsonToXmlImpl(ast, new DeferredXMLReceiverImpl(receiver), rootElementName)
    receiver.endDocument()
  }

  // Convert an XML tree to a JSON String
  def xmlToJsonString(root: XmlElem, strict: Boolean): String =
    xmlToJson(root, strict).toString

  // Convert an XML tree to a JSON AST
  def xmlToJson(root: XmlElem, strict: Boolean): JsValue =
    xmlToJsonImpl(
      if (root.isDocument)
        root.rootElement
      else if (root.isElement)
        root
      else
        throw new IllegalArgumentException("node must be an element or document"),
      strict
    )
}