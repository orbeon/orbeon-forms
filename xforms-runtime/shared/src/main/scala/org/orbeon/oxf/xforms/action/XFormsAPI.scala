/**
 * Copyright (C) 2011 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.action

import java.util.{List => JList}

import cats.data.NonEmptyList
import cats.syntax.option._
import org.orbeon.dom.QName
import org.orbeon.oxf.util.CollectionUtils._
import org.orbeon.oxf.util.DynamicVariable
import org.orbeon.oxf.util.MarkupUtils._
import org.orbeon.oxf.xforms.NodeInfoFactory.{attributeInfo, elementInfo}
import org.orbeon.oxf.xforms.XFormsContainingDocument
import org.orbeon.oxf.xforms.action.actions._
import org.orbeon.oxf.xforms.control.XFormsControl
import org.orbeon.oxf.xforms.control.controls.XFormsCaseControl
import org.orbeon.oxf.xforms.event.XFormsEvent._
import org.orbeon.oxf.xforms.event.events.{XFormsSubmitDoneEvent, XFormsSubmitErrorEvent, XFormsSubmitEvent}
import org.orbeon.oxf.xforms.event.{Dispatch, XFormsEvent, XFormsEventTarget}
import org.orbeon.oxf.xforms.model.{DataModel, XFormsInstance, XFormsModel}
import org.orbeon.oxf.xforms.submission.XFormsModelSubmission
import org.orbeon.saxon.om
import org.orbeon.scaxon.Implicits._
import org.orbeon.scaxon.SimplePath._
import org.orbeon.xforms.{Constants, UrlType}
import org.w3c.dom.Node.{ATTRIBUTE_NODE, ELEMENT_NODE}

import scala.jdk.CollectionConverters._
import scala.reflect.ClassTag
import scala.util.Try

object XFormsAPI {

  // Dynamically set context
  private val containingDocumentDyn = new DynamicVariable[XFormsContainingDocument]
  private val actionInterpreterDyn  = new DynamicVariable[XFormsActionInterpreter]

  // Every block of action must be run within this
  def withScalaAction[T](interpreter: XFormsActionInterpreter)(body: XFormsActionInterpreter => T): T = {
    actionInterpreterDyn.withValue(interpreter) {
      body(interpreter)
    }
  }

  // Every block of action must be run within this
  def withContainingDocument[T](containingDocument: XFormsContainingDocument)(body: => T): T = {
    containingDocumentDyn.withValue(containingDocument) {
      body
    }
  }

  // Return the action interpreter
  def inScopeActionInterpreter: XFormsActionInterpreter = actionInterpreterDyn.value.get

  // Return the containing document
  def inScopeContainingDocument: XFormsContainingDocument = inScopeContainingDocumentOpt.get

  def inScopeContainingDocumentOpt: Option[XFormsContainingDocument] = containingDocumentDyn.value

  // xf:setvalue
  // @return the node whose value was set, if any
  def setvalue(ref: Seq[om.NodeInfo], value: String): Option[(om.NodeInfo, Boolean)] =
    ref.headOption map { nodeInfo =>

      def onSuccess(oldValue: String): Unit =
        for (action <- actionInterpreterDyn.value)
          yield
            DataModel.logAndNotifyValueChange(
              source             = "scala setvalue",
              nodeInfo           = nodeInfo,
              oldValue           = oldValue,
              newValue           = value,
              isCalculate        = false,
              collector          = Dispatch.dispatchEvent)(
              containingDocument = action.containingDocument,
              logger             = action.indentedLogger
            )

      val changed = DataModel.setValueIfChanged(nodeInfo, value, onSuccess)

      (nodeInfo, changed)
    }

  // xf:setindex
  // @return:
  //
  // - None        if the control is not found
  // - Some(0)     if the control is non-relevant or doesn't have any iterations
  // - Some(index) otherwise, where index is the control's new index
  def setindex(repeatStaticId: String, index: Int): Option[Int] =
    actionInterpreterDyn.value map { interpreter =>
      XFormsSetindexAction.executeSetindexAction(interpreter, interpreter.outerAction, repeatStaticId, index)(interpreter.indentedLogger)
    } collect {
      case newIndex if newIndex >= 0 => newIndex
    }

  // xf:insert
  // @return the inserted nodes
  def insert[T <: om.Item](
    origin                            : Seq[T],
    into                              : Seq[om.NodeInfo] = Nil,
    after                             : Seq[om.NodeInfo] = Nil,
    before                            : Seq[om.NodeInfo] = Nil,
    doDispatch                        : Boolean       = true,
    requireDefaultValues              : Boolean       = false,
    searchForInstance                 : Boolean       = true,
    removeInstanceDataFromClonedNodes : Boolean       = true
  ): Seq[T] =
    if (origin.nonEmpty && (into.nonEmpty || after.nonEmpty || before.nonEmpty)) {

      val action = actionInterpreterDyn.value
      val docOpt = action map (_.containingDocument) orElse containingDocumentDyn.value

      val (positionAttribute, collectionToUpdate) =
        if (before.nonEmpty)
          (InsertPosition.Before, before)
        else
          (InsertPosition.After, after)

      val insertLocation =
        NonEmptyList.fromList(collectionToUpdate.toList).map(_ -> collectionToUpdate.size).toLeft(into.headOption.get) // xxx get

      XFormsInsertAction.doInsert(
        containingDocumentOpt             = docOpt,
        insertPosition                    = positionAttribute,
        insertLocation                    = insertLocation,
        originItemsOpt                    = origin.some,
        doClone                           = true,
        doDispatch                        = doDispatch,
        requireDefaultValues              = requireDefaultValues,
        searchForInstance                 = searchForInstance,
        removeInstanceDataFromClonedNodes = removeInstanceDataFromClonedNodes,
        structuralDependencies            = true)(
        indentedLogger                    = action map (_.indentedLogger) orNull,
      ).asInstanceOf[JList[T]].asScala
    } else
      Nil

  // xf:delete
  def delete(
    ref           : Seq[om.NodeInfo],
    doDispatch    : Boolean = true
  ): Seq[om.NodeInfo] =
    if (ref.nonEmpty) {

      val actionOpt = actionInterpreterDyn.value
      val docOpt    = actionOpt map (_.containingDocument) orElse containingDocumentDyn.value

      val deletionDescriptors =
        XFormsDeleteAction.doDelete(
          containingDocumentOpt = docOpt,
          collectionToUpdate    = ref,
          deleteIndexOpt        = None,
          doDispatch            = doDispatch)(
          indentedLogger        = actionOpt map (_.indentedLogger) orNull
        )

      deletionDescriptors map (_.nodeInfo)
    } else
      Nil

  // Rename an element or attribute
  // - if the name hasn't changed, don't do anything
  // - if the node is an element, its content is placed back into the renamed element
  // NOTE: This should be implemented as a core XForms action (see also XQuery updates)
  def rename(nodeInfo: om.NodeInfo, newName: QName): om.NodeInfo = {
    require(nodeInfo ne null)
    require(Set(ELEMENT_NODE, ATTRIBUTE_NODE)(nodeInfo.getNodeKind.toShort))

    val oldName: QName = nodeInfo.uriQualifiedName
    if (oldName != newName) {
      val newNodeInfo = nodeInfo.getNodeKind match {
        case ELEMENT_NODE   => elementInfo(newName, (nodeInfo /@ @*) ++ (nodeInfo / Node))
        case _              => throw new IllegalArgumentException
      }

      insert(into = nodeInfo parent *, after = nodeInfo, origin = newNodeInfo)
      // Don't rely on the value returned by `insert()` for `result` as it is `Nil` if not inserting into an instance
      val result = nodeInfo.followingSibling(*).head
      delete(nodeInfo)
      result
    } else {
      nodeInfo
    }
  }

  // TODO: Move next 3 methods to a more general place.
  // Move the given element before another element
  def moveElementBefore(element: om.NodeInfo, other: om.NodeInfo) = {
    val inserted = insert(into = element parent *, before = other, origin = element)
    delete(element)
    inserted.head
  }

  // Move the given element after another element
  def moveElementAfter(element: om.NodeInfo, other: om.NodeInfo) = {
    val inserted = insert(into = element parent *, after = other, origin = element)
    delete(element)
    inserted.head
  }

  // Move the given element into another element as the last element
  def moveElementIntoAsLast(element: om.NodeInfo, other: om.NodeInfo) = {
    val inserted = insert(into = other, after = other / *, origin = element)
    delete(element)
    inserted.head
  }

  // Set an attribute value, creating it if missing, updating it if present
  // NOTE: This should be implemented as an optimization of the XForms insert action.
  // @return the new or existing attribute node
  // NOTE: Would be nice to return attribute (new or existing), but doInsert() is not always able to wrap the inserted
  // nodes.
  def ensureAttribute(element: om.NodeInfo, attName: QName, value: String): Unit =
    element /@ attName match {
      case Seq()        => insert(into = element, origin = attributeInfo(attName, value))
      case Seq(att, _*) => setvalue(att, value)
    }

  // NOTE: The value is by-name and used only if needed
  def toggleAttribute(element: om.NodeInfo, attName: QName, value: => String, set: Boolean): Unit =
    if (set)
      ensureAttribute(element, attName, value)
    else
      delete(element /@ attName)

  def toggleAttribute(element: om.NodeInfo, attName: QName, value: Option[String]): Unit =
    toggleAttribute(element, attName, value.get, value.isDefined)

  // Return an instance's root element in the current action context as per xxf:instance()
  def instanceRoot(staticId: String): Option[om.NodeInfo] =
    XFormsInstance.findInAncestorScopes(inScopeActionInterpreter.container, staticId)

  // Return an instance within a top-level model
  def topLevelInstance(modelId: String, instanceId: String): Option[XFormsInstance] =
    topLevelModel(modelId) flatMap (_.findInstance(instanceId))

  // Return a top-level model by static id
  // NOTE: This search is not very efficient, but this allows mocking in tests, where getObjectByEffectiveId causes issues
  // 2013-04-03: Unsure if we still need this for mocking
  def topLevelModel(modelId: String): Option[XFormsModel] =
    inScopeContainingDocumentOpt flatMap (_.models find (_.getId == modelId))

  def context[T](xpath: String)(body: => T): T = throw new NotImplementedError("context")
  def context[T](item: om.Item)(body: => T): T = throw new NotImplementedError("context")
  def event[T](attributeName: String): Seq[om.Item] = throw new NotImplementedError("event")

  // The xf:dispatch action
  def dispatch(
    name            : String,
    targetId        : String,
    bubbles         : Boolean                    = true,
    cancelable      : Boolean                    = true,
    properties      : XFormsEvent.PropertyGetter = XFormsEvent.EmptyGetter,
    delay           : Option[Int]                = None,
    showProgress    : Boolean                    = true,
    allowDuplicates : Boolean                    = false
  ): Unit =
    resolveAs[XFormsEventTarget](targetId) foreach {
      XFormsDispatchAction.dispatch(
        name,
        _,
        bubbles,
        cancelable,
        properties,
        delay,
        showProgress,
        allowDuplicates
      )
    }

  private val SubmitEvents = Seq("xforms-submit-done", "xforms-submit-error")

  // xf:send
  // Send the given submission and applies the body with the resulting event if the submission completed
  def send[T](submissionId: String, properties: PropertyGetter = EmptyGetter)(body: XFormsEvent => T): Option[T] = {
    resolveAs[XFormsModelSubmission](submissionId) flatMap { submission =>

      var result: Option[Try[T]] = None

      // Listener runs right away but stores the Try
      val listener: Dispatch.EventListener = { e =>
        result = Some(Try(body(e)))
      }

      // Add both listeners
      SubmitEvents foreach (submission.addListener(_, listener))

      // Dispatch and make sure the listeners are removed
      try Dispatch.dispatchEvent(new XFormsSubmitEvent(submission, properties))
      finally SubmitEvents foreach (submission.removeListener(_, Some(listener)))

      // - If the dispatch completed successfully and the submission started, it *should* have completed with either
      //   `xforms-submit-done` or `xforms-submit-error`. In this case, we have called `body(event)` and return
      //   `Option[T]` or throw an exception if `body(event)` failed.
      // - But in particular if the xforms-submit event got canceled, we might be in a situation where no
      //   xforms-submit-done or xforms-submit-error was dispatched. In this case, we return `None`.
      // - If the dispatch failed for other reasons, it might have thrown an exception, which is propagated.

      result map (_.get)
    }
  }

  class SubmitException(e: XFormsSubmitErrorEvent) extends RuntimeException

  // xf:send which throws a SubmitException in case of error
  def sendThrowOnError(submissionId: String, properties: PropertyGetter = EmptyGetter): Option[XFormsSubmitDoneEvent] =
    send(submissionId, properties) {
      case done:  XFormsSubmitDoneEvent  => done
      case error: XFormsSubmitErrorEvent => throw new SubmitException(error)
    }

  // NOTE: There is no source id passed so we resolve relative to the document
  def resolveAs[T: ClassTag](staticOrAbsoluteId: String): Option[T] =
    inScopeContainingDocument.resolveObjectByIdInScope(Constants.DocumentId, staticOrAbsoluteId, None) flatMap collectByErasedType[T]

  // xf:toggle
  def toggle(caseId: String, mustHonorDeferredUpdateFlags: Boolean = true): Unit =
    resolveAs[XFormsCaseControl](caseId) foreach
      (XFormsToggleAction.toggle(_, mustHonorDeferredUpdateFlags))

  // xf:rebuild
  def rebuild(modelId: String, mustHonorDeferredUpdateFlags: Boolean = false): Unit =
    resolveAs[XFormsModel](modelId) foreach
      (RRRAction.rebuild(_, mustHonorDeferredUpdateFlags))

  // xf:recalculate
  def recalculate(modelId: String, mustHonorDeferredUpdateFlags: Boolean = false, applyDefaults: Boolean = false): Unit =
    resolveAs[XFormsModel](modelId) foreach
      (RRRAction.recalculate(_, mustHonorDeferredUpdateFlags, applyDefaults))

  // xf:refresh
  def refresh(modelId: String): Unit =
    resolveAs[XFormsModel](modelId) foreach
      XFormsRefreshAction.refresh

  // xf:show
  def show(dialogId: String, properties: PropertyGetter = EmptyGetter): Unit =
    resolveAs[XFormsEventTarget](dialogId) foreach
      (XXFormsShowAction.showDialog(_, properties = properties))

  // xf:hide
  def hide(dialogId: String, properties: PropertyGetter = EmptyGetter): Unit =
    resolveAs[XFormsEventTarget](dialogId) foreach
      (XXFormsHideAction.hideDialog(_, properties = properties))

  // xf:load
  def load(
    url                          : String,
    target                       : Option[String] = None,
    showProgress                 : Boolean        = false,
    mustHonorDeferredUpdateFlags : Boolean        = true
  ): Unit =
    XFormsLoadAction.resolveStoreLoadValue(
      containingDocument           = inScopeContainingDocument,
      currentElem                  = None,
      doReplace                    = true,
      value                        = url.encodeHRRI(processSpace = true),
      target                       = target,
      urlType                      = UrlType.Render,
      urlNorewrite                 = false,
      isShowProgressOpt            = Some(showProgress),
      mustHonorDeferredUpdateFlags = mustHonorDeferredUpdateFlags
    )

  // xf:setfocus
  def setfocus(controlId: String, includes: Set[QName], excludes: Set[QName]): Unit =
    resolveAs[XFormsControl](controlId) foreach
      (XFormsSetfocusAction.setfocus(_, includes, excludes))
}