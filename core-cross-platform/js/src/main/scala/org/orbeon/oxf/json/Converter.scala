package org.orbeon.oxf.json

import org.orbeon.oxf.util.StaticXPath
import org.orbeon.oxf.xml.XMLReceiver
import org.orbeon.saxon.om.NodeInfo


// TODO: Spray JSON is not available for Scala.js so we need a new implementation, for example using Circe.
// We should be able to have a shared implementation.
object Converter {

  type XmlElem = NodeInfo

  def xmlToJsonString(root: XmlElem, strict: Boolean): String = throw new NotImplementedError("xmlToJsonString")
  def jsonStringToXmlDoc(source: String, rootElementName: String = Symbols.JSON): StaticXPath.DocumentNodeInfoType = throw new NotImplementedError("jsonStringToXmlDoc")
  def jsonStringToXmlStream(source: String, receiver: XMLReceiver, rootElementName: String = Symbols.JSON): Unit = throw new NotImplementedError("jsonStringToXmlStream")
}
