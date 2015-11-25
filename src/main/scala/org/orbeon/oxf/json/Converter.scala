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

import org.orbeon.oxf.util.XPath
import org.orbeon.oxf.xml._
import org.orbeon.saxon.om.{DocumentInfo, NodeInfo}
import org.orbeon.scaxon.XML._
import org.xml.sax.helpers.AttributesImpl
import spray.json._

//
// Concrete functions to convert JSON to XML and back following the XForms 2.0 specification.
//
object Converter extends ConverterTrait {

  type XMLElem = NodeInfo

  def localname(elem: XMLElem)                    = elem.localname
  def stringValue(elem: XMLElem)                  = elem.stringValue
  def attValueOpt(elem: XMLElem, attName: String) = elem attValueOpt attName
  def childrenElem(elem: XMLElem)                 = elem / * iterator

  type XMLStream = DeferredXMLReceiver

  def startElem(rcv: XMLStream, name: String)                   = rcv.startElement("", name, name, new AttributesImpl)
  def endElem(rcv: XMLStream, name: String)                     = rcv.endElement("", name, name)
  def addAttribute(rcv: XMLStream, name: String, value: String) = rcv.addAttribute("", name, name, value)
  def text(rcv: XMLStream, value: String)                       = { val a = value.toCharArray; rcv.characters(a, 0, a.length) }

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
    receiver.startDocument()
    jsonToXMLImpl(ast, new DeferredXMLReceiverImpl(receiver))
    receiver.endDocument()
  }

  // Convert an XML tree to a JSON String
  def xmlToJsonString(root: XMLElem, strict: Boolean): String =
    xmlToJson(root, strict).toString

  // Convert an XML tree to a JSON AST
  def xmlToJson(root: XMLElem, strict: Boolean): JsValue =
    xmlToJsonImpl(
      if (isDocument(root))
        root.rootElement
      else if (isElement(root))
        root
      else
        throw new IllegalArgumentException("node must be an element or document"),
      strict
    )
}