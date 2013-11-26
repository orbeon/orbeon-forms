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
import org.orbeon.oxf.util.ScalaUtils._

trait FormRunnerContainerOps extends FormRunnerControlOps {

    // Node tests
    val GridElementTest     : Test = FR → "grid"
    val SectionElementTest  : Test = FR → "section"
    val GroupElementTest    : Test = XF → "group"
    val ContainerElementTest       = SectionElementTest || GridElementTest

    def isFBBody(node: NodeInfo) = (node self GroupElementTest) && node.attClasses("fb-body")

    // Predicates
    val IsGrid:    NodeInfo ⇒ Boolean = _ self GridElementTest
    val IsSection: NodeInfo ⇒ Boolean = _ self SectionElementTest
    val IsRepeat:  NodeInfo ⇒ Boolean = node ⇒ (IsGrid(node) || IsSection(node)) && node.attValue("repeat") == "true"

    def isRepeat(node: NodeInfo) = IsRepeat(node) // for Java/XSLT callers

    val IsContainer: NodeInfo ⇒ Boolean =
        node ⇒ (node self ContainerElementTest) || isFBBody(node)

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
    def findContainerNames(descendant: NodeInfo, includeSelf: Boolean = false, fromParent: Option[String] = None): Seq[String] = {
        
        val allNamesWithContainers =
            for {
                container ← findAncestorContainers(descendant, includeSelf)
                name      ← getControlNameOpt(container)
            } yield
                name → container
        
        val namesWithContainers =
            fromParent match {
                case Some(parentName) ⇒
                    val matches = Set(parentName, iterationName(parentName))
                    allNamesWithContainers takeWhile {
                        case (name, _) ⇒ ! matches(name)
                    }
                case None ⇒
                    allNamesWithContainers
            }

        // Repeated sections add an intermediary iteration element
        val namesFromLeaf =
            namesWithContainers flatMap {
                case (name, container) ⇒
                    (IsRepeat(container) && IsSection(container) list iterationName(name)) ::: name :: Nil
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
        findAncestorContainers(descendantOrSelf, includeSelf) filter IsRepeat
}
