package org.orbeon.oxf.xml

import org.xml.sax.Attributes


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