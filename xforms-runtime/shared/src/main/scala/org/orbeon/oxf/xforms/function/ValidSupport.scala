package org.orbeon.oxf.xforms.function

import org.orbeon.oxf.xforms.model.InstanceData
import org.orbeon.oxf.xml.AttributesAndElementsIterator
import org.orbeon.saxon.om
import org.orbeon.scaxon.SimplePath._


object ValidSupport {

  def isValid(
    items           : Iterator[om.Item],
    pruneNonRelevant: Boolean,
    recurse         : Boolean
  ): Boolean =
    if (recurse)
      items.forall(isTreeValid(_, pruneNonRelevant))
    else
      items.forall(isItemValid(_, pruneNonRelevant))

  // Item is valid unless it is a relevant (unless relevance is ignored) element/attribute and marked as invalid
  def isItemValid(item: om.Item, pruneNonRelevant: Boolean): Boolean = item match {
    case nodeInfo: om.NodeInfo if nodeInfo.isElementOrAttribute =>
      pruneNonRelevant && ! InstanceData.getInheritedRelevant(nodeInfo) || InstanceData.getValid(nodeInfo)
    case _ =>
      true
  }

  // Tree is valid unless one of its descendant-or-self nodes is invalid
  def isTreeValid(item: om.Item, pruneNonRelevant: Boolean): Boolean = item match {
    case nodeInfo: om.NodeInfo if nodeInfo.isElementOrAttribute =>
      AttributesAndElementsIterator(nodeInfo).forall(isItemValid(_, pruneNonRelevant))
    case _ =>
      true
  }
}
