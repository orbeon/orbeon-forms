package org.orbeon.oxf.xml

import org.orbeon.dom.QName
import org.xml.sax.helpers.AttributesImpl


object SaxSupport {

  implicit class AttributeOps(private val atts: AttributesImpl) extends AnyVal {

    def addOrReplace(qName: QName, value: String): Unit =
      atts.getIndex(qName.namespace.uri, qName.localName) match {
        case -1 =>
          atts.addAttribute(qName.namespace.uri, qName.localName, qName.qualifiedName, XMLReceiverHelper.CDATA, value)
        case index =>
          atts.setAttribute(index, qName.namespace.uri, qName.localName, qName.qualifiedName, XMLReceiverHelper.CDATA, value)
      }

    def addOrReplace(name: String, value: String): Unit =
      atts.getIndex("", name) match {
        case -1 =>
          atts.addAttribute("", name, name, XMLReceiverHelper.CDATA, value)
        case index =>
          atts.setAttribute(index, "", name, name, XMLReceiverHelper.CDATA, value)
      }
  }
}
