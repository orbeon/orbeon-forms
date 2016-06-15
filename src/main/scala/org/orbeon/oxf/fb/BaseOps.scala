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
package org.orbeon.oxf.fb

import org.orbeon.oxf.fr.FormRunner._
import org.orbeon.oxf.fr.XMLNames._
import org.orbeon.oxf.util.{Logging, UserAgent, NetUtils}
import org.orbeon.oxf.xforms.action.XFormsAPI._
import org.orbeon.oxf.xforms.{XFormsProperties, XFormsModel}
import org.orbeon.oxf.xml.TransformerUtils
import org.orbeon.oxf.xml.TransformerUtils._
import org.orbeon.saxon.om.{DocumentInfo, NodeInfo}
import org.orbeon.scaxon.XML._
import org.orbeon.oxf.xforms.XFormsConstants.COMPONENT_SEPARATOR

trait BaseOps extends Logging {

  implicit def logger = inScopeContainingDocument.getIndentedLogger("form-builder")

  // Minimal version of IE supported
  val MinimalIEVersion = 11

  // Id of the xxf:dynamic control holding the edited form
  val DynamicControlId = "fb"

  // Find the form document being edited
  def getFormDoc = asNodeInfo(topLevelModel("fr-form-model").get.getVariable("model")).getDocumentRoot

  // All xbl:binding elements available
  def componentBindings =
    asScalaSeq(topLevelModel("fr-form-model").get.getVariable("component-bindings")).asInstanceOf[Seq[NodeInfo]]

  // All xbl:binding elements available for section templates
  def availableSectionTemplateXBLBindings(componentBindings: Seq[NodeInfo]) =
    componentBindings filter (_.attClasses("fr-section-component"))

  // Find the binding's first URI qualified name
  // For now takes the first CSS rule and assume the form foo|bar.
  def bindingFirstURIQualifiedName(binding: NodeInfo) = {
    val firstElementCSSName = (binding /@ "element" stringValue) split "," head
    val elementQName        = firstElementCSSName.replace('|', ':')

    binding.resolveURIQualifiedName(elementQName)
  }

  def sectionTemplateXBLBindingsByURIQualifiedName(xblElems: Seq[NodeInfo]) = {

    val bindingsForSectionTemplates =
      availableSectionTemplateXBLBindings(xblElems / XBLBindingTest)

    bindingsForSectionTemplates map { binding ⇒
      bindingFirstURIQualifiedName(binding) → extractAsMutableDocument(binding)
    } toMap
  }

  // Return fb-form-instance
  def fbFormInstance = topLevelInstance("fr-form-model", "fb-form-instance").get

  // Find the top-level form model of the form being edited
  def getFormModel = inScopeContainingDocument.getObjectByEffectiveId(DynamicControlId + COMPONENT_SEPARATOR + "fr-form-model").asInstanceOf[XFormsModel] ensuring (_ ne null, "did not find fb$fr-form-model")

  def formResourcesRoot = asNodeInfo(topLevelModel("fr-form-model").get.getVariable("resources"))

  def templateRoot(inDoc: NodeInfo, repeatName: String) =
    inlineInstanceRootElement(inDoc, templateId(repeatName))

  // Find the next available id for a given token
  def nextId(inDoc: NodeInfo, token: String) =
    nextIds(inDoc, token, 1).head

  // Find a series of next available ids for a given token
  // Return ids of the form "foo-123-foo", where "foo" is the token
  def nextIds(inDoc: NodeInfo, token: String, count: Int) = {

    val prefix = token + "-"
    val suffix = "-" + token

    def findAllIds = {
      val root = inDoc.getDocumentRoot

      // Use id index when possible, otherwise use plain XPath
      val fbInstance = fbFormInstance

      def elementIdsFromIndex = fbInstance.idsIterator filter (_.endsWith(suffix))
      def elementIdsFromXPath = root \\ * \@ "id" map (_.stringValue) filter (_.endsWith(suffix)) iterator

      def canUseIndex = fbInstance.documentInfo == root

      val elementIds  = if (canUseIndex) elementIdsFromIndex else elementIdsFromXPath
      val instanceIds = formInstanceRoot(root) \\ * map (_.localname + suffix)

      elementIds ++ instanceIds
    }

    val allIds = collection.mutable.Set() ++ findAllIds
    var guess = allIds.size + 1

    def nextId = {
      def buildId(i: Int) = prefix + i + suffix

      while (allIds(buildId(guess)))
        guess += 1

      val result = buildId(guess)
      allIds += result
      result
    }

    for (_ ← 1 to count)
      yield nextId
  }

  def makeInstanceExpression(name: String) = "instance('" + name + "')"

  // Whether the browser is supported
  // Concretely, we only return false if the browser is an "old" version of IE
  def isBrowserSupported = {
    val request = NetUtils.getExternalContext.getRequest
    ! UserAgent.isUserAgentIE(request) || UserAgent.getMSIEVersion(request) >= MinimalIEVersion
  }

  def debugDumpDocumentForGrids(message: String, inDoc: NodeInfo) =
    if (XFormsProperties.getDebugLogging.contains("form-builder-grid"))
      debugDumpDocument(message, inDoc)

  def debugDumpDocument(message: String, inDoc: NodeInfo) =
    debug(message, Seq("doc" → TransformerUtils.tinyTreeToString(inDoc.getDocumentRoot)))

  def insertElementsImposeOrder(into: Seq[NodeInfo], origin: Seq[NodeInfo], order: Seq[String]): Seq[NodeInfo] = {
    val name            = origin.head.localname
    val namesUntil      = (order takeWhile (_ != name)) :+ name toSet
    val elementsBefore  = into child * filter (e ⇒ namesUntil(e.localname))

    insert(into = into, after = elementsBefore, origin = origin)
  }
}
