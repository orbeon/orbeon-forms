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

import org.orbeon.scaxon.XML._
import org.orbeon.saxon.om.NodeInfo
import XMLNames._

trait FormRunnerContainerOps extends FormRunnerControlOps {

  def isFBBody(node: NodeInfo) = (node self XFGroupTest) && node.attClasses("fb-body")

  val RepeatContentToken       = "content"
  val LegacyRepeatContentToken = "true"

  // Predicates
  val IsGrid:    NodeInfo ⇒ Boolean = _ self FRGridTest
  val IsSection: NodeInfo ⇒ Boolean = _ self FRSectionTest

  def isRepeatable(node: NodeInfo) =
    IsGrid(node) || IsSection(node)

  def isContentRepeat(node: NodeInfo) =
    isRepeatable(node) && node.attValue("repeat") == RepeatContentToken

  def isLegacyRepeat(node: NodeInfo) =
    ! isContentRepeat(node) &&
    isRepeatable(node)      && (
      node.attValue("repeat") == LegacyRepeatContentToken ||
      node.att("minOccurs").nonEmpty                      ||
      node.att("maxOccurs").nonEmpty                      ||
      node.att("min").nonEmpty                            ||
      node.att("max").nonEmpty
    )

  def isRepeat(node: NodeInfo) =
    isContentRepeat(node) || isLegacyRepeat(node)

  // Non-repeated grid doesn't (yet) have container settings
  def hasContainerSettings(node: NodeInfo) =
    IsSection(node) || isRepeat(node)

  val IsContainer: NodeInfo ⇒ Boolean =
    node ⇒ (node self FRContainerTest) || isFBBody(node)

  def controlRequiresNestedIterationElement(node: NodeInfo) =
    isRepeat(node)

  // Namespace URL a section template component must match
  private val ComponentURI = """^http://orbeon.org/oxf/xml/form-builder/component/([^/]+)/([^/]+)$""".r

  val IsSectionTemplateContent: NodeInfo ⇒ Boolean =
    container ⇒ (container parent * exists IsSection) && ComponentURI.findFirstIn(container.namespaceURI).nonEmpty

  // XForms callers: get the name for a section or grid element or null (the empty sequence)
  def getContainerNameOrEmpty(elem: NodeInfo) = getControlNameOpt(elem).orNull

  // Find ancestor sections and grids (including non-repeated grids) from leaf to root
  def findAncestorContainers(descendant: NodeInfo, includeSelf: Boolean = false) =
    (if (includeSelf) descendant ancestorOrSelf * else descendant ancestor *) filter IsContainer

  // Find ancestor section and grid names from root to leaf
  // Don't return non-repeated fr:grid until an enclosing element is needed. See:
  // - https://github.com/orbeon/orbeon-forms/issues/2173
  // - https://github.com/orbeon/orbeon-forms/issues/1947
  def findContainerNamesForModel(descendant: NodeInfo, includeSelf: Boolean = false): Seq[String] = {

    val namesWithContainers =
      for {
        container ← findAncestorContainers(descendant, includeSelf)
        name      ← getControlNameOpt(container)
        if ! (IsGrid(container) && ! isRepeat(container))
      } yield
        name → container

    // Repeated sections add an intermediary iteration element
    val namesFromLeaf =
      namesWithContainers flatMap {
        case (name, container) ⇒
          findRepeatIterationName(descendant, name).toList ::: name :: Nil
      }

    namesFromLeaf.reverse
  }

  // A container's children containers
  def childrenContainers(container: NodeInfo) =
    container \ * filter IsContainer

  // A container's children grids (including repeated grids)
  def childrenGrids(container: NodeInfo) =
    container \ * filter IsGrid

  // Find all ancestor repeats from leaf to root
  def findAncestorRepeats(descendantOrSelf: NodeInfo, includeSelf: Boolean = false) =
    findAncestorContainers(descendantOrSelf, includeSelf) filter isRepeat

  def findAncestorRepeatNames(descendantOrSelf: NodeInfo, includeSelf: Boolean = false) =
    findAncestorRepeats(descendantOrSelf, includeSelf) flatMap getControlNameOpt

  // Find all ancestor sections from leaf to root
  def findAncestorSections(descendantOrSelf: NodeInfo, includeSelf: Boolean = false) =
    findAncestorContainers(descendantOrSelf, includeSelf) filter IsSection

  //@XPathFunction
  def findRepeatIterationNameOrEmpty(inDoc: NodeInfo, controlName: String) =
    findRepeatIterationName(inDoc, controlName) getOrElse ""

  def findRepeatIterationName(inDoc: NodeInfo, controlName: String): Option[String] =
    for {
      control       ← findControlByName(inDoc, controlName)
      if controlRequiresNestedIterationElement(control)
      bind          ← control attValueOpt "bind" map controlNameFromId flatMap (findBindByName(inDoc, _))
      iterationBind ← bind / XFBindTest headOption // there should be only a single nested bind
    } yield
      getBindNameOrEmpty(iterationBind)
}
