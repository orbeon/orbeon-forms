/**
 * Copyright (C) 2010 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.control

import org.dom4j.Element
import org.orbeon.oxf.xforms.analysis.ControlAnalysisFactory.ValueControl
import org.orbeon.oxf.xforms.analysis.ElementAnalysis
import org.orbeon.oxf.xforms.analysis.controls.ComponentControl
import org.orbeon.oxf.xforms.analysis.model.Instance
import org.orbeon.oxf.xforms.control.controls.InstanceMirror._
import org.orbeon.oxf.xforms.control.controls.{InstanceMirror, XXFormsComponentRootControl}
import org.orbeon.oxf.xforms.event.Dispatch.EventListener
import org.orbeon.oxf.xforms.event.events.{XFormsModelConstructDoneEvent, XFormsModelConstructEvent, XFormsReadyEvent}
import org.orbeon.oxf.xforms.event.{Dispatch, XFormsEvent, XFormsEvents}
import org.orbeon.oxf.xforms.model.AllDefaultsStrategy
import org.orbeon.oxf.xforms.state.ControlState
import org.orbeon.oxf.xforms.xbl.XBLContainer
import org.orbeon.oxf.xforms.{BindingContext, XFormsInstance}
import org.orbeon.oxf.xml.SaxonUtils
import org.orbeon.saxon.om.VirtualNode
import org.orbeon.scaxon.XML.unsafeUnwrapElement
import org.w3c.dom.Node.ELEMENT_NODE
import org.xml.sax.helpers.AttributesImpl

import scala.collection.JavaConverters._

// A component control with native support for a value
class XFormsValueComponentControl(
  container   : XBLContainer,
  parent      : XFormsControl,
  element     : Element,
  effectiveId : String
) extends XFormsComponentControl(
  container,
  parent,
  element,
  effectiveId
) with XFormsValueControl {

  override type Control <: ComponentControl with ValueControl

  // Don't expose an external value unless explicitly allowed
  // Q: Should throw if not allowed?
  override def storeExternalValue(value: String): Unit =
    if (staticControl.binding.abstractBinding.modeExternalValue)
      super.storeExternalValue(value)

  // Don't expose an external value unless explicitly allowed
  override def evaluateExternalValue(): Unit =
    if (staticControl.binding.abstractBinding.modeExternalValue)
      super.evaluateExternalValue()
    else
      setExternalValue(null)

  // Because of the above, `super.equalsExternalUseExternalValue` returns `true`if just the value has changed, therefore it doesn't
  // catch and send changes to the `empty` property. So we override and check whether `empty` has changed.
  // See: https://github.com/orbeon/orbeon-forms/issues/2310
  override def compareExternalUseExternalValue(
    previousExternalValue : Option[String],
    previousControl       : Option[XFormsControl]
  ): Boolean =
    previousControl match {
      case Some(other: XFormsValueComponentControl) ⇒
        isEmptyValue == other.isEmptyValue &&
        super.compareExternalUseExternalValue(previousExternalValue, previousControl)
      case _ ⇒
        false
    }
}

// A component control with or without a value
class XFormsComponentControl(
  container   : XBLContainer,
  parent      : XFormsControl,
  element     : Element,
  effectiveId : String
) extends XFormsSingleNodeContainerControl(container, parent, element, effectiveId) {

  override type Control <: ComponentControl

  private var _nestedContainer: Option[XBLContainer] = None
  def nestedContainer = _nestedContainer.get

  // If the component handles LHHA itself, they are considered under of the nested container
  override def lhhaContainer = nestedContainer

  private var _listeners: Seq[(XFormsInstance, EventListener)] = Nil

  // Create nested container upon creation
  createNestedContainer()

  // React to external events only if we are relevant OR upon receiving xforms-disabled
  def canRunEventHandlers(event: XFormsEvent) =
    isRelevant || event.name == "xforms-disabled"

  private def createNestedContainer(): Unit = {

    assert(_nestedContainer.isEmpty)

    val newContainer = container.createChildContainer(this)
    // There may or may not be nested models
    newContainer.addAllModels()

    _nestedContainer = Some(newContainer)
  }

  // Destroy container and models if any
  def destroyNestedContainer(): Unit = {
    destroyMirrorListenerIfNeeded()
    nestedContainer.destroy()
    _nestedContainer = None
  }

  // For xxf:dynamic updates of top-level XBL shadow trees
  def recreateNestedContainer(): Unit = {
    createNestedContainer()
    if (isRelevant)
      initializeModels()
  }

  private def modeBinding = staticControl.binding.abstractBinding.modeBinding

  // Only handle binding if we support modeBinding
  override protected def computeBinding(parentContext: BindingContext) =
    if (modeBinding)
      super.computeBinding(parentContext)
    else
      super.computeBindingCopy(parentContext)

  override def bindingContextForChild = {
    // Start with inner context
    // For nested event handlers, this still works, because the nested handler can never match the inner scope. So
    // the context goes inner context → component binding.
    val contextStack = nestedContainer.getContextStack
    contextStack.setParentBindingContext(bindingContext)
    contextStack.resetBindingContext
    contextStack.getCurrentBindingContext
  }

  override def onCreate(restoreState: Boolean, state: Option[ControlState]): Unit = {
    super.onCreate(restoreState, state)

    if (Controls.isRestoringDynamicState)
      restoreModels()
    else
      initializeModels()

    addEnabledListener()
  }

  // Attach a mirror listener if needed
  // Return the reference node if a listener was created
  private def createMirrorListener(mirrorInstance: XFormsInstance, referenceNode: VirtualNode): Option[VirtualNode] = {

    val outerDocument = referenceNode.getDocumentRoot
    val outerInstance = containingDocument.getInstanceForNode(outerDocument)

    val newListenerWithCycleDetector = new ListenerCycleDetector

    val outerListener = toEventListener(
      newListenerWithCycleDetector(
        containingDocument,
        toInnerInstanceNode(
          outerDocument,
          nestedContainer.partAnalysis,
          nestedContainer,
          findOuterInstanceDetailsXBL(mirrorInstance, referenceNode)
        )
      )
    )

    val innerListener = toEventListener(
      newListenerWithCycleDetector(
        containingDocument,
        toOuterInstanceNodeXBL(outerInstance, referenceNode, nestedContainer.partAnalysis)
      )
    )

    // Set outer and inner listeners

    _listeners = List(outerInstance → outerListener, mirrorInstance → innerListener)

    _listeners foreach { case (instance, listener) ⇒
      InstanceMirror.addListener(instance, listener)
    }

    Some(referenceNode)
  }

  private def destroyMirrorListenerIfNeeded(): Unit = {
    _listeners foreach { case (instance, listener) ⇒
      InstanceMirror.removeListener(instance, listener)
    }

    _listeners = Nil
  }

  override def onDestroy(): Unit = {
    removeEnabledListener()
    destroyMirrorListenerIfNeeded()
    super.onDestroy()
  }

  private def initializeModels(): Unit = {

    // xforms-model-construct, without RRR
    for (model ← nestedContainer.models) {
      Dispatch.dispatchEvent(new XFormsModelConstructEvent(model, rrr = false))
      // NOTE: xforms-model-construct already does a `markStructuralChange()` but without `AllDefaultsStrategy`
      model.markStructuralChange(None, AllDefaultsStrategy)
    }

    initializeMirrorListenerIfNeeded(dispatch = false)

    // Do RRR as xforms-model-construct didn't do it
    for (model ← nestedContainer.models) {
      model.doRebuild()
      model.doRecalculateRevalidate()
    }

    // xforms-model-construct-done
    for (model ← nestedContainer.models)
      Dispatch.dispatchEvent(new XFormsModelConstructDoneEvent(model))
  }

  private def restoreModels(): Unit = {
    nestedContainer.restoreModelsState(deferRRR = true)
    initializeMirrorListenerIfNeeded(dispatch = false)
    // Do RRR as isRestoringDynamicState() didn't do it
    for (model ← nestedContainer.models) {
      model.doRebuild()
      model.doRecalculateRevalidate()
    }
  }

  private def initializeMirrorListenerIfNeeded(dispatch: Boolean): Option[XFormsInstance] = {

    // NOTE: Must be called after xforms-model-construct so that instances are present
    def findMirrorInstance = (
      nestedContainer.models.iterator
      flatMap (_.getInstances.asScala)
      find    (_.instance.element.attributeValue("mirror") == "true")
    )

    // Process mirror instance if any
    findMirrorInstance map { mirrorInstance ⇒

      // Reference node must be a wrapped element
      // Also support case where there is no binding, and in which case use the binding context. This is done
      // because Form Builder doesn't place a ref or bind on section template components as of 2013-01-17.
      val referenceNode = (if (modeBinding) binding.headOption else contextForBinding) collect {
        case node: VirtualNode if node.getNodeKind == ELEMENT_NODE ⇒ node
      }

      // Create new doc rooted at reference node
      val doc =
        Instance.extractDocument(
          element               = unsafeUnwrapElement(referenceNode.get),
          excludeResultPrefixes = Set(),
          readonly              = false,
          exposeXPathTypes      = mirrorInstance.exposeXPathTypes,
          removeInstanceData    = true
        )

      // Update initial instance
      mirrorInstance.replace(doc, dispatch)

      // Create the listeners
      createMirrorListener(mirrorInstance, referenceNode.get)

      mirrorInstance
    }
  }

  private var _enabledListener: Dispatch.EventListener = _

  private def addEnabledListener() = {

    assert(_enabledListener eq null)

    // Logic: when the component control receives the `xforms-enabled` event (which occurs during refresh after
    // the nested models have already been initialized) we dispatch `xforms-ready` to the nested models. This is
    // for consistency with the top-level `xforms-ready` which occurs when the control tree has been initialized.
    // We could have considered using another event, as the name `xforms-ready` does not fully reflect the
    // meaning associated with the top-level. On the other hand, this makes it easier to translate a top-level
    // model into a nested model.
    _enabledListener = _ ⇒
      for {
        container ← _nestedContainer.iterator
        model     ← nestedContainer.models
      } locally {
        Dispatch.dispatchEvent(new XFormsReadyEvent(model))
      }

    addListener(XFormsEvents.XFORMS_ENABLED, _enabledListener)
  }

  private def removeEnabledListener(): Unit =
    _enabledListener = null

  override def onBindingUpdate(oldBinding: BindingContext, newBinding: BindingContext): Unit = {
    super.onBindingUpdate(oldBinding, newBinding)
    val isNodesetChange = ! SaxonUtils.compareItemSeqs(oldBinding.nodeset.asScala, newBinding.nodeset.asScala)
    if (isNodesetChange) {
      destroyMirrorListenerIfNeeded()
      initializeMirrorListenerIfNeeded(dispatch = true) foreach { mirrorInstance ⇒
        // If the instance was updated, it is due for an RRR, but nobody will check that before the refresh is done, so do it here.
        mirrorInstance.model.doRebuild()
        mirrorInstance.model.doRecalculateRevalidate()
      }
    }
  }

  // This is called iif the iteration index changes
  override def updateEffectiveId(): Unit = {

    // Update rest of control tree
    super.updateEffectiveId()

    // Update container with new effective id
    nestedContainer.updateEffectiveId(getEffectiveId)
  }

  override def iterationRemoved(): Unit = {
    // Inform descendants
    super.iterationRemoved()

    // Destroy container and models if any
    destroyNestedContainer()
  }

  // Simply delegate but switch the container
  override def buildChildren(
    buildTree: (XBLContainer, BindingContext, ElementAnalysis, Seq[Int]) ⇒ Option[XFormsControl],
    idSuffix: Seq[Int]
  ): Unit =
    Controls.buildChildren(
      control   = this,
      children  = staticControl.children,
      buildTree = (_, bindingContext, staticElement, idSuffix) ⇒ buildTree(nestedContainer, bindingContext, staticElement, idSuffix),
      idSuffix  = idSuffix
    )

  // Get the control at the root of the inner scope of the component
  def innerRootControl = children collectFirst { case root: XXFormsComponentRootControl ⇒ root } get

  private lazy val handleLHHA =
    staticControl.binding.abstractBinding.modeLHHA && ! staticControl.binding.abstractBinding.modeLHHACustom


  override def addAjaxAttributes(attributesImpl: AttributesImpl, previousControlOpt: Option[XFormsControl]): Boolean = {

    var added = super.addAjaxAttributes(attributesImpl, previousControlOpt)

    // Whether to tell the client to initialize the control within a new iteration
    if (previousControlOpt.isEmpty && isRelevant && staticControl.binding.abstractBinding.modeJavaScriptLifecycle)
      added |= ControlAjaxSupport.addAttributeIfNeeded(attributesImpl, "init", "true", isNewRepeatIteration = false, isDefaultValue = false)

    added
  }

  // Don't add Ajax LHHA for custom-lhha mode
  override def addAjaxLHHA(attributesImpl: AttributesImpl, previousControlOpt: Option[XFormsControl]) =
    handleLHHA && super.addAjaxLHHA(attributesImpl, previousControlOpt)

  // Consider LHHA hasn't externally changed for custom-lhha mode
  override def compareLHHA(other: XFormsControl) =
    ! handleLHHA || super.compareLHHA(other)
}