/**
  * Copyright (C) 2017 Orbeon, Inc.
  *
  * This program is free software; you can redistribute it and/or modify it under the terms of the
  * GNU Lesser General Public License as published by the Free Software Foundation; either version
  *  2.1 of the License, or (at your option) any later version.
  *
  * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
  * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
  * See the GNU Lesser General Public License for more details.
  *
  * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
  */
package org.orbeon.scaxon

import org.orbeon.dom
import org.orbeon.dom.saxon.DocumentWrapper
import org.orbeon.oxf.util.StaticXPath
import org.orbeon.saxon.om
import shapeless.syntax.typeable._
import org.orbeon.oxf.util.StaticXPath.{DocumentNodeInfoType, VirtualNodeType}


object NodeInfoConversions {

  def unsafeUnwrapElement(nodeInfo: om.NodeInfo): dom.Element =
    nodeInfo.asInstanceOf[VirtualNodeType].getUnderlyingNode.asInstanceOf[dom.Element]

  def unwrapElement(nodeInfo: om.NodeInfo): Option[dom.Element] =
    nodeInfo.narrowTo[VirtualNodeType] flatMap (_.getUnderlyingNode.cast[dom.Element])

  def unwrapAttribute(nodeInfo: om.NodeInfo): Option[dom.Attribute] =
    nodeInfo.narrowTo[VirtualNodeType] flatMap (_.getUnderlyingNode.cast[dom.Attribute])

  def unwrapNode(nodeInfo: om.NodeInfo): Option[dom.Node] =
    nodeInfo.narrowTo[VirtualNodeType] flatMap (_.getUnderlyingNode.cast[dom.Node])

  def getNodeFromNodeInfoConvert(nodeInfo: om.NodeInfo): dom.Node =
    nodeInfo match {
      case vn: VirtualNodeType => vn.getUnderlyingNode.asInstanceOf[dom.Node]
      case _ =>
        if (nodeInfo.getNodeKind == org.w3c.dom.Node.ATTRIBUTE_NODE)
          dom.Attribute(dom.QName(nodeInfo.getLocalPart, dom.Namespace(nodeInfo.getPrefix, nodeInfo.getURI)), nodeInfo.getStringValue)
        else
          StaticXPath.tinyTreeToOrbeonDom(if (nodeInfo.getParent.isInstanceOf[DocumentNodeInfoType]) nodeInfo.getParent
        else
          nodeInfo
      )
    }

  def extractAsMutableDocument(elementOrDocument: om.NodeInfo): DocumentWrapper =
    new DocumentWrapper(StaticXPath.tinyTreeToOrbeonDom(elementOrDocument), null, StaticXPath.GlobalConfiguration)
}
