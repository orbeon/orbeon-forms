package org.orbeon.oxf.json

import org.orbeon.saxon.om.NodeInfo


// TODO: Spray JSON is not available for Scala.js so we need a new implementation, for example using Circe.
// We should be able to have a shared implementation.
object Converter {

  type XmlElem = NodeInfo

  def xmlToJsonString(root: XmlElem, strict: Boolean): String = ???
}
