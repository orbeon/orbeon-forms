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

import org.orbeon.oxf.fr.FormRunner._
import org.orbeon.oxf.fr.XMLNames._
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.SimplePath._
import org.orbeon.xforms.XFormsNames
import org.orbeon.scaxon.Implicits._

import scala.collection.compat._


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

  // Find the binding's first URI qualified name
  // For now takes the first CSS rule and assume the form foo|bar.
  def bindingFirstURIQualifiedName(bindingElem: NodeInfo): URIQualifiedName = {
    val firstElementCSSName = (bindingElem attValue "element") split "," head
    val elementQName        = firstElementCSSName.replace('|', ':')

    bindingElem.resolveURIQualifiedName(elementQName)
  }

  // Get the name for a section or grid element or null (the empty sequence)
  //@XPathFunction
  def getContainerNameOrEmpty(elem: NodeInfo): String = getControlNameOpt(elem).orNull

  def precedingSiblingOrSelfContainers(container: NodeInfo, includeSelf: Boolean = false): List[NodeInfo] =
    (includeSelf list container) ++ (container precedingSibling * filter IsContainer)

  // Find ancestor sections and grids and root
  def findAncestorContainersLeafToRoot(descendant: NodeInfo, includeSelf: Boolean = false): Seq[NodeInfo] =
    (if (includeSelf) descendant ancestorOrSelf * else descendant ancestor *) filter IsContainer

  //@XPathFunction
  def findAncestorSectionsLeafToRoot(descendant: NodeInfo, includeSelf: Boolean = false): Seq[NodeInfo] =
    findAncestorContainersLeafToRoot(descendant, includeSelf) filter IsSection

  // Find ancestor section and grid names from root to leaf
  // See also:
  // - https://github.com/orbeon/orbeon-forms/issues/2173
  // - https://github.com/orbeon/orbeon-forms/issues/1947
  def findContainerNamesForModel(
    descendant               : NodeInfo,
    includeSelf              : Boolean = false,
    includeIterationElements : Boolean = true,
    includeNonRepeatedGrids  : Boolean = true
  ): List[String] = {

    val namesWithContainers =
      for {
        container <- findAncestorContainersLeafToRoot(descendant, includeSelf).to(List)
        name      <- getControlNameOpt(container)
        if includeNonRepeatedGrids || ! (IsGrid(container) && ! isRepeat(container))
      } yield
        name

    // Repeated sections and grids add an intermediary iteration element
    val namesFromLeaf =
      if (includeIterationElements)
        namesWithContainers flatMap { name =>
          findRepeatIterationName(descendant, name).toList ::: name :: Nil
        }
      else
        namesWithContainers

    namesFromLeaf.reverse
  }

  // A container's children containers
  def childrenContainers(container: NodeInfo): Seq[NodeInfo] =
    container / * filter IsContainer

  // A container's children grids (including repeated grids)
  def childrenGrids(container: NodeInfo): Seq[NodeInfo] =
    container / * filter IsGrid

  // Find all ancestor repeats from leaf to root
  def findAncestorRepeats(descendantOrSelf: NodeInfo, includeSelf: Boolean = false): Seq[NodeInfo] =
    findAncestorContainersLeafToRoot(descendantOrSelf, includeSelf) filter isRepeat

  def findAncestorRepeatNames(descendantOrSelf: NodeInfo, includeSelf: Boolean = false): Seq[String] =
    findAncestorRepeats(descendantOrSelf, includeSelf) flatMap getControlNameOpt

  // Find all ancestor sections from leaf to root
  def findAncestorSections(descendantOrSelf: NodeInfo, includeSelf: Boolean = false): Seq[NodeInfo] =
    findAncestorContainersLeafToRoot(descendantOrSelf, includeSelf) filter IsSection

  //@XPathFunction
  def findRepeatIterationNameOrEmpty(inDoc: NodeInfo, controlName: String): String =
    findRepeatIterationName(inDoc, controlName) getOrElse ""

  def findRepeatIterationName(inDoc: NodeInfo, controlName: String): Option[String] =
    for {
      control       <- findControlByName(inDoc, controlName)
      if controlRequiresNestedIterationElement(control)
      bind          <- control attValueOpt XFormsNames.BIND_QNAME flatMap (findInBindsTryIndex(inDoc, _))
      iterationBind <- bind / XFBindTest headOption // there should be only a single nested bind
    } yield
      getBindNameOrEmpty(iterationBind)

  def findNestedControls(containerElem: NodeInfo): Seq[NodeInfo] =
    containerElem descendant (NodeInfoCell.CellTest || NodeInfoCell.TdTest) flatMap findCellNestedControl

  def findCellNestedControl(containerElem: NodeInfo): Option[NodeInfo] =
    containerElem child * find IsControl

  // Return all the controls in the view
  def getAllControlsWithIds(inDoc: NodeInfo): Seq[NodeInfo] =
    getFormRunnerBodyElem(inDoc) descendant * filter
      (e => isIdForControl(e.id))
}
