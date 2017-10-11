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

import org.orbeon.dom.saxon.DocumentWrapper
import org.orbeon.oxf.fr.XMLNames._
import org.orbeon.oxf.util.CollectionUtils.collectByErasedType
import org.orbeon.oxf.xforms.control.XFormsComponentControl
import org.orbeon.oxf.xml.TransformerUtils._
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.SimplePath._

trait FormRunnerContainerOps extends FormRunnerControlOps {

  import Private._

  def isFBBody(node: NodeInfo): Boolean =
    (node self XFGroupTest effectiveBooleanValue) && node.attClasses("fb-body")

  val RepeatContentToken       = "content"
  val LegacyRepeatContentToken = "true"

  // Predicates
  val IsGrid:    NodeInfo ⇒ Boolean = _ self FRGridTest    effectiveBooleanValue
  val IsSection: NodeInfo ⇒ Boolean = _ self FRSectionTest effectiveBooleanValue

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

  //@XPathFunction
  def isRepeat(node: NodeInfo) =
    isContentRepeat(node) || isLegacyRepeat(node)

  // Non-repeated grid doesn't (yet) have container settings
  def hasContainerSettings(node: NodeInfo) =
    IsSection(node) || isRepeat(node)

  val IsContainer: NodeInfo ⇒ Boolean =
    node ⇒ (node self FRContainerTest effectiveBooleanValue) || isFBBody(node)

  def controlRequiresNestedIterationElement(node: NodeInfo) =
    isRepeat(node)

  def isSectionTemplateContent(container: NodeInfo) =
    (container parent * exists IsSection) && ComponentURI.findFirstIn(container.namespaceURI).nonEmpty

  def sectionTemplateBindingName(section: NodeInfo): Option[URIQualifiedName] =
    section / * filter isSectionTemplateContent map (_.uriQualifiedName) headOption

  def findSectionsWithTemplates(view: NodeInfo) =
    view descendant * filter IsSection filter (_ / * exists isSectionTemplateContent)

  // Find the binding's first URI qualified name
  // For now takes the first CSS rule and assume the form foo|bar.
  def bindingFirstURIQualifiedName(binding: NodeInfo): URIQualifiedName = {
    val firstElementCSSName = (binding /@ "element" stringValue) split "," head
    val elementQName        = firstElementCSSName.replace('|', ':')

    binding.resolveURIQualifiedName(elementQName)
  }

  def sectionTemplateXBLBindingsByURIQualifiedName(xblElems: Seq[NodeInfo]): Map[URIQualifiedName, DocumentWrapper] = {

    // All xbl:binding elements available for section templates
    def availableSectionTemplateXBLBindings(componentBindings: Seq[NodeInfo]) =
      componentBindings filter (_.attClasses("fr-section-component"))

    val bindingsForSectionTemplates =
      availableSectionTemplateXBLBindings(xblElems / XBLBindingTest)

    bindingsForSectionTemplates map { binding ⇒
      bindingFirstURIQualifiedName(binding) → extractAsMutableDocument(binding)
    } toMap
  }

  // Get the name for a section or grid element or null (the empty sequence)
  //@XPathFunction
  def getContainerNameOrEmpty(elem: NodeInfo) = getControlNameOpt(elem).orNull

  // Find ancestor sections and grids (including non-repeated grids)
  def findAncestorContainersLeafToRoot(descendant: NodeInfo, includeSelf: Boolean = false): Seq[NodeInfo] =
    (if (includeSelf) descendant ancestorOrSelf * else descendant ancestor *) filter IsContainer

  // Find ancestor section and grid names from root to leaf
  // Don't return non-repeated fr:grid until an enclosing element is needed. See:
  // - https://github.com/orbeon/orbeon-forms/issues/2173
  // - https://github.com/orbeon/orbeon-forms/issues/1947
  def findContainerNamesForModel(descendant: NodeInfo, includeSelf: Boolean = false): Seq[String] = {

    val namesWithContainers =
      for {
        container ← findAncestorContainersLeafToRoot(descendant, includeSelf)
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
  def childrenContainers(container: NodeInfo): Seq[NodeInfo] =
    container / * filter IsContainer

  // A container's children grids (including repeated grids)
  def childrenGrids(container: NodeInfo): Seq[NodeInfo] =
    container / * filter IsGrid

  // Find all ancestor repeats from leaf to root
  def findAncestorRepeats(descendantOrSelf: NodeInfo, includeSelf: Boolean = false) =
    findAncestorContainersLeafToRoot(descendantOrSelf, includeSelf) filter isRepeat

  def findAncestorRepeatNames(descendantOrSelf: NodeInfo, includeSelf: Boolean = false) =
    findAncestorRepeats(descendantOrSelf, includeSelf) flatMap getControlNameOpt

  // Find all ancestor sections from leaf to root
  def findAncestorSections(descendantOrSelf: NodeInfo, includeSelf: Boolean = false) =
    findAncestorContainersLeafToRoot(descendantOrSelf, includeSelf) filter IsSection

  //@XPathFunction
  def findRepeatIterationNameOrEmpty(inDoc: NodeInfo, controlName: String) =
    findRepeatIterationName(inDoc, controlName) getOrElse ""

  def findRepeatIterationName(inDoc: NodeInfo, controlName: String): Option[String] =
    for {
      control       ← findControlByName(inDoc, controlName)
      if controlRequiresNestedIterationElement(control)
      bind          ← control attValueOpt "bind" flatMap (findInBindsTryIndex(inDoc, _))
      iterationBind ← bind / XFBindTest headOption // there should be only a single nested bind
    } yield
      getBindNameOrEmpty(iterationBind)

  def sectionTemplateForSection(frSectionComponent: XFormsComponentControl): Option[XFormsComponentControl] = {

    def matchesComponentURI(uri: String) =
      ComponentNS.findFirstIn(uri).isDefined

    // Find the concrete section template component (component:foo)
    // A bit tricky because there might not be an id on the component element:
    // <component:eid xmlns:component="http://orbeon.org/oxf/xml/form-builder/component/orbeon/library"/>
    val sectionTemplateElementOpt =
      frSectionComponent.staticControl.descendants find
      (c ⇒ matchesComponentURI(c.element.getNamespaceURI))

    sectionTemplateElementOpt flatMap
      (e ⇒ frSectionComponent.resolve(e.staticId)) flatMap
      collectByErasedType[XFormsComponentControl]
  }

  private object Private {

    val ComponentNS = """http://orbeon.org/oxf/xml/form-builder/component/([^/]+)/library""".r

    // Namespace URL a section template component must match
    val ComponentURI = """^http://orbeon.org/oxf/xml/form-builder/component/([^/]+)/([^/]+)$""".r
  }
}
