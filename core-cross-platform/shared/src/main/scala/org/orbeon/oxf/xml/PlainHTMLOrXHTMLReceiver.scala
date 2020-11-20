/**
 * Copyright (C) 2015 Orbeon, Inc.
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
package org.orbeon.oxf.xml

import org.orbeon.oxf.xml.XMLConstants.{XHTML_NAMESPACE_URI => HtmlURI}
import org.xml.sax.Attributes
import org.xml.sax.helpers.AttributesImpl


class PlainHTMLOrXHTMLReceiver(targetURI: String, xmlReceiver: XMLReceiver)
    extends ForwardingXMLReceiver(xmlReceiver) {

  var level = 0
  var inXHTMLNamespace = false

  // Consider elements in no namespace to be HTML, see #1981
  def isHTMLElement(uri: String): Boolean = uri == HtmlURI || uri == ""

  override def startElement(uri: String, localname: String, qName: String, attributes: Attributes): Unit = {

    // http://www.w3.org/TR/xslt-xquery-serialization/#xhtml-output: "The serializer SHOULD output
    // namespace declarations in a way that is consistent with the requirements of the XHTML DTD if
    // this is possible". We tried to output the document in the XHTML namespace only if the root
    // element is "{http://www.w3.org/1999/xhtml}html", however the issue then is that in the case
    // of a fragment, the resulting document is not in the XHTML namespace and the XHTML serializer
    // is unable to output elements such as <br />. So we have reverted this change and when the
    // HTML namespace is specified we now always output the document in the XHTML namespace.
    if (level == 0 && targetURI == HtmlURI) {
      inXHTMLNamespace = true
      super.startPrefixMapping("", HtmlURI)
    }

    if (isHTMLElement(uri))
      super.startElement(if (inXHTMLNamespace) targetURI else "", localname, localname, filterAttributes(attributes))

    level += 1
  }

  override def endElement(uri: String, localname: String, qName: String): Unit = {

    level -= 1

    if (isHTMLElement(uri))
      super.endElement(if (inXHTMLNamespace) targetURI else "", localname, localname)

    if (level == 0 && inXHTMLNamespace)
      super.endPrefixMapping("")
  }

  // Swallow all namespace mappings
  override def startPrefixMapping(prefix: String, uri: String): Unit = ()
  override def endPrefixMapping(prefix: String): Unit = ()

  // Only keep attributes in no namespace
  def filterAttributes(attributes: Attributes): Attributes = {

    val length = attributes.getLength

    // Whether there is at least one attribute in a namespace
    def hasNamespace: Boolean = {
      var i = 0
      while (i < length) {
        if (attributes.getURI(i) != "")
          return true

        i += 1
      }
      false
    }

    if (hasNamespace) {
      val newAttributes = new AttributesImpl

      var i = 0
      while (i < length) {
        if (attributes.getURI(i) == "")
          newAttributes.addAttribute(attributes.getURI(i), attributes.getLocalName(i),
            attributes.getQName(i), attributes.getType(i), attributes.getValue(i))

        i += 1
      }

      newAttributes
    } else
      attributes
  }
}