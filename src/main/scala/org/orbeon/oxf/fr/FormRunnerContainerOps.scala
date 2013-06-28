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

trait FormRunnerContainerOps extends FormRunnerControlOps {

    // Node tests
    private val GridElementTest     : Test = FR → "grid"
    private val SectionElementTest  : Test = FR → "section"
    private val GroupElementTest    : Test = XF → "group"
    private val ContainerElementTest       = SectionElementTest || GridElementTest

    def isFBBody(node: NodeInfo) = (node self GroupElementTest) && node.attClasses("fb-body")

    // Predicates
    val IsGrid: NodeInfo ⇒ Boolean = _ self GridElementTest
    val IsSection: NodeInfo ⇒ Boolean = _ self SectionElementTest
    val IsRepeat: NodeInfo ⇒ Boolean = node ⇒ IsGrid(node) && node.attValue("repeat") == "true"

    def isRepeat(node: NodeInfo) = IsRepeat(node) // for Java callers

    val IsContainer: NodeInfo ⇒ Boolean =
        node ⇒ (node self ContainerElementTest) || isFBBody(node)

    // Namespace URL a section template component must match
    private val ComponentURI = """^http://orbeon.org/oxf/xml/form-builder/component/([^/]+)/([^/]+)$""".r

    val IsSectionTemplateContent: NodeInfo ⇒ Boolean =
        container ⇒ (container parent * exists IsSection) && ComponentURI.findFirstIn(namespaceURI(container)).nonEmpty

    // XForms callers: get the name for a section or grid element or null (the empty sequence)
    def getContainerNameOrEmpty(elem: NodeInfo) = getControlNameOption(elem).orNull

    // Find ancestor sections and grids (including non-repeated grids) from leaf to root
    def findAncestorContainers(descendant: NodeInfo, includeSelf: Boolean = false) =
        (if (includeSelf) descendant ancestorOrSelf * else descendant ancestor *) filter IsContainer

    // Find ancestor section and grid names from root to leaf
    def findContainerNames(descendant: NodeInfo): Seq[String] =
        findAncestorContainers(descendant).reverse map (getControlNameOption(_)) flatten

    // A container's children containers
    def childrenContainers(container: NodeInfo) =
        container \ * filter IsContainer

    // A container's children grids (including repeated grids)
    def childrenGrids(container: NodeInfo) =
        container \ * filter IsGrid
}
