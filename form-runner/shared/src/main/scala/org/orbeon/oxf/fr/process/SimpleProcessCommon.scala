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

import cats.effect.IO
import org.orbeon.dom.Document
import org.orbeon.oxf.externalcontext.ExternalContext
import org.orbeon.oxf.fr.FormRunner.*
import org.orbeon.oxf.fr.process.ProcessInterpreter.Action
import org.orbeon.oxf.fr.process.ProcessParser.Combinator
import org.orbeon.oxf.fr.{DataStatus, FormRunnerParams, Names}
import org.orbeon.oxf.logging.LifecycleLogger
import org.orbeon.oxf.util.StringUtils.*
import org.orbeon.oxf.util.{CoreCrossPlatformSupport, FunctionContext, IndentedLogger, LoggerFactory, XPath}
import org.orbeon.oxf.xforms.XFormsContainingDocument
import org.orbeon.oxf.xforms.action.XFormsAPI
import org.orbeon.oxf.xforms.action.XFormsAPI.*
import org.orbeon.oxf.xforms.function.XFormsFunction
import org.orbeon.oxf.xforms.library.XFormsFunctionLibrary
import org.orbeon.oxf.xforms.model.XFormsInstanceSupport
import org.orbeon.saxon.functions.FunctionLibrary
import org.orbeon.saxon.om.Item
import org.orbeon.scaxon.Implicits.*
import org.orbeon.scaxon.NodeInfoConversions
import org.orbeon.scaxon.SimplePath.*

import scala.collection.mutable.ListBuffer
import scala.concurrent.Future
import scala.concurrent.duration.Duration
import scala.util.{Failure, Success, Try}


// Implementation of simple processes
//
// - A process is usually associated with a Form Runner button.
// - A process can have a name which translates into a definition defined in a property.
// - The property specifies a sequence of actions separated by combinators.
// - Actions are predefined, but some of them are configurable.
//
trait SimpleProcessCommon
  extends ProcessInterpreter
     with FormRunnerActionsCommon
     with XFormsActions {

  def AllowedFormRunnerActions: Map[String, Action]

  // Don't store the logger as a `val` or `lazy val`!
  // https://github.com/orbeon/orbeon-forms/issues/179
  implicit def logger: IndentedLogger = inScopeContainingDocumentOpt.map(_.getIndentedLogger("process")).getOrElse {
    // don't depend on in-scope document for tests
    new IndentedLogger(LoggerFactory.createLogger(classOf[SimpleProcessCommon]), true)
  }

  override def extensionActions: Iterable[(String, ProcessInterpreter.Action)] =
    AllowedFormRunnerActions ++ AllowedXFormsActions

  // All XPath runs in the context of the main form instance's root element
  def xpathContext: Item = formInstance.rootElement

  def xpathFunctionLibrary: FunctionLibrary =
    inScopeContainingDocumentOpt map (_.functionLibrary) getOrElse XFormsFunctionLibrary // don't depend on in-scope document for tests

  // Use the in-scope context if present. This is the case if the process is called from XPath. If not, use a top-level
  // context. This is the case if the process is called from an async continuation. It is a unclear whether any
  // processes make use, or should make use of a context other than the top-level context.
  // https://github.com/orbeon/orbeon-forms/issues/6148
  // TODO: How should these examples work? What context should be available for example to the `bind()` function?
  // - inside XBL: xxf:run-process('...', 'save then xf:setvalue(ref = "//my-value", value = "bind('bar')")')
  // - inside XBL: xxf:run-process-by-name('...', 'foobar')
  def xpathFunctionContext: FunctionContext =
    XPath.functionContext.orElse(
      inScopeContainingDocumentOpt.map { doc =>
        XFormsFunction.Context(
          container         = doc,
          bindingContext    = doc.getDefaultModel.getDefaultEvaluationContext,
          sourceEffectiveId = doc.effectiveId,
          modelOpt          = doc.findDefaultModel,
          bindNodeOpt       = None
        )
      }
    ).orNull // not ideal, but at least one test runs in a scope without an in-scope containing document

  override def processError(t: Throwable): Unit =
    tryErrorMessage(Map(Some("resource") -> "process-error"))

  def writeSuspendedProcess(processId: String, process: String): Unit =
    setvalue(topLevelInstance(Names.PersistenceModel, "fr-processes-instance").get.rootElement, List(processId, process).mkString("|"))

  def readSuspendedProcess: Try[(String, String)] =
    topLevelInstance(Names.PersistenceModel, "fr-processes-instance").get.rootElement.stringValue.splitTo[List]("|") match {
      case processId :: continuation :: Nil =>
        Success((processId, continuation))
      case _ =>
        Failure(new IllegalStateException("Invalid or missing suspended process"))
    }

  def clearSuspendedProcess(): Unit =
    setvalue(topLevelInstance(Names.PersistenceModel, "fr-processes-instance").get.rootElement, "")

  def submitContinuation[T, U](
    message     : String,
    computation : IO[T],
    continuation: (XFormsContainingDocument, Try[T]) => Either[Try[U], Future[U]]
  ): Future[U] =
    inScopeContainingDocument
      .getAsynchronousSubmissionManager
      .addAsynchronousCompletion(
        description           = message,
        computation           = computation,
        continuation          = continuation,
        awaitInCurrentRequest = Some(Duration.Inf)
      )

  def currentXFormsDocumentId: String = XFormsAPI.inScopeContainingDocument.uuid

  private case class RollbackContent(data: Document, saveStatus: Option[DataStatus], autoSaveStatus: Option[DataStatus])

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
          autoSaveStatus = DataStatus.withNameInsensitiveOption((persistenceInstance.rootElement / "autosave" / "status").stringValue)
        )
      )

  // Only called from `rollback` action
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

  def withRunProcess[T](scope: String, name: String)(body: => T): T = {
    implicit val ec: ExternalContext = CoreCrossPlatformSupport.externalContext
    LifecycleLogger.withEventAssumingRequest("fr", "process", List("uuid" -> currentXFormsDocumentId, "scope" -> scope, "name" -> name)) {
      body
    }
  }

  // Legacy: build "workflow-send" process based on properties
  private def buildProcessFromLegacyProperties(buttonName: String)(implicit p: FormRunnerParams) = {

    def booleanPropertySet(name: String) = booleanFormRunnerProperty(name)
    def stringPropertySet (name: String) = formRunnerProperty(name).flatMap(trimAllToOpt).isDefined

    buttonName match {
      case "workflow-send" =>
        val isLegacySendEmail       = booleanPropertySet("oxf.fr.detail.send.email")
        val isLegacyNavigateSuccess = stringPropertySet("oxf.fr.detail.send.success.uri")
        val isLegacyNavigateError   = stringPropertySet("oxf.fr.detail.send.error.uri")

        val buffer = ListBuffer[String]()

        buffer += "require-uploads"
        buffer += Combinator.Then.name
        buffer += "require-valid"
        buffer += Combinator.Then.name
        buffer += "save"
        buffer += Combinator.Then.name
        buffer += """success-message("save-success")"""

        if (isLegacySendEmail) {
          buffer += Combinator.Then.name
          buffer += "email"
        }

        // TODO: Pass `content = "pdf-url"` if isLegacyCreatePDF. Requires better parsing of process arguments.
        //def isLegacyCreatePDF = isLegacyNavigateSuccess && booleanPropertySet("oxf.fr.detail.send.pdf")

        // Workaround is to change config from oxf.fr.detail.send.pdf = true to oxf.fr.detail.send.success.content = "pdf-url"
        if (isLegacyNavigateSuccess) {
          buffer += Combinator.Then.name
          buffer += """send("oxf.fr.detail.send.success")"""
        }

        if (isLegacyNavigateError) {
          buffer += Combinator.Recover.name
          buffer += """send("oxf.fr.detail.send.error")"""
        }

        Some(buffer mkString " ")
      case _ =>
        None
    }
  }
}
