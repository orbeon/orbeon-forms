package org.orbeon.oxf.xml.dom4j

import javax.xml.transform.sax.SAXResult
import org.orbeon.dom.io.DocumentSource
import org.orbeon.oxf.xml.dom.{LocationSAXContentHandler, LocationSAXWriter}


class LocationDocumentResult
  extends SAXResult {

  private val locationSAXContentHandler = new LocationSAXContentHandler
  setHandler(locationSAXContentHandler)

  def getDocument: org.orbeon.dom.Document = locationSAXContentHandler.getDocument
}

class LocationDocumentSource(document: org.orbeon.dom.Document)
  extends DocumentSource(document) {

  setXMLReader(new LocationSAXWriter)
}
