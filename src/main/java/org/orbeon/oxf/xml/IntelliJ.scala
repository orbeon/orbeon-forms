package org.orbeon.oxf.xml

import org.orbeon.dom.io.XMLWriter
import org.orbeon.oxf.xml.TransformerUtils.tinyTreeToOrbeonDom
import org.orbeon.saxon.om.NodeInfo

object IntelliJ {

  // Intended to be used in Data Views, so we can more easily view the value of `NodeInfo` while debugging,
  // with the following expression: `org.orbeon.oxf.xml.IntelliJ.tinyTreeToPrettyString(this)`
  def tinyTreeToPrettyString(nodeInfo: NodeInfo): String = {
    tinyTreeToOrbeonDom(nodeInfo).serializeToString(XMLWriter.PrettyFormat)
  }
}
