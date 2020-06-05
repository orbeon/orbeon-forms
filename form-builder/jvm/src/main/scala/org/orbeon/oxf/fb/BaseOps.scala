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
import org.orbeon.oxf.xforms.action.XFormsAPI._
import org.orbeon.oxf.xforms.model.{XFormsInstance, XFormsModel}
import org.orbeon.oxf.xml.TransformerUtils
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.SimplePath._

import scala.collection.immutable

trait BaseOps extends Logging {

  implicit def logger: IndentedLogger = inScopeContainingDocument.getIndentedLogger("form-builder")

  // Id of the xxf:dynamic control holding the edited form
  val DynamicControlId = "fb"

  // Find the top-level form model of the form being edited
  def getFormModel: XFormsModel =
    inScopeContainingDocument.getObjectByEffectiveId(s"$DynamicControlId${COMPONENT_SEPARATOR}fr-form-model")
      .asInstanceOf[XFormsModel] ensuring (_ ne null, "did not find fb$fr-form-model")

  def findTemplateRoot(repeatName: String)(implicit ctx: FormBuilderDocContext): Option[NodeInfo] =
    inlineInstanceRootElem(ctx.formDefinitionRootElem, templateId(repeatName))

  // Find the next available id for a given token
  def nextId(token: String)(implicit ctx: FormBuilderDocContext): String =
    nextIds(token, 1).head

  def nextTmpId()(implicit ctx: FormBuilderDocContext): String =
    nextTmpIds(count = 1).head

  def idsIterator(docWithIdsInstanceOrElem: XFormsInstance Either NodeInfo): Iterator[String] = {

    val formDefinitionRootElem = docWithIdsInstanceOrElem match {
      case Left(instance)  => instance.rootElement
      case Right(formElem) => formElem
    }

    // This is not ideal, but we special-case search in XHTML vs. in an "xcv" document so as to avoid finding
    // ids under `/xh:html/xh:head/xbl:xbl`.
    if (formDefinitionRootElem self "*:html" nonEmpty) {
      val modelOpt = findModelElem(formDefinitionRootElem)
      val bodyOpt  = findFormRunnerBodyElem(formDefinitionRootElem)

      (modelOpt.toList descendantOrSelf *).ids.iterator ++ (bodyOpt.toList descendantOrSelf *).ids.iterator
    } else {
      (formDefinitionRootElem descendantOrSelf *).ids.iterator
    }
  }

  // We search ids looking for `id` attributes in a document, whether via an index or XPath.
  //
  // We used to check element names in a another, optional document as well, typically instance data.
  // But this should not be done:
  //
  // - it is redundant, as ids on binds and controls must identity all data in use
  // - it is incorrect, as section templates insert their data templates and those names
  //   must not be considered as being in use
  //
  // The resulting `Iterator` can contain duplicates.
  //
  // NOTE: We consider that an `-iteration` suffix is not allowed as a control name,
  // and always used only as a suffix of a repeated grid or section name.
  //
  def iterateNamesInUse(docWithIdsInstanceOrElem: XFormsInstance Either NodeInfo): Iterator[String] =
    idsIterator(docWithIdsInstanceOrElem) flatMap
      controlNameFromIdOpt                filterNot
      (_.endsWith(DefaultIterationSuffix))

  // Special id namespace for `tmp-n-tmp` ids. We don't care if those are used in data as element names, or
  // if they are in the clipboard.
  def nextTmpIds(token: String = "tmp", count: Int)(implicit ctx: FormBuilderDocContext): immutable.IndexedSeq[String] = {

    val allIdsIt = idsIterator(ctx.explicitFormDefinitionInstance.toRight(ctx.formDefinitionInstance.get))

    val prefix = token + "-"
    val suffix = "-" + token

    val allTmpIdsInUse =
      collection.mutable.Set() ++ allIdsIt filter (id => id.startsWith(prefix) && id.endsWith(suffix))

    var guess = 1

    def nextId(): String = {

      def buildName(i: Int) = prefix + i + suffix

      while (allTmpIdsInUse(buildName(guess)))
        guess += 1

      val result = buildName(guess)
      allTmpIdsInUse += result
      result
    }

    for (_ <- 1 to count)
      yield nextId()
  }

  // Find a series of next available ids for a given token
  // Return ids of the form "foo-123-foo", where "foo" is the token
  def nextIds(
    token  : String,
    count  : Int,
    others : Iterable[String] = Nil)(implicit
    ctx    : FormBuilderDocContext
  ): immutable.IndexedSeq[String] = {

    val prefix = token + "-"
    val suffix = "-" + token

    val allNamesInUse =
      collection.mutable.Set() ++ others ++
        // Ids coming from the form definition
        iterateNamesInUse(ctx.explicitFormDefinitionInstance.toRight(ctx.formDefinitionInstance.get)) ++ {
        // Ids coming from the special cut/copy/paste instance, if present
        ToolboxOps.readXcvFromClipboard match {
          case Some(xcvNode) => iterateNamesInUse(Right(xcvNode))
          case None          => Nil
        }
      }

    var guess = 1

    def nextId(): String = {

      def buildName(i: Int) = prefix + i

      while (allNamesInUse(buildName(guess)))
        guess += 1

      val result = buildName(guess)
      allNamesInUse += result
      result + suffix
    }

    for (_ <- 1 to count)
      yield nextId()
  }

  def makeInstanceExpression(name: String): String = "instance('" + name + "')"

  def withDebugGridOperation[T](message: String)(body: => T)(implicit ctx: FormBuilderDocContext): T = {
    debugDumpDocument(s"before $message")
    val result = body
    debugDumpDocument(s"after $message")
    result
  }

  def debugDumpDocument(message: String)(implicit ctx: FormBuilderDocContext): Unit =
    debug(message, Seq("doc" -> TransformerUtils.tinyTreeToString(ctx.formDefinitionRootElem)))

  def insertElementsImposeOrder(into: Seq[NodeInfo], origin: Seq[NodeInfo], order: Seq[String]): Seq[NodeInfo] = {
    val name            = origin.head.localname
    val namesUntil      = (order takeWhile (_ != name)) :+ name toSet
    val elementsBefore  = into child * filter (e => namesUntil(e.localname))

    insert(into = into, after = elementsBefore, origin = origin)
  }
}
