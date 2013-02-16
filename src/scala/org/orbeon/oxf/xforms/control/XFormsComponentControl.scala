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

import collection.JavaConverters._
import org.dom4j.Element
import org.orbeon.oxf.xforms.analysis.ControlAnalysisFactory.ValueControl
import org.orbeon.oxf.xforms.analysis.ElementAnalysis
import org.orbeon.oxf.xforms.analysis.controls.ComponentControl
import org.orbeon.oxf.xforms.control.controls.InstanceMirror._
import org.orbeon.oxf.xforms.control.controls.{XXFormsComponentRootControl, InstanceMirror}
import org.orbeon.oxf.xforms.event.events.{XFormsModelConstructDoneEvent, XFormsModelConstructEvent}
import org.orbeon.oxf.xforms.event.{EventListener ⇒ JEventListener, Dispatch}
import org.orbeon.oxf.xforms.xbl.XBLContainer
import org.orbeon.oxf.xforms.{XFormsInstance, BindingContext}
import org.orbeon.saxon.om.VirtualNode
import org.orbeon.scaxon.XML.unwrapElement
import org.w3c.dom.Node.ELEMENT_NODE
import org.xml.sax.helpers.AttributesImpl
import org.orbeon.oxf.xforms.analysis.model.Instance

// A component control with native support for a value
class XFormsValueComponentControl(container: XBLContainer, parent: XFormsControl, element: Element, effectiveId: String)
        extends XFormsComponentControl(container, parent, element, effectiveId) with XFormsValueControl {
    override type Control <: ComponentControl with ValueControl

    // Don't expose an external value
    override def evaluateExternalValue(): Unit = setExternalValue(null)
}

// A component control with or without a value
class XFormsComponentControl(container: XBLContainer, parent: XFormsControl, element: Element, effectiveId: String)
        extends XFormsSingleNodeContainerControl(container, parent, element, effectiveId) {

    override type Control <: ComponentControl

    private var _nestedContainer: Option[XBLContainer] = None
    def nestedContainer = _nestedContainer.get

    private var _outerListener: Option[(XFormsInstance, JEventListener)] = None

    // Create nested container upon creation
    createNestedContainer()

    def createNestedContainer(): Unit = {

        assert(_nestedContainer.isEmpty)

        val newContainer = container.createChildContainer(this)
        // There may or may not be nested models
        newContainer.addAllModels()
        // Make sure there is location data
        newContainer.locationData = getLocationData

        _nestedContainer = Some(newContainer)
    }

    // Destroy container and models if any
    def destroyNestedContainer(): Unit = {
        destroyMirrorListener()
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

    override def onCreate() {
        super.onCreate()
        if (Controls.isRestoringDynamicState)
            nestedContainer.restoreModelsState()
        else
            initializeModels()
    }

    // Attach a mirror listener if needed
    // Return the reference node if a listener was created
    private def createMirrorListener(mirrorInstance: XFormsInstance, referenceNode: VirtualNode): Option[VirtualNode] = {

        implicit val logger = getIndentedLogger

        val outerDocument = referenceNode.getDocumentRoot
        val outerInstance = containingDocument.getInstanceForNode(outerDocument)

        val outerListener: JEventListener = mirrorListener(
            containingDocument,
            toInnerInstanceNode(
                outerDocument,
                nestedContainer.partAnalysis,
                nestedContainer,
                findOuterInstanceDetailsXBL(mirrorInstance, referenceNode)))

        val innerListener: JEventListener = mirrorListener(
            containingDocument,
            toOuterInstanceNodeXBL(outerInstance, referenceNode, nestedContainer.partAnalysis))

        // Set outer and inner listeners
        InstanceMirror.addListener(outerInstance, outerListener)
        InstanceMirror.addListener(mirrorInstance, innerListener)

        _outerListener = Some((outerInstance, outerListener))

        Some(referenceNode)
    }

    private def destroyMirrorListener(): Unit = {
        _outerListener foreach { case (outerInstance, outerListener) ⇒
            InstanceMirror.removeListener(outerInstance, outerListener)
        }

        _outerListener = None
    }

    override def onDestroy() {
        destroyMirrorListener()
        super.onDestroy()
    }

    private def initializeModels(): Unit = {

        // xforms-model-construct, without RRR
        for (model ← nestedContainer.models)
            Dispatch.dispatchEvent(new XFormsModelConstructEvent(model, rrr = false))

        // NOTE: Must be called after xforms-model-construct so that instances are present
        def findMirrorInstance =
            nestedContainer.models.iterator flatMap (_.getInstances.asScala) find (i ⇒ i.instance.element.attributeValue("mirror") == "true")

        // Process mirror instance if any
        findMirrorInstance foreach { mirrorInstance ⇒

            // Reference node must be a wrapped element
            // Also support case where there is no binding, and in which case use the binding context. This is done
            // because Form Builder doesn't place a ref or bind on section template components as of 2013-01-17.
            val referenceNode = (if (modeBinding) binding else contextForBinding).headOption collect {
                case node: VirtualNode if node.getNodeKind == ELEMENT_NODE ⇒ node
            }

            // Create new doc rooted at reference node
            val doc =
                Instance.extractDocument(
                    unwrapElement(referenceNode.get),
                    excludeResultPrefixes = Set(),
                    readonly              = false,
                    exposeXPathTypes      = mirrorInstance.exposeXPathTypes)

            // Update initial instance without dispatching events
            mirrorInstance.replace(doc, dispatch = false)

            // Create the listeners
            createMirrorListener(mirrorInstance, referenceNode.get)
        }

        // Do RRR as xforms-model-construct didn't do it
        for (model ← nestedContainer.models) {
            model.doRebuild()
            model.doRecalculate(true)
            model.doRevalidate()
        }

        // xforms-model-construct-done
        for (model ← nestedContainer.models)
            Dispatch.dispatchEvent(new XFormsModelConstructDoneEvent(model))
    }

    override def onBindingUpdate(oldBinding: BindingContext, newBinding: BindingContext) {
        super.onBindingUpdate(oldBinding, newBinding)
        val isNodesetChange = ! Controls.compareNodesets(oldBinding.getNodeset.asScala, newBinding.getNodeset.asScala)
        if (isNodesetChange) {
            // Control's binding changed
            // NOP for now
        }
    }

    // This is called iif the iteration index changes
    override def updateEffectiveId() {

        // Update rest of control tree
        super.updateEffectiveId()

        // Update container with new effective id
        nestedContainer.updateEffectiveId(getEffectiveId)
    }

    override def iterationRemoved() {
        // Inform descendants
        super.iterationRemoved()

        // Destroy container and models if any
        destroyNestedContainer()
    }

    // Simply delegate but switch the container
    override def buildChildren(buildTree: (XBLContainer, BindingContext, ElementAnalysis, Seq[Int]) ⇒ Option[XFormsControl], idSuffix: Seq[Int]) =
        Controls.buildChildren(this, staticControl.children, (_, bindingContext, staticElement, idSuffix) ⇒ buildTree(nestedContainer, bindingContext, staticElement, idSuffix), idSuffix)

    // Get the control at the root of the inner scope of the component
    def innerRootControl = children collectFirst { case root: XXFormsComponentRootControl ⇒ root } get

    private lazy val handleLHHA = staticControl.binding.abstractBinding.modeLHHA && ! staticControl.binding.abstractBinding.modeLHHACustom

    // Don't add Ajax LHHA for custom-lhha mode
    override def addAjaxLHHA(other: XFormsControl, attributesImpl: AttributesImpl, isNewlyVisibleSubtree: Boolean) =
        handleLHHA && super.addAjaxLHHA(other, attributesImpl, isNewlyVisibleSubtree)

    // Consider LHHA hasn't externally changed for custom-lhha mode
    override def compareLHHA(other: XFormsControl) =
        ! handleLHHA || super.compareLHHA(other)

    // If component is directly focusable, attempt focus, otherwise delegate to container behavior
    override def setFocus(inputOnly: Boolean) =
        if (isFocusable)
            Focus.focusWithEvents(this)
        else
            super.setFocus(inputOnly)
}