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
import org.orbeon.oxf.util.{IndentedLogger, Logging}
import org.orbeon.oxf.xforms.XFormsConstants.COMPONENT_SEPARATOR
import org.orbeon.oxf.xforms.XFormsProperties
import org.orbeon.oxf.xforms.action.XFormsAPI._
import org.orbeon.oxf.xforms.model.{XFormsInstance, XFormsModel}
import org.orbeon.oxf.xml.TransformerUtils
import org.orbeon.saxon.om.{DocumentInfo, NodeInfo}
import org.orbeon.scaxon.Implicits._
import org.orbeon.scaxon.SimplePath._

import scala.collection.immutable

case class FormBuilderDocContext(rootElem: NodeInfo, formInstance: Option[XFormsInstance]) {

  lazy val componentBindings: Seq[NodeInfo] =
    asScalaSeq(topLevelModel("fr-form-model").get.getVariable("component-bindings")).asInstanceOf[Seq[NodeInfo]]

  lazy val formResourcesRoot: NodeInfo =
    topLevelModel("fr-form-model").get.unsafeGetVariableAsNodeInfo("resources")


  val modelElem        = findModelElem(rootElem)
  val topLevelBindElem = findTopLevelBindFromModelElem(modelElem)
  val bodyElem         = findFRBodyElem(rootElem)

}

object FormBuilderDocContext {

  def apply(inDoc: NodeInfo): FormBuilderDocContext =
    FormBuilderDocContext(inDoc.rootElement, None)

  def apply(formInstance: XFormsInstance): FormBuilderDocContext =
    FormBuilderDocContext(formInstance.rootElement, Some(formInstance ensuring (_ ne null)))

  def apply(): FormBuilderDocContext =
    FormBuilderDocContext(
      topLevelInstance("fr-form-model", "fb-form-instance") getOrElse (throw new IllegalStateException)
    )
}

trait BaseOps extends Logging {

  implicit def logger: IndentedLogger = inScopeContainingDocument.getIndentedLogger("form-builder")

  // Minimal version of IE supported for Form Builder
  // 2017-10-06: Starting Orbeon Forms 2017.2, we don't support IE11 anymore and require Edge.
  //@XPathExpression
  val MinimalIEVersion = 12

  // Id of the xxf:dynamic control holding the edited form
  val DynamicControlId = "fb"

  // Find the form document being edited
  // TODO: remove once `FormBuilderDocContext` is used
  def getFormDoc: DocumentInfo =
    topLevelModel("fr-form-model").get.unsafeGetVariableAsNodeInfo("model").getDocumentRoot

  // All xbl:binding elements available
  // TODO: remove once `FormBuilderDocContext` is used
  def componentBindings: Seq[NodeInfo] =
    asScalaSeq(topLevelModel("fr-form-model").get.getVariable("component-bindings")).asInstanceOf[Seq[NodeInfo]]

  // Return fb-form-instance
  // TODO: remove once `FormBuilderDocContext` is used
  def fbFormInstance: XFormsInstance =
    topLevelInstance("fr-form-model", "fb-form-instance").get

  // Find the top-level form model of the form being edited
  def getFormModel: XFormsModel =
    inScopeContainingDocument.getObjectByEffectiveId(DynamicControlId + COMPONENT_SEPARATOR + "fr-form-model")
      .asInstanceOf[XFormsModel] ensuring (_ ne null, "did not find fb$fr-form-model")

  // TODO: remove once `FormBuilderDocContext` is used
  def formResourcesRoot: NodeInfo =
    topLevelModel("fr-form-model").get.unsafeGetVariableAsNodeInfo("resources")

  def templateRoot(repeatName: String)(implicit ctx: FormBuilderDocContext): Option[NodeInfo] =
    inlineInstanceRootElem(ctx.rootElem, templateId(repeatName))

  // Find the next available id for a given token
  def nextId(token: String)(implicit ctx: FormBuilderDocContext): String =
    nextIds(token, 1).head

  // Find a series of next available ids for a given token
  // Return ids of the form "foo-123-foo", where "foo" is the token
  def nextIds(token: String, count: Int)(implicit ctx: FormBuilderDocContext): immutable.IndexedSeq[String] = {

    val prefix = token + "-"
    val suffix = "-" + token

    def findAllIds = {

      val root = ctx.rootElem.root

      // Use id index when possible, otherwise use plain XPath

      // TODO: also get from xcv instance

      val fbInstance = fbFormInstance

      def elementIdsFromIndex = fbInstance.idsIterator filter (_.endsWith(suffix))
      def elementIdsFromXPath = (root descendant *).ids filter (_.endsWith(suffix)) iterator

      def canUseIndex = fbInstance.documentInfo == root

      val elementIds  = if (canUseIndex) elementIdsFromIndex else elementIdsFromXPath
      val instanceIds = formInstanceRoot(root) descendant * map (_.localname + suffix)

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

  def makeInstanceExpression(name: String): String = "instance('" + name + "')"

  def debugDumpDocumentForGrids(message: String)(implicit ctx: FormBuilderDocContext): Unit =
    if (XFormsProperties.getDebugLogging.contains("form-builder-grid"))
      debugDumpDocument(message)

  def debugDumpDocument(message: String)(implicit ctx: FormBuilderDocContext): Unit =
    debug(message, Seq("doc" → TransformerUtils.tinyTreeToString(ctx.rootElem)))
//    println(Seq(message → TransformerUtils.tinyTreeToString(ctx.rootElem)))

  def insertElementsImposeOrder(into: Seq[NodeInfo], origin: Seq[NodeInfo], order: Seq[String]): Seq[NodeInfo] = {
    val name            = origin.head.localname
    val namesUntil      = (order takeWhile (_ != name)) :+ name toSet
    val elementsBefore  = into child * filter (e ⇒ namesUntil(e.localname))

    insert(into = into, after = elementsBefore, origin = origin)
  }
}
