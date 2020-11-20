/**
 *  Copyright (C) 2013 Orbeon, Inc.
 *
 *  This program is free software; you can redistribute it and/or modify it under the terms of the
 *  GNU Lesser General Public License as published by the Free Software Foundation; either version
 *  2.1 of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *  See the GNU Lesser General Public License for more details.
 *
 *  The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.fr.process

import org.orbeon.dom.Document
import org.orbeon.oxf.fr.FormRunner._
import org.orbeon.oxf.fr.process.ProcessParser.{RecoverCombinator, ThenCombinator}
import org.orbeon.oxf.fr.{DataStatus, FormRunnerParams, Names}
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.util.{FunctionContext, IndentedLogger, Logging, XPath}
import org.orbeon.oxf.xforms.action.XFormsAPI
import org.orbeon.oxf.xforms.action.XFormsAPI._
import org.orbeon.oxf.xforms.library.XFormsFunctionLibrary
import org.orbeon.oxf.xforms.model.XFormsInstanceSupport
import org.orbeon.oxf.xforms.processor.XFormsAssetServer
import org.orbeon.saxon.functions.FunctionLibrary
import org.orbeon.saxon.om.Item
import org.orbeon.scaxon.Implicits._
import org.orbeon.scaxon.NodeInfoConversions
import org.orbeon.scaxon.SimplePath._

import scala.collection.mutable.ListBuffer
import scala.util.Try

// Implementation of simple processes
//
// - A process is usually associated with a Form Runner button.
// - A process can have a name which translates into a definition defined in a property.
// - The property specifies a sequence of actions separated by combinators.
// - Actions are predefined, but some of them are configurable.
//
object SimpleProcess extends ProcessInterpreter with FormRunnerActions with XFormsActions with Logging {

  implicit val logger: IndentedLogger = inScopeContainingDocument.getIndentedLogger("process")

  override def extensionActions: Iterable[(String, SimpleProcess.Action)] = AllowedFormRunnerActions ++ AllowedXFormsActions

  def currentXFormsDocumentId: String = XFormsAPI.inScopeContainingDocument.uuid

  // All XPath runs in the context of the main form instance's root element
  def xpathContext: Item = formInstance.rootElement
  def xpathFunctionLibrary: FunctionLibrary = inScopeContainingDocumentOpt map (_.functionLibrary) getOrElse XFormsFunctionLibrary // don't depend on in-scope document for tests
  def xpathFunctionContext: FunctionContext = XPath.functionContext.orNull

  // NOTE: Clear the PDF/TIFF URLs *before* the process, because if we clear it after, it will be already cleared
  // during the second pass of a two-pass submission.
  override def beforeProcess(): Try[Any] = Try {

    val childElems = findUrlsInstanceRootElem.toList child *

    // Remove resource and temporary file if any
    childElems map (_.stringValue) flatMap trimAllToOpt foreach { path =>
      XFormsAssetServer.tryToRemoveDynamicResource(path, removeFile = true)
    }

    // Clear stored paths
    delete(childElems)
  }

  override def processError(t: Throwable): Unit =
    tryErrorMessage(Map(Some("resource") -> "process-error"))

  def writeSuspendedProcess(process: String): Unit =
    setvalue(topLevelInstance(Names.PersistenceModel, "fr-processes-instance").get.rootElement, process)

  def readSuspendedProcess: String =
    topLevelInstance(Names.PersistenceModel, "fr-processes-instance").get.rootElement.stringValue

  case class RollbackContent(data: Document, saveStatus: Option[DataStatus], autoSaveStatus: Option[DataStatus])

  // Store information about what we need to potential rollback
  // Currently, assume we don't need to persist this information between client requests, and assume that
  // the instance is mutable.
  // See https://github.com/orbeon/orbeon-forms/issues/3301
  def transactionStart(): Unit =
    if (isNewOrEditMode(FormRunnerParams().mode))
      inScopeContainingDocument.setTransientState(
        RollbackContent.getClass.getName,
        RollbackContent(
          data           = NodeInfoConversions.getNodeFromNodeInfoConvert(formInstance.root).deepCopy.asInstanceOf[Document], // ugly way to copy
          saveStatus     = DataStatus.withNameInsensitiveOption(persistenceInstance.rootElement elemValue "data-status"),
          autoSaveStatus = DataStatus.withNameInsensitiveOption(persistenceInstance.rootElement / "autosave" / "status" stringValue)
        )
      )

  def transactionRollback(): Unit =
    inScopeContainingDocument.getTransientState[RollbackContent](RollbackContent.getClass.getName) foreach { rollbackContent =>

      // Q: Should we also store `instanceCaching` and `readonly` into `RollbackContent`? As of now,
      // these won't change over time for `fr-form-instance` so we can safely read their latest version
      // on `update`.
      formInstance.update(
        instanceCaching = formInstance.instanceCaching,
        documentInfo    = XFormsInstanceSupport.wrapDocument(rollbackContent.data, formInstance.exposeXPathTypes),
        readonly        = formInstance.readonly
      )

      setvalue(persistenceInstance.rootElement / "data-status",         rollbackContent.saveStatus.map(_.entryName).getOrElse(""))
      setvalue(persistenceInstance.rootElement / "autosave" / "status", rollbackContent.autoSaveStatus.map(_.entryName).getOrElse(""))

      inScopeContainingDocument.removeTransientState(RollbackContent.getClass.getName)
    }

  // Search first in properties, then try legacy workflow-send
  // The scope is interpreted as a property prefix.
  def findProcessByName(scope: String, name: String): Option[String] = {
    implicit val formRunnerParams: FormRunnerParams = FormRunnerParams()
    formRunnerProperty(scope + '.' + name) orElse buildProcessFromLegacyProperties(name)
  }

  // Legacy: build "workflow-send" process based on properties
  private def buildProcessFromLegacyProperties(buttonName: String)(implicit p: FormRunnerParams) = {

    def booleanPropertySet(name: String) = booleanFormRunnerProperty(name)
    def stringPropertySet (name: String) = formRunnerProperty(name) flatMap trimAllToOpt isDefined

    buttonName match {
      case "workflow-send" =>
        val isLegacySendEmail       = booleanPropertySet("oxf.fr.detail.send.email")
        val isLegacyNavigateSuccess = stringPropertySet("oxf.fr.detail.send.success.uri")
        val isLegacyNavigateError   = stringPropertySet("oxf.fr.detail.send.error.uri")

        val buffer = ListBuffer[String]()

        buffer += "require-uploads"
        buffer += ThenCombinator.name
        buffer += "require-valid"
        buffer += ThenCombinator.name
        buffer += "save"
        buffer += ThenCombinator.name
        buffer += """success-message("save-success")"""

        if (isLegacySendEmail) {
          buffer += ThenCombinator.name
          buffer += "email"
        }

        // TODO: Pass `content = "pdf-url"` if isLegacyCreatePDF. Requires better parsing of process arguments.
        //def isLegacyCreatePDF = isLegacyNavigateSuccess && booleanPropertySet("oxf.fr.detail.send.pdf")

        // Workaround is to change config from oxf.fr.detail.send.pdf = true to oxf.fr.detail.send.success.content = "pdf-url"
        if (isLegacyNavigateSuccess) {
          buffer += ThenCombinator.name
          buffer += """send("oxf.fr.detail.send.success")"""
        }

        if (isLegacyNavigateError) {
          buffer += RecoverCombinator.name
          buffer += """send("oxf.fr.detail.send.error")"""
        }

        Some(buffer mkString " ")
      case _ =>
        None
    }
  }
}
