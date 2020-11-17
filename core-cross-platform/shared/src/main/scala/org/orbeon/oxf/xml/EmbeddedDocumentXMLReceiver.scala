package org.orbeon.oxf.xml

/**
 * Forwards all the SAX events to a content handler, except for startDocument and endDocument.
 */
class EmbeddedDocumentXMLReceiver(val xmlReceiver: XMLReceiver) extends SimpleForwardingXMLReceiver(xmlReceiver) {
  override def startDocument(): Unit = ()
  override def endDocument(): Unit = ()
}