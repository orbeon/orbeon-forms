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

import cats.data.NonEmptyList
import cats.syntax.option.*
import org.orbeon.dom.QName
import org.orbeon.oxf.util.CollectionUtils.*
import org.orbeon.oxf.util.MarkupUtils.*
import org.orbeon.oxf.util.{DynamicVariable, IndentedLogger}
import org.orbeon.oxf.xforms.NodeInfoFactory.{attributeInfo, elementInfo}
import org.orbeon.oxf.xforms.XFormsContainingDocument
import org.orbeon.oxf.xforms.action.actions.*
import org.orbeon.oxf.xforms.control.XFormsControl
import org.orbeon.oxf.xforms.control.controls.XFormsCaseControl
import org.orbeon.oxf.xforms.event.XFormsEvent.*
import org.orbeon.oxf.xforms.event.XFormsEvents.{XFORMS_SUBMIT_DONE, XFORMS_SUBMIT_ERROR}
import org.orbeon.oxf.xforms.event.events.{XFormsSubmitDoneEvent, XFormsSubmitErrorEvent, XFormsSubmitEvent}
import org.orbeon.oxf.xforms.event.{Dispatch, EventCollector, XFormsEvent, XFormsEventTarget}
import org.orbeon.oxf.xforms.model.{DataModel, XFormsInstance, XFormsModel}
import org.orbeon.oxf.xforms.submission.XFormsModelSubmission
import org.orbeon.saxon.om
import org.orbeon.scaxon.Implicits.*
import org.orbeon.scaxon.SimplePath.*
import org.orbeon.xforms.{Constants, UrlType}
import org.w3c.dom.Node.{ATTRIBUTE_NODE, ELEMENT_NODE}

import java.util.{List => JList}
import scala.jdk.CollectionConverters.*
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

  def withContainingDocument[T](containingDocument: XFormsContainingDocument)(body: => T): T =
    containingDocumentDyn.withValue(containingDocument) {
      body
    }

  // Return the action interpreter
  def inScopeActionInterpreter: XFormsActionInterpreter = actionInterpreterDyn.value.get

  // Return the containing document
  def inScopeContainingDocument: XFormsContainingDocument = inScopeContainingDocumentOpt.get

  def inScopeContainingDocumentOpt: Option[XFormsContainingDocument] = containingDocumentDyn.value

  // xf:setvalue
  // @return the node whose value was set, if any
  def setvalue(ref: collection.Seq[om.NodeInfo], value: String): Option[(om.NodeInfo, Boolean)] =
    ref.headOption map { nodeInfo =>

      val (docOpt, indentedLoggerOpt) = findDocAndLoggerFromActionOrScope

      def onSuccess(oldValue: String): Unit =
        for (doc <- docOpt)
        yield
          DataModel.logAndNotifyValueChange(
            source             = "scala setvalue",
            nodeInfo           = nodeInfo,
            oldValue           = oldValue,
            newValue           = value,
            isCalculate        = false,
            collector          = (event: XFormsEvent) => Dispatch.dispatchEvent(event, EventCollector.Throw))(
            containingDocument = doc,
            logger             = indentedLoggerOpt.orNull
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
      XFormsSetindexAction.executeSetindexAction(
        interpreter,
        interpreter.outerAction,
        repeatStaticId,
        index,
        EventCollector.Throw
      )(interpreter.indentedLogger)
    } collect {
      case newIndex if newIndex >= 0 => newIndex
    }

  private def findDocAndLoggerFromActionOrScope: (Option[XFormsContainingDocument], Option[IndentedLogger]) = {
    val maybeActionInterpreter = actionInterpreterDyn.value

    // If we are in an action the `ActionInterpreter` also returns this same logger
    def defaultLogger: Option[IndentedLogger] =
      containingDocumentDyn.value.map(_.getIndentedLogger(XFormsActions.LoggingCategory))

    (
      maybeActionInterpreter.map(_.containingDocument).orElse(containingDocumentDyn.value),
      maybeActionInterpreter.map(_.indentedLogger).orElse(defaultLogger)
    )
  }

  // xf:insert
  // @return the inserted nodes
  def insert[T <: om.Item](
    origin                            : collection.Seq[T],
    into                              : collection.Seq[om.NodeInfo] = Nil,
    after                             : collection.Seq[om.NodeInfo] = Nil,
    before                            : collection.Seq[om.NodeInfo] = Nil,
    doDispatch                        : Boolean       = true,
    requireDefaultValues              : Boolean       = false,
    searchForInstance                 : Boolean       = true,
    removeInstanceDataFromClonedNodes : Boolean       = true
  ): collection.Seq[T] =
    if (origin.nonEmpty && (into.nonEmpty || after.nonEmpty || before.nonEmpty)) {

      val (docOpt, indentedLoggerOpt) = findDocAndLoggerFromActionOrScope

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
        structuralDependencies            = true,
        collector                         = EventCollector.Throw
      )(
        indentedLogger                    = indentedLoggerOpt.orNull,
      ).asInstanceOf[JList[T]].asScala
    } else
      Nil

  // xf:delete
  def delete(
    ref           : collection.Seq[om.NodeInfo],
    doDispatch    : Boolean = true
  ): collection.Seq[om.NodeInfo] =
    if (ref.nonEmpty) {

      val (docOpt, indentedLoggerOpt) = findDocAndLoggerFromActionOrScope

      val deletionDescriptors =
        XFormsDeleteAction.doDelete(
          containingDocumentOpt = docOpt,
          collectionToUpdate    = ref,
          deleteIndexOpt        = None,
          doDispatch            = doDispatch,
          collector             = EventCollector.Throw
        )(
          indentedLogger        = indentedLoggerOpt.orNull
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
      case collection.Seq()        => insert(into = element, origin = attributeInfo(attName, value))
      case collection.Seq(att, _*) => setvalue(att, value)
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
  def topLevelInstance(modelId: String, instanceId: String)(implicit xfcd: XFormsContainingDocument = inScopeContainingDocumentOpt.orNull): Option[XFormsInstance] =
    topLevelModel(modelId) flatMap (_.findInstance(instanceId))

  // Return a top-level model by static id
  // NOTE: This search is not very efficient, but this allows mocking in tests, where getObjectByEffectiveId causes issues
  // 2013-04-03: Unsure if we still need this for mocking
  def topLevelModel(modelId: String)(implicit xfcd: XFormsContainingDocument = inScopeContainingDocumentOpt.orNull): Option[XFormsModel] =
    Option(xfcd) flatMap (_.models find (_.getId == modelId))

  def context[T](xpath: String)(body: => T): T = throw new NotImplementedError("context")
  def context[T](item: om.Item)(body: => T): T = throw new NotImplementedError("context")
  def event[T](attributeName: String): collection.Seq[om.Item] = throw new NotImplementedError("event")

  // The xf:dispatch action
  def dispatch(
    name            : String,
    targetId        : String,
    bubbles         : Boolean                    = true,
    cancelable      : Boolean                    = true,
    properties      : XFormsEvent.PropertyGetter = XFormsEvent.EmptyGetter, // todo: handle tunnel params
    delay           : Option[Int]                = None,
    showProgress    : Boolean                    = true,
    allowDuplicates : Boolean                    = false
  )(implicit
    xfcd            : XFormsContainingDocument = inScopeContainingDocument
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
        allowDuplicates,
        EventCollector.Throw
      )
    }

  private val SubmitEvents = List(XFORMS_SUBMIT_DONE, XFORMS_SUBMIT_ERROR)

  // xf:send
  // Send the given submission and applies the body with the resulting event if the submission completed
  private def send[T](
    submissionId: String,
    props       : List[PropertyValue])(
    body        : XFormsEvent => T
  )(implicit
    xfcd        : XFormsContainingDocument
  ): Option[T] = {
    resolveAs[XFormsModelSubmission](submissionId) flatMap { submission =>

      var result: Option[Try[T]] = None

      // Listener runs right away but stores the Try
      val listener: Dispatch.EventListener = { e =>
        result = Some(Try(body(e)))
      }

      // Add both listeners
      SubmitEvents foreach (submission.addListener(_, listener))

      // Dispatch and make sure the listeners are removed
      try Dispatch.dispatchEvent(new XFormsSubmitEvent(submission, ActionPropertyGetter(props)), EventCollector.Throw)
      finally SubmitEvents foreach (submission.removeListener(_, Some(listener)))

      // - If the dispatch completed successfully and the submission started, it *should* have completed with either
      //   `xforms-submit-done` or `xforms-submit-error`. In this case, we have called `body(event)` and return
      //   `Option[T]` or throw an exception if `body(event)` failed.
      // - But in particular if the `xforms-submit` event got canceled, we might be in a situation where no
      //   `xforms-submit-done` or `xforms-submit-error` was dispatched. In this case, we return `None`.
      // - If the dispatch failed for other reasons, it might have thrown an exception, which is propagated.

      result map (_.get)
    }
  }

//  private def sendAsyncImpl(
//    submissionId   : String,
//    props          : List[PropertyValue]
//  )(implicit
//    externalContext: ExternalContext,
//    xfcd           : XFormsContainingDocument
//  ): Option[Future[XFormsEvent]] = {
//    resolveAs[XFormsModelSubmission](submissionId) map { submission =>
//
//      val p = Promise[XFormsEvent]()
//
//      val doneListener: Dispatch.EventListener =
//        e => p.success(e)
//
//      val errorListener: Dispatch.EventListener =
//        e => p.failure(new SubmitException(e.asInstanceOf[XFormsSubmitErrorEvent]))
//
//      submission.addListener(XFORMS_SUBMIT_DONE,  doneListener)
//      submission.addListener(XFORMS_SUBMIT_ERROR, errorListener)
//
//      try
//        Dispatch.dispatchEvent(new XFormsSubmitEvent(submission, ActionPropertyGetter(props)))
//      catch {
//        case NonFatal(t) =>
//          p.failure(t)
//      }
//
//      val f = p.future
//      f.onComplete { _ =>
//        submission.removeListener(XFORMS_SUBMIT_DONE, Some(doneListener))
//        submission.removeListener(XFORMS_SUBMIT_ERROR, Some(errorListener))
//      }
//      f
//    }
//  }

  class SubmitException(e: XFormsSubmitErrorEvent) extends RuntimeException

  // xf:send which throws a SubmitException in case of error
  def sendThrowOnError(submissionId: String, props: List[PropertyValue] = Nil)(implicit xfcd: XFormsContainingDocument = inScopeContainingDocument): Option[XFormsSubmitDoneEvent] =
    send(submissionId, props) {
      case done:  XFormsSubmitDoneEvent  => done
      case error: XFormsSubmitErrorEvent => throw new SubmitException(error)
    }

//    def sendAsync(
//      submissionId   : String,
//      props          : List[PropertyValue] = Nil
//    )(implicit
//      externalContext: ExternalContext,
//      xfcd           : XFormsContainingDocument
//    ): Option[Future[XFormsSubmitDoneEvent]] =
//      sendAsyncImpl(submissionId, props).map { f =>
//        f.map {
//          case done:  XFormsSubmitDoneEvent  => done
//          case error: XFormsSubmitErrorEvent => throw new SubmitException(error)
//        }
//      }

  // NOTE: There is no source id passed so we resolve relative to the document
  def resolveAs[T: ClassTag](staticOrAbsoluteId: String)(implicit xfcd: XFormsContainingDocument = inScopeContainingDocument): Option[T] =
    xfcd.resolveObjectByIdInScope(Constants.DocumentId, staticOrAbsoluteId, None) flatMap collectByErasedType[T]

  // xf:toggle
  def toggle(caseId: String, mustHonorDeferredUpdateFlags: Boolean = true)(implicit xfcd: XFormsContainingDocument = inScopeContainingDocument): Unit =
    resolveAs[XFormsCaseControl](caseId) foreach
      (XFormsToggleAction.toggle(_, mustHonorDeferredUpdateFlags, collector = EventCollector.Throw))

  // xf:rebuild
  def rebuild(modelId: String, mustHonorDeferredUpdateFlags: Boolean = false)(implicit xfcd: XFormsContainingDocument = inScopeContainingDocument): Unit =
    resolveAs[XFormsModel](modelId) foreach
      (RRRAction.rebuild(_, mustHonorDeferredUpdateFlags))

  // xf:recalculate
  def recalculate(modelId: String, mustHonorDeferredUpdateFlags: Boolean = false, applyDefaults: Boolean = false)(implicit xfcd: XFormsContainingDocument = inScopeContainingDocument): Unit =
    resolveAs[XFormsModel](modelId) foreach
      (RRRAction.recalculate(_, mustHonorDeferredUpdateFlags, applyDefaults))

  // xf:refresh
  def refresh(modelId: String)(implicit xfcd: XFormsContainingDocument = inScopeContainingDocument): Unit =
    resolveAs[XFormsModel](modelId) foreach
      (XFormsRefreshAction.refresh(_, EventCollector.Throw))

  // xf:show
  def show(dialogId: String, properties: PropertyGetter = EmptyGetter)(implicit xfcd: XFormsContainingDocument = inScopeContainingDocument): Unit =
    resolveAs[XFormsEventTarget](dialogId) foreach
      (XXFormsShowAction.showDialog(_, properties = properties, collector = EventCollector.Throw))

  // xf:hide
  def hide(dialogId: String, properties: PropertyGetter = EmptyGetter)(implicit xfcd: XFormsContainingDocument = inScopeContainingDocument): Unit =
    resolveAs[XFormsEventTarget](dialogId) foreach
      (XXFormsHideAction.hideDialog(_, properties = properties, collector = EventCollector.Throw))

  // xf:load
  def load(
    url                          : String,
    target                       : Option[String] = None,
    showProgress                 : Boolean        = false,
    mustHonorDeferredUpdateFlags : Boolean        = true
  )(implicit
    xfcd                         : XFormsContainingDocument = inScopeContainingDocument
  ): Unit =
    XFormsLoadAction.resolveStoreLoadValue(
      containingDocument           = xfcd,
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
  def setfocus(controlId: String, includes: Set[QName], excludes: Set[QName])(implicit xfcd: XFormsContainingDocument = inScopeContainingDocument): Unit =
    resolveAs[XFormsControl](controlId) foreach
      (XFormsSetfocusAction.setfocus(_, includes, excludes, collector = EventCollector.Throw))
}