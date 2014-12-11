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

import org.orbeon.oxf.xforms.state.ControlState
import org.orbeon.oxf.xml.SaxonUtils

import collection.JavaConverters._
import org.dom4j.Element
import org.orbeon.oxf.xforms.analysis.ControlAnalysisFactory.ValueControl
import org.orbeon.oxf.xforms.analysis.ElementAnalysis
import org.orbeon.oxf.xforms.analysis.controls.ComponentControl
import org.orbeon.oxf.xforms.control.controls.InstanceMirror._
import org.orbeon.oxf.xforms.control.controls.{XXFormsComponentRootControl, InstanceMirror}
import org.orbeon.oxf.xforms.event.events.{XFormsModelConstructDoneEvent, XFormsModelConstructEvent}
import org.orbeon.oxf.xforms.event.{XFormsEvent, Dispatch}
import Dispatch.EventListener
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

    // If the component handles LHHA itself, they are considered under of the nested container
    override def lhhaContainer = nestedContainer

    private var _listeners: Seq[(XFormsInstance, EventListener)] = Seq.empty

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
            nestedContainer.restoreModelsState()
        else
            initializeModels()
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

        _listeners = Seq(outerInstance → outerListener, mirrorInstance → innerListener)

        _listeners foreach { case (instance, listener) ⇒
            InstanceMirror.addListener(instance, listener)
        }

        Some(referenceNode)
    }

    private def destroyMirrorListenerIfNeeded(): Unit = {
        _listeners foreach { case (instance, listener) ⇒
            InstanceMirror.removeListener(instance, listener)
        }

        _listeners = Seq.empty
    }

    override def onDestroy(): Unit = {
        destroyMirrorListenerIfNeeded()
        super.onDestroy()
    }

    private def initializeModels(): Unit = {

        // xforms-model-construct, without RRR
        for (model ← nestedContainer.models)
            Dispatch.dispatchEvent(new XFormsModelConstructEvent(model, rrr = false))

        initializeMirrorListenerIfNeeded(dispatch = false)

        // Do RRR as xforms-model-construct didn't do it
        for (model ← nestedContainer.models) {
            model.doRebuild()
            model.doRecalculateRevalidate(applyDefaults = true)
        }

        // xforms-model-construct-done
        for (model ← nestedContainer.models)
            Dispatch.dispatchEvent(new XFormsModelConstructDoneEvent(model))
    }

    private def initializeMirrorListenerIfNeeded(dispatch: Boolean): Unit = {

        // NOTE: Must be called after xforms-model-construct so that instances are present
        def findMirrorInstance = (
            nestedContainer.models.iterator
            flatMap (_.getInstances.asScala)
            find    (_.instance.element.attributeValue("mirror") == "true")
        )

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
                    element               = unwrapElement(referenceNode.get),
                    excludeResultPrefixes = Set(),
                    readonly              = false,
                    exposeXPathTypes      = mirrorInstance.exposeXPathTypes,
                    removeInstanceData    = true
                )

            // Update initial instance
            mirrorInstance.replace(doc, dispatch)

            // Create the listeners
            createMirrorListener(mirrorInstance, referenceNode.get)
        }
    }

    override def onBindingUpdate(oldBinding: BindingContext, newBinding: BindingContext): Unit = {
        super.onBindingUpdate(oldBinding, newBinding)
        val isNodesetChange = ! SaxonUtils.compareItemSeqs(oldBinding.nodeset.asScala, newBinding.nodeset.asScala)
        if (isNodesetChange) {
            destroyMirrorListenerIfNeeded()
            initializeMirrorListenerIfNeeded(dispatch = true)
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

    // Don't add Ajax LHHA for custom-lhha mode
    override def addAjaxLHHA(other: XFormsControl, attributesImpl: AttributesImpl, isNewlyVisibleSubtree: Boolean) =
        handleLHHA && super.addAjaxLHHA(other, attributesImpl, isNewlyVisibleSubtree)

    // Consider LHHA hasn't externally changed for custom-lhha mode
    override def compareLHHA(other: XFormsControl) =
        ! handleLHHA || super.compareLHHA(other)
}