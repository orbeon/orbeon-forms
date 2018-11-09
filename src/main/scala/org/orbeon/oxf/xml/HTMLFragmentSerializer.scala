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

import java.io.Writer

import javax.xml.transform.stream.StreamResult
import org.orbeon.dom.QName
import org.orbeon.oxf.processor.converter.{TextConverterBase, XMLConverter}
import org.orbeon.oxf.properties.Properties
import org.orbeon.oxf.xml.dom4j.Dom4jUtils
import org.xml.sax.Attributes

object HTMLFragmentSerializer {

  def create(writer: Writer, skipRootElement: Boolean) = {

    val identity = TransformerUtils.getIdentityTransformerHandler

    TransformerUtils.applyOutputProperties(
      identity.getTransformer,
      Dom4jUtils.qNameToExplodedQName(
        Properties.instance.getPropertySet(
          QName(
            "html-converter",
            XMLConstants.OXF_PROCESSORS_NAMESPACE
          )
        ).getQName(
          TextConverterBase.DEFAULT_METHOD_PROPERTY_NAME,
          XMLConverter.DEFAULT_METHOD
        )
      ),
      null,
      null,
      null,
      "utf-8",
      true,
      null,
      false,
      0
    )

    identity.setResult(new StreamResult(writer))

    val htmlReceiver = new PlainHTMLOrXHTMLReceiver("", identity)

    if (skipRootElement)
      new SkipRootElement(htmlReceiver)
    else
      htmlReceiver

  }

  class SkipRootElement(receiver: XMLReceiver) extends ForwardingXMLReceiver(receiver) {

    var level = 0

    override def startElement(uri: String, localname: String, qName: String, attributes: Attributes): Unit = {

      if (level > 0)
        super.startElement(uri, localname, qName, attributes)

      level += 1
    }

    override def endElement(uri: String, localname: String, qName: String): Unit = {

      level -= 1

      if (level > 0)
        super.endElement(uri, localname, qName)
    }
  }
}
