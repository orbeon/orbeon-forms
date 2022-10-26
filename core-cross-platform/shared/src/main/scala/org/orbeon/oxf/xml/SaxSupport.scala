package org.orbeon.oxf.xml

import org.orbeon.dom.QName
import org.orbeon.oxf.util.CoreUtils._
import org.xml.sax.Attributes
import org.xml.sax.helpers.AttributesImpl


object SaxSupport {

  implicit class AttributesImplOps(private val atts: AttributesImpl) extends AnyVal {

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

  implicit class AttributesOps(private val atts: Attributes) extends AnyVal {

    // Used by handlers
    // 2022-03-22: Only one use.
    def remove(uri: String, localname: String): AttributesImpl = {
      val newAttributes = new AttributesImpl
      for (i <- 0 until atts.getLength) {
        val attributeURI       = atts.getURI(i)
        val attributeValue     = atts.getValue(i)
        val attributeType      = atts.getType(i)
        val attributeQName     = atts.getQName(i)
        val attributeLocalname = atts.getLocalName(i)
        if (uri != attributeURI || localname != attributeLocalname) // not a matched attribute
          newAttributes.addAttribute(attributeURI, attributeLocalname, attributeQName, attributeType, attributeValue)
      }
      newAttributes
    }
  }

  def newAttributes(qName: QName, value: String): Attributes =
    new AttributesImpl kestrel (_.addOrReplace(qName, value))

  val EmptyAttributes: Attributes = new Attributes {
    def getLength                              : Int    = 0
    def getURI      (i: Int)                   : String = null
    def getLocalName(i: Int)                   : String = null
    def getQName    (i: Int)                   : String = null
    def getType     (i: Int)                   : String = null
    def getValue    (i: Int)                   : String = null
    def getIndex    (s: String, s1: String)    : Int    = -1
    def getIndex    (s: String)                : Int    = -1
    def getType     (s: String, s1: String)    : String = null
    def getType     (s: String)                : String = null
    def getValue    (s: String, s1: String)    : String = null
    def getValue    (s: String)                : String = null
  }
}
