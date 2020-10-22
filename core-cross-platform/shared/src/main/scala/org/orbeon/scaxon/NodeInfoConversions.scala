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
import org.orbeon.saxon.om
import shapeless.syntax.typeable._
import org.orbeon.oxf.util.StaticXPath.VirtualNodeType


object NodeInfoConversions {

  def unsafeUnwrapElement(nodeInfo: om.NodeInfo): dom.Element =
    nodeInfo.asInstanceOf[VirtualNodeType].getUnderlyingNode.asInstanceOf[dom.Element]

  def unwrapElement(nodeInfo: om.NodeInfo): Option[dom.Element] =
    nodeInfo.narrowTo[VirtualNodeType] flatMap (_.getUnderlyingNode.cast[dom.Element])

  def unwrapAttribute(nodeInfo: om.NodeInfo): Option[dom.Attribute] =
    nodeInfo.narrowTo[VirtualNodeType] flatMap (_.getUnderlyingNode.cast[dom.Attribute])

  def unwrapNode(nodeInfo: om.NodeInfo): Option[dom.Node] =
    nodeInfo.narrowTo[VirtualNodeType] flatMap (_.getUnderlyingNode.cast[dom.Node])
}
