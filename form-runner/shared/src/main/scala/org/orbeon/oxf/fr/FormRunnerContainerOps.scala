/**
 * Copyright (C) 2013 Orbeon, Inc.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation; either version
 * 2.1 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.fr

import org.orbeon.oxf.fr.XMLNames.*
import org.orbeon.oxf.util.CoreUtils.*
import org.orbeon.oxf.util.StringUtils.*
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.SimplePath.*
import org.orbeon.xforms.XFormsNames
import org.orbeon.scaxon.Implicits.*



trait FormRunnerContainerOps extends FormRunnerControlOps {

  def isFBBody(node: NodeInfo): Boolean =
    (node self XFGroupTest effectiveBooleanValue) && node.attClasses("fb-body")

  val RepeatContentToken       = "content"
  val LegacyRepeatContentToken = "true"

  // Predicates
  val IsGrid:    NodeInfo => Boolean = _ self FRGridTest    effectiveBooleanValue
  val IsSection: NodeInfo => Boolean = _ self FRSectionTest effectiveBooleanValue

  def isRepeatable(node: NodeInfo): Boolean =
    IsGrid(node) || IsSection(node)

  def isContentRepeat(node: NodeInfo): Boolean =
    isRepeatable(node) && node.attValue(RepeatTest) == RepeatContentToken

  def isLegacyRepeat(node: NodeInfo): Boolean =
    ! isContentRepeat(node) &&
    isRepeatable(node)      && (
      node.attValue(RepeatTest) == LegacyRepeatContentToken ||
      node.att("minOccurs").nonEmpty                        ||
      node.att("maxOccurs").nonEmpty                        ||
      node.att("min").nonEmpty                              ||
      node.att("max").nonEmpty
    )

  def isLegacyUnrepeatedGrid(node: NodeInfo): Boolean =
    IsGrid(node) && ! node.hasAtt(XFormsNames.BIND_QNAME)

  //@XPathFunction
  def isRepeat(node: NodeInfo): Boolean =
    isContentRepeat(node) || isLegacyRepeat(node)

  def hasContainerSettings(node: NodeInfo): Boolean =
    IsSection(node) || IsGrid(node)

  val IsContainer: NodeInfo => Boolean =
    node => (node self FRContainerTest effectiveBooleanValue) || isFBBody(node)

  def controlRequiresNestedIterationElement(node: NodeInfo): Boolean =
    isRepeat(node)

  // Get the name for a section or grid element or null (the empty sequence)
  //@XPathFunction
  def getContainerNameOrEmpty(elem: NodeInfo): String = getControlNameOpt(elem).orNull

  def precedingSiblingOrSelfContainers(container: NodeInfo, includeSelf: Boolean = false): List[NodeInfo] =
    (includeSelf list container) ++ (container precedingSibling * filter IsContainer)

  // Find ancestor sections and grids and root
  def findAncestorContainersLeafToRoot(descendant: NodeInfo, includeSelf: Boolean = false): NodeColl =
    (if (includeSelf) descendant ancestorOrSelf * else descendant ancestor *) filter IsContainer

  //@XPathFunction
  def findAncestorSectionsLeafToRoot(descendant: NodeInfo, includeSelf: Boolean = false): NodeColl =
    findAncestorContainersLeafToRoot(descendant, includeSelf) filter IsSection

  // Find ancestor section and grid names from root to leaf
  // See also:
  // - https://github.com/orbeon/orbeon-forms/issues/2173
  // - https://github.com/orbeon/orbeon-forms/issues/1947
  def findContainerNamesForModel(
    descendant               : NodeInfo,
    includeSelf              : Boolean = false,
    includeIterationElements : Boolean = true,
    includeNonRepeatedGrids  : Boolean = true)(implicit
    ctx                      : FormRunnerDocContext
  ): List[String] = {

    val namesWithContainers =
      for {
        container <- findAncestorContainersLeafToRoot(descendant, includeSelf).toList
        name      <- getControlNameOpt(container)
        if includeNonRepeatedGrids || ! (IsGrid(container) && ! isRepeat(container))
      } yield
        name

    // Repeated sections and grids add an intermediary iteration element
    val namesFromLeaf =
      if (includeIterationElements)
        namesWithContainers flatMap { name =>
          findRepeatIterationName(name).toList ::: name :: Nil
        }
      else
        namesWithContainers

    namesFromLeaf.reverse
  }

  // A container's children containers
  def childrenContainers(container: NodeInfo): NodeColl =
    container / * filter IsContainer

  // A container's children grids (including repeated grids)
  def childrenGrids(container: NodeInfo): NodeColl =
    container / * filter IsGrid

  // Find all ancestor repeats from leaf to root
  def findAncestorRepeats(descendantOrSelf: NodeInfo, includeSelf: Boolean = false): NodeColl =
    findAncestorContainersLeafToRoot(descendantOrSelf, includeSelf) filter isRepeat

  //@XPathFunction
  def findAncestorRepeatNames(descendantOrSelf: NodeInfo, includeSelf: Boolean = false): collection.Seq[String] =
    findAncestorRepeats(descendantOrSelf, includeSelf) flatMap getControlNameOpt

  // Find all ancestor sections from leaf to root
  def findAncestorSections(descendantOrSelf: NodeInfo, includeSelf: Boolean = false): NodeColl =
    findAncestorContainersLeafToRoot(descendantOrSelf, includeSelf) filter IsSection

  def findRepeatIterationName(controlName: String)(implicit ctx: FormRunnerDocContext): Option[String] =
    for {
      control       <- findControlByName(controlName)
      if controlRequiresNestedIterationElement(control)
      bind          <- control attValueOpt XFormsNames.BIND_QNAME flatMap findInBindsTryIndex
      iterationBind <- bind / XFBindTest headOption // there should be only a single nested bind
    } yield
      getBindNameOrEmpty(iterationBind)

  def findNestedControls(containerElem: NodeInfo): NodeColl =
    containerElem descendant (NodeInfoCell.CellTest || NodeInfoCell.TdTest) flatMap findCellNestedControl

  def findCellNestedControl(containerElem: NodeInfo): Option[NodeInfo] =
    containerElem child * find IsControl

  // Return all the controls in the view
  def getAllControlsWithIds(implicit ctx: FormRunnerDocContext): NodeColl =
    ctx.bodyElem descendant * filter
      (e => isIdForControl(e.id))
}
